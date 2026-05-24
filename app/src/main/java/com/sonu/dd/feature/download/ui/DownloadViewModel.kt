package com.sonu.dd.feature.download.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonu.dd.core.data.datastore.DDPreferences
import com.sonu.dd.core.data.db.DownloadDao
import com.sonu.dd.core.data.db.DownloadEntity
import com.sonu.dd.core.data.db.LibraryDao
import com.sonu.dd.core.data.db.LibraryItemEntity
import com.sonu.dd.core.domain.model.DownloadState
import com.sonu.dd.core.domain.model.DownloadStatus
import com.sonu.dd.core.domain.model.FileCategory
import com.sonu.dd.core.domain.model.TorrentSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class DownloadsUiState(
    val totalSpeed: Long = 0L,
    val peakSpeed: Long = 0L,
    val activeCount: Int = 0,
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val libraryDao: LibraryDao,
    private val preferences: DDPreferences,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    companion object {
        private const val TAG = "DownloadVM"
    }

    val allDownloads = downloadDao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads = downloadDao.getActiveDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _downloadsState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadsState: StateFlow<Map<String, DownloadState>> = _downloadsState.asStateFlow()

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _totalSpeed = MutableStateFlow(0L)
    val totalSpeed: StateFlow<Long> = _totalSpeed.asStateFlow()

    private val _peakSpeed = MutableStateFlow(0L)
    val peakSpeed: StateFlow<Long> = _peakSpeed.asStateFlow()

    fun addDownload(
        magnetUri: String, name: String, size: Long,
        source: String, selectedFormat: String = "original"
    ) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val entity = DownloadEntity(
                id = id, name = name, magnetUri = magnetUri,
                totalSize = size, downloadedSize = 0L, progress = 0f,
                status = DownloadStatus.QUEUED.name, source = source,
                filePath = "", selectedFormat = selectedFormat,
                addedTimestamp = System.currentTimeMillis()
            )
            downloadDao.insertDownload(entity)
            startDownloadSimulation(id, name, magnetUri, size, source)
        }
    }

    private fun startDownloadSimulation(
        id: String, name: String, magnetUri: String, totalSize: Long, source: String
    ) {
        viewModelScope.launch {
            val safeSource = try { TorrentSource.valueOf(source) } catch (_: Exception) { TorrentSource.TPB }
            val actualSize = if (totalSize > 0) totalSize else 700 * 1024 * 1024L

            // Metadata phase
            updateDownloadState(id, DownloadState(
                id = id, name = name, magnetUri = magnetUri,
                totalSize = actualSize, downloadedSize = 0,
                progress = 0f, downloadSpeed = 0, uploadSpeed = 0,
                seeds = 0, peers = 0, eta = 0,
                status = DownloadStatus.METADATA, source = safeSource
            ))
            downloadDao.updateStatus(id, DownloadStatus.METADATA.name)
            delay(2000)

            // Downloading phase
            var downloaded = 0L
            val baseSpeed = (2 * 1024 * 1024L)..(15 * 1024 * 1024L)
            downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING.name)

            while (downloaded < actualSize) {
                val currentState = _downloadsState.value[id]
                if (currentState?.status == DownloadStatus.PAUSED) { delay(500); continue }
                if (currentState?.status == DownloadStatus.FAILED) return@launch

                val speed = baseSpeed.random()
                val chunk = (speed * 0.3).toLong()
                downloaded = (downloaded + chunk).coerceAtMost(actualSize)
                val progress = downloaded.toFloat() / actualSize
                val eta = if (speed > 0) (actualSize - downloaded) / speed else 0L

                updateDownloadState(id, DownloadState(
                    id = id, name = name, magnetUri = magnetUri,
                    totalSize = actualSize, downloadedSize = downloaded,
                    progress = progress, downloadSpeed = speed, uploadSpeed = speed / 8,
                    seeds = (5..50).random(), peers = (2..20).random(),
                    eta = eta, status = DownloadStatus.DOWNLOADING, source = safeSource
                ))
                downloadDao.updateProgress(id, progress, downloaded)
                updateTotalSpeed()
                delay(300)
            }

            // === COMPLETED: Save to file system, MediaStore, and Library ===
            val filePath = saveToDevice(name, actualSize)

            updateDownloadState(id, DownloadState(
                id = id, name = name, magnetUri = magnetUri,
                totalSize = actualSize, downloadedSize = actualSize,
                progress = 1f, downloadSpeed = 0, uploadSpeed = 0,
                seeds = 0, peers = 0, eta = 0,
                status = DownloadStatus.COMPLETED, source = safeSource,
                filePath = filePath
            ))
            downloadDao.updateStatus(id, DownloadStatus.COMPLETED.name)
            downloadDao.updateProgress(id, 1f, actualSize)

            // Insert into Library
            val mimeType = guessMimeType(name)
            val category = FileCategory.fromMimeType(mimeType)
            libraryDao.insertItem(LibraryItemEntity(
                id = id, name = name, filePath = filePath,
                size = actualSize, mimeType = mimeType,
                category = category.name,
                format = name.substringAfterLast(".", "unknown"),
                downloadedAt = System.currentTimeMillis(),
                thumbnailPath = null,
            ))
            Log.d(TAG, "Download complete: $name → Library (${category.name})")

            // Register in MediaStore for gallery/file manager visibility
            val autoSave = preferences.autoSaveGalleryFlow.first()
            if (autoSave) {
                registerInMediaStore(name, filePath, mimeType, actualSize)
            }

            updateTotalSpeed()
        }
    }

    /**
     * Create a placeholder file in the Downloads directory.
     * In production with LibTorrent4J, the real file would already be here.
     */
    private fun saveToDevice(name: String, size: Long): String {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val ddDir = File(downloadsDir, "DD")
            ddDir.mkdirs()
            val file = File(ddDir, sanitizeFilename(name))
            if (!file.exists()) {
                file.createNewFile()
                // Write size marker so the file has content
                file.writeText("DD Download Placeholder — $name — ${size}B")
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file: ${e.message}")
            ""
        }
    }

    /**
     * Register file in Android MediaStore so it appears in Files, Gallery, etc.
     */
    private fun registerInMediaStore(name: String, filePath: String, mimeType: String, size: Long) {
        try {
            val resolver = appContext.contentResolver
            val collection = when {
                mimeType.startsWith("video/") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                mimeType.startsWith("audio/") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                mimeType.startsWith("image/") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else return // Can't register on older APIs for non-media
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizeFilename(name))
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.SIZE, size)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DD")
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
            }
            resolver.insert(collection, values)
            Log.d(TAG, "Registered in MediaStore: $name ($mimeType)")
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore registration failed: ${e.message}")
        }
    }

    private fun guessMimeType(filename: String): String {
        val ext = filename.substringAfterLast(".", "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "flac" -> "audio/flac"
                "epub" -> "application/epub+zip"
                "mobi" -> "application/x-mobipocket-ebook"
                "torrent" -> "application/x-bittorrent"
                else -> "application/octet-stream"
            }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)
    }

    private fun updateDownloadState(id: String, state: DownloadState) {
        _downloadsState.value = _downloadsState.value.toMutableMap().apply { put(id, state) }
    }

    private fun updateTotalSpeed() {
        val speeds = _downloadsState.value.values
            .filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { it.downloadSpeed }
        _totalSpeed.value = speeds
        if (speeds > _peakSpeed.value) _peakSpeed.value = speeds
        _uiState.value = _uiState.value.copy(
            totalSpeed = speeds, peakSpeed = _peakSpeed.value,
            activeCount = _downloadsState.value.values.count { it.status.isActive }
        )
    }

    fun pauseDownload(id: String) {
        val current = _downloadsState.value[id] ?: return
        updateDownloadState(id, current.copy(status = DownloadStatus.PAUSED, downloadSpeed = 0))
        viewModelScope.launch { downloadDao.updateStatus(id, DownloadStatus.PAUSED.name); updateTotalSpeed() }
    }

    fun resumeDownload(id: String) {
        val current = _downloadsState.value[id] ?: return
        updateDownloadState(id, current.copy(status = DownloadStatus.DOWNLOADING))
        viewModelScope.launch { downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING.name) }
    }

    fun cancelDownload(id: String) {
        val current = _downloadsState.value[id]
        if (current != null) updateDownloadState(id, current.copy(status = DownloadStatus.FAILED))
        _downloadsState.value = _downloadsState.value.toMutableMap().apply { remove(id) }
        viewModelScope.launch { downloadDao.deleteDownload(id); updateTotalSpeed() }
    }

    fun pauseAll() { _downloadsState.value.keys.forEach { pauseDownload(it) } }

    fun resumeAll() {
        _downloadsState.value.filter { it.value.status == DownloadStatus.PAUSED }
            .keys.forEach { resumeDownload(it) }
    }
}
