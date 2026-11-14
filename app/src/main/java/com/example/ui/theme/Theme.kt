package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColorGoldDark = Color(0xFF9A7B16)

private val JaxDarkColorScheme = darkColorScheme(
    primary = MetallicGold,
    onPrimary = ObsidianBlack,
    primaryContainer = GoldContainer,
    onPrimaryContainer = BrightGold,
    secondary = EmeraldBullish,
    onSecondary = ObsidianBlack,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldBullish,
    tertiary = CyanInfo,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = CrispWhite,
    surface = BlackGlass,
    onSurface = CrispWhite,
    surfaceVariant = BlackGlassElevated,
    onSurfaceVariant = CoolGrey,
    outline = GlassBorder,
    outlineVariant = MutedSlate,
    error = DeepRedBearish,
    onError = CrispWhite,
    errorContainer = CrimsonContainer,
    onErrorContainer = DeepRedBearish
)

private val JaxLightColorScheme = lightColorScheme(
    primary = ColorGoldDark,
    onPrimary = CrispWhite,
    primaryContainer = GoldContainer,
    onPrimaryContainer = BrightGold,
    secondary = EmeraldBullish,
    onSecondary = CrispWhite,
    background = LightBg,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = MutedSlate,
    error = DeepRedBearish,
    onError = CrispWhite
)

val JaxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun JaxOmniverseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JaxDarkColorScheme else JaxLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JaxTypography,
        shapes = JaxShapes,
        content = content
    )
}
