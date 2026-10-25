package com.ctify.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ctify.player.BuildConfig
import com.ctify.player.ui.SectionHeader

/** One credit line; [note] says what it gives the app. */
internal data class AboutCredit(val name: String, val url: String, val license: String?, val note: String)

// switch to Credits.entries once ui/theme/Credits.kt lands
internal val AboutCredits = listOf(
    AboutCredit("Levyra", "https://github.com/LUC4N3X/Levyra-deepsound", "GPL-3.0", "Expressive shapes style"),
    AboutCredit("NewPipe Extractor", "https://github.com/TeamNewPipe/NewPipeExtractor", "GPL-3.0", "YouTube search, streams and downloads"),
    AboutCredit("Radio Browser", "https://www.radio-browser.info", null, "Radio station directory"),
    AboutCredit("LRCLIB", "https://lrclib.net", null, "Synced lyrics"),
    AboutCredit("Last.fm", "https://www.last.fm", null, "Data from Last.fm: genres, artists and charts"),
    AboutCredit("MusicBrainz", "https://musicbrainz.org", null, "Album info and ratings"),
    AboutCredit("ListenBrainz", "https://listenbrainz.org", null, "Listening history"),
)

private const val REPO = "https://github.com/vladbiber/guava-player"

/** App name, version, license, source link and credits. */
@Composable
internal fun ColumnScope.AboutSettings(credits: List<AboutCredit> = AboutCredits) {
    Text("Guava Player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Free software under the GPL-3.0 license", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Link("Source code on GitHub", REPO)

    SectionHeader("Credits", Modifier.padding(top = 20.dp, bottom = 4.dp))
    credits.forEach { c ->
        Column(Modifier.padding(vertical = 6.dp)) {
            Link(c.name, c.url, compact = true)
            Text(
                listOfNotNull(c.note, c.license).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Link(text: String, url: String, compact: Boolean = false) {
    val uri = LocalUriHandler.current
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Open $url") { runCatching { uri.openUri(url) } }
            .padding(vertical = if (compact) 4.dp else 12.dp),
    )
}
