package com.ctify.player.player.fx

/** Slider limits for the effects; [t] is the "extreme range" setting, 0 normal .. 1 widest. */
object FxRange {
    fun slow(t: Float) = (0.5f - 0.25f * t)..(1.25f + 0.75f * t)
    fun reverb(t: Float) = 0f..(1f + t)
    fun bass(t: Float) = 0f..(12f + 12f * t)
    fun width(t: Float) = 0f..(2f + 2f * t)
    fun muffle(t: Float) = 0f..(1f + t)
    fun rain(t: Float) = 0f..(1.5f + 1.5f * t)
}
