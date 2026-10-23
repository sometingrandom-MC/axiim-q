package com.axiim.q.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.axiim.q.data.AxiimRepository
import com.axiim.q.data.AxiimUiState
import com.axiim.q.model.EqBandShortLabels
import com.axiim.q.model.SpeakerChannel
import com.axiim.q.theme.HoloBlueBright
import com.axiim.q.theme.HoloBlueCore
import com.axiim.q.theme.StaggeredEntrance
import com.axiim.q.theme.glassEffect
import com.axiim.q.theme.holoGlow
import java.util.Locale
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.axiim.q.model.SpeakerLayout
import com.axiim.q.model.WisaSpeaker

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WisaEqScreen(
  state: AxiimUiState,
  repository: AxiimRepository,
) {
  val view = LocalView.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val isConnected = state.isLiveTcpConnected
  var selectedSection by remember { mutableIntStateOf(0) } // 0 = Equalizer, 1 = Speaker Calibration

  val controlsAlpha by animateFloatAsState(
    targetValue = if (isConnected) 1.0f else 0.42f,
    animationSpec = spring(stiffness = Spring.StiffnessLow),
    label = "audioControlsAlpha",
  )

  DisposableEffect(selectedSection) {
    onDispose {
      if (state.isSpeakerSetupModeActive) {
        repository.setSpeakerSetupMode(false)
      }
      if (state.activeCalibrationNoiseChannel != null) {
        repository.toggleLevelCalibrationNoise(state.activeCalibrationNoiseChannel)
      }
    }
  }

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      LargeTopAppBar(
        title = {
          Text(
            text = "Audio & Tuning",
            fontWeight = FontWeight.Bold,
          )
        },
        scrollBehavior = scrollBehavior,
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
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
          .padding(top = 8.dp, bottom = 140.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      StaggeredEntrance(index = 0) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = selectedSection == 0,
            onClick = {
              view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
              selectedSection = 0
            },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { Icon(Icons.Rounded.Equalizer, contentDescription = null, modifier = Modifier.size(18.dp)) },
          ) {
            Text("Equalizer")
          }
          SegmentedButton(
            selected = selectedSection == 1,
            onClick = {
              view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
              selectedSection = 1
            },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { Icon(Icons.Rounded.SpeakerGroup, contentDescription = null, modifier = Modifier.size(18.dp)) },
          ) {
            Text("Speaker Levels")
          }
        }
      }

      if (selectedSection == 0) {
        StaggeredEntrance(index = 1) {
          EqualizerCard(
            state = state,
            isConnected = isConnected,
            controlsAlpha = controlsAlpha,
            onSelectPreset = { name ->
              view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
              repository.selectEqPreset(name)
            },
            onSelectChannel = { ch ->
              view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
              repository.selectEqChannel(ch)
            },
            onUpdateBandGain = repository::updateEqBandGain,
            onUpdateSubLpf = repository::updateSubwooferLpf,
          )
        }
      } else {
        StaggeredEntrance(index = 1) {
          RoomSpeakerMapCard(
            state = state,
            isConnected = isConnected,
            controlsAlpha = controlsAlpha,
            onToggleSetupMode = {
              view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
              repository.setSpeakerSetupMode(!state.isSpeakerSetupModeActive)
            },
            onToggleSpeakerTone = { speakerId ->
              view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
              repository.toggleSpeakerIdentificationTone(speakerId)
            },
            onMoveSpeaker = repository::setSpeakerPosition,
            onResetLayout = repository::resetSpeakerPositions,
          )
        }

        StaggeredEntrance(index = 2) {
          ChannelLevelsAndDistanceCard(
            state = state,
            isConnected = isConnected,
            controlsAlpha = controlsAlpha,
            onUpdateTrim = repository::updateChannelTrim,
            onTogglePinkNoise = { ch ->
              view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
              repository.toggleLevelCalibrationNoise(ch)
            },
            onUpdateDistance = repository::updateChannelDistance,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerCard(
  state: AxiimUiState,
  isConnected: Boolean,
  controlsAlpha: Float,
  onSelectPreset: (String) -> Unit,
  onSelectChannel: (SpeakerChannel) -> Unit,
  onUpdateBandGain: (Int, Float) -> Unit,
  onUpdateSubLpf: (Int, Float) -> Unit,
) {
  val activePreset = state.activeEqPreset
  val isPerSpeaker = activePreset.name == "per_speaker"
  val currentChannelIndex = if (isPerSpeaker) state.selectedEqChannel.eqIndex else 1
  val currentBands = activePreset.channelBands[currentChannelIndex] ?: List(10) { 0f }

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .alpha(controlsAlpha)
          .clip(RoundedCornerShape(24.dp))
          .glassEffect(cornerRadius = 24.dp, alpha = 0.1f)
          .holoGlow(
            isGlowing = isConnected && activePreset.name != "disabled",
            cornerRadius = 24.dp,
            intensity = 0.7f,
          ),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "10-Band Equalizer",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = activePreset.displayName,
              style = MaterialTheme.typography.bodySmall,
              color = HoloBlueBright,
            )
          }
          if (activePreset.name != "disabled") {
            Text(
              text = "Reset Flat",
              style = MaterialTheme.typography.labelMedium,
              color = HoloBlueBright,
              modifier =
                Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .clickable(enabled = isConnected) { onSelectPreset("disabled") }
                  .padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        // Preset selector row
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          state.eqPresets.forEach { preset ->
            FilterChip(
              selected = preset.active,
              enabled = isConnected,
              onClick = { onSelectPreset(preset.name) },
              label = { Text(preset.displayName) },
              shape = RoundedCornerShape(14.dp),
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = HoloBlueCore.copy(alpha = 0.24f),
                  selectedLabelColor = HoloBlueBright,
                ),
            )
          }
        }

        // Per-speaker channel selector when in 8x10 mode
        if (isPerSpeaker) {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SpeakerChannel.entries.forEach { ch ->
              val selected = ch == state.selectedEqChannel
              FilterChip(
                selected = selected,
                enabled = isConnected,
                onClick = { onSelectChannel(ch) },
                label = { Text(ch.displayName) },
                shape = RoundedCornerShape(12.dp),
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = HoloBlueCore.copy(alpha = 0.24f),
                    selectedLabelColor = HoloBlueBright,
                  ),
              )
            }
          }
        }

        // Glowing Holo Blue Frequency Response Spline
        EqResponseCurveCanvas(
          bands = currentBands,
          modifier =
            Modifier
              .fillMaxWidth()
              .height(110.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f)),
        )

        // 10 Vertical Band Sliders (-5.0 to +5.0 dB)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          currentBands.forEachIndexed { index, gainDb ->
            Column(
              modifier = Modifier.width(44.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = String.format(Locale.US, "%+.1f", gainDb),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (gainDb != 0f) HoloBlueBright else MaterialTheme.colorScheme.onSurfaceVariant,
              )

              Slider(
                value = gainDb,
                onValueChange = { onUpdateBandGain(index, it) },
                valueRange = -5f..5f,
                steps = 19,
                enabled = isConnected,
                modifier =
                  Modifier
                    .graphicsLayer {
                      rotationZ = 270f
                      transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .layout { measurable, constraints ->
                      val placeable =
                        measurable.measure(
                          Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                          ),
                        )
                      layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                      }
                    }
                    .width(150.dp)
                    .height(36.dp),
                colors =
                  SliderDefaults.colors(
                    thumbColor = HoloBlueBright,
                    activeTrackColor = HoloBlueCore,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                  ),
              )

              Text(
                text = EqBandShortLabels.getOrElse(index) { "" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    // Subwoofer Crossover Card
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Subwoofer Crossover",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Low-Pass Frequency",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "${activePreset.lpf1Freq} Hz",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = HoloBlueBright,
            fontWeight = FontWeight.Bold,
          )
        }
        Slider(
          value = activePreset.lpf1Freq.toFloat(),
          onValueChange = { onUpdateSubLpf(it.toInt(), activePreset.lpf1Q) },
          valueRange = 40f..200f,
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
}

