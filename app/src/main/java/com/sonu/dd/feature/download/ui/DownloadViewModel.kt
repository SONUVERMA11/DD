package com.sonu.dd.feature.download.ui

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
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
import com.sonu.dd.core.torrent.TorrentDownloadInfo
import com.sonu.dd.core.torrent.TorrentDownloadState
import com.sonu.dd.core.torrent.TorrentEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val torrentEngine: TorrentEngine,
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

    init {
        // Start the torrent engine when ViewModel is created
        torrentEngine.start()
    }

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
            startRealDownload(id, name, magnetUri, size, source)
        }
    }

    /**
     * Start a REAL torrent download using libtorrent4j.
     */
    private fun startRealDownload(
        id: String, name: String, magnetUri: String, totalSize: Long, source: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeSource = try { TorrentSource.valueOf(source) } catch (_: Exception) { TorrentSource.TPB }

            // Update initial state
            updateDownloadState(id, DownloadState(
                id = id, name = name, magnetUri = magnetUri,
                totalSize = totalSize, downloadedSize = 0,
                progress = 0f, downloadSpeed = 0, uploadSpeed = 0,
                seeds = 0, peers = 0, eta = 0,
                status = DownloadStatus.METADATA, source = safeSource
            ))
            downloadDao.updateStatus(id, DownloadStatus.METADATA.name)

            torrentEngine.addDownload(
                id = id,
                magnetUri = magnetUri,
                onProgress = { info ->
                    viewModelScope.launch {
                        handleProgress(id, info, name, magnetUri, totalSize, safeSource)
                    }
                },
                onComplete = { filePath ->
                    viewModelScope.launch {
                        handleComplete(id, name, magnetUri, filePath, safeSource)
                    }
                }
            )
        }
    }

    /**
     * Handle real-time progress updates from the torrent engine.
     */
    private suspend fun handleProgress(
        id: String, info: TorrentDownloadInfo,
        originalName: String, magnetUri: String,
        originalSize: Long, source: TorrentSource
    ) {
        val displayName = if (info.name.isNotEmpty() && info.name != "Fetching metadata..." && info.name != "Error") {
            info.name
        } else originalName

        val resolvedSize = if (info.totalSize > 0) info.totalSize else originalSize

        val status = when (info.state) {
            TorrentDownloadState.FETCHING_METADATA -> DownloadStatus.METADATA
            TorrentDownloadState.DOWNLOADING -> DownloadStatus.DOWNLOADING
            TorrentDownloadState.PAUSED -> DownloadStatus.PAUSED
            TorrentDownloadState.COMPLETED, TorrentDownloadState.SEEDING -> DownloadStatus.COMPLETED
            TorrentDownloadState.ERROR -> DownloadStatus.FAILED
        }

        updateDownloadState(id, DownloadState(
            id = id, name = displayName, magnetUri = magnetUri,
            totalSize = resolvedSize, downloadedSize = info.downloadedSize,
            progress = info.progress, downloadSpeed = info.downloadSpeed,
            uploadSpeed = info.uploadSpeed, seeds = info.seeds, peers = info.peers,
            eta = info.eta, status = status, source = source,
            filePath = info.savePath
        ))

        downloadDao.updateProgress(id, info.progress, info.downloadedSize)
        downloadDao.updateStatus(id, status.name)
        updateTotalSpeed()
    }

    /**
     * Handle download completion — register in Library and MediaStore.
     */
    private suspend fun handleComplete(
        id: String, originalName: String, magnetUri: String,
        filePath: String, source: TorrentSource
    ) {
        val file = File(filePath)
        val actualName = file.name.ifEmpty { originalName }
        val actualSize = if (file.exists()) file.length() else 0L

        // Detect content type from file extension (real file now has real extension)
        val contentInfo = detectContentType(actualName)

        updateDownloadState(id, DownloadState(
            id = id, name = actualName, magnetUri = magnetUri,
            totalSize = actualSize, downloadedSize = actualSize,
            progress = 1f, downloadSpeed = 0, uploadSpeed = 0,
            seeds = 0, peers = 0, eta = 0,
            status = DownloadStatus.COMPLETED, source = source,
            filePath = filePath
        ))
        downloadDao.updateStatus(id, DownloadStatus.COMPLETED.name)
        downloadDao.updateProgress(id, 1f, actualSize)

        // Insert into Library
        libraryDao.insertItem(LibraryItemEntity(
            id = id, name = actualName, filePath = filePath,
            size = actualSize, mimeType = contentInfo.mimeType,
            category = contentInfo.category.name,
            format = contentInfo.extension.uppercase(),
            downloadedAt = System.currentTimeMillis(),
            thumbnailPath = null,
        ))
        Log.d(TAG, "Download complete: $actualName → Library (${contentInfo.category.name})")

        // Register in MediaStore
        registerInMediaStore(actualName, filePath, contentInfo, actualSize)
        updateTotalSpeed()
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Content type detection
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    data class ContentInfo(
        val mimeType: String,
        val extension: String,
        val category: FileCategory,
        val mediaStoreType: MediaStoreType
    )

    enum class MediaStoreType { VIDEO, AUDIO, IMAGE, DOWNLOAD }

    private fun detectContentType(name: String): ContentInfo {
        val lower = name.lowercase()

        // 1. Check actual file extension first
        val ext = name.substringAfterLast(".", "").lowercase()
        if (ext.length in 2..5 && ext != name.lowercase()) {
            val mimeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: when (ext) {
                    "mkv" -> "video/x-matroska"
                    "avi" -> "video/x-msvideo"
                    "flac" -> "audio/flac"
                    "epub" -> "application/epub+zip"
                    "mobi" -> "application/x-mobipocket-ebook"
                    else -> null
                }
            if (mimeFromExt != null) {
                return ContentInfo(
                    mimeType = mimeFromExt, extension = ext,
                    category = FileCategory.fromMimeType(mimeFromExt),
                    mediaStoreType = when {
                        mimeFromExt.startsWith("video/") -> MediaStoreType.VIDEO
                        mimeFromExt.startsWith("audio/") -> MediaStoreType.AUDIO
                        mimeFromExt.startsWith("image/") -> MediaStoreType.IMAGE
                        else -> MediaStoreType.DOWNLOAD
                    }
                )
            }
        }

        // 2. Keyword-based detection for torrent names without extensions
        val videoPatterns = listOf("1080p", "720p", "480p", "2160p", "4k", "brrip", "bluray", "webrip", "web-dl", "hdtv", "x264", "x265", "hevc", "yify", "yts", "mkv", "mp4", "avi", "season", "episode")
        if (videoPatterns.any { lower.contains(it) }) {
            val isHevc = lower.contains("x265") || lower.contains("hevc") || lower.contains("mkv")
            return if (isHevc) ContentInfo("video/x-matroska", "mkv", FileCategory.VIDEO, MediaStoreType.VIDEO)
            else ContentInfo("video/mp4", "mp4", FileCategory.VIDEO, MediaStoreType.VIDEO)
        }

        val audioPatterns = listOf("mp3", "flac", "320kbps", "192kbps", "cbr", "vbr", "album", "discography", "soundtrack", "lossless")
        if (audioPatterns.any { lower.contains(it) }) {
            return if (lower.contains("flac") || lower.contains("lossless")) ContentInfo("audio/flac", "flac", FileCategory.AUDIO, MediaStoreType.AUDIO)
            else ContentInfo("audio/mpeg", "mp3", FileCategory.AUDIO, MediaStoreType.AUDIO)
        }

        val bookPatterns = listOf("pdf", "epub", "mobi", "ebook", "book", "programming", "python", "novel", "textbook", "fiction", "collection")
        if (bookPatterns.any { lower.contains(it) }) {
            return if (lower.contains("epub")) ContentInfo("application/epub+zip", "epub", FileCategory.BOOK, MediaStoreType.DOWNLOAD)
            else ContentInfo("application/pdf", "pdf", FileCategory.BOOK, MediaStoreType.DOWNLOAD)
        }

        val archivePatterns = listOf("iso", "zip", "rar", "7z", "tar", "exe", "setup", "install")
        if (archivePatterns.any { lower.contains(it) }) {
            return ContentInfo("application/zip", "zip", FileCategory.ARCHIVE, MediaStoreType.DOWNLOAD)
        }

        // Default
        return ContentInfo("application/octet-stream", "bin", FileCategory.OTHER, MediaStoreType.DOWNLOAD)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // MediaStore registration
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private fun registerInMediaStore(name: String, filePath: String, contentInfo: ContentInfo, size: Long) {
        try {
            val resolver = appContext.contentResolver
            val displayName = sanitizeFilename(name)

            val collection = when (contentInfo.mediaStoreType) {
                MediaStoreType.VIDEO -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                MediaStoreType.AUDIO -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                MediaStoreType.IMAGE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                MediaStoreType.DOWNLOAD -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else return
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, contentInfo.mimeType)
                put(MediaStore.MediaColumns.SIZE, size)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DD")
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.DATA, filePath)
                }
            }
            resolver.insert(collection, values)

            // Trigger media scanner
            MediaScannerConnection.scanFile(appContext, arrayOf(filePath), arrayOf(contentInfo.mimeType), null)
            Log.d(TAG, "MediaStore: $displayName (${contentInfo.mimeType})")
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore registration failed: ${e.message}")
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
        torrentEngine.pauseDownload(id)
        val current = _downloadsState.value[id] ?: return
        updateDownloadState(id, current.copy(status = DownloadStatus.PAUSED, downloadSpeed = 0))
        viewModelScope.launch { downloadDao.updateStatus(id, DownloadStatus.PAUSED.name); updateTotalSpeed() }
    }

    fun resumeDownload(id: String) {
        torrentEngine.resumeDownload(id)
        val current = _downloadsState.value[id] ?: return
        updateDownloadState(id, current.copy(status = DownloadStatus.DOWNLOADING))
        viewModelScope.launch { downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING.name) }
    }

    fun cancelDownload(id: String) {
        torrentEngine.cancelDownload(id)
        _downloadsState.value = _downloadsState.value.toMutableMap().apply { remove(id) }
        viewModelScope.launch { downloadDao.deleteDownload(id); updateTotalSpeed() }
    }

    fun pauseAll() { _downloadsState.value.keys.forEach { pauseDownload(it) } }

    fun resumeAll() {
        _downloadsState.value.filter { it.value.status == DownloadStatus.PAUSED }
            .keys.forEach { resumeDownload(it) }
    }

    override fun onCleared() {
        super.onCleared()
        torrentEngine.stop()
    }
}
