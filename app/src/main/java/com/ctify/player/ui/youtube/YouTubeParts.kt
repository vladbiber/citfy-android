package com.ctify.player.ui.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ctify.player.ui.theme.TextPrimary
import com.ctify.player.ui.theme.TextSecondary

/** A cover, a title and a subtitle in one tappable row: albums, playlists, matches. */
@Composable
internal fun CardRow(image: String?, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(image, null, Modifier.size(56.dp).clip(MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(24.dp))
}
