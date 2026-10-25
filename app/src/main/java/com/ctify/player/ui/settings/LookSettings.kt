package com.ctify.player.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctify.player.ui.theme.AppFont
import com.ctify.player.ui.theme.Appearance
import com.ctify.player.ui.theme.DyeColors
import com.ctify.player.ui.theme.DyeStrength
import com.ctify.player.ui.theme.GradientStyle
import com.ctify.player.ui.theme.ThemeSource

/** Theme colors, backdrop, font and the player tint switches. */
@Composable
internal fun ColumnScope.LookSettings() {
    val source by Appearance.source.collectAsState()
    val color1 by Appearance.color1.collectAsState()
    val color2 by Appearance.color2.collectAsState()
    val gradient by Appearance.gradient.collectAsState()
    val pureBlack by Appearance.pureBlack.collectAsState()
    val coverColors by Appearance.coverColors.collectAsState()
    val reactToMusic by Appearance.reactToMusic.collectAsState()
    val font by Appearance.font.collectAsState()
    val dyeColors by Appearance.dyeColors.collectAsState()
    val dyeStrength by Appearance.dyeStrength.collectAsState()

    SettingLabel("Theme", Modifier.padding(top = 8.dp))
    // Below Android 12 there are no wallpaper colors and Wallpaper looks like Guava Blue, so only Guava Blue is offered.
    val sources = if (Appearance.dynamicSupported) ThemeSource.entries else ThemeSource.entries - ThemeSource.WALLPAPER
    val shownSource = if (source in sources) source else ThemeSource.CTIFY_BLUE
    Choice(sources, shownSource, { it.label }, Appearance::setSource)
    if (source == ThemeSource.DUO) {
        SwatchRow("Color 1", "Play button, selected tab, sliders and switches", color1, Appearance::setColor1)
        SwatchRow("Color 2", "Chips, badges and the gradient. Same as color 1 gives a darker shade.", color2, Appearance::setColor2)
    }
    SettingLabel("Background", Modifier.padding(top = 12.dp))
    Choice(GradientStyle.entries, gradient, { it.label }, Appearance::setGradient)
    if (gradient.dyed) {
        SettingLabel("Dye colors")
        Choice(DyeColors.entries, dyeColors, { it.label }, Appearance::setDyeColors)
        SettingLabel("Dye strength")
        Choice(DyeStrength.entries, dyeStrength, { it.label }, Appearance::setDyeStrength)
    }
    SettingLabel("Font", Modifier.padding(top = 12.dp))
    Choice(AppFont.entries, font, { it.label }, Appearance::setFont, perRow = 3)
    SwitchRow("Pure black", "Black background, for OLED screens", pureBlack, Appearance::setPureBlack)
    SwitchRow("Cover colors in Now Playing", "Tint the player with the album cover instead of the theme", coverColors, Appearance::setCoverColors)
    SwitchRow("React to music", "Player background pulses with the bass. Off in battery saver.", reactToMusic, Appearance::setReactToMusic)
}