@Composable
private fun EqResponseCurveCanvas(
  bands: List<Float>,
  modifier: Modifier = Modifier,
) {
  val holoCore = HoloBlueCore
  val holoBright = HoloBlueBright
  Canvas(modifier = modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
    val w = size.width
    val h = size.height
    val midY = h / 2f

    // 0 dB center line
    drawLine(
      color = Color.White.copy(alpha = 0.14f),
      start = Offset(0f, midY),
      end = Offset(w, midY),
      strokeWidth = 1.dp.toPx(),
    )

    if (bands.isEmpty()) return@Canvas
    val points =
      bands.mapIndexed { idx, gain ->
        val x = if (bands.size > 1) (idx.toFloat() / (bands.size - 1)) * w else w / 2f
        val normalized = (gain / 5f).coerceIn(-1f, 1f)
        val y = midY - normalized * (midY * 0.82f)
        Offset(x, y)
      }

    val curvePath =
      Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.lastIndex) {
          val p0 = points[i]
          val p1 = points[i + 1]
          val controlX = (p0.x + p1.x) / 2f
          cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
        }
      }

    val fillPath =
      Path().apply {
        addPath(curvePath)
        lineTo(points.last().x, h)
        lineTo(points.first().x, h)
        close()
      }

    drawPath(
      path = fillPath,
      brush =
        Brush.verticalGradient(
          colors = listOf(holoCore.copy(alpha = 0.35f), Color.Transparent),
        ),
    )

    drawPath(
      path = curvePath,
      color = holoBright,
      style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
    )

    points.forEach { pt ->
      drawCircle(
        color = holoBright,
        radius = 3.5.dp.toPx(),
        center = pt,
      )
    }
  }
}

