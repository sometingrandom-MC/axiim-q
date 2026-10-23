package com.axiim.q.theme

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ambient animated background from BLE-Control (`AnimatedBackground.kt`),
 * blending Material You dynamic colors with subtle Glowing Holo Blue.
 */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
  val infiniteTransition = rememberInfiniteTransition(label = "background")

  val t1 by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(20000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "t1",
    )
  val t2 by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(25000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "t2",
    )
  val t3 by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(30000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "t3",
    )

  val surfaceColor = MaterialTheme.colorScheme.surface
  val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
  val holoBlueColor = HoloBlueGlow.copy(alpha = 0.09f)
  val tertiaryColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)

  Spacer(
    modifier =
      modifier
        .fillMaxSize()
        .drawWithCache {
          onDrawBehind {
            drawRect(color = surfaceColor)

            // Blob 1: Material You Primary
            drawCircle(
              brush =
                Brush.radialGradient(
                  colors = listOf(primaryColor, Color.Transparent),
                  center = Offset(t1 * size.width, t2 * size.height),
                  radius = size.maxDimension * 0.8f,
                ),
              center = Offset(t1 * size.width, t2 * size.height),
              radius = size.maxDimension * 0.8f,
            )

            // Blob 2: Glowing Holo Blue
            drawCircle(
              brush =
                Brush.radialGradient(
                  colors = listOf(holoBlueColor, Color.Transparent),
                  center = Offset((1 - t2) * size.width, t3 * size.height),
                  radius = size.maxDimension * 0.9f,
                ),
              center = Offset((1 - t2) * size.width, t3 * size.height),
              radius = size.maxDimension * 0.9f,
            )

            // Blob 3: Material You Tertiary
            drawCircle(
              brush =
                Brush.radialGradient(
                  colors = listOf(tertiaryColor, Color.Transparent),
                  center = Offset(t3 * size.width, (1 - t1) * size.height),
                  radius = size.maxDimension * 0.7f,
                ),
              center = Offset(t3 * size.width, (1 - t1) * size.height),
              radius = size.maxDimension * 0.7f,
            )
          }
        },
  )
}

/** Seed-42 grain positions (normalized x,y pairs), identical to the per-surface `Random(42)` sequence. */
private val GlassNoise: FloatArray =
  Random(42).let { random -> FloatArray(2000) { random.nextFloat() } }

/**
 * Glassmorphic surface modifier from BLE-Control (`Glassmorphism.kt`) with micro-grain
 * noise, specular diagonal highlight, and inset rounded border so the border is never clipped at corners.
 */
@Composable
fun Modifier.glassEffect(
  cornerRadius: Dp = 24.dp,
  alpha: Float = 0.05f,
  color: Color = Color.Unspecified,
): Modifier {
  val finalColor =
    if (color == Color.Unspecified) MaterialTheme.colorScheme.surfaceVariant else color

  return this
    .clip(RoundedCornerShape(cornerRadius))
    .drawWithCache {
      // Same seeded grain as before, but the random sequence is generated once for the whole app
      // instead of on every recomposition, and drawn as one batched call instead of ~1000 circles.
      val noiseCount = (size.width * size.height / 100).toInt().coerceIn(0, 1000)
      val noisePoints =
        List(noiseCount) { i ->
          Offset(GlassNoise[2 * i] * size.width, GlassNoise[2 * i + 1] * size.height)
        }

      val specularBrush =
        Brush.linearGradient(
          colors =
            listOf(
              Color.White.copy(alpha = 0.08f),
              Color.Transparent,
            ),
          start = Offset(0f, 0f),
          end = Offset(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f)),
        )

      val borderBrush =
        Brush.linearGradient(
          colors =
            listOf(
              Color.White.copy(alpha = 0.35f),
              Color.White.copy(alpha = 0.05f),
            ),
          start = Offset(0f, 0f),
          end = Offset(1000f, 1000f),
        )

      val strokeWidthPx = 1.dp.toPx()
      val halfStroke = strokeWidthPx / 2f
      val cornerRadiusPx = (cornerRadius.toPx() - halfStroke).coerceAtLeast(0f)

      onDrawBehind {
        // A round 2 px point is exactly the former radius-1 circle.
        drawPoints(
          points = noisePoints,
          pointMode = PointMode.Points,
          color = Color.White.copy(alpha = 0.05f),
          strokeWidth = 2f,
          cap = StrokeCap.Round,
        )
        drawRect(brush = specularBrush)
        drawRect(color = finalColor.copy(alpha = alpha))
        drawRoundRect(
          brush = borderBrush,
          topLeft = Offset(halfStroke, halfStroke),
          size =
            Size(
              (size.width - strokeWidthPx).coerceAtLeast(0f),
              (size.height - strokeWidthPx).coerceAtLeast(0f),
            ),
          cornerRadius = CornerRadius(cornerRadiusPx),
          style = Stroke(width = strokeWidthPx),
        )
      }
    }
}

