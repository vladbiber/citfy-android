package com.ctify.player.radio

import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ProtocolException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException

/** Directory data is written by strangers: only public http(s) hosts are fetched. */
object RadioUrlPolicy {
    private val blockedSuffixes = listOf(".local", ".internal", ".localhost", ".lan", ".home.arpa")

    fun isAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (host == "localhost" || blockedSuffixes.any { host.endsWith(it) }) return false
        if (!host.contains('.') && !host.contains(':')) return false
        return !isPrivateLiteral(host)
    }

    /** URLs to try for a station, in order: https first for a plain http one, then the URL as filed. */
    fun streamCandidates(url: String): List<String> {
        val u = url.trim().toHttpUrlOrNull()
        // a custom port almost always speaks plain http only, and a TLS hello there can hang until timeout
        if (u == null || u.isHttps || u.port != 80) return listOf(url)
        return listOf(u.newBuilder().scheme("https").build().toString(), url)
    }

    /** Checks literal IP hosts only; names are not resolved here, so no lookup leaks. */
    private fun isPrivateLiteral(host: String): Boolean {
        val literal = host.removePrefix("[").removeSuffix("]")
        val looksLikeIp = literal.all { it.isDigit() || it == '.' } || literal.contains(':')
        if (!looksLikeIp) return false
        val addr = runCatching { InetAddress.getByName(literal) }.getOrNull() ?: return true
        return addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress ||
            addr.isAnyLocalAddress || addr.isMulticastAddress ||
            (addr is Inet4Address && addr.address[0].toInt() and 0xFF == 100 && addr.address[1].toInt() and 0xC0 == 64) ||
            (addr is Inet6Address && (addr.address[0].toInt() and 0xFE) == 0xFC)
    }
}

/** What a short check of a station's stream address says. */
object StreamProbe {
    private val streamTypes = listOf("audio/", "mpegurl", "ogg", "aac", "flac", "video/mp2t", "scpls", "dash+xml", "octet-stream")

    /** A 2xx that looks like audio, a playlist or an ICY server; a missing type is given the benefit of the doubt. */
    fun isStream(code: Int, contentType: String?, hasIcyHeaders: Boolean): Boolean {
        if (code !in 200..299) return false
        if (hasIcyHeaders || contentType.isNullOrBlank()) return true
        val ct = contentType.lowercase()
        return streamTypes.any { it in ct }
    }

    /** Alive if any address answered like a stream; null when none gave a real answer (no network, timeouts aside). */
    fun verdict(answers: List<Boolean?>): Boolean? = when {
        answers.any { it == true } -> true
        answers.any { it == false } -> false
        else -> null
    }
}

enum class StreamError { PERMANENT, TRANSIENT, DNS }

/** Sorts stream failures into ones worth waiting out and ones that will fail the same way again. */
object StreamErrors {
    private val permanentHttp = setOf(400, 401, 403, 404, 410, 451)
    private val permanentCodes = setOf(
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    )

    fun classify(cause: Throwable, httpStatus: Int?, errorCode: Int = PlaybackException.ERROR_CODE_UNSPECIFIED): StreamError = when {
        httpStatus != null -> if (httpStatus in permanentHttp) StreamError.PERMANENT else StreamError.TRANSIENT
        errorCode in permanentCodes -> StreamError.PERMANENT
        cause is UnknownHostException -> StreamError.DNS
        // cleartext refused, blocked or endless redirects, not audio, blocked address or empty playlist
        cause is UnknownServiceException || cause is ProtocolException || cause is ParserException ||
            cause is IllegalArgumentException || cause is IllegalStateException -> StreamError.PERMANENT
        else -> StreamError.TRANSIENT
    }
}

/** What the player shows for a stream title. */
data class LiveTitle(val artist: String?, val title: String?, val isAd: Boolean = false) {
    val display: String? get() = when {
        isAd -> "Advertisement"
        artist != null && title != null -> "$artist – $title"
        else -> title ?: artist
    }

    companion object {
        val NONE = LiveTitle(null, null)
    }
}

object StreamTitles {
    /** Hosts that splice ads into streams; their metadata needs the ad check. */
    private val adHosts = listOf("zeno.fm", "surfernetwork.com", "streamtheworld.com", "adswizz.com", "tritondigital.com")
    private val adMarkers = Regex(
        "(?i)\\b(advert(isement)?|commercial|reclam[aă]|publicitate|spot publicitar|adbreak|ad break|ad_break|" +
            "adswizz|preroll|pre-roll|midroll|sponsor(ed)?|werbung|publicit[eé])\\b",
    )
    private val junk = Regex("(?i)^(unknown|untitled|n/a|-|\\.|live|on air|stream|null)$")

    fun isAdHost(url: String?): Boolean {
        val host = runCatching { URI(url ?: return false).host?.lowercase() }.getOrNull() ?: return false
        return adHosts.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Turns an ICY StreamTitle into artist and title. Hides titles that repeat the
     * station name, empty or placeholder ones, and marks ads.
     */
    fun parse(raw: String?, stationName: String, streamUrl: String? = null): LiveTitle {
        val clean = sanitize(raw) ?: return LiveTitle.NONE
        if (adMarkers.containsMatchIn(clean)) return LiveTitle(null, null, isAd = true)
        if (isAdHost(streamUrl) && (clean.contains("adContext", true) || clean.startsWith("text=", true))) {
            return LiveTitle(null, null, isAd = true)
        }
        if (sameName(clean, stationName)) return LiveTitle.NONE
        val parts = clean.split(" - ", " – ", " — ", limit = 2).map { it.trim() }
        return if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
            LiveTitle(parts[0], parts[1])
        } else LiveTitle(null, clean)
    }

    private fun sanitize(raw: String?): String? {
        var s = raw?.trim() ?: return null
        // some servers send "StreamTitle='...';" verbatim or wrap the text in quotes
        s = s.removePrefix("StreamTitle=").trim('\'', '"', ';', ' ')
        s = s.replace(Regex("\\s+"), " ")
            .replace(Regex("(?i)\\s*\\|\\s*(www\\.|https?://)\\S+"), "")
            .replace(Regex("(?i)^(now playing|now|playing|on air)\\s*[:\\-]\\s*"), "")
            .trim()
        if (s.isEmpty() || junk.matches(s) || s.all { !it.isLetterOrDigit() }) return null
        return s.take(200)
    }

    private fun sameName(a: String, b: String): Boolean {
        fun n(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        val na = n(a)
        return na.isNotEmpty() && (na == n(b) || na == n(b) + "radio" || n(b).startsWith(na) && na.length > 4)
    }
}
