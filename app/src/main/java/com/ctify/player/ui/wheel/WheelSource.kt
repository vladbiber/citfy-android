package com.ctify.player.ui.wheel

import com.ctify.player.data.MusicRepository
import com.ctify.player.data.Track
import com.ctify.player.player.PlayerHub
import com.ctify.player.radio.RadioRepository
import com.ctify.player.radio.Station
import kotlinx.coroutines.flow.first

/** One slice on the wheel. [ref] is the Track or Station behind it. */
data class WheelItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val cover: String?,
    val ref: Any,
)

/** Where the wheel's candidates come from. Streaming sources plug in here later. */
interface WheelSource {
    val id: String
    val label: String
    /** "songs" or "stations", for the odds line */
    val noun: String
    /** why this source can't be used now, or null */
    val unavailable: String? get() = null
    /** key for the no-repeat deck; changes when the pool's filter changes */
    val poolKey: String get() = id

    /** the whole pool the winner is drawn from */
    suspend fun pool(): List<WheelItem>

    /** runs while the wheel cruises (stream URL, pre-buffer); false drops the winner */
    suspend fun prepare(winner: WheelItem): Boolean = true

    fun play(winner: WheelItem, wheel: List<WheelItem>)

    fun isFavorite(item: WheelItem): Boolean = false
    fun toggleFavorite(item: WheelItem) {}
    val canDownload: Boolean get() = false
    fun download(item: WheelItem) {}
}

/** Which part of the library the library source spins over. */
sealed interface LibraryScope {
    data object All : LibraryScope
    data object Favorites : LibraryScope
    data class InPlaylist(val name: String) : LibraryScope
}

internal fun Track.toWheelItem() = WheelItem(id, title, artist, coverUri, this)
internal fun Station.toWheelItem() = WheelItem(uuid, name, detail, favicon, this)

class LibrarySource(private val repo: MusicRepository, val scope: LibraryScope) : WheelSource {
    override val id = "library"
    override val label = "Your library"
    override val noun = "songs"
    override val poolKey: String
        get() = when (scope) {
            LibraryScope.All -> "lib"
            LibraryScope.Favorites -> "lib:fav"
            is LibraryScope.InPlaylist -> "lib:pl:${scope.name}"
        }

    override suspend fun pool(): List<WheelItem> = when (scope) {
        LibraryScope.All -> repo.tracks.value
        LibraryScope.Favorites -> repo.favoriteTracks()
        is LibraryScope.InPlaylist -> repo.playlistTracks(scope.name)
    }.map { it.toWheelItem() }

    override fun play(winner: WheelItem, wheel: List<WheelItem>) =
        PlayerHub.playQueue(listOf(winner.id) + wheel.map { it.id }.filter { it != winner.id }.shuffled(), 0)
    override fun isFavorite(item: WheelItem) = repo.isFavorite(item.id)
    override fun toggleFavorite(item: WheelItem) = repo.toggleFavorite(item.id)
}

/** A fixed station list, e.g. the radio tab that is open. */
class StationListSource(
    override val id: String,
    override val label: String,
    private val radio: RadioRepository,
    private val load: suspend () -> List<Station>,
) : WheelSource {
    override val noun = "stations"
    private var favs: Set<String> = emptySet()

    override suspend fun pool(): List<WheelItem> {
        favs = radio.favoriteIds.first()
        return load().distinctBy { it.uuid }.map { it.toWheelItem() }
    }

    override fun play(winner: WheelItem, wheel: List<WheelItem>) {
        val list = wheel.map { it.ref as Station }
        PlayerHub.playStations(list, wheel.indexOf(winner).coerceAtLeast(0))
    }

    override fun isFavorite(item: WheelItem) = item.id in favs
    override fun toggleFavorite(item: WheelItem) {
        val on = item.id in favs
        radio.toggleFavorite(item.ref as Station, on)
        favs = if (on) favs - item.id else favs + item.id
    }
}

/** Saved stations from the pinned genre tabs, else the favorites. No network. */
fun liveRadioSource(radio: RadioRepository) = StationListSource("live", "Live radio", radio) {
    val fromTabs = radio.tabs.first().flatMap { radio.cached("tab_${it.id}").orEmpty() }
    fromTabs.ifEmpty { radio.favorites.first() }
}

/** A YouTube source that isn't built yet; shown as a disabled chip. */
class PendingSource(override val id: String, override val label: String) : WheelSource {
    override val noun = "songs"
    override val unavailable = "needs YouTube"
    override suspend fun pool(): List<WheelItem> = emptyList()
    override fun play(winner: WheelItem, wheel: List<WheelItem>) {}
}

fun pendingYouTubeSources(repo: MusicRepository) = listOf(
    GenreRouletteSource(repo),
    PendingSource("deep", "Deep random"),
    PendingSource("charts", "World charts"),
    PendingSource("taste", "Your taste"),
)
