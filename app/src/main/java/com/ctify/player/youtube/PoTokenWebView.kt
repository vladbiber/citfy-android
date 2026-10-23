// Adapted from Levyra (GPL-3.0), app/src/main/java/com/luc4n3x/levyra/data/YoutubePlaybackSecurity.kt
package com.ctify.player.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** One BotGuard runtime per guest session, rebuilt on expiry, with 2 s to 60 s back-off on failure. */
internal class WebViewPoTokens(private val context: Context) : PoTokenSource {
    private val lock = Mutex()
    @Volatile private var active: Ready? = null
    @Volatile private var failures = 0
    @Volatile private var nextBuildAt = 0L

    private class Ready(val runtime: PoTokenRuntime, val visitorData: String, val generation: Long, val playerToken: String)

    override suspend fun playerToken(visitorData: String, generation: Long): String = ready(visitorData, generation).playerToken

    override suspend fun streamingToken(videoId: String, visitorData: String, generation: Long): String {
        val r = ready(visitorData, generation)
        return try {
            r.runtime.generate(videoId)
        } catch (e: Throwable) {
            drop(r)
            ready(visitorData, generation).runtime.generate(videoId)
        }
    }

    override fun warmUp(visitorData: String, generation: Long) {
        CoroutineScope(Dispatchers.Default).launch { runCatching { ready(visitorData, generation) } }
    }

    override fun invalidate() {
        val old = active
        active = null
        failures = 0
        nextBuildAt = 0L
        old?.let { CoroutineScope(Dispatchers.Main).launch { it.runtime.close() } }
    }

    private suspend fun ready(visitorData: String, generation: Long): Ready = lock.withLock {
        active?.takeIf { it.visitorData == visitorData && it.generation == generation && !it.runtime.isExpired }?.let { return@withLock it }
        val now = System.currentTimeMillis()
        if (now < nextBuildAt) throw IllegalStateException("Integrity runtime in back-off for ${nextBuildAt - now} ms")
        active?.let { old -> active = null; withContext(NonCancellable) { old.runtime.close() } }
        try {
            val runtime = PoTokenRuntime.create(context)
            val player = try { runtime.generate(visitorData) } catch (e: Throwable) { runtime.close(); throw e }
            failures = 0
            nextBuildAt = 0L
            Ready(runtime, visitorData, generation, player).also { active = it }
        } catch (e: Throwable) {
            failures++
            nextBuildAt = System.currentTimeMillis() + (2_000L shl (failures - 1).coerceAtMost(5)).coerceAtMost(60_000L)
            throw e
        }
    }

    private suspend fun drop(r: Ready) = lock.withLock {
        if (active === r) { active = null; withContext(NonCancellable) { r.runtime.close() } }
    }
}

