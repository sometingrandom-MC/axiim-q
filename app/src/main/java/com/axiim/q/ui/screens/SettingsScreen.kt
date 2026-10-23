package com.axiim.q.ui.screens

import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.axiim.q.data.AxiimRepository
import com.axiim.q.data.AxiimUiState
import com.axiim.q.theme.GlassPopupDialog
import com.axiim.q.theme.HoloBlueBright
import com.axiim.q.theme.HoloBlueCore
import com.axiim.q.theme.StaggeredEntrance
import com.axiim.q.theme.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  state: AxiimUiState,
  repository: AxiimRepository,
  onOpenDeviceSwitcher: () -> Unit = {},
) {
  val context = LocalContext.current
  val view = LocalView.current
  val isConnected = state.isLiveTcpConnected

  var showRenameDialog by remember { mutableStateOf(false) }

  val controlsAlpha by
    animateFloatAsState(
      targetValue = if (isConnected) 1.0f else 0.42f,
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "settingsControlsAlpha",
    )

  val mediaPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "audio/mpeg"
        var displayName = "Phone Audio Stream"
        resolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIdx >= 0 && cursor.moveToFirst()) {
            displayName = cursor.getString(nameIdx) ?: displayName
          }
        }
        repository.startPlayToQFromUri(uri, displayName, mime)
      }
    }

  Scaffold(
    topBar = {
      LargeTopAppBar(
        title = {
          Text(
            text = "Settings",
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
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      // Group 1: Sound System & Personalization
      StaggeredEntrance(index = 0) {
        SettingsGroup(
          title = "Sound System",
          icon = Icons.Rounded.LightMode,
          alpha = controlsAlpha,
        ) {
          ListItem(
            headlineContent = { Text("System Name") },
            supportingContent = { Text(state.personalize.deviceName.ifBlank { "Axiim Q" }) },
            trailingContent = {
              FilledTonalButton(
                onClick = { showRenameDialog = true },
                enabled = isConnected,
              ) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Rename")
              }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          )
          ListItem(
            headlineContent = { Text("Front Status Light") },
            supportingContent = { Text("Blue status indicator on the front of the Axiim Q") },
            trailingContent = {
              Switch(
                checked = state.personalize.blueLed,
                enabled = isConnected,
                onCheckedChange = {
                  view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                  repository.toggleFrontBlueLed(it)
                },
              )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          )
          ListItem(
            headlineContent = { Text("Speaker Lights") },
            supportingContent = { Text("Accent lighting on compatible wireless speakers") },
            trailingContent = {
              Switch(
                checked = state.dspSettings.klipschLedsEnabled,
                enabled = isConnected,
                onCheckedChange = {
                  view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                  repository.toggleKlipschLeds(it)
                },
              )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          )
          Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Startup Volume Limit",
                style = MaterialTheme.typography.bodyLarge,
              )
              Text(
                text = "${state.dspSettings.highVolume}%",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = HoloBlueBright,
                fontWeight = FontWeight.Bold,
              )
            }
            Text(
              text = "Prevents loud surprises when turning on your system",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
              value = state.dspSettings.highVolume.toFloat(),
              onValueChange = { repository.setStartupHighVolume(it.toInt()) },
              valueRange = 10f..80f,
              enabled = isConnected,
              colors =
                SliderDefaults.colors(
                  thumbColor = HoloBlueBright,
                  activeTrackColor = HoloBlueCore,
                ),
            )
          }
        }
      }

      // Group 2: HDMI & TV Integration
      StaggeredEntrance(index = 1) {
        SettingsGroup(
          title = "TV & HDMI",
          icon = Icons.Rounded.SettingsInputHdmi,
          alpha = controlsAlpha,
        ) {
          ListItem(
            headlineContent = { Text("HDMI-CEC Control") },
            supportingContent = { Text("Power on/off and control volume with your TV remote") },
            trailingContent = {
              Switch(
                checked = state.cecSettings.enabled,
                enabled = isConnected,
                onCheckedChange = {
                  view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                  repository.toggleCecEnabled(it)
                },
              )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          )
          ListItem(
            headlineContent = { Text("Audio Return Channel (ARC)") },
            supportingContent = { Text("Play built-in TV apps through your sound system") },
            trailingContent = {
              Switch(
                checked = state.cecSettings.arc,
                enabled = isConnected,
                onCheckedChange = {
                  view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                  repository.toggleCecArc(it)
                },
              )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          )
          ListItem(
            headlineContent = { Text("Send Audio to TV") },
            supportingContent = { Text("Pass audio through HDMI Out to TV speakers") },
            trailingContent = {
              Switch(
                checked = state.dspSettings.hdmiOut,
                enabled = isConnected,
                onCheckedChange = {
                  view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                  repository.toggleHdmiAudioOut(it)
                },
              )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          )
          Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Audio Delay / Lip-Sync",
              style = MaterialTheme.typography.bodyLarge,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              val modes = listOf("bypass" to "Direct (Low Latency)", "match" to "Match TV Video")
              modes.forEachIndexed { idx, (key, label) ->
                SegmentedButton(
                  selected = state.cecSettings.latencyMode == key,
                  enabled = isConnected,
                  onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    repository.setAvrLatencyMode(key)
                  },
                  shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                ) {
                  Text(label)
                }
              }
            }
          }
        }
      }

      // Group 3: Wireless & Phone Audio
      StaggeredEntrance(index = 2) {
        SettingsGroup(
          title = "Wireless & Phone Audio",
          icon = Icons.Rounded.Headphones,
          alpha = controlsAlpha,
        ) {
          ListItem(
            headlineContent = { Text("Bluetooth Pairing") },
            supportingContent = {
              Text(
                if (state.bluetoothDiscoverableActive) {
                  "Visible in your phone's Bluetooth settings now..."
                } else {
                  "Make Axiim Q discoverable as a Bluetooth speaker"
                },
              )
            },
            trailingContent = {
              FilledTonalButton(
                onClick = {
                  view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                  repository.enableBluetoothPairing()
                },
                enabled = isConnected && !state.bluetoothDiscoverableActive,
              ) {
                Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (state.bluetoothDiscoverableActive) "Pairing..." else "Pair")
              }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          )
          ListItem(
            headlineContent = { Text("Stream File to Axiim Q") },
            supportingContent = {
              Text(
                if (state.playToQ.isServing) {
                  "Playing ${state.playToQ.fileName}"
                } else {
                  "Play a song or video from your phone on the sound system"
                },
              )
            },
            trailingContent = {
              if (state.playToQ.isServing) {
                TextButton(onClick = { repository.stopPlayToQ() }) {
                  Text("Stop")
                }
              } else {
                FilledTonalButton(
                  onClick = { mediaPickerLauncher.launch(arrayOf("audio/*", "video/*")) },
                  enabled = isConnected,
                ) {
                  Icon(Icons.Rounded.Cast, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text("Choose File")
                }
              }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
        }
      }

      // Group 4: About & Connection
      StaggeredEntrance(index = 3) {
        SettingsGroup(
          title = "About",
          icon = Icons.Rounded.Info,
          alpha = 1.0f,
        ) {
          ListItem(
            headlineContent = { Text("Switch Axiim Q System") },
            supportingContent = { Text(state.selectedDevice.name) },
            modifier = Modifier.clickable { onOpenDeviceSwitcher() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          )
          ListItem(
            headlineContent = { Text("System Firmware") },
            supportingContent = { Text("Version ${state.firmwareVersion}") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
        }
      }
    }
  }

  if (showRenameDialog) {
    var draftName by
      remember(state.personalize.deviceName) {
        mutableStateOf(state.personalize.deviceName)
      }
    GlassPopupDialog(
      title = "Rename Sound System",
      subtitle = "Choose a friendly name for your Axiim Q on your network.",
      confirmText = "Save",
      onConfirm = {
        repository.updateDeviceName(draftName)
        showRenameDialog = false
      },
      dismissText = "Cancel",
      onDismiss = { showRenameDialog = false },
    ) {
      Box(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        StaggeredEntrance(index = 1) {
          OutlinedTextField(
            value = draftName,
            onValueChange = { draftName = it },
            label = { Text("System Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
private fun SettingsGroup(
  title: String,
  icon: ImageVector,
  alpha: Float = 1.0f,
  content: @Composable () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(end = 8.dp),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
    }
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(24.dp))
          .glassEffect(cornerRadius = 24.dp, alpha = 0.1f),
    ) {
      Column(modifier = Modifier.padding(vertical = 8.dp)) {
        content()
      }
    }
  }
}
