// Adapted from Levyra (GPL-3.0), app/src/main/java/com/luc4n3x/levyra/data/PlaybackResolver.kt (Android Reel audio strategy)
package com.ctify.player.youtube

import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom

/** The ANDROID client through reel_item_watch; Levyra's first song strategy, no PO token needed. */
internal object YtReel {
    const val CLIENT_VERSION = "21.03.36"
    private val random = SecureRandom()
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val JSON = "application/json".toMediaType()

    private val userAgent get() = "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android 15; ${YtRuntime.gl}) gzip"

    fun resolve(videoId: String): YtStream {
        val visitorData = visitor()
        val cpn = nonce(16)
        val body = JSONObject()
            .put("context", context(visitorData))
            .put("playerRequest", JSONObject().put("videoId", videoId).put("cpn", cpn).put("contentCheckOk", true).put("racyCheckOk", true))
            .put("disablePlayerResponse", false)
        val url = "https://youtubei.googleapis.com/youtubei/v1/reel/reel_item_watch?prettyPrint=false&t=${nonce(12)}&id=$videoId&\$fields=playerResponse"
        val root = post(url, body)
        val player = root.optJSONObject("playerResponse") ?: throw YtParseException("android_reel: no playerResponse")
        val identity = StreamIdentity("ANDROID", "3", userAgent, videoId)
        return try {
            YtClients.streamFrom(player, videoId, "android_reel", null, true, identity, cpn)
        } catch (e: YtPlayabilityException) {
            throw e
        } catch (e: java.io.IOException) {
            // audio-only formats come as SABR now; the progressive MP4 still has a plain URL
            muxed(player, videoId, identity, cpn) ?: throw e
        }
    }

    private fun muxed(player: JSONObject, videoId: String, identity: StreamIdentity, cpn: String): YtStream? {
        val formats = player.optJSONObject("streamingData")?.optJSONArray("formats") ?: return null
        val f = (0 until formats.length()).mapNotNull { formats.optJSONObject(it) }
            .filter { it.optString("url").isNotBlank() && it.optString("mimeType").contains("mp4a") }
            .minByOrNull { it.optInt("bitrate") } ?: return null
        val url = f.optString("url") + "&cpn=" + cpn
        val details = player.optJSONObject("videoDetails")
        return YtStream(
            videoId = videoId,
            url = url,
            mimeType = f.optString("mimeType").substringBefore(';'),
            itag = f.optInt("itag"),
            bitrate = f.optInt("bitrate"),
            contentLength = f.optString("contentLength").toLongOrNull() ?: -1L,
            durationMs = details?.optString("lengthSeconds")?.toLongOrNull()?.times(1000L) ?: 0L,
            expiresAt = YtClients.expiresAt(url),
            via = "android_reel muxed",
            identity = identity,
            title = details?.optString("title")?.takeIf { it.isNotBlank() },
            author = details?.optString("author")?.takeIf { it.isNotBlank() },
            muxed = true,
        )
    }

    @Volatile private var visitor: Pair<String, Long>? = null

    // one guest identity for an hour, not a new one per song
    private fun visitor(): String {
        visitor?.takeIf { System.currentTimeMillis() - it.second < 3_600_000L }?.let { return it.first }
        val v = runCatching { visitorData() }.getOrNull() ?: return YtSession.cached().visitorData
        visitor = v to System.currentTimeMillis()
        return v
    }

    private fun visitorData(): String =
        post("https://youtubei.googleapis.com/youtubei/v1/visitor_id?prettyPrint=false", JSONObject().put("context", context("")))
            .optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotBlank() }
            ?: throw YtParseException("android_reel: visitorData missing")

    private fun context(visitorData: String): JSONObject {
        val client = JSONObject()
            .put("clientName", "ANDROID")
            .put("clientVersion", CLIENT_VERSION)
            .put("clientScreen", "WATCH")
            .put("platform", "MOBILE")
            .put("osName", "Android")
            .put("osVersion", "16")
            .put("androidSdkVersion", 36)
            .put("hl", YtRuntime.hl)
            .put("gl", YtRuntime.gl)
            .put("utcOffsetMinutes", 0)
        if (visitorData.isNotBlank()) client.put("visitorData", visitorData)
        return JSONObject()
            .put("client", client)
            .put("request", JSONObject().put("internalExperimentFlags", JSONArray()).put("useSsl", true))
            .put("user", JSONObject().put("lockedSafetyMode", false))
    }

    private fun post(url: String, body: JSONObject): JSONObject {
        val req = NetworkGate.request(url, Feature.YOUTUBE)
            .post(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .header("X-Goog-Api-Format-Version", "2")
            .build()
        val text = NetworkGate.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw YtHttpException(r.code, "android_reel HTTP ${r.code}")
            r.body.string()
        }
        return try { JSONObject(text) } catch (e: JSONException) { throw YtParseException("android_reel: not JSON", e) }
    }

    private fun nonce(n: Int) = buildString(n) { synchronized(random) { repeat(n) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } } }
}
