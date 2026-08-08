package com.orienteer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class MapOverlayColors(
    val plannedPath: Color,
    val actualPath: Color,
    val airLeg: Color
) {
    /** Walking / GPS track color (same as [actualPath]). */
    val walkingPath: Color get() = actualPath
}

val LocalMapOverlayColors = staticCompositionLocalOf {
    MapOverlayColors(
        plannedPath = MapPlannedPath,
        actualPath = MapActualPath,
        airLeg = MapAirLeg
    )
}

private val OrienteerDarkColorScheme = darkColorScheme(
    primary = OrienteerPrimary,
    onPrimary = OrienteerOnPrimary,
    primaryContainer = OrienteerPrimaryContainer,
    onPrimaryContainer = OrienteerOnPrimaryContainer,
    secondary = OrienteerSecondary,
    onSecondary = OrienteerOnSecondary,
    secondaryContainer = OrienteerSecondaryContainer,
    onSecondaryContainer = OrienteerOnSecondaryContainer,
    tertiary = OrienteerTertiary,
    onTertiary = OrienteerOnPrimary,
    tertiaryContainer = OrienteerPrimaryContainer,
    onTertiaryContainer = OrienteerOnPrimaryContainer,
    background = OrienteerBackground,
    onBackground = OrienteerOnBackground,
    surface = OrienteerSurface,
    onSurface = OrienteerOnSurface,
    surfaceVariant = OrienteerSurfaceContainerHighest,
    onSurfaceVariant = OrienteerOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF060E20),
    surfaceContainerLow = OrienteerSurfaceContainerLow,
    surfaceContainer = OrienteerSurfaceContainer,
    surfaceContainerHigh = OrienteerSurfaceContainerHigh,
    surfaceContainerHighest = OrienteerSurfaceContainerHighest,
    outline = OrienteerOutline,
    outlineVariant = OrienteerOutlineVariant,
    error = OrienteerError,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = OrienteerPrimary
)

private val OrienteerLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    surfaceTint = LightSurfaceTint
)

@Composable
fun OrienteerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = themeMode.isDarkTheme(isSystemInDarkTheme())
    val colorScheme = if (darkTheme) OrienteerDarkColorScheme else OrienteerLightColorScheme
    val mapColors = if (darkTheme) {
        MapOverlayColors(
            plannedPath = MapPlannedPath,
            actualPath = MapActualPath,
            airLeg = MapAirLeg
        )
    } else {
        MapOverlayColors(
            plannedPath = LightMapPlannedPath,
            actualPath = LightMapActualPath,
            airLeg = LightMapAirLeg
        )
    }

    CompositionLocalProvider(LocalMapOverlayColors provides mapColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OrienteerTypography,
            shapes = OrienteerShapes,
            content = content
        )
    }
}

@Composable
fun mapOverlayColors(): MapOverlayColors = LocalMapOverlayColors.current
