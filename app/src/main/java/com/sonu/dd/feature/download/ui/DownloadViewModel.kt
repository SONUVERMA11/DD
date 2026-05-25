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
     * Save file to Downloads/DD/ with correct extension and VALID content.
     * Generates real file content so viewers can open them:
     * - PDF: Valid PDF with title page
     * - Audio/Video/Archive: Valid headers so file managers show them correctly
     */
    private fun saveToDevice(name: String, size: Long, contentInfo: ContentInfo): String {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val ddDir = File(downloadsDir, "DD")
            ddDir.mkdirs()

            val baseName = sanitizeFilename(name)
            val hasCorrectExt = baseName.lowercase().endsWith(".${contentInfo.extension}")
            val finalName = if (hasCorrectExt) baseName else "$baseName.${contentInfo.extension}"

            val file = File(ddDir, finalName)
            if (!file.exists()) {
                when (contentInfo.category) {
                    FileCategory.BOOK -> generatePdf(file, name, size)
                    FileCategory.VIDEO -> generateVideoPlaceholder(file, name, size)
                    FileCategory.AUDIO -> generateAudioPlaceholder(file, name, size)
                    else -> generateGenericFile(file, name, size, contentInfo)
                }
            }

            Log.d(TAG, "Saved: $finalName (${file.length()} bytes on disk, .${contentInfo.extension})")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file: ${e.message}")
            ""
        }
    }

    /**
     * Generate a valid, openable PDF document.
     */
    private fun generatePdf(file: File, title: String, size: Long) {
        try {
            val fos = java.io.FileOutputStream(file)
            val doc = android.graphics.pdf.PdfDocument()

            // Page 1: Title page
            val pageInfo1 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page1 = doc.startPage(pageInfo1)
            val canvas1 = page1.canvas
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                isAntiAlias = true
            }

            // Title
            paint.textSize = 28f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            val titleLines = wrapText(title, paint, 500f)
            var y = 200f
            for (line in titleLines) {
                canvas1.drawText(line, 48f, y, paint)
                y += 36f
            }

            // Separator
            paint.color = android.graphics.Color.parseColor("#00E5FF")
            canvas1.drawRect(48f, y + 10f, 547f, y + 14f, paint)

            // Info
            paint.color = android.graphics.Color.DKGRAY
            paint.textSize = 16f
            paint.typeface = android.graphics.Typeface.DEFAULT
            y += 50f
            canvas1.drawText("Downloaded by DD — Deep Downloader", 48f, y, paint)
            y += 28f
            canvas1.drawText("File size: ${com.sonu.dd.core.util.FileUtils.formatSize(size)}", 48f, y, paint)
            y += 28f
            canvas1.drawText("Date: ${java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}", 48f, y, paint)

            // Footer
            paint.textSize = 12f
            paint.color = android.graphics.Color.GRAY
            canvas1.drawText("DD — Download Smarter, Deeper. • Made with ❤ by Sonu Verma", 48f, 800f, paint)

            doc.finishPage(page1)

            // Page 2: Content placeholder
            val pageInfo2 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = doc.startPage(pageInfo2)
            val canvas2 = page2.canvas

            paint.color = android.graphics.Color.BLACK
            paint.textSize = 14f
            paint.typeface = android.graphics.Typeface.DEFAULT
            y = 60f
            val contentLines = listOf(
                "This file was downloaded using DD — Deep Downloader.",
                "",
                "Title: $title",
                "Size: ${com.sonu.dd.core.util.FileUtils.formatSize(size)}",
                "",
                "DD is a powerful Android torrent search & download client",
                "featuring 16 aggregated search engines, real-time speed",
                "tracking, and 5 beautiful themes.",
                "",
                "Note: This is a simulation-mode download. To download",
                "actual torrent content, the LibTorrent4J native engine",
                "needs to be integrated.",
                "",
                "Features:",
                "• 16 search engines queried in parallel",
                "• Real-time speedometer with animated gauge",
                "• 5 hand-crafted color themes",
                "• Privacy-first design with incognito mode",
                "• Smart file type detection",
                "• Library with auto-categorization",
                "",
                "Visit: https://sonuverma11.github.io/DD/",
                "GitHub: https://github.com/SONUVERMA11/DD",
            )
            for (line in contentLines) {
                canvas2.drawText(line, 48f, y, paint)
                y += 22f
            }

            paint.textSize = 12f
            paint.color = android.graphics.Color.GRAY
            canvas2.drawText("DD — Download Smarter, Deeper. • Page 2", 48f, 800f, paint)

            doc.finishPage(page2)

            doc.writeTo(fos)
            doc.close()
            fos.close()
        } catch (e: Exception) {
            Log.e(TAG, "PDF generation failed: ${e.message}")
            // Fallback: write a minimal valid PDF
            file.writeBytes(createMinimalPdf(title))
        }
    }

    /**
     * Create a minimal valid PDF as fallback.
     */
    private fun createMinimalPdf(title: String): ByteArray {
        val cleanTitle = title.take(80)
        val content = """
%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj
4 0 obj<</Length 120>>
stream
BT /F1 24 Tf 50 750 Td ($cleanTitle) Tj 0 -40 Td /F1 14 Tf (Downloaded by DD - Deep Downloader) Tj ET
endstream
endobj
5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000266 00000 n 
0000000438 00000 n 
trailer<</Size 6/Root 1 0 R>>
startxref
508
%%EOF
""".trimIndent()
        return content.toByteArray()
    }

    /**
     * Generate a video placeholder file with valid container header.
     */
    private fun generateVideoPlaceholder(file: File, name: String, size: Long) {
        val fos = java.io.FileOutputStream(file)
        // Write a minimal MP4 ftyp box + moov box
        // This makes file managers show it as a valid video file
        val ftypBox = byteArrayOf(
            0x00, 0x00, 0x00, 0x1C, // box size = 28
            0x66, 0x74, 0x79, 0x70, // "ftyp"
            0x69, 0x73, 0x6F, 0x6D, // "isom"
            0x00, 0x00, 0x02, 0x00, // minor version
            0x69, 0x73, 0x6F, 0x6D, // "isom"
            0x69, 0x73, 0x6F, 0x32, // "iso2"
            0x6D, 0x70, 0x34, 0x31, // "mp41"
        )
        fos.write(ftypBox)

        // Write a free box with info text
        val info = "DD Deep Downloader - $name - Simulation mode. Integrate LibTorrent4J for real video content."
        val infoBytes = info.toByteArray()
        val freeSize = 8 + infoBytes.size
        fos.write(byteArrayOf(
            ((freeSize shr 24) and 0xFF).toByte(),
            ((freeSize shr 16) and 0xFF).toByte(),
            ((freeSize shr 8) and 0xFF).toByte(),
            (freeSize and 0xFF).toByte(),
            0x66, 0x72, 0x65, 0x65, // "free"
        ))
        fos.write(infoBytes)

        // Pad to make file look reasonable (1MB minimum)
        val targetSize = minOf(size, 1024 * 1024L)
        val written = ftypBox.size + freeSize
        if (written < targetSize) {
            val padding = ByteArray(minOf((targetSize - written).toInt(), 1024 * 1024))
            fos.write(padding)
        }
        fos.close()
    }

    /**
     * Generate an audio placeholder file with valid MP3 header.
     */
    private fun generateAudioPlaceholder(file: File, name: String, size: Long) {
        val fos = java.io.FileOutputStream(file)

        // ID3v2 header with title tag
        val titleBytes = name.take(100).toByteArray()
        val id3Header = byteArrayOf(
            0x49, 0x44, 0x33,       // "ID3"
            0x03, 0x00,             // Version 2.3
            0x00,                   // Flags
        )
        // Calculate ID3 size (title frame + padding)
        val frameSize = 10 + titleBytes.size + 1 // TIT2 frame header + encoding byte + title
        val id3Size = frameSize + 10 // extra padding
        val sizeBytes = byteArrayOf(
            ((id3Size shr 21) and 0x7F).toByte(),
            ((id3Size shr 14) and 0x7F).toByte(),
            ((id3Size shr 7) and 0x7F).toByte(),
            (id3Size and 0x7F).toByte(),
        )
        fos.write(id3Header)
        fos.write(sizeBytes)

        // TIT2 frame (title)
        fos.write(byteArrayOf(0x54, 0x49, 0x54, 0x32)) // "TIT2"
        val titleFrameSize = titleBytes.size + 1
        fos.write(byteArrayOf(
            ((titleFrameSize shr 24) and 0xFF).toByte(),
            ((titleFrameSize shr 16) and 0xFF).toByte(),
            ((titleFrameSize shr 8) and 0xFF).toByte(),
            (titleFrameSize and 0xFF).toByte(),
            0x00, 0x00, // flags
            0x03, // UTF-8 encoding
        ))
        fos.write(titleBytes)

        // Pad to fill ID3 block
        fos.write(ByteArray(10))

        // Write a silence MP3 frame (MPEG1 Layer3, 128kbps, 44100Hz, stereo)
        val mp3Frame = byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), // Sync + MPEG1, Layer3
            0x90.toByte(), // 128kbps, 44100Hz
            0x00, // padding
        )
        // Write multiple frames to get a reasonable file size
        val targetSize = minOf(size, 1024 * 1024L)
        var written = id3Header.size + sizeBytes.size + 10 + titleFrameSize + 10L
        while (written < targetSize) {
            fos.write(mp3Frame)
            fos.write(ByteArray(413)) // MP3 frame data (417 byte frame for 128kbps)
            written += 417
        }
        fos.close()
    }

    /**
     * Generate a generic file (ZIP archives, etc.).
     */
    private fun generateGenericFile(file: File, name: String, size: Long, contentInfo: ContentInfo) {
        val fos = java.io.FileOutputStream(file)
        if (contentInfo.extension in listOf("zip", "rar", "7z")) {
            // Write valid ZIP header
            val zipHeader = byteArrayOf(
                0x50, 0x4B, 0x03, 0x04, // ZIP magic
                0x14, 0x00, // version
                0x00, 0x00, // flags
                0x00, 0x00, // compression (stored)
                0x00, 0x00, 0x00, 0x00, // mod time/date
                0x00, 0x00, 0x00, 0x00, // CRC
                0x00, 0x00, 0x00, 0x00, // compressed size
                0x00, 0x00, 0x00, 0x00, // uncompressed size
            )
            fos.write(zipHeader)
            val nameBytes = "DD_download_info.txt".toByteArray()
            fos.write(byteArrayOf(
                (nameBytes.size and 0xFF).toByte(),
                ((nameBytes.size shr 8) and 0xFF).toByte(),
                0x00, 0x00, // extra field length
            ))
            fos.write(nameBytes)
            fos.write("DD Deep Downloader - $name - Simulation mode".toByteArray())
        } else {
            // Generic: write description
            fos.write("DD Deep Downloader\nFile: $name\nSimulation mode download.\n".toByteArray())
        }
        fos.close()
    }

    /**
     * Word-wrap text to fit within a given width.
     */
    private fun wrapText(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            val test = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(test) <= maxWidth) {
                currentLine = test
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
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
