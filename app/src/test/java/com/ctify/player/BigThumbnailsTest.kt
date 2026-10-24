package com.ctify.player

import com.ctify.player.youtube.download.SongFile
import org.junit.Assert.assertEquals
import org.junit.Test

class BigThumbnailsTest {
    @Test
    fun musicArtAsks1080Square() {
        assertEquals(
            listOf("https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj"),
            SongFile.bigThumbnails("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"),
        )
    }

    @Test
    fun videoStillTriesMaxresThenSdThenOriginal() {
        val hq = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=x&rs=y"
        assertEquals(
            listOf("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg", hq),
            SongFile.bigThumbnails(hq),
        )
    }

    @Test
    fun otherUrlsUntouched() {
        assertEquals(listOf("content://media/1"), SongFile.bigThumbnails("content://media/1"))
    }
}
