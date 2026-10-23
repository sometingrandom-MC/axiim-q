package com.axiim.q.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.axiim.q.model.AudioDspSettings
import com.axiim.q.model.AvrInput
import com.axiim.q.model.CecPortDevice
import com.axiim.q.model.CecSettings
import com.axiim.q.model.ChannelDistance
import com.axiim.q.model.ChannelTrim
import com.axiim.q.model.DiagnosticReport
import com.axiim.q.model.DiscoveredDevice
import com.axiim.q.model.EqualizerPreset
import com.axiim.q.model.NowPlayingItem
import com.axiim.q.model.ODataMediaCategory
import com.axiim.q.model.ODataMediaItem
import com.axiim.q.model.PersonalizeSettings
import com.axiim.q.model.QuickTileId
import com.axiim.q.model.RemoteKey
import com.axiim.q.model.SpeakerChannel
import com.axiim.q.model.SpeakerLayout
import com.axiim.q.model.SystemCapabilities
import com.axiim.q.model.VideoSignalTelemetry
import com.axiim.q.model.WisaGroup
import com.axiim.q.model.WisaSpeaker
import com.axiim.q.network.AxiimRpcClient
import com.axiim.q.network.IpAudioReceiver
import com.axiim.q.network.IpAudioState
import com.axiim.q.network.ODataMediaClient
import com.axiim.q.network.PlayToQHttpServer
import com.axiim.q.network.PlayToQServerState
import com.axiim.q.network.RpcNotification
import com.axiim.q.network.SsdpDiscoveryClient
import com.axiim.q.network.primitiveOrNull
import com.axiim.q.network.scalarOrNull
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

data class AxiimUiState(
  val selectedDevice: DiscoveredDevice = DefaultLiveSnapshot.defaultDevice,
  val devices: List<DiscoveredDevice> = listOf(DefaultLiveSnapshot.defaultDevice),
  val isDiscoveringSsdp: Boolean = false,
  val isConnecting: Boolean = false,
  val isLiveTcpConnected: Boolean = false,
  val isSimulatedPreview: Boolean = false,
  val enabledQuickTiles: List<QuickTileId> = QuickTileId.defaultIds,
  /** App theme: false = built-in Axiim palette, true = follow the phone's Material You wallpaper colors. */
  val materialYouColors: Boolean = false,
  val lastActiveEqPresetName: String = "custom",
  val firmwareVersion: String = "19.618.421",
  val powered: Boolean = true,
  val volume: Int = 65,
  val muted: Boolean = false,
  val isUserDraggingVolume: Boolean = false,
  val inputs: List<AvrInput> = DefaultLiveSnapshot.defaultInputs,
  val showDisconnectedInputs: Boolean = false,
  val videoTelemetry: VideoSignalTelemetry = DefaultLiveSnapshot.defaultVideo,
  val speakers: List<WisaSpeaker> = DefaultLiveSnapshot.defaultSpeakers,
  /** User-placed map positions (normalized 0..1), keyed by speaker id. */
  val speakerPositions: Map<String, Offset> = emptyMap(),
  val groups: List<WisaGroup> = DefaultLiveSnapshot.defaultGroups,
  val activeToneSpeakerId: String? = null,
  val activeCalibrationNoiseChannel: SpeakerChannel? = null,
  val isSpeakerSetupModeActive: Boolean = false,
  val channelTrims: List<ChannelTrim> = DefaultLiveSnapshot.defaultTrims,
  val channelDistances: List<ChannelDistance> = DefaultLiveSnapshot.defaultDistances,
  val eqPresets: List<EqualizerPreset> = DefaultLiveSnapshot.defaultEqPresets,
  val selectedEqChannel: SpeakerChannel = SpeakerChannel.FRONT_LEFT,
  val dspSettings: AudioDspSettings = AudioDspSettings(),
  val personalize: PersonalizeSettings = PersonalizeSettings(),
  val cecSettings: CecSettings = DefaultLiveSnapshot.defaultCec,
  val capabilities: SystemCapabilities = SystemCapabilities(),
  val nowPlaying: NowPlayingItem? = DefaultLiveSnapshot.defaultNowPlaying,
  val isMediaPlaying: Boolean = false,
  val odataCategory: ODataMediaCategory = ODataMediaCategory.MUSIC,
  val odataItems: List<ODataMediaItem> = DefaultLiveSnapshot.defaultODataMusic,
  val isLoadingOData: Boolean = false,
  val ipAudio: IpAudioState = IpAudioState(),
  val playToQ: PlayToQServerState = PlayToQServerState(),
  val customerEmail: String = "",
  val diagnosticHistory: List<DiagnosticReport> = emptyList(),
  val bluetoothDiscoverableActive: Boolean = false,
  val lastRemoteKeySent: RemoteKey? = null,
  val statusBannerMessage: String? = null,
) {
  val activeInput: AvrInput
    get() = inputs.firstOrNull { it.active } ?: inputs.firstOrNull() ?: DefaultLiveSnapshot.defaultInputs[2]

  val activeEqPreset: EqualizerPreset
    get() = eqPresets.firstOrNull { it.active } ?: eqPresets.first()

  /** Where to draw [speaker] on the placement map: the user's spot, else its channel's default. */
  fun positionFor(speaker: WisaSpeaker): Offset =
    speakerPositions[speaker.id]
      ?: (SpeakerChannel.fromLoc(speaker.loc) ?: SpeakerChannel.CENTER).roomPosition
}

/**
 * Exact live-captured hardware snapshot from `192.168.1.83` (`v19.618.421`) documented in
 * `docs/api-reference.md`, `docs/data-models.md`, and `docs/wisa.md`.
 */
object DefaultLiveSnapshot {
  val defaultDevice =
    DiscoveredDevice(
      uuid = "4365746F-6E2D-5A6F-7061-98E8486000FC",
      name = "axiim Q",
      ip = "192.168.1.83",
      port = 80,
      rpcPort = 3000,
      accentColor = "#00FFBF",
      serverInfo = "axiim-Zopa/2019.0501.0073 UPnP/1.1 zopa_ssdp/2019.0501.0073",
      userAdded = false,
      missedDiscoveries = 0,
      isOnline = true,
    )

  val defaultInputs =
    listOf(
      AvrInput(
        port = 0,
        label = "axiim Q",
        active = false,
        connected = true,
        isCecDevice = false,
        logo = "q",
        mfg = "axiim",
        model = "q",
        latency = "normal",
      ),
      AvrInput(
        port = 1,
        label = "HDMI 1",
        active = false,
        connected = false,
        isCecDevice = false,
      ),
      AvrInput(
        port = 2,
        label = "SHIELD",
        active = true,
        connected = true,
        isCecDevice = true,
        logo = "nvidia",
        mfg = "nvidia",
        model = "nvidia",
        logoUrl = "/cloud/img/nvidia.png",
        latency = "normal",
      ),
      AvrInput(
        port = 3,
        label = "HDMI 3",
        active = false,
        connected = true,
        isCecDevice = false,
      ),
      AvrInput(
        port = 4,
        label = "HDMI 4",
        active = false,
        connected = false,
        isCecDevice = false,
      ),
      AvrInput(
        port = 5,
        label = "HDMI 5",
        active = false,
        connected = false,
        isCecDevice = false,
      ),
      AvrInput(
        port = 6,
        label = "HDMI 6",
        active = false,
        connected = false,
        isCecDevice = false,
      ),
    )

