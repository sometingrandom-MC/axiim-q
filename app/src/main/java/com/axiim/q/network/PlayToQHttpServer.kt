package com.axiim.q.network

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * - Every client connection is fully isolated: the Q routinely probes, seeks and drops connections
 *   mid-transfer, so socket and content-provider errors are contained per request and never escape
 *   into the shared coroutine scope (which previously crashed the app).
 */
class PlayToQHttpServer(
  private val context: Context?,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  companion object {
    const val DEFAULT_PORT = 9999
    private const val BUFFER_SIZE = 64 * 1024
    private const val STATE_UPDATE_INTERVAL_MS = 500L
    private const val CLIENT_READ_TIMEOUT_MS = 15_000

    /**
     * Best-effort LAN IPv4 lookup, preferring Wi-Fi / Ethernet interfaces over cellular (`rmnet*`)
     * interfaces, which frequently hold private `10.x` carrier-NAT addresses the Q cannot reach.
     */
    fun resolveLocalLanIp(): String? {
      return try {
        val candidates =
          NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { intf ->
              intf.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .mapNotNull { addr -> addr.hostAddress?.let { intf.name.orEmpty() to it } }
            }
        val preferred =
          candidates.firstOrNull { (name, _) -> name.startsWith("wlan") || name.startsWith("eth") }
            ?: candidates.firstOrNull { (name, _) -> !name.startsWith("rmnet") && !name.startsWith("ccmni") }
            ?: candidates.firstOrNull()
        preferred?.second
      } catch (_: Exception) {
        null
      }
    }

    /** Parses a single `Range: bytes=` spec into an inclusive byte range, or null if unsatisfiable. */
    fun parseRange(header: String, totalLength: Long): LongRange? {
      if (totalLength <= 0) return null
      val spec = header.trim().removePrefix("bytes=").substringBefore(",").trim()
      val startStr = spec.substringBefore("-").trim()
      val endStr = spec.substringAfter("-", "").trim()
      return if (startStr.isEmpty()) {
        // Suffix range: last N bytes
        val suffix = endStr.toLongOrNull() ?: return null
        if (suffix <= 0) return null
        (totalLength - suffix).coerceAtLeast(0L)..(totalLength - 1)
      } else {
        val start = startStr.toLongOrNull() ?: return null
        if (start < 0 || start >= totalLength) return null
        val end = endStr.toLongOrNull()?.coerceAtMost(totalLength - 1) ?: (totalLength - 1)
        if (end < start) return null
        start..end
      }
    }
  }

  private val _state = MutableStateFlow(PlayToQServerState())
  val state: StateFlow<PlayToQServerState> = _state.asStateFlow()

  private val lock = Any()
  private var serverSocket: ServerSocket? = null
  private var acceptJob: Job? = null
  private val bytesServed = AtomicLong(0L)
  @Volatile private var lastStateUpdateMs = 0L

  /**
   * Binds the server and starts accepting connections. Must be called off the main thread.
   * Returns the stream URL the Q should fetch, or throws if no socket could be bound.
   */
  fun startServingUri(
    uri: Uri,
    displayName: String,
    mimeType: String,
    mediaType: String,
    localIp: String,
  ): String {
    stop()
    val socket = bindServerSocket()
    val boundPort = socket.localPort
    val url = "http://$localIp:$boundPort/${Uri.encode(displayName.ifBlank { "stream" })}"

    bytesServed.set(0L)
    _state.value =
      PlayToQServerState(
        isServing = true,
        streamUrl = url,
        mediaType = mediaType,
        fileName = displayName,
        mimeType = mimeType,
      )

    val job =
      scope.launch(Dispatchers.IO) {
        while (isActive && !socket.isClosed) {
          val client =
            try {
              socket.accept()
            } catch (_: Exception) {
              break // Server socket closed
            }
          // Each request runs in its own coroutine; failures are swallowed so one dropped
          // connection never cancels the accept loop or crashes the process.
          launch(Dispatchers.IO) {
            try {
              handleHttpClient(client, uri, mimeType)
            } catch (_: Throwable) {
              // Client disconnected, provider revoked access, etc.
            } finally {
              runCatching { client.close() }
            }
          }
        }
      }

    synchronized(lock) {
      serverSocket = socket
      acceptJob = job
    }
    return url
  }

  private fun bindServerSocket(): ServerSocket {
    fun tryBind(port: Int): ServerSocket? =
      runCatching {
        ServerSocket().apply {
          reuseAddress = true
          bind(InetSocketAddress(port))
        }
      }.getOrNull()
    return tryBind(DEFAULT_PORT) ?: tryBind(0) ?: throw IllegalStateException("Unable to open a local streaming port")
  }

  private fun queryContentLength(uri: Uri): Long {
    val resolver = context?.contentResolver ?: return -1L
    runCatching {
      resolver.openFileDescriptor(uri, "r")?.use { pfd ->
        if (pfd.statSize > 0) return pfd.statSize
      }
    }
    runCatching {
      resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) {
          val size = cursor.getLong(idx)
          if (size > 0) return size
        }
      }
    }
    return -1L
  }

  private fun readHeaderLine(input: InputStream): String? {
    val sb = StringBuilder()
    while (true) {
      val b = input.read()
      if (b == -1) return if (sb.isEmpty()) null else sb.toString()
      if (b == '\n'.code) return sb.toString().trimEnd('\r')
      if (sb.length < 8192) sb.append(b.toChar())
    }
  }

  private fun writeSimpleResponse(out: OutputStream, status: String, extraHeaders: String = "") {
    out.write(
      ("HTTP/1.1 $status\r\n" + extraHeaders + "Content-Length: 0\r\nConnection: close\r\n\r\n")
        .toByteArray(Charsets.US_ASCII),
    )
    out.flush()
  }

  private fun handleHttpClient(client: Socket, uri: Uri, mimeType: String) {
    client.soTimeout = CLIENT_READ_TIMEOUT_MS
    client.tcpNoDelay = true
    val input = BufferedInputStream(client.getInputStream())
    val out = client.getOutputStream()

    val requestLine = readHeaderLine(input) ?: return
    val method = requestLine.substringBefore(' ').uppercase()
    if (method != "GET" && method != "HEAD") {
      writeSimpleResponse(out, "405 Method Not Allowed", "Allow: GET, HEAD\r\n")
      return
    }

    var rangeHeader: String? = null
    while (true) {
      val line = readHeaderLine(input) ?: break
      if (line.isEmpty()) break
      if (line.startsWith("Range:", ignoreCase = true)) {
        rangeHeader = line.substringAfter(":").trim()
      }
    }

    val resolver = context?.contentResolver
    if (resolver == null) {
      writeSimpleResponse(out, "503 Service Unavailable")
      return
    }

    val totalLength = queryContentLength(uri)
    var range: LongRange? = null
    if (rangeHeader != null && rangeHeader.startsWith("bytes=", ignoreCase = true) && totalLength > 0) {
      range = parseRange(rangeHeader, totalLength)
      if (range == null) {
        writeSimpleResponse(out, "416 Range Not Satisfiable", "Content-Range: bytes */$totalLength\r\n")
        return
      }
    }

    val rangeStart = range?.first ?: 0L
    val contentLength =
      when {
        range != null -> range.last - range.first + 1
        totalLength > 0 -> totalLength
        else -> -1L
      }

    val stream =
      try {
        resolver.openInputStream(uri)
      } catch (_: Exception) {
        null
      }
    if (stream == null) {
      writeSimpleResponse(out, "404 Not Found")
      return
    }

    stream.use { media ->
      val headers = buildString {
        if (range != null) {
          append("HTTP/1.1 206 Partial Content\r\n")
          append("Content-Range: bytes ${range.first}-${range.last}/$totalLength\r\n")
        } else {
          append("HTTP/1.1 200 OK\r\n")
        }
        append("Content-Type: $mimeType\r\n")
        if (totalLength > 0) append("Accept-Ranges: bytes\r\n")
        if (contentLength >= 0) append("Content-Length: $contentLength\r\n")
        append("Connection: close\r\n\r\n")
      }
      out.write(headers.toByteArray(Charsets.US_ASCII))

      if (method == "HEAD") {
        out.flush()
        return
      }

      // InputStream.skip may skip fewer bytes than requested; loop until we reach the offset.
      var toSkip = rangeStart
      while (toSkip > 0) {
        val skipped = media.skip(toSkip)
        if (skipped <= 0) {
          if (media.read() == -1) return
          toSkip -= 1
        } else {
          toSkip -= skipped
        }
      }

      val buf = ByteArray(BUFFER_SIZE)
      var remaining = if (contentLength >= 0) contentLength else Long.MAX_VALUE
      while (remaining > 0) {
        val toRead = minOf(buf.size.toLong(), remaining).toInt()
        val read = media.read(buf, 0, toRead)
        if (read <= 0) break
        out.write(buf, 0, read)
        remaining -= read
        recordBytesServed(read)
      }
      out.flush()
      publishBytesServed()
    }
  }

  private fun recordBytesServed(count: Int) {
    bytesServed.addAndGet(count.toLong())
    val now = SystemClock.elapsedRealtime()
    // Throttle StateFlow updates: emitting on every 64 KB chunk recomposed the whole UI dozens of times a second.
    if (now - lastStateUpdateMs >= STATE_UPDATE_INTERVAL_MS) {
      lastStateUpdateMs = now
      publishBytesServed()
    }
  }

  private fun publishBytesServed() {
    val served = bytesServed.get()
    _state.update { if (it.isServing) it.copy(bytesServed = served) else it }
  }

  fun stop() {
    val (socket, job) =
      synchronized(lock) {
        val pair = serverSocket to acceptJob
        serverSocket = null
        acceptJob = null
        pair
      }
    runCatching { socket?.close() }
    job?.cancel()
    _state.value = PlayToQServerState(isServing = false)
  }
}
