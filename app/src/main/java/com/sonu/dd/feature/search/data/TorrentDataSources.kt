package com.sonu.dd.feature.search.data

import com.sonu.dd.core.domain.model.TorrentCategory
import com.sonu.dd.core.domain.model.TorrentResult
import com.sonu.dd.core.domain.model.TorrentSource
import com.sonu.dd.core.util.FileUtils
import com.sonu.dd.core.util.buildMagnetUri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Individual torrent source data sources.
 */

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// YTS API Data Source
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Serializable
data class YtsResponse(
    val status: String = "",
    val data: YtsData? = null
)

@Serializable
data class YtsData(
    @SerialName("movie_count") val movieCount: Int = 0,
    val movies: List<YtsMovie>? = null
)

@Serializable
data class YtsMovie(
    val title: String = "",
    val year: Int = 0,
    @SerialName("title_long") val titleLong: String = "",
    @SerialName("medium_cover_image") val coverImage: String = "",
    @SerialName("date_uploaded") val dateUploaded: String = "",
    val torrents: List<YtsTorrent>? = null
)

@Serializable
data class YtsTorrent(
    val hash: String = "",
    val quality: String = "",
    val type: String = "",
    val size: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    val seeds: Int = 0,
    val peers: Int = 0
)

@Singleton
class YtsDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val url = "https://yts.mx/api/v2/list_movies.json?query_term=$query&limit=20&sort_by=seeds"
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val ytsResponse = json.decodeFromString<YtsResponse>(body)

            ytsResponse.data?.movies?.flatMap { movie ->
                movie.torrents?.map { torrent ->
                    TorrentResult(
                        name = "${movie.titleLong} [${torrent.quality}] [${torrent.type}]",
                        size = torrent.sizeBytes,
                        sizeFormatted = torrent.size,
                        seeds = torrent.seeds,
                        leeches = torrent.peers,
                        infoHash = torrent.hash.lowercase(),
                        magnetUri = buildMagnetUri(torrent.hash, movie.title),
                        source = TorrentSource.YTS,
                        quality = torrent.quality,
                        category = TorrentCategory.MOVIES,
                        uploadDate = movie.dateUploaded,
                        thumbnailUrl = movie.coverImage
                    )
                } ?: emptyList()
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TPB (The Pirate Bay) API Data Source
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Serializable
data class TpbResult(
    val id: String = "",
    val name: String = "",
    @SerialName("info_hash") val infoHash: String = "",
    val leechers: String = "0",
    val seeders: String = "0",
    val size: String = "0",
    @SerialName("added") val added: String = "",
    val category: String = ""
)

@Singleton
class TpbDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://apibay.org/q.php?q=$encodedQuery"
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val results = json.decodeFromString<List<TpbResult>>(body)

            results
                .filter { it.name != "No results returned" && it.id != "0" }
                .map { result ->
                    val sizeBytes = result.size.toLongOrNull() ?: 0L
                    TorrentResult(
                        name = result.name,
                        size = sizeBytes,
                        sizeFormatted = FileUtils.formatSize(sizeBytes),
                        seeds = result.seeders.toIntOrNull() ?: 0,
                        leeches = result.leechers.toIntOrNull() ?: 0,
                        infoHash = result.infoHash.lowercase(),
                        magnetUri = buildMagnetUri(result.infoHash, result.name),
                        source = TorrentSource.TPB,
                        quality = FileUtils.extractQuality(result.name),
                        category = tpbCategoryMap(result.category),
                        uploadDate = result.added,
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun tpbCategoryMap(cat: String): TorrentCategory = when {
        cat.startsWith("2") -> TorrentCategory.MOVIES
        cat.startsWith("1") -> TorrentCategory.MUSIC
        cat.startsWith("6") -> TorrentCategory.BOOKS
        cat.startsWith("3") -> TorrentCategory.SOFTWARE
        cat.startsWith("4") -> TorrentCategory.GAMES
        else -> TorrentCategory.OTHER
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 1337x Scraper Data Source
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Singleton
class X1337DataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val baseUrl = "https://1337x.to"

    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$baseUrl/search/$encodedQuery/1/"
            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            val rows = doc.select("table.table-list tbody tr")
            rows.take(20).mapNotNull { row ->
                try {
                    val nameLink = row.select("td.name a:nth-child(2)") .firstOrNull() ?: return@mapNotNull null
                    val name = nameLink.text()
                    val detailUrl = baseUrl + nameLink.attr("href")
                    val seeds = row.select("td.seeds").text().toIntOrNull() ?: 0
                    val leeches = row.select("td.leeches").text().toIntOrNull() ?: 0
                    val sizeText = row.select("td.size").text().replace(Regex("\\s+\\d+$"), "")
                    val date = row.select("td.coll-date").text()

                    // Fetch magnet from detail page
                    val magnetUri = fetchMagnetFrom1337x(detailUrl) ?: return@mapNotNull null
                    val infoHash = extractInfoHash(magnetUri)

                    TorrentResult(
                        name = name,
                        size = parseSizeToBytes(sizeText),
                        sizeFormatted = sizeText,
                        seeds = seeds,
                        leeches = leeches,
                        infoHash = infoHash,
                        magnetUri = magnetUri,
                        source = TorrentSource.X1337,
                        quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.OTHER,
                        uploadDate = date,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchMagnetFrom1337x(url: String): String? {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
            doc.select("a[href^=magnet:]").firstOrNull()?.attr("href")
        } catch (e: Exception) {
            null
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// EZTV Data Source
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Serializable
data class EztvResponse(
    @SerialName("torrents_count") val torrentsCount: Int = 0,
    val torrents: List<EztvTorrent>? = null
)

@Serializable
data class EztvTorrent(
    val id: Int = 0,
    val hash: String = "",
    @SerialName("filename") val filename: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("magnet_url") val magnetUrl: String = "",
    @SerialName("size_bytes") val sizeBytes: String = "0",
    val seeds: Int = 0,
    val peers: Int = 0,
    @SerialName("date_released_unix") val dateReleased: Long = 0,
    @SerialName("small_screenshot") val screenshot: String = ""
)

@Singleton
class EztvDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            // EZTV API search by page
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://eztv.re/search/$encodedQuery")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            val rows = doc.select("table.forum_header_border tr.forum_header_border")
            rows.take(20).mapNotNull { row ->
                try {
                    val nameElement = row.select("td:nth-child(2) a").firstOrNull() ?: return@mapNotNull null
                    val name = nameElement.text()
                    val magnetLink = row.select("a.magnet[href^=magnet:]").firstOrNull()?.attr("href")
                        ?: return@mapNotNull null
                    val sizeText = row.select("td:nth-child(4)").text()
                    val seeds = row.select("td:nth-child(6) font").text().toIntOrNull() ?: 0
                    val infoHash = extractInfoHash(magnetLink)

                    TorrentResult(
                        name = name,
                        size = parseSizeToBytes(sizeText),
                        sizeFormatted = sizeText,
                        seeds = seeds,
                        leeches = 0,
                        infoHash = infoHash,
                        magnetUri = magnetLink,
                        source = TorrentSource.EZTV,
                        quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.TV,
                        uploadDate = "",
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Nyaa.si Data Source (RSS/Scraping)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Singleton
class NyaaDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://nyaa.si/?q=$encodedQuery&s=seeders&o=desc")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            val rows = doc.select("table.torrent-list tbody tr")
            rows.take(20).mapNotNull { row ->
                try {
                    val nameLink = row.select("td:nth-child(2) a:last-child")
                    val name = nameLink.text().ifEmpty { return@mapNotNull null }
                    val magnetLink = row.select("td:nth-child(3) a[href^=magnet:]").firstOrNull()
                        ?.attr("href") ?: return@mapNotNull null
                    val sizeText = row.select("td:nth-child(4)").text()
                    val seeds = row.select("td:nth-child(6)").text().toIntOrNull() ?: 0
                    val leeches = row.select("td:nth-child(7)").text().toIntOrNull() ?: 0
                    val date = row.select("td:nth-child(5)").text()
                    val infoHash = extractInfoHash(magnetLink)

                    TorrentResult(
                        name = name,
                        size = parseSizeToBytes(sizeText),
                        sizeFormatted = sizeText,
                        seeds = seeds,
                        leeches = leeches,
                        infoHash = infoHash,
                        magnetUri = magnetLink,
                        source = TorrentSource.NYAA,
                        quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.ANIME,
                        uploadDate = date,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Academic Torrents Data Source
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Singleton
class AcademicDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://academictorrents.com/browse.php?search=$encodedQuery")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            val rows = doc.select("tr.odd, tr.even")
            rows.take(20).mapNotNull { row ->
                try {
                    val nameLink = row.select("td:nth-child(2) a").firstOrNull() ?: return@mapNotNull null
                    val name = nameLink.text().ifEmpty { return@mapNotNull null }
                    val detailUrl = "https://academictorrents.com" + nameLink.attr("href")
                    val sizeText = row.select("td:nth-child(4)").text()
                    val seeds = row.select("td:nth-child(6)").text().toIntOrNull() ?: 0
                    val leeches = row.select("td:nth-child(7)").text().toIntOrNull() ?: 0

                    // Extract hash from URL
                    val hash = nameLink.attr("href").substringAfter("/details/").substringBefore("/")
                    val magnetUri = buildMagnetUri(hash, name)

                    TorrentResult(
                        name = name,
                        size = parseSizeToBytes(sizeText),
                        sizeFormatted = sizeText,
                        seeds = seeds,
                        leeches = leeches,
                        infoHash = hash.lowercase(),
                        magnetUri = magnetUri,
                        source = TorrentSource.ACADEMIC,
                        quality = "",
                        category = TorrentCategory.DOCUMENTS,
                        uploadDate = "",
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Utility functions
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private fun extractInfoHash(magnetUri: String): String {
    val regex = Regex("btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE)
    return regex.find(magnetUri)?.groupValues?.get(1)?.lowercase() ?: magnetUri.hashCode().toString()
}

private fun parseSizeToBytes(sizeText: String): Long {
    val cleaned = sizeText.trim().uppercase()
    val numberPart = cleaned.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0L
    return when {
        "TB" in cleaned -> (numberPart * 1024 * 1024 * 1024 * 1024).toLong()
        "GB" in cleaned || "GIB" in cleaned -> (numberPart * 1024 * 1024 * 1024).toLong()
        "MB" in cleaned || "MIB" in cleaned -> (numberPart * 1024 * 1024).toLong()
        "KB" in cleaned || "KIB" in cleaned -> (numberPart * 1024).toLong()
        else -> numberPart.toLong()
    }
}
