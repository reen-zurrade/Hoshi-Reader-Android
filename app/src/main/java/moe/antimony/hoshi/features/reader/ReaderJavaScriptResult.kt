package moe.antimony.hoshi.features.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Decodes what `WebView.evaluateJavascript` hands back for an expression that produced a string.
 *
 * The callback receives a JSON literal — quotes and escapes included — so the raw text must never be
 * used directly. Anything that is not a JSON string (nullish, blank, an object, a number) decodes to
 * an empty string, which callers treat as "nothing to act on".
 */
internal fun readerJavaScriptStringResult(result: String?): String {
    val trimmed = result?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed == "null" || trimmed == "undefined") return ""
    val primitive = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull() as? JsonPrimitive ?: return ""
    return if (primitive.isString) primitive.content else ""
}
