package com.axiim.q.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Snapping rules for the Speaker Placement map. Positions are normalized (0..1) within the room.
 * The Q only reports which channel a speaker is assigned to, so physical placement is app-side.
 */
object SpeakerLayout {
  const val MIN = 0.06f
  const val MAX = 0.94f
  const val GRID_STEP = 0.05f
  const val ALIGN_THRESHOLD = 0.04f

  /** Result of snapping a dragged speaker; [alignedX]/[alignedY] are the guide lines it locked to. */
  data class Snap(
    val position: Offset,
    val alignedX: Float? = null,
    val alignedY: Float? = null,
  )

  fun clamp(position: Offset): Offset =
    Offset(position.x.coerceIn(MIN, MAX), position.y.coerceIn(MIN, MAX))

  /**
   * Snaps each axis independently: first to the nearest other speaker's coordinate within
   * [ALIGN_THRESHOLD] (so speakers line up), otherwise to the [GRID_STEP] grid.
   */
  fun snap(raw: Offset, others: List<Offset>): Snap {
    val p = clamp(raw)
    val alignX = others.map { it.x }.filter { abs(it - p.x) <= ALIGN_THRESHOLD }.minByOrNull { abs(it - p.x) }
    val alignY = others.map { it.y }.filter { abs(it - p.y) <= ALIGN_THRESHOLD }.minByOrNull { abs(it - p.y) }
    val x = alignX ?: snapToGrid(p.x)
    val y = alignY ?: snapToGrid(p.y)
    return Snap(clamp(Offset(x, y)), alignedX = alignX, alignedY = alignY)
  }

  private fun snapToGrid(v: Float): Float = (v / GRID_STEP).roundToInt() * GRID_STEP

  fun encode(positions: Map<String, Offset>): String =
    positions.entries.joinToString(";") { (id, p) -> "$id=${p.x},${p.y}" }

  fun decode(raw: String?): Map<String, Offset> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(";").mapNotNull { entry ->
      val id = entry.substringBefore("=", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val coords = entry.substringAfter("=").split(",")
      val x = coords.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
      val y = coords.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
      id to clamp(Offset(x, y))
    }.toMap()
  }
}