@Composable
private fun RoomSpeakerMapCard(
  state: AxiimUiState,
  isConnected: Boolean,
  controlsAlpha: Float,
  onToggleSetupMode: () -> Unit,
  onToggleSpeakerTone: (String) -> Unit,
  onMoveSpeaker: (String, Offset) -> Unit,
  onResetLayout: () -> Unit,
) {
  val holoCore = HoloBlueCore
  val holoBright = HoloBlueBright
  val view = LocalView.current
  val haptic = LocalHapticFeedback.current
  val textMeasurer = rememberTextMeasurer()
  val labelColor = MaterialTheme.colorScheme.onSurface
  val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
  val hitRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }

  // Gesture detectors are long-lived; read the latest values through these instead of restarting them.
  val currentState by rememberUpdatedState(state)
  val currentIsConnected by rememberUpdatedState(isConnected)
  val currentOnTone by rememberUpdatedState(onToggleSpeakerTone)
  val currentOnMove by rememberUpdatedState(onMoveSpeaker)

  var draggingId by remember { mutableStateOf<String?>(null) }
  var dragRaw by remember { mutableStateOf(Offset.Zero) }
  var dragSnap by remember { mutableStateOf<SpeakerLayout.Snap?>(null) }

  fun nearestSpeaker(touch: Offset, size: IntSize): WisaSpeaker? {
    val (speaker, distance) =
      currentState.speakers
        .map { sp ->
          val p = currentState.positionFor(sp)
          sp to (Offset(p.x * size.width, p.y * size.height) - touch).getDistance()
        }.minByOrNull { it.second } ?: return null
    return speaker.takeIf { distance <= hitRadiusPx }
  }

  fun snapTargets(excludingId: String): List<Offset> =
    currentState.speakers.filter { it.id != excludingId }.map { currentState.positionFor(it) } + ListenerPosition

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .alpha(controlsAlpha)
        .clip(RoundedCornerShape(24.dp))
        .glassEffect(cornerRadius = 24.dp, alpha = 0.1f)
        .holoGlow(
          isGlowing = state.activeToneSpeakerId != null,
          cornerRadius = 24.dp,
          intensity = 0.85f,
        ),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Speaker Placement",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Tap to chime · hold & drag to move",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (state.speakerPositions.isNotEmpty()) {
            Text(
              text = "Reset layout",
              style = MaterialTheme.typography.labelMedium,
              color = HoloBlueBright,
              modifier =
                Modifier
                  .padding(top = 4.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onResetLayout()
                  }
                  .padding(vertical = 4.dp),
            )
          }
        }
        FilledTonalButton(
          onClick = onToggleSetupMode,
          enabled = isConnected,
        ) {
          Text(if (state.isSpeakerSetupModeActive) "Done" else "Find Speakers")
        }
      }

      // Interactive Room Canvas
      val speakers = state.speakers
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f))
            .pointerInput(Unit) {
              detectTapGestures(
                // A hold belongs to the drag detector; without this a hold-and-release would also chime.
                onLongPress = {},
                onTap = { tap ->
                  if (!currentIsConnected) return@detectTapGestures
                  nearestSpeaker(tap, size)?.let { currentOnTone(it.id) }
                },
              )
            }
            .pointerInput(Unit) {
              fun endDrag(commit: Boolean) {
                val id = draggingId
                val snap = dragSnap
                if (commit && id != null && snap != null) currentOnMove(id, snap.position)
                draggingId = null
                dragSnap = null
              }
              detectDragGesturesAfterLongPress(
                onDragStart = { start ->
                  val speaker = nearestSpeaker(start, size) ?: return@detectDragGesturesAfterLongPress
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  draggingId = speaker.id
                  dragRaw = currentState.positionFor(speaker)
                  dragSnap = SpeakerLayout.snap(dragRaw, snapTargets(speaker.id))
                },
                onDrag = { _, amount ->
                  val id = draggingId ?: return@detectDragGesturesAfterLongPress
                  dragRaw = SpeakerLayout.clamp(dragRaw + Offset(amount.x / size.width, amount.y / size.height))
                  val next = SpeakerLayout.snap(dragRaw, snapTargets(id))
                  val previous = dragSnap
                  val newAlignment =
                    (next.alignedX != null && next.alignedX != previous?.alignedX) ||
                      (next.alignedY != null && next.alignedY != previous?.alignedY)
                  if (newAlignment) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                  dragSnap = next
                },
                onDragEnd = { endDrag(commit = true) },
                onDragCancel = { endDrag(commit = false) },
              )
            },
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val center = Offset(size.width * ListenerPosition.x, size.height * ListenerPosition.y)
          drawCircle(
            color = holoCore.copy(alpha = 0.10f),
            radius = size.minDimension * 0.36f,
            center = center,
          )
          drawCircle(
            color = holoCore.copy(alpha = 0.25f),
            radius = 8.dp.toPx(),
            center = center,
          )

          // Alignment guides while dragging
          dragSnap?.let { snap ->
            val guide = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            snap.alignedX?.let { x ->
              drawLine(
                color = holoBright.copy(alpha = 0.5f),
                start = Offset(x * size.width, 0f),
                end = Offset(x * size.width, size.height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = guide,
              )
            }
            snap.alignedY?.let { y ->
              drawLine(
                color = holoBright.copy(alpha = 0.5f),
                start = Offset(0f, y * size.height),
                end = Offset(size.width, y * size.height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = guide,
              )
            }
          }

          speakers.forEach { sp ->
            val ch = SpeakerChannel.fromLoc(sp.loc) ?: SpeakerChannel.CENTER
            val isDragging = sp.id == draggingId
            val normalized = if (isDragging) dragSnap?.position ?: state.positionFor(sp) else state.positionFor(sp)
            val pos = Offset(normalized.x * size.width, normalized.y * size.height)
            val isPlayingTone = state.activeToneSpeakerId == sp.id
            val radius =
              when {
                isDragging -> 18.dp.toPx()
                isPlayingTone -> 16.dp.toPx()
                else -> 12.dp.toPx()
              }
            drawLine(
              color =
                if (isPlayingTone) {
                  holoBright
                } else {
                  Color.White.copy(alpha = 0.12f)
                },
              start = center,
              end = pos,
              strokeWidth = if (isPlayingTone) 2.5.dp.toPx() else 1.dp.toPx(),
            )
            drawCircle(
              color = if (isPlayingTone || isDragging) holoBright else holoCore,
              radius = radius,
              center = pos,
            )

            // Channel label below the dot (above it near the bottom edge so it isn't clipped)
            val label = textMeasurer.measure(ch.shortLabel, labelStyle)
            val gap = 4.dp.toPx()
            val labelY =
              if (pos.y + radius + gap + label.size.height > size.height) {
                pos.y - radius - gap - label.size.height
              } else {
                pos.y + radius + gap
              }
            drawText(
              textLayoutResult = label,
              color = if (isPlayingTone || isDragging) holoBright else labelColor,
              topLeft =
                Offset(
                  (pos.x - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)),
                  labelY,
                ),
            )
          }
        }
      }
    }
  }
}

