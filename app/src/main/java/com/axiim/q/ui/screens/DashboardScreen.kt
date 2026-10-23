package com.axiim.q.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axiim.q.data.AxiimRepository
import com.axiim.q.data.AxiimUiState
import com.axiim.q.model.EqualizerPreset
import com.axiim.q.model.QuickTileId
import com.axiim.q.theme.GlassPopupDialog
import com.axiim.q.theme.HoloBlueBright
import com.axiim.q.theme.HoloBlueCore
import com.axiim.q.theme.HoloWaveProgressIndicator
import com.axiim.q.theme.IntelligentStatusCard
import com.axiim.q.theme.QuickSettingTile
import com.axiim.q.theme.StaggeredEntrance
import com.axiim.q.theme.glassEffect
import com.axiim.q.theme.holoGlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  state: AxiimUiState,
  repository: AxiimRepository,
) {
  val view = LocalView.current
  val isConnected = state.isLiveTcpConnected

  var showConfigureTilesDialog by remember { mutableStateOf(false) }
  var showEqPresetDialog by remember { mutableStateOf(false) }

  val controlsAlpha by
    animateFloatAsState(
      targetValue = if (isConnected) 1.0f else 0.42f,
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "homeControlsAlpha",
    )

  val statusTitle =
    when {
      isConnected && state.powered -> "Playing from ${state.activeInput.label}"
      isConnected && !state.powered -> "${state.selectedDevice.name} is in Standby"
      state.isDiscoveringSsdp -> "Searching for ${state.selectedDevice.name}..."
      else -> "${state.selectedDevice.name} Offline"
    }

  val activeModeSummary =
    buildList {
      if (state.dspSettings.dtsNeuralEnable) add("DTS Neural")
      if (state.dspSettings.dolbyDrc == "on") add("Night Mode")
      if (state.dspSettings.dolbyDrc == "auto") add("Night Mode (Auto)")
      if (state.dspSettings.superStereoEnable) add("Super Stereo")
      if (state.dspSettings.partyModeEnable) add("Party Mode")
      if (state.activeEqPreset.name != "disabled") add(state.activeEqPreset.displayName)
    }.joinToString(" • ")

  val statusDescription =
    when {
      isConnected && state.powered -> {
        val volText = if (state.muted) "Muted" else "Volume ${state.volume}%"
        if (activeModeSummary.isNotEmpty()) "$volText • $activeModeSummary" else volText
      }
      isConnected && !state.powered -> "Flip the System Power switch below to wake your sound system."
      state.isDiscoveringSsdp -> "Auto-connecting over your Wi-Fi network..."
      else -> "Controls will wake automatically as soon as your Axiim Q responds."
    }

  Scaffold(
    topBar = {
      LargeTopAppBar(
        title = {
          Text(
            text = state.personalize.deviceName.ifBlank { "Axiim Q" },
            fontWeight = FontWeight.Bold,
          )
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
      StaggeredEntrance(index = 0) {
        IntelligentStatusCard(
          title = statusTitle,
          description = statusDescription,
          isActive = isConnected && state.powered,
          icon = if (isConnected && state.powered) Icons.Rounded.AutoAwesome else Icons.Rounded.SurroundSound,
        )
      }

      // Master Volume, System Power & HDMI Source Card (Modeled after InverterSwitchCard in BLE-Control)
      StaggeredEntrance(index = 1) {
        VolumeAndSourceCard(
          state = state,
          isConnected = isConnected,
          controlsAlpha = controlsAlpha,
          onTogglePower = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            repository.togglePower()
          },
          onVolumeDragStart = repository::onVolumeDragStart,
          onVolumeChange = repository::onVolumeChange,
          onVolumeDragEnd = repository::onVolumeDragEnd,
          onStepDown = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            repository.stepVolumeDown()
          },
          onStepUp = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            repository.stepVolumeUp()
          },
          onToggleMute = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            repository.toggleMute()
          },
          onSelectInput = { port ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            repository.selectInput(port)
          },
          onToggleShowAllInputs = repository::toggleShowDisconnectedInputs,
        )
      }

      // Quick Settings Header + Grid
      StaggeredEntrance(index = 2) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Quick Settings",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          TextButton(onClick = { showConfigureTilesDialog = true }) {
            Icon(
              imageVector = Icons.Rounded.Tune,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Edit")
          }
        }
      }

      StaggeredEntrance(index = 3) {
        val rows = state.enabledQuickTiles.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          for (rowTiles in rows) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              for (tileId in rowTiles) {
                val spec =
                  resolveQuickTileSpec(
                    tileId = tileId,
                    state = state,
                    repository = repository,
                    onOpenEqSheet = { showEqPresetDialog = true },
                  )
                QuickSettingTile(
                  title = spec.title,
                  subtitle = spec.subtitle,
                  icon = spec.icon,
                  isActive = spec.isActive,
                  isAutoMode = spec.isAutoMode,
                  enabled = isConnected,
                  onClick = spec.onTap,
                  onLongClick = spec.onLongPress,
                  modifier = Modifier.weight(1f),
                )
              }
              if (rowTiles.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }
    }
  }

  if (showConfigureTilesDialog) {
    ConfigureQuickSettingsDialog(
      enabledTiles = state.enabledQuickTiles,
      onToggleTile = repository::toggleQuickTileVisibility,
      onResetDefaults = repository::resetQuickTilesToDefault,
      onDismiss = { showConfigureTilesDialog = false },
    )
  }

  if (showEqPresetDialog) {
    EqualizerPresetPopupDialog(
      presets = state.eqPresets,
      onSelectPreset = { presetName ->
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        repository.selectEqPreset(presetName)
        showEqPresetDialog = false
      },
      onDismiss = { showEqPresetDialog = false },
    )
  }
}

