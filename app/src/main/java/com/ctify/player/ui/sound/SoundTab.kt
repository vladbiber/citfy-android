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
import com.ctify.player.ui.SoundPanel

/** The Sound tab top to bottom: suggestion, genre presets, headphones, hi-fi, EQ, effects. */
@Composable
fun SoundTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { SoundEngine.init(context) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SuggestionCard()
        GenrePresetsRow()
        HorizontalDivider()
        HeadphonesSection()
        HifiSection()
        EqSection()
        Text(
            "Effects", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
        )
        SoundPanel()
    }
}
