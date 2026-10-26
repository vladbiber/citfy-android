package com.ctify.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import com.ctify.player.CtifyApp
import com.ctify.player.MainActivity
import com.ctify.player.R
import com.ctify.player.player.Cmd
import com.ctify.player.player.NowState
import com.ctify.player.player.fx.Moods
import com.ctify.player.radio.Station
import com.ctify.player.ui.home.NowPlayingFace
import com.ctify.player.ui.home.RadioStill
import com.ctify.player.ui.home.stillProgress
import com.ctify.player.ui.skinSide
import com.ctify.player.ui.SkinView
import com.ctify.player.ui.skins.Skin
import com.ctify.player.ui.skins.radio.RadioSkin
import com.ctify.player.ui.theme.Appearance
import com.ctify.player.ui.theme.CtifyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The home screen widgets look like the player: each one is the real skin (vinyl, CD, cassette, TP-7, radio)
 * or the Now playing card, drawn by [Snapshot] whenever the song, play state or mode changes, with live buttons under it.
 */
object GuavaWidgets {
    const val PLAY = "com.ctify.player.widget.PLAY"
    const val PLAY_RADIO = "com.ctify.player.widget.PLAY_RADIO"
    const val NEXT = "com.ctify.player.widget.NEXT"
    const val PREVIOUS = "com.ctify.player.widget.PREVIOUS"

    enum class Kind(val provider: Class<*>, val layout: Int) {
        VINYL(VinylWidget::class.java, R.layout.widget_skin),
        CD(CdWidget::class.java, R.layout.widget_skin),
        TAPE(TapeWidget::class.java, R.layout.widget_skin),
        TP7(Tp7Widget::class.java, R.layout.widget_skin),
        RADIO(RadioDeviceWidget::class.java, R.layout.widget_skin),
        NOW(NowWidget::class.java, R.layout.widget_now),
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: Player? = null
    private var command: ((String, Bundle) -> Unit)? = null
    private var last: NowState? = null
    private var cover: Bitmap? = null
    private var coverOf: String? = "none"
    private val pictures = mutableMapOf<Kind, Bitmap>()
    private var drawing: Job? = null

    fun attach(context: Context, player: Player, command: (String, Bundle) -> Unit) {
        this.player = player
        this.command = command
        redraw(context)
    }

    fun detach(context: Context) {
        player = null
        command = null
        last = last?.copy(isPlaying = false)
        redraw(context)
    }

    /** New playback state from the service. */
    fun update(context: Context, state: NowState?) {
        last = state
        redraw(context)
    }

    /** Buttons first (instant), then the pictures again; a newer call cancels a drawing in progress. */
    fun redraw(context: Context, only: Kind? = null) {
        val app = context.applicationContext
        publish(app, only)
        drawing?.cancel()
        drawing = scope.launch {
            // a quick burst of changes (skip, then play) draws once
            delay(250)
            draw(app, only)
        }
    }

    private suspend fun draw(context: Context, only: Kind?) {
        val manager = AppWidgetManager.getInstance(context)
        val kinds = Kind.entries.filter { (only == null || it == only) && ids(context, manager, it).isNotEmpty() }
        if (kinds.isEmpty()) return
        Appearance.init(context)
        Moods.init(context)
        // the position from the player itself: the state may still carry the last song's
        val s = last?.let { st ->
            val p = player ?: return@let st
            if (p.duration > 0) st.copy(progressMs = p.currentPosition.toInt(), durationMs = p.duration) else st
        }
        val station = s?.station ?: if (Kind.RADIO in kinds) lastStation(context) else null
        val art = s?.track?.coverUri ?: s?.station?.favicon
        if (art != coverOf) {
            coverOf = art
            cover = art?.let { load(context, it) }
        }
        val stationArt = station?.favicon?.let { if (it == art) cover else load(context, it) }
        for (kind in kinds) {
            val (w, h) = size(context, manager, kind)
            val pic = Snapshot.render(context, w.px, h.px, w.dpi) {
                CtifyTheme { Face(kind, s, if (kind == Kind.RADIO) stationArt else cover, station) }
            } ?: continue
            pictures.put(kind, pic)?.takeIf { it !== pic }?.recycle()
            publish(context, kind)
        }
    }