@Composable
private fun VolumeAndSourceCard(
  state: AxiimUiState,
  isConnected: Boolean,
  controlsAlpha: Float,
  onTogglePower: () -> Unit,
  onVolumeDragStart: () -> Unit,
  onVolumeChange: (Int) -> Unit,
  onVolumeDragEnd: () -> Unit,
  onStepDown: () -> Unit,
  onStepUp: () -> Unit,
  onToggleMute: () -> Unit,
  onSelectInput: (Int) -> Unit,
  onToggleShowAllInputs: () -> Unit,
) {
  val animatedVolumeProgress by
    animateFloatAsState(
      targetValue = if (state.muted || !state.powered) 0f else (state.volume / 100f).coerceIn(0f, 1f),
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "volumeProgress",
    )

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .alpha(controlsAlpha)
        .clip(RoundedCornerShape(24.dp))
        .glassEffect(cornerRadius = 24.dp, alpha = 0.1f)
        .holoGlow(
          isGlowing = isConnected && state.powered && !state.muted,
          cornerRadius = 24.dp,
          intensity = 0.7f,
        ),
  ) {
    Column(
      modifier = Modifier.padding(20.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // System Power Row (Exact InverterSwitchCard style from BLE-Control/InverterControl.kt)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                  if (state.powered) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surface
                  },
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (state.powered) Icons.Default.Power else Icons.Default.PowerOff,
              contentDescription = null,
              tint =
                if (state.powered) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
          }

          Column {
            Text(
              text = "System Power",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = if (state.powered) "Running • ${state.activeInput.label}" else "Standby",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Switch(
          checked = state.powered,
          enabled = isConnected,
          onCheckedChange = { onTogglePower() },
        )
      }

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
      )

      // Master Volume Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Master Volume",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = if (state.muted) "Muted" else "${state.volume}",
              style =
                MaterialTheme.typography.displaySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = (-1).sp,
                ),
              color =
                if (state.muted) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
            )
            if (!state.muted) {
              Text(
                text = "%",
                style = MaterialTheme.typography.titleMedium,
                color = HoloBlueBright,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
              )
            }
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledTonalIconButton(
            onClick = onToggleMute,
            enabled = isConnected,
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor =
                  if (state.muted) {
                    MaterialTheme.colorScheme.errorContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                  },
                contentColor =
                  if (state.muted) {
                    MaterialTheme.colorScheme.onErrorContainer
                  } else {
                    MaterialTheme.colorScheme.onSurface
                  },
              ),
          ) {
            Icon(
              imageVector =
                if (state.muted) {
                  Icons.AutoMirrored.Rounded.VolumeOff
                } else {
                  Icons.AutoMirrored.Rounded.VolumeUp
                },
              contentDescription = "Mute",
            )
          }

          FilledTonalIconButton(
            onClick = onStepDown,
            enabled = isConnected,
          ) {
            Icon(
              imageVector = Icons.Rounded.Remove,
              contentDescription = "Volume Down",
            )
          }

          FilledTonalIconButton(
            onClick = onStepUp,
            enabled = isConnected,
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "Volume Up",
            )
          }
        }
      }

      // Volume Slider
      Slider(
        value = state.volume.toFloat(),
        onValueChange = { value ->
          if (!state.isUserDraggingVolume) onVolumeDragStart()
          onVolumeChange(value.toInt())
        },
        onValueChangeFinished = onVolumeDragEnd,
        valueRange = 0f..100f,
        enabled = isConnected,
        colors =
          SliderDefaults.colors(
            thumbColor = HoloBlueBright,
            activeTrackColor = HoloBlueCore,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
          ),
      )

      // HDMI Input Selector Row
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.SettingsInputHdmi,
              contentDescription = null,
              tint = HoloBlueBright,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "HDMI Source",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Text(
            text = if (state.showDisconnectedInputs) "Active Only" else "All Ports",
            style = MaterialTheme.typography.labelMedium,
            color = HoloBlueBright,
            modifier =
              Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = isConnected) { onToggleShowAllInputs() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }

        val visibleInputs =
          if (state.showDisconnectedInputs) {
            state.inputs
          } else {
            state.inputs.filter { it.connected || it.active || it.port == 0 }
          }

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          visibleInputs.forEach { input ->
            val selected = input.active
            FilterChip(
              selected = selected,
              enabled = isConnected,
              onClick = { onSelectInput(input.port) },
              label = {
                Text(
                  text = input.label.ifBlank { input.portDisplayName },
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
              },
              leadingIcon =
                if (selected) {
                  {
                    Box(
                      modifier =
                        Modifier
                          .size(8.dp)
                          .clip(CircleShape)
                          .background(HoloBlueBright),
                    )
                  }
                } else {
                  null
                },
              shape = RoundedCornerShape(14.dp),
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = HoloBlueCore.copy(alpha = 0.22f),
                  selectedLabelColor = HoloBlueBright,
                ),
            )
          }
        }
      }
    }

    if (animatedVolumeProgress > 0.01f) {
      HoloWaveProgressIndicator(
        progress = animatedVolumeProgress,
        modifier =
          Modifier
            .fillMaxWidth()
            .height(6.dp)
            .align(Alignment.BottomCenter),
        color = HoloBlueCore,
      )
    }
  }
}

