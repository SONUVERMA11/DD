package com.sonu.dd.feature.search.data

import com.sonu.dd.core.data.datastore.DDPreferences
import com.sonu.dd.core.data.db.SearchCacheDao
import com.sonu.dd.core.data.db.SearchCacheEntity
import com.sonu.dd.core.data.db.SearchHistoryDao
import com.sonu.dd.core.data.db.SearchHistoryEntity
import com.sonu.dd.core.domain.model.TorrentCategory
import com.sonu.dd.core.domain.model.TorrentResult
import com.sonu.dd.core.domain.model.TorrentSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified torrent search aggregator.
 * Queries all enabled sources in parallel, merges and deduplicates results.
 * Caches results in Room for 30-minute offline support.
 */
@Singleton
class TorrentSearchAggregator @Inject constructor(
    private val ytsDataSource: YtsDataSource,
    private val tpbDataSource: TpbDataSource,
    private val x1337DataSource: X1337DataSource,
    private val eztvDataSource: EztvDataSource,
    private val nyaaDataSource: NyaaDataSource,
    private val academicDataSource: AcademicDataSource,
    private val torrentGalaxyDataSource: TorrentGalaxyDataSource,
    private val limeTorrentsDataSource: LimeTorrentsDataSource,
    private val solidTorrentsDataSource: SolidTorrentsDataSource,
    private val bitsearchDataSource: BitsearchDataSource,
    private val searchCacheDao: SearchCacheDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val preferences: DDPreferences,
) {
    companion object {
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes
    }

    /**
     * Search all enabled sources in parallel.
     * Returns cached results if available and fresh, otherwise fetches new.
     */
    suspend fun search(query: String, category: String = ""): List<TorrentResult> {
        // Save to search history (unless incognito)
        val isIncognito = preferences.incognitoDownloadsFlow.first()
        if (!isIncognito) {
            searchHistoryDao.insertSearch(SearchHistoryEntity(query = query))
        }

        // Check cache first
        val minTime = System.currentTimeMillis() - CACHE_DURATION_MS
        val cached = searchCacheDao.getCachedResults(query, minTime)
        if (cached.isNotEmpty()) {
            return cached.map { it.toDomainModel() }
        }

        // Clear expired cache entries
        searchCacheDao.clearExpired(minTime)

        // Fetch from all sources in parallel
        val results = fetchFromSources(query)

        // Cache results
        val cacheEntities = results.map { it.toCacheEntity(query) }
        searchCacheDao.insertAll(cacheEntities)

        return results
    }

    private suspend fun fetchFromSources(query: String): List<TorrentResult> = coroutineScope {
        val enabledSources = getEnabledSources()

        val deferreds = buildList {
            if (TorrentSource.YTS in enabledSources) {
                add(async { runCatching { ytsDataSource.search(query) }.getOrDefault(emptyList()) })
            }
            if (TorrentSource.TPB in enabledSources) {
                add(async { runCatching { tpbDataSource.search(query) }.getOrDefault(emptyList()) })
            }
            if (TorrentSource.X1337 in enabledSources) {
                add(async { runCatching { x1337DataSource.search(query) }.getOrDefault(emptyList()) })
            }
            if (TorrentSource.EZTV in enabledSources) {
                add(async { runCatching { eztvDataSource.search(query) }.getOrDefault(emptyList()) })
            }
            if (TorrentSource.NYAA in enabledSources) {
                add(async { runCatching { nyaaDataSource.search(query) }.getOrDefault(emptyList()) })
            }
            if (TorrentSource.ACADEMIC in enabledSources) {
                add(async { runCatching { academicDataSource.search(query) }.getOrDefault(emptyList()) })
            }
            if (TorrentSource.TORRENT_GALAXY in enabledSources) {
                add(async { runCatching { torrentGalaxyDataSource.search(query) }.getOrDefault(emptyList()) })
            }
            if (TorrentSource.LIME_TORRENTS in enabledSources) {
                add(async { runCatching { limeTorrentsDataSource.search(query) }.getOrDefault(emptyList()) })
            }
            if (TorrentSource.SOLID_TORRENTS in enabledSources) {
                add(async { runCatching { solidTorrentsDataSource.search(query) }.getOrDefault(emptyList()) })
            }
            if (TorrentSource.BITSEARCH in enabledSources) {
                add(async { runCatching { bitsearchDataSource.search(query) }.getOrDefault(emptyList()) })
            }
        }

        val allResults = deferreds.flatMap { it.await() }

        // Deduplicate by infoHash, sort by seeds descending
        allResults
            .distinctBy { it.infoHash }
            .sortedByDescending { it.seeds }
    }

    private suspend fun getEnabledSources(): Set<TorrentSource> {
        val sources = mutableSetOf<TorrentSource>()
        if (preferences.sourceYtsFlow.first()) sources.add(TorrentSource.YTS)
        if (preferences.source1337xFlow.first()) sources.add(TorrentSource.X1337)
        if (preferences.sourceTpbFlow.first()) sources.add(TorrentSource.TPB)
        if (preferences.sourceEztvFlow.first()) sources.add(TorrentSource.EZTV)
        if (preferences.sourceNyaaFlow.first()) sources.add(TorrentSource.NYAA)
        if (preferences.sourceAcademicFlow.first()) sources.add(TorrentSource.ACADEMIC)
        if (preferences.sourceTorrentGalaxyFlow.first()) sources.add(TorrentSource.TORRENT_GALAXY)
        if (preferences.sourceLimeTorrentsFlow.first()) sources.add(TorrentSource.LIME_TORRENTS)
        if (preferences.sourceSolidTorrentsFlow.first()) sources.add(TorrentSource.SOLID_TORRENTS)
        if (preferences.sourceBitsearchFlow.first()) sources.add(TorrentSource.BITSEARCH)
        return sources
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mapper extensions
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private fun SearchCacheEntity.toDomainModel(): TorrentResult = TorrentResult(
    name = name,
    size = size,
    sizeFormatted = sizeFormatted,
    seeds = seeds,
    leeches = leeches,
    infoHash = infoHash,
    magnetUri = magnetUri,
    source = TorrentSource.valueOf(source),
    quality = quality,
    category = TorrentCategory.valueOf(category),
    uploadDate = uploadDate,
    thumbnailUrl = thumbnailUrl,
)

private fun TorrentResult.toCacheEntity(query: String): SearchCacheEntity = SearchCacheEntity(
    infoHash = infoHash,
    name = name,
    size = size,
    sizeFormatted = sizeFormatted,
    seeds = seeds,
    leeches = leeches,
    magnetUri = magnetUri,
    source = source.name,
    quality = quality,
    category = category.name,
    uploadDate = uploadDate,
    thumbnailUrl = thumbnailUrl,
    query = query,
)
