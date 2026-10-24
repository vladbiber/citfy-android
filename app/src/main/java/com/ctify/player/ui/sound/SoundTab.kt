package com.ctify.player.ui.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctify.player.player.sound.SoundEngine
import com.ctify.player.ui.BassSection
import com.ctify.player.ui.EffectRangeSection
import com.ctify.player.ui.MoodsSection
import com.ctify.player.ui.RainSection
import com.ctify.player.ui.ResetFxButton
import com.ctify.player.ui.ReverbSection
import com.ctify.player.ui.ReverseSection
import com.ctify.player.ui.SpaceSection
import com.ctify.player.ui.SpeedSection
import com.ctify.player.ui.TuningSection
import com.ctify.player.ui.VinylSection

/** Every sound setting, top to bottom: suggestion, moods, genre presets, effects, then headphones, hi-fi, EQ. */
@Composable
fun SoundTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { SoundEngine.init(context) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SuggestionCard()
        MoodsSection()
        GenrePresetsRow()
        Text(
            "Effects", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
        )
        SpeedSection()
        ReverbSection()
        BassSection()
        TuningSection()
        SpaceSection()
        RainSection()
        VinylSection()
        ReverseSection()
        EffectRangeSection()
        ResetFxButton()
        HorizontalDivider()
        HeadphonesSection()
        HifiSection()
        EqSection()
    }
}
