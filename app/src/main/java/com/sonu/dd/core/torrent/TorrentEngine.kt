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
import org.libtorrent4j.swig.settings_pack
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
    val eta: Long, // seconds
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

    // Track active torrent handles by ID (magnetUri hash or infoHash)
    private val activeTorrents = ConcurrentHashMap<String, TorrentHandle>()
    private val downloadCallbacks = ConcurrentHashMap<String, (TorrentDownloadInfo) -> Unit>()
    private val completionCallbacks = ConcurrentHashMap<String, (String) -> Unit>()

    /**
     * Initialize the libtorrent session.
     */
    fun start() {
        if (sessionManager != null) return

        try {
            val sm = SessionManager()

            // Configure session settings
            val params = SessionParams(defaultSettingsPack())
            sm.start(params)

            // Set up alert listener for torrent events
            sm.addListener(object : AlertListener {
                override fun types(): IntArray? = null // listen to all alerts

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

    /**
     * Create default settings for the libtorrent session.
     */
    private fun defaultSettingsPack(): SettingsPack {
        val sp = SettingsPack()

        // Connections
        sp.connectionsLimit(200)
        sp.maxPeerlistSize(1000)
        sp.activeDownloads(5)
        sp.activeSeeds(5)
        sp.activeLimit(10)

        // Performance
        sp.sendBufferWatermark(512 * 1024) // 512KB
        sp.sendBufferLowWatermark(128 * 1024) // 128KB

        // Enable DHT, LSD, UPnP, NAT-PMP
        sp.enableDht(true)
        sp.enableLsd(true)
        sp.enableUpnp(true)
        sp.enableNatpmp(true)

        // Anonymous mode
        sp.anonymousMode(false)

        // User agent
        sp.setString(settings_pack.string_types.user_agent.swigValue(), "DD/1.0 libtorrent")

        return sp
    }

    /**
     * Get the save directory for downloads.
     */
    private fun getDownloadDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val ddDir = File(downloadsDir, "DD")
        ddDir.mkdirs()
        return ddDir
    }

    /**
     * Add a magnet URI for downloading.
     * @param id Unique identifier for this download
     * @param magnetUri The magnet URI to download
     * @param onProgress Callback for progress updates
     * @param onComplete Callback when download completes, receives the file path
     */
    suspend fun addDownload(
        id: String,
        magnetUri: String,
        onProgress: (TorrentDownloadInfo) -> Unit,
        onComplete: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val sm = sessionManager
        if (sm == null) {
            start()
        }

        val session = sessionManager ?: run {
            Log.e(TAG, "Session not available")
            onProgress(TorrentDownloadInfo(
                id = id, name = "Error", totalSize = 0, downloadedSize = 0,
                downloadSpeed = 0, uploadSpeed = 0, seeds = 0, peers = 0,
                progress = 0f, savePath = "", state = TorrentDownloadState.ERROR, eta = 0
            ))
            return@withContext
        }

        try {
            val saveDir = getDownloadDir()
            Log.d(TAG, "Adding magnet: ${magnetUri.take(60)}...")

            // Register callbacks
            downloadCallbacks[id] = onProgress
            completionCallbacks[id] = onComplete

            // Notify: fetching metadata
            onProgress(TorrentDownloadInfo(
                id = id, name = "Fetching metadata...", totalSize = 0, downloadedSize = 0,
                downloadSpeed = 0, uploadSpeed = 0, seeds = 0, peers = 0,
                progress = 0f, savePath = saveDir.absolutePath,
                state = TorrentDownloadState.FETCHING_METADATA, eta = 0
            ))

            // Download the torrent from magnet
            session.download(magnetUri, saveDir)

            // Wait for the torrent handle to become available
            var handle: TorrentHandle? = null
            val infoHash = extractInfoHash(magnetUri)

            // Poll for the handle
            for (i in 0..60) { // wait up to 60 seconds for metadata
                delay(1000)
                val torrents = session.torrents()
                handle = torrents.find { th: TorrentHandle ->
                    th.infoHash()?.toHex()?.lowercase() == infoHash?.lowercase()
                }
                if (handle != null) {
                    activeTorrents[id] = handle
                    break
                }
            }

            if (handle == null) {
                Log.e(TAG, "Failed to get torrent handle for: $id")
                onProgress(TorrentDownloadInfo(
                    id = id, name = "Failed: No peers found", totalSize = 0,
                    downloadedSize = 0, downloadSpeed = 0, uploadSpeed = 0,
                    seeds = 0, peers = 0, progress = 0f, savePath = "",
                    state = TorrentDownloadState.ERROR, eta = 0
                ))
                return@withContext
            }

            Log.d(TAG, "Handle acquired for: $id")

            // Monitor download progress
            monitorDownload(id, handle, onProgress, onComplete)

        } catch (e: Exception) {
            Log.e(TAG, "Error adding download: ${e.message}", e)
            onProgress(TorrentDownloadInfo(
                id = id, name = "Error: ${e.message}", totalSize = 0,
                downloadedSize = 0, downloadSpeed = 0, uploadSpeed = 0,
                seeds = 0, peers = 0, progress = 0f, savePath = "",
                state = TorrentDownloadState.ERROR, eta = 0
            ))
        }
    }

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
            delay(1000) // Update every second

            try {
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

                // Calculate ETA
                val eta = if (dlSpeed > 0 && totalSize > downloaded) {
                    (totalSize - downloaded) / dlSpeed
                } else 0L

                val state = when {
                    status.isFinished -> TorrentDownloadState.COMPLETED
                    status.isSeeding -> TorrentDownloadState.SEEDING
                    status.isPaused -> TorrentDownloadState.PAUSED
                    !handle.isValid -> TorrentDownloadState.ERROR
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

                    // Determine the primary file path
                    val primaryFile = if (files.isNotEmpty()) {
                        // Find the largest file (usually the main content)
                        val savePath = handle.savePath()
                        val largestFile = if (ti != null) {
                            val fs = ti.files()
                            var maxSize = 0L
                            var maxIdx = 0
                            for (i in 0 until fs.numFiles()) {
                                if (fs.fileSize(i) > maxSize) {
                                    maxSize = fs.fileSize(i)
                                    maxIdx = i
                                }
                            }
                            fs.filePath(maxIdx)
                        } else files.firstOrNull() ?: name
                        File(savePath, largestFile).absolutePath
                    } else {
                        File(handle.savePath(), name).absolutePath
                    }

                    Log.d(TAG, "Download complete: $name → $primaryFile")
                    onComplete(primaryFile)
                    downloadCallbacks.remove(id)
                    completionCallbacks.remove(id)
                }

                if (state == TorrentDownloadState.ERROR) {
                    completed = true
                    downloadCallbacks.remove(id)
                    completionCallbacks.remove(id)
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error monitoring download $id: ${e.message}")
                // Don't break on transient errors, keep monitoring
            }
        }
    }

    /**
     * Pause a download.
     */
    fun pauseDownload(id: String) {
        activeTorrents[id]?.pause()
    }

    /**
     * Resume a download.
     */
    fun resumeDownload(id: String) {
        activeTorrents[id]?.resume()
    }

    /**
     * Cancel a download and remove files.
     */
    fun cancelDownload(id: String) {
        val handle = activeTorrents.remove(id) ?: return
        sessionManager?.remove(handle)
        downloadCallbacks.remove(id)
        completionCallbacks.remove(id)
    }

    /**
     * Handle libtorrent alerts.
     */
    private fun handleAlert(alert: Alert<*>) {
        when (alert.type()) {
            AlertType.TORRENT_FINISHED -> {
                val a = alert as TorrentFinishedAlert
                Log.d(TAG, "Alert: Torrent finished — ${a.torrentName()}")
            }
            AlertType.TORRENT_ERROR -> {
                val a = alert as TorrentErrorAlert
                Log.e(TAG, "Alert: Torrent error — ${a.torrentName()}: ${a.error()}")
            }
            AlertType.METADATA_RECEIVED -> {
                Log.d(TAG, "Alert: Metadata received")
            }
            AlertType.PEER_CONNECT -> {
                // Peer connected, normal operation
            }
            AlertType.DHT_BOOTSTRAP -> {
                Log.d(TAG, "Alert: DHT bootstrapped")
            }
            else -> {
                // Other alerts
            }
        }
    }

    /**
     * Extract info hash from magnet URI.
     */
    private fun extractInfoHash(magnetUri: String): String? {
        val regex = Regex("btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE)
        return regex.find(magnetUri)?.groupValues?.get(1)
    }

    /**
     * Stop the engine and release resources.
     */
    fun stop() {
        try {
            activeTorrents.clear()
            downloadCallbacks.clear()
            completionCallbacks.clear()
            sessionManager?.stop()
            sessionManager = null
            _isRunning.value = false
            Log.d(TAG, "TorrentEngine stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping engine: ${e.message}")
        }
    }
}