    /** What each widget draws: the real skin, frozen at the current song and position. */
    @Composable
    private fun Face(kind: Kind, s: NowState?, art: Bitmap?, station: Station?) {
        val accent = MaterialTheme.colorScheme.primary
        val painter: Painter = art?.let { BitmapPainter(it.asImageBitmap()) } ?: ColorPainter(accent.copy(alpha = 0.6f))
        val playing = s?.isPlaying == true && player != null
        val track = s?.track?.takeIf { s.station == null }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (kind) {
                Kind.NOW -> NowPlayingFace(
                    title = s?.station?.name ?: track?.title ?: "Guava Player",
                    artist = when {
                        s?.station != null -> s.track?.title?.takeIf { it != s.station.name } ?: "Live radio"
                        track != null -> track.artist
                        else -> "Tap to open"
                    },
                    art = painter, accent = accent, playing = playing, animate = false,
                    label = if (track == null && s?.station == null) "Guava" else if (playing) "Now playing" else "Paused",
                    modifier = Modifier.fillMaxSize(),
                ) { Spacer(Modifier.height(72.dp)) }
                Kind.RADIO -> {
                    val w = maxWidth
                    val h = maxHeight
                    RadioStill(
                        RadioSkin.load(LocalContext.current), station,
                        if (s?.station != null) s.track?.title?.takeIf { it != s.station.name } else null,
                        // a station without a logo keeps the radio's own glass, never the last song's cover
                        art?.let { BitmapPainter(it.asImageBitmap()) }, accent, playing = playing && s?.station != null,
                        width = w, height = h,
                    )
                }
                else -> {
                    val skin = when (kind) { Kind.VINYL -> Skin.VINYL; Kind.CD -> Skin.CD; Kind.TAPE -> Skin.TAPE; else -> Skin.TP7 }
                    // right after a restart the player has no duration yet; the song knows its own
                    val dur = s?.durationMs?.takeIf { it > 0 } ?: track?.durationMs ?: 0L
                    val live = stillProgress(if (dur > 0) s?.progressMs?.toLong() ?: 0L else 0L, dur, playing)
                    SkinView(skin, live, painter, accent, track, skinSide(skin, maxWidth, maxHeight), seek = false)
                }
            }
        }
    }

    /** Puts the last picture and fresh buttons on every widget of [only] (or all). */
    private fun publish(context: Context, only: Kind? = null) {
        val manager = AppWidgetManager.getInstance(context)
        for (kind in Kind.entries) {
            if (only != null && kind != only) continue
            val ids = ids(context, manager, kind)
            if (ids.isEmpty()) continue
            runCatching { manager.updateAppWidget(ids, views(context, kind)) }
        }
    }

    private fun views(context: Context, kind: Kind): RemoteViews {
        val v = RemoteViews(context.packageName, kind.layout)
        pictures[kind]?.let { v.setImageViewBitmap(R.id.wg_image, it) }
        val s = last
        val live = player != null
        val radioOn = s?.station != null
        val playing = live && s?.isPlaying == true && (kind != Kind.RADIO || radioOn)
        // off air the radio's skip buttons would change songs, not stations
        val skips = if (kind == Kind.RADIO && !radioOn) android.view.View.GONE else android.view.View.VISIBLE
        v.setViewVisibility(R.id.wg_prev, skips)
        v.setViewVisibility(R.id.wg_next, skips)
        v.setImageViewResource(R.id.wg_play_icon, if (playing) R.drawable.wg_pause else R.drawable.wg_play)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        v.setOnClickPendingIntent(R.id.wg_root, open)
        v.setOnClickPendingIntent(R.id.wg_image, open)
        // the radio's play button tunes in to the last station when songs are playing
        val play = if (kind == Kind.RADIO && !radioOn) PLAY_RADIO else PLAY
        v.setOnClickPendingIntent(R.id.wg_play, if (live || play == PLAY_RADIO) action(context, play) else open)
        v.setOnClickPendingIntent(R.id.wg_next, if (live) action(context, NEXT) else open)
        v.setOnClickPendingIntent(R.id.wg_prev, if (live) action(context, PREVIOUS) else open)
        return v
    }

