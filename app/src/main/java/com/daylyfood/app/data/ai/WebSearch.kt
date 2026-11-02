package com.daylyfood.app.data.ai

import org.json.JSONObject

/**
 * One web page the search API returned, used only as evidence for the model to extract from.
 *
 * We never store these pages: the point is to turn "the model remembers roughly" into
 * "the model read the recipe", which is what keeps ingredient lists from drifting.
 */
data class WebRef(
    val title: String,
    val url: String,
    val text: String,
    val score: Double,
)

object WebSearch {

    private const val MIN_TEXT = 16

    /** Endpoint shown in settings points at chat/completions; the search tool lives on the same host. */
    fun searchUrl(endpointUrl: String): String? {
        val normalized = EndpointNormalizer.normalize(endpointUrl)
        if (normalized.isBlank()) return null
        val host = runCatching { java.net.URL(normalized).host }.getOrNull() ?: return null
        if (host.isBlank()) return null
        return "https://$host/v1/tools/search"
    }

    /** Keeps only results that carry usable text, best first. */
    fun parse(json: String, limit: Int = 3): List<WebRef> {
        val results = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return emptyList()
        return (0 until results.length())
            .mapNotNull { index ->
                val row = results.optJSONObject(index) ?: return@mapNotNull null
                val text = row.optString("content").ifBlank { row.optString("summary") }.trim()
                if (text.length < MIN_TEXT) return@mapNotNull null
                WebRef(
                    title = row.optString("title").trim(),
                    url = row.optString("url").trim(),
                    text = text,
                    score = row.optDouble("rerank_score", 0.0),
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }
}
