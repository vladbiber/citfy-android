package com.ctify.player.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ctify.player.ui.theme.TextPrimary

/** The big title at the top of a tab or full-screen page. */
@Composable
internal fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = TextPrimary, style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.padding(vertical = 6.dp).semantics { heading() },
    )
}

/** A heading above a group of rows, chips or cards; callers pass the spacing their list needs. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = TextPrimary, style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { heading() },
    )
}
