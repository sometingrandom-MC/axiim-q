package com.axiim.q.network

/**
 * Streaming JSON object framer for the Axiim Q TCP 3000 JSON-RPC 2.0 bus.
 *
 * Per docs/transport.md, the Axiim Q server writes raw JSON objects to the TCP stream
 * WITHOUT any trailing '\n' or '\r\n', and setter responses often arrive concatenated
 * back-to-back with broadcast notifications in a single TCP read:
 *   {"jsonrpc":"2.0","id":"...","result":{...}}{"jsonrpc":"2.0","method":"audio.volume_changed",...}
 *
 * This parser tracks brace depth and string escaping across arbitrary TCP read chunks
 * and yields complete top-level JSON objects in order.
 */
class JsonFrameExtractor {
  private val buffer = StringBuilder()

  @Synchronized
  fun feed(chunk: String): List<String> {
    if (chunk.isEmpty()) return emptyList()
    buffer.append(chunk)
    val frames = mutableListOf<String>()

    while (true) {
      // Trim leading whitespace before a JSON object
      var startIdx = 0
      while (startIdx < buffer.length && buffer[startIdx].isWhitespace()) {
        startIdx++
      }
      if (startIdx > 0) {
        buffer.delete(0, startIdx)
      }
      if (buffer.isEmpty()) break

      // Find opening '{'
      val firstBrace = buffer.indexOf('{')
      if (firstBrace < 0) {
        buffer.clear()
        break
      } else if (firstBrace > 0) {
        buffer.delete(0, firstBrace)
      }

      var depth = 0
      var inString = false
      var escaped = false
      var endIdx = -1

      for (i in 0 until buffer.length) {
        val ch = buffer[i]
        if (escaped) {
          escaped = false
          continue
        }
        if (ch == '\\' && inString) {
          escaped = true
          continue
        }
        if (ch == '"') {
          inString = !inString
          continue
        }
        if (!inString) {
          if (ch == '{') {
            depth++
          } else if (ch == '}') {
            depth--
            if (depth == 0) {
              endIdx = i
              break
            }
          }
        }
      }

      if (endIdx >= 0) {
        val jsonFrame = buffer.substring(0, endIdx + 1)
        frames.add(jsonFrame)
        buffer.delete(0, endIdx + 1)
      } else {
        // Incomplete JSON object; wait for next TCP read chunk
        break
      }
    }

    return frames
  }

  @Synchronized
  fun reset() {
    buffer.clear()
  }
}
