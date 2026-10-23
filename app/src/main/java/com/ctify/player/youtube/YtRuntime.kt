// Adapted from Levyra (GPL-3.0), app/src/main/java/com/luc4n3x/levyra/data/NewPipeRuntime.kt
package com.ctify.player.youtube

import android.content.Context
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.net.Traffic
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** An HTTP status from a YouTube request, kept for diagnostics and back-off. */
class YtHttpException(val status: Int, message: String) : IOException(message)

/** Sets the extractor up once, with a downloader that goes through [NetworkGate]. */
object YtRuntime {
    private val initialized = AtomicBoolean(false)
    private val providerInstalled = AtomicBoolean(false)

    val hl: String get() = Locale.getDefault().language.ifBlank { "en" }
    val gl: String get() = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    /** [context] is null on the JVM; then no PO-token provider is installed. */
    fun ensure(context: Context? = null) {
        if (initialized.compareAndSet(false, true)) {
            try {
                NewPipe.init(GateDownloader, Localization(hl, gl), ContentCountry(gl))
            } catch (e: Throwable) {
                initialized.set(false)
                throw e
            }
        }
        val app = context?.applicationContext ?: return
        if (providerInstalled.compareAndSet(false, true)) {
            NewPipe.setYoutubeSessionPoTokenProvider(SessionPoTokenProvider(app))
        }
    }

    fun acceptLanguage(): String = if (hl == "en") "en-$gl,en;q=0.9" else "$hl-$gl,$hl;q=0.9,en;q=0.7"
}

/** Every extractor request is tagged YOUTUBE, so offline mode and the switch apply. */
internal object GateDownloader : Downloader() {
    private const val MAX_BYTES = 8L * 1024 * 1024

    private val client: OkHttpClient by lazy {
        NetworkGate.client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val r = chain.request()
                // PO tokens only ever go to googlevideo
                if (!r.url.queryParameter("pot").isNullOrBlank() && !r.url.host.endsWith("googlevideo.com")) {
                    throw IOException("Blocked token redirect outside googlevideo")
                }
                chain.proceed(r)
            }
            .build()
    }

    override fun execute(request: Request): Response =
        client.newCall(toOkHttp(request)).execute().use(::toResponse)

    override fun executeAsync(request: Request, callback: AsyncCallback): CancellableCall {
        val call = client.newCall(toOkHttp(request))
        val cancellable = CancellableCall(call)
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                cancellable.setFinished()
                callback.onError(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    response.use { callback.onSuccess(toResponse(it)) }
                } catch (e: Exception) {
                    callback.onError(e)
                } finally {
                    cancellable.setFinished()
                }
            }
        })
        return cancellable
    }

    private fun isPlayerEndpoint(url: String) =
        url.startsWith("https://www.youtube.com/youtubei/v1/player") ||
            url.startsWith("https://youtubei.googleapis.com/youtubei/v1/player")

    private fun toOkHttp(request: Request): okhttp3.Request {
        val method = request.httpMethod().uppercase()
        val raw = request.dataToSend()
        val data = if (raw != null && method == "POST" && isPlayerEndpoint(request.url())) {
            runCatching {
                YoutubeParsingHelper.addSessionPoTokenToPlayerBody(raw, NewPipe.getPreferredLocalization(), NewPipe.getPreferredContentCountry())
            }.getOrDefault(raw)
        } else raw
        val body = when {
            method == "GET" || method == "HEAD" -> null
            data != null -> data.toRequestBody()
            else -> ByteArray(0).toRequestBody()
        }
        val headers = Headers.Builder().apply {
            request.headers().forEach { (name, values) ->
                if (name.isNotBlank()) values.filter { it.isNotBlank() }.forEach { add(name, it) }
            }
        }.build()
        val b = NetworkGate.request(request.url(), Feature.YOUTUBE, Traffic.LOOKUP)
            .method(method, body)
            .headers(headers)
        if (headers["User-Agent"].isNullOrBlank()) b.header("User-Agent", WEB_USER_AGENT)
        if (headers["Accept-Language"].isNullOrBlank()) b.header("Accept-Language", YtRuntime.acceptLanguage())
        return b.build()
    }

    private fun toResponse(response: okhttp3.Response): Response {
        if (response.code == 429) throw YtHttpException(429, "HTTP 429 from ${response.request.url.host}")
        val bytes = readBounded(response.body)
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            bytes.toString(Charsets.UTF_8),
            bytes,
            response.request.url.toString(),
        )
    }

    private fun readBounded(body: okhttp3.ResponseBody): ByteArray {
        if (body.contentLength() > MAX_BYTES) throw IOException("YouTube response too large")
        val out = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buf = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw IOException("YouTube response too large")
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
}
