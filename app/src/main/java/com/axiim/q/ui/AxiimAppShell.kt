package com.axiim.q.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.axiim.q.data.AxiimRepository
import com.axiim.q.model.DiscoveredDevice
import com.axiim.q.theme.AnimatedBackground
import com.axiim.q.theme.AxiimQTheme
import com.axiim.q.theme.GlassPopupDialog
import com.axiim.q.theme.HoloBlueBright
import com.axiim.q.theme.StaggeredEntrance
import com.axiim.q.theme.glassEffect
import com.axiim.q.ui.screens.DashboardScreen
import com.axiim.q.ui.screens.RemoteScreen
import com.axiim.q.ui.screens.SettingsScreen
import com.axiim.q.ui.screens.WisaEqScreen

enum class AppDestinations(
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  HOME("Home", Icons.Rounded.Home, Icons.Outlined.Home),
  AUDIO("Audio", Icons.Rounded.Equalizer, Icons.Outlined.Equalizer),
  REMOTE("Remote", Icons.Rounded.SettingsRemote, Icons.Outlined.SettingsRemote),
  SETTINGS("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

@Composable
fun AxiimAppShell() {
  val context = LocalContext.current
  val view = LocalView.current
  val repository = remember { AxiimRepository(context.applicationContext) }
  val state by repository.state.collectAsState()

  var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
  var showDeviceDialog by rememberSaveable { mutableStateOf(false) }

  val backdropGraphicsLayer = rememberGraphicsLayer()
  var pillRootOffset by remember { mutableStateOf(Offset.Zero) }

  AxiimQTheme {
    Box(modifier = Modifier.fillMaxSize()) {
      // Main background + Scaffold recorded into backdropGraphicsLayer so the Connected Pill blurs content behind it
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .drawWithContent {
              backdropGraphicsLayer.record {
                this@drawWithContent.drawContent()
              }
              drawLayer(backdropGraphicsLayer)
            },
      ) {
        AnimatedBackground(modifier = Modifier.fillMaxSize())

        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color.Transparent,
        ) {
          NavigationSuiteScaffold(
            navigationSuiteItems = {
              AppDestinations.entries.forEach { dest ->
                val selected = dest == currentDestination
                item(
                  icon = {
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = dest.label,
                    )
                  },
                  label = { Text(dest.label) },
                  selected = selected,
                  onClick = {
                    if (currentDestination != dest) {
                      view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                      currentDestination = dest
                    }
                  },
                )
              }
            },
            containerColor = Color.Transparent,
            navigationSuiteColors =
              NavigationSuiteDefaults.colors(
                navigationBarContainerColor = Color.Transparent,
                navigationRailContainerColor = Color.Transparent,
              ),
          ) {
            // Exact screen transition and blur curve from BLE-Control/MainActivity.kt
            AnimatedContent(
              targetState = currentDestination,
              transitionSpec = {
                (slideInVertically { height -> height / 12 } + fadeIn(animationSpec = tween(600)))
                  .togetherWith(fadeOut(animationSpec = tween(400)))
              },
              label = "screenTransition",
              modifier = Modifier.fillMaxSize(),
            ) { destination ->
              val blurRadius by
                transition.animateDp(
                  label = "blur",
                  transitionSpec = { tween(600) },
                ) { targetState ->
                  if (targetState == EnterExitState.Visible) 0.dp else 16.dp
                }

              Box(
                modifier =
                  if (blurRadius > 0.dp) {
                    Modifier.blur(blurRadius).fillMaxSize()
                  } else {
                    Modifier.fillMaxSize()
                  },
              ) {
                when (destination) {
                  AppDestinations.HOME -> DashboardScreen(state = state, repository = repository)
                  AppDestinations.AUDIO -> WisaEqScreen(state = state, repository = repository)
                  AppDestinations.REMOTE -> RemoteScreen(state = state, repository = repository)
                  AppDestinations.SETTINGS ->
                    SettingsScreen(
                      state = state,
                      repository = repository,
                      onOpenDeviceSwitcher = {
                        repository.runSsdpDiscoverySweep()
                        showDeviceDialog = true
                      },
                    )
                }
              }
            }
          }
        }
      }

      // Global Persistent Status Chip (Placed in outer Box like BLE-Control/MainActivity.kt, with real backdrop blur)
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(bottom = 84.dp),
        contentAlignment = Alignment.BottomCenter,
      ) {
        val isConnected = state.isLiveTcpConnected
        val isScanning = state.isDiscoveringSsdp

        val statusText =
          when {
            isConnected -> "Connected"
            isScanning -> "Scanning..."
            else -> "Disconnected"
          }

        val statusColor =
          when {
            isConnected -> Color(0xFF4CAF50)
            isScanning -> Color(0xFFFFC107)
            else -> MaterialTheme.colorScheme.error
          }

        StaggeredEntrance(index = 2) {
          Box(
            modifier =
              Modifier
                .onGloballyPositioned { coords ->
                  pillRootOffset = coords.positionInRoot()
                }
                .clip(RoundedCornerShape(25.dp)),
            contentAlignment = Alignment.Center,
          ) {
            // Real-time backdrop blur of the screen content behind the Connected Pill
            Box(
              modifier =
                Modifier
                  .matchParentSize()
                  .blur(18.dp)
                  .drawWithContent {
                    translate(left = -pillRootOffset.x, top = -pillRootOffset.y) {
                      drawLayer(backdropGraphicsLayer)
                    }
                  },
            )

            AssistChip(
              label = { Text(statusText) },
              onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                repository.runSsdpDiscoverySweep()
                showDeviceDialog = true
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Circle,
                  contentDescription = null,
                  tint = statusColor,
                  modifier = Modifier.size(10.dp),
                )
              },
              shape = RoundedCornerShape(25.dp),
              border = null,
              colors =
                AssistChipDefaults.assistChipColors(
                  containerColor = Color.Transparent,
                ),
              modifier = Modifier.glassEffect(cornerRadius = 25.dp, alpha = 0.2f),
            )
          }
        }
      }

      if (showDeviceDialog) {
        DeviceSelectionDialog(
          devices = state.devices,
          selectedDevice = state.selectedDevice,
          isConnected = state.isLiveTcpConnected,
          isScanning = state.isDiscoveringSsdp,
          onSelectDevice = { device ->
            repository.selectDevice(device)
            showDeviceDialog = false
          },
          onAddManualIp = { name, ip ->
            repository.addManualDevice(name, ip)
            showDeviceDialog = false
          },
          onRescan = {
            repository.runSsdpDiscoverySweep()
            repository.connectToSelectedDevice()
          },
          onDismiss = { showDeviceDialog = false },
        )
      }
    }
  }
}

