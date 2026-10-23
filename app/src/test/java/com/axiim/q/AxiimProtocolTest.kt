package com.axiim.q

import com.axiim.q.model.QuickTileId
import com.axiim.q.model.SpeakerChannel
import com.axiim.q.network.AxiimRpcClient
import com.axiim.q.network.JsonFrameExtractor
import com.axiim.q.network.SsdpDiscoveryClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AxiimProtocolTest {

  @Test
  fun jsonFrameExtractor_splitsConcatenatedRpcResponseAndNotificationWithoutNewlines() {
    val extractor = JsonFrameExtractor()
    val concatenated =
      """{"jsonrpc":"2.0","id":"4b07e364-32d5-44ea-a500-83df77d9a100","result":{"volume":65,"muted":false}}{"jsonrpc":"2.0","method":"audio.volume_changed","params":{"volume":65,"muted":false}}"""

    val frames = extractor.feed(concatenated)
    assertEquals(2, frames.size)
    assertTrue(frames[0].contains("\"id\":\"4b07e364-32d5-44ea-a500-83df77d9a100\""))
    assertTrue(frames[1].contains("\"method\":\"audio.volume_changed\""))
  }

  @Test
  fun jsonFrameExtractor_handlesChunkedPartialTcpReadsAndEscapedQuotes() {
    val extractor = JsonFrameExtractor()
    val part1 = """{"jsonrpc":"2.0","id":"1","result":"axiim \"Q\" {UHD}"}"""
    val part2FirstHalf = """{"jsonrpc":"2.0","method":"avr.input_changed","params":{"port":2,"""
    val part2SecondHalf = """"label":"SHIELD"}}"""

    val firstBatch = extractor.feed(part1 + part2FirstHalf)
    assertEquals(1, firstBatch.size)
    assertEquals(part1, firstBatch[0])

    val secondBatch = extractor.feed(part2SecondHalf)
    assertEquals(1, secondBatch.size)
    assertEquals(part2FirstHalf + part2SecondHalf, secondBatch[0])
  }

  @Test
  fun ssdpDiscoveryParser_handlesDoubleColonStHeaderAndExtractsUuidAndMac() {
    val rawCapturedSsdp =
      "HTTP/1.1 200 OK\r\n" +
        "CACHE-CONTROL: max-age=1800\r\n" +
        "DATE: Fri, 06 Jun 2014 15:48:32 GMT\r\n" +
        "EXT:\r\n" +
        "ST:: urn:axiim-com:device:zopa:1\r\n" +
        "LOCATION: http://192.168.1.83/\r\n" +
        "BOOTID.UPNP.ORG: 1401397260\r\n" +
        "CONFIGID.UPNP.ORG: 1401397260\r\n" +
        "SERVER: axiim-Zopa/2019.0501.0073 UPnP/1.1 zopa_ssdp/2019.0501.0073\r\n" +
        "USN: uuid:4365746F-6E2D-5A6F-7061-98E8486000FC::urn:axiim-com:device:zopa:1\r\n" +
        "ACCENT-COLOR: #00FFBF\r\n" +
        "DEVICE-NAME: axiim Q\r\n\r\n"

    val device = SsdpDiscoveryClient.parseSsdpResponse(rawCapturedSsdp)
    assertNotNull(device)
    assertEquals("192.168.1.83", device!!.ip)
    assertEquals("4365746F-6E2D-5A6F-7061-98E8486000FC", device.uuid)
    assertEquals("98:E8:48:60:00:FC", device.macAddress)
    assertEquals("#00FFBF", device.accentColor)
    assertEquals("axiim Q", device.name)
  }

  @Test
  fun speakerChannel_mapsAllThreeIdentifierConventions() {
    assertEquals(SpeakerChannel.FRONT_LEFT, SpeakerChannel.fromLoc("FL"))
    assertEquals(SpeakerChannel.FRONT_LEFT, SpeakerChannel.fromLoc("fl"))
    assertEquals(1, SpeakerChannel.FRONT_LEFT.eqIndex)

    assertEquals(SpeakerChannel.SUBWOOFER, SpeakerChannel.fromLoc("S"))
    assertEquals(SpeakerChannel.SUBWOOFER, SpeakerChannel.fromLoc("s"))
    assertEquals(3, SpeakerChannel.SUBWOOFER.eqIndex)

    assertEquals(SpeakerChannel.CENTER, SpeakerChannel.fromLoc("C"))
    assertEquals(4, SpeakerChannel.CENTER.eqIndex)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rpcClient_rejectsSettingsGetWithEmptyParamsArray() = runTest {
    val client = AxiimRpcClient()
    // Must throw IllegalArgumentException before touching network
    client.call("settings.get", buildJsonArray {})
  }

  @Test
  fun parseEqualizerList_parsesLiveCapturedEqResponse() {
    val rawEqArray =
      """
      [
        {
          "name": "disabled",
          "preset": 0,
          "mode": 0,
          "uEQ_linked": 0,
          "uEQC3_1_type": "lpf",
          "uEQC3_1_freq": 80,
          "uEQC3_1_Q": 0.54,
          "uEQC3_2_type": "lpf",
          "uEQC3_2_freq": 80,
          "uEQC3_2_Q": 1.31,
          "active": true,
          "uEQC1_1": 1.5,
          "uEQC1_10": -2.0,
          "gaincompression": 0
        },
        {"name": "per_speaker", "preset": 0, "mode": 0, "uEQ_linked": 0, "poweruser": true}
      ]
      """.trimIndent()
    val jsonArray = AxiimRpcClient.json.parseToJsonElement(rawEqArray) as JsonArray
    val presets = AxiimRpcClient.parseEqualizerList(jsonArray)
    assertEquals(2, presets.size)
    assertTrue(presets[0].active)
    assertEquals(1.5f, presets[0].channelBands[1]!![0])
    assertEquals(-2.0f, presets[0].channelBands[1]!![9])
    assertEquals(80, presets[0].lpf1Freq)
    assertTrue(presets[1].powerUser)
  }

  @Test
  fun quickTileId_defaultConfigurationExcludesNeo6AndIncludesDtsNeuralAndNightMode() {
    val defaults = QuickTileId.defaultIds
    assertTrue(QuickTileId.DTS_NEURAL in defaults)
    assertTrue(QuickTileId.NIGHT_MODE in defaults)
    assertTrue(QuickTileId.DTS_NEO6 !in defaults)
  }
}
