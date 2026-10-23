// Adapted from Levyra (GPL-3.0), app/src/main/java/com/luc4n3x/levyra/data/YoutubePlaybackSecurity.kt (failure markers)
package com.ctify.player.youtube

import com.ctify.player.net.NetworkBlockedException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** The player said no for this video: status is OK, UNPLAYABLE, LOGIN_REQUIRED, ERROR... */
class YtPlayabilityException(val status: String, val reason: String) : IOException("$status: $reason")

/** A stream URL couldn't be built: signature or n parameter decoding failed, or no URL at all. */
class YtDecodeException(message: String) : IOException(message)

/** A response no longer has the shape the code expects. */
class YtParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Every client failed for reasons that aren't the network or this one video. */
class YtAllClientsFailedException(message: String, cause: Throwable?) : IOException(message, cause)

/** Sorts YouTube failures into diagnostics error classes and session/back-off decisions. */
object YtFailures {
    data class Rotation(val rotate: Boolean, val immediate: Boolean, val reset: Boolean)

    private val GEO = listOf("not available in your country", "not available in your region", "geo_restricted", "geographic")
    private val BOT = listOf("not a bot", "automated traffic", "unusual traffic", "confirm you're not a bot", "confirm you’re not a bot")
    private val TOKEN = listOf("po token", "potoken", "po_token", "token rejected", "serviceintegrity")
    private val GONE = listOf("unavailable", "private", "removed", "terminated", "does not exist", "no longer available", "confirm your age", "age-restricted", "inappropriate", "copyright")

    private fun blob(e: Throwable) = generateSequence(e) { it.cause }.joinToString(" ") { it.message.orEmpty() }.lowercase()
    private fun status(e: Throwable) = generateSequence(e) { it.cause }.firstNotNullOfOrNull { (it as? YtHttpException)?.status }

    fun rotation(e: Throwable): Rotation {
        val b = blob(e)
        if (GEO.any(b::contains)) return Rotation(false, false, true)
        val bot = BOT.any(b::contains)
        val token = TOKEN.any(b::contains)
        val login = b.contains("login_required") && !b.contains("confirm your age")
        val code = status(e)
        val rotate = bot || token || login || code == 403 || code == 410 || code == 429
        return Rotation(rotate, bot || token || login, !rotate)
    }

    /** True for "this video only": private, removed, age or region locked. Not a client fault. */
    fun isVideoProblem(e: Throwable): Boolean {
        val p = generateSequence(e) { it.cause }.firstNotNullOfOrNull { it as? YtPlayabilityException } ?: return false
        val r = p.reason.lowercase()
        if (BOT.any(r::contains)) return false
        return p.status == "UNPLAYABLE" || p.status == "ERROR" || GEO.any(r::contains) || GONE.any(r::contains)
    }

    /** 403, 429, bot check or LOGIN_REQUIRED: downloads pause for about an hour. */
    fun needsBackoff(e: Throwable): Boolean {
        val b = blob(e)
        val code = status(e)
        return code == 403 || code == 429 || BOT.any(b::contains) || (b.contains("login_required") && !b.contains("confirm your age"))
    }

    fun isNetwork(e: Throwable) = generateSequence(e) { it.cause }.any {
        it is UnknownHostException || it is SSLException || it is InterruptedIOException || it is NetworkBlockedException
    }

    /** Error class for diagnostics; the yt_* ones that mean "YouTube changed" map to WAIT_FOR_UPDATE. */
    fun errorClass(e: Throwable?): String? {
        if (e == null) return null
        val chain = generateSequence(e) { it.cause }.toList()
        val b = blob(e)
        return when {
            chain.any { it is NetworkBlockedException } -> "blocked_by_settings"
            status(e) == 429 -> "http_429"
            BOT.any(b::contains) -> "yt_bot_check"
            isVideoProblem(e) -> "unavailable"
            b.contains("login_required") -> "yt_login_required"
            chain.any { it is YtAllClientsFailedException } -> when {
                chain.any { it is YtDecodeException } -> "yt_stream_decode_failed"
                chain.any { it is YtParseException } -> "yt_extractor_parse_failed"
                else -> "yt_all_clients_failed"
            }
            chain.any { it is YtDecodeException } -> "yt_stream_decode_failed"
            chain.any { it is YtParseException || it is org.schabi.newpipe.extractor.exceptions.ParsingException } -> "yt_extractor_parse_failed"
            else -> null
        }
    }
}
