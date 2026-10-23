package com.ctify.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctify.player.CtifyApp
import com.ctify.player.player.PlayerHub

@Composable
internal fun QueueSheet() {
    val repository = (LocalContext.current.applicationContext as CtifyApp).repository
    val ui by PlayerHub.ui.collectAsState()
    Text(
        "Up next", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() },
    )
    LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(ui.queueIds) { index, id ->
            val t = repository.trackById(id) ?: return@itemsIndexed
            TrackRow(
                track = t,
                isCurrent = index == ui.currentIndex,
                isPlaying = ui.isPlaying && index == ui.currentIndex,
                isFavorite = false,
                onClick = { PlayerHub.playQueueIndex(index) },
                onToggleFavorite = null,
            )
        }
    }
}

@Composable
internal fun SoundSheet() {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        Text("Sound", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        SoundPanel()
    }
}
