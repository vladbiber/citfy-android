package com.ctify.player.radio

import com.ctify.player.genre.Genre
import com.ctify.player.integration.Provider
import com.ctify.player.integration.ProviderChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/** Radio state the UI and the player share: directory lookups, favorites, recents, tabs. */
class RadioRepository(private val dao: RadioDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val browser = RadioBrowser()
    private val cliampRadio = CliampRadio()

    /** Directory calls go through a chain so failures are counted and filed in diagnostics. */
    val directory = ProviderChain("Radio", listOf(Provider("Radio Browser", "radio-browser.info API", browser)))
    private val cliamp = ProviderChain("Radio", listOf(Provider("cliamp channels", "radio.cliamp.stream", cliampRadio)))

    val favorites: Flow<List<Station>> = dao.favorites()
    val favoriteIds: Flow<Set<String>> = dao.favoriteIds().map { it.toSet() }
    val recents: Flow<List<Station>> = dao.recents()
    val customStations: Flow<List<Station>> = dao.customStations()
    val tabs: Flow<List<RadioTab>> = dao.tabs()

    // ---- lists ----

    suspend fun search(q: StationQuery, cacheKey: String? = null): Result<List<Station>> {
        val r = directory.run("load stations", cacheKey ?: q.name) { it.search(q) }
        r.onSuccess { list -> if (cacheKey != null) dao.putCache(RadioCacheEntry(cacheKey, Station.toJson(list), System.currentTimeMillis())) }
        return r
    }

    suspend fun cached(key: String): List<Station>? = dao.cached(key)?.let { Station.fromJson(it.json) }

    suspend fun cliampChannels(): Result<List<Station>> {
        val r = cliamp.run("load cliamp channels") { it.stations() }
        r.onSuccess { dao.putCache(RadioCacheEntry(CLIAMP_KEY, Station.toJson(it), System.currentTimeMillis())) }
        return r
    }

    suspend fun tagIndex(): Result<List<TagCount>> = directory.run("load genre list") { it.tags() }
    suspend fun countries(): Result<List<CountryCount>> = directory.run("load countries") { it.countries() }
    suspend fun regions(countryName: String): Result<List<TagCount>> = directory.run("load regions", countryName) { it.states(countryName) }

    /** Query for a pinned tab: a genre family expands to its tags, a raw tag stays exact. */
    fun queryFor(tab: RadioTab, offset: Int = 0) = StationQuery(
        tags = when (tab.kind) {
            TabKind.GENRE -> runCatching { Genre.valueOf(tab.key).tags }.getOrDefault(listOf(tab.key))
            TabKind.TAG -> listOf(tab.key)
        },
        countryCode = tab.countryCode,
        sort = tab.sort,
        minBitrate = tab.minBitrate,
        hideBroken = tab.hideBroken,
        offset = offset,
    )

    /**
     * "For you": the cliamp channels, then stations in the genres the user's favorite and
     * recent stations lean to. With no history yet, popular stations fill in.
     */
    suspend fun forYou(recent: List<Station>, favs: List<Station>): Result<List<Station>> {
        val channels = cliampChannels().getOrElse { cached(CLIAMP_KEY).orEmpty() }
        val top = (favs + favs + recent).mapNotNull { it.genre }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(3).map { it.key }
        val q = if (top.isEmpty()) StationQuery(sort = StationSort.VOTES, limit = 40)
        else StationQuery(tags = top.flatMap { it.tags.take(3) }, sort = StationSort.VOTES, limit = 50)
        val directoryPart = search(q, FOR_YOU_KEY)
        if (directoryPart.isFailure && channels.isEmpty()) return directoryPart
        val list = (channels + directoryPart.getOrElse { cached(FOR_YOU_KEY).orEmpty() }).distinctBy { it.uuid }
        return Result.success(list)
    }

    /** Other directory entries with the same station name: backup streams when one keeps failing. */
    suspend fun siblings(station: Station): List<Station> {
        if (station.source != StationSource.DIRECTORY) return emptyList()
        return directory.run("find backup streams", station.uuid) {
            it.search(StationQuery(name = station.name, limit = 10, sort = StationSort.VOTES))
        }.getOrDefault(emptyList())
            .filter { it.uuid != station.uuid && it.name.equals(station.name, ignoreCase = true) && it.streamUrl != station.streamUrl }
    }

    // ---- user state ----

    fun toggleFavorite(station: Station, isFavorite: Boolean) = scope.launch {
        if (isFavorite) dao.removeFavorite(station.uuid)
        else {
            dao.upsertStations(listOf(station))
            dao.addFavorite(StationFavorite(station.uuid, System.currentTimeMillis()))
        }
    }

    /** Called when a station starts: remembers it and, for directory stations, reports the click. */
    fun recordPlay(station: Station) = scope.launch {
        dao.upsertStations(listOf(station))
        dao.addRecent(StationRecent(station.uuid, System.currentTimeMillis()))
        dao.trimRecents()
        if (station.source == StationSource.DIRECTORY) runCatching { browser.reportClick(station.uuid) }
    }

    suspend fun stationById(uuid: String): Station? = dao.station(uuid)

    /** Adds a station by URL after checking it resolves to a stream. */
    suspend fun addCustom(name: String, url: String, tags: String): Result<Station> = runCatching {
        val resolved = StreamResolver.resolve(url.trim())
        val station = Station(
            uuid = "custom-" + UUID.randomUUID(),
            name = name.trim().ifEmpty { "My station" },
            streamUrl = resolved,
            tags = tags.lowercase(),
            source = StationSource.CUSTOM,
            hls = resolved.contains(".m3u8", true),
        )
        dao.upsertStations(listOf(station))
        station
    }

    fun deleteCustom(station: Station) = scope.launch {
        dao.removeFavorite(station.uuid)
        dao.deleteStation(station.uuid)
    }

    // ---- tabs ----

    fun pinTab(kind: TabKind, key: String, title: String) = scope.launch {
        val tabs = dao.tabsNow()
        if (tabs.any { it.kind == kind && it.key == key }) return@launch
        dao.upsertTab(RadioTab(kind = kind, key = key, title = title, position = tabs.size))
    }

    fun updateTab(tab: RadioTab) = scope.launch { dao.upsertTab(tab) }
    fun removeTab(tab: RadioTab) = scope.launch { dao.deleteTab(tab.id) }
    fun reorderTabs(ids: List<Long>) = scope.launch { dao.reorderTabs(ids) }

    companion object {
        const val CLIAMP_KEY = "cliamp"
        const val FOR_YOU_KEY = "for_you"
    }
}
