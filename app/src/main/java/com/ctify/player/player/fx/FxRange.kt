package com.ctify.player.player.fx

import kotlin.math.pow

/** Slider limits for the effects; [t] is the "extreme range" setting, 0 normal .. 1 up to 1000%. */
object FxRange {
    fun slow(t: Float) = (0.5f * 0.2f.pow(t))..(1.25f * 3.2f.pow(t))
    fun reverb(t: Float) = 0f..10f.pow(t)
    fun bass(t: Float) = 0f..(12f * 3f.pow(t))
    fun width(t: Float) = 0f..(2f * 5f.pow(t))
    fun muffle(t: Float) = 0f..10f.pow(t)
    fun rain(t: Float) = 0f..(1.5f * (10f / 1.5f).pow(t))
}
