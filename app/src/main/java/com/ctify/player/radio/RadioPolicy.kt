package com.ctify.player.radio

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

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
