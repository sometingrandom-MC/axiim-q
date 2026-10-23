package com.axiim.q.model

import androidx.compose.ui.geometry.Offset

/**
 * Configurable Quick Setting tiles for the Home Screen.
 * Note: DTS_NEO6 is disabled by default and can be toggled on/off by the user.
 */
enum class QuickTileId(
  val id: String,
  val title: String,
  val description: String,
  val defaultEnabled: Boolean,
) {
  DTS_NEURAL(
    id = "dts_neural",
    title = "DTS Neural",
    description = "Surround upmixing for stereo & 5.1 audio",
    defaultEnabled = true,
  ),
  NIGHT_MODE(
    id = "night_mode",
    title = "Night Mode",
    description = "Tap for On/Off • Hold for Auto dynamic range",
    defaultEnabled = true,
  ),
  SUPER_STEREO(
    id = "super_stereo",
    title = "Super Stereo",
    description = "Rich multi-speaker stereo for music",
    defaultEnabled = true,
  ),
  PARTY_MODE(
    id = "party_mode",
    title = "Party Mode",
    description = "Full-range stereo across all speakers",
    defaultEnabled = true,
  ),
  EQUALIZER(
    id = "equalizer",
    title = "Equalizer",
    description = "Tap to toggle EQ • Hold to change preset",
    defaultEnabled = true,
  ),
  LISTEN_ON_PHONE(
    id = "listen_on_phone",
    title = "Private Listen",
    description = "Stream live audio to your phone headphones",
    defaultEnabled = true,
  ),
  LFE_BOOST(
    id = "lfe_boost",
    title = "Sub Boost",
    description = "+10 dB subwoofer boost on TrueHD movies",
    defaultEnabled = false,
  ),
  AUTO_GAIN(
    id = "auto_gain",
    title = "Volume Leveler",
    description = "Keep volume consistent across sources",
    defaultEnabled = false,
  ),
  FRONT_LED(
    id = "front_led",
    title = "Status Light",
    description = "Front panel blue light on the Axiim Q",
    defaultEnabled = false,
  ),
  DTS_NEO6(
    id = "dts_neo6",
    title = "DTS Neo:6",
    description = "Tap for Cinema/Off • Hold for Music mode",
    defaultEnabled = false,
  );

  companion object {
    val defaultIds: List<QuickTileId> = entries.filter { it.defaultEnabled }

    fun fromId(id: String): QuickTileId? = entries.find { it.id == id }
  }
}

data class DiscoveredDevice(
  val uuid: String,
  val name: String,
  val ip: String,
  val port: Int = 80,
  val rpcPort: Int = 3000,
  val accentColor: String = "#33B5E5",
  val serverInfo: String = "axiim-Zopa/2019.0501.0073",
  val userAdded: Boolean = false,
  val missedDiscoveries: Int = 0,
  val isOnline: Boolean = true,
) {
  val macAddress: String
    get() {
      val clean = uuid.replace("-", "").uppercase()
      return if (clean.length >= 12) {
        clean.takeLast(12).chunked(2).joinToString(":")
      } else {
        "98:E8:48:60:00:FC"
      }
    }
}

enum class SpeakerChannel(
  val upperLoc: String,
  val lowerLoc: String,
  val eqIndex: Int,
  val displayName: String,
  val shortLabel: String,
  val roomPosition: Offset,
) {
  FRONT_LEFT("FL", "fl", 1, "Front Left", "FL", Offset(0.20f, 0.20f)),
  FRONT_RIGHT("FR", "fr", 2, "Front Right", "FR", Offset(0.80f, 0.20f)),
  SUBWOOFER("S", "s", 3, "Subwoofer", "SUB", Offset(0.50f, 0.80f)),
  CENTER("C", "c", 4, "Center", "C", Offset(0.50f, 0.14f)),
  SIDE_LEFT("SL", "sl", 5, "Side Left", "SL", Offset(0.12f, 0.54f)),
  SIDE_RIGHT("SR", "sr", 6, "Side Right", "SR", Offset(0.88f, 0.54f)),
  REAR_LEFT("RL", "rl", 7, "Rear Left", "RL", Offset(0.26f, 0.86f)),
  REAR_RIGHT("RR", "rr", 8, "Rear Right", "RR", Offset(0.74f, 0.86f));

  companion object {
    fun fromLoc(loc: String): SpeakerChannel? {
      return entries.find {
        it.upperLoc.equals(loc, ignoreCase = true) || it.lowerLoc.equals(loc, ignoreCase = true)
      }
    }
  }
}