private data class QuickTileSpec(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val isActive: Boolean,
  val isAutoMode: Boolean = false,
  val onTap: () -> Unit,
  val onLongPress: (() -> Unit)? = null,
)

private fun resolveQuickTileSpec(
  tileId: QuickTileId,
  state: AxiimUiState,
  repository: AxiimRepository,
  onOpenEqSheet: () -> Unit,
): QuickTileSpec {
  return when (tileId) {
    QuickTileId.DTS_NEURAL -> {
      val active = state.dspSettings.dtsNeuralEnable
      QuickTileSpec(
        title = tileId.title,
        subtitle = if (active) "Upmixing On" else "Off",
        icon = Icons.Rounded.SurroundSound,
        isActive = active,
        onTap = { repository.toggleDtsNeural(!active) },
      )
    }
    QuickTileId.NIGHT_MODE -> {
      val mode = state.dspSettings.dolbyDrc
      val isAuto = mode == "auto"
      val isActive = mode == "on" || isAuto
      QuickTileSpec(
        title = tileId.title,
        subtitle =
          when (mode) {
            "auto" -> "On (Auto)"
            "on" -> "On"
            else -> "Off"
          },
        icon = Icons.Rounded.Bedtime,
        isActive = isActive,
        isAutoMode = isAuto,
        onTap = { repository.toggleNightModeOnOff() },
        onLongPress = { repository.toggleNightModeAuto() },
      )
    }
    QuickTileId.SUPER_STEREO -> {
      val active = state.dspSettings.superStereoEnable
      QuickTileSpec(
        title = tileId.title,
        subtitle = if (active) "On" else "Off",
        icon = Icons.Rounded.Speaker,
        isActive = active,
        onTap = { repository.toggleSuperStereo(!active) },
      )
    }
    QuickTileId.PARTY_MODE -> {
      val active = state.dspSettings.partyModeEnable
      QuickTileSpec(
        title = tileId.title,
        subtitle = if (active) "On" else "Off",
        icon = Icons.Rounded.MusicNote,
        isActive = active,
        onTap = { repository.togglePartyMode(!active) },
      )
    }
    QuickTileId.EQUALIZER -> {
      val activePreset = state.activeEqPreset
      val isActive = activePreset.name != "disabled"
      val isCustom = activePreset.name == "custom" || activePreset.name == "per_speaker"
      QuickTileSpec(
        title = tileId.title,
        subtitle = if (isActive) activePreset.displayName else "Off",
        icon = Icons.Rounded.Equalizer,
        isActive = isActive,
        isAutoMode = isCustom && isActive,
        onTap = { repository.toggleEqualizerOnOff() },
        onLongPress = onOpenEqSheet,
      )
    }
    QuickTileId.LISTEN_ON_PHONE -> {
      val active = state.ipAudio.isStreaming
      QuickTileSpec(
        title = tileId.title,
        subtitle = if (active) "Streaming" else "Off",
        icon = Icons.Rounded.Headphones,
        isActive = active,
        isAutoMode = active,
        onTap = { repository.toggleIpAudioListenOnPhone() },
      )
    }
    QuickTileId.LFE_BOOST -> {
      val active = state.dspSettings.lfeBoostTrueHd
      QuickTileSpec(
        title = tileId.title,
        subtitle = if (active) "+10 dB On" else "Off",
        icon = Icons.Rounded.GraphicEq,
        isActive = active,
        onTap = { repository.toggleLfeBoostTrueHd(!active) },
      )
    }
    QuickTileId.AUTO_GAIN -> {
      val active = state.dspSettings.autoFormatGain
      QuickTileSpec(
        title = tileId.title,
        subtitle = if (active) "On" else "Off",
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        isActive = active,
        onTap = { repository.toggleAutoFormatGain(!active) },
      )
    }
    QuickTileId.FRONT_LED -> {
      val active = state.personalize.blueLed
      QuickTileSpec(
        title = tileId.title,
        subtitle = if (active) "On" else "Off",
        icon = Icons.Rounded.LightMode,
        isActive = active,
        onTap = { repository.toggleFrontBlueLed(!active) },
      )
    }
    QuickTileId.DTS_NEO6 -> {
      val mode = state.dspSettings.neo6Mode
      val isMusic = mode == "music"
      val isActive = mode == "cinema" || isMusic
      QuickTileSpec(
        title = tileId.title,
        subtitle =
          when (mode) {
            "cinema" -> "Cinema"
            "music" -> "Music (Auto)"
            else -> "Off"
          },
        icon = Icons.Rounded.SurroundSound,
        isActive = isActive,
        isAutoMode = isMusic,
        onTap = { repository.toggleNeo6OnOff() },
        onLongPress = { repository.toggleNeo6Music() },
      )
    }
  }
}

