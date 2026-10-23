package com.axiim.q.network

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayToQServerState(
  val isServing: Boolean = false,
  val streamUrl: String = "",
  val mediaType: String = "music",
  val fileName: String = "",
  val mimeType: String = "audio/mpeg",
  val bytesServed: Long = 0L,
)

/**
 * Range-capable embedded HTTP/1.1 media streaming server on TCP port 9999 for "Play to Q" (`player.remote_src`).
 *
 * Per `docs/media-streaming.md` section 1 and `docs/integration-notes.md` lines 101-104:
 * - Fixes the legacy app's `File.ReadAllBytes` OOM crash by streaming directly from Android's
 *   `ContentResolver` with a 64 KB buffer and supporting HTTP `Range: bytes=start-end` (`206 Partial Content`)
 *   so the Q can seek smoothly through large audio and video files.
 */
class PlayToQHttpServer(
  private val context: Context?,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  companion object {
    const val DEFAULT_PORT = 9999

    fun resolveLocalLanIp(): String {
      return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        for (intf in interfaces) {
          if (!intf.isUp || intf.isLoopback) continue
          for (addr in intf.inetAddresses.toList()) {
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
              val ip = addr.hostAddress ?: continue
              if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                return ip
              }
            }
          }
        }
        "192.168.1.225"
      } catch (_: Exception) {
        "192.168.1.225"
      }
    }
  }

  private val _state = MutableStateFlow(PlayToQServerState())
  val state: StateFlow<PlayToQServerState> = _state.asStateFlow()

  private var serverSocket: ServerSocket? = null
  private var acceptJob: Job? = null

  fun startServingUri(
    uri: Uri,
    displayName: String,
    mimeType: String,
    mediaType: String,
  ): String {
    stop()
    val localIp = resolveLocalLanIp()
    val socket =
      runCatching { ServerSocket(DEFAULT_PORT) }
        .getOrElse { ServerSocket(0) }
    serverSocket = socket
    val boundPort = socket.localPort
    val url = "http://$localIp:$boundPort/"

    _state.value =
      PlayToQServerState(
        isServing = true,
        streamUrl = url,
        mediaType = mediaType,
        fileName = displayName,
        mimeType = mimeType,
      )

    acceptJob =
      scope.launch(Dispatchers.IO) {
        try {
          while (isActive) {
            val client = socket.accept()
            launch(Dispatchers.IO) {
              handleHttpClient(client, uri, mimeType)
            }
          }
        } catch (_: Exception) {
          // Server socket closed
        }
      }

    return url
  }

  private fun handleHttpClient(client: Socket, uri: Uri, mimeType: String) {
    client.use { sock ->
      val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
      val requestLine = reader.readLine() ?: return
      if (!requestLine.startsWith("GET") && !requestLine.startsWith("HEAD")) return

      var rangeHeader: String? = null
      while (true) {
        val line = reader.readLine()
        if (line.isNullOrEmpty()) break
        if (line.startsWith("Range:", ignoreCase = true)) {
          rangeHeader = line.substringAfter(":").trim()
        }
      }

      val resolver = context?.contentResolver ?: return
      val pfd = resolver.openFileDescriptor(uri, "r")
      val totalLength = pfd?.statSize?.takeIf { it > 0 } ?: -1L
      pfd?.close()

      var rangeStart = 0L
      var rangeEnd = if (totalLength > 0) totalLength - 1 else -1L
      var isPartial = false

      if (rangeHeader != null && rangeHeader.startsWith("bytes=") && totalLength > 0) {
        val spec = rangeHeader.removePrefix("bytes=").substringBefore(",")
        val startStr = spec.substringBefore("-").trim()
        val endStr = spec.substringAfter("-", "").trim()
        rangeStart = startStr.toLongOrNull()?.coerceIn(0L, totalLength - 1) ?: 0L
        if (endStr.isNotEmpty()) {
          rangeEnd = endStr.toLongOrNull()?.coerceIn(rangeStart, totalLength - 1) ?: (totalLength - 1)
        }
        isPartial = true
      }

      val contentLength = if (totalLength > 0) (rangeEnd - rangeStart + 1) else -1L
      val out = sock.getOutputStream()

      val headers = buildString {
        if (isPartial) {
          append("HTTP/1.1 206 Partial Content\r\n")
          append("Content-Range: bytes $rangeStart-$rangeEnd/$totalLength\r\n")
        } else {
          append("HTTP/1.1 200 OK\r\n")
        }
        append("Content-Type: $mimeType\r\n")
        append("Accept-Ranges: bytes\r\n")
        if (contentLength >= 0) {
          append("Content-Length: $contentLength\r\n")
        }
        append("Connection: close\r\n\r\n")
      }
      out.write(headers.toByteArray(Charsets.UTF_8))

      if (requestLine.startsWith("HEAD")) {
        out.flush()
        return
      }

      resolver.openInputStream(uri)?.use { input ->
        if (rangeStart > 0) {
          input.skip(rangeStart)
        }
        val buf = ByteArray(64 * 1024)
        var remaining = if (contentLength >= 0) contentLength else Long.MAX_VALUE
        while (remaining > 0) {
          val toRead = minOf(buf.size.toLong(), remaining).toInt()
          val read = input.read(buf, 0, toRead)
          if (read <= 0) break
          out.write(buf, 0, read)
          remaining -= read
          _state.value = _state.value.copy(bytesServed = _state.value.bytesServed + read)
        }
        out.flush()
      }
    }
  }

  fun stop() {
    acceptJob?.cancel()
    acceptJob = null
    runCatching { serverSocket?.close() }
    serverSocket = null
    _state.value = PlayToQServerState(isServing = false)
  }
}