  val defaultVideo =
    VideoSignalTelemetry(
      width = 1920,
      height = 1080,
      refresh = 59,
      interlaced = false,
      pixelFormat = "RGB",
      colorSpace = "RGB",
      pixelDepth = 24,
      autoRefresh = true,
      auto3d = false,
    )

  val defaultSpeakers =
    listOf(
      WisaSpeaker(
        id = "02:EA:31:00:A3:EB",
        name = "Center",
        desc = "axiim XM.4211CS",
        connected = true,
        type = "center",
        defaultLoc = "C",
        groupName = "default",
        loc = "C",
      ),
      WisaSpeaker(
        id = "02:EA:31:00:F8:E1",
        name = "Front Right",
        desc = "axiim XM.4111SS",
        connected = true,
        type = "surround",
        groupName = "default",
        loc = "FR",
      ),
      WisaSpeaker(
        id = "02:EA:31:00:A8:E0",
        name = "Front Left",
        desc = "axiim XM.4111SS",
        connected = true,
        type = "surround",
        groupName = "default",
        loc = "FL",
      ),
      WisaSpeaker(
        id = "02:EA:31:00:F9:04",
        name = "Side Right",
        desc = "axiim XM.4111SS",
        connected = true,
        type = "surround",
        groupName = "default",
        loc = "SR",
      ),
      WisaSpeaker(
        id = "02:EA:31:00:A0:16",
        name = "Side Left",
        desc = "axiim XM.4111SS",
        connected = true,
        type = "surround",
        groupName = "default",
        loc = "SL",
      ),
      WisaSpeaker(
        id = "02:EA:31:00:A3:58",
        name = "Subwoofer",
        desc = "axiim XM.101SW",
        connected = true,
        type = "subwoofer",
        defaultLoc = "S",
        groupName = "default",
        loc = "S",
      ),
    )

  val defaultGroups =
    listOf(
      WisaGroup(
        id = "ebac4547-d82b-445a-a2a8-72cc91737992",
        name = "default",
        enabled = true,
        defaultGroup = true,
        speakerLocations =
          mapOf(
            "02:EA:31:00:A3:EB" to "C",
            "02:EA:31:00:F8:E1" to "FR",
            "02:EA:31:00:A8:E0" to "FL",
            "02:EA:31:00:F9:04" to "SR",
            "02:EA:31:00:A0:16" to "SL",
            "02:EA:31:00:A3:58" to "S",
          ),
      ),
    )

  val defaultTrims =
    listOf(
      ChannelTrim(SpeakerChannel.FRONT_LEFT, 0),
      ChannelTrim(SpeakerChannel.FRONT_RIGHT, 0),
      ChannelTrim(SpeakerChannel.CENTER, 0),
      ChannelTrim(SpeakerChannel.SUBWOOFER, 0),
      ChannelTrim(SpeakerChannel.SIDE_LEFT, -2),
      ChannelTrim(SpeakerChannel.SIDE_RIGHT, -2),
      ChannelTrim(SpeakerChannel.REAR_LEFT, 0),
      ChannelTrim(SpeakerChannel.REAR_RIGHT, 0),
    )

  val defaultDistances =
    listOf(
      ChannelDistance(SpeakerChannel.FRONT_LEFT, 11.8f),
      ChannelDistance(SpeakerChannel.FRONT_RIGHT, 11.8f),
      ChannelDistance(SpeakerChannel.CENTER, 12.0f),
      ChannelDistance(SpeakerChannel.SUBWOOFER, 1.0f),
      ChannelDistance(SpeakerChannel.SIDE_LEFT, 6.5f),
      ChannelDistance(SpeakerChannel.SIDE_RIGHT, 9.0f),
      ChannelDistance(SpeakerChannel.REAR_LEFT, 0.0f),
      ChannelDistance(SpeakerChannel.REAR_RIGHT, 0.0f),
    )

  private fun bandsForAllChannels(curve: List<Float>): Map<Int, List<Float>> =
    (1..8).associateWith { curve }

  val defaultEqPresets =
    listOf(
      EqualizerPreset(
        name = "disabled",
        displayName = "Flat (Off)",
        preset = 0,
        mode = 0,
        uEqLinked = 0,
        active = true,
        channelBands = bandsForAllChannels( listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
      ),
      EqualizerPreset(
        name = "custom",
        displayName = "Custom 10-Band",
        preset = 0,
        mode = 0,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(2.0f, 1.5f, 0.5f, 0.0f, -0.5f, 0.0f, 1.0f, 2.0f, 2.5f, 2.0f)),
      ),
      EqualizerPreset(
        name = "per_speaker",
        displayName = "Per-Speaker 8×10",
        preset = 0,
        mode = 0,
        uEqLinked = 0,
        powerUser = true,
        channelBands =
          mapOf(
            1 to listOf(1.5f, 1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 1.5f),
            2 to listOf(1.5f, 1.0f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 1.5f),
            3 to listOf(3.5f, 3.0f, 1.5f, 0.0f, -2.0f, -3.5f, -4.5f, -5.0f, -5.0f, -5.0f),
            4 to listOf(-1.0f, -0.5f, 0.5f, 1.5f, 2.5f, 2.5f, 2.0f, 1.0f, 0.5f, 0.0f),
            5 to listOf(0.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 1.5f, 1.0f, 0.5f),
            6 to listOf(0.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 1.5f, 1.0f, 0.5f),
            7 to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            8 to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
          ),
      ),
      EqualizerPreset(
        name = "rock",
        displayName = "Rock",
        preset = 1,
        mode = 1,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(3.5f, 2.5f, 1.0f, -1.0f, -1.5f, -0.5f, 1.5f, 2.5f, 3.5f, 3.5f)),
      ),
      EqualizerPreset(
        name = "jazz",
        displayName = "Jazz",
        preset = 3,
        mode = 1,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(2.5f, 1.5f, 0.5f, 1.0f, -1.0f, -1.0f, 0.0f, 1.0f, 2.0f, 2.5f)),
      ),
      EqualizerPreset(
        name = "classical",
        displayName = "Classical",
        preset = 4,
        mode = 1,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(3.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0.0f, -0.5f, -0.5f, 1.5f, 2.5f)),
      ),
      EqualizerPreset(
        name = "dance",
        displayName = "Dance",
        preset = 5,
        mode = 1,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(4.0f, 3.5f, 1.5f, 0.0f, 0.0f, -1.0f, -0.5f, 1.5f, 3.0f, 3.5f)),
      ),
      EqualizerPreset(
        name = "pop",
        displayName = "Pop",
        preset = 6,
        mode = 1,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(-1.0f, 1.0f, 2.5f, 3.0f, 2.0f, 0.0f, -0.5f, -0.5f, 1.0f, 1.5f)),
      ),
      EqualizerPreset(
        name = "vocal",
        displayName = "Vocal",
        preset = 10,
        mode = 1,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(-2.0f, -1.5f, -0.5f, 1.5f, 3.5f, 3.5f, 2.5f, 1.0f, 0.0f, -1.0f)),
      ),
      EqualizerPreset(
        name = "hiphop",
        displayName = "Hip-Hop",
        preset = 11,
        mode = 1,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(4.5f, 3.5f, 1.5f, 1.0f, -1.0f, -1.0f, 1.0f, 0.5f, 2.0f, 2.5f)),
      ),
      EqualizerPreset(
        name = "bassboost",
        displayName = "Bass Boost",
        preset = 13,
        mode = 1,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(4.5f, 4.0f, 3.0f, 1.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
      ),
      EqualizerPreset(
        name = "loudness",
        displayName = "Loudness",
        preset = 16,
        mode = 1,
        uEqLinked = 1,
        channelBands = bandsForAllChannels(listOf(4.0f, 3.0f, 0.5f, 0.0f, -1.0f, 0.0f, 0.5f, 2.0f, 3.5f, 4.0f)),
      ),
    )

  val defaultCec =
    CecSettings(
      enabled = true,
      arc = true,
      preArcPort = 0,
      latencyMode = "bypass",
      ports =
        listOf(
          CecPortDevice(portIndex = 0, osdName = "axiim Q", isCecDevice = false),
          CecPortDevice(
            portIndex = 2,
            osdName = "SHIELD",
            logicalAddress = 11,
            physicalAddress = "2.0.0.0",
            vendorId = 1099,
            isCecDevice = true,
            powerStatus = 0,
          ),
        ),
    )

  val defaultNowPlaying =
    NowPlayingItem(
      type = "music",
      id = 42,
      title = "Solar Sailer (WiSA 24/96 Master)",
      artist = "Daft Punk",
      album = "TRON: Legacy — Complete Edition",
      image = "/-/music/42/image",
      thumbnail = "/-/music/42/thumbnail",
      description = "Uncompressed 24-bit / 96 kHz Wireless Surround",
      runtime = 162,
    )

  val defaultODataMusic =
    listOf(
      ODataMediaItem(
        id = "42",
        title = "Solar Sailer (WiSA 24/96 Master)",
        artistOrSubtitle = "Daft Punk",
        album = "TRON: Legacy",
        runtimeSeconds = 162,
        category = ODataMediaCategory.MUSIC,
      ),
      ODataMediaItem(
        id = "43",
        title = "Derezzed",
        artistOrSubtitle = "Daft Punk",
        album = "TRON: Legacy",
        runtimeSeconds = 104,
        category = ODataMediaCategory.MUSIC,
      ),
      ODataMediaItem(
        id = "44",
        title = "The Grid (7.1 Surround Check)",
        artistOrSubtitle = "Axiim Acoustic Lab",
        album = "WiSA Reference Disc",
        runtimeSeconds = 215,
        category = ODataMediaCategory.MUSIC,
      ),
      ODataMediaItem(
        id = "45",
        title = "Contact",
        artistOrSubtitle = "Daft Punk",
        album = "Random Access Memories",
        runtimeSeconds = 383,
        category = ODataMediaCategory.MUSIC,
      ),
    )

  val defaultODataVideos =
    listOf(
      ODataMediaItem(
        id = "101",
        title = "Dolby TrueHD 7.1 Atmos Trailer",
        artistOrSubtitle = "1920×1080p • TrueHD 7.1",
        album = "Demo Trailers",
        runtimeSeconds = 94,
        category = ODataMediaCategory.VIDEOS,
      ),
      ODataMediaItem(
        id = "102",
        title = "DTS-HD Master Audio Soundcheck",
        artistOrSubtitle = "3840×2160p • DTS-HD MA",
        album = "Demo Trailers",
        runtimeSeconds = 128,
        category = ODataMediaCategory.VIDEOS,
      ),
    )
}

