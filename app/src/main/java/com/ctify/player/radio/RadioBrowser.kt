package com.ctify.player.radio

import com.ctify.player.integration.HttpStatusException
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkBlockedException
import com.ctify.player.net.NetworkGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress

data class TagCount(val name: String, val count: Int)
data class CountryCount(val code: String, val name: String, val count: Int)

data class StationQuery(
    val name: String? = null,
    /** exact Radio Browser tags; a station matching any of them is returned */
    val tags: List<String> = emptyList(),
    val countryCode: String? = null,
    val state: String? = null,
    val sort: StationSort = StationSort.VOTES,
    val minBitrate: Int = 0,
    val hideBroken: Boolean = true,
    val limit: Int = 60,
    val offset: Int = 0,
)

/**
 * Client for the community directory at radio-browser.info. Finds a live mirror
 * through DNS, keeps using it, and moves to another one when it stops answering.
 */
class RadioBrowser {
    private val fallbacks = listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info", "fi1.api.radio-browser.info")
    @Volatile private var pinned: String? = null
    @Volatile private var discovered: List<String>? = null

    suspend fun search(q: StationQuery): List<Station> {
        if (q.tags.size > 1) {
            // the API matches one tag per call; merge the families' tags and keep the requested order
            val perTag = (q.limit / q.tags.size).coerceAtLeast(15)
            val merged = q.tags.flatMap { tag -> runCatching { search(q.copy(tags = listOf(tag), limit = perTag)) }.getOrDefault(emptyList()) }
                .distinctBy { it.uuid }
            return sortLocal(merged, q.sort).take(q.limit)
        }
        val params = buildList {
            add("order" to q.sort.apiOrder)
            add("reverse" to q.sort.reverse.toString())
            add("hidebroken" to q.hideBroken.toString())
            add("limit" to q.limit.toString())
            add("offset" to q.offset.toString())
            q.name?.takeIf { it.isNotBlank() }?.let { add("name" to it) }
            q.tags.firstOrNull()?.let { add("tag" to it); add("tagExact" to "true") }
            q.countryCode?.let { add("countrycode" to it) }
            q.state?.let { add("state" to it); add("stateExact" to "true") }
            if (q.minBitrate > 0) add("bitrateMin" to q.minBitrate.toString())
        }
        val arr = JSONArray(get("/json/stations/search", params))
        return (0 until arr.length()).mapNotNull { parseStation(arr.getJSONObject(it)) }
    }

    suspend fun tags(limit: Int = 500): List<TagCount> {
        val arr = JSONArray(get("/json/tags", listOf("order" to "stationcount", "reverse" to "true", "hidebroken" to "true", "limit" to "$limit")))
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> TagCount(o.getString("name"), o.optInt("stationcount")) } }
            .filter { it.name.isNotBlank() && it.count > 0 }
    }

    suspend fun countries(): List<CountryCount> {
        val arr = JSONArray(get("/json/countries", listOf("order" to "stationcount", "reverse" to "true", "hidebroken" to "true")))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { CountryCount(it.optString("iso_3166_1"), it.optString("name"), it.optInt("stationcount")) }
            .filter { it.code.length == 2 && it.count > 0 }
    }

    suspend fun states(countryName: String): List<TagCount> {
        val arr = JSONArray(get("/json/states/$countryName", listOf("order" to "stationcount", "reverse" to "true", "hidebroken" to "true")))
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> TagCount(o.getString("name"), o.optInt("stationcount")) } }
            .filter { it.count > 0 }
    }

    suspend fun byUuid(uuid: String): Station? {
        val arr = JSONArray(get("/json/stations/byuuid/$uuid", emptyList()))
        return if (arr.length() > 0) parseStation(arr.getJSONObject(0)) else null
    }

    /** Tells the directory a station was played; it uses this for its "most listened" order. */
    suspend fun reportClick(uuid: String) {
        runCatching { get("/json/url/$uuid", emptyList()) }
    }

    private suspend fun get(path: String, params: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        // checked before the DNS lookup too, so offline mode really sends nothing
        NetworkGate.blockReason(Feature.RADIO)?.let { throw NetworkBlockedException(Feature.RADIO, it) }
        var lastError: Exception? = null
        for (host in candidates()) {
            val url = HttpUrl.Builder().scheme("https").host(host).encodedPath(path)
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            try {
                NetworkGate.client.newCall(NetworkGate.request(url.toString(), Feature.RADIO).build()).execute().use { resp ->
                    if (resp.code == 429 || resp.code >= 500) throw HttpStatusException(resp.code, "Radio Browser $host: HTTP ${resp.code}")
                    if (!resp.isSuccessful) throw HttpStatusException(resp.code, "HTTP ${resp.code}")
                    pinned = host
                    return@withContext resp.body.string()
                }
            } catch (e: NetworkBlockedException) {
                throw e
            } catch (e: HttpStatusException) {
                if (e.status in 400..499 && e.status != 429) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
            if (pinned == host) pinned = null
        }
        throw lastError ?: IllegalStateException("No Radio Browser server answered")
    }

    /** The pinned mirror first, then the ones DNS knows about, then a fixed list. */
    private fun candidates(): List<String> {
        val found = discovered ?: runCatching {
            InetAddress.getAllByName("all.api.radio-browser.info")
                .mapNotNull { a -> a.canonicalHostName.takeIf { it.endsWith(".api.radio-browser.info") } }
                .distinct().shuffled()
        }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) discovered = it }
        return (listOfNotNull(pinned) + found + fallbacks).distinct()
    }

    private fun parseStation(o: JSONObject): Station? {
        val url = o.optString("url_resolved").ifBlank { o.optString("url") }
        if (!RadioUrlPolicy.isAllowed(url)) return null
        return Station(
            uuid = o.optString("stationuuid").ifBlank { return null },
            name = o.optString("name").trim().ifBlank { return null },
            streamUrl = url,
            homepage = o.optString("homepage").takeIf { RadioUrlPolicy.isAllowed(it) },
            favicon = o.optString("favicon").takeIf { RadioUrlPolicy.isAllowed(it) },
            tags = o.optString("tags").lowercase(),
            countryCode = o.optString("countrycode").takeIf { it.length == 2 },
            state = o.optString("state").takeIf { it.isNotBlank() },
            codec = o.optString("codec").takeIf { it.isNotBlank() },
            bitrate = o.optInt("bitrate"),
            hls = o.optInt("hls") == 1,
            votes = o.optInt("votes"),
            clickCount = o.optInt("clickcount"),
            clickTrend = o.optInt("clicktrend"),
        )
    }

    private fun sortLocal(list: List<Station>, sort: StationSort) = when (sort) {
        StationSort.VOTES -> list.sortedByDescending { it.votes }
        StationSort.CLICKS -> list.sortedByDescending { it.clickCount }
        StationSort.TREND -> list.sortedByDescending { it.clickTrend }
        StationSort.NAME -> list.sortedBy { it.name.lowercase() }
        StationSort.RANDOM -> list.shuffled()
    }
}