data class AvrInput(
  val port: Int,
  val label: String,
  val active: Boolean = false,
  val connected: Boolean = false,
  val isCecDevice: Boolean = false,
  val logo: String = "",
  val mfg: String = "",
  val model: String = "",
  val logoUrl: String? = null,
  val latency: String = "normal",
) {
  val portDisplayName: String
    get() = if (port == 0) "Axiim Q" else "HDMI $port"
}

data class WisaSpeaker(
  val id: String,
  val name: String,
  val desc: String,
  val connected: Boolean,
  val type: String,
  val defaultLoc: String = "",
  val groupName: String = "default",
  val loc: String,
)

data class WisaGroup(
  val id: String,
  val name: String,
  val enabled: Boolean = true,
  val defaultGroup: Boolean = true,
  val speakerLocations: Map<String, String> = emptyMap(),
)

data class ChannelTrim(
  val channel: SpeakerChannel,
  val volumeDb: Int,
)

data class ChannelDistance(
  val channel: SpeakerChannel,
  val distanceFeet: Float,
)

val EqBandFrequencies = listOf(
  "31 Hz",
  "63 Hz",
  "125 Hz",
  "250 Hz",
  "500 Hz",
  "1 kHz",
  "2 kHz",
  "4 kHz",
  "8 kHz",
  "16 kHz",
)

val EqBandShortLabels = listOf(
  "31",
  "63",
  "125",
  "250",
  "500",
  "1k",
  "2k",
  "4k",
  "8k",
  "16k",
)

data class EqualizerPreset(
  val name: String,
  val displayName: String,
  val preset: Int,
  val mode: Int,
  val uEqLinked: Int,
  val active: Boolean = false,
  val powerUser: Boolean = false,
  val channelBands: Map<Int, List<Float>> = (1..8).associateWith { List(10) { 0f } },
  val gainCompression: Int = 0,
  val lpf1Type: String = "lpf",
  val lpf1Freq: Int = 80,
  val lpf1Q: Float = 0.54f,
  val lpf2Type: String = "lpf",
  val lpf2Freq: Int = 80,
  val lpf2Q: Float = 1.31f,
)

data class AudioDspSettings(
  val partyModeEnable: Boolean = false,
  val superStereoEnable: Boolean = false,
  val dtsNeuralEnable: Boolean = true,
  val neo6Mode: String = "disabled",
  val dolbyDrc: String = "off",
  val lfeBoostTrueHd: Boolean = true,
  val autoFormatGain: Boolean = true,
  val klipschLedsEnabled: Boolean = true,
  val hdmiOut: Boolean = false,
  val highVolume: Int = 40,
  val sampleRate: Int = 48000,
)

data class VideoSignalTelemetry(
  val width: Int = 1920,
  val height: Int = 1080,
  val refresh: Int = 60,
  val interlaced: Boolean = false,
  val stereoType: String = "none",
  val clampEnable: Boolean = true,
  val pixelFormat: String = "RGB",
  val colorSpace: String = "RGB",
  val pixelDepth: Int = 24,
  val autoRefresh: Boolean = true,
  val auto3d: Boolean = false,
)

data class PersonalizeSettings(
  val blueLed: Boolean = false,
  val menuWrap: Boolean = true,
  val deviceName: String = "Axiim Q",
  val accentColor: String = "#33B5E5",
  val menuOnUp: Boolean = true,
  val powerUser: Boolean = true,
  val timezone: String = "America/New_York",
)

