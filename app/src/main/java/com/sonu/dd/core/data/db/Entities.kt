package com.sonu.dd.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey val infoHash: String,
    val name: String,
    val size: Long,
    val sizeFormatted: String,
    val seeds: Int,
    val leeches: Int,
    val magnetUri: String,
    val source: String,
    val quality: String,
    val category: String,
    val uploadDate: String,
    val thumbnailUrl: String?,
    val query: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val name: String,
    val magnetUri: String,
    val totalSize: Long,
    val downloadedSize: Long,
    val progress: Float,
    val status: String,
    val source: String,
    val filePath: String,
    val selectedFormat: String,
    val addedTimestamp: Long,
    val completedTimestamp: Long? = null,
)

@Entity(tableName = "library_items")
data class LibraryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    val size: Long,
    val mimeType: String,
    val category: String,
    val format: String,
    val downloadedAt: Long,
    val thumbnailPath: String?,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