/**
 * Glowing Holo Blue effect adapted from `aiGlow` in BLE-Control (`QuickSettingTile.kt`).
 * Uses inset stroke geometry so the glowing border is never clipped at the rounded corners.
 */
@Composable
fun Modifier.holoGlow(
  isGlowing: Boolean,
  cornerRadius: Dp = 24.dp,
  intensity: Float = 1f,
): Modifier {
  val infiniteTransition = rememberInfiniteTransition(label = "holoGlow")

  val t1 by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(8000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "t1",
    )
  val t2 by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(11000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "t2",
    )
  val t3 by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(14000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "t3",
    )
  val pulse by
    infiniteTransition.animateFloat(
      initialValue = 0.6f,
      targetValue = 1.0f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(4000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse",
    )

  val glowAlpha by
    animateFloatAsState(
      targetValue = if (isGlowing) intensity else 0f,
      animationSpec = tween(500),
      label = "glowAlpha",
    )

  return this.drawWithCache {
    val cornerRadiusPx = cornerRadius.toPx()
    val strokeWidth = 2.dp.toPx()
    val halfStroke = strokeWidth / 2f
    val stroke = Stroke(width = strokeWidth)
    val rectCornerRadius = CornerRadius(cornerRadiusPx)
    val insetCornerRadius = CornerRadius((cornerRadiusPx - halfStroke).coerceAtLeast(0f))

    onDrawBehind {
      if (glowAlpha > 0.01f) {
        // Blob 1: Signature Holo Blue (#33B5E5)
        drawRoundRect(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  HoloBlueGlow.copy(alpha = 0.30f * glowAlpha),
                  Color.Transparent,
                ),
              center = Offset(t1 * size.width, t2 * size.height),
              radius = (size.maxDimension * 0.7f).coerceAtLeast(1f),
            ),
          cornerRadius = rectCornerRadius,
        )
        // Blob 2: Electric Blue (#4285F4)
        drawRoundRect(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  HoloElectricBlue.copy(alpha = 0.22f * glowAlpha),
                  Color.Transparent,
                ),
              center = Offset((1 - t2) * size.width, t3 * size.height),
              radius = (size.maxDimension * 0.8f).coerceAtLeast(1f),
            ),
          cornerRadius = rectCornerRadius,
        )
        // Blob 3: Luminous Cyan (#00E5FF)
        drawRoundRect(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  HoloCyanGlow.copy(alpha = 0.18f * glowAlpha * pulse),
                  Color.Transparent,
                ),
              center = Offset(t3 * size.width, (1 - t1) * size.height),
              radius = (size.maxDimension * 0.6f).coerceAtLeast(1f),
            ),
          cornerRadius = rectCornerRadius,
        )

        // Internal glowing border (inset by halfStroke so it is never cut off by rounded clip)
        drawRoundRect(
          brush =
            Brush.sweepGradient(
              colors = listOf(HoloBlueGlow, HoloCyanGlow, HoloElectricBlue, HoloBlueGlow),
              center = Offset(t2 * size.width, t1 * size.height),
            ),
          topLeft = Offset(halfStroke, halfStroke),
          size =
            Size(
              (size.width - strokeWidth).coerceAtLeast(0f),
              (size.height - strokeWidth).coerceAtLeast(0f),
            ),
          cornerRadius = insetCornerRadius,
          style = stroke,
          alpha = 0.38f * glowAlpha,
        )
      }
    }
  }
}

@Composable
fun Modifier.holoGlow(
  isActive: Boolean,
  borderRadius: Dp = 24.dp,
  strokeWidth: Dp = 2.dp,
  intensity: Float = 1f,
): Modifier = holoGlow(isGlowing = isActive, cornerRadius = borderRadius, intensity = intensity)

