package com.ctify.player.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything Home can show, in the default order; [widget]s are off until added. */
enum class HomeSection(val id: String, val title: String, val icon: ImageVector, val widget: Boolean = false) {
    NOW_PLAYING("w_now", "Now playing", Icons.Filled.PlayCircle, widget = true),
    QUICK_PICKS("quick", "Quick picks", Icons.Filled.GridView),
    CONTINUE("continue", "Continue listening", Icons.Filled.Replay),
    FOR_YOU("for_you", "For you", Icons.Filled.AutoAwesome),
    RECENT("recent", "Recently played", Icons.Filled.History),
    PLAYLISTS("playlists", "Your playlists", Icons.AutoMirrored.Filled.QueueMusic),
    LIVE_RADIO("live_radio", "Live on radio now", Icons.Filled.Radio),
    STARS("stars", "From your stars", Icons.Filled.Star),
    PLAYED("played", "From what you played", Icons.AutoMirrored.Filled.TrendingUp),
    REDISCOVER("rediscover", "Rediscover", Icons.Filled.Album),
    DOWNLOADS("downloads", "From your downloads", Icons.Filled.Download),
    MOST("most", "Most played", Icons.Filled.BarChart),
    HEARD("heard", "Heard on the radio", Icons.Filled.Hearing),
    GENRES("genres", "Genres", Icons.Filled.Category),
    CREATORS("creators", "Creators", Icons.Filled.People),
    WORLD("world", "Around the world", Icons.Filled.Public),
    VISUALIZER("w_viz", "Live visualizer", Icons.Filled.Equalizer, widget = true),
    SHORTCUTS("w_shortcuts", "Shortcuts", Icons.Filled.TouchApp, widget = true),
    STATS("w_stats", "Your music in numbers", Icons.Filled.Insights, widget = true),
    CLOCK("w_clock", "Clock", Icons.Filled.AccessTime, widget = true);

    /** One line for the editor, under the name. */
    val hint: String get() = when (this) {
        NOW_PLAYING -> "Big cover, controls and the time bar"
        VISUALIZER -> "Bars that dance to what's playing"
        SHORTCUTS -> "Shuffle liked, shuffle all, radio, spin the wheel"
        STATS -> "Songs, liked, playlists and artists"
        CLOCK -> "Time and date in big type"
        else -> if (widget) "" else "Shelf"
    }

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Which sections Home shows and in what order; kept in the "ctify-ui" prefs so backup carries it. */
object HomeLayout {
    private const val PREFS = "ctify-ui"
    private const val ORDER = "home_order"
    // every id the user has seen, so a section added in a later version shows up without bringing back removed ones
    private const val KNOWN = "home_known"

    private var prefs: SharedPreferences? = null
    private val defaults = HomeSection.entries.filterNot { it.widget }
    private val state = MutableStateFlow(defaults)
    val shown: StateFlow<List<HomeSection>> = state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString(ORDER, null) ?: return
        val known = p.getString(KNOWN, "").orEmpty().split(',').toSet()
        val order = saved.split(',').filter { it.isNotBlank() }.mapNotNull(HomeSection::byId).distinct()
        state.value = order + defaults.filter { it.id !in known && it !in order }
    }

    fun hidden(): List<HomeSection> = HomeSection.entries - state.value.toSet()

    fun set(list: List<HomeSection>) {
        state.value = list.distinct()
        prefs?.edit()
            ?.putString(ORDER, state.value.joinToString(",") { it.id })
            ?.putString(KNOWN, HomeSection.entries.joinToString(",") { it.id })
            ?.apply()
    }

    fun reset() {
        state.value = defaults
        prefs?.edit()?.remove(ORDER)?.remove(KNOWN)?.apply()
    }
}
