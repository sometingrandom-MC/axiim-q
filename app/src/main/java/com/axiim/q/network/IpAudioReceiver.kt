package com.axiim.q.network

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class IpAudioState(
  val isStreaming: Boolean = false,
  val localUdpPort: Int = 0,
  val jitterBufferMs: Int = 80,
  val packetsReceived: Long = 0L,
  val leftRmsLevel: Float = 0f,
  val rightRmsLevel: Float = 0f,
  val spectrumBands: List<Float> = List(16) { 0f },
)

/**
 * "Listen on Phone" UDP RTP Audio Receiver (`audio.ip_out_enable`, `audio.ip_out_keepalive`, `audio.ip_out_disable`).
 *
 * Implements the RTP PCM protocol documented in `docs/media-streaming.md` section 3 AND fixes the
 * original app's zero-buffer dropout bug (`docs/integration-notes.md` lines 97-100):
 * - Binds an ephemeral local UDP port (`0.0.0.0:0`).
 * - Strips the 12-byte RTP header (`RTP_HEADER_SIZE = 12`) from each incoming UDP datagram.
 * - Pre-fills an **80 ms jitter buffer** before starting playback into a 48,000 Hz, 16-bit signed
 *   little-endian stereo `AudioTrack`, preventing Wi-Fi micro-jitter clicks/dropouts.
 * - Sends `audio.ip_out_keepalive` every 28,000 ms.
 * - Computes real-time stereo L/R RMS VU levels and 16-band spectrum bars for the UI.
 */
