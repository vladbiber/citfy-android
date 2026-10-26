package com.ctify.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import com.ctify.player.MainActivity
import com.ctify.player.R
import com.ctify.player.player.Cmd
import com.ctify.player.player.NowState
import com.ctify.player.player.fx.Mood
import com.ctify.player.player.fx.Moods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The home screen widgets: a slim bar, a big card and a record. The service feeds them; their buttons reach the player directly. */
object GuavaWidgets {
    const val PLAY = "com.ctify.player.widget.PLAY"
    const val NEXT = "com.ctify.player.widget.NEXT"
    const val PREVIOUS = "com.ctify.player.widget.PREVIOUS"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: Player? = null
    private var command: ((String) -> Unit)? = null
    private var last: NowState? = null
    private var cover: Bitmap? = null
    private var coverOf: String? = null
    private var loading: Job? = null

    private val kinds = listOf(
        BarWidget::class.java to R.layout.widget_bar,
        CardWidget::class.java to R.layout.widget_card,
        DiscWidget::class.java to R.layout.widget_disc,
    )

    /** Called by the playback service when its player exists; [command] runs a [Cmd] action. */
    fun attach(context: Context, player: Player, command: (String) -> Unit) {
        this.player = player
        this.command = command
        render(context)
    }

    fun detach(context: Context) {
        player = null
        command = null
        last = last?.copy(isPlaying = false)
        render(context)
    }

    /** New playback state; the cover is fetched again only when the song changes. */
    fun update(context: Context, state: NowState?) {
        last = state
        val art = state?.track?.coverUri ?: state?.station?.favicon
        if (art != coverOf) {
            coverOf = art
            cover = null
            loading?.cancel()
            loading = scope.launch {
                cover = art?.let { load(context, it) }
                render(context)
            }
        }
        render(context)
    }

    internal fun onAction(context: Context, action: String?) {
        val p = player
        // the app was closed and its player is gone: show the idle look, whose buttons open the app
        if (p == null) { render(context); return }
        when (action) {
            PLAY -> if (p.isPlaying) p.pause() else {
                when (p.playbackState) {
                    Player.STATE_IDLE -> p.prepare()
                    Player.STATE_ENDED -> p.seekTo(0)
                }
                p.play()
            }
            NEXT -> command?.invoke(Cmd.NEXT)
            PREVIOUS -> command?.invoke(Cmd.PREVIOUS)
        }
    }

