package com.axiim.q.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/** Material You (wallpaper) colors exist from Android 12. */
val supportsMaterialYouColors: Boolean
  get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * App theme. Uses the built-in Axiim palette by default; with [materialYouColors] the whole app,
 * including the holo accents, follows the phone's wallpaper colors.
 */
@Composable
fun AxiimQTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  materialYouColors: Boolean = false,
  content: @Composable () -> Unit,
) {
  val useDynamic = materialYouColors && supportsMaterialYouColors
  val colorScheme =
    when {
      useDynamic -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> AxiimDarkColorScheme
      else -> AxiimLightColorScheme
    }
  val holoPalette = if (useDynamic) holoPaletteFrom(colorScheme) else AxiimHoloPalette

  CompositionLocalProvider(LocalHoloPalette provides holoPalette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content,
    )
  }
}
