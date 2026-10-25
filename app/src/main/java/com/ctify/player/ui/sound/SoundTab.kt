package com.ctify.player.ui.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Every sound setting: presets, speed, reverb, bass, EQ, then effects, output and the range; [radio] leaves out vinyl and reverse. */
@Composable
fun SoundTab(modifier: Modifier = Modifier, radio: Boolean = false) {
    val context = LocalContext.current
    remember { SoundEngine.init(context) }
    val sections = listOfNotNull<@Composable () -> Unit>(
        { PresetStatus() },
        { GenrePresetsRow() },
        { SpeedSection() },
        { ReverbSection() },
        { BassSection() },
        { EqSection() },
        { Heading("Effects") },
        { TuningSection() },
        { SpaceSection() },
        { RainSection() },
        if (radio) null else { { VinylSection() } },
        if (radio) null else { { ReverseSection() } },
        { Heading("Output") },
        { HeadphonesSection() },
        { HifiSection() },
        { Heading("Range") },
        { EffectRangeSection() },
        { ResetFxButton() },
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // each section emits a single node, so the wrapper keeps the spacing as it was
        sections.forEachIndexed { i, section -> Box(Modifier.enterStagger(i)) { section() } }
    }
}

@Composable
private fun Heading(text: String) = SectionHeader(text, Modifier.padding(top = 12.dp, start = 4.dp))
