package com.axiim.q.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Built-in "Axiim" theme: a frozen copy of the vibrant teal Material You palette the app was designed
 * against, so the look no longer depends on the phone's wallpaper unless Material You colors are enabled.
 */
val AxiimDarkColorScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFF77E6FF),
    onPrimary = Color(0xFF005360),
    primaryContainer = Color(0xFF00DCFC),
    onPrimaryContainer = Color(0xFF004955),
    inversePrimary = Color(0xFF006574),
    secondary = Color(0xFF00CEFF),
    onSecondary = Color(0xFF004051),
    secondaryContainer = Color(0xFF006780),
    onSecondaryContainer = Color(0xFFEFF9FF),
    tertiary = Color(0xFF7EABFF),
    onTertiary = Color(0xFF002B5F),
    tertiaryContainer = Color(0xFF659DFE),
    onTertiaryContainer = Color(0xFF001F48),
    background = Color(0xFF001017),
    onBackground = Color(0xFFC1ECFF),
    surface = Color(0xFF001017),
    onSurface = Color(0xFFC1ECFF),
    surfaceVariant = Color(0xFF002A36),
    onSurfaceVariant = Color(0xFF81B2C6),
    surfaceTint = Color(0xFF77E6FF),
    inverseSurface = Color(0xFFECF8FF),
    inverseOnSurface = Color(0xFF003342),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF4B7C8F),
    outlineVariant = Color(0xFF174E5F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF00313E),
    surfaceDim = Color(0xFF001017),
    surfaceContainer = Color(0xFF001D26),
    surfaceContainerHigh = Color(0xFF00232E),
    surfaceContainerHighest = Color(0xFF002A36),
    surfaceContainerLow = Color(0xFF00161E),
    surfaceContainerLowest = Color(0xFF000000),
  )

val AxiimLightColorScheme: ColorScheme =
  lightColorScheme(
    primary = Color(0xFF006574),
    onPrimary = Color(0xFFDCF7FF),
    primaryContainer = Color(0xFF00DCFC),
    onPrimaryContainer = Color(0xFF004955),
    inversePrimary = Color(0xFF77E6FF),
    secondary = Color(0xFF00647D),
    onSecondary = Color(0xFFE3F6FF),
    secondaryContainer = Color(0xFF84DDFF),
    onSecondaryContainer = Color(0xFF004E62),
    tertiary = Color(0xFF0959B5),
    onTertiary = Color(0xFFEFF2FF),
    tertiaryContainer = Color(0xFF87B1FF),
    onTertiaryContainer = Color(0xFF002F67),
    background = Color(0xFFECF8FF),
    onBackground = Color(0xFF003342),
    surface = Color(0xFFECF8FF),
    onSurface = Color(0xFF003342),
    surfaceVariant = Color(0xFFA8E6FF),
    onSurfaceVariant = Color(0xFF2F6274),
    surfaceTint = Color(0xFF006574),
    inverseSurface = Color(0xFF001017),
    inverseOnSurface = Color(0xFFC1ECFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF4C7D90),
    outlineVariant = Color(0xFF83B4C8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFECF8FF),
    surfaceDim = Color(0xFF91DFFE),
    surfaceContainer = Color(0xFFC8EEFF),
    surfaceContainerHigh = Color(0xFFB9EAFF),
    surfaceContainerHighest = Color(0xFFA8E6FF),
    surfaceContainerLow = Color(0xFFDEF4FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
  )

/** Glow / accent colors layered on top of the Material scheme (holo blobs, sliders, highlights). */
@Immutable
data class HoloPalette(
  val glow: Color,
  val cyan: Color,
  val electric: Color,
  val deep: Color,
)

/** The signature holo blues used with the Axiim theme. */
val AxiimHoloPalette =
  HoloPalette(
    glow = Color(0xFF33B5E5),
    cyan = Color(0xFF00E5FF),
    electric = Color(0xFF4285F4),
    deep = Color(0xFF0099CC),
  )

/** With Material You colors the accents come from the wallpaper scheme, so nothing stays blue on e.g. a yellow theme. */
fun holoPaletteFrom(scheme: ColorScheme): HoloPalette =
  HoloPalette(
    glow = scheme.secondary,
    cyan = scheme.primary,
    electric = scheme.tertiary,
    deep = scheme.secondary,
  )

val LocalHoloPalette = staticCompositionLocalOf { AxiimHoloPalette }

// Glowing Holo accents, resolved from the active theme
val HoloBlueGlow: Color
  @Composable @ReadOnlyComposable get() = LocalHoloPalette.current.glow
val HoloCyanGlow: Color
  @Composable @ReadOnlyComposable get() = LocalHoloPalette.current.cyan
val HoloElectricBlue: Color
  @Composable @ReadOnlyComposable get() = LocalHoloPalette.current.electric
val HoloDeepBlue: Color
  @Composable @ReadOnlyComposable get() = LocalHoloPalette.current.deep

// Convenient aliases used across screens
val HoloBlueCore: Color
  @Composable @ReadOnlyComposable get() = HoloBlueGlow
val HoloBlueBright: Color
  @Composable @ReadOnlyComposable get() = HoloCyanGlow

val StatusConnectedGreen = Color(0xFF4CAF50)
val StatusScanningAmber = Color(0xFFFFC107)
