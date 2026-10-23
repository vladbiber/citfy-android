// Adapted from Levyra (GPL-3.0), app/src/main/java/com/luc4n3x/levyra/data/YoutubePlaybackSecurity.kt and LevyraYoutubeSessionPoTokenProvider.kt
package com.ctify.player.youtube

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoTokenProvider
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

data class GuestSession(val visitorData: String, val generation: Long)

data class PoTokens(val player: String, val streaming: String)

/** Mints PO tokens; the Android build uses a WebView running BotGuard. */
interface PoTokenSource {
    suspend fun playerToken(visitorData: String, generation: Long): String
    suspend fun streamingToken(videoId: String, visitorData: String, generation: Long): String
    fun invalidate()
    fun warmUp(visitorData: String, generation: Long) {}
}

/** Guest session (visitorData) and PO tokens shared by the resolver and the extractor. */
object YtSession {
    private var prefs: SharedPreferences? = null
    private val memory = HashMap<String, Any>()
    private val mutex = Mutex()
    private val lock = Any()
    private val failures = AtomicLong(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastWarm = 0L
    @Volatile var poSource: PoTokenSource? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        poSource = WebViewPoTokens(context.applicationContext)
    }

    fun cached(): GuestSession = synchronized(lock) { GuestSession(getS(KEY_VISITOR), getL(KEY_GEN)) }

    suspend fun current(): GuestSession = mutex.withLock {
        val c = cached()
        if (c.visitorData.isNotBlank()) return@withLock c
        persist(fetchVisitorData(), c.generation + 1)
    }

    fun observeVisitorData(visitorData: String) {
        if (visitorData.isBlank()) return
        val changed = synchronized(lock) {
            if (getS(KEY_VISITOR) == visitorData) false
            else { put(KEY_VISITOR, visitorData); put(KEY_GEN, getL(KEY_GEN) + 1); true }
        }
        if (changed) { failures.set(0); lastWarm = 0L; poSource?.invalidate() }
    }

    /** Builds the BotGuard runtime ahead of the first tap; never on the tap path. */
    fun warmUp() {
        val src = poSource ?: return
        val now = System.currentTimeMillis()
        if (now - lastWarm < 60_000L) return
        lastWarm = now
        scope.launch {
            runCatching { val s = current(); src.warmUp(s.visitorData, s.generation) }.onFailure { lastWarm = 0L }
        }
    }

    /** null when no token source exists (JVM) or it can't mint within [budgetMs]. */
    suspend fun poTokens(videoId: String, session: GuestSession, budgetMs: Long): PoTokens? {
        val src = poSource ?: return null
        if (session.visitorData.isBlank()) return null
        return withTimeoutOrNull(budgetMs) {
            PoTokens(
                player = src.playerToken(session.visitorData, session.generation),
                streaming = src.streamingToken(videoId, session.visitorData, session.generation),
            )
        }
    }

    /** Drops the guest session after a bot check, token rejection or repeated 403. */
    suspend fun rotateIfNeeded(error: Throwable): Boolean {
        val d = YtFailures.rotation(error)
        if (!d.rotate) { if (d.reset) failures.set(0); return false }
        return mutex.withLock {
            val tries = failures.incrementAndGet()
            if (!d.immediate && tries < 2) return@withLock false
            val now = System.currentTimeMillis()
            if (now - getL(KEY_ROTATED) < ROTATION_COOLDOWN_MS) return@withLock false
            val gen = synchronized(lock) {
                val next = getL(KEY_GEN) + 1
                put(KEY_VISITOR, ""); put(KEY_GEN, next); put(KEY_ROTATED, now)
                next
            }
            failures.set(0)
            lastWarm = 0L
            poSource?.invalidate()
            val fresh = try { fetchVisitorData() } catch (e: CancellationException) { throw e } catch (_: Throwable) { "" }
            if (fresh.isNotBlank()) persist(fresh, gen)
            true
        }
    }

    fun resetFailures() = failures.set(0)

    private suspend fun fetchVisitorData(): String = withContext(Dispatchers.IO) {
        val client = JSONObject()
            .put("clientName", WEB_CLIENT_NAME)
            .put("clientVersion", WEB_CLIENT_VERSION)
            .put("hl", YtRuntime.hl).put("gl", YtRuntime.gl)
            .put("utcOffsetMinutes", 0).put("timeZone", "UTC")
        val body = JSONObject().put("context", JSONObject().put("client", client)).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = NetworkGate.request("https://youtubei.googleapis.com/youtubei/v1/visitor_id?key=$INNERTUBE_KEY&prettyPrint=false", Feature.YOUTUBE)
            .post(body)
            .header("Content-Type", "application/json")
            .header("User-Agent", GateDownloader.WEB_USER_AGENT)
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", WEB_CLIENT_VERSION)
            .build()
        NetworkGate.client.newCall(req).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw YtHttpException(r.code, "visitor_id HTTP ${r.code}")
            JSONObject(text).optJSONObject("responseContext")?.optString("visitorData").orEmpty()
                .ifBlank { throw IOException("visitorData missing") }
        }
    }

    private fun persist(visitorData: String, generation: Long): GuestSession = synchronized(lock) {
        val curGen = getL(KEY_GEN)
        val cur = getS(KEY_VISITOR)
        if (curGen > generation || (curGen == generation && cur.isNotBlank() && cur != visitorData)) return GuestSession(cur, curGen)
        put(KEY_VISITOR, visitorData); put(KEY_GEN, generation)
        GuestSession(visitorData, generation)
    }

    private fun getS(k: String): String = prefs?.getString(k, "") ?: (memory[k] as? String).orEmpty()
    private fun getL(k: String): Long = prefs?.getLong(k, 0L) ?: (memory[k] as? Long ?: 0L)
    private fun put(k: String, v: Any) {
        val p = prefs
        if (p == null) { memory[k] = v; return }
        p.edit().apply { if (v is String) putString(k, v) else putLong(k, v as Long) }.apply()
    }

    const val PREFS = "ctify-youtube"
    private const val KEY_VISITOR = "visitor_data"
    private const val KEY_GEN = "generation"
    private const val KEY_ROTATED = "last_rotation"
    private const val ROTATION_COOLDOWN_MS = 30_000L
    const val INNERTUBE_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
    const val WEB_CLIENT_NAME = "WEB"
    const val WEB_CLIENT_VERSION = "2.20260805.01.00"
}

/** Lets the extractor's own player requests carry the session PO token too. */
internal class SessionPoTokenProvider(@Suppress("unused") private val app: Context) : YoutubeSessionPoTokenProvider {
    override fun getSessionPoToken(
        clientName: String,
        clientVersion: String,
        userAgent: String?,
        localization: Localization,
        contentCountry: ContentCountry,
        loggedIn: Boolean,
    ): YoutubeSessionPoToken? {
        if (clientName.startsWith("TVHTML5", ignoreCase = true)) return null
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val src = YtSession.poSource ?: return null
        return try {
            runBlocking {
                withTimeout(55_000L) {
                    val s = YtSession.current()
                    src.playerToken(s.visitorData, s.generation).takeIf { it.isNotBlank() }?.let { YoutubeSessionPoToken(s.visitorData, it) }
                }
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Extractor session PoToken failed", e)
        }
    }
}
