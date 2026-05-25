package com.sonu.dd.core.torrent

import android.content.Context
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Torrent download status for tracking individual downloads.
 */
data class TorrentDownloadInfo(
    val id: String,
    val name: String,
    val totalSize: Long,
    val downloadedSize: Long,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val seeds: Int,
    val peers: Int,
    val progress: Float,
    val savePath: String,
    val state: TorrentDownloadState,
    val eta: Long,
    val files: List<String> = emptyList(),
)

enum class TorrentDownloadState {
    FETCHING_METADATA,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    SEEDING,
    ERROR,
}

/**
 * Real torrent engine powered by libtorrent4j.
 * Handles magnet URI resolution, metadata fetching, and file downloading.
 */
@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "TorrentEngine"
    }

    private var sessionManager: SessionManager? = null
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val activeTorrents = ConcurrentHashMap<String, TorrentHandle>()
    private val downloadCallbacks = ConcurrentHashMap<String, (TorrentDownloadInfo) -> Unit>()
    private val completionCallbacks = ConcurrentHashMap<String, (String) -> Unit>()
    private val infoHashMap = ConcurrentHashMap<String, Sha1Hash>() // download ID → info hash

    /**
     * Initialize the libtorrent session.
     */
    fun start() {
        if (sessionManager != null) return

        try {
            val sm = SessionManager()
            sm.start()

            sm.addListener(object : AlertListener {
                override fun types(): IntArray? = null

                override fun alert(alert: Alert<*>) {
                    handleAlert(alert)
                }
            })

            sessionManager = sm
            _isRunning.value = true
            Log.d(TAG, "TorrentEngine started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TorrentEngine: ${e.message}", e)
        }
    }

    private fun getDownloadDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val ddDir = File(downloadsDir, "DD")
        ddDir.mkdirs()
        return ddDir
    }

    /**
     * Add a magnet URI for downloading.
     */
    suspend fun addDownload(
        id: String,
        magnetUri: String,
        onProgress: (TorrentDownloadInfo) -> Unit,
        onComplete: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (sessionManager == null) start()

        val session = sessionManager ?: run {
            Log.e(TAG, "Session not available")
            onProgress(errorInfo(id, "Engine failed to start"))
            return@withContext
        }

        try {
            val saveDir = getDownloadDir()
            Log.d(TAG, "Adding magnet: ${magnetUri.take(80)}...")

            downloadCallbacks[id] = onProgress
            completionCallbacks[id] = onComplete

            onProgress(TorrentDownloadInfo(
                id = id, name = "Fetching metadata...", totalSize = 0, downloadedSize = 0,
                downloadSpeed = 0, uploadSpeed = 0, seeds = 0, peers = 0,
                progress = 0f, savePath = saveDir.absolutePath,
                state = TorrentDownloadState.FETCHING_METADATA, eta = 0
            ))

            // Parse the magnet URI to extract info hash
            val ec = error_code()
            val p = libtorrent.parse_magnet_uri(magnetUri, ec)
            if (ec.value() != 0) {
                Log.e(TAG, "Invalid magnet URI: ${ec.message()}")
                onProgress(errorInfo(id, "Invalid magnet: ${ec.message()}"))
                return@withContext
            }

            val infoHash = Sha1Hash(p.getInfo_hashes().get_best())
            infoHashMap[id] = infoHash

            // Use the 3-param download method for magnet URIs
            session.download(magnetUri, saveDir, torrent_flags_t())

            // Wait for the handle to appear
            var handle: TorrentHandle? = null
            for (i in 0..120) { // wait up to 2 minutes for metadata
                delay(1000)
                handle = session.find(infoHash)
                if (handle != null && handle.isValid) {
                    activeTorrents[id] = handle
                    Log.d(TAG, "Handle acquired for: $id")
                    break
                }
            }

            if (handle == null || !handle.isValid) {
                Log.e(TAG, "Failed to get torrent handle for: $id")
                onProgress(errorInfo(id, "Timeout: no peers found"))
                return@withContext
            }

            // Monitor download progress
            monitorDownload(id, handle, onProgress, onComplete)

        } catch (e: Exception) {
            Log.e(TAG, "Error adding download: ${e.message}", e)
            onProgress(errorInfo(id, "Error: ${e.message}"))
        }
    }

    private fun errorInfo(id: String, message: String) = TorrentDownloadInfo(
        id = id, name = message, totalSize = 0, downloadedSize = 0,
        downloadSpeed = 0, uploadSpeed = 0, seeds = 0, peers = 0,
        progress = 0f, savePath = "", state = TorrentDownloadState.ERROR, eta = 0
    )

    /**
     * Monitor an active download and report progress.
     */
    private suspend fun monitorDownload(
        id: String,
        handle: TorrentHandle,
        onProgress: (TorrentDownloadInfo) -> Unit,
        onComplete: (String) -> Unit,
    ) {
        var completed = false

        while (!completed) {
            delay(1000)

            try {
                if (!handle.isValid) {
                    onProgress(errorInfo(id, "Torrent handle invalid"))
                    completed = true
                    continue
                }

                val status = handle.status()
                val ti = handle.torrentFile()

                val name = ti?.name() ?: status.name()
                val totalSize = ti?.totalSize() ?: 0L
                val downloaded = status.totalDone()
                val dlSpeed = status.downloadRate().toLong()
                val ulSpeed = status.uploadRate().toLong()
                val seeds = status.numSeeds()
                val peers = status.numPeers()
                val progress = if (totalSize > 0) downloaded.toFloat() / totalSize else status.progress()

                val eta = if (dlSpeed > 0 && totalSize > downloaded) {
                    (totalSize - downloaded) / dlSpeed
                } else 0L

                val isPaused = status.flags().and_(TorrentFlags.PAUSED).ne(torrent_flags_t())

                val state = when {
                    status.isFinished -> TorrentDownloadState.COMPLETED
                    status.isSeeding -> TorrentDownloadState.SEEDING
                    isPaused -> TorrentDownloadState.PAUSED
                    totalSize == 0L -> TorrentDownloadState.FETCHING_METADATA
                    else -> TorrentDownloadState.DOWNLOADING
                }

                // Get file list
                val files = mutableListOf<String>()
                if (ti != null) {
                    val fileStorage = ti.files()
                    for (i in 0 until fileStorage.numFiles()) {
                        files.add(fileStorage.filePath(i))
                    }
                }

                val info = TorrentDownloadInfo(
                    id = id, name = name, totalSize = totalSize,
                    downloadedSize = downloaded, downloadSpeed = dlSpeed,
                    uploadSpeed = ulSpeed, seeds = seeds, peers = peers,
                    progress = progress, savePath = handle.savePath(),
                    state = state, eta = eta, files = files
                )

                onProgress(info)

                if (state == TorrentDownloadState.COMPLETED || state == TorrentDownloadState.SEEDING) {
                    completed = true

                    // Find the largest file (main content)
                    val primaryFile = if (ti != null && ti.files().numFiles() > 0) {
                        val fs = ti.files()
                        var maxSize = 0L
                        var maxIdx = 0
                        for (i in 0 until fs.numFiles()) {
                            if (fs.fileSize(i) > maxSize) {
                                maxSize = fs.fileSize(i)
                                maxIdx = i
                            }
                        }
                        File(handle.savePath(), fs.filePath(maxIdx)).absolutePath
                    } else {
                        File(handle.savePath(), name).absolutePath
                    }

                    Log.d(TAG, "Download complete: $name → $primaryFile")
                    onComplete(primaryFile)
                    cleanup(id)
                }

                if (state == TorrentDownloadState.ERROR) {
                    completed = true
                    cleanup(id)
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error monitoring download $id: ${e.message}")
            }
        }
    }

    private fun cleanup(id: String) {
        downloadCallbacks.remove(id)
        completionCallbacks.remove(id)
    }

    fun pauseDownload(id: String) {
        activeTorrents[id]?.pause()
    }

    fun resumeDownload(id: String) {
        activeTorrents[id]?.resume()
    }

    fun cancelDownload(id: String) {
        val handle = activeTorrents.remove(id) ?: return
        sessionManager?.remove(handle)
        infoHashMap.remove(id)
        cleanup(id)
    }

    private fun handleAlert(alert: Alert<*>) {
        when (alert.type()) {
            AlertType.TORRENT_FINISHED -> {
                Log.d(TAG, "Alert: Torrent finished")
            }
            AlertType.TORRENT_ERROR -> {
                val a = alert as TorrentErrorAlert
                Log.e(TAG, "Alert: Torrent error — ${a.error()}")
            }
            AlertType.METADATA_RECEIVED -> {
                Log.d(TAG, "Alert: Metadata received")
            }
            AlertType.DHT_BOOTSTRAP -> {
                Log.d(TAG, "Alert: DHT bootstrapped")
            }
            else -> { }
        }
    }

    fun stop() {
        try {
            activeTorrents.clear()
            downloadCallbacks.clear()
            completionCallbacks.clear()
            infoHashMap.clear()
            sessionManager?.stop()
            sessionManager = null
            _isRunning.value = false
            Log.d(TAG, "TorrentEngine stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping engine: ${e.message}")
        }
    }
}
