package com.sonu.dd.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Unified torrent search result from all sources.
 */
@Serializable
data class TorrentResult(
    val name: String,
    val size: Long, // bytes
    val sizeFormatted: String,
    val seeds: Int,
    val leeches: Int,
    val infoHash: String,
    val magnetUri: String,
    val source: TorrentSource,
    val quality: String = "",
    val category: TorrentCategory = TorrentCategory.OTHER,
    val uploadDate: String = "",
    val thumbnailUrl: String? = null,
)

@Serializable
enum class TorrentSource(val displayName: String) {
    YTS("YTS"),
    X1337("1337x"),
    TPB("TPB"),
    EZTV("EZTV"),
    NYAA("Nyaa"),
    ACADEMIC("Academic"),
    TORRENT_GALAXY("TorrentGalaxy"),
    LIME_TORRENTS("LimeTorrents"),
    SOLID_TORRENTS("SolidTorrents"),
    BITSEARCH("Bitsearch"),
    KNABEN("Knaben"),
    BTDIGG("BTDigg"),
    TORRENTZ2("Torrentz2"),
    GLOTORRENTS("GloTorrents"),
    MAGNETDL("MagnetDL"),
    TORRENT_PROJECT("TorrentProject");
}

@Serializable
enum class TorrentCategory(val displayName: String, val emoji: String) {
    MOVIES("Movies", "🎬"),
    MUSIC("Music", "🎵"),
    BOOKS("Books", "📚"),
    SOFTWARE("Software", "🖥️"),
    GAMES("Games", "🎮"),
    DOCUMENTS("Docs", "📄"),
    ANIME("Anime", "🎌"),
    TV("TV Shows", "📺"),
    OTHER("Other", "📦");
}

/**
 * Health indicator derived from seed/leech ratio.
 */
enum class TorrentHealth {
    HEALTHY, OK, DEAD;

    companion object {
        fun from(seeds: Int, leeches: Int): TorrentHealth = when {
            seeds >= 10 -> HEALTHY
            seeds >= 2 -> OK
            else -> DEAD
        }
    }
}

/**
 * Active download state.
 */
data class DownloadState(
    val id: String,
    val name: String,
    val magnetUri: String,
    val totalSize: Long,
    val downloadedSize: Long,
    val progress: Float, // 0.0 - 1.0
    val downloadSpeed: Long, // bytes/s
    val uploadSpeed: Long, // bytes/s
    val seeds: Int,
    val peers: Int,
    val eta: Long, // seconds
    val status: DownloadStatus,
    val source: TorrentSource,
    val filePath: String = "",
    val selectedFormat: String = "original",
    val addedTimestamp: Long = System.currentTimeMillis(),
)

enum class DownloadStatus {
    QUEUED,
    METADATA,
    DOWNLOADING,
    PAUSED,
    SEEDING,
    CONVERTING,
    COMPLETED,
    FAILED;

    val isActive: Boolean
        get() = this == DOWNLOADING || this == METADATA || this == SEEDING

    val displayName: String
        get() = when (this) {
            QUEUED -> "Queued"
            METADATA -> "Fetching metadata…"
            DOWNLOADING -> "Downloading"
            PAUSED -> "Paused"
            SEEDING -> "Seeding"
            CONVERTING -> "Converting…"
            COMPLETED -> "Completed"
            FAILED -> "Failed"
        }
}

/**
 * Completed download / library item.
 */
data class LibraryItem(
    val id: String,
    val name: String,
    val filePath: String,
    val size: Long,
    val mimeType: String,
    val category: FileCategory,
    val format: String,
    val downloadedAt: Long,
    val thumbnailPath: String? = null,
)

enum class FileCategory(val displayName: String) {
    VIDEO("Videos"),
    AUDIO("Music"),
    BOOK("Books"),
    IMAGE("Images"),
    ARCHIVE("Archives"),
    OTHER("Other");

    companion object {
        fun fromMimeType(mime: String): FileCategory = when {
            mime.startsWith("video/") -> VIDEO
            mime.startsWith("audio/") -> AUDIO
            mime.startsWith("image/") -> IMAGE
            mime.contains("pdf") || mime.contains("epub") || mime.contains("mobi") -> BOOK
            mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar") -> ARCHIVE
            else -> OTHER
        }
    }
}
