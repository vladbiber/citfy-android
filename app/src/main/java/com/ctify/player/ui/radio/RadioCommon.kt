package com.ctify.player.ui.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.ctify.player.CtifyApp
import com.ctify.player.net.Feature
import com.ctify.player.net.NetworkGate
import com.ctify.player.player.PlayerHub
import com.ctify.player.radio.RadioRepository
import com.ctify.player.radio.Station
import com.ctify.player.radio.StationSource
import com.ctify.player.ui.EmptyHint
import com.ctify.player.ui.theme.Accent
import com.ctify.player.ui.theme.SurfaceElevated
import com.ctify.player.ui.theme.TextMuted
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun radioRepo(): RadioRepository = (LocalContext.current.applicationContext as CtifyApp).radio

/** Why radio can't use the network right now, or null; recomposes when the switches change. */
@Composable
internal fun rememberRadioBlock(): String? {
    val offline by NetworkGate.offline.collectAsState()
    val enabled by NetworkGate.enabled.collectAsState()
    return remember(offline, enabled) { NetworkGate.blockReason(Feature.RADIO) }
}

/** What every station row needs to know: hearts and the station on air. */
internal class RadioUi(
    val favoriteIds: Set<String>,
    val currentUuid: String?,
    val isPlaying: Boolean,
    val toggleFavorite: (Station) -> Unit,
)

@Composable
internal fun rememberRadioUi(): RadioUi {
    val radio = radioRepo()
    val favs by radio.favoriteIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val now by PlayerHub.playback.collectAsState()
    return RadioUi(favs, now?.station?.uuid, now?.isPlaying == true) { s -> radio.toggleFavorite(s, s.uuid in favs) }
}

/** A list that loads from the network, shows the saved copy first and keeps it on failure. */
@Stable
internal class LoadState<T> {
    var data by mutableStateOf<T?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var attempt by mutableIntStateOf(0)
    fun retry() { attempt++ }
}

@Composable
internal fun <T> rememberLoad(
    vararg keys: Any?,
    blocked: String?,
    cached: suspend () -> T? = { null },
    load: suspend () -> Result<T>,
): LoadState<T> {
    val state = remember(*keys) { LoadState<T>() }
    val currentLoad by rememberUpdatedState(load)
    val currentCached by rememberUpdatedState(cached)
    LaunchedEffect(state, state.attempt, blocked) {
        if (state.data == null) state.data = currentCached()
        state.error = null
        if (blocked != null) return@LaunchedEffect
        state.loading = true
        try {
            currentLoad().fold(
                onSuccess = { state.data = it },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    state.error = e.message?.takeIf { it.isNotBlank() } ?: "No answer from the station directory"
                },
            )
        } finally {
            state.loading = false
        }
    }
    return state
}

/** Blocked, loading, error and empty states above a list. */
internal fun <T> LazyListScope.loadStatus(state: LoadState<T>, blocked: String?, isEmpty: (T) -> Boolean, emptyText: String) {
    val data = state.data
    val hasData = data != null && !isEmpty(data)
    when {
        blocked != null -> item(key = "blocked") { BlockedNote(blocked) }
        state.error != null && hasData -> item(key = "stale") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Couldn't refresh, showing the saved list", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = state::retry) { Text("Retry") }
            }
        }
        state.error != null -> item(key = "error") { ErrorBlock(state.error!!, state::retry) }
    }
    if (state.loading) {
        item(key = "loading") {
            if (hasData) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(2.dp), color = Accent)
            } else {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
        }
    } else if (!hasData && state.error == null && blocked == null && data != null) {
        item(key = "empty") { EmptyHint(emptyText) }
    }
}

@Composable
internal fun BlockedNote(reason: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).alpha(0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(reason, color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Couldn't load stations", color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(message, color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = onRetry) { Text("Retry") }
    }
}

/** Station rows; a tap plays [list] from that row so next and previous stay in it. */
internal fun LazyListScope.stationRows(
    list: List<Station>,
    ui: RadioUi,
    keyPrefix: String = "s",
    onDelete: ((Station) -> Unit)? = null,
) {
    itemsIndexed(list, key = { _, s -> keyPrefix + s.uuid }) { i, s ->
        StationRow(
            station = s,
            isCurrent = s.uuid == ui.currentUuid,
            isPlaying = ui.isPlaying && s.uuid == ui.currentUuid,
            isFavorite = s.uuid in ui.favoriteIds,
            onClick = { PlayerHub.playStations(list, i) },
            onToggleFavorite = { ui.toggleFavorite(s) },
            onDelete = onDelete?.let { { it(s) } },
        )
    }
}

internal fun Station.subtitle(): String = listOfNotNull(
    description?.takeIf { source == StationSource.CLIAMP },
    detail.ifEmpty { null },
    listeners?.takeIf { source == StationSource.CLIAMP }?.let { "$it listening" },
).joinToString(" · ").ifEmpty { "Live radio" }

@Composable
internal fun StationRow(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) Accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClickLabel = "play", onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = "${station.name}, ${station.subtitle()}"
                if (isCurrent) stateDescription = if (isPlaying) "now playing" else "paused"
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                StationArt(station, 50.dp)
                if (isCurrent) {
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.Pause, null, tint = Accent, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    station.name, color = if (isCurrent) Accent else TextPrimary, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(station.subtitle(), color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (isFavorite) "Remove ${station.name} from favorites" else "Add ${station.name} to favorites",
                tint = if (isFavorite) Accent else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete ${station.name}", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The station logo, or a radio icon when there is none or it fails to load. */
@Composable
internal fun StationArt(station: Station, size: Dp, corner: Dp = 10.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner)).background(SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        val fallback = @Composable {
            Icon(Icons.Filled.Radio, null, tint = TextSecondary, modifier = Modifier.size(size / 2.4f))
        }
        if (station.favicon != null) {
            SubcomposeAsyncImage(
                model = station.favicon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { fallback() },
                error = { fallback() },
            )
        } else fallback()
    }
}

/** "RO" to the flag emoji. */
internal fun flagOf(code: String): String =
    if (code.length != 2 || !code.all { it.isLetter() }) ""
    else code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

/** The small "LIVE" badge shown instead of a seek bar while radio plays. */
@Composable
fun LiveBadge(color: Color = Accent, modifier: Modifier = Modifier) {
    Text(
        "LIVE",
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
