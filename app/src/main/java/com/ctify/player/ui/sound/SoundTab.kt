package com.ctify.player.ui.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ctify.player.player.sound.SoundEngine
import com.ctify.player.ui.BassSection
import com.ctify.player.ui.EffectRangeSection
import com.ctify.player.ui.RainSection
import com.ctify.player.ui.ResetFxButton
import com.ctify.player.ui.ReverbSection
import com.ctify.player.ui.ReverseSection
import com.ctify.player.ui.SectionHeader
import com.ctify.player.ui.SpaceSection
import com.ctify.player.ui.SpeedSection
import com.ctify.player.ui.TuningSection
import com.ctify.player.ui.VinylSection
import com.ctify.player.ui.enterStagger

/** Every sound setting, top to bottom: suggestion, genre presets, effects, then headphones, hi-fi, EQ. */
@Composable
fun SoundTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { SoundEngine.init(context) }
    val sections = listOf<@Composable () -> Unit>(
        { SuggestionCard() },
        { GenrePresetsRow() },
        { SectionHeader("Effects", Modifier.padding(top = 4.dp)) },
        { SpeedSection() },
        { ReverbSection() },
        { BassSection() },
        { TuningSection() },
        { SpaceSection() },
        { RainSection() },
        { VinylSection() },
        { ReverseSection() },
        { EffectRangeSection() },
        { ResetFxButton() },
        { HorizontalDivider() },
        { HeadphonesSection() },
        { HifiSection() },
        { EqSection() },
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // each section emits a single node, so the wrapper keeps the spacing as it was
        sections.forEachIndexed { i, section -> Box(Modifier.enterStagger(i)) { section() } }
    }
}