private const val MIN_VISIBLE_BLUR_DP = 1f

/**
 * Pixel-identical to `Modifier.blur(radius)` (rectangle edge treatment), but the radius is read in
 * the draw layer instead of composition: animating it no longer recomposes the content every frame,
 * and the layer isn't torn down when the radius reaches 0 (which caused a hitch at the end).
 */
fun Modifier.animatedBlur(radius: () -> Dp): Modifier =
  graphicsLayer {
    val px = radius().toPx()
    // Below ~1 dp the blur is invisible but still costs a full offscreen pass on the GPU; skipping
    // that tail of each 16 dp -> 0 animation removes a large share of transition jank.
    if (px > MIN_VISIBLE_BLUR_DP.dp.toPx()) {
      renderEffect = BlurEffect(px, px, TileMode.Clamp)
      shape = RectangleShape
      clip = true
    } else {
      renderEffect = null
      clip = false
    }
  }

/** How long after a screen or dialog opens its [StaggeredEntrance] children still play an entrance. */
private const val STAGGER_WINDOW_MS = 450L
private const val STAGGER_STEP_MS = 100L
private const val STAGGER_MAX_DELAY_MS = 500L

private val LocalStaggerOrigin = compositionLocalOf<Long?> { null }

/**
 * Marks the moment a screen or dialog appeared. [StaggeredEntrance] children composed shortly after
 * play the staggered entrance; children composed later (lazy-list rows scrolled into view, sections
 * revealed by a toggle) render immediately instead of popping in with a delay.
 */
@Composable
fun StaggeredEntranceScope(content: @Composable () -> Unit) {
  val origin = remember { SystemClock.uptimeMillis() }
  CompositionLocalProvider(LocalStaggerOrigin provides origin, content = content)
}

/**
 * Exact StaggeredEntrance animation and blur curve from BLE-Control (`StaggeredEntrance.kt`):
 * 100ms delay per index, 600ms tween enter + 600ms tween blur (16.dp -> 0.dp), 400ms exit.
 */
@Composable
fun StaggeredEntrance(
  index: Int,
  content: @Composable () -> Unit,
) {
  val origin = LocalStaggerOrigin.current
  val shouldAnimate = remember { origin == null || SystemClock.uptimeMillis() - origin < STAGGER_WINDOW_MS }

  // Same motion as the original AnimatedVisibility(slideInVertically { it / 2 } + fadeIn(tween(600)))
  // with a 16 dp -> 0 blur, but the content is composed immediately (invisible) instead of on its
  // delayed turn, so building each card no longer lands in the middle of the running animation.
  val alpha = remember { Animatable(if (shouldAnimate) 0f else 1f) }
  val slideFraction = remember { Animatable(if (shouldAnimate) 0.5f else 0f) }
  val blurDp = remember { Animatable(if (shouldAnimate) 16f else 0f) }
  LaunchedEffect(Unit) {
    if (shouldAnimate) {
      delay((index * STAGGER_STEP_MS).coerceIn(0L, STAGGER_MAX_DELAY_MS))
      launch { alpha.animateTo(1f, tween(600)) }
      launch { blurDp.animateTo(0f, tween(600)) }
      // AnimatedVisibility's default slide spec
      slideFraction.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f))
    }
  }

  Box(
    modifier =
      Modifier
        .graphicsLayer {
          this.alpha = alpha.value
          translationY = slideFraction.value * size.height
        }
        .animatedBlur { blurDp.value.dp },
  ) {
    content()
  }
}

