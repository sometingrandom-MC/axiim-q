package com.axiim.q.network

import android.content.Context
import android.net.wifi.WifiManager
import com.axiim.q.model.DiscoveredDevice
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SSDP Discovery client for the Axiim Q UHD (`urn:axiim-com:device:zopa:1`).
 *
 * Implements the exact specification and quirk handling documented in `docs/discovery.md`:
 * - Sends M-SEARCH twice back-to-back to `239.255.255.250:1900` with TTL = 5 and timeout = 1500 ms.
 * - Handles the malformed `ST:: urn:axiim-com:device:zopa:1` double-colon header.
 * - Extracts `LOCATION` IP, `USN` UUID (which encodes "Ceton-Zopa" + 6-byte hardware MAC),
 *   `ACCENT-COLOR` (defaults to `#00a5dd`), `DEVICE-NAME`, and `SERVER`.
 */
class SsdpDiscoveryClient(private val context: Context?) {

  companion object {
    const val SSDP_ADDRESS = "239.255.255.250"
    const val SSDP_PORT = 1900
    const val SEARCH_TARGET = "urn:axiim-com:device:zopa:1"
    const val RECEIVE_TIMEOUT_MS = 1500
    const val MULTICAST_TTL = 5

    val M_SEARCH_PAYLOAD: String =
      "M-SEARCH * HTTP/1.1\r\n" +
        "HOST: 239.255.255.250:1900\r\n" +
        "ST: urn:axiim-com:device:zopa:1\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 1\r\n\r\n"

    private val UUID_REGEX =
      Regex("([A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12})")
    private val LOCATION_IP_REGEX =
      Regex("http://([^/:]+)")

    /**
     * Parses a raw SSDP HTTP 200 OK datagram into a [DiscoveredDevice].
     * Exposed as a pure companion function for unit testing.
     */
    fun parseSsdpResponse(rawResponse: String, fallbackIp: String? = null): DiscoveredDevice? {
      if (!rawResponse.contains("200 OK", ignoreCase = true)) return null
      // Verify it's an Axiim Q / zopa response
      val isZopa =
        rawResponse.contains(SEARCH_TARGET, ignoreCase = true) ||
          rawResponse.contains("zopa_ssdp", ignoreCase = true) ||
          rawResponse.contains("axiim-Zopa", ignoreCase = true)
      if (!isZopa) return null

      var locationIp: String? = fallbackIp
      var uuid: String? = null
      var accentColor = "#00a5dd"
      var deviceName: String? = null
      var serverInfo = "axiim-Zopa/2019.0501.0073"

      for (rawLine in rawResponse.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        when {
          line.startsWith("LOCATION:", ignoreCase = true) -> {
            val value = line.substringAfter(":").trim()
            val match = LOCATION_IP_REGEX.find(value)
            if (match != null) {
              locationIp = match.groupValues[1]
            }
          }
          line.startsWith("USN:", ignoreCase = true) -> {
            val match = UUID_REGEX.find(line)
            if (match != null) {
              uuid = match.groupValues[1].uppercase()
            }
          }
          line.startsWith("ACCENT-COLOR:", ignoreCase = true) -> {
            val value = line.substringAfter(":").trim()
            if (value.isNotEmpty()) {
              accentColor = value
            }
          }
          line.startsWith("DEVICE-NAME:", ignoreCase = true) -> {
            val value = line.substringAfter(":").trim()
            if (value.isNotEmpty()) {
              deviceName = value
            }
          }
          line.startsWith("SERVER:", ignoreCase = true) -> {
            val value = line.substringAfter(":").trim()
            if (value.isNotEmpty()) {
              serverInfo = value
            }
          }
        }
      }

      val resolvedIp = locationIp ?: return null
      val resolvedUuid = uuid ?: "4365746F-6E2D-5A6F-7061-${resolvedIp.hashCode().toUInt().toString(16).padStart(12, '0').uppercase()}"
      val resolvedName = deviceName?.takeIf { it.isNotBlank() } ?: resolvedIp

      return DiscoveredDevice(
        uuid = resolvedUuid,
        name = resolvedName,
        ip = resolvedIp,
        port = 80,
        rpcPort = 3000,
        accentColor = accentColor,
        serverInfo = serverInfo,
        userAdded = false,
        missedDiscoveries = 0,
        isOnline = true,
      )
    }
  }

  suspend fun discoverDevices(): List<DiscoveredDevice> =
    withContext(Dispatchers.IO) {
      val discoveredByUuid = linkedMapOf<String, DiscoveredDevice>()
      val wifiManager =
        context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      val multicastLock =
        wifiManager?.createMulticastLock("axiim_q_ssdp_lock")?.apply {
          setReferenceCounted(true)
          runCatching { acquire() }
        }

      var socket: MulticastSocket? = null
      try {
        socket = MulticastSocket(null).apply {
          reuseAddress = true
          bind(InetSocketAddress(0))
          timeToLive = MULTICAST_TTL
          soTimeout = RECEIVE_TIMEOUT_MS
        }

        val payloadBytes = M_SEARCH_PAYLOAD.toByteArray(Charsets.UTF_8)
        val groupAddress = InetAddress.getByName(SSDP_ADDRESS)
        val packet = DatagramPacket(payloadBytes, payloadBytes.size, groupAddress, SSDP_PORT)

        // Per docs/discovery.md: send twice back-to-back
        socket.send(packet)
        socket.send(packet)

        val deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS
        val recvBuf = ByteArray(4096)

        while (System.currentTimeMillis() < deadline) {
          val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(100)
          socket.soTimeout = remaining
          val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
          try {
            socket.receive(recvPacket)
            val text = String(recvPacket.data, recvPacket.offset, recvPacket.length, Charsets.UTF_8)
            val senderIp = recvPacket.address?.hostAddress
            val parsed = parseSsdpResponse(text, senderIp)
            if (parsed != null) {
              discoveredByUuid[parsed.uuid] = parsed
            }
          } catch (_: SocketTimeoutException) {
            break
          }
        }
      } catch (_: Exception) {
        // Ignore network errors when Wi-Fi is unavailable
      } finally {
        runCatching { socket?.close() }
        runCatching {
          if (multicastLock?.isHeld == true) {
            multicastLock.release()
          }
        }
      }

      discoveredByUuid.values.toList()
    }
}
