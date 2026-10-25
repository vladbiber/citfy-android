package com.ctify.player.youtube

import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/** YouTube's search-as-you-type suggestions; cached per prefix for the session. */
object Suggest {
    private const val MAX = 6
    private val cache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>) = size > 64
    }

    fun cached(q: String): List<String>? = synchronized(cache) { cache[q.trim().lowercase()] }

    suspend fun of(q: String): List<String> = withContext(Dispatchers.IO) {
        val key = q.trim().lowercase()
        cached(key)?.let { return@withContext it }
        val url = "https://suggestqueries.google.com/complete/search".toHttpUrl().newBuilder()
            .addQueryParameter("client", "firefox").addQueryParameter("ds", "yt")
            .addQueryParameter("hl", YtRuntime.hl).addQueryParameter("q", key).build()
        val body = NetworkGate.client.newCall(NetworkGate.request(url.toString(), Feature.YOUTUBE).build()).execute().use { r ->
            if (!r.isSuccessful) return@withContext emptyList()
            r.body.string()
        }
        parse(body, key).also { synchronized(cache) { cache[key] = it } }
    }

    private val STRING = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** ["query", ["s1", "s2", ...]]; the query itself is left out. Plain parsing, so it runs in JVM tests. */
    fun parse(body: String, q: String): List<String> {
        val start = body.indexOf('[', body.indexOf('[') + 1)
        if (!body.trimStart().startsWith("[") || start < 0) return emptyList()
        val list = body.substring(start, body.indexOf(']', start).takeIf { it > 0 } ?: return emptyList())
        return STRING.findAll(list).map { unescape(it.groupValues[1]) }.filterNot { it.equals(q, true) }.distinct().take(MAX).toList()
    }

    private fun unescape(s: String) = Regex("\\\\(u[0-9a-fA-F]{4}|.)").replace(s) { m ->
        val e = m.groupValues[1]
        if (e.length == 5) e.substring(1).toInt(16).toChar().toString() else when (e) { "n" -> "\n"; "t" -> "\t"; else -> e }
    }
}