class IpAudioReceiver(
  private val rpcClient: AxiimRpcClient,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  companion object {
    const val SAMPLE_RATE = 48000
    const val RTP_HEADER_SIZE = 12
    const val KEEPALIVE_INTERVAL_MS = 28_000L
    const val JITTER_BUFFER_TARGET_MS = 80
    // Once buffered audio exceeds the target by this much, drop the excess to catch back up with the TV
    const val LATENCY_SLACK_MS = 40
    const val TRACK_BUFFER_MS = 40
    const val MAX_EXTRA_DELAY_MS = 500
    // 48,000 Hz * 2 channels * 2 bytes/sample = 192 bytes per ms
    const val BYTES_PER_MS = 192
  }

  private val _state = MutableStateFlow(IpAudioState())
  val state: StateFlow<IpAudioState> = _state.asStateFlow()

  /**
   * Lip-sync calibration: extra delay on top of the jitter buffer, for when the TV picture lags
   * the phone (e.g. the Q's own A/V sync delay). Can change mid-stream.
   */
  @Volatile var extraDelayMs: Int = 0
    set(value) {
      field = value.coerceIn(0, MAX_EXTRA_DELAY_MS)
    }

  private var udpSocket: DatagramSocket? = null
  private var receiveJob: Job? = null
  private var playJob: Job? = null
  private var keepaliveJob: Job? = null

  /** Starts receiving; returns false if the local UDP socket could not be opened. */
  fun start(localIp: String, isSimulatedMode: Boolean): Boolean {
    stop()
    if (isSimulatedMode) {
      startSimulatedVisualizer()
      return true
    }

    // Sized to hold the jitter buffer plus the largest calibration delay
    val pcmQueue = LinkedBlockingQueue<ByteArray>(4096)
    val socket =
      try {
        DatagramSocket(null).apply {
          reuseAddress = true
          bind(InetSocketAddress(0))
          soTimeout = 500
        }
      } catch (_: Exception) {
        return false
      }
    udpSocket = socket
    val boundPort = socket.localPort

    _state.value =
      IpAudioState(
        isStreaming = true,
        localUdpPort = boundPort,
        jitterBufferMs = JITTER_BUFFER_TARGET_MS,
      )

    // 1. Enable IP Audio Output on the Q and start 28s keepalive timer
    keepaliveJob =
      scope.launch {
        rpcClient.enableIpAudioOut(localIp, boundPort)
        while (isActive) {
          delay(KEEPALIVE_INTERVAL_MS)
          rpcClient.sendIpAudioKeepalive()
        }
      }

    // 2. UDP RTP packet receiver thread
    receiveJob =
      scope.launch(Dispatchers.IO) {
        val buf = ByteArray(4096)
        var packetCount = 0L
        try {
          while (isActive) {
            val packet = DatagramPacket(buf, buf.size)
            try {
              socket.receive(packet)
              val len = packet.length
              if (len > RTP_HEADER_SIZE) {
                val pcmLen = len - RTP_HEADER_SIZE
                val pcmBytes = ByteArray(pcmLen)
                System.arraycopy(packet.data, packet.offset + RTP_HEADER_SIZE, pcmBytes, 0, pcmLen)
                if (!pcmQueue.offer(pcmBytes)) {
                  // Drop oldest frame if jitter queue overflows
                  pcmQueue.poll()
                  pcmQueue.offer(pcmBytes)
                }
                packetCount++
                updateVuMetersFromPcm(pcmBytes, packetCount)
              }
            } catch (_: SocketTimeoutException) {
              // Decay VU meters when idle
              _state.value =
                _state.value.copy(
                  leftRmsLevel = _state.value.leftRmsLevel * 0.6f,
                  rightRmsLevel = _state.value.rightRmsLevel * 0.6f,
                  spectrumBands = _state.value.spectrumBands.map { it * 0.6f },
                )
            }
          }
        } catch (_: Exception) {
          // Socket closed
        }
      }

    // 3. Jitter-buffered AudioTrack playback consumer
    playJob =
      scope.launch(Dispatchers.IO) {
        val minBufSize =
          AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
          ).coerceAtLeast(TRACK_BUFFER_MS * BYTES_PER_MS)

        val audioTrack =
          runCatching {
            AudioTrack.Builder()
              .setAudioAttributes(
                AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                  .build(),
              )
              .setAudioFormat(
                AudioFormat.Builder()
                  .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                  .setSampleRate(SAMPLE_RATE)
                  .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                  .build(),
              )
              .setBufferSizeInBytes(minBufSize)
              .setTransferMode(AudioTrack.MODE_STREAM)
              .build()
          }.getOrNull()

        try {
          if (audioTrack == null) return@launch
          audioTrack.play()
          var prebuffering = true
          val prebufferQueue = mutableListOf<ByteArray>()
          var prebufferedBytes = 0
          var appliedExtraDelayMs = extraDelayMs
          var targetBytes = (JITTER_BUFFER_TARGET_MS + appliedExtraDelayMs) * BYTES_PER_MS
          var framesWritten = 0L
          // Android may grow the buffer a lot (e.g. ~360 ms on Bluetooth) and by default waits for it
          // to fill before playing; start as soon as the jitter buffer is in instead.
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioTrack.startThresholdInFrames = targetBytes / 4 }
          }

          while (isActive) {
            val chunk = pcmQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
            if (prebuffering) {
              prebufferQueue.add(chunk)
              prebufferedBytes += chunk.size
              if (prebufferedBytes >= targetBytes) {
                for (buffered in prebufferQueue) {
                  framesWritten += audioTrack.write(buffered, 0, buffered.size).coerceAtLeast(0) / 4
                }
                prebufferQueue.clear()
                prebuffering = false
              }
              continue
            }
            val wantedExtraDelayMs = extraDelayMs
            if (wantedExtraDelayMs != appliedExtraDelayMs) {
              // A longer delay is added at once with silence; a shorter one by the drop below
              if (wantedExtraDelayMs > appliedExtraDelayMs) {
                val silence = ByteArray((wantedExtraDelayMs - appliedExtraDelayMs) * BYTES_PER_MS)
                framesWritten += audioTrack.write(silence, 0, silence.size).coerceAtLeast(0) / 4
              }
              appliedExtraDelayMs = wantedExtraDelayMs
              targetBytes = (JITTER_BUFFER_TARGET_MS + appliedExtraDelayMs) * BYTES_PER_MS
            }
            // Latency creeps up after every Wi-Fi stall (the burst that follows is queued, never
            // skipped) and with clock drift between the Q and the phone. Keep the total delay
            // (queued + inside the AudioTrack) near the target so the audio stays in sync with the TV.
            // Only the queue is trimmed: the chunk in hand is always played, so playback can't stall.
            // Nothing is trimmed until the track is actually playing, or its fill-up looks like backlog.
            val playedFrames = audioTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (playedFrames > 0) {
              val trackBytes = ((framesWritten - playedFrames) and 0xFFFFFFFFL) * 4
              var queuedBytes = trackBytes + pcmQueue.size * chunk.size
              if (queuedBytes > targetBytes + LATENCY_SLACK_MS * BYTES_PER_MS) {
                while (queuedBytes > targetBytes && pcmQueue.poll() != null) {
                  queuedBytes -= chunk.size
                }
              }
            }
            framesWritten += audioTrack.write(chunk, 0, chunk.size).coerceAtLeast(0) / 4
          }
        } catch (_: Exception) {
          // AudioTrack failure (e.g. audio focus/device change); stop playback quietly
        } finally {
          runCatching {
            audioTrack?.stop()
            audioTrack?.release()
          }
        }
      }
    return true
  }

  private fun startSimulatedVisualizer() {
    _state.value =
      IpAudioState(
        isStreaming = true,
        localUdpPort = 54321,
        jitterBufferMs = JITTER_BUFFER_TARGET_MS,
      )
    receiveJob =
      scope.launch {
        var tick = 0L
        while (isActive) {
          delay(65)
          tick++
          val phase = tick * 0.22
          val left = (0.45f + 0.35f * sin(phase).toFloat() + 0.12f * sin(phase * 2.7).toFloat()).coerceIn(0.08f, 0.96f)
          val right = (0.42f + 0.38f * sin(phase + 0.6).toFloat() + 0.14f * sin(phase * 1.9).toFloat()).coerceIn(0.08f, 0.96f)
          val bands =
            List(16) { idx ->
              val f = (idx + 1) * 0.35
              (0.30f + 0.45f * kotlin.math.abs(sin(phase * 0.9 + f)).toFloat()).coerceIn(0.1f, 0.98f)
            }
          _state.value =
            _state.value.copy(
              packetsReceived = tick * 6,
              leftRmsLevel = left,
              rightRmsLevel = right,
              spectrumBands = bands,
            )
        }
      }
  }

  @Volatile private var lastVuUpdateMs = 0L

  private fun updateVuMetersFromPcm(pcm: ByteArray, packetsCount: Long) {
    if (pcm.size < 4) return
    // RTP packets arrive hundreds of times per second; cap UI state updates at ~30 fps.
    val now = SystemClock.elapsedRealtime()
    if (now - lastVuUpdateMs < 33) return
    lastVuUpdateMs = now
    var sumLeftSq = 0.0
    var sumRightSq = 0.0
    var frames = 0
    var i = 0
    while (i + 3 < pcm.size) {
      val leftSample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort() / 32768.0
      val rightSample = ((pcm[i + 3].toInt() shl 8) or (pcm[i + 2].toInt() and 0xFF)).toShort() / 32768.0
      sumLeftSq += leftSample * leftSample
      sumRightSq += rightSample * rightSample
      frames++
      i += 16 // Subsample every 4th stereo frame for low CPU overhead
    }
    if (frames == 0) return
    val leftRms = (sqrt(sumLeftSq / frames) * 2.6).toFloat().coerceIn(0f, 1f)
    val rightRms = (sqrt(sumRightSq / frames) * 2.6).toFloat().coerceIn(0f, 1f)
    val bands =
      List(16) { idx ->
        val mix = if (idx % 2 == 0) leftRms else rightRms
        (mix * (0.55f + 0.45f * kotlin.math.abs(sin(packetsCount * 0.15 + idx)).toFloat())).coerceIn(0.05f, 1f)
      }
    _state.value =
      _state.value.copy(
        packetsReceived = packetsCount,
        leftRmsLevel = leftRms,
        rightRmsLevel = rightRms,
        spectrumBands = bands,
      )
  }

  fun stop() {
    val wasStreaming = _state.value.isStreaming
    keepaliveJob?.cancel()
    receiveJob?.cancel()
    playJob?.cancel()
    keepaliveJob = null
    receiveJob = null
    playJob = null
    runCatching { udpSocket?.close() }
    udpSocket = null
    _state.value = IpAudioState(isStreaming = false)
    if (wasStreaming) {
      scope.launch {
        rpcClient.disableIpAudioOut()
      }
    }
  }
}
