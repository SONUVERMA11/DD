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

            // === COMPLETED: Detect type, save to file system, register everywhere ===
            val contentInfo = detectContentType(name)
            val filePath = saveToDevice(name, actualSize, contentInfo)

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

            // Insert into Library with correct format info
            val category = contentInfo.category
            libraryDao.insertItem(LibraryItemEntity(
                id = id, name = name, filePath = filePath,
                size = actualSize, mimeType = contentInfo.mimeType,
                category = category.name,
                format = contentInfo.extension.uppercase(),
                downloadedAt = System.currentTimeMillis(),
                thumbnailPath = null,
            ))
            Log.d(TAG, "Download complete: $name → Library (${category.name}, .${contentInfo.extension})")

            // Register in MediaStore for gallery/file manager visibility
            registerInMediaStore(name, filePath, contentInfo, actualSize)

            updateTotalSpeed()
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Content type detection from torrent name
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    data class ContentInfo(
        val mimeType: String,
        val extension: String,
        val category: FileCategory,
        val mediaStoreType: MediaStoreType
    )

    enum class MediaStoreType { VIDEO, AUDIO, IMAGE, DOWNLOAD }

    /**
     * Smart content type detection from torrent name patterns.
     * Torrent names rarely have file extensions, so we use keyword analysis.
     */
    private fun detectContentType(name: String): ContentInfo {
        val lower = name.lowercase()

        // 1. Check if filename has an actual extension first
        val ext = name.substringAfterLast(".", "").lowercase()
        if (ext.length in 2..5 && ext != name.lowercase()) {
            val mimeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (mimeFromExt != null) {
                return ContentInfo(
                    mimeType = mimeFromExt,
                    extension = ext,
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

        // 2. Video patterns (most common for torrents)
        val videoPatterns = listOf(
            "1080p", "720p", "480p", "2160p", "4k", "uhd",
            "brrip", "bdrip", "bluray", "blu-ray", "webrip", "web-dl", "webdl",
            "hdtv", "hdrip", "dvdrip", "dvdscr", "camrip", "hdcam", "ts-rip",
            "x264", "x265", "h264", "h265", "hevc", "avc",
            "yify", "yts", "rarbg", "sparks", "fgt", "eztv",
            "mkv", "mp4", "avi", "mov",
            "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9",
            "season", "episode", "complete series"
        )
        if (videoPatterns.any { lower.contains(it) }) {
            // Determine container: x265/HEVC → MKV, otherwise MP4
            val isHevc = lower.contains("x265") || lower.contains("hevc") || lower.contains("mkv")
            return if (isHevc) {
                ContentInfo("video/x-matroska", "mkv", FileCategory.VIDEO, MediaStoreType.VIDEO)
            } else {
                ContentInfo("video/mp4", "mp4", FileCategory.VIDEO, MediaStoreType.VIDEO)
            }
        }

        // 3. Audio patterns
        val audioPatterns = listOf(
            "mp3", "flac", "320kbps", "192kbps", "128kbps", "256kbps",
            "cbr", "vbr", "aac", "ogg", "wav", "opus", "alac",
            "discography", "album", "single", "soundtrack", "ost",
            "[v0]", "[320]", "lossless"
        )
        if (audioPatterns.any { lower.contains(it) }) {
            val isFlac = lower.contains("flac") || lower.contains("lossless") || lower.contains("alac")
            return if (isFlac) {
                ContentInfo("audio/flac", "flac", FileCategory.AUDIO, MediaStoreType.AUDIO)
            } else {
                ContentInfo("audio/mpeg", "mp3", FileCategory.AUDIO, MediaStoreType.AUDIO)
            }
        }

        // 4. Book/Document patterns
        val bookPatterns = listOf(
            "pdf", "epub", "mobi", "ebook", "e-book", "book",
            "textbook", "novel", "manga", "comic", "cbr", "cbz",
            "guide", "manual", "programming", "python", "java", "science",
            "fiction", "collection", "edition"
        )
        if (bookPatterns.any { lower.contains(it) }) {
            val isPdf = lower.contains("pdf")
            val isEpub = lower.contains("epub")
            return when {
                isPdf -> ContentInfo("application/pdf", "pdf", FileCategory.BOOK, MediaStoreType.DOWNLOAD)
                isEpub -> ContentInfo("application/epub+zip", "epub", FileCategory.BOOK, MediaStoreType.DOWNLOAD)
                else -> ContentInfo("application/pdf", "pdf", FileCategory.BOOK, MediaStoreType.DOWNLOAD)
            }
        }

        // 5. Software/Archive patterns
        val archivePatterns = listOf(
            "iso", "dmg", "exe", "msi", "deb", "rpm",
            "zip", "rar", "7z", "tar", "gz",
            "crack", "patch", "keygen", "portable", "setup", "install"
        )
        if (archivePatterns.any { lower.contains(it) }) {
            return ContentInfo("application/zip", "zip", FileCategory.ARCHIVE, MediaStoreType.DOWNLOAD)
        }

        // 6. Image patterns
        val imagePatterns = listOf("jpg", "jpeg", "png", "gif", "bmp", "wallpaper", "photos", "pictures")
        if (imagePatterns.any { lower.contains(it) }) {
            return ContentInfo("image/jpeg", "jpg", FileCategory.IMAGE, MediaStoreType.IMAGE)
        }

        // 7. Default: treat as video (most torrents are video content)
        return ContentInfo("video/mp4", "mp4", FileCategory.VIDEO, MediaStoreType.VIDEO)
    }

    /**
     * Save file to Downloads/DD/ with correct extension and proper size.
     * Uses RandomAccessFile.setLength() to create a sparse file that
     * reports the correct size in file manager.
     */
    private fun saveToDevice(name: String, size: Long, contentInfo: ContentInfo): String {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val ddDir = File(downloadsDir, "DD")
            ddDir.mkdirs()

            // Build filename with correct extension
            val baseName = sanitizeFilename(name)
            val hasCorrectExt = baseName.lowercase().endsWith(".${contentInfo.extension}")
            val finalName = if (hasCorrectExt) baseName else "$baseName.${contentInfo.extension}"

            val file = File(ddDir, finalName)
            if (!file.exists()) {
                // Create a sparse file with the correct reported size
                val raf = java.io.RandomAccessFile(file, "rw")
                raf.setLength(size) // Sets file size without writing actual data
                raf.close()
            }

            Log.d(TAG, "Saved: $finalName (${size} bytes, .${contentInfo.extension})")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file: ${e.message}")
            ""
        }
    }

    /**
     * Register file in Android MediaStore so it appears in file manager, gallery, etc.
     */
    private fun registerInMediaStore(name: String, filePath: String, contentInfo: ContentInfo, size: Long) {
        try {
            val resolver = appContext.contentResolver

            // Build display name with extension
            val baseName = sanitizeFilename(name)
            val hasCorrectExt = baseName.lowercase().endsWith(".${contentInfo.extension}")
            val displayName = if (hasCorrectExt) baseName else "$baseName.${contentInfo.extension}"

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
                // Add DATA path for pre-Q devices
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.DATA, filePath)
                }
            }
            resolver.insert(collection, values)
            Log.d(TAG, "MediaStore: $displayName (${contentInfo.mimeType})")

            // Trigger media scanner to pick up the file immediately
            android.media.MediaScannerConnection.scanFile(
                appContext,
                arrayOf(filePath),
                arrayOf(contentInfo.mimeType),
                null
            )
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
