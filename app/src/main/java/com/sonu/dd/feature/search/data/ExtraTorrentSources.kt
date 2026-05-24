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

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Knaben Database (JSON API)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Serializable
data class KnabenHit(
    val title: String = "",
    @SerialName("infoHash") val infoHash: String = "",
    val bytes: Long = 0,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val category: String = ""
)

@Singleton
class KnabenDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://knaben.eu/api/v1/search?q=$encoded&sort=seeders&order=desc&limit=20")
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val hits = try {
                json.decodeFromString<List<KnabenHit>>(body)
            } catch (_: Exception) { emptyList() }

            hits.map { h ->
                TorrentResult(
                    name = h.title, size = h.bytes,
                    sizeFormatted = FileUtils.formatSize(h.bytes),
                    seeds = h.seeders, leeches = h.leechers,
                    infoHash = h.infoHash.lowercase(),
                    magnetUri = buildMagnetUri(h.infoHash, h.title),
                    source = TorrentSource.KNABEN,
                    quality = FileUtils.extractQuality(h.title),
                    category = TorrentCategory.OTHER
                )
            }
        } catch (e: Exception) { emptyList() }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// BTDigg DHT Search
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Singleton
class BTDiggDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://btdig.com/search?q=$encoded&order=0")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000).get()

            doc.select("div.one_result").take(20).mapNotNull { row ->
                try {
                    val nameEl = row.select("div.torrent_name a").firstOrNull() ?: return@mapNotNull null
                    val name = nameEl.text()
                    val magnetEl = row.select("a[href^=magnet:]").firstOrNull()
                    val magnet = magnetEl?.attr("href") ?: return@mapNotNull null
                    val sizeText = row.select("span.torrent_size").text()
                    val hash = extractInfoHashFromMagnet(magnet)

                    TorrentResult(
                        name = name, size = parseSizeStr(sizeText),
                        sizeFormatted = sizeText,
                        seeds = 0, leeches = 0,
                        infoHash = hash, magnetUri = magnet,
                        source = TorrentSource.BTDIGG,
                        quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.OTHER
                    )
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Torrentz2 Meta-search
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Singleton
class Torrentz2DataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://torrentz2.nz/search?q=$encoded")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000).get()

            doc.select("div.results dl").take(20).mapNotNull { row ->
                try {
                    val nameEl = row.select("dt a").firstOrNull() ?: return@mapNotNull null
                    val name = nameEl.text()
                    val href = nameEl.attr("href")
                    val hash = href.replace("/", "").take(40)
                    val spans = row.select("dd span")
                    val sizeText = spans.getOrNull(2)?.text() ?: ""
                    val seeds = spans.getOrNull(3)?.text()?.replace(",", "")?.toIntOrNull() ?: 0
                    val leeches = spans.getOrNull(4)?.text()?.replace(",", "")?.toIntOrNull() ?: 0

                    TorrentResult(
                        name = name, size = parseSizeStr(sizeText),
                        sizeFormatted = sizeText, seeds = seeds, leeches = leeches,
                        infoHash = hash.lowercase(),
                        magnetUri = buildMagnetUri(hash, name),
                        source = TorrentSource.TORRENTZ2,
                        quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.OTHER
                    )
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// GloTorrents Scraper
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Singleton
class GloTorrentsDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://www.glotorrents.com/search_results.php?search=$encoded&sort=seeders&order=desc")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000).get()

            doc.select("table tr").drop(1).take(20).mapNotNull { row ->
                try {
                    val cells = row.select("td")
                    if (cells.size < 5) return@mapNotNull null
                    val nameLink = cells[1].select("a").firstOrNull() ?: return@mapNotNull null
                    val name = nameLink.text()
                    val magnetLink = row.select("a[href^=magnet:]").firstOrNull() ?: return@mapNotNull null
                    val magnet = magnetLink.attr("href")
                    val sizeText = cells[3].text()
                    val seeds = cells[4].text().replace(",", "").toIntOrNull() ?: 0
                    val leeches = cells[5].text().replace(",", "").toIntOrNull() ?: 0
                    val hash = extractInfoHashFromMagnet(magnet)

                    TorrentResult(
                        name = name, size = parseSizeStr(sizeText),
                        sizeFormatted = sizeText, seeds = seeds, leeches = leeches,
                        infoHash = hash, magnetUri = magnet,
                        source = TorrentSource.GLOTORRENTS,
                        quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.OTHER
                    )
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MagnetDL Scraper
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Singleton
class MagnetDLDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val cleaned = query.replace(" ", "-").lowercase()
            val firstChar = cleaned.firstOrNull() ?: return emptyList()
            val doc = Jsoup.connect("https://www.magnetdl.com/$firstChar/$cleaned/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000).get()

            doc.select("table.download tbody tr").take(20).mapNotNull { row ->
                try {
                    val cells = row.select("td")
                    if (cells.size < 6) return@mapNotNull null
                    val magnetLink = cells[0].select("a[href^=magnet:]").firstOrNull() ?: return@mapNotNull null
                    val magnet = magnetLink.attr("href")
                    val name = cells[1].select("a").text().ifEmpty { return@mapNotNull null }
                    val sizeText = cells[5].text()
                    val seeds = cells[6].text().replace(",", "").toIntOrNull() ?: 0
                    val leeches = cells[7].text().replace(",", "").toIntOrNull() ?: 0
                    val hash = extractInfoHashFromMagnet(magnet)

                    TorrentResult(
                        name = name, size = parseSizeStr(sizeText),
                        sizeFormatted = sizeText, seeds = seeds, leeches = leeches,
                        infoHash = hash, magnetUri = magnet,
                        source = TorrentSource.MAGNETDL,
                        quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.OTHER
                    )
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TorrentProject Scraper
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Singleton
class TorrentProjectDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://torrentproject.cc/?t=$encoded&orderby=seeders")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000).get()

            doc.select("div#similarfiles div.torrent").take(20).mapNotNull { row ->
                try {
                    val nameEl = row.select("a.tt").firstOrNull() ?: return@mapNotNull null
                    val name = nameEl.text()
                    val href = nameEl.attr("href")
                    val hash = href.split("/").find { it.length == 40 } ?: ""
                    val sizeText = row.select("span.sz").text()
                    val seeds = row.select("span.seed").text().replace(",", "").toIntOrNull() ?: 0
                    val leeches = row.select("span.leech").text().replace(",", "").toIntOrNull() ?: 0

                    TorrentResult(
                        name = name, size = parseSizeStr(sizeText),
                        sizeFormatted = sizeText, seeds = seeds, leeches = leeches,
                        infoHash = hash.lowercase(),
                        magnetUri = if (hash.length == 40) buildMagnetUri(hash, name) else "",
                        source = TorrentSource.TORRENT_PROJECT,
                        quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.OTHER
                    )
                } catch (_: Exception) { null }
            }.filter { it.magnetUri.isNotEmpty() }
        } catch (e: Exception) { emptyList() }
    }
}
