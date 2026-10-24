package com.ctify.player

import com.ctify.player.ui.youtube.PickSong
import com.ctify.player.ui.youtube.splitPicked
import com.ctify.player.youtube.download.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPickerTest {
    private fun song(key: String) = PickSong(key, key, "artist", key, find = { null }, enqueue = {})

    @Test
    fun rowHoldsPickedAndQueuedElsewhere_failedGoBackToTheList() {
        val a = song("a"); val b = song("b"); val c = song("c"); val d = song("d")
        val states = mapOf("a" to DownloadState.RUNNING, "b" to DownloadState.FAILED, "c" to DownloadState.QUEUED)
        // "done" was picked earlier and has left the source list; it keeps its place in the row
        val done = song("done")
        val (row, listed) = splitPicked(listOf(done, a, b), listOf(a, b, c, d)) { states[it.key] }
        assertEquals(listOf("done", "a", "c"), row.map { it.key })
        assertEquals(listOf("b", "d"), listed.map { it.key })
    }
}
