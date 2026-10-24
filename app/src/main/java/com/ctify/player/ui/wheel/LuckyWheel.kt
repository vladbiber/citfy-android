package com.ctify.player.ui.wheel

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ctify.player.data.MusicRepository
import com.ctify.player.lastfm.LastFm
import com.ctify.player.lastfm.Taste
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.recommend.SongRadio
import com.ctify.player.ui.EmptyHint
import com.ctify.player.ui.home.genreLabel
import com.ctify.player.ui.radio.radioRepo
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.youtube.YtSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Random

/** A wheel whose winner is drawn by [weight]; at most [WheelPicker.MAX_SLICES] slices. */
internal fun luckyWheel(pool: List<WheelItem>, weight: (WheelItem) -> Int, rnd: Random): Wheel? {
    if (pool.isEmpty()) return null
    val w = pool.map { weight(it).coerceAtLeast(1) }
    var x = rnd.nextInt(w.sum())
    val winner = pool[w.indices.first { x -= w[it]; x < 0 }]
    val others = pool.filter { it.id != winner.id }.shuffled(rnd).take(WheelPicker.MAX_SLICES - 1).toMutableList()
    val slot = rnd.nextInt(others.size + 1)
    others.add(slot, winner)
    return Wheel(others, slot, pool.size, pool.size)
}

private fun item(id: String, title: String, subtitle: String = "") = WheelItem(id, title, subtitle, null, id)

/** "I'm feeling lucky": spins a genre, then a creator in it, then one of their songs, and plays it from YouTube. */
@Composable
internal fun LuckyWheel(repo: MusicRepository) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val radio = radioRepo()
    val motion = remember { WheelMotion() }
    val glow = remember { Animatable(0f) }
    val rng = remember { SecureRandom() }
    val reduced = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var wheel by remember { mutableStateOf<Wheel?>(null) }
    var running by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf(listOf<String>()) }
    var note by remember { mutableStateOf<String?>(null) }

    if (!LastFm.enabled) {
        EmptyHint("I'm feeling lucky picks a genre, a creator and a song from Last.fm. This build has no Last.fm key, so it is off.")
        return
    }

    suspend fun spinOnce(pool: List<WheelItem>, weight: (WheelItem) -> Int = { 1 }, ok: suspend (WheelItem) -> Boolean = { true }): WheelItem? {
        val w = luckyWheel(pool, weight, rng) ?: return null
        glow.snapTo(0f)
        motion.slices = w.slices.size
        wheel = w
        val slot = motion.spin(
            target = w.winner, v0 = 0f,
            timing = if (reduced) SpinTiming.Reduced else SpinTiming.Normal,
            rnd = rng::nextFloat,
            // a song that isn't on YouTube quietly gives way to another slice
            prepare = { t -> if (ok(w.slices[t])) t else w.slices.indices.filter { it != t }.firstOrNull { ok(w.slices[it]) } ?: t },
            onTick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        )
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (slot != w.winner) wheel = w.copy(winner = slot)
        glow.animateTo(1f, tween(if (reduced) 0 else 520))
        delay(if (reduced) 200 else 700)
        return w.slices[slot]
    }

    fun go() {
        if (running) return
        running = true
        steps = emptyList()
        note = null
        scope.launch {
            try {
                NetworkGate.blockReason(Feature.LASTFM)?.let { note = it; return@launch }
                val mine = Taste.myGenres(repo, radio)
                val tags = (LastFm.topTags().take(30) + mine).distinct()
                val genre = spinOnce(tags.map { item(it, genreLabel(it)) }, { Taste.weight(it.id, mine) }) ?: run { note = "Last.fm sent no genres"; return@launch }
                steps = listOf(genre.title)
                val artist = spinOnce(LastFm.tagArtists(genre.id).map { item(it.name, it.name, genre.title) }.distinctBy { it.id }) ?: run { note = "No creators found for ${genre.title}"; return@launch }
                steps = steps + artist.title
                val found = HashMap<String, YtSong>()
                val song = spinOnce(LastFm.artistTracks(artist.id).map { item(it.title, it.title, artist.title) }.distinctBy { it.id }) { s ->
                    found[s.id] != null || SongRadio.find(artist.id, s.id)?.also { found[s.id] = it } != null
                }
                val yt = song?.let { found[it.id] } ?: run { note = "None of ${artist.title}'s top songs were found on YouTube"; return@launch }
                steps = steps + song.title
                SongRadio.play(repo, yt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note = e.message ?: "Couldn't reach Last.fm"
            } finally {
                running = false
            }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Three spins: a genre, a creator from it, then one of their songs.",
            color = TextMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val w = wheel
        if (w != null) WheelStage(w, motion, glow = { glow.value }, onFling = { go() }, modifier = Modifier.padding(horizontal = 8.dp))
        else Spacer(Modifier.height(24.dp))
        Text(
            listOf("Genre", "Creator", "Song").zip(steps).joinToString("  ·  ") { (k, v) -> "$k: $v" },
            color = TextPrimary, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        note?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp)) }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Button(onClick = ::go, enabled = !running, colors = ButtonDefaults.buttonColors(containerColor = Accent), modifier = Modifier.height(52.dp)) {
                Text(if (running) "Spinning…" else if (steps.size == 3) "Feeling lucky again" else "I'm feeling lucky", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