private fun iconForTileId(tileId: QuickTileId): ImageVector =
  when (tileId) {
    QuickTileId.DTS_NEURAL -> Icons.Rounded.SurroundSound
    QuickTileId.NIGHT_MODE -> Icons.Rounded.Bedtime
    QuickTileId.SUPER_STEREO -> Icons.Rounded.Speaker
    QuickTileId.PARTY_MODE -> Icons.Rounded.MusicNote
    QuickTileId.EQUALIZER -> Icons.Rounded.Equalizer
    QuickTileId.LISTEN_ON_PHONE -> Icons.Rounded.Headphones
    QuickTileId.LFE_BOOST -> Icons.Rounded.GraphicEq
    QuickTileId.AUTO_GAIN -> Icons.AutoMirrored.Rounded.VolumeUp
    QuickTileId.FRONT_LED -> Icons.Rounded.LightMode
    QuickTileId.DTS_NEO6 -> Icons.Rounded.SurroundSound
  }

/**
 * Customize Quick Settings popup styled identically to BLE-Control's `DeviceSelectionDialog`.
 */
@Composable
private fun ConfigureQuickSettingsDialog(
  enabledTiles: List<QuickTileId>,
  onToggleTile: (QuickTileId) -> Unit,
  onResetDefaults: () -> Unit,
  onDismiss: () -> Unit,
) {
  GlassPopupDialog(
    title = "Quick Settings",
    subtitle = "Choose which audio controls appear on your Home screen.",
    confirmText = "Done",
    onConfirm = onDismiss,
    dismissText = "Reset",
    onDismiss = onResetDefaults,
  ) {
    LazyColumn(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(300.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(QuickTileId.entries) { index, tile ->
        val checked = tile in enabledTiles
        StaggeredEntrance(index = index + 1) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .glassEffect(
                  cornerRadius = 16.dp,
                  alpha = if (checked) 0.12f else 0.05f,
                )
                .clickable { onToggleTile(tile) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Box(
              modifier =
                Modifier
                  .size(40.dp)
                  .glassEffect(cornerRadius = 10.dp, alpha = 0.1f),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = iconForTileId(tile),
                contentDescription = null,
                tint = if (checked) HoloBlueBright else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = tile.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = tile.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Checkbox(
              checked = checked,
              onCheckedChange = { onToggleTile(tile) },
            )
          }
        }
      }
    }
  }
}

/**
 * Equalizer Preset popup styled identically to BLE-Control's `DeviceSelectionDialog`.
 */
@Composable
private fun EqualizerPresetPopupDialog(
  presets: List<EqualizerPreset>,
  onSelectPreset: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  GlassPopupDialog(
    title = "Equalizer Preset",
    subtitle = "Select an acoustic profile or open the Audio tab for 10-band tuning.",
    confirmText = "Close",
    onConfirm = onDismiss,
    dismissText = null,
    onDismiss = onDismiss,
  ) {
    LazyColumn(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(300.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(presets) { index, preset ->
        StaggeredEntrance(index = index + 1) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .glassEffect(
                  cornerRadius = 16.dp,
                  alpha = if (preset.active) 0.14f else 0.05f,
                )
                .clickable { onSelectPreset(preset.name) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Box(
              modifier =
                Modifier
                  .size(40.dp)
                  .glassEffect(cornerRadius = 10.dp, alpha = 0.1f),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Equalizer,
                contentDescription = null,
                tint = if (preset.active) HoloBlueBright else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = preset.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = if (preset.active) "Active Preset" else "Tap to apply",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (preset.active) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Active",
                tint = HoloBlueBright,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }
    }
  }
}
