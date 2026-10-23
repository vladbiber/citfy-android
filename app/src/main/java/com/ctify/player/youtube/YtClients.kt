// Adapted from Levyra (GPL-3.0), app/src/main/java/com/luc4n3x/levyra/data/PlaybackResolver.kt (client profiles, player request, format URL)
// and app/src/main/java/com/luc4n3x/levyra/data/network/YoutubeStreamClientIdentity.kt
package com.ctify.player.youtube

import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.net.URLDecoder
import java.net.URLEncoder

/** One InnerTube client identity, as Levyra sends it. */
data class ClientProfile(
    val clientName: String,
    val clientVersion: String,
    val headerName: String,
    val userAgent: String,
    val android: Boolean,
    /** WEB clients need a PO token; Android VR uses one when it's ready */
    val poToken: PoTokenUse,
) {
    val key: String get() = clientName.lowercase()
    val isWeb: Boolean get() = clientName.startsWith("WEB")
}

enum class PoTokenUse { NONE, OPTIONAL, REQUIRED }

/** Headers the media request must carry so googlevideo sees the same client. */
data class StreamIdentity(val clientName: String, val headerName: String, val userAgent: String, val videoId: String) {
    fun mediaHeaders(): Map<String, String> {
        val h = linkedMapOf("User-Agent" to userAgent, "Accept" to "*/*", "Accept-Encoding" to "identity")
        val origin = when (headerName) {
            "1" -> "https://www.youtube.com" to "https://www.youtube.com/"
            "67" -> "https://music.youtube.com" to "https://music.youtube.com/"
            "56" -> "https://www.youtube.com" to "https://www.youtube.com/embed/$videoId"
            else -> null
        }
        if (origin != null) {
            h["Origin"] = origin.first
            h["Referer"] = origin.second
            h["Sec-Fetch-Dest"] = "empty"
            h["Sec-Fetch-Mode"] = "cors"
            h["Sec-Fetch-Site"] = "cross-site"
        }
        return h
    }
}

/** A resolved audio-only stream. */
data class YtStream(
    val videoId: String,
    val url: String,
    val mimeType: String,
    val itag: Int,
    val bitrate: Int,
    val contentLength: Long,
    val durationMs: Long,
    val expiresAt: Long,
    val via: String,
    val identity: StreamIdentity?,
    val title: String? = null,
    val author: String? = null,
    /** an MP4 with video too (itag 18); played audio-only, downloads keep just the AAC track */
    val muxed: Boolean = false,
) {
    val isM4a: Boolean get() = mimeType.contains("mp4", true)
    fun headers(): Map<String, String> = identity?.mediaHeaders() ?: mapOf("User-Agent" to GateDownloader.WEB_USER_AGENT)
}

/** InnerTube player requests per client, in Levyra's order. */
object YtClients {
    val ORDER = listOf(
        ClientProfile("ANDROID_VR", "1.65.10", "28", "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; Quest 3 Build/SQ3A.220605.009.A1) gzip", true, PoTokenUse.OPTIONAL),
        ClientProfile("ANDROID_MUSIC", "8.10.52", "21", "Mozilla/5.0 (Linux; Android 15; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) com.google.android.apps.youtube.music/8.10.52", true, PoTokenUse.NONE),
        ClientProfile("ANDROID", "19.44.38", "3", "com.google.android.youtube/19.44.38 (Linux; U; Android 15)", true, PoTokenUse.NONE),
        ClientProfile("IOS", "20.10.4", "5", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3 like Mac OS X; en_US)", false, PoTokenUse.NONE),
        ClientProfile("WEB_REMIX", "1.20260804.16.00", "67", GateDownloader.WEB_USER_AGENT, false, PoTokenUse.REQUIRED),
        ClientProfile(YtSession.WEB_CLIENT_NAME, YtSession.WEB_CLIENT_VERSION, "1", GateDownloader.WEB_USER_AGENT, false, PoTokenUse.REQUIRED),
        ClientProfile("WEB_EMBEDDED_PLAYER", "1.20260423.01.00", "56", GateDownloader.WEB_USER_AGENT, false, PoTokenUse.NONE),
    )

    private val JSON = "application/json".toMediaType()

