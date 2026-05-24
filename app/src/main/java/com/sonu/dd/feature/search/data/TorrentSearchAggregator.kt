package com.sonu.dd.feature.search.data

import android.util.Log
import com.sonu.dd.core.data.datastore.DDPreferences
import com.sonu.dd.core.data.db.SearchCacheDao
import com.sonu.dd.core.data.db.SearchCacheEntity
import com.sonu.dd.core.data.db.SearchHistoryDao
import com.sonu.dd.core.data.db.SearchHistoryEntity
import com.sonu.dd.core.domain.model.TorrentCategory
import com.sonu.dd.core.domain.model.TorrentResult
import com.sonu.dd.core.domain.model.TorrentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified torrent search aggregator — 16 sources.
 * Queries all enabled sources in parallel on IO dispatcher,
 * merges and deduplicates results.
 * Caches results in Room for 30-minute offline support.
 */
@Singleton
class TorrentSearchAggregator @Inject constructor(
    // Original 10
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
    // New 6
    private val knabenDataSource: KnabenDataSource,
    private val btDiggDataSource: BTDiggDataSource,
    private val torrentz2DataSource: Torrentz2DataSource,
    private val gloTorrentsDataSource: GloTorrentsDataSource,
    private val magnetDLDataSource: MagnetDLDataSource,
    private val torrentProjectDataSource: TorrentProjectDataSource,
    // DAOs & prefs
    private val searchCacheDao: SearchCacheDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val preferences: DDPreferences,
) {
    companion object {
        private const val TAG = "TorrentAggregator"
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L
    }

    suspend fun search(query: String, category: String = ""): List<TorrentResult> {
        val isIncognito = preferences.incognitoDownloadsFlow.first()
        if (!isIncognito) {
            searchHistoryDao.insertSearch(SearchHistoryEntity(query = query))
        }

        val minTime = System.currentTimeMillis() - CACHE_DURATION_MS
        val cached = searchCacheDao.getCachedResults(query, minTime)
        if (cached.isNotEmpty()) {
            Log.d(TAG, "Returning ${cached.size} cached results for '$query'")
            return cached.map { it.toDomainModel() }
        }

        searchCacheDao.clearExpired(minTime)

        val results = withContext(Dispatchers.IO) { fetchFromSources(query) }
        Log.d(TAG, "Fetched ${results.size} results for '$query'")

        if (results.isNotEmpty()) {
            searchCacheDao.insertAll(results.map { it.toCacheEntity(query) })
        }

        return results
    }

    private suspend fun fetchFromSources(query: String): List<TorrentResult> = coroutineScope {
        val enabledSources = getEnabledSources()
        Log.d(TAG, "Searching ${enabledSources.size} sources: ${enabledSources.joinToString()}")

        // Map each source to its data source search function
        data class SourceJob(val source: TorrentSource, val search: suspend () -> List<TorrentResult>)

        val jobs = listOf(
            SourceJob(TorrentSource.YTS) { ytsDataSource.search(query) },
            SourceJob(TorrentSource.TPB) { tpbDataSource.search(query) },
            SourceJob(TorrentSource.X1337) { x1337DataSource.search(query) },
            SourceJob(TorrentSource.EZTV) { eztvDataSource.search(query) },
            SourceJob(TorrentSource.NYAA) { nyaaDataSource.search(query) },
            SourceJob(TorrentSource.ACADEMIC) { academicDataSource.search(query) },
            SourceJob(TorrentSource.TORRENT_GALAXY) { torrentGalaxyDataSource.search(query) },
            SourceJob(TorrentSource.LIME_TORRENTS) { limeTorrentsDataSource.search(query) },
            SourceJob(TorrentSource.SOLID_TORRENTS) { solidTorrentsDataSource.search(query) },
            SourceJob(TorrentSource.BITSEARCH) { bitsearchDataSource.search(query) },
            SourceJob(TorrentSource.KNABEN) { knabenDataSource.search(query) },
            SourceJob(TorrentSource.BTDIGG) { btDiggDataSource.search(query) },
            SourceJob(TorrentSource.TORRENTZ2) { torrentz2DataSource.search(query) },
            SourceJob(TorrentSource.GLOTORRENTS) { gloTorrentsDataSource.search(query) },
            SourceJob(TorrentSource.MAGNETDL) { magnetDLDataSource.search(query) },
            SourceJob(TorrentSource.TORRENT_PROJECT) { torrentProjectDataSource.search(query) },
        )

        val deferreds = jobs
            .filter { it.source in enabledSources }
            .map { job ->
                async(Dispatchers.IO) {
                    runCatching { job.search() }
                        .onSuccess { Log.d(TAG, "${job.source.displayName}: ${it.size} results") }
                        .onFailure { Log.w(TAG, "${job.source.displayName} failed: ${it.message}") }
                        .getOrDefault(emptyList())
                }
            }

        val allResults = deferreds.flatMap { it.await() }
        Log.d(TAG, "Raw results before dedup: ${allResults.size}")

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
        // New sources — enabled by default (no preference toggle yet)
        sources.add(TorrentSource.KNABEN)
        sources.add(TorrentSource.BTDIGG)
        sources.add(TorrentSource.TORRENTZ2)
        sources.add(TorrentSource.GLOTORRENTS)
        sources.add(TorrentSource.MAGNETDL)
        sources.add(TorrentSource.TORRENT_PROJECT)
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
    source = try { TorrentSource.valueOf(source) } catch (_: Exception) { TorrentSource.TPB },
    quality = quality,
    category = try { TorrentCategory.valueOf(category) } catch (_: Exception) { TorrentCategory.OTHER },
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
