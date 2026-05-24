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
// TorrentGalaxy Scraper
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Singleton
class TorrentGalaxyDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://torrentgalaxy.to/torrents.php?search=$encoded&sort=seeders&order=desc")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000).get()

            doc.select("div.tgxtablerow").take(20).mapNotNull { row ->
                try {
                    val nameEl = row.select("div.tgxtablecell a.txlight b").firstOrNull() ?: return@mapNotNull null
                    val name = nameEl.text()
                    val magnetEl = row.select("a[href^=magnet:]").firstOrNull() ?: return@mapNotNull null
                    val magnet = magnetEl.attr("href")
                    val sizeText = row.select("div.tgxtablecell span.badge-secondary").text()
                    val seedsText = row.select("div.tgxtablecell span[title=Seeders/Leechers] font[color=green]").text()
                    val leechText = row.select("div.tgxtablecell span[title=Seeders/Leechers] font[color=#ff0000]").text()
                    val hash = extractInfoHashFromMagnet(magnet)

                    TorrentResult(
                        name = name, size = parseSizeStr(sizeText), sizeFormatted = sizeText,
                        seeds = seedsText.toIntOrNull() ?: 0, leeches = leechText.toIntOrNull() ?: 0,
                        infoHash = hash, magnetUri = magnet, source = TorrentSource.TORRENT_GALAXY,
                        quality = FileUtils.extractQuality(name), category = TorrentCategory.OTHER
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LimeTorrents Scraper
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Singleton
class LimeTorrentsDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://www.limetorrents.lol/search/all/$encoded/seeds/1/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000).get()

            doc.select("table.table2 tr.table2ta").take(20).mapNotNull { row ->
                try {
                    val nameLink = row.select("td.tdleft div.tt-name a.cspider_blue14").firstOrNull() ?: return@mapNotNull null
                    val name = nameLink.text()
                    val detailUrl = "https://www.limetorrents.lol" + nameLink.attr("href")
                    val sizeText = row.select("td.tdnormal:nth-child(3)").text()
                    val seeds = row.select("td.tdseed").text().replace(",", "").toIntOrNull() ?: 0
                    val leeches = row.select("td.tdleech").text().replace(",", "").toIntOrNull() ?: 0

                    // Get magnet from detail page
                    val magnet = try {
                        val detailDoc = Jsoup.connect(detailUrl).userAgent("Mozilla/5.0").timeout(10000).get()
                        detailDoc.select("a.cspider_magnet[href^=magnet:]").firstOrNull()?.attr("href")
                    } catch (e: Exception) { null } ?: return@mapNotNull null

                    val hash = extractInfoHashFromMagnet(magnet)
                    TorrentResult(
                        name = name, size = parseSizeStr(sizeText), sizeFormatted = sizeText,
                        seeds = seeds, leeches = leeches, infoHash = hash, magnetUri = magnet,
                        source = TorrentSource.LIME_TORRENTS, quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.OTHER
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SolidTorrents API
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Serializable
data class SolidTorrentsResponse(val results: List<SolidTorrent> = emptyList())
@Serializable
data class SolidTorrent(
    val title: String = "", @SerialName("magnet") val magnetUri: String = "",
    @SerialName("infohash") val infoHash: String = "", val size: Long = 0,
    val seeders: Int = 0, val leechers: Int = 0, val category: String = ""
)

@Singleton
class SolidTorrentsDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient, private val json: Json
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder().url("https://solidtorrents.to/api/v1/search?q=$encoded&sort=seeders").build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val parsed = json.decodeFromString<SolidTorrentsResponse>(body)

            parsed.results.map { t ->
                TorrentResult(
                    name = t.title, size = t.size, sizeFormatted = FileUtils.formatSize(t.size),
                    seeds = t.seeders, leeches = t.leechers, infoHash = t.infoHash.lowercase(),
                    magnetUri = t.magnetUri.ifEmpty { buildMagnetUri(t.infoHash, t.title) },
                    source = TorrentSource.SOLID_TORRENTS, quality = FileUtils.extractQuality(t.title),
                    category = TorrentCategory.OTHER
                )
            }
        } catch (e: Exception) { emptyList() }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Bitsearch API
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Singleton
class BitsearchDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun search(query: String): List<TorrentResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("https://bitsearch.to/search?q=$encoded&sort=seeders")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000).get()

            doc.select("li.search-result").take(20).mapNotNull { item ->
                try {
                    val name = item.select("h5.title a").text().ifEmpty { return@mapNotNull null }
                    val magnet = item.select("a.dl-magnet[href^=magnet:]").firstOrNull()?.attr("href") ?: return@mapNotNull null
                    val stats = item.select("div.stats div span")
                    val sizeText = stats.getOrNull(1)?.text() ?: ""
                    val seeds = stats.getOrNull(2)?.text()?.toIntOrNull() ?: 0
                    val leeches = stats.getOrNull(3)?.text()?.toIntOrNull() ?: 0
                    val hash = extractInfoHashFromMagnet(magnet)

                    TorrentResult(
                        name = name, size = parseSizeStr(sizeText), sizeFormatted = sizeText,
                        seeds = seeds, leeches = leeches, infoHash = hash, magnetUri = magnet,
                        source = TorrentSource.BITSEARCH, quality = FileUtils.extractQuality(name),
                        category = TorrentCategory.OTHER
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Utility functions
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
internal fun extractInfoHashFromMagnet(magnetUri: String): String {
    val regex = Regex("btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE)
    return regex.find(magnetUri)?.groupValues?.get(1)?.lowercase() ?: magnetUri.hashCode().toString()
}

internal fun parseSizeStr(sizeText: String): Long {
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