    internal fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        kinds.forEach { (cls, layout) ->
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, cls)) }.getOrNull() ?: return@forEach
            if (ids.isEmpty()) return@forEach
            manager.updateAppWidget(ids, views(context, layout))
        }
    }

    private fun views(context: Context, layout: Int): RemoteViews {
        val v = RemoteViews(context.packageName, layout)
        val s = last
        val live = player != null && s != null && (s.track != null || s.station != null)
        val station = s?.station
        val title = station?.name ?: s?.track?.title ?: "Guava Player"
        val artist = when {
            station != null -> s.track?.title?.takeIf { it != station.name } ?: "Live radio"
            s?.track != null -> s.track.artist
            else -> "Tap to start the music"
        }
        val (accent, onAccent) = moodColors()
        v.setTextViewText(R.id.wg_title, title)
        v.setTextViewText(R.id.wg_artist, artist)
        v.setTextViewText(R.id.wg_label, if (s?.isPlaying == true) "Now playing" else if (live) "Paused" else "Guava")
        v.setTextColor(R.id.wg_label, accent)
        v.setInt(R.id.wg_play_bg, "setColorFilter", accent)
        v.setInt(R.id.wg_play_icon, "setColorFilter", onAccent)
        v.setImageViewResource(R.id.wg_play_icon, if (s?.isPlaying == true && live) R.drawable.wg_pause else R.drawable.wg_play)

        val art = cover
        when (layout) {
            R.layout.widget_disc -> v.setImageViewBitmap(R.id.wg_cover, record(art, accent))
            R.layout.widget_card -> {
                v.setImageViewBitmap(R.id.wg_cover, rounded(art, accent, 36f))
                if (art != null) v.setImageViewBitmap(R.id.wg_backdrop, soft(art)) else v.setImageViewBitmap(R.id.wg_backdrop, placeholder(accent, 8))
            }
            else -> v.setImageViewBitmap(R.id.wg_cover, rounded(art, accent, 28f))
        }

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        v.setOnClickPendingIntent(R.id.wg_root, open)
        v.setOnClickPendingIntent(R.id.wg_play, if (live) action(context, PLAY, 1) else open)
        v.setOnClickPendingIntent(R.id.wg_next, if (live) action(context, NEXT, 2) else open)
        v.setOnClickPendingIntent(R.id.wg_prev, if (live) action(context, PREVIOUS, 3) else open)
        return v
    }

    private fun action(context: Context, name: String, code: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, WidgetActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The play button and the label follow the app's mode. */
    private fun moodColors(): Pair<Int, Int> = when (runCatching { Moods.mood.value }.getOrDefault(Mood.NORMAL)) {
        Mood.EVIL -> 0xFFD00000.toInt() to Color.WHITE
        Mood.GODLY -> 0xFFFFFF55.toInt() to 0xFF0000AA.toInt()
        Mood.GOOD_EVIL -> Color.WHITE to Color.BLACK
        else -> 0xFF5B9DFF.toInt() to 0xFF0A1428.toInt()
    }

    private suspend fun load(context: Context, uri: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context).data(uri).size(SIZE).allowHardware(false).build()
            context.imageLoader.execute(request).drawable?.toBitmap(SIZE, SIZE)
        }.getOrNull()
    }

    /** A cover-less tile: the accent fading into the night blue. */
    private fun placeholder(accent: Int, size: Int = SIZE): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(b).drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), accent, 0xFF0B0D12.toInt(), Shader.TileMode.CLAMP)
        })
        return b
    }

    private fun rounded(src: Bitmap?, accent: Int, radius: Float): Bitmap {
        val art = src ?: placeholder(accent)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val scale = SIZE / art.width.toFloat()
        paint.shader.setLocalMatrix(android.graphics.Matrix().apply { setScale(scale, SIZE / art.height.toFloat()) })
        Canvas(out).drawRoundRect(RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), radius * SIZE / 160f, radius * SIZE / 160f, paint)
        return out
    }

    /** A cheap blur: shrink a lot, the widget stretches it back. */
    private fun soft(src: Bitmap): Bitmap = Bitmap.createScaledBitmap(src, 12, 12, true)

    /** A black record with grooves and a shine, the cover as its round label. */
    private fun record(src: Bitmap?, accent: Int): Bitmap {
        val n = SIZE
        val c = n / 2f
        val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF0E0E10.toInt()
        canvas.drawCircle(c, c, c - 2, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        var r = c - 10
        while (r > n * 0.2f) {
            p.color = if ((r.toInt() / 6) % 2 == 0) 0xFF1E1E22.toInt() else 0xFF151518.toInt()
            canvas.drawCircle(c, c, r, p)
            r -= 5f
        }
        // the light catching the vinyl
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, n.toFloat(), n.toFloat(), intArrayOf(0x00FFFFFF, 0x22FFFFFF, 0x00FFFFFF), floatArrayOf(0.3f, 0.5f, 0.7f), Shader.TileMode.CLAMP)
        canvas.drawCircle(c, c, c - 2, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = accent
        canvas.drawCircle(c, c, c - 3, p)
        val label = n * 0.29f
        val art = src ?: placeholder(accent)
        p.style = Paint.Style.FILL
        p.shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply {
                setScale(label * 2 / art.width, label * 2 / art.height)
                postTranslate(c - label, c - label)
            })
        }
        canvas.drawCircle(c, c, label, p)
        return out
    }

    private const val SIZE = 320
}

class BarWidget : GuavaWidgetProvider()
class CardWidget : GuavaWidgetProvider()
class DiscWidget : GuavaWidgetProvider()

open class GuavaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = GuavaWidgets.render(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = GuavaWidgets.render(context)
}

/** Play, next and previous from a widget. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = GuavaWidgets.onAction(context, intent.action)
}
