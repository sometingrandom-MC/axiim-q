package com.axiim.q.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.axiim.q.data.AxiimRepository
import com.axiim.q.data.AxiimUiState
import com.axiim.q.model.RemoteKey
import com.axiim.q.theme.GlassPopupDialog
import com.axiim.q.theme.HoloBlueBright
import com.axiim.q.theme.HoloBlueCore
import com.axiim.q.theme.StaggeredEntrance
import com.axiim.q.theme.glassEffect
import com.axiim.q.theme.holoGlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
  state: AxiimUiState,
  repository: AxiimRepository,
) {
  val view = LocalView.current
  val isConnected = state.isLiveTcpConnected
  var showNumPadDialog by remember { mutableStateOf(false) }

  val controlsAlpha by
    animateFloatAsState(
      targetValue = if (isConnected) 1.0f else 0.42f,
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "remoteControlsAlpha",
    )

  fun pressKey(key: RemoteKey) {
    if (!isConnected) return
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    repository.sendRemoteKey(key)
  }

  Scaffold(
    topBar = {
      LargeTopAppBar(
        title = {
          Text(
            text = "Remote",
            fontWeight = FontWeight.Bold,
          )
        },
        actions = {
          FilledTonalIconButton(
            onClick = { showNumPadDialog = true },
            enabled = isConnected,
            modifier = Modifier.padding(end = 8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Dialpad,
              contentDescription = "Keypad",
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
          ),
      )
    },
    containerColor = Color.Transparent,
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .padding(innerPadding)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
          .padding(top = 8.dp, bottom = 140.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Main D-Pad & Navigation Card
      StaggeredEntrance(index = 0) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .alpha(controlsAlpha)
              .clip(RoundedCornerShape(24.dp))
              .glassEffect(cornerRadius = 24.dp, alpha = 0.1f)
              .holoGlow(
                isGlowing = isConnected,
                cornerRadius = 24.dp,
                intensity = 0.7f,
              ),
        ) {
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
          ) {
            // Circular Tactile D-Pad
            Box(
              modifier =
                Modifier
                  .size(248.dp)
                  .clip(CircleShape)
                  .glassEffect(cornerRadius = 124.dp, alpha = 0.12f)
                  .holoGlow(isGlowing = isConnected, cornerRadius = 124.dp, intensity = 0.8f),
              contentAlignment = Alignment.Center,
            ) {
              // Up
              DpadDirectionButton(
                icon = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Up",
                enabled = isConnected,
                onClick = { pressKey(RemoteKey.UP) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
              )
              // Down
              DpadDirectionButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Down",
                enabled = isConnected,
                onClick = { pressKey(RemoteKey.DOWN) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
              )
              // Left
              DpadDirectionButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Left",
                enabled = isConnected,
                onClick = { pressKey(RemoteKey.LEFT) },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
              )
              // Right
              DpadDirectionButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Right",
                enabled = isConnected,
                onClick = { pressKey(RemoteKey.RIGHT) },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
              )

              // Center Select Button
              Box(
                modifier =
                  Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(HoloBlueCore.copy(alpha = 0.22f))
                    .holoGlow(isGlowing = isConnected, cornerRadius = 46.dp, intensity = 0.9f)
                    .clickable(enabled = isConnected) { pressKey(RemoteKey.ENTER) },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "OK",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = HoloBlueBright,
                )
              }
            }

            // Back / Home / Menu Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RemoteActionPill(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = "Back",
                enabled = isConnected,
                onClick = { pressKey(RemoteKey.BACK) },
              )
              RemoteActionPill(
                icon = Icons.Rounded.Home,
                label = "Home",
                enabled = isConnected,
                onClick = { pressKey(RemoteKey.HOME) },
              )
              RemoteActionPill(
                icon = Icons.Rounded.Menu,
                label = "Menu",
                enabled = isConnected,
                onClick = { pressKey(RemoteKey.MENU_INFO) },
              )
            }
          }
        }
      }

      // Playback Transport Controls Card
      StaggeredEntrance(index = 1) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .alpha(controlsAlpha)
              .clip(RoundedCornerShape(24.dp))
              .glassEffect(cornerRadius = 24.dp, alpha = 0.1f),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Text(
              text = "Playback Controls",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.Bold,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              FilledTonalIconButton(
                onClick = { pressKey(RemoteKey.SKIP_BACK) },
                enabled = isConnected,
              ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
              }
              FilledTonalIconButton(
                onClick = { pressKey(RemoteKey.REWIND) },
                enabled = isConnected,
              ) {
                Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind")
              }
              FilledTonalIconButton(
                onClick = { pressKey(RemoteKey.PLAY_PAUSE) },
                enabled = isConnected,
                modifier = Modifier.size(56.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.PlayArrow,
                  contentDescription = "Play / Pause",
                  tint = HoloBlueBright,
                  modifier = Modifier.size(28.dp),
                )
              }
              FilledTonalIconButton(
                onClick = { pressKey(RemoteKey.FAST_FORWARD) },
                enabled = isConnected,
              ) {
                Icon(Icons.Rounded.FastForward, contentDescription = "Fast Forward")
              }
              FilledTonalIconButton(
                onClick = { pressKey(RemoteKey.SKIP_FORWARD) },
                enabled = isConnected,
              ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
              }
              FilledTonalIconButton(
                onClick = { pressKey(RemoteKey.STOP) },
                enabled = isConnected,
              ) {
                Icon(Icons.Rounded.Stop, contentDescription = "Stop")
              }
            }
          }
        }
      }
    }
  }

  if (showNumPadDialog) {
    GlassPopupDialog(
      title = "TV Keypad",
      subtitle = "Send channel and numeric commands to your active device.",
      confirmText = "Close",
      onConfirm = { showNumPadDialog = false },
      dismissText = null,
      onDismiss = { showNumPadDialog = false },
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        val rows =
          listOf(
            listOf(RemoteKey.NUM_1, RemoteKey.NUM_2, RemoteKey.NUM_3),
            listOf(RemoteKey.NUM_4, RemoteKey.NUM_5, RemoteKey.NUM_6),
            listOf(RemoteKey.NUM_7, RemoteKey.NUM_8, RemoteKey.NUM_9),
            listOf(RemoteKey.STAR, RemoteKey.NUM_0, RemoteKey.POUND),
          )
        rows.forEachIndexed { idx, rowKeys ->
          StaggeredEntrance(index = idx + 1) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              rowKeys.forEach { key ->
                Box(
                  modifier =
                    Modifier
                      .weight(1f)
                      .height(48.dp)
                      .glassEffect(cornerRadius = 14.dp, alpha = 0.08f)
                      .clickable { pressKey(key) },
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = key.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        StaggeredEntrance(index = 5) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            listOf(
              RemoteKey.CHANNEL_DOWN to "Ch -",
              RemoteKey.GUIDE to "Guide",
              RemoteKey.CHANNEL_UP to "Ch +",
            ).forEach { (key, label) ->
              Box(
                modifier =
                  Modifier
                    .weight(1f)
                    .height(44.dp)
                    .glassEffect(cornerRadius = 14.dp, alpha = 0.1f)
                    .clickable { pressKey(key) },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = HoloBlueBright,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DpadDirectionButton(
  icon: ImageVector,
  contentDescription: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .size(56.dp)
        .clip(CircleShape)
        .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.size(30.dp),
    )
  }
}

@Composable
private fun RemoteActionPill(
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    FilledTonalIconButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.size(52.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
      )
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
