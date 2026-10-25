package com.ctify.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.Alignment
import com.ctify.player.recommend.UpNext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ctify.player.CtifyApp
import com.ctify.player.player.PlayerHub
import com.ctify.player.player.sound.SoundEngine
import com.ctify.player.ui.sound.GenrePresetsRow
import com.ctify.player.ui.sound.SoundTab
import com.ctify.player.ui.sound.SuggestionCard

@Composable
internal fun SoundSheet(radio: Boolean = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        Text("Sound", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        SoundTab(radio = radio)
        val uri = LocalUriHandler.current
        Text(
            "Player designs inspired by Spun by @yappologistic",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "open Spun on GitHub") {
                    uri.openUri("https://github.com/yappologistic/Spun")
                }
                .padding(vertical = 14.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** What a player chip opens: only that control, in its own small sheet. */
internal enum class ChipPanel { GENRE, SPEED, REVERB, BASS, HZ, SPACE, MORE }

@Composable
internal fun ChipPanelSheet(panel: ChipPanel, radio: Boolean, onEqualizer: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (panel) {
            ChipPanel.GENRE -> {
                val context = LocalContext.current
                remember { SoundEngine.init(context) }
                SuggestionCard()
                GenrePresetsRow()
            }
            ChipPanel.SPEED -> SpeedSection()
            ChipPanel.REVERB -> ReverbSection()
            ChipPanel.BASS -> BassSection()
            ChipPanel.HZ -> TuningSection()
            ChipPanel.SPACE -> SpaceSection()
            ChipPanel.MORE -> {
                CtButton(onClick = onEqualizer, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Equalizer and all sound settings")
                }
                RainSection()
                // a live stream can't be played backwards, and vinyl wear is for records
                if (!radio) {
                    VinylSection()
                    ReverseSection()
                }
            }
        }
    }
}
