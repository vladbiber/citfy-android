package com.ctify.player.ui.art

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.ctify.player.youtube.YtRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Artist photos from YouTube Music's artist search (Last.fm stopped serving artist images).
 * Kept a week in prefs, misses too, so a Home full of artist circles does not search again on every launch.
 */
object ArtistPhotos {
    private val memory = ConcurrentHashMap<String, String>()
    // a few at a time: a row of twenty circles must not fire twenty searches at once
    private val gate = Semaphore(2)

    suspend fun photo(context: Context, name: String): String? {
        val key = name.trim().lowercase()
        if (key.isEmpty()) return null
        memory[key]?.let { return it.ifEmpty { null } }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(key, null)?.split('\n', limit = 2)
        val at = stored?.getOrNull(0)?.toLongOrNull()
        if (at != null && System.currentTimeMillis() - at < TTL_MS) {
            val url = stored.getOrNull(1).orEmpty()
            memory[key] = url
            return url.ifEmpty { null }
        }
        val url = try { gate.withPermit { search(name) } } catch (e: CancellationException) { throw e } catch (_: Exception) { return null }
        memory[key] = url.orEmpty()
        prefs.edit().putString(key, "${System.currentTimeMillis()}\n${url.orEmpty()}").apply()
        return url
    }

    /** The first artist whose name matches, with its picture asked for at a size fit for a header. */
    private suspend fun search(name: String): String? = withContext(Dispatchers.IO) {
        YtRuntime.ensure()
        val f = ServiceList.YouTube.searchQHFactory.availableContentFilter.filterGroups
            .flatMap { it.filterItems.asList() }.firstOrNull { it.name == YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS } ?: return@withContext null
        val info = SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.searchQHFactory.fromQuery(name, listOf(f), null))
        val want = normalize(name)
        val hit = info.relatedItems.filterIsInstance<ChannelInfoItem>().firstOrNull { normalize(it.name) == want } ?: return@withContext null
        hit.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(::large)
    }

    internal fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** googleusercontent pictures carry their size in the URL; ask for a bigger one. */
    internal fun large(url: String): String {
        val u = if (url.startsWith("//")) "https:$url" else url
        return u.replace(Regex("=w\\d+-h\\d+[^/]*$"), "=w544-h544-l90-rj")
    }

    private const val PREFS = "artist-photos"
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
}

/** The artist's photo URL once found; null while looking or when there is none. */
@Composable
fun rememberArtistPhoto(name: String, known: String? = null): String? {
    val ctx = LocalContext.current
    return produceState(known, name, known) {
        if (known == null) value = ArtistPhotos.photo(ctx, name)
    }.value
}
