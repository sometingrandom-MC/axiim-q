package com.axiim.q.network

import com.axiim.q.model.ODataMediaCategory
import com.axiim.q.model.ODataMediaItem
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * HTTP Port 80 client for the Axiim Q's OData v3/v4 media library REST endpoints
 * (`/-/music`, `/-/music/artists`, `/-/music/albums`, `/-/videos`, `/-/videos/movies`, `/-/pictures`)
 * and device status endpoints (`/version`).
 */
class ODataMediaClient {

  suspend fun fetchMediaItems(
    hostIp: String,
    category: ODataMediaCategory,
    top: Int = 50,
  ): List<ODataMediaItem> =
    withContext(Dispatchers.IO) {
      val selectFields =
        when (category) {
          ODataMediaCategory.MUSIC -> "id,title,artist,album,thumbnail,url,runtime"
          ODataMediaCategory.ARTISTS -> "id,title,thumbnail"
          ODataMediaCategory.ALBUMS -> "id,title,artist,thumbnail"
          ODataMediaCategory.VIDEOS, ODataMediaCategory.MOVIES ->
            "id,title,thumbnail,url,description,runtime"
          ODataMediaCategory.PICTURES -> "id,title,thumbnail,image,url"
        }
      val urlStr = "http://$hostIp${category.path}?\$top=$top&\$select=$selectFields"
      try {
        val conn =
          (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2500
            readTimeout = 3500
            setRequestProperty("Accept", "application/json")
          }
        if (conn.responseCode != 200) return@withContext emptyList()
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = AxiimRpcClient.json.parseToJsonElement(body) as? JsonObject ?: return@withContext emptyList()
        val valueArr = root["value"] as? JsonArray ?: return@withContext emptyList()

        valueArr.mapNotNull { elem ->
          val obj = elem as? JsonObject ?: return@mapNotNull null
          val id = obj["id"]?.primitiveOrNull?.contentOrNull ?: return@mapNotNull null
          val title = obj["title"]?.primitiveOrNull?.contentOrNull ?: "Untitled #$id"
          val artist =
            obj["artist"]?.primitiveOrNull?.contentOrNull
              ?: obj["description"]?.primitiveOrNull?.contentOrNull
              ?: ""
          val album = obj["album"]?.primitiveOrNull?.contentOrNull ?: ""
          val thumbPath =
            obj["thumbnail"]?.primitiveOrNull?.contentOrNull
              ?: obj["image"]?.primitiveOrNull?.contentOrNull
          val resolvedThumb =
            thumbPath?.let { if (it.startsWith("http")) it else "http://$hostIp$it" }
          val runtime = obj["runtime"]?.primitiveOrNull?.intOrNull ?: 0

          ODataMediaItem(
            id = id,
            title = title,
            artistOrSubtitle = artist,
            album = album,
            thumbnailUrl = resolvedThumb,
            runtimeSeconds = runtime,
            category = category,
          )
        }
      } catch (_: Exception) {
        emptyList()
      }
    }

  suspend fun fetchFirmwareVersion(hostIp: String): String? =
    withContext(Dispatchers.IO) {
      try {
        val conn =
          (URL("http://$hostIp/version").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2000
            readTimeout = 2000
          }
        if (conn.responseCode != 200) return@withContext null
        val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val h2Match = Regex("<h2>([^<]+)</h2>", RegexOption.IGNORE_CASE).find(html)
        h2Match?.groupValues?.get(1)?.trim()
      } catch (_: Exception) {
        null
      }
    }
}
