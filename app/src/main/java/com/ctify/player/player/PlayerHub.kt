package com.ctify.player.player

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.ctify.player.data.Track
import com.ctify.player.player.fx.Fx
import com.ctify.player.player.fx.Mood
import com.ctify.player.player.fx.RETUNE_PRESETS
import com.ctify.player.player.fx.RainPreset
import com.ctify.player.player.fx.VinylSettings
import kotlinx.coroutines.flow.MutableStateFlow

/** What the player screens read: the current track and transport state. */
data class NowState(
    val track: Track?,
    val isPlaying: Boolean,
    val progressMs: Int,
    val durationMs: Long,
    val shuffle: Boolean,
    val repeat: String,
    val volumePercent: Int,
)

/** Sound settings, spectrum and transport shared by the UI, view model and audio processor. */
object PlayerHub {
    private lateinit var app: Context
    val fx = Fx()
    val spectrum = MutableStateFlow(FloatArray(com.ctify.player.player.fx.SpectrumAnalyzer.BAR_COUNT))
    val playback = MutableStateFlow<NowState?>(null)
    val reverseOn = MutableStateFlow(false)
    /** true while a reversed copy of the track is being decoded */
    val reversePreparing = MutableStateFlow(false)

    /** what the playback controller in the service publishes */
    val ui = MutableStateFlow(PlayerUiState())

