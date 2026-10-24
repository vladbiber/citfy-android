package com.ctify.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ctify.player.ui.skinSide
import com.ctify.player.ui.skins.Skin
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Swipe through the skins, drawn still; Select applies the centred one, outside or back cancels. */
@Composable
fun SkinPicker(
    current: Skin,
    preview: @Composable (skin: Skin, side: Dp) -> Unit,
    onSelect: (Skin) -> Unit,
    onDismiss: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = current.ordinal) { Skin.entries.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(
                Modifier.padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Player look", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 56.dp),
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                ) { page ->
                    val s = Skin.entries[page]
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val off = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceAtMost(1f)
                                scaleX = lerp(1f, 0.85f, off); scaleY = scaleX
                                alpha = lerp(1f, 0.5f, off)
                            }
                            .clickable(role = Role.Tab) { scope.launch { pager.animateScrollToPage(page) } }
                            // the preview is the real skin: keep its play/seek accessibility actions out
                            .clearAndSetSemantics {
                                contentDescription = "${s.label} look"
                                selected = page == pager.currentPage
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            preview(s, skinSide(s, maxWidth, maxHeight))
                            // on top, so touches skip the skin's own play/scratch/seek gestures and reach the pager
                            Box(Modifier.matchParentSize().pointerInput(Unit) {})
                        }
                        Text(s.label, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Button(onClick = { onSelect(Skin.entries[pager.currentPage]) }) { Text("Select") }
            }
        }
    }
}
