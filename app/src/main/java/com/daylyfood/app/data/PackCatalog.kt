package com.daylyfood.app.data

import org.json.JSONObject

/**
 * One regional pack listed in `data/index.json`.
 *
 * Packs are optional: the base library works without any of them, and installing one only tags
 * its rows with the pack id so it can be removed again without touching base data.
 */
data class PackRef(
    val id: String,
    val name: String,
    val desc: String,
    val version: Int,
    val file: String,
    val sha256: String,
    val bytes: Long,
    val foods: Int,
)

object PackCatalog {
    private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{1,31}$")

    /** Reads the optional `packs` array; an index without it simply means "no packs yet". */
    fun parse(indexJson: String): List<PackRef> {
        val root = JSONObject(indexJson)
        val array = root.optJSONArray("packs") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val id = o.optString("id").trim()
            val file = o.optString("file").trim()
            val sha = o.optString("sha256").trim()
            if (!ID_PATTERN.matches(id) || file.isBlank() || sha.isBlank()) return@mapNotNull null
            PackRef(
                id = id,
                name = o.optString("name").ifBlank { id },
                desc = o.optString("desc"),
                version = o.optInt("version", 1),
                file = file,
                sha256 = sha,
                bytes = o.optLong("bytes", 0L),
                foods = o.optInt("foods", 0),
            )
        }
    }
}
