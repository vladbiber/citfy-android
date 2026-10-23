package com.ctify.player.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.youtube.StreamResolver
import com.ctify.player.youtube.YtStream
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Plays ytstream://<videoId>: resolves the stream when opened and once more after a 403. */
@UnstableApi
class YtStreamDataSource : DataSource {
    private val http = OkHttpDataSource.Factory(NetworkGate.mediaClient).createDataSource()
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = http.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val videoId = dataSpec.uri.host?.takeIf { StreamResolver.VIDEO_ID.matches(it) } ?: throw IOException("Bad YouTube stream address")
        uri = dataSpec.uri
        val first = resolve(videoId, fresh = false)
        return try {
            http.open(spec(dataSpec, first))
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode != 403 && e.responseCode != 410) throw e
            http.close()
            http.open(spec(dataSpec, resolve(videoId, fresh = true)))
        }
    }

    private fun resolve(videoId: String, fresh: Boolean): YtStream = try {
        runBlocking { StreamResolver.resolve(videoId, fresh) }
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException("YouTube stream lookup failed: ${e.message}", e)
    }

    private fun spec(original: DataSpec, s: YtStream): DataSpec = original.buildUpon()
        .setUri(Uri.parse(s.url))
        .setHttpRequestHeaders(s.headers() + (NetworkGate.FEATURE_HEADER to Feature.YOUTUBE.key))
        .build()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = http.read(buffer, offset, length)
    override fun getUri(): Uri? = uri
    override fun getResponseHeaders(): Map<String, List<String>> = http.responseHeaders
    override fun close() = http.close()

    companion object {
        const val SCHEME = "ytstream"
    }
}
