package com.ctify.player.youtube

import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A mood or genre button on YouTube Music's browse page. */
data class YtCategory(val title: String, val browseId: String, val params: String?)

/** A playlist or album card; [id] is a playlist id (no VL prefix). */
data class YtPlaylistRef(val id: String, val title: String, val subtitle: String, val thumbnail: String?)

/** YouTube Music browse pages (moods, genres, charts) read straight from InnerTube as WEB_REMIX. */
internal object YtBrowse {
    private const val CLIENT_VERSION = "1.20260804.16.00"

    suspend fun browse(browseId: String, params: String? = null): JSONObject {
        val session = runCatching { YtSession.current() }.getOrNull()
        val client = JSONObject()
            .put("clientName", "WEB_REMIX").put("clientVersion", CLIENT_VERSION)
            .put("hl", YtRuntime.hl).put("gl", YtRuntime.gl)
        session?.visitorData?.takeIf { it.isNotBlank() }?.let { client.put("visitorData", it) }
        val body = JSONObject().put("context", JSONObject().put("client", client)).put("browseId", browseId)
        params?.let { body.put("params", it) }
        val req = NetworkGate.request("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", Feature.YOUTUBE)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", GateDownloader.WEB_USER_AGENT)
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", CLIENT_VERSION)
            .apply { session?.visitorData?.takeIf { it.isNotBlank() }?.let { header("X-Goog-Visitor-Id", it) } }
            .build()
        val text = NetworkGate.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw YtHttpException(r.code, "browse $browseId HTTP ${r.code}")
            r.body.string()
        }
        return try { JSONObject(text) } catch (e: JSONException) { throw YtParseException("browse $browseId is not JSON", e) }
    }

    fun categories(root: JSONObject): List<YtCategory> = collect(root, "musicNavigationButtonRenderer").mapNotNull { b ->
        val ep = b.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint") ?: return@mapNotNull null
        val title = text(b.optJSONObject("buttonText")) ?: return@mapNotNull null
        YtCategory(title, ep.optString("browseId"), ep.optString("params").takeIf { it.isNotBlank() })
    }.distinctBy { it.title }

    fun playlists(root: JSONObject): List<YtPlaylistRef> = collect(root, "musicTwoRowItemRenderer").mapNotNull { r ->
        val browseId = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
        if (!browseId.startsWith("VL")) return@mapNotNull null
        YtPlaylistRef(
            id = browseId.removePrefix("VL"),
            title = text(r.optJSONObject("title")) ?: return@mapNotNull null,
            subtitle = text(r.optJSONObject("subtitle")).orEmpty(),
            thumbnail = thumb(r),
        )
    }.distinctBy { it.id }

    /** Every object under a key named [key], depth first. */
    private fun collect(node: Any?, key: String, out: MutableList<JSONObject> = ArrayList()): List<JSONObject> {
        when (node) {
            is JSONObject -> node.keys().forEach { k ->
                val v = node.opt(k)
                if (k == key && v is JSONObject) out += v else collect(v, key, out)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
        return out
    }

    private fun text(o: JSONObject?): String? {
        if (o == null) return null
        o.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return null
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }.takeIf { it.isNotBlank() }
    }

    private fun thumb(o: JSONObject): String? {
        val arr = findArray(o, "thumbnails") ?: return null
        return arr.optJSONObject(arr.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
    }

    private fun findArray(node: Any?, key: String): JSONArray? {
        when (node) {
            is JSONObject -> node.keys().forEach { k ->
                val v = node.opt(k)
                if (k == key && v is JSONArray) return v
                findArray(v, key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findArray(node.opt(i), key)?.let { return it }
        }
        return null
    }
}