@Keep
internal class PoTokenRuntime private constructor(context: Context) {
    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ready = CompletableDeferred<Unit>()
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val cache = ConcurrentHashMap<String, String>()
    private val closed = AtomicBoolean(false)
    private val dead = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    @Volatile private var expiresAtMs = 0L

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = USER_AGENT
        webView.settings.blockNetworkLoads = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.domStorageEnabled = false
        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) = downloadAndRunBotguard()
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                markDead(IllegalStateException("PO Token render process gone"))
                return true
            }
        }
        webView.loadDataWithBaseURL("https://www.youtube.com", HTML, "text/html", "utf-8", null)
    }

    val isExpired: Boolean get() = closed.get() || dead.get() || expiresAtMs <= System.currentTimeMillis()

    suspend fun generate(identifier: String): String {
        val binding = identifier.trim()
        require(binding.isNotEmpty() && binding.length <= 4096) { "Invalid PO Token binding" }
        cache[binding]?.let { return it }
        check(!closed.get() && !dead.get()) { "PO Token runtime unavailable" }
        withTimeout(INIT_TIMEOUT_MS) { ready.await() }
        val id = UUID.randomUUID().toString()
        val d = CompletableDeferred<String>()
        waiters[id] = d
        try {
            withContext(Dispatchers.Main.immediate) {
                val bytes = jsUint8Array(binding.toByteArray(StandardCharsets.UTF_8))
                val q = JSONObject.quote(id)
                webView.evaluateJavascript(
                    "try{obtainPoToken($bytes).then(function(v){$JS_INTERFACE.onToken($q,Array.from(v).join(','));}).catch(function(e){$JS_INTERFACE.onTokenError($q,String(e));});}catch(e){$JS_INTERFACE.onTokenError($q,String(e));}",
                    null,
                )
            }
            val token = withTimeout(TOKEN_TIMEOUT_MS) { d.await() }
            if (!isValidPoToken(token)) throw IllegalStateException("Invalid PO Token output")
            if (cache.size > 64) cache.clear()
            cache[binding] = token
            return token
        } finally {
            waiters.remove(id)
        }
    }

    @JavascriptInterface
    fun downloadAndRunBotguard() {
        if (closed.get() || dead.get() || !started.compareAndSet(false, true)) return
        scope.launch {
            try {
                val parsed = parseChallengeData(requestBotguard(CREATE_URL, JSONArray().put(REQUEST_KEY).toString()))
                webView.evaluateJavascript(
                    "try{const data=$parsed;runBotGuard(data).then(function(r){window.__ctifyWebPoSignalOutput=r.webPoSignalOutput;$JS_INTERFACE.onBotguardResult(String(r.botguardResponse));}).catch(function(e){$JS_INTERFACE.onInitError(String(e));});}catch(e){$JS_INTERFACE.onInitError(String(e));}",
                    null,
                )
            } catch (e: Throwable) {
                markDead(e)
            }
        }
    }

    @JavascriptInterface
    fun onBotguardResult(response: String) {
        if (closed.get() || dead.get()) return
        if (response.isBlank()) { markDead(IllegalStateException("Empty BotGuard response")); return }
        scope.launch {
            try {
                val (token, ttl) = parseIntegrityTokenData(requestBotguard(GENERATE_URL, JSONArray().put(REQUEST_KEY).put(response).toString()))
                val bounded = ttl.coerceIn(30L, 86_400L)
                expiresAtMs = System.currentTimeMillis() + maxOf(1L, bounded - minOf(600L, maxOf(15L, bounded / 10L))) * 1000L
                webView.evaluateJavascript(
                    "try{createPoTokenMinter(window.__ctifyWebPoSignalOutput,$token).then(function(){$JS_INTERFACE.onMinterReady();}).catch(function(e){$JS_INTERFACE.onInitError(String(e));});}catch(e){$JS_INTERFACE.onInitError(String(e));}",
                    null,
                )
            } catch (e: Throwable) {
                markDead(e)
            }
        }
    }

    @JavascriptInterface
    fun onMinterReady() { if (!closed.get() && !dead.get()) ready.complete(Unit) }

    @JavascriptInterface
    fun onInitError(error: String) = markDead(IllegalStateException(error.ifBlank { "PO Token initialization failed" }))

    @JavascriptInterface
    fun onToken(requestId: String, bytes: String) {
        runCatching { byteCsvToBase64Url(bytes) }
            .onSuccess { waiters[requestId]?.complete(it) }
            .onFailure { waiters[requestId]?.completeExceptionally(it) }
    }

    @JavascriptInterface
    fun onTokenError(requestId: String, error: String) {
        waiters[requestId]?.completeExceptionally(IllegalStateException(error.ifBlank { "PO Token generation failed" }))
    }

    suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        dead.set(true)
        scope.cancel()
        val e = IllegalStateException("PO Token runtime closed")
        if (!ready.isCompleted) ready.completeExceptionally(e)
        waiters.values.forEach { it.completeExceptionally(e) }
        waiters.clear()
        cache.clear()
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            runCatching {
                webView.removeJavascriptInterface(JS_INTERFACE)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    private suspend fun requestBotguard(url: String, data: String): String = withContext(Dispatchers.IO) {
        val req = NetworkGate.request(url, Feature.YOUTUBE)
            .post(data.toRequestBody())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json+protobuf")
            .header("x-goog-api-key", BOTGUARD_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .build()
        NetworkGate.client.newCall(req).execute().use { r ->
            val body = r.body.string()
            if (!r.isSuccessful) throw YtHttpException(r.code, "BotGuard HTTP ${r.code}")
            body.ifBlank { throw IllegalStateException("BotGuard returned an empty response") }
        }
    }

    private fun markDead(error: Throwable) {
        if (!dead.compareAndSet(false, true) && ready.isCompleted) return
        if (!ready.isCompleted) ready.completeExceptionally(error)
        waiters.values.forEach { it.completeExceptionally(error) }
        waiters.clear()
        cache.clear()
    }

    companion object {
        private const val JS_INTERFACE = "CtifyPoToken"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private val BOTGUARD_KEY = "AIzaSyDyT5W0Jh49F30Pqq" + "tyfdf7pDLFKLJoAnw"
        private const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
        private const val GENERATE_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
        private const val USER_AGENT = GateDownloader.WEB_USER_AGENT
        private const val INIT_TIMEOUT_MS = 20_000L
        private const val TOKEN_TIMEOUT_MS = 12_000L
        private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]+$")
        private val HTML = """
            <!doctype html><html><head><meta charset="utf-8"><script>
            let bgVmFunctions=null;let bgVm=null;let bgProgram=null;let poTokenMinter=null;
            function loadBotGuard(challengeData){bgVm=window[challengeData.globalName];bgProgram=challengeData.program;bgVmFunctions=null;if(!bgVm||!bgVm.a)throw new Error('BotGuard VM unavailable');const cb=function(a,b,c,d){bgVmFunctions={asyncSnapshotFunction:a,shutdownFunction:b,passEventFunction:c,checkCameraFunction:d};};bgVm.a(bgProgram,cb,true,undefined,function(){},[[],[]]);return new Promise(function(resolve,reject){let n=0;const timer=setInterval(function(){if(bgVmFunctions&&bgVmFunctions.asyncSnapshotFunction){clearInterval(timer);resolve({vmFunctions:bgVmFunctions});}else if(++n>=10000){clearInterval(timer);reject(new Error('BotGuard initialization timeout'));}},1);});}
            function snapshot(botguard,args){return new Promise(function(resolve,reject){try{botguard.vmFunctions.asyncSnapshotFunction(function(value){resolve(value);},[args.contentBinding,args.signedTimestamp,args.webPoSignalOutput,args.skipPrivacyBuffer]);}catch(e){reject(e);}});}
            function runBotGuard(challengeData){const code=challengeData.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;if(!code)throw new Error('BotGuard interpreter unavailable');new Function(code)();const output=[];return loadBotGuard({globalName:challengeData.globalName,program:challengeData.program}).then(function(botguard){return snapshot(botguard,{webPoSignalOutput:output});}).then(function(response){return{webPoSignalOutput:output,botguardResponse:response};});}
            async function createPoTokenMinter(output,integrityToken){if(!output||typeof output[0]!=='function')throw new Error('PO Token minter factory unavailable');const candidate=output[0](integrityToken);poTokenMinter=candidate&&typeof candidate.then==='function'?await candidate:candidate;if(typeof poTokenMinter!=='function')throw new Error('PO Token minter unavailable');}
            async function obtainPoToken(identifier){if(typeof poTokenMinter!=='function')throw new Error('PO Token minter not initialized');const candidate=poTokenMinter(identifier);const result=candidate&&typeof candidate.then==='function'?await candidate:candidate;if(!(result instanceof Uint8Array)||result.length===0)throw new Error('Invalid PO Token result');return result;}
            </script></head><body></body></html>
        """.trimIndent()

        suspend fun create(context: Context): PoTokenRuntime = withContext(Dispatchers.Main.immediate) {
            val runtime = PoTokenRuntime(context.applicationContext)
            try {
                withTimeout(INIT_TIMEOUT_MS) { runtime.ready.await() }
                runtime
            } catch (e: Throwable) {
                runtime.close()
                throw e
            }
        }

        private fun parseChallengeData(raw: String): String {
            val scrambled = JSONArray(raw)
            val challenge = if (scrambled.length() > 1 && scrambled.opt(1) is String) JSONArray(descramble(scrambled.getString(1)))
            else scrambled.optJSONArray(1) ?: throw IllegalStateException("Invalid BotGuard challenge")
            val messageId = challenge.optString(0)
            val js = firstString(challenge.optJSONArray(1))
            val jsUrl = firstString(challenge.optJSONArray(2))
            val hash = challenge.optString(3)
            val program = challenge.optString(4)
            val globalName = challenge.optString(5)
            if (messageId.isBlank() || js.isBlank() || hash.isBlank() || program.isBlank() || globalName.isBlank()) {
                throw IllegalStateException("Incomplete BotGuard challenge")
            }
            return JSONObject()
                .put("messageId", messageId)
                .put("interpreterJavascript", JSONObject()
                    .put("privateDoNotAccessOrElseSafeScriptWrappedValue", js)
                    .put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", jsUrl))
                .put("interpreterHash", hash)
                .put("program", program)
                .put("globalName", globalName)
                .put("clientExperimentsStateBlob", challenge.optString(7))
                .toString()
        }

        private fun parseIntegrityTokenData(raw: String): Pair<String, Long> {
            val array = JSONArray(raw)
            val bytes = decodeYoutubeBase64(array.getString(0))
            if (bytes.isEmpty()) throw IllegalStateException("Empty integrity token")
            return jsUint8Array(bytes) to array.optLong(1, 3600L)
        }

        private fun isValidPoToken(v: String) = v.length in 16..8192 && TOKEN_PATTERN.matches(v)

        private fun firstString(a: JSONArray?): String {
            if (a == null) return ""
            for (i in 0 until a.length()) (a.opt(i) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
            return ""
        }

        private fun descramble(v: String): String =
            String(decodeYoutubeBase64(v).map { ((it.toInt() + 97) and 0xFF).toByte() }.toByteArray(), StandardCharsets.UTF_8)

        private fun decodeYoutubeBase64(v: String): ByteArray {
            var n = v.replace('-', '+').replace('_', '/').replace('.', '=')
            n += "=".repeat((4 - n.length % 4) % 4)
            return Base64.decode(n, Base64.DEFAULT)
        }

        private fun jsUint8Array(bytes: ByteArray) = "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"

        private fun byteCsvToBase64Url(v: String): String {
            val bytes = v.split(',').filter { it.isNotBlank() }.map { p ->
                val n = p.trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid PO Token byte")
                require(n in 0..255) { "PO Token byte out of range" }
                n.toByte()
            }.toByteArray()
            require(bytes.isNotEmpty()) { "Empty PO Token bytes" }
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}