/**
 * Quick Setting Tile from BLE-Control (`QuickSettingTile.kt`) with:
 * - Bouncy press scale (`1.0f -> 1.05f`) and corner morphing (`24.dp -> 12.dp`)
 * - Haptic feedback (`VIRTUAL_KEY` on tap, `LongPress` on hold)
 * - Glowing Holo Blue (`holoGlow`) when active or in Auto mode
 * - Automatic greyed-out dimming when disconnected (`enabled = false`)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickSettingTile(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String,
  isActive: Boolean,
  isAutoMode: Boolean = false,
  enabled: Boolean = true,
  progress: Float? = null,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
  icon: ImageVector,
  bottomContent: @Composable (() -> Unit)? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isClickPressed by interactionSource.collectIsPressedAsState()
  // Inside a scrolling screen, clickable delays its Press by ~100 ms to rule out a scroll, so a
  // quick tap barely moved the spring. Track finger-down directly (as on BLE Control's
  // non-scrolling home screen) and drop it if the touch turns into a scroll.
  var isTouched by remember { mutableStateOf(false) }
  val isPressed = isTouched || isClickPressed
  val view = LocalView.current
  val haptic = LocalHapticFeedback.current

  val enabledAlpha by
    animateFloatAsState(
      targetValue = if (enabled) 1f else 0.42f,
      animationSpec = tween(350),
      label = "enabledAlpha",
    )

  val contentColor by
    animateColorAsState(
      targetValue =
        if (isActive && enabled) {
          // The active tile is translucent glass over the backdrop, not a solid primaryContainer,
          // so onPrimaryContainer had poor contrast; onSurface is tuned for that backdrop.
          MaterialTheme.colorScheme.onSurface
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "contentColor",
    )

  val scale by
    animateFloatAsState(
      targetValue = if (isPressed && enabled) 1.05f else 1.0f,
      animationSpec =
        spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessLow,
        ),
      label = "scale",
    )

  val cornerRadius by
    animateDpAsState(
      targetValue = if (isPressed && enabled) 12.dp else 24.dp,
      animationSpec =
        spring(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMedium,
        ),
      label = "cornerRadius",
    )

  Box(
    modifier =
      modifier
        .height(112.dp)
        .scale(scale)
        .alpha(enabledAlpha),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(cornerRadius))
          .glassEffect(
            cornerRadius = cornerRadius,
            alpha = if (isActive && enabled) 0.2f else 0.1f,
            color =
              if (isActive && enabled) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant
              },
          )
          .holoGlow(
            isGlowing = enabled && (isAutoMode || isActive),
            cornerRadius = cornerRadius,
            intensity = if (isAutoMode) 1.0f else 0.65f,
          )
          .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
              isTouched = true
              try {
                while (true) {
                  val event = awaitPointerEvent(PointerEventPass.Final)
                  val scrolled = event.changes.any { it.isConsumed && it.positionChanged() }
                  if (scrolled || event.changes.none { it.pressed }) break
                }
              } finally {
                isTouched = false
              }
            }
          }
          .combinedClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = {
              view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
              onClick()
            },
            onLongClick =
              onLongClick?.let {
                {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  it()
                }
              },
          ),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
          tint = if (isAutoMode && enabled) HoloCyanGlow else contentColor,
        )

        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.8f),
            maxLines = 1,
          )
        }
      }

      if (bottomContent != null && enabled) {
        Box(
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          bottomContent()
        }
      }
    }
  }
}

/**
 * Reusable popup dialog matching the exact `DeviceSelectionDialog` aesthetic from BLE-Control:
 * - Full-width custom `Dialog` with `24.dp` outer margin
 * - `28.dp` rounded glass container (`alpha = 0.15f`)
 * - Confined `AnimatedBackground(modifier = Modifier.matchParentSize())` inside the popup
 * - `StaggeredEntrance` header, subtitle, content, and bottom action buttons
 */