/** SharedPreferences shared by the repository and [com.axiim.q.MainActivity] (read before the first frame). */
object AppPrefs {
  const val FILE = "axiim_q_prefs"
  const val MATERIAL_YOU_COLORS = "material_you_colors"
}

class AxiimRepository(private val context: Context?) {
  // Last line of defense: an unexpected exception in any background RPC / socket coroutine is
  // logged instead of taking the whole app down (the default handler kills the process).
  private val crashGuard =
    CoroutineExceptionHandler { _, throwable ->
      Log.e(TAG, "Background task failed", throwable)
    }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)
  private val prefs = context?.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE)

  val ssdpClient = SsdpDiscoveryClient(context)
  val rpcClient = AxiimRpcClient(scope)
  val odataClient = ODataMediaClient()
  val ipAudioReceiver = IpAudioReceiver(rpcClient, scope)
  val playToQServer = PlayToQHttpServer(context, scope)

  private val _state = MutableStateFlow(AxiimUiState())
  val state: StateFlow<AxiimUiState> = _state.asStateFlow()

  private var volumeDebounceJob: Job? = null
  private var lastVolumeSentMs = 0L
  private var setupKeepaliveJob: Job? = null
  private var heartbeatJob: Job? = null
  private var connectJob: Job? = null
  private var bannerJob: Job? = null
  private val debouncedJobs = mutableMapOf<String, Job>()

  private companion object {
    const val TAG = "AxiimRepository"
    const val SLIDER_DEBOUNCE_MS = 150L
    const val VOLUME_THROTTLE_MS = 120L
  }

  init {
    loadSavedDevice()
    observeRpcNotifications()
    observeIpAudioAndHttpServer()
    connectToSelectedDevice()
    startHeartbeatLoop()
  }

  private fun loadSavedDevice() {
    val savedIp = prefs?.getString("zopa_ip", null) ?: DefaultLiveSnapshot.defaultDevice.ip
    val savedName = prefs?.getString("zopa_name", null) ?: DefaultLiveSnapshot.defaultDevice.name
    val savedUuid = prefs?.getString("zopa_uuid", null) ?: DefaultLiveSnapshot.defaultDevice.uuid
    val savedAccent = prefs?.getString("zopa_accent", null) ?: DefaultLiveSnapshot.defaultDevice.accentColor
    val savedQuickTilesCsv = prefs?.getString("quick_tiles_csv", null)
    val savedSpeakerPositions = SpeakerLayout.decode(prefs?.getString("speaker_positions", null))
    val enabledTiles =
      if (savedQuickTilesCsv != null) {
        savedQuickTilesCsv
          .split(",")
          .mapNotNull { QuickTileId.fromId(it.trim()) }
          .distinct()
          .ifEmpty { QuickTileId.defaultIds }
      } else {
        QuickTileId.defaultIds
      }
    val device =
      DiscoveredDevice(
        uuid = savedUuid,
        name = savedName,
        ip = savedIp,
        accentColor = savedAccent,
      )
    _state.update {
      it.copy(
        selectedDevice = device,
        devices = listOf(device),
        enabledQuickTiles = enabledTiles,
        speakerPositions = savedSpeakerPositions,
        materialYouColors = prefs?.getBoolean(AppPrefs.MATERIAL_YOU_COLORS, false) ?: false,
        personalize = it.personalize.copy(deviceName = savedName, accentColor = savedAccent),
      )
    }
  }

  private fun saveQuickTiles(tiles: List<QuickTileId>) {
    prefs
      ?.edit()
      ?.putString("quick_tiles_csv", tiles.joinToString(",") { it.id })
      ?.apply()
  }

  fun toggleQuickTileVisibility(tile: QuickTileId) {
    _state.update { current ->
      val currentList = current.enabledQuickTiles
      val updated =
        if (tile in currentList) {
          if (currentList.size > 1) currentList - tile else currentList
        } else {
          QuickTileId.entries.filter { it in currentList || it == tile }
        }
      saveQuickTiles(updated)
      current.copy(enabledQuickTiles = updated)
    }
  }

  fun setSpeakerPosition(speakerId: String, position: Offset) {
    _state.update { current ->
      val updated = current.speakerPositions + (speakerId to SpeakerLayout.clamp(position))
      prefs?.edit()?.putString("speaker_positions", SpeakerLayout.encode(updated))?.apply()
      current.copy(speakerPositions = updated)
    }
  }

  fun resetSpeakerPositions() {
    prefs?.edit()?.remove("speaker_positions")?.apply()
    _state.update { it.copy(speakerPositions = emptyMap()) }
  }

  fun setMaterialYouColors(enabled: Boolean) {
    prefs?.edit()?.putBoolean(AppPrefs.MATERIAL_YOU_COLORS, enabled)?.apply()
    _state.update { it.copy(materialYouColors = enabled) }
  }

  fun resetQuickTilesToDefault() {
    val defaults = QuickTileId.defaultIds
    saveQuickTiles(defaults)
    _state.update { it.copy(enabledQuickTiles = defaults) }
  }

  private fun saveSelectedDevice(device: DiscoveredDevice) {
    prefs
      ?.edit()
      ?.putString("zopa_ip", device.ip)
      ?.putString("zopa_name", device.name)
      ?.putString("zopa_uuid", device.uuid)
      ?.putString("zopa_accent", device.accentColor)
      ?.apply()
  }

  private fun observeRpcNotifications() {
    scope.launch {
      rpcClient.isConnected.collect { connected ->
        _state.update {
          it.copy(
            isLiveTcpConnected = connected,
            isSimulatedPreview = false,
          )
        }
      }
    }
    scope.launch {
      rpcClient.notifications.collect { notification ->
        // A malformed push must not end the collector (and with it every future push update).
        try {
          handlePushNotification(notification)
        } catch (e: Exception) {
          Log.w(TAG, "Ignoring malformed push ${notification.method}", e)
        }
      }
    }
  }

  private fun observeIpAudioAndHttpServer() {
    scope.launch {
      ipAudioReceiver.state.collect { ipState ->
        _state.update { it.copy(ipAudio = ipState) }
      }
    }
    scope.launch {
      playToQServer.state.collect { serverState ->
        _state.update { it.copy(playToQ = serverState) }
      }
    }
  }

  /**
   * Handles all 8 unsolicited server-to-client push notifications documented in `docs/events.md`.
   */
  private fun handlePushNotification(notification: RpcNotification) {
    val params = notification.params
    when (notification.method) {
      "audio.volume_changed" -> {
        val obj = params as? JsonObject ?: return
        val vol = obj["volume"]?.primitiveOrNull?.intOrNull ?: return
        val muted = obj["muted"]?.primitiveOrNull?.booleanOrNull ?: false
        // Per docs/events.md line 47: ignore incoming volume_changed while user is dragging slider
        _state.update { current ->
          if (current.isUserDraggingVolume) {
            current.copy(muted = muted)
          } else {
            current.copy(volume = vol.coerceIn(0, 100), muted = muted)
          }
        }
      }
      "avr.input_changed" -> {
        val obj = params as? JsonObject ?: return
        val updatedInput = AxiimRpcClient.parseInputItem(obj)
        _state.update { current ->
          val updatedList =
            current.inputs.map { inp ->
              if (inp.port == updatedInput.port) {
                updatedInput.copy(active = true)
              } else {
                inp.copy(active = false)
              }
            }
          current.copy(inputs = updatedList)
        }
      }
      "power.power_state" -> {
        val obj = params as? JsonObject ?: return
        val powered = obj["powered"]?.primitiveOrNull?.booleanOrNull ?: return
        _state.update { it.copy(powered = powered) }
      }
      "nowplaying.new_item" -> {
        val item = AxiimRpcClient.parseNowPlaying(params)
        _state.update { it.copy(nowPlaying = item, isMediaPlaying = item != null) }
      }
      "audio.eq_set" -> {
        val arr = params as? JsonArray ?: return
        val presets = AxiimRpcClient.parseEqualizerList(arr)
        if (presets.isNotEmpty()) {
          _state.update { it.copy(eqPresets = presets) }
        }
      }
      "theme.accent-color" -> {
        val color = params.scalarOrNull()?.contentOrNull ?: return
        _state.update {
          it.copy(
            selectedDevice = it.selectedDevice.copy(accentColor = color),
            personalize = it.personalize.copy(accentColor = color),
          )
        }
      }
      "system.device-name" -> {
        val name = params.scalarOrNull()?.contentOrNull ?: return
        _state.update {
          it.copy(
            selectedDevice = it.selectedDevice.copy(name = name),
            personalize = it.personalize.copy(deviceName = name),
          )
        }
      }
      "diagnostic.customer" -> {
        val customer = params.scalarOrNull()?.contentOrNull ?: return
        _state.update { it.copy(customerEmail = customer) }
      }
    }
  }

  /**
   * Connects to the selected Axiim Q on TCP 3000 and executes the initialization sequence
   * documented in `docs/transport.md` lines 18-24 plus full state hydration.
   */
  /**
   * Connects (or reconnects) to the selected device. When [force] is false and a connection attempt
   * is already running, the call is ignored; when true the running attempt is replaced.
   */
  fun connectToSelectedDevice(force: Boolean = true) {
    if (!force && connectJob?.isActive == true) return
    connectJob?.cancel()
    connectJob =
      scope.launch {
        try {
          connectAndHydrate()
        } finally {
          // A superseded attempt must not clear the flag owned by its replacement.
          if (connectJob === coroutineContext[Job]) {
            _state.update { it.copy(isConnecting = false) }
          }
        }
      }
  }

  /** Tapping the Connected pill: drop the current session and reconnect to the selected Q. */
  fun reconnect() {
    connectToSelectedDevice(force = true)
  }

  private suspend fun connectAndHydrate() {
    val device = _state.value.selectedDevice
    _state.update { it.copy(isConnecting = true) }
    val connected = rpcClient.connect(device.ip, device.rpcPort)
    if (connected) {
      _state.update { it.copy(isLiveTcpConnected = true, isSimulatedPreview = false) }
      refreshFullDeviceState()
      return
    }

    // Try SSDP discovery to see if the Axiim Q is on a new IP
    val found = ssdpClient.discoverDevices()
    val match = found.firstOrNull { it.uuid == device.uuid } ?: found.firstOrNull()
    if (match != null && match.ip != device.ip) {
      saveSelectedDevice(match)
      _state.update {
        it.copy(
          selectedDevice = match,
          devices = (it.devices.filterNot { d -> d.uuid == match.uuid } + match),
        )
      }
      val reconnected = rpcClient.connect(match.ip, match.rpcPort)
      _state.update { it.copy(isLiveTcpConnected = reconnected, isSimulatedPreview = false) }
      if (reconnected) {
        refreshFullDeviceState()
      }
    } else {
      _state.update { it.copy(isLiveTcpConnected = false, isSimulatedPreview = false) }
    }
  }

  suspend fun refreshFullDeviceState() {
    if (!rpcClient.isConnected.value) return
    val host = _state.value.selectedDevice.ip

    // Parallel initialization sequence per docs/transport.md
    val name = rpcClient.getDeviceName()
    val accent = rpcClient.getAccentColor()
    val volMute = rpcClient.getVolume()
    val caps = rpcClient.getSystemCapabilities()
    val customer = rpcClient.getSetting("diagnostic.customer").scalarOrNull()?.contentOrNull
    val power = rpcClient.getPowerState()

    val inputs = rpcClient.getInputList()
    val eqs = rpcClient.getEqualizerPresets()
    val speakers = rpcClient.getSpeakers()
    val groups = rpcClient.getGroups()
    val trims = rpcClient.getChannelVolumes()
    val distances = rpcClient.getChannelDistances()
    val nowPlaying = rpcClient.getCurrentNowPlaying()
    val dsp = rpcClient.getAudioDspSettings()
    val video = rpcClient.getVideoTelemetry()
    val cec = rpcClient.getCecSettings()
    val diagHistory = rpcClient.getDiagnosticHistory()
    val fwVersion = odataClient.fetchFirmwareVersion(host)

    val resolvedName = name ?: _state.value.selectedDevice.name
    val resolvedAccent = accent ?: _state.value.selectedDevice.accentColor
    val personalize = rpcClient.getPersonalizeSettings(resolvedName, resolvedAccent)

    _state.update { current ->
      val updatedSelected =
        current.selectedDevice.copy(
          name = resolvedName,
          accentColor = resolvedAccent,
          isOnline = true,
        )
      saveSelectedDevice(updatedSelected)

      current.copy(
        selectedDevice = updatedSelected,
        firmwareVersion = fwVersion ?: current.firmwareVersion,
        powered = power ?: current.powered,
        volume = volMute?.first ?: current.volume,
        muted = volMute?.second ?: current.muted,
        inputs = inputs?.takeIf { it.isNotEmpty() } ?: current.inputs,
        eqPresets = eqs?.takeIf { it.isNotEmpty() } ?: current.eqPresets,
        speakers = speakers?.takeIf { it.isNotEmpty() } ?: current.speakers,
        groups = groups?.takeIf { it.isNotEmpty() } ?: current.groups,
        channelTrims = mergeChannelTrims(current.channelTrims, trims),
        channelDistances = mergeChannelDistances(current.channelDistances, distances),
        nowPlaying = nowPlaying ?: current.nowPlaying,
        dspSettings = dsp ?: current.dspSettings,
        videoTelemetry = video ?: current.videoTelemetry,
        personalize = personalize ?: current.personalize,
        cecSettings = cec ?: current.cecSettings,
        capabilities = caps ?: current.capabilities,
        customerEmail = customer ?: current.customerEmail,
        diagnosticHistory = diagHistory ?: current.diagnosticHistory,
      )
    }
  }

  private fun mergeChannelTrims(
    existing: List<ChannelTrim>,
    incoming: List<ChannelTrim>?,
  ): List<ChannelTrim> {
    if (incoming.isNullOrEmpty()) return existing
    val byChannel = incoming.associateBy { it.channel }
    return SpeakerChannel.entries.map { ch ->
      byChannel[ch] ?: existing.find { it.channel == ch } ?: ChannelTrim(ch, 0)
    }
  }

  private fun mergeChannelDistances(
    existing: List<ChannelDistance>,
    incoming: List<ChannelDistance>?,
  ): List<ChannelDistance> {
    if (incoming.isNullOrEmpty()) return existing
    val byChannel = incoming.associateBy { it.channel }
    return SpeakerChannel.entries.map { ch ->
      byChannel[ch] ?: existing.find { it.channel == ch } ?: ChannelDistance(ch, 0f)
    }
  }

  private fun startHeartbeatLoop() {
    heartbeatJob?.cancel()
    heartbeatJob =
      scope.launch {
        var missedHeartbeats = 0
        while (isActive) {
          if (rpcClient.isConnected.value) {
            // Wake immediately if the connection drops instead of sleeping the full 30 s.
            val dropped = withTimeoutOrNull(30_000L) { rpcClient.isConnected.first { !it } }
            if (dropped != null) continue
            val power = rpcClient.getPowerState()
            if (power != null) {
              missedHeartbeats = 0
              _state.update { it.copy(powered = power) }
            } else if (++missedHeartbeats >= 2 && connectJob?.isActive != true) {
              // Half-open socket (e.g. the Q lost power): force a reconnect.
              missedHeartbeats = 0
              rpcClient.disconnect()
            }
          } else {
            // Auto-connect in background when disconnected
            missedHeartbeats = 0
            delay(6_000L)
            if (!rpcClient.isConnected.value && !_state.value.isDiscoveringSsdp) {
              connectToSelectedDevice(force = false)
            }
          }
        }
      }
  }

  /** Cancels every background job and releases sockets. The repository is unusable afterwards. */
  fun close() {
    ipAudioReceiver.stop()
    playToQServer.stop()
    rpcClient.disconnect()
    scope.cancel()
  }

  private fun showBanner(message: String) {
    bannerJob?.cancel()
    _state.update { it.copy(statusBannerMessage = message) }
    bannerJob =
      scope.launch {
        delay(4_000L)
        _state.update { if (it.statusBannerMessage == message) it.copy(statusBannerMessage = null) else it }
      }
  }

  /** Coalesces rapid slider changes into one RPC per [key] once the value settles. */
  private fun debounced(key: String, block: suspend () -> Unit) {
    synchronized(debouncedJobs) {
      debouncedJobs[key]?.cancel()
      debouncedJobs[key] =
        scope.launch {
          delay(SLIDER_DEBOUNCE_MS)
          block()
        }
    }
  }

  /** The phone's address as seen from the Q: prefer the interface of the live RPC socket. */
  private fun resolvePhoneLanIp(): String? = rpcClient.localAddress ?: PlayToQHttpServer.resolveLocalLanIp()

  // ============================================================================
  // SSDP Discovery & Device Selection (docs/discovery.md & docs/pairing.md)
  // ============================================================================

  fun runSsdpDiscoverySweep() {
    if (_state.value.isDiscoveringSsdp) return
    _state.update { it.copy(isDiscoveringSsdp = true) }
    scope.launch {
      val found =
        try {
          ssdpClient.discoverDevices()
        } catch (e: Exception) {
          _state.update { it.copy(isDiscoveringSsdp = false) }
          throw e
        }
      _state.update { current ->
        val foundByUuid = found.associateBy { it.uuid }
        val updatedKnown =
          current.devices.mapNotNull { existing ->
            val fresh = foundByUuid[existing.uuid]
            if (fresh != null) {
              fresh.copy(userAdded = existing.userAdded, missedDiscoveries = 0, isOnline = true)
            } else {
              val missed = existing.missedDiscoveries + 1
              // Per docs/discovery.md: MaxMissedAttempts = 2 for non-user-added devices,
              // but always keep the currently selected device visible
              if (!existing.userAdded && missed > 2 && existing.uuid != current.selectedDevice.uuid) {
                null
              } else {
                existing.copy(missedDiscoveries = missed)
              }
            }
          }.toMutableList()

        for (newDev in found) {
          if (updatedKnown.none { it.uuid == newDev.uuid }) {
            updatedKnown.add(newDev)
          }
        }

        // If the currently selected device's UUID responded with a new IP/name/accent, update it
        val updatedSelected =
          foundByUuid[current.selectedDevice.uuid] ?: current.selectedDevice
        val ipChanged = updatedSelected.ip != current.selectedDevice.ip
        if (ipChanged) {
          saveSelectedDevice(updatedSelected)
        }

        current.copy(
          isDiscoveringSsdp = false,
          devices = updatedKnown,
          selectedDevice = updatedSelected,
        )
      }
    }
  }

  fun selectDevice(device: DiscoveredDevice) {
    saveSelectedDevice(device)
    _state.update {
      it.copy(
        selectedDevice = device,
        personalize = it.personalize.copy(deviceName = device.name, accentColor = device.accentColor),
      )
    }
    connectToSelectedDevice()
  }

  fun addManualDevice(name: String, ip: String) {
    val cleanIp = ip.trim()
    if (cleanIp.isEmpty()) return
    val cleanName = name.trim().ifEmpty { "axiim Q ($cleanIp)" }
    val newDevice =
      DiscoveredDevice(
        uuid = "USER-${cleanIp.hashCode().toUInt().toString(16).padStart(12, '0').uppercase()}",
        name = cleanName,
        ip = cleanIp,
        accentColor = _state.value.personalize.accentColor,
        userAdded = true,
        isOnline = true,
      )
    _state.update { current ->
      val updatedList = (current.devices.filterNot { it.ip == cleanIp } + newDevice)
      current.copy(devices = updatedList, selectedDevice = newDevice)
    }
    selectDevice(newDevice)
  }

  // ============================================================================
  // Power, Master Volume & Mute, Inputs & Remote Keys
  // ============================================================================

  fun togglePower() {
    val targetPower = !_state.value.powered
    _state.update { it.copy(powered = targetPower, lastRemoteKeySent = RemoteKey.POWER) }
    scope.launch {
      // Per docs/api-reference.md line 78: power is toggled via inject.key_press 116
      rpcClient.injectKeyPress(RemoteKey.POWER.keyCode)
    }
  }

  fun onVolumeDragStart() {
    _state.update { it.copy(isUserDraggingVolume = true) }
  }

  fun onVolumeChange(newVolume: Int) {
    val clamped = newVolume.coerceIn(0, 100)
    if (clamped == _state.value.volume && !_state.value.muted) return
    _state.update { it.copy(volume = clamped, muted = false) }
    // Throttle (not debounce): the Q follows the slider live during a drag, at most every 120 ms,
    // with a trailing send so the final position always lands.
    volumeDebounceJob?.cancel()
    val now = SystemClock.elapsedRealtime()
    val wait = (VOLUME_THROTTLE_MS - (now - lastVolumeSentMs)).coerceAtLeast(0L)
    volumeDebounceJob =
      scope.launch {
        delay(wait)
        lastVolumeSentMs = SystemClock.elapsedRealtime()
        rpcClient.setVolume(_state.value.volume)
      }
  }

  fun onVolumeDragEnd() {
    _state.update { it.copy(isUserDraggingVolume = false) }
    volumeDebounceJob?.cancel()
    scope.launch {
      lastVolumeSentMs = SystemClock.elapsedRealtime()
      rpcClient.setVolume(_state.value.volume)
    }
  }

  fun stepVolumeUp() {
    val next = (_state.value.volume + 1).coerceIn(0, 100)
    _state.update { it.copy(volume = next, muted = false) }
    scope.launch { rpcClient.volumeUp() }
  }

  fun stepVolumeDown() {
    val next = (_state.value.volume - 1).coerceIn(0, 100)
    _state.update { it.copy(volume = next) }
    scope.launch { rpcClient.volumeDown() }
  }

  fun toggleMute() {
    val nextMuted = !_state.value.muted
    _state.update { it.copy(muted = nextMuted) }
    scope.launch { rpcClient.toggleMute() }
  }

  fun selectInput(port: Int) {
    _state.update { current ->
      current.copy(
        inputs = current.inputs.map { it.copy(active = it.port == port) },
      )
    }
    scope.launch {
      rpcClient.setInput(port)
    }
  }

  fun toggleShowDisconnectedInputs() {
    _state.update { it.copy(showDisconnectedInputs = !it.showDisconnectedInputs) }
  }

  fun sendRemoteKey(key: RemoteKey) {
    _state.update { it.copy(lastRemoteKeySent = key) }
    scope.launch {
      if (key == RemoteKey.POWER) {
        _state.update { it.copy(powered = !it.powered) }
      }
      rpcClient.injectKeyPress(key.keyCode)
      if (key.udpStringSymbol != null) {
        rpcClient.sendUdpStringCommand(key.udpStringSymbol)
      }
    }
  }

  // ============================================================================
  // WiSA Speakers, Calibration (Manual Trim & Distance) & 8×10-Band Equalizer
  // ============================================================================

  fun setSpeakerSetupMode(active: Boolean) {
    _state.update { it.copy(isSpeakerSetupModeActive = active) }
    setupKeepaliveJob?.cancel()
    if (active) {
      setupKeepaliveJob =
        scope.launch {
          rpcClient.discoverSpeakers()
          // Per docs/wisa.md line 52: send audio.setup_active every 2000 ms
          while (isActive) {
            rpcClient.sendSetupActiveKeepalive()
            delay(2000L)
          }
        }
    } else {
      stopIdentificationTone()
    }
  }

  fun toggleSpeakerIdentificationTone(speakerId: String) {
    val currentTone = _state.value.activeToneSpeakerId
    if (currentTone == speakerId) {
      stopIdentificationTone()
    } else {
      _state.update { it.copy(activeToneSpeakerId = speakerId) }
      scope.launch {
        rpcClient.playIdentificationTone(speakerId)
      }
    }
  }

  fun stopIdentificationTone() {
    _state.update { it.copy(activeToneSpeakerId = null) }
    scope.launch {
      rpcClient.stopIdentificationTone()
    }
  }

  fun renameSpeaker(speakerId: String, newName: String) {
    val clean = newName.trim()
    if (clean.isEmpty()) return
    _state.update { current ->
      current.copy(
        speakers = current.speakers.map { if (it.id == speakerId) it.copy(name = clean) else it },
      )
    }
    scope.launch {
      rpcClient.setSpeakerName(speakerId, clean)
    }
  }

  fun assignSpeakerLocation(speakerId: String, channel: SpeakerChannel) {
    val groupId = _state.value.groups.firstOrNull()?.id ?: "ebac4547-d82b-445a-a2a8-72cc91737992"
    _state.update { current ->
      current.copy(
        speakers =
          current.speakers.map {
            if (it.id == speakerId) {
              it.copy(loc = channel.upperLoc, name = channel.displayName)
            } else {
              it
            }
          },
      )
    }
    scope.launch {
      rpcClient.setSpeakerLoc(groupId, speakerId, channel.upperLoc)
    }
  }

  fun updateChannelTrim(channel: SpeakerChannel, volumeDb: Int) {
    val clamped = volumeDb.coerceIn(-20, 20)
    val updated =
      _state.value.channelTrims.map {
        if (it.channel == channel) it.copy(volumeDb = clamped) else it
      }
    _state.update { it.copy(channelTrims = updated) }
    debounced("channelTrims") { rpcClient.setChannelVolumes(_state.value.channelTrims) }
  }

  fun toggleLevelCalibrationNoise(channel: SpeakerChannel) {
    val current = _state.value.activeCalibrationNoiseChannel
    if (current == channel) {
      _state.update { it.copy(activeCalibrationNoiseChannel = null) }
      scope.launch { rpcClient.stopLevelCalibrationNoise() }
    } else {
      _state.update { it.copy(activeCalibrationNoiseChannel = channel) }
      scope.launch { rpcClient.playLevelCalibrationNoise(channel, gain = 0) }
    }
  }

  fun updateChannelDistance(channel: SpeakerChannel, distanceFeet: Float) {
    val rounded = ((distanceFeet * 10f).roundToInt() / 10f).coerceIn(0f, 40f)
    val updated =
      _state.value.channelDistances.map {
        if (it.channel == channel) it.copy(distanceFeet = rounded) else it
      }
    _state.update { it.copy(channelDistances = updated) }
    debounced("channelDistances") { rpcClient.setChannelDistances(_state.value.channelDistances) }
  }

  fun selectEqPreset(presetName: String) {
    val target = _state.value.eqPresets.firstOrNull { it.name == presetName } ?: return
    val updatedList =
      _state.value.eqPresets.map { it.copy(active = it.name == presetName) }
    val nextLastActive =
      if (presetName != "disabled") presetName else _state.value.lastActiveEqPresetName
    _state.update {
      it.copy(
        eqPresets = updatedList,
        lastActiveEqPresetName = nextLastActive,
      )
    }
    synchronized(debouncedJobs) { debouncedJobs.remove("equalizer")?.cancel() }
    scope.launch {
      rpcClient.setEqualizer(target.copy(active = true))
    }
  }

  fun toggleEqualizerOnOff() {
    val active = _state.value.activeEqPreset
    if (active.name == "disabled") {
      val restoreName =
        _state.value.lastActiveEqPresetName.takeIf { it != "disabled" } ?: "custom"
      selectEqPreset(restoreName)
    } else {
      selectEqPreset("disabled")
    }
  }

  fun selectEqChannel(channel: SpeakerChannel) {
    _state.update { it.copy(selectedEqChannel = channel) }
  }

  /**
   * Updates a single EQ band gain (-5.0 to +5.0 dB in 0.5 dB steps).
   * If currently on a fixed preset, automatically transitions to "custom" (linked) or "per_speaker".
   */
  fun updateEqBandGain(bandIndexZeroBased: Int, gainDb: Float) {
    val snapped = ((gainDb * 2f).roundToInt() / 2f).coerceIn(-5f, 5f)
    val currentState = _state.value
    val active = currentState.activeEqPreset
    val isPerSpeakerMode = active.name == "per_speaker"
    val targetPresetName = if (isPerSpeakerMode) "per_speaker" else "custom"
    val chIndex = currentState.selectedEqChannel.eqIndex

    var modifiedPreset: EqualizerPreset? = null
    val updatedPresets =
      currentState.eqPresets.map { preset ->
        if (preset.name == targetPresetName) {
          val baseBands =
            if (active.name != targetPresetName) active.channelBands else preset.channelBands
          val newChannelBands =
            if (isPerSpeakerMode) {
              val chList = baseBands[chIndex].orEmpty().toMutableList().apply {
                if (size == 10) this[bandIndexZeroBased] = snapped
              }
              baseBands + (chIndex to chList)
            } else {
              // Linked custom EQ shares bands across all channels
              val chList = (baseBands[1] ?: List(10) { 0f }).toMutableList().apply {
                if (size == 10) this[bandIndexZeroBased] = snapped
              }
              (1..8).associateWith { chList.toList() }
            }
          val updated =
            preset.copy(
              active = true,
              mode = 0,
              preset = 0,
              channelBands = newChannelBands,
            )
          modifiedPreset = updated
          updated
        } else {
          preset.copy(active = false)
        }
      }

    _state.update { it.copy(eqPresets = updatedPresets) }
    if (modifiedPreset != null) {
      debounced("equalizer") { rpcClient.setEqualizer(_state.value.activeEqPreset) }
    }
  }

  fun updateSubwooferLpf(freqHz: Int, qFactor: Float) {
    val clampedFreq = freqHz.coerceIn(40, 200)
    val clampedQ = ((qFactor * 100f).roundToInt() / 100f).coerceIn(0.3f, 2.5f)
    val active = _state.value.activeEqPreset
    val updatedActive = active.copy(lpf1Freq = clampedFreq, lpf2Freq = clampedFreq, lpf1Q = clampedQ)
    _state.update { current ->
      current.copy(
        eqPresets = current.eqPresets.map { if (it.name == active.name) updatedActive else it },
      )
    }
    debounced("equalizer") { rpcClient.setEqualizer(_state.value.activeEqPreset) }
  }

  // ============================================================================
  // Hardware DSP & Personalization Settings (docs/integration-notes.md)
  // ============================================================================

  fun togglePartyMode(enabled: Boolean) {
    _state.update { it.copy(dspSettings = it.dspSettings.copy(partyModeEnable = enabled)) }
    scope.launch { rpcClient.setSetting("audio.party_mode_enable", JsonPrimitive(enabled)) }
  }

  fun toggleSuperStereo(enabled: Boolean) {
    _state.update { it.copy(dspSettings = it.dspSettings.copy(superStereoEnable = enabled)) }
    scope.launch { rpcClient.setSetting("audio.super_stereo_enable", JsonPrimitive(enabled)) }
  }

  fun toggleDtsNeural(enabled: Boolean) {
    _state.update { it.copy(dspSettings = it.dspSettings.copy(dtsNeuralEnable = enabled)) }
    scope.launch { rpcClient.setSetting("audio.dts_neural_enable", JsonPrimitive(enabled)) }
  }

  fun setNeo6Mode(mode: String) {
    _state.update { it.copy(dspSettings = it.dspSettings.copy(neo6Mode = mode)) }
    scope.launch { rpcClient.setSetting("audio.neo6_mode", JsonPrimitive(mode)) }
  }

  /** Single tap on DTS Neo:6 tile toggles Cinema <-> Off. */
  fun toggleNeo6OnOff() {
    val current = _state.value.dspSettings.neo6Mode
    val next = if (current == "disabled") "cinema" else "disabled"
    setNeo6Mode(next)
  }

  /** Tap-and-hold on DTS Neo:6 tile toggles Music <-> Off. */
  fun toggleNeo6Music() {
    val current = _state.value.dspSettings.neo6Mode
    val next = if (current == "music") "disabled" else "music"
    setNeo6Mode(next)
  }

  fun setDolbyDrc(mode: String) {
    _state.update { it.copy(dspSettings = it.dspSettings.copy(dolbyDrc = mode)) }
    scope.launch { rpcClient.setSetting("audio.dolby_drc", JsonPrimitive(mode)) }
  }

  /** Single tap on Night Mode tile toggles On <-> Off. */
  fun toggleNightModeOnOff() {
    val current = _state.value.dspSettings.dolbyDrc
    val next = if (current == "off") "on" else "off"
    setDolbyDrc(next)
  }

  /** Tap-and-hold on Night Mode tile toggles Auto <-> Off. */
  fun toggleNightModeAuto() {
    val current = _state.value.dspSettings.dolbyDrc
    val next = if (current == "auto") "off" else "auto"
    setDolbyDrc(next)
  }

  fun toggleLfeBoostTrueHd(enabled: Boolean) {
    _state.update { it.copy(dspSettings = it.dspSettings.copy(lfeBoostTrueHd = enabled)) }
    scope.launch { rpcClient.setSetting("audio.lfe_boost_truehd", JsonPrimitive(enabled)) }
  }

  fun toggleAutoFormatGain(enabled: Boolean) {
    _state.update { it.copy(dspSettings = it.dspSettings.copy(autoFormatGain = enabled)) }
    scope.launch { rpcClient.setSetting("audio.auto_format_gain", JsonPrimitive(enabled)) }
  }

  fun toggleKlipschLeds(enabled: Boolean) {
    _state.update { it.copy(dspSettings = it.dspSettings.copy(klipschLedsEnabled = enabled)) }
    scope.launch { rpcClient.setSetting("audio.klipsch_leds_enabled", JsonPrimitive(enabled)) }
  }

  fun toggleHdmiAudioOut(enabled: Boolean) {
    _state.update { it.copy(dspSettings = it.dspSettings.copy(hdmiOut = enabled)) }
    scope.launch { rpcClient.setSetting("audio.hdmi_out", JsonPrimitive(enabled)) }
  }

  fun setStartupHighVolume(limit: Int) {
    val clamped = limit.coerceIn(10, 80)
    _state.update { it.copy(dspSettings = it.dspSettings.copy(highVolume = clamped)) }
    debounced("highVolume") {
      rpcClient.setSetting("audio.high_volume", JsonPrimitive(_state.value.dspSettings.highVolume))
    }
  }

  fun toggleFrontBlueLed(enabled: Boolean) {
    _state.update { it.copy(personalize = it.personalize.copy(blueLed = enabled)) }
    scope.launch { rpcClient.setSetting("personalize.blueled", JsonPrimitive(enabled)) }
  }

  fun toggleMenuWrap(enabled: Boolean) {
    _state.update { it.copy(personalize = it.personalize.copy(menuWrap = enabled)) }
    scope.launch { rpcClient.setSetting("personalize.menuwrap", JsonPrimitive(enabled)) }
  }

  fun updateDeviceName(name: String) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    _state.update {
      it.copy(
        selectedDevice = it.selectedDevice.copy(name = clean),
        personalize = it.personalize.copy(deviceName = clean),
      )
    }
    saveSelectedDevice(_state.value.selectedDevice)
    scope.launch { rpcClient.setSetting("system.device-name", JsonPrimitive(clean)) }
  }

  fun updateAccentColor(hexColor: String) {
    _state.update {
      it.copy(
        selectedDevice = it.selectedDevice.copy(accentColor = hexColor),
        personalize = it.personalize.copy(accentColor = hexColor),
      )
    }
    saveSelectedDevice(_state.value.selectedDevice)
    scope.launch { rpcClient.setSetting("theme.accent-color", JsonPrimitive(hexColor)) }
  }

  fun toggleCecEnabled(enabled: Boolean) {
    _state.update { it.copy(cecSettings = it.cecSettings.copy(enabled = enabled)) }
    scope.launch { rpcClient.setSetting("cec.enable", JsonPrimitive(enabled)) }
  }

  fun toggleCecArc(enabled: Boolean) {
    _state.update { it.copy(cecSettings = it.cecSettings.copy(arc = enabled)) }
    scope.launch { rpcClient.setSetting("cec.arc", JsonPrimitive(enabled)) }
  }

  fun setAvrLatencyMode(mode: String) {
    _state.update { it.copy(cecSettings = it.cecSettings.copy(latencyMode = mode)) }
    scope.launch { rpcClient.setSetting("avr.latency_mode", JsonPrimitive(mode)) }
  }

  fun enableBluetoothPairing() {
    _state.update { it.copy(bluetoothDiscoverableActive = true) }
    showBanner("Axiim Q is now discoverable for Bluetooth pairing")
    scope.launch {
      rpcClient.enableBluetoothDiscovery()
      delay(30_000L)
      _state.update { it.copy(bluetoothDiscoverableActive = false) }
    }
  }

  // ============================================================================
  // Media Streaming: Listen on Phone (RTP), Play to Q (HTTP) & OData Library
  // ============================================================================

  fun toggleIpAudioListenOnPhone() {
    if (_state.value.ipAudio.isStreaming) {
      ipAudioReceiver.stop()
    } else {
      val localIp = resolvePhoneLanIp()
      if (localIp == null) {
        showBanner("Couldn't find this phone's Wi-Fi address")
        return
      }
      if (!ipAudioReceiver.start(localIp, isSimulatedMode = _state.value.isSimulatedPreview)) {
        showBanner("Couldn't open an audio port on this phone")
      }
    }
  }

  fun startPlayToQFromUri(uri: Uri, fileName: String, mimeType: String) {
    val mediaType = if (mimeType.startsWith("video")) "video" else "music"
    // Keep read access to the picked document for the whole stream, even if the activity is recreated.
    context?.let { ctx ->
      runCatching {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }
    scope.launch {
      val localIp = resolvePhoneLanIp()
      if (localIp == null) {
        showBanner("Couldn't find this phone's Wi-Fi address")
        return@launch
      }
      val streamUrl =
        try {
          playToQServer.startServingUri(uri, fileName, mimeType, mediaType, localIp)
        } catch (e: Exception) {
          Log.w(TAG, "Play to Q server failed to start", e)
          showBanner("Couldn't start streaming: ${e.message ?: "unknown error"}")
          return@launch
        }
      _state.update {
        it.copy(
          isMediaPlaying = true,
          nowPlaying =
            NowPlayingItem(
              type = mediaType,
              id = 999,
              title = fileName,
              artist = "Streaming from Phone",
              album = streamUrl,
              runtime = 0,
            ),
        )
      }
      rpcClient.playRemoteStream(mediaType, streamUrl)
    }
  }

  fun startPlayToQFromUrl(url: String, mediaType: String = "music") {
    val cleanUrl = url.trim()
    if (cleanUrl.isEmpty()) return
    _state.update {
      it.copy(
        isMediaPlaying = true,
        nowPlaying =
          NowPlayingItem(
            type = mediaType,
            id = 998,
            title = cleanUrl.substringAfterLast('/').ifEmpty { "Network Stream" },
            artist = "HTTP Stream → Axiim Q",
            album = cleanUrl,
            runtime = 0,
          ),
      )
    }
    scope.launch {
      rpcClient.playRemoteStream(mediaType, cleanUrl)
    }
  }

  fun stopPlayToQ() {
    val mediaType = _state.value.playToQ.mediaType.ifEmpty { "music" }
    playToQServer.stop()
    _state.update { it.copy(isMediaPlaying = false) }
    scope.launch {
      rpcClient.playRemoteStream(mediaType, "")
    }
  }

  fun toggleMediaPlayPause() {
    _state.update { it.copy(isMediaPlaying = !it.isMediaPlaying) }
    scope.launch { rpcClient.togglePlayPause() }
  }

  fun stopMediaPlayer() {
    _state.update { it.copy(isMediaPlaying = false) }
    scope.launch { rpcClient.pauseOrStopPlayer() }
  }

  fun nextMediaTrack() {
    scope.launch { rpcClient.nextTrack() }
  }

  fun previousMediaTrack() {
    scope.launch { rpcClient.previousTrack() }
  }

  fun selectODataCategory(category: ODataMediaCategory) {
    _state.update { it.copy(odataCategory = category, isLoadingOData = true) }
    scope.launch {
      val host = _state.value.selectedDevice.ip
      val fetched =
        if (_state.value.isLiveTcpConnected) {
          odataClient.fetchMediaItems(host, category)
        } else {
          emptyList()
        }
      val items =
        fetched.ifEmpty {
          when (category) {
            ODataMediaCategory.VIDEOS, ODataMediaCategory.MOVIES -> DefaultLiveSnapshot.defaultODataVideos
            else -> DefaultLiveSnapshot.defaultODataMusic.map { it.copy(category = category) }
          }
        }
      _state.update { it.copy(odataItems = items, isLoadingOData = false) }
    }
  }

  fun playODataItem(item: ODataMediaItem, enqueue: Boolean = false) {
    _state.update {
      it.copy(
        isMediaPlaying = true,
        nowPlaying =
          NowPlayingItem(
            type = item.category.playerType,
            id = item.id.toIntOrNull() ?: 1,
            title = item.title,
            artist = item.artistOrSubtitle,
            album = item.album,
            runtime = item.runtimeSeconds,
          ),
      )
    }
    scope.launch {
      rpcClient.playODataMedia(item.category.playerType, item.id, enqueue)
    }
  }

  fun submitDiagnostic(customerEmail: String, description: String) {
    if (description.isBlank()) return
    _state.update { it.copy(customerEmail = customerEmail) }
    scope.launch {
      val report =
        rpcClient.sendDiagnosticReport(customerEmail, description)
          ?: DiagnosticReport(
            code = "DIAG-${(1000..9999).random()}",
            description = description.trim(),
          )
      _state.update { it.copy(diagnosticHistory = listOf(report) + it.diagnosticHistory) }
      showBanner("Submitted diagnostic report ${report.code}")
    }
  }

  fun dismissStatusBanner() {
    bannerJob?.cancel()
    _state.update { it.copy(statusBannerMessage = null) }
  }
}