    /** One player request with [p]; throws on any refusal so the chain moves on. */
    suspend fun resolve(videoId: String, p: ClientProfile): YtStream {
        val session = YtSession.current()
        val tokens = when (p.poToken) {
            PoTokenUse.NONE -> null
            PoTokenUse.OPTIONAL -> YtSession.poTokens(videoId, session, 4_000L)
            PoTokenUse.REQUIRED -> YtSession.poTokens(videoId, session, 35_000L)
                ?: throw IllegalStateException("${p.clientName} needs a PO token and none is available")
        }
        val sts = if (p.isWeb) runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }.getOrNull() else null
        val host = if (p.clientName == "ANDROID_VR") "https://youtubei.googleapis.com" else "https://www.youtube.com"
        val body = playerBody(videoId, p, session.visitorData, tokens?.player, sts).toString()
        val req = NetworkGate.request("$host/youtubei/v1/player?key=${YtSession.INNERTUBE_KEY}&prettyPrint=false", Feature.YOUTUBE)
            .post(body.toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Origin", if (p.clientName == "WEB_REMIX") "https://music.youtube.com" else "https://www.youtube.com")
            .header("Referer", if (p.clientName == "WEB_EMBEDDED_PLAYER") "https://www.youtube.com/embed/$videoId" else "https://www.youtube.com/watch?v=$videoId")
            .header("User-Agent", p.userAgent)
            .header("X-Youtube-Client-Name", p.headerName)
            .header("X-Youtube-Client-Version", p.clientVersion)
            .apply {
                if (session.visitorData.isNotBlank()) header("X-Goog-Visitor-Id", session.visitorData)
                if (p.clientName == "ANDROID_VR") header("X-Goog-Api-Format-Version", "2")
            }
            .build()
        val text = NetworkGate.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw YtHttpException(r.code, "${p.key} HTTP ${r.code}")
            r.body.string()
        }
        val root = try { JSONObject(text) } catch (e: JSONException) { throw YtParseException("${p.key}: player response is not JSON", e) }
        return streamFrom(root, videoId, p.key + if (tokens != null) "+token" else "", tokens?.streaming, p.isWeb, StreamIdentity(p.clientName, p.headerName, p.userAgent, videoId))
    }

    /** Picks the best audio format of a player response and builds its URL. */
    internal fun streamFrom(
        root: JSONObject,
        videoId: String,
        via: String,
        streamingToken: String?,
        decodeThrottling: Boolean,
        identity: StreamIdentity,
        cpn: String? = null,
    ): YtStream {
        root.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotBlank() }?.let(YtSession::observeVisitorData)
        val play = root.optJSONObject("playabilityStatus")
        val status = play?.optString("status").orEmpty()
        if (status.isNotBlank() && status != "OK") {
            throw YtPlayabilityException(status, play?.optString("reason").orEmpty().ifBlank { status })
        }
        val details = root.optJSONObject("videoDetails")
        if (details != null && details.optString("videoId") != videoId) throw YtParseException("$via: player answered for another video")
        val streaming = root.optJSONObject("streamingData") ?: throw YtParseException("$via: no streamingData")
        val formats = streaming.optJSONArray("adaptiveFormats") ?: throw YtParseException("$via: no adaptiveFormats")
        val audio = (0 until formats.length()).mapNotNull { formats.optJSONObject(it) }
            .filter { it.optString("mimeType").startsWith("audio/") }
            .sortedByDescending(::score)
        if (audio.isEmpty()) throw YtParseException("$via: no audio formats")
        val duration = details?.optString("lengthSeconds")?.toLongOrNull()?.times(1000L) ?: 0L
        var decodeError: String? = null
        for (f in audio) {
            var url = formatUrl(f, videoId, streamingToken, decodeThrottling)
            if (url.isBlank()) { decodeError = "$via: stream address could not be decoded"; continue }
            if (cpn != null) url = withParam(url, "cpn", cpn)
            return YtStream(
                videoId = videoId,
                url = url,
                mimeType = f.optString("mimeType").substringBefore(';'),
                itag = f.optInt("itag"),
                bitrate = f.optInt("bitrate"),
                contentLength = f.optString("contentLength").toLongOrNull() ?: -1L,
                durationMs = duration,
                expiresAt = expiresAt(url),
                via = via,
                identity = identity,
                title = details?.optString("title")?.takeIf { it.isNotBlank() },
                author = details?.optString("author")?.takeIf { it.isNotBlank() },
            )
        }
        throw YtDecodeException(decodeError ?: "$via: no usable audio URL")
    }

    private fun playerBody(videoId: String, p: ClientProfile, visitorData: String, playerToken: String?, sts: Int?): JSONObject {
        val client = JSONObject()
            .put("clientName", p.clientName)
            .put("clientVersion", p.clientVersion)
            .put("hl", YtRuntime.hl)
            .put("gl", YtRuntime.gl)
            .put("utcOffsetMinutes", 0)
            .put("timeZone", "UTC")
        if (visitorData.isNotBlank()) client.put("visitorData", visitorData)
        if (p.android) {
            val vr = p.clientName == "ANDROID_VR"
            client.put("androidSdkVersion", if (vr) 32 else 35).put("osName", "Android").put("osVersion", if (vr) "12L" else "15").put("platform", "MOBILE")
            if (vr) client.put("deviceMake", "Oculus").put("deviceModel", "Quest 3")
        }
        if (p.clientName == "IOS") {
            client.put("deviceMake", "Apple").put("deviceModel", "iPhone16,2").put("osName", "iPhone").put("osVersion", "18.3").put("platform", "MOBILE")
        }
        if (p.clientName == "WEB_EMBEDDED_PLAYER") {
            client.put("clientScreen", "EMBED").put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/embed/$videoId"))
        }
        val cpc = JSONObject().put("html5Preference", "HTML5_PREF_WANTS")
        sts?.let { cpc.put("signatureTimestamp", it) }
        return JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("playbackContext", JSONObject().put("contentPlaybackContext", cpc))
            .apply { playerToken?.takeIf { it.isNotBlank() }?.let { put("serviceIntegrityDimensions", JSONObject().put("poToken", it)) } }
    }

    /** M4A first (itag 141, 140, 139), then Opus, then by bitrate. */
    private fun score(f: JSONObject): Int {
        val mime = f.optString("mimeType").lowercase()
        val mp4 = if (mime.contains("mp4")) 3_000_000 else 0
        val itag = when (f.optInt("itag")) { 141 -> 700_000; 140 -> 560_000; 251 -> 300_000; 139 -> 120_000; else -> 0 }
        return mp4 + itag + f.optInt("bitrate") / 2
    }

    private fun formatUrl(f: JSONObject, videoId: String, streamingToken: String?, decodeThrottling: Boolean): String {
        val direct = f.optString("url")
        val base = if (direct.isNotBlank()) direct else {
            val v = formValues(f.optString("signatureCipher").ifBlank { f.optString("cipher") })
            val url = v["url"].orEmpty()
            if (url.isBlank()) return ""
            val sig = v["sig"] ?: v["signature"] ?: v["s"]?.let { decodeSignature(videoId, it) }
            if (sig.isNullOrBlank()) return ""
            url + (if ('?' in url) "&" else "?") + enc(v["sp"]?.takeIf { it.isNotBlank() } ?: "signature") + "=" + enc(sig)
        }
        val throttled = if (decodeThrottling && Regex("[?&]n=").containsMatchIn(base)) decodeN(videoId, base) ?: return "" else base
        return streamingToken?.takeIf { it.isNotBlank() }?.let { withParam(throttled, "pot", it) } ?: throttled
    }

    private fun decodeSignature(videoId: String, s: String): String =
        runCatching { YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, s) }
            .recoverCatching { YoutubeJavaScriptPlayerManager.clearAllCaches(); YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, s) }
            .getOrDefault("")

    private fun decodeN(videoId: String, url: String): String? {
        val out = runCatching { YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url) }
            .recoverCatching { YoutubeJavaScriptPlayerManager.clearThrottlingParametersCache(); YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url) }
            .getOrNull() ?: return null
        return out.takeIf { nParam(it) != nParam(url) }
    }

    private fun nParam(url: String) = Regex("[?&]n=([^&]*)").find(url)?.groupValues?.get(1).orEmpty()

    fun expiresAt(url: String): Long {
        val now = System.currentTimeMillis()
        val fromUrl = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000L)?.minus(4 * 60_000L)
        return when {
            fromUrl == null -> now + 2 * 3600_000L
            else -> minOf(fromUrl, now + 5 * 3600_000L).coerceAtLeast(now)
        }
    }

    private fun formValues(s: String): Map<String, String> = s.split('&').mapNotNull { part ->
        val k = part.substringBefore('=', "")
        if (k.isBlank()) null else dec(k) to dec(part.substringAfter('=', ""))
    }.toMap()

    private fun withParam(url: String, key: String, value: String): String {
        val re = Regex("([?&])${Regex.escape(key)}=[^&]*")
        return if (re.containsMatchIn(url)) url.replace(re) { "${it.groupValues[1]}$key=${enc(value)}" }
        else url + (if ('?' in url) "&" else "?") + key + "=" + enc(value)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