@Composable
private fun DeviceSelectionDialog(
  devices: List<DiscoveredDevice>,
  selectedDevice: DiscoveredDevice,
  isConnected: Boolean,
  isScanning: Boolean,
  onSelectDevice: (DiscoveredDevice) -> Unit,
  onAddManualIp: (String, String) -> Unit,
  onRescan: () -> Unit,
  onDismiss: () -> Unit,
) {
  var showManualInput by remember { mutableStateOf(false) }
  var manualIp by remember { mutableStateOf("") }

  LaunchedEffect(Unit) {
    onRescan()
  }

  GlassPopupDialog(
    title = "Select Device",
    subtitle = "Please select your Axiim Q sound system to continue.",
    headerTrailing = {
      if (isScanning) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.primary,
        )
      } else {
        IconButton(onClick = onRescan) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh",
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    },
    confirmText = if (showManualInput && manualIp.isNotBlank()) "Connect" else "Rescan",
    onConfirm = {
      if (showManualInput && manualIp.isNotBlank()) {
        onAddManualIp("Axiim Q", manualIp)
      } else {
        onRescan()
      }
    },
    dismissText = "Cancel",
    onDismiss = onDismiss,
  ) {
    LazyColumn(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(300.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(devices) { index, device ->
        val isSelected = device.uuid == selectedDevice.uuid
        StaggeredEntrance(index = index + 1) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .glassEffect(
                  cornerRadius = 16.dp,
                  alpha = if (isSelected) 0.14f else 0.05f,
                )
                .clickable { onSelectDevice(device) }
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
                imageVector = Icons.Rounded.Speaker,
                contentDescription = null,
                tint = if (isSelected) HoloBlueBright else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = device.name.ifBlank { "Axiim Q" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text =
                  when {
                    isSelected && isConnected -> "Connected"
                    isSelected && isScanning -> "Connecting..."
                    else -> "Tap to connect"
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (isSelected && isConnected) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Connected",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }
    }

    if (showManualInput) {
      OutlinedTextField(
        value = manualIp,
        onValueChange = { manualIp = it },
        label = { Text("Axiim Q IP Address") },
        placeholder = { Text("192.168.1.83") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      )
    } else {
      TextButton(
        onClick = { showManualInput = true },
        modifier = Modifier.padding(top = 8.dp),
      ) {
        Text("Connect by IP address")
      }
    }
  }
}
