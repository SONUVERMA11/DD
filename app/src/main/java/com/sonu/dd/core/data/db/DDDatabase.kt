package com.sonu.dd.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SearchCacheEntity::class,
        DownloadEntity::class,
        LibraryItemEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class DDDatabase : RoomDatabase() {
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun downloadDao(): DownloadDao
    abstract fun libraryDao(): LibraryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
