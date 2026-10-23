package com.ctify.player.youtube

import com.ctify.player.diag.classOf
import com.ctify.player.integration.Provider
import com.ctify.player.integration.ProviderChain
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

private typealias Resolve = suspend (String) -> YtStream

/** Audio stream for a videoId: cached until it expires, re-resolved on demand or after a 403. */
object StreamResolver {
    const val COMPONENT = "LevyraExtractor v1.0.0-levyra.10 + Levyra token code e16e9805"

    private val cache = object : LinkedHashMap<String, YtStream>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YtStream>?) = size > 128
    }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<YtStream>>()

    private fun clientProvider(p: ClientProfile) = Provider<Resolve>(p.key, "InnerTube ${p.clientName} ${p.clientVersion}") { id ->
        try {
            YtClients.resolve(id, p).also { probe(it); YtSession.resetFailures() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!YtSession.rotateIfNeeded(e)) throw e
            YtClients.resolve(id, p).also { probe(it) }
        }
    }

    private val reelProvider = Provider<Resolve>("android_reel", "InnerTube ANDROID reel ${YtReel.CLIENT_VERSION}") { id ->
        YtReel.resolve(id).also { probe(it) }
    }

    private val extractorProvider = Provider<Resolve>("extractor", "LevyraExtractor stream extractor") { id -> viaExtractor(id) }

    private val clientProviders = YtClients.ORDER.map(::clientProvider)

    private fun chainOf(feature: String, providers: List<Provider<Resolve>>) = ProviderChain(
        feature = feature,
        providers = providers,
        pauseAfterFailures = 4,
        classify = ::classify,
        // "unavailable" from one client often means only that client can't play it, so keep going
        stopOn = YtFailures::isNetwork,
    )

    /** Playback starts fast on the Reel progressive file, as Levyra's song policy does. */
    val chain = chainOf("YouTube streams", listOf(reelProvider) + clientProviders + extractorProvider)

    /** Downloads try the audio-only M4A clients first for better quality, then the same fallbacks. */
    val downloadChain = chainOf("YouTube downloads", clientProviders + reelProvider + extractorProvider)

    /** Diagnostics class when the whole chain failed. */
    fun classify(e: Throwable?, status: Int?): String = when {
        e == null -> classOf(null, status)
        YtFailures.isVideoProblem(e) || YtFailures.isNetwork(e) -> YtFailures.errorClass(e) ?: classOf(e, status)
        else -> YtFailures.errorClass(YtAllClientsFailedException("every client failed", e)) ?: "yt_all_clients_failed"
    }

    fun cached(videoId: String, forDownload: Boolean = false): YtStream? = synchronized(cache) {
        val key = key(videoId, forDownload)
        cache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() } ?: run { cache.remove(key); null }
    }

    fun invalidate(videoId: String) = synchronized(cache) { cache.remove(key(videoId, false)); cache.remove(key(videoId, true)) }

    private fun key(videoId: String, forDownload: Boolean) = if (forDownload) "d:$videoId" else videoId

    /** Fresh or cached stream; [fresh] forces a new lookup (after a 403). */
    suspend fun resolve(videoId: String, fresh: Boolean = false, forDownload: Boolean = false): YtStream {
        require(VIDEO_ID.matches(videoId)) { "Not a YouTube video id: $videoId" }
        if (fresh) invalidate(videoId) else cached(videoId, forDownload)?.let { return it }
        YtRuntime.ensure()
        val k = key(videoId, forDownload)
        val mine = CompletableDeferred<YtStream>()
        val existing = inFlight.putIfAbsent(k, mine)
        if (existing != null) return existing.await()
        try {
            val s = withContext(Dispatchers.IO) {
                (if (forDownload) downloadChain else chain).run("Get audio stream", videoId) { it(videoId) }.getOrThrow()
            }
            synchronized(cache) { cache[k] = s }
            mine.complete(s)
            return s
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(k, mine)
        }
    }

    /**
     * A small range read past the first megabyte: without a PO token googlevideo serves
     * only the start of a file, and answers 403 further in.
     */
    private fun probe(s: YtStream) {
        val from = if (s.contentLength > 2_000_000L) s.contentLength / 2 else 0L
        val req = NetworkGate.request(s.url, Feature.YOUTUBE, Traffic.STREAM)
            .header("Range", "bytes=$from-${from + 4095}")
            .apply { s.headers().forEach { (k, v) -> header(k, v) } }
            .build()
        NetworkGate.client.newCall(req).execute().use { r ->
            if (r.code == 403 && from > 0) throw YtDecodeException("${s.via} serves only the start of the file (no PO token)")
            if (r.code != 200 && r.code != 206) throw YtHttpException(r.code, "${s.via} stream HTTP ${r.code}")
            val type = r.header("Content-Type").orEmpty()
            if (type.contains("text/html") || type.contains("json")) throw YtDecodeException("${s.via} stream returned $type")
        }
    }

    private fun viaExtractor(videoId: String): YtStream {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val audio = info.audioStreams.filter { it.isUrl && it.content.isNotBlank() }
        val best = audio.sortedWith(
            compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format?.mimeType?.contains("mp4") == true }
                .thenByDescending { it.averageBitrate }
        ).firstOrNull() ?: throw YtDecodeException("extractor: no audio stream")
        val s = YtStream(
            videoId = videoId,
            url = best.content,
            mimeType = best.format?.mimeType ?: "audio/mp4",
            itag = best.itag,
            bitrate = best.averageBitrate * 1000,
            contentLength = -1L,
            durationMs = info.duration * 1000L,
            expiresAt = YtClients.expiresAt(best.content),
            via = "extractor",
            identity = null,
            title = info.name,
            author = info.uploaderName,
        )
        probe(s)
        return s
    }

    val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
}