data class CecPortDevice(
  val portIndex: Int,
  val osdName: String = "",
  val logicalAddress: Int = -1,
  val physicalAddress: String = "",
  val vendorId: Int = 0,
  val isCecDevice: Boolean = false,
  val powerStatus: Int = 0,
)

data class CecSettings(
  val enabled: Boolean = true,
  val arc: Boolean = true,
  val preArcPort: Int = 0,
  val latencyMode: String = "bypass",
  val ports: List<CecPortDevice> = emptyList(),
)

data class SystemCapabilities(
  val uhdtv: Boolean = false,
  val calibration: Boolean = false,
  val cec: Boolean = true,
  val listen: Boolean = true,
  val updates: Boolean = true,
  val speakerSetup: Boolean = true,
  val powerUser: Boolean = true,
  val menuOnUp: Boolean = true,
  val sii: Boolean = true,
)

data class NowPlayingItem(
  val type: String = "music",
  val id: Int = 0,
  val title: String = "",
  val artist: String = "",
  val album: String = "",
  val image: String = "",
  val thumbnail: String = "",
  val description: String = "",
  val runtime: Int = 0,
)

enum class ODataMediaCategory(val path: String, val displayName: String, val playerType: String) {
  MUSIC("/-/music", "Songs", "music"),
  ARTISTS("/-/music/artists", "Artists", "music"),
  ALBUMS("/-/music/albums", "Albums", "music"),
  VIDEOS("/-/videos", "Videos", "videos"),
  MOVIES("/-/videos/movies", "Movies", "videos/movies"),
  PICTURES("/-/pictures", "Photos", "pictures"),
}

data class ODataMediaItem(
  val id: String,
  val title: String,
  val artistOrSubtitle: String = "",
  val album: String = "",
  val thumbnailUrl: String? = null,
  val runtimeSeconds: Int = 0,
  val category: ODataMediaCategory = ODataMediaCategory.MUSIC,
)

data class DiagnosticReport(
  val code: String,
  val description: String,
)

enum class RemoteKey(val keyCode: Int, val label: String, val udpStringSymbol: String? = null) {
  POWER(116, "Power"),
  UP(103, "Up"),
  DOWN(108, "Down"),
  LEFT(105, "Left"),
  RIGHT(106, "Right"),
  ENTER(28, "Select"),
  PREVIOUS(352, "Previous"),
  BACK(158, "Back"),
  HOME(102, "Home"),
  MENU_INFO(139, "Menu"),
  PLAY_PAUSE(164, "Play / Pause"),
  PLAY(207, "Play"),
  PAUSE(119, "Pause"),
  STOP(128, "Stop"),
  REWIND(168, "Rewind"),
  FAST_FORWARD(208, "Fast Forward"),
  SKIP_BACK(165, "Previous"),
  SKIP_FORWARD(163, "Next"),
  RECORD(167, "Record"),
  CHANNEL_UP(402, "Ch +"),
  CHANNEL_DOWN(403, "Ch -"),
  LIVE_TV(170, "Live TV"),
  GUIDE(172, "Guide"),
  RECORDED_TV(173, "Recorded TV"),
  NUM_1(2, "1"),
  NUM_2(3, "2"),
  NUM_3(4, "3"),
  NUM_4(5, "4"),
  NUM_5(6, "5"),
  NUM_6(7, "6"),
  NUM_7(8, "7"),
  NUM_8(9, "8"),
  NUM_9(10, "9"),
  NUM_0(11, "0"),
  BACKSPACE(14, "Bksp"),
  STAR(522, "*", "*"),
  POUND(523, "#", "#"),
  RED(64, "Red"),
  GREEN(65, "Green"),
  YELLOW(87, "Yellow"),
  BLUE(88, "Blue"),
  SCROLL_UP(177, "Scroll Up"),
  SCROLL_DOWN(178, "Scroll Down"),
  SCROLL_LEFT(65641, "Scroll Left"),
  SCROLL_RIGHT(65642, "Scroll Right"),
}