@Composable
fun GlassPopupDialog(
  title: String,
  subtitle: String? = null,
  headerTrailing: @Composable (() -> Unit)? = null,
  confirmText: String = "Done",
  onConfirm: (() -> Unit)? = null,
  dismissText: String? = "Cancel",
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    StaggeredEntranceScope {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(28.dp))
              .glassEffect(cornerRadius = 28.dp, alpha = 0.15f)
              .holoGlow(isGlowing = true, cornerRadius = 28.dp, intensity = 0.45f),
        ) {
          // Confined AnimatedBackground strictly inside the dialog area (from BLE-Control)
          AnimatedBackground(modifier = Modifier.matchParentSize())

          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(24.dp),
          ) {
            StaggeredEntrance(index = 0) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = title,
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                if (headerTrailing != null) {
                  headerTrailing()
                }
              }
            }

            if (subtitle != null) {
              Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
              )
            } else {
              Spacer(modifier = Modifier.height(16.dp))
            }

            content()

            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(top = 20.dp),
              horizontalArrangement = Arrangement.End,
            ) {
              if (dismissText != null) {
                TextButton(
                  onClick = onDismiss,
                  modifier = Modifier.padding(end = 8.dp),
                ) {
                  Text(
                    text = dismissText,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              if (onConfirm != null) {
                TextButton(onClick = onConfirm) {
                  Text(
                    text = confirmText,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

data class SystemInsight(
  val text: String,
  val icon: ImageVector,
  val color: Color? = null,
  val stableKey: String,
)

@Composable
fun TypewriterText(
  text: String,
  stableKey: Any,
  modifier: Modifier = Modifier,
  delayMillis: Long = 28,
  onAnimationFinish: () -> Unit = {},
  style: TextStyle = MaterialTheme.typography.bodyLarge,
  color: Color = MaterialTheme.colorScheme.onSurface,
  lineHeight: TextUnit = style.lineHeight,
) {
  var visibleCharCount by remember(stableKey) { mutableIntStateOf(0) }
  var hasFinished by remember(stableKey) { mutableStateOf(false) }

  LaunchedEffect(stableKey) {
    visibleCharCount = 0
    hasFinished = false
    for (i in text.indices) {
      delay(delayMillis)
      visibleCharCount = i + 1
    }
    hasFinished = true
    onAnimationFinish()
  }

  val displayedText = if (hasFinished) text else text.take(visibleCharCount)

  Text(
    text = displayedText,
    modifier = modifier,
    style = style,
    color = color,
    lineHeight = lineHeight,
  )
}

/**
 * Intelligent status card from BLE-Control (`IntelligentCard.kt`) with Glowing Holo Blue border.
 */
@Composable
fun IntelligentStatusCard(
  insight: SystemInsight,
  isConnected: Boolean,
  modifier: Modifier = Modifier,
) {
  var displayedInsight by remember { mutableStateOf(insight) }
  var isTyping by remember { mutableStateOf(true) }

  LaunchedEffect(insight) {
    if (insight.stableKey == displayedInsight.stableKey) {
      displayedInsight = insight
      return@LaunchedEffect
    }
    displayedInsight = insight
    isTyping = true
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .glassEffect(cornerRadius = 24.dp, alpha = 0.15f)
        .holoGlow(isGlowing = isConnected, cornerRadius = 24.dp, intensity = 0.85f)
        .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Default.AutoAwesome,
        contentDescription = null,
        tint = if (isConnected) HoloBlueGlow else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = "SYSTEM INSIGHTS",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (isConnected) HoloBlueGlow else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    AnimatedContent(
      targetState = displayedInsight,
      contentKey = { it.stableKey },
      transitionSpec = {
        if (initialState.stableKey == targetState.stableKey) {
          EnterTransition.None togetherWith ExitTransition.None
        } else {
          fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
        }
      },
      label = "insightAnimation",
    ) { current ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(
          imageVector = current.icon,
          contentDescription = null,
          tint = current.color ?: if (isConnected) HoloBlueGlow else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(32.dp),
        )

        TypewriterText(
          text = current.text,
          stableKey = current.stableKey,
          onAnimationFinish = { isTyping = false },
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f,
        )
      }
    }
  }
}

@Composable
fun IntelligentStatusCard(
  title: String,
  description: String,
  isActive: Boolean,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  IntelligentStatusCard(
    insight =
      SystemInsight(
        text = "$title — $description",
        icon = icon,
        color = if (isActive) HoloCyanGlow else null,
        stableKey = title,
      ),
    isConnected = isActive,
    modifier = modifier,
  )
}

@Composable
fun HoloWaveProgressIndicator(
  progress: Float,
  modifier: Modifier = Modifier,
  color: Color = HoloBlueGlow,
  trackColor: Color = Color.Transparent,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "waveProgress")
  val phase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(2200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "wavePhase",
    )

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val midY = h / 2f
    val clamped = progress.coerceIn(0f, 1f)
    val activeEnd = w * clamped

    if (activeEnd < w && trackColor != Color.Transparent) {
      drawLine(
        color = trackColor,
        start = Offset(activeEnd, midY),
        end = Offset(w, midY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
      )
    }

    if (activeEnd > 1f) {
      val path = Path()
      val amplitude = (h * 0.34f).coerceAtLeast(1f)
      val wavelength = 28.dp.toPx().coerceAtLeast(8f)
      var x = 0f
      while (x <= activeEnd) {
        val y = midY + amplitude * sin((x / wavelength) * (2f * PI).toFloat() - phase)
        if (x == 0f) {
          path.moveTo(x, y)
        } else {
          path.lineTo(x, y)
        }
        x += 3f
      }
      drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
      )
    }
  }
}
