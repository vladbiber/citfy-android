package com.ctify.player.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.ctify.player.radio.RewindRecorder
import java.io.IOException

/** Plays a [RewindRecorder]'s ring file; reads wait at the live edge instead of ending. */
@UnstableApi
class BufferDataSource : BaseDataSource(/* isNetwork = */ false) {
    private var recorder: RewindRecorder? = null
    private var uri: Uri? = null
    private var position = 0L

    override fun open(dataSpec: DataSpec): Long {
        val rec = RewindRecorder.get(dataSpec.uri.host ?: "") ?: throw IOException("Radio buffer is gone")
        if (dataSpec.position < rec.windowStart) throw IOException("Position left the rewind buffer")
        transferInitializing(dataSpec)
        recorder = rec
        uri = dataSpec.uri
        position = dataSpec.position
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val rec = recorder ?: return C.RESULT_END_OF_INPUT
        val n = rec.read(position, buffer, offset, length)
        if (n < 0) return C.RESULT_END_OF_INPUT
        position += n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (recorder != null) transferEnded()
        recorder = null
        uri = null
    }
}

/** Sends ctifybuffer:// to [BufferDataSource] and everything else to [fallback]. */
@UnstableApi
class RoutingDataSourceFactory(private val fallback: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = Routing(fallback.createDataSource())

    private class Routing(private val other: DataSource) : DataSource by other {
        private var active: DataSource = other

        override fun open(dataSpec: DataSpec): Long {
            active = when (dataSpec.uri.scheme) {
                RewindRecorder.SCHEME -> BufferDataSource()
                YtStreamDataSource.SCHEME -> YtStreamDataSource()
                else -> other
            }
            return active.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int) = active.read(buffer, offset, length)
        override fun getUri(): Uri? = active.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders
        override fun close() = active.close()
    }
}
