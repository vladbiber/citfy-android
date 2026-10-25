package com.ctify.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonsTest {
    @Test
    fun playCornerTightensFromPausedToPlayingToPressed() {
        assertEquals(50, playCorner(playing = false, pressed = false))
        assertTrue(playCorner(playing = true, pressed = false) < 50)
        assertTrue(playCorner(playing = true, pressed = true) < playCorner(playing = true, pressed = false))
        assertEquals(playCorner(playing = false, pressed = true), playCorner(playing = true, pressed = true))
    }

    @Test
    fun waveEasesWithoutOvershoot() {
        var amp = 0f
        repeat(120) { amp = easeToward(amp, 1f, 1f / 60, 0.18f); assertTrue(amp in 0f..1f) }
        assertEquals(1f, amp, 0.01f)
        assertEquals(0.5f, easeToward(0.5f, 1f, 0f, 0.18f), 0f)
        repeat(120) { amp = easeToward(amp, 0f, 1f / 60, 0.18f) }
        assertTrue(amp < 0.01f)
    }
}
