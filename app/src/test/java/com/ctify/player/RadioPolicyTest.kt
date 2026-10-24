package com.ctify.player

import androidx.media3.common.PlaybackException
import com.ctify.player.radio.RadioUrlPolicy
import com.ctify.player.radio.StreamError
import com.ctify.player.radio.StreamErrors
import com.ctify.player.radio.StreamProbe
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException

class RadioPolicyTest {

    @Test
    fun httpStationsTryHttpsFirst() {
        assertEquals(
            listOf("https://stream.example.com/live.mp3", "http://stream.example.com/live.mp3"),
            RadioUrlPolicy.streamCandidates("http://stream.example.com/live.mp3"),
        )
        assertEquals(
            listOf("https://stream.example.com/live", "http://stream.example.com:80/live"),
            RadioUrlPolicy.streamCandidates("http://stream.example.com:80/live"),
        )
        assertEquals(listOf("https://a.example.com/x"), RadioUrlPolicy.streamCandidates("https://a.example.com/x"))
        // custom ports stay as filed
        assertEquals(listOf("http://a.example.com:8000/x"), RadioUrlPolicy.streamCandidates("http://a.example.com:8000/x"))
    }

    @Test
    fun localAndPrivateAddressesAreRefused() {
        listOf(
            "http://localhost/s", "http://127.0.0.1/s", "http://10.1.2.3/s", "http://172.16.0.1/s", "http://172.31.255.1/s",
            "http://192.168.1.1/s", "http://169.254.169.254/latest", "http://[::1]/s", "http://[fd00::1]/s", "http://[fc00::1]/s",
            "file:///sdcard/a.mp3", "ftp://radio.example.com/s", "http://printer.local/s",
        ).forEach { assertFalse(it, RadioUrlPolicy.isAllowed(it)) }
        listOf("http://172.32.0.1/s", "https://ice.example.com:8443/s", "http://8.8.8.8/s").forEach { assertTrue(it, RadioUrlPolicy.isAllowed(it)) }
    }

    @Test
    fun errorsAreSorted() {
        val io = java.io.IOException("x")
        listOf(400, 401, 403, 404, 410, 451).forEach { assertEquals(StreamError.PERMANENT, StreamErrors.classify(io, it)) }
        listOf(500, 502, 503, 429).forEach { assertEquals(StreamError.TRANSIENT, StreamErrors.classify(io, it)) }
        assertEquals(StreamError.PERMANENT, StreamErrors.classify(UnknownServiceException("CLEARTEXT not permitted"), null))
        assertEquals(StreamError.PERMANENT, StreamErrors.classify(io, null, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertEquals(StreamError.PERMANENT, StreamErrors.classify(IllegalStateException("Playlist has no playable stream"), null))
        assertEquals(StreamError.DNS, StreamErrors.classify(UnknownHostException("gone.example"), null))
        assertEquals(StreamError.TRANSIENT, StreamErrors.classify(SocketTimeoutException(), null))
        assertEquals(StreamError.TRANSIENT, StreamErrors.classify(InterruptedIOException("Stream stalled for 20 s"), null))
        assertEquals(StreamError.TRANSIENT, StreamErrors.classify(java.net.SocketException("Connection reset"), null))
    }

    @Test
    fun probeKnowsAStreamFromAPage() {
        assertTrue(StreamProbe.isStream(200, "audio/mpeg", false))
        assertTrue(StreamProbe.isStream(200, "application/vnd.apple.mpegurl", false))
        assertTrue(StreamProbe.isStream(200, "text/html", hasIcyHeaders = true))
        assertTrue(StreamProbe.isStream(206, null, false))
        assertFalse(StreamProbe.isStream(200, "text/html; charset=utf-8", false))
        assertFalse(StreamProbe.isStream(404, "audio/mpeg", true))
        assertEquals(true, StreamProbe.verdict(listOf(false, true)))
        assertEquals(false, StreamProbe.verdict(listOf(null, false)))
        assertNull(StreamProbe.verdict(listOf(null, null)))
    }
}