    internal fun onAction(context: Context, action: String?) {
        val p = player
        if (action == PLAY_RADIO) {
            scope.launch {
                val list = (context.applicationContext as CtifyApp).radio.let { r -> r.recents.first().ifEmpty { r.favorites.first() } }
                if (list.isEmpty() || command == null) { redraw(context); return@launch }
                command?.invoke(Cmd.PLAY_STATIONS, Bundle().apply { putString(Cmd.ARG_STATIONS, Station.toJson(list)); putInt(Cmd.ARG_INDEX, 0) })
            }
            return
        }
        // the app was closed and its player is gone: show the idle look, whose buttons open the app
        if (p == null) { redraw(context); return }
        when (action) {
            PLAY -> if (p.isPlaying) p.pause() else {
                when (p.playbackState) {
                    Player.STATE_IDLE -> p.prepare()
                    Player.STATE_ENDED -> p.seekTo(0)
                }
                p.play()
            }
            NEXT -> command?.invoke(Cmd.NEXT, Bundle.EMPTY)
            PREVIOUS -> command?.invoke(Cmd.PREVIOUS, Bundle.EMPTY)
        }
    }

    private fun action(context: Context, name: String) = PendingIntent.getBroadcast(
        context, name.hashCode(), Intent(context, WidgetActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ids(context: Context, manager: AppWidgetManager, kind: Kind): IntArray =
        runCatching { manager.getAppWidgetIds(ComponentName(context, kind.provider)) }.getOrNull() ?: IntArray(0)

    private data class Dim(val dp: Float, val px: Int, val dpi: Int)

    /** The widget's size in portrait, minus the button pill for the skins, as (width, height) with a shared density. */
    private fun size(context: Context, manager: AppWidgetManager, kind: Kind): Pair<Dim, Dim> {
        val o = ids(context, manager, kind).firstOrNull()?.let { manager.getAppWidgetOptions(it) }
        val (dw, dh) = when (kind) {
            Kind.VINYL, Kind.CD -> 200f to 250f
            Kind.TP7 -> 170f to 330f
            else -> 300f to 190f
        }
        val wDp = (o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0).takeIf { it > 0 }?.toFloat() ?: dw
        val hAll = (o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0).takeIf { it > 0 }?.toFloat() ?: dh
        val hDp = if (kind.layout == R.layout.widget_skin) (hAll - 56f).coerceAtLeast(80f) else hAll
        val scale = minOf(context.resources.displayMetrics.density, MAX_PX / maxOf(wDp, hDp))
        val dpi = (scale * 160).toInt()
        return Dim(wDp, (wDp * scale).toInt(), dpi) to Dim(hDp, (hDp * scale).toInt(), dpi)
    }

    private suspend fun lastStation(context: Context): Station? = withTimeoutOrNull(2000) {
        val r = (context.applicationContext as CtifyApp).radio
        r.recents.first().firstOrNull() ?: r.favorites.first().firstOrNull()
    }

    private suspend fun load(context: Context, uri: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context).data(uri).size(512).allowHardware(false).build()
            context.imageLoader.execute(request).drawable?.toBitmap()
        }.getOrNull()
    }

    private const val MAX_PX = 760f
}

class VinylWidget : GuavaWidgetProvider(GuavaWidgets.Kind.VINYL)
class CdWidget : GuavaWidgetProvider(GuavaWidgets.Kind.CD)
class TapeWidget : GuavaWidgetProvider(GuavaWidgets.Kind.TAPE)
class Tp7Widget : GuavaWidgetProvider(GuavaWidgets.Kind.TP7)
class RadioDeviceWidget : GuavaWidgetProvider(GuavaWidgets.Kind.RADIO)
class NowWidget : GuavaWidgetProvider(GuavaWidgets.Kind.NOW)

open class GuavaWidgetProvider(private val kind: GuavaWidgets.Kind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        GuavaWidgets.redraw(context, kind)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        GuavaWidgets.redraw(context, kind)
    }
}

/** Play, next and previous from a widget. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = GuavaWidgets.onAction(context, intent.action)
}
