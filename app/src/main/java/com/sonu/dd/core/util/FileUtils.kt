package com.sonu.dd.core.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formats byte sizes into human-readable strings.
 */
object FileUtils {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            .coerceIn(0, units.lastIndex)
        val df = DecimalFormat("#,##0.##")
        return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> {
                val kb = bytesPerSecond / 1024.0
                "${DecimalFormat("#0.#").format(kb)} KB/s"
            }
            else -> {
                val mb = bytesPerSecond / (1024.0 * 1024.0)
                "${DecimalFormat("#0.##").format(mb)} MB/s"
            }
        }
    }

    fun formatSpeedMbps(bytesPerSecond: Long): Double {
        return bytesPerSecond / (1024.0 * 1024.0)
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "∞"
        val hours = TimeUnit.SECONDS.toHours(seconds)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val secs = seconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "epub" -> "application/epub+zip"
            "mobi" -> "application/x-mobipocket-ebook"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            else -> "application/octet-stream"
        }
    }

    /**
     * Extracts quality info from filename (e.g., "4K", "1080p", "720p", "FLAC").
     */
    fun extractQuality(name: String): String {
        val upper = name.uppercase()
        return when {
            "2160P" in upper || "4K" in upper || "UHD" in upper -> "4K"
            "1080P" in upper || "FULLHD" in upper || "FHD" in upper -> "1080p"
            "720P" in upper || "HD" in upper -> "720p"
            "480P" in upper || "SD" in upper -> "480p"
            "FLAC" in upper -> "FLAC"
            "MP3" in upper && "320" in upper -> "MP3 320"
            "EPUB" in upper -> "EPUB"
            "PDF" in upper -> "PDF"
            else -> ""
        }
    }
}

/**
 * Generates a magnet URI from an info hash.
 */
fun buildMagnetUri(infoHash: String, name: String): String {
    val trackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
    )
    val trackerParams = trackers.joinToString("") { "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}" }
    val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
    return "magnet:?xt=urn:btih:$infoHash&dn=$encodedName$trackerParams"
}