/** Listening position at the center of the room map (normalized). */
private val ListenerPosition = Offset(0.5f, 0.54f)

@Composable
private fun ChannelLevelsAndDistanceCard(
  state: AxiimUiState,
  isConnected: Boolean,
  controlsAlpha: Float,
  onUpdateTrim: (SpeakerChannel, Int) -> Unit,
  onTogglePinkNoise: (SpeakerChannel) -> Unit,
  onUpdateDistance: (SpeakerChannel, Float) -> Unit,
) {
  val distanceByChannel = state.channelDistances.associateBy { it.channel }

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
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Column {
        Text(
          text = "Channel Trim & Distance",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Balance speaker volume (-20 to +20 dB) and listening distance",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      state.channelTrims.forEach { trim ->
        val channel = trim.channel
        val distFeet = distanceByChannel[channel]?.distanceFeet ?: 10.0f
        val isNoisePlaying = state.activeCalibrationNoiseChannel == channel

        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.45f))
              .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = channel.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text =
                  String.format(
                    Locale.US,
                    "Level %+d dB • %.1f ft",
                    trim.volumeDb,
                    distFeet,
                  ),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = HoloBlueBright,
              )
            }

            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              FilledTonalIconButton(
                onClick = { onTogglePinkNoise(channel) },
                enabled = isConnected,
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = if (isNoisePlaying) Icons.Rounded.Stop else Icons.Rounded.RecordVoiceOver,
                  contentDescription = "Test Tone",
                  modifier = Modifier.size(16.dp),
                )
              }
              FilledTonalIconButton(
                onClick = { onUpdateTrim(channel, trim.volumeDb - 1) },
                enabled = isConnected,
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Remove,
                  contentDescription = "Decrease Trim",
                  modifier = Modifier.size(16.dp),
                )
              }
              FilledTonalIconButton(
                onClick = { onUpdateTrim(channel, trim.volumeDb + 1) },
                enabled = isConnected,
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Add,
                  contentDescription = "Increase Trim",
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }

          Slider(
            value = distFeet,
            onValueChange = { onUpdateDistance(channel, it) },
            valueRange = 0f..40f,
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
  }
}
