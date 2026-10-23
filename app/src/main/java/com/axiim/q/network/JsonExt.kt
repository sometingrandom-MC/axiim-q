package com.axiim.q.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Non-throwing replacement for kotlinx `jsonPrimitive`, which throws [IllegalArgumentException]
 * when the Q returns an object or array where a scalar was expected.
 */
val JsonElement.primitiveOrNull: JsonPrimitive?
  get() = this as? JsonPrimitive

/** Unwraps notification params that the Q may send either as a bare scalar or as a one-element array. */
fun JsonElement?.scalarOrNull(): JsonPrimitive? =
  when (this) {
    is JsonPrimitive -> this
    is JsonArray -> firstOrNull() as? JsonPrimitive
    else -> null
  }