    /** session handle held while the app is open; all transport goes through the service */
    @Volatile var remote: MediaController? = null

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        loadFx()
    }

    fun togglePlay() {
        val c = remote ?: return
        if (c.isPlaying) c.pause()
        else {
            when (c.playbackState) {
                Player.STATE_IDLE -> c.prepare()
                // After a track ends, play() alone does nothing; rewind first so the
                // single tap actually restarts playback.
                Player.STATE_ENDED -> c.seekTo(0)
            }
            c.play()
        }
    }
    fun next() = send(Cmd.NEXT)
    fun previous() = send(Cmd.PREVIOUS)
    fun seek(ms: Int) = send(Cmd.SEEK) { putLong(Cmd.ARG_MS, ms.toLong()) }
    fun setVolume(percent: Int) { remote?.volume = (percent / 100f).coerceIn(0f, 1f) }
    fun toggleShuffle() { remote?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        val c = remote ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun toggleReverse() = send(Cmd.TOGGLE_REVERSE)
    fun playQueue(ids: List<String>, startIndex: Int) = send(Cmd.PLAY_QUEUE) {
        putStringArray(Cmd.ARG_IDS, ids.toTypedArray()); putInt(Cmd.ARG_INDEX, startIndex)
    }
    fun addToQueue(id: String) = send(Cmd.ADD_TO_QUEUE) { putString(Cmd.ARG_ID, id) }
    fun playQueueIndex(index: Int) = send(Cmd.PLAY_INDEX) { putInt(Cmd.ARG_INDEX, index) }

    private fun send(action: String, args: Bundle.() -> Unit = {}) {
        remote?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), Bundle().apply(args))
    }

    // ---- effects --------------------------------------------------------

    /** UI-observable mirrors of [fx]; the DSP thread reads the Fx fields. */
    val speed = MutableStateFlow(1.0f)
    val reverb = MutableStateFlow(0.0f)
    val retuneIndex = MutableStateFlow(0)
    val bassGain = MutableStateFlow(0.0f)
    val bassFreq = MutableStateFlow(120.0f)

    /** What you actually hear: the slow slider times the tuning ratio. */
    val rate = MutableStateFlow(1.0f)

    fun setSpeed(value: Float) {
        speed.value = value
        fx.speed = value
        rate.value = fx.rate
        saveFx()
    }

    /** Drags on the slow bar, which shows the combined rate, not the raw speed. */
    fun setRate(value: Float) {
        val ratio = RETUNE_PRESETS.getOrNull(retuneIndex.value)?.ratio ?: 1f
        setSpeed(value / ratio)
    }

    fun setReverb(value: Float) {
        reverb.value = value
        fx.reverb = value
        saveFx()
    }

    fun setBass(gainDb: Float, freq: Float) {
        bassGain.value = gainDb
        bassFreq.value = freq
        fx.bassGainDb = gainDb
        fx.bassFreq = freq
        saveFx()
    }

    fun setRetune(index: Int) {
        val preset = RETUNE_PRESETS.getOrNull(index) ?: return
        retuneIndex.value = index
        fx.retune = preset.ratio
        rate.value = fx.rate
        if (preset.reverb > 0f) setReverb(preset.reverb) else saveFx()
    }

    val width = MutableStateFlow(1.0f)
    val pan8d = MutableStateFlow(0.0f)
    val muffle = MutableStateFlow(0.0f)

    fun setSpace(width: Float = this.width.value, pan8d: Float = this.pan8d.value, muffle: Float = this.muffle.value) {
        this.width.value = width; this.pan8d.value = pan8d; this.muffle.value = muffle
        fx.width = width; fx.pan8d = pan8d; fx.muffle = muffle
        saveFx()
    }

    fun applyMood(mood: Mood) {
        setRetune(0)
        setSpeed(mood.rate)
        setReverb(mood.reverb)
        setBass(mood.bassDb, 120f)
        setSpace(mood.width, mood.pan8d, mood.muffle)
        val v = vinyl.value
        setVinyl(mood.vinyl?.apply(on = true) ?: v.copy(on = false))
    }

    val rain = MutableStateFlow(RainPreset.OFF)
    val rainLevel = MutableStateFlow(1f)
    /** what the rain chip turns back on */
    private var lastRain = RainPreset.RAIN

    fun setRain(preset: RainPreset, level: Float = rainLevel.value) {
        if (preset != RainPreset.OFF) lastRain = preset
        rain.value = preset; rainLevel.value = level
        fx.rain = preset; fx.rainLevel = level
        saveFx()
    }

    fun toggleRain() = setRain(if (rain.value == RainPreset.OFF) lastRain else RainPreset.OFF)

    val vinyl = MutableStateFlow(VinylSettings())

    fun setVinyl(value: VinylSettings) {
        vinyl.value = value
        fx.vinyl = value.on
        fx.vinylCrackle = value.crackle
        fx.vinylWarmth = value.warmth
        fx.vinylWow = value.wow
        fx.vinylSurface = value.surface
        saveFx()
    }

    fun resetFx() {
        setSpeed(1f)
        setReverb(0f)
        setRetune(0)
        setBass(0f, 120f)
        setSpace(1f, 0f, 0f)
        setVinyl(vinyl.value.copy(on = false))
        setRain(RainPreset.OFF)
    }

    private fun saveFx() {
        if (!::app.isInitialized) return
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("speed", speed.value)
            .putFloat("reverb", reverb.value)
            .putInt("retune", retuneIndex.value)
            .putFloat("bass_gain", bassGain.value)
            .putFloat("bass_freq", bassFreq.value)
            .putFloat("width", width.value)
            .putFloat("pan8d", pan8d.value)
            .putFloat("muffle", muffle.value)
            .putString("rain", rain.value.name)
            .putFloat("rain_level", rainLevel.value)
            .putBoolean("vinyl", vinyl.value.on)
            .putFloat("vinyl_crackle", vinyl.value.crackle)
            .putFloat("vinyl_warmth", vinyl.value.warmth)
            .putFloat("vinyl_wow", vinyl.value.wow)
            .putFloat("vinyl_surface", vinyl.value.surface)
            .apply()
    }

    private fun loadFx() {
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        speed.value = p.getFloat("speed", 1f)
        reverb.value = p.getFloat("reverb", 0f)
        retuneIndex.value = p.getInt("retune", 0)
        bassGain.value = p.getFloat("bass_gain", 0f)
        bassFreq.value = p.getFloat("bass_freq", 120f)
        fx.speed = speed.value
        fx.reverb = reverb.value
        fx.retune = RETUNE_PRESETS.getOrNull(retuneIndex.value)?.ratio ?: 1f
        fx.bassGainDb = bassGain.value
        fx.bassFreq = bassFreq.value
        rate.value = fx.rate
        width.value = p.getFloat("width", 1f)
        pan8d.value = p.getFloat("pan8d", 0f)
        muffle.value = p.getFloat("muffle", 0f)
        fx.width = width.value; fx.pan8d = pan8d.value; fx.muffle = muffle.value
        rain.value = runCatching { RainPreset.valueOf(p.getString("rain", "OFF")!!) }.getOrDefault(RainPreset.OFF)
        rainLevel.value = p.getFloat("rain_level", 1f)
        fx.rain = rain.value; fx.rainLevel = rainLevel.value
        if (rain.value != RainPreset.OFF) lastRain = rain.value
        val d = VinylSettings()
        setVinyl(
            VinylSettings(
                on = p.getBoolean("vinyl", false),
                crackle = p.getFloat("vinyl_crackle", d.crackle),
                warmth = p.getFloat("vinyl_warmth", d.warmth),
                wow = p.getFloat("vinyl_wow", d.wow),
                surface = p.getFloat("vinyl_surface", d.surface),
            )
        )
    }

    private const val PREFS = "ctify-fx"
}
