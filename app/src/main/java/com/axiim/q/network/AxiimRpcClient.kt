package com.axiim.q.network

import com.axiim.q.model.AudioDspSettings
import com.axiim.q.model.AvrInput
import com.axiim.q.model.CecPortDevice
import com.axiim.q.model.CecSettings
import com.axiim.q.model.ChannelDistance
import com.axiim.q.model.ChannelTrim
import com.axiim.q.model.DiagnosticReport
import com.axiim.q.model.EqualizerPreset
import com.axiim.q.model.NowPlayingItem
import com.axiim.q.model.PersonalizeSettings
import com.axiim.q.model.SpeakerChannel
import com.axiim.q.model.SystemCapabilities
import com.axiim.q.model.VideoSignalTelemetry
import com.axiim.q.model.WisaGroup
import com.axiim.q.model.WisaSpeaker
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class RpcNotification(
  val method: String,
  val params: JsonElement?,
)

/**
 * Persistent TCP 3000 JSON-RPC 2.0 Control & Push Event Bus Client for the Axiim Q UHD,
 * plus secondary UDP 6210 remote command datagram sender.
 */
class AxiimRpcClient(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  companion object {
    const val DEFAULT_TCP_PORT = 3000
    const val DEFAULT_UDP_PORT = 6210
    const val CONNECT_TIMEOUT_MS = 3000
    const val RPC_TIMEOUT_MS = 4500L

    val json = Json {
      ignoreUnknownKeys = true
      isLenient = true
      encodeDefaults = true
    }

    fun formatPresetDisplayName(rawName: String): String =
      when (rawName.lowercase()) {
        "disabled" -> "Flat (Off)"
        "custom" -> "Custom 10-Band"
        "per_speaker" -> "Per-Speaker 8×10"
        "rock" -> "Rock"
        "jazz" -> "Jazz"
        "classical" -> "Classical"
        "dance" -> "Dance"
        "pop" -> "Pop"
        "vocal" -> "Vocal"
        "hiphop" -> "Hip-Hop"
        "bassboost" -> "Bass Boost"
        "loudness" -> "Loudness"
        else -> rawName.replaceFirstChar { it.uppercase() }
      }

    fun parseInputItem(obj: JsonObject): AvrInput {
      val port = obj["port"]?.primitiveOrNull?.intOrNull ?: 0
      val defaultLabel = if (port == 0) "axiim Q" else "HDMI $port"
      return AvrInput(
        port = port,
        label = obj["label"]?.primitiveOrNull?.contentOrNull?.takeIf { it.isNotBlank() } ?: defaultLabel,
        active = obj["active"]?.primitiveOrNull?.booleanOrNull ?: false,
        connected = obj["connected"]?.primitiveOrNull?.booleanOrNull ?: (port == 0),
        isCecDevice = obj["is_cec_device"]?.primitiveOrNull?.booleanOrNull ?: false,
        logo = obj["logo"]?.primitiveOrNull?.contentOrNull ?: "",
        mfg = obj["mfg"]?.primitiveOrNull?.contentOrNull ?: "",
        model = obj["model"]?.primitiveOrNull?.contentOrNull ?: "",
        logoUrl = obj["logourl"]?.primitiveOrNull?.contentOrNull,
        latency = obj["latency"]?.primitiveOrNull?.contentOrNull ?: "normal",
      )
    }

    fun parseEqualizerList(array: JsonArray): List<EqualizerPreset> {
      return array.mapNotNull { elem ->
        val obj = elem as? JsonObject ?: return@mapNotNull null
        val name = obj["name"]?.primitiveOrNull?.contentOrNull ?: return@mapNotNull null
        val preset = obj["preset"]?.primitiveOrNull?.intOrNull ?: 0
        val mode = obj["mode"]?.primitiveOrNull?.intOrNull ?: 0
        val uEqLinked = obj["uEQ_linked"]?.primitiveOrNull?.intOrNull ?: 0
        val active = obj["active"]?.primitiveOrNull?.booleanOrNull ?: false
        val powerUser = obj["poweruser"]?.primitiveOrNull?.booleanOrNull ?: (name == "per_speaker")
        val gainCompression = obj["gaincompression"]?.primitiveOrNull?.intOrNull ?: 0

        val channelBands = mutableMapOf<Int, List<Float>>()
        for (ch in 1..8) {
          val bands = MutableList(10) { 0f }
          for (b in 1..10) {
            val key = "uEQC${ch}_$b"
            bands[b - 1] = (obj[key]?.primitiveOrNull?.floatOrNull ?: 0f).coerceIn(-5f, 5f)
          }
          channelBands[ch] = bands
        }

        EqualizerPreset(
          name = name,
          displayName = formatPresetDisplayName(name),
          preset = preset,
          mode = mode,
          uEqLinked = uEqLinked,
          active = active,
          powerUser = powerUser,
          channelBands = channelBands,
          gainCompression = gainCompression,
          lpf1Type = obj["uEQC3_1_type"]?.primitiveOrNull?.contentOrNull ?: "lpf",
          lpf1Freq = obj["uEQC3_1_freq"]?.primitiveOrNull?.intOrNull ?: 80,
          lpf1Q = obj["uEQC3_1_Q"]?.primitiveOrNull?.floatOrNull ?: 0.54f,
          lpf2Type = obj["uEQC3_2_type"]?.primitiveOrNull?.contentOrNull ?: "lpf",
          lpf2Freq = obj["uEQC3_2_freq"]?.primitiveOrNull?.intOrNull ?: 80,
          lpf2Q = obj["uEQC3_2_Q"]?.primitiveOrNull?.floatOrNull ?: 1.31f,
        )
      }
    }

    fun parseNowPlaying(elem: JsonElement?): NowPlayingItem? {
      if (elem == null || elem is JsonNull) return null
      val obj = elem as? JsonObject ?: return null
      val title = obj["title"]?.primitiveOrNull?.contentOrNull ?: return null
      if (title.isBlank()) return null
      return NowPlayingItem(
        type = obj["type"]?.primitiveOrNull?.contentOrNull ?: "music",
        id = obj["id"]?.primitiveOrNull?.intOrNull ?: 0,
        title = title,
        artist = obj["artist"]?.primitiveOrNull?.contentOrNull ?: "",
        album = obj["album"]?.primitiveOrNull?.contentOrNull ?: "",
        image = obj["image"]?.primitiveOrNull?.contentOrNull ?: "",
        thumbnail = obj["thumbnail"]?.primitiveOrNull?.contentOrNull ?: "",
        description = obj["description"]?.primitiveOrNull?.contentOrNull ?: "",
        runtime = obj["runtime"]?.primitiveOrNull?.intOrNull ?: 0,
      )
    }
  }

  private val writeMutex = Mutex()
  private val connectMutex = Mutex()
  private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonElement?>>()
  private val frameExtractor = JsonFrameExtractor()

  private var socket: Socket? = null
  private var outputStream: OutputStream? = null
  private var readerJob: Job? = null
  private var currentHost: String? = null
  private var currentPort: Int = DEFAULT_TCP_PORT

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  private val _notifications = MutableSharedFlow<RpcNotification>(extraBufferCapacity = 64)
  val notifications: SharedFlow<RpcNotification> = _notifications.asSharedFlow()

  private val _rpcLog = MutableStateFlow<List<String>>(emptyList())
  val rpcLog: StateFlow<List<String>> = _rpcLog.asStateFlow()

  suspend fun connect(host: String, port: Int = DEFAULT_TCP_PORT): Boolean =
    withContext(Dispatchers.IO) {
      // Serialize connection attempts: the heartbeat, the Connected pill and device switching can
      // all request a (re)connect at the same time.
      connectMutex.withLock {
        disconnect()
        currentHost = host
        currentPort = port
        val newSocket = Socket()
        try {
          newSocket.tcpNoDelay = true
          newSocket.keepAlive = true
          newSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
          socket = newSocket
          outputStream = newSocket.getOutputStream()
          frameExtractor.reset()
          _isConnected.value = true
          appendLog("Connected TCP $host:$port")
          startReadLoop(newSocket)
          true
        } catch (e: Exception) {
          runCatching { newSocket.close() }
          appendLog("Connect failed ($host:$port): ${e.message}")
          disconnect()
          false
        }
      }
    }

  /** IPv4 address of this phone on the interface that routes to the connected Q, if any. */
  val localAddress: String?
    get() = socket?.localAddress?.takeIf { !it.isAnyLocalAddress }?.hostAddress

  fun disconnect() {
    _isConnected.value = false
    readerJob?.cancel()
    readerJob = null
    runCatching { outputStream?.close() }
    runCatching { socket?.close() }
    outputStream = null
    socket = null
    failPendingRequests()
    frameExtractor.reset()
  }

  private fun failPendingRequests() {
    val pending = pendingRequests.values.toList()
    pendingRequests.clear()
    pending.forEach { it.complete(null) }
  }

  private fun startReadLoop(ownSocket: Socket) {
    readerJob?.cancel()
    readerJob =
      scope.launch(Dispatchers.IO) {
        // InputStreamReader keeps multi-byte UTF-8 sequences intact across TCP read boundaries.
        val reader = InputStreamReader(ownSocket.getInputStream(), Charsets.UTF_8)
        val buf = CharArray(8192)
        try {
          while (isActive) {
            val readCount = reader.read(buf)
            if (readCount < 0) break
            if (readCount == 0) continue
            val frames = frameExtractor.feed(String(buf, 0, readCount))
            for (frame in frames) {
              handleIncomingFrame(frame)
            }
          }
        } catch (_: Exception) {
          // Socket closed or dropped
        } finally {
          // Only tear down if this reader still owns the active socket; a stale reader from a
          // previous connection must not mark a freshly established connection as disconnected.
          if (socket === ownSocket) {
            appendLog("Connection closed by ${currentHost ?: "device"}")
            disconnect()
          }
        }
      }
  }

  private fun handleIncomingFrame(rawJson: String) {
    try {
      val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return
      val id = root["id"]?.primitiveOrNull?.contentOrNull
      val method = root["method"]?.primitiveOrNull?.contentOrNull

      if (id != null && method == null) {
        // RPC Response or Error
        val deferred = pendingRequests.remove(id)
        if (root.containsKey("error")) {
          appendLog("RPC Error [$id]: ${root["error"]}")
          deferred?.complete(null)
        } else {
          deferred?.complete(root["result"])
        }
      } else if (method != null) {
        // Unsolicited Server-to-Client Push Notification
        val params = root["params"]
        appendLog("Push ← $method")
        _notifications.tryEmit(RpcNotification(method = method, params = params))
      }
    } catch (_: Exception) {
      // Ignore malformed frame
    }
  }

  /**
   * Sends a JSON-RPC 2.0 request over TCP 3000 and awaits the correlated response.
   *
   * Includes a strict guard preventing `settings.get` with empty `params: []`, which
   * per `docs/transport.md` crashes the Axiim Q's JSON-RPC daemon.
   */
  suspend fun call(method: String, params: JsonElement = buildJsonArray {}): JsonElement? =
    withContext(Dispatchers.IO) {
      if (method == "settings.get") {
        val validKey =
          (params as? JsonArray)?.firstOrNull()?.primitiveOrNull?.contentOrNull?.isNotBlank() == true
        if (!validKey) {
          appendLog("Blocked settings.get with empty params[] (crashes Q daemon)")
          return@withContext null
        }
      }

      if (!_isConnected.value) return@withContext null
      val reqId = UUID.randomUUID().toString()
      val deferred = CompletableDeferred<JsonElement?>()
      pendingRequests[reqId] = deferred

      val envelope =
        buildJsonObject {
          put("jsonrpc", "2.0")
          put("method", method)
          put("params", params)
          put("id", reqId)
        }
      val wireString = json.encodeToString(JsonObject.serializer(), envelope) + "\r\n"

      val sent =
        writeMutex.withLock {
          try {
            val out = outputStream ?: return@withLock false
            out.write(wireString.toByteArray(Charsets.UTF_8))
            out.flush()
            appendLog("RPC → $method")
            true
          } catch (_: Exception) {
            false
          }
        }

      if (!sent) {
        pendingRequests.remove(reqId)
        disconnect()
        return@withContext null
      }

      val result = withTimeoutOrNull(RPC_TIMEOUT_MS) { deferred.await() }
      pendingRequests.remove(reqId)
      result
    }

  /**
   * Sends a fire-and-forget JSON datagram to UDP port 6210 with a 3-byte UTF-8 BOM (`EF BB BF`)
   * as documented in `docs/transport.md` (`RemoteService.SendCommand<StringCommand>`).
   */
  suspend fun sendUdpStringCommand(value: String) =
    withContext(Dispatchers.IO) {
      val host = currentHost ?: return@withContext
      runCatching {
        val payloadObj =
          buildJsonObject {
            put("value", value)
            put("pin", "")
            put("type", 2)
          }
        val jsonBytes = json.encodeToString(JsonObject.serializer(), payloadObj).toByteArray(Charsets.UTF_8)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val datagramBytes = bom + jsonBytes
        DatagramSocket().use { udpSocket ->
          val packet =
            DatagramPacket(
              datagramBytes,
              datagramBytes.size,
              InetAddress.getByName(host),
              DEFAULT_UDP_PORT,
            )
          udpSocket.send(packet)
        }
        appendLog("UDP 6210 → StringCommand(\"$value\")")
      }
    }

  // ============================================================================
  // High-level JSON-RPC 2.0 API wrappers (All 41 methods from api-reference.md)
  // ============================================================================

  suspend fun getPowerState(): Boolean? {
    val res = call("power.get_power_state") as? JsonObject ?: return null
    return res["powered"]?.primitiveOrNull?.booleanOrNull
  }

  suspend fun getDeviceName(): String? {
    return call("personalize.get_device_name")?.primitiveOrNull?.contentOrNull
  }

  suspend fun getAccentColor(): String? {
    return call("personalize.get_accent_color")?.primitiveOrNull?.contentOrNull
  }

  suspend fun getVolume(): Pair<Int, Boolean>? {
    val res = call("audio.volume_get") as? JsonObject ?: return null
    val vol = res["volume"]?.primitiveOrNull?.intOrNull ?: return null
    val muted = res["muted"]?.primitiveOrNull?.booleanOrNull ?: false
    return vol.coerceIn(0, 100) to muted
  }

  suspend fun setVolume(volume: Int): Pair<Int, Boolean>? {
    val clamped = volume.coerceIn(0, 100)
    val params =
      buildJsonObject {
        put(
          "options",
          buildJsonObject {
            put("volume", clamped)
          },
        )
      }
    val res = call("audio.volume_set", params) as? JsonObject ?: return null
    val newVol = res["volume"]?.primitiveOrNull?.intOrNull ?: clamped
    val muted = res["muted"]?.primitiveOrNull?.booleanOrNull ?: false
    return newVol to muted
  }

  suspend fun volumeUp() {
    call("audio.volume_up")
  }

  suspend fun volumeDown() {
    call("audio.volume_down")
  }

  suspend fun toggleMute() {
    call("audio.volume_mute_toggle")
  }

  suspend fun getInputList(): List<AvrInput>? {
    val arr = call("avr.input_list") as? JsonArray ?: return null
    return arr.mapNotNull { elem -> (elem as? JsonObject)?.let(::parseInputItem) }
  }

  suspend fun setInput(port: Int) {
    call(
      "avr.input_set",
      buildJsonObject {
        put("input_port", port)
      },
    )
  }

  suspend fun injectKeyPress(keyCode: Int) {
    call(
      "inject.key_press",
      buildJsonObject {
        put("key", keyCode)
      },
    )
  }

  suspend fun getSetting(key: String): JsonElement? {
    if (key.isBlank()) return null
    return call(
      "settings.get",
      buildJsonArray {
        add(JsonPrimitive(key))
      },
    )
  }

  suspend fun setSetting(key: String, value: JsonElement) {
    if (key.isBlank()) return
    call(
      "settings.set",
      buildJsonArray {
        add(JsonPrimitive(key))
        add(value)
      },
    )
  }

  suspend fun getSystemCapabilities(): SystemCapabilities? {
    val obj = getSetting("system.capabilities") as? JsonObject ?: return null
    return SystemCapabilities(
      uhdtv = obj["uhdtv"]?.primitiveOrNull?.booleanOrNull ?: false,
      calibration = obj["calibration"]?.primitiveOrNull?.booleanOrNull ?: false,
      cec = obj["cec"]?.primitiveOrNull?.booleanOrNull ?: true,
      listen = obj["listen"]?.primitiveOrNull?.booleanOrNull ?: true,
      updates = obj["updates"]?.primitiveOrNull?.booleanOrNull ?: true,
      speakerSetup = obj["speaker_setup"]?.primitiveOrNull?.booleanOrNull ?: true,
      powerUser = obj["poweruser"]?.primitiveOrNull?.booleanOrNull ?: true,
      menuOnUp = obj["menu_on_up"]?.primitiveOrNull?.booleanOrNull ?: true,
      sii = obj["sii"]?.primitiveOrNull?.booleanOrNull ?: true,
    )
  }

  suspend fun getAudioDspSettings(): AudioDspSettings? {
    val obj = getSetting("audio") as? JsonObject ?: return null
    return AudioDspSettings(
      partyModeEnable = obj["party_mode_enable"]?.primitiveOrNull?.booleanOrNull ?: false,
      superStereoEnable = obj["super_stereo_enable"]?.primitiveOrNull?.booleanOrNull ?: false,
      dtsNeuralEnable = obj["dts_neural_enable"]?.primitiveOrNull?.booleanOrNull ?: true,
      neo6Mode = obj["neo6_mode"]?.primitiveOrNull?.contentOrNull ?: "disabled",
      dolbyDrc = obj["dolby_drc"]?.primitiveOrNull?.contentOrNull ?: "off",
      lfeBoostTrueHd = obj["lfe_boost_truehd"]?.primitiveOrNull?.booleanOrNull ?: true,
      autoFormatGain = obj["auto_format_gain"]?.primitiveOrNull?.booleanOrNull ?: true,
      klipschLedsEnabled = obj["klipsch_leds_enabled"]?.primitiveOrNull?.booleanOrNull ?: true,
      hdmiOut = obj["hdmi_out"]?.primitiveOrNull?.booleanOrNull ?: false,
      highVolume = obj["high_volume"]?.primitiveOrNull?.intOrNull ?: 40,
      sampleRate = obj["sample_rate"]?.primitiveOrNull?.intOrNull ?: 48000,
    )
  }

  suspend fun getVideoTelemetry(): VideoSignalTelemetry? {
    val obj = getSetting("video") as? JsonObject ?: return null
    val mode = obj["mode"] as? JsonObject
    return VideoSignalTelemetry(
      width = mode?.get("width")?.primitiveOrNull?.intOrNull ?: 1920,
      height = mode?.get("height")?.primitiveOrNull?.intOrNull ?: 1080,
      refresh = mode?.get("refresh")?.primitiveOrNull?.intOrNull ?: 59,
      interlaced = mode?.get("interlaced")?.primitiveOrNull?.booleanOrNull ?: false,
      stereoType = mode?.get("stereo_type")?.primitiveOrNull?.contentOrNull ?: "none",
      clampEnable = mode?.get("clamp_enable")?.primitiveOrNull?.booleanOrNull ?: true,
      pixelFormat = mode?.get("pixel_format")?.primitiveOrNull?.contentOrNull ?: "RGB",
      colorSpace = mode?.get("color_space")?.primitiveOrNull?.contentOrNull ?: "RGB",
      pixelDepth = mode?.get("pixel_depth")?.primitiveOrNull?.intOrNull ?: 24,
      autoRefresh = obj["auto_refresh"]?.primitiveOrNull?.booleanOrNull ?: true,
      auto3d = obj["auto3d"]?.primitiveOrNull?.booleanOrNull ?: false,
    )
  }

  suspend fun getPersonalizeSettings(
    currentName: String,
    currentAccent: String,
  ): PersonalizeSettings? {
    val pers = getSetting("personalize") as? JsonObject
    val sys = getSetting("system") as? JsonObject
    if (pers == null && sys == null) return null
    return PersonalizeSettings(
      blueLed = pers?.get("blueled")?.primitiveOrNull?.booleanOrNull ?: false,
      menuWrap = pers?.get("menuwrap")?.primitiveOrNull?.booleanOrNull ?: true,
      deviceName = sys?.get("device-name")?.primitiveOrNull?.contentOrNull ?: currentName,
      accentColor = currentAccent,
      menuOnUp = sys?.get("menu_on_up")?.primitiveOrNull?.booleanOrNull ?: true,
      powerUser = sys?.get("poweruser")?.primitiveOrNull?.booleanOrNull ?: true,
      timezone = sys?.get("timezone")?.primitiveOrNull?.contentOrNull ?: "America/New_York",
    )
  }

  suspend fun getCecSettings(): CecSettings? {
    val cec = getSetting("cec") as? JsonObject ?: return null
    val avr = getSetting("avr") as? JsonObject
    val portsArray = cec["ports"] as? JsonArray
    val ports =
      portsArray?.mapIndexedNotNull { idx, elem ->
        val pObj = elem as? JsonObject ?: return@mapIndexedNotNull null
        val isCec = pObj["is_cec_device"]?.primitiveOrNull?.booleanOrNull ?: false
        val osd = pObj["osd_name"]?.primitiveOrNull?.contentOrNull ?: if (idx == 0) "axiim Q" else ""
        val physArr = pObj["physical_address"] as? JsonArray
        val physStr = physArr?.joinToString(".") { it.primitiveOrNull?.contentOrNull.orEmpty() } ?: ""
        CecPortDevice(
          portIndex = idx,
          osdName = osd,
          logicalAddress = pObj["logical_address"]?.primitiveOrNull?.intOrNull ?: -1,
          physicalAddress = physStr,
          vendorId = pObj["vendor_id"]?.primitiveOrNull?.intOrNull ?: 0,
          isCecDevice = isCec,
          powerStatus = pObj["power_status"]?.primitiveOrNull?.intOrNull ?: 0,
        )
      } ?: emptyList()

    return CecSettings(
      enabled = cec["enable"]?.primitiveOrNull?.booleanOrNull ?: true,
      arc = cec["arc"]?.primitiveOrNull?.booleanOrNull ?: true,
      preArcPort = cec["pre_arc_port"]?.primitiveOrNull?.intOrNull ?: 0,
      latencyMode = avr?.get("latency_mode")?.primitiveOrNull?.contentOrNull ?: "bypass",
      ports = ports,
    )
  }

  suspend fun getSpeakers(): List<WisaSpeaker>? {
    val arr = call("audio.get_speakers") as? JsonArray ?: return null
    return arr.mapNotNull { elem ->
      val obj = elem as? JsonObject ?: return@mapNotNull null
      WisaSpeaker(
        id = obj["id"]?.primitiveOrNull?.contentOrNull ?: return@mapNotNull null,
        name = obj["name"]?.primitiveOrNull?.contentOrNull ?: "Speaker",
        desc = obj["desc"]?.primitiveOrNull?.contentOrNull ?: "WiSA Speaker",
        connected = obj["connected"]?.primitiveOrNull?.booleanOrNull ?: true,
        type = obj["type"]?.primitiveOrNull?.contentOrNull ?: "surround",
        defaultLoc = obj["default_loc"]?.primitiveOrNull?.contentOrNull ?: "",
        groupName = obj["group_name"]?.primitiveOrNull?.contentOrNull ?: "default",
        loc = obj["loc"]?.primitiveOrNull?.contentOrNull ?: "FL",
      )
    }
  }

  suspend fun getGroups(): List<WisaGroup>? {
    val arr = call("audio.get_groups") as? JsonArray ?: return null
    return arr.mapNotNull { elem ->
      val obj = elem as? JsonObject ?: return@mapNotNull null
      WisaGroup(
        id = obj["id"]?.primitiveOrNull?.contentOrNull ?: return@mapNotNull null,
        name = obj["name"]?.primitiveOrNull?.contentOrNull ?: "default",
        enabled = obj["enabled"]?.primitiveOrNull?.booleanOrNull ?: true,
        defaultGroup = obj["default_group"]?.primitiveOrNull?.booleanOrNull ?: false,
      )
    }
  }

  suspend fun discoverSpeakers() {
    call("audio.discover")
  }

  suspend fun sendSetupActiveKeepalive() {
    call("audio.setup_active")
  }

  suspend fun setSpeakerName(speakerId: String, name: String) {
    call(
      "audio.set_speaker_name",
      buildJsonArray {
        add(JsonPrimitive(speakerId))
        add(JsonPrimitive(name))
      },
    )
  }

  suspend fun setSpeakerLoc(groupId: String, speakerId: String, upperLoc: String) {
    call(
      "audio.set_speaker_loc",
      buildJsonArray {
        add(JsonPrimitive(groupId))
        add(JsonPrimitive(speakerId))
        add(JsonPrimitive(upperLoc.uppercase()))
      },
    )
  }

  suspend fun playIdentificationTone(speakerId: String) {
    call(
      "audio.play_tone",
      buildJsonArray {
        add(JsonPrimitive(speakerId))
      },
    )
  }

  suspend fun stopIdentificationTone() {
    call("audio.stop_tone")
  }

  suspend fun getChannelVolumes(): List<ChannelTrim>? {
    val arr = call("audio.get_channel_volume") as? JsonArray ?: return null
    return arr.mapNotNull { elem ->
      val obj = elem as? JsonObject ?: return@mapNotNull null
      val loc = obj["loc"]?.primitiveOrNull?.contentOrNull ?: return@mapNotNull null
      val channel = SpeakerChannel.fromLoc(loc) ?: return@mapNotNull null
      val vol = obj["volume"]?.primitiveOrNull?.doubleOrNull?.roundToInt() ?: 0
      ChannelTrim(channel = channel, volumeDb = vol.coerceIn(-20, 20))
    }
  }

  suspend fun setChannelVolumes(trims: List<ChannelTrim>) {
    val params =
      buildJsonObject {
        put(
          "volumes",
          buildJsonArray {
            for (trim in trims) {
              add(
                buildJsonObject {
                  put("loc", trim.channel.lowerLoc)
                  put("volume", trim.volumeDb.coerceIn(-20, 20))
                },
              )
            }
          },
        )
      }
    call("audio.set_channel_volume", params)
  }

  suspend fun playLevelCalibrationNoise(channel: SpeakerChannel, gain: Int = 0) {
    call(
      "audio.play_level_calibration",
      buildJsonObject {
        put("channel", channel.lowerLoc)
        put("gain", gain)
      },
    )
  }

  suspend fun stopLevelCalibrationNoise() {
    call("audio.stop_level_calibration")
  }

  suspend fun getChannelDistances(): List<ChannelDistance>? {
    val arr = call("audio.get_channel_distance") as? JsonArray ?: return null
    return arr.mapNotNull { elem ->
      val obj = elem as? JsonObject ?: return@mapNotNull null
      val loc = obj["loc"]?.primitiveOrNull?.contentOrNull ?: return@mapNotNull null
      val channel = SpeakerChannel.fromLoc(loc) ?: return@mapNotNull null
      val dist = obj["distance"]?.primitiveOrNull?.floatOrNull ?: 0f
      val rounded = ((dist * 10f).roundToInt() / 10f).coerceIn(0f, 40f)
      ChannelDistance(channel = channel, distanceFeet = rounded)
    }
  }

  suspend fun setChannelDistances(distances: List<ChannelDistance>) {
    val params =
      buildJsonObject {
        put(
          "distances",
          buildJsonArray {
            for (item in distances) {
              val rounded = ((item.distanceFeet * 10f).roundToInt() / 10.0)
              add(
                buildJsonObject {
                  put("loc", item.channel.lowerLoc)
                  put("distance", rounded)
                },
              )
            }
          },
        )
      }
    call("audio.set_channel_distance", params)
  }

  suspend fun getEqualizerPresets(): List<EqualizerPreset>? {
    val arr = call("audio.eq_get") as? JsonArray ?: return null
    return parseEqualizerList(arr)
  }

  suspend fun setEqualizer(preset: EqualizerPreset) {
    val eqObj =
      buildJsonObject {
        put("name", preset.name)
        put("preset", preset.preset)
        put("mode", preset.mode)
        put("uEQ_linked", preset.uEqLinked)
        put("gaincompression", preset.gainCompression)
        put("active", true)
        put("uEQC3_1_type", preset.lpf1Type)
        put("uEQC3_1_freq", preset.lpf1Freq)
        put("uEQC3_1_Q", preset.lpf1Q)
        put("uEQC3_2_type", preset.lpf2Type)
        put("uEQC3_2_freq", preset.lpf2Freq)
        put("uEQC3_2_Q", preset.lpf2Q)
        for (ch in 1..8) {
          val gains = preset.channelBands[ch] ?: List(10) { 0f }
          for (b in 1..10) {
            val g = ((gains.getOrElse(b - 1) { 0f } * 2f).roundToInt() / 2.0)
            put("uEQC${ch}_$b", g)
          }
        }
      }
    call(
      "audio.eq_set",
      buildJsonObject {
        put("eq", eqObj)
      },
    )
  }

  suspend fun getCurrentNowPlaying(): NowPlayingItem? {
    val res = call("nowplaying.get_current_item")
    return parseNowPlaying(res)
  }

  suspend fun nextTrack() {
    call("nowplaying.next_item")
  }

  suspend fun previousTrack() {
    call("nowplaying.previous_item")
  }

  suspend fun togglePlayPause() {
    call("player.playpause")
  }

  suspend fun pauseOrStopPlayer() {
    call("player.pause")
  }

  suspend fun playODataMedia(type: String, id: String, enqueue: Boolean = false) {
    call(
      "player.cdata_src",
      buildJsonArray {
        add(JsonPrimitive(type))
        add(JsonPrimitive(id))
        add(JsonPrimitive(enqueue))
      },
    )
  }

  suspend fun playRemoteStream(type: String, url: String) {
    call(
      "player.remote_src",
      buildJsonArray {
        add(JsonPrimitive(type))
        add(JsonPrimitive(url))
      },
    )
  }

  suspend fun enableIpAudioOut(clientIp: String, clientUdpPort: Int) {
    call(
      "audio.ip_out_enable",
      buildJsonObject {
        put("ip", clientIp)
        put("port", clientUdpPort)
      },
    )
  }

  suspend fun sendIpAudioKeepalive() {
    call("audio.ip_out_keepalive")
  }

  suspend fun disableIpAudioOut() {
    call("audio.ip_out_disable")
  }

  suspend fun enableBluetoothDiscovery() {
    call(
      "bluetooth.discoverable",
      buildJsonArray {
        add(JsonPrimitive(true))
      },
    )
  }

  suspend fun getDiagnosticHistory(): List<DiagnosticReport>? {
    val arr = call("diagnostic.history") as? JsonArray ?: return null
    return arr.mapNotNull { elem ->
      val obj = elem as? JsonObject ?: return@mapNotNull null
      DiagnosticReport(
        code = obj["code"]?.primitiveOrNull?.contentOrNull ?: "DIAG",
        description = obj["description"]?.primitiveOrNull?.contentOrNull ?: "",
      )
    }
  }

  suspend fun sendDiagnosticReport(customerEmail: String, description: String): DiagnosticReport? {
    if (customerEmail.isNotBlank()) {
      setSetting("diagnostic.customer", JsonPrimitive(customerEmail))
    }
    val res =
      call(
        "diagnostic.send",
        buildJsonObject {
          put("description", description)
        },
      ) as? JsonObject ?: return null
    return DiagnosticReport(
      code = res["code"]?.primitiveOrNull?.contentOrNull ?: "DIAG-${(1000..9999).random()}",
      description = res["description"]?.primitiveOrNull?.contentOrNull ?: description,
    )
  }

  private fun appendLog(entry: String) {
    _rpcLog.update { current -> (listOf(entry) + current).take(40) }
  }
}
