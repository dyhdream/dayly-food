package com.daylyfood.app.domain

/**
 * A dish composed at runtime from ingredients the library already knows.
 *
 * The model only picks ingredients and portions; every number the user sees is computed here
 * from our own per-100g values. Anything outside these rules is rejected, so the worst case is
 * "that dish could not be added", never a number nobody can explain.
 */
data class RecipeDraft(
    val name: String,
    val methodId: String,
    val lines: List<Line>,
    val oilGrams: Double,
    /** Ingredients the model thinks the dish needs but our library does not have. */
    val missing: List<String>,
) {
    data class Line(val ingredientId: String, val grams: Double)
}

object RecipeDraftParser {

    data class CatalogItem(val id: String, val name: String)

    const val MAX_LINES = 6
    private const val MAX_NAME = 12
    private const val MIN_GRAMS = 5.0
    private const val MAX_GRAMS = 400.0
    private const val MIN_TOTAL = 100.0
    private const val MAX_TOTAL = 1200.0
    private const val MAX_OIL = 60.0

    /** Parses the model's JSON reply; returns a failure with a user-facing reason on any doubt. */
    fun parse(
        json: String,
        catalog: List<CatalogItem>,
        methodIds: Set<String>,
    ): Result<RecipeDraft> = runCatching {
        val root = org.json.JSONObject(json.trim().removeSurrounding("```json", "```").trim())

        val name = root.optString("name").trim()
        require(name.isNotEmpty() && name.length <= MAX_NAME) { "菜名不合适" }

        val method = root.optString("method").trim()
        require(method in methodIds) { "烹饪方式不认识" }

        val array = root.optJSONArray("ingredients") ?: throw IllegalArgumentException("没有食材")
        require(array.length() in 1..MAX_LINES) { "食材数量不合适" }

        val known = catalog.associateBy { it.id }
        val seen = mutableSetOf<String>()
        val lines = (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            val id = row.optString("id").trim()
            val item = known[id] ?: throw IllegalArgumentException("食材不在库里：$id")
            require(seen.add(id)) { "同一种食材出现了两次" }
            val grams = row.optDouble("grams", -1.0)
            require(grams >= MIN_GRAMS && grams <= MAX_GRAMS) { "${item.name} 的克数不合理" }
            RecipeDraft.Line(id, grams)
        }

        val missingJson = root.optJSONArray("missing")
        val missing = if (missingJson == null) {
            emptyList()
        } else {
            (0 until missingJson.length()).mapNotNull { index ->
                missingJson.optString(index).trim().takeIf { it.isNotEmpty() && it.length <= 12 }
            }
        }
        // If the model could not use most of what it wanted, the composition is guesswork.
        require(missing.size < lines.size + 2) { "这道菜大半配料库里没有，凑不出来" }

        // A dish whose name appears nowhere in its own ingredients is usually the wrong dish.
        val nameChars = name.toCharArray().toSet()
        val ingredientChars = lines
            .mapNotNull { known[it.ingredientId]?.name }
            .flatMap { it.toCharArray().toList() }
            .toSet()
        require(ingredientChars.any { it in nameChars }) {
            "配料和菜名对不上，可能是别的菜"
        }

        val total = lines.sumOf { it.grams }
        require(total in MIN_TOTAL..MAX_TOTAL) { "总重量不合理" }

        val oil = if (root.has("oilGrams")) {
            root.optDouble("oilGrams", 0.0).also { require(it in 0.0..MAX_OIL) { "油量不合理" } }
        } else {
            CookingMethods.defaultOilGrams(method, total)
        }

        RecipeDraft(name, method, lines, oil, missing)
    }
}

/** What the model is allowed to say: ingredient ids from our library, nothing else. */
object RecipePrompt {

    fun system(): String = """
        你是中式家常菜配方助手。只输出 JSON，不要解释。
        你只能使用用户给出的食材 id，绝对不要编造 id 或菜名以外的内容。
        不要输出任何营养数值——营养由程序按食材表计算。
    """.trimIndent()

    fun user(
        dish: String,
        catalog: List<RecipeDraftParser.CatalogItem>,
        /** Real recipe pages found by search. When present the model may only extract from them. */
        references: List<String> = emptyList(),
    ): String = buildString {
        appendLine("把「$dish」拆成食材和克数。")
        if (references.isNotEmpty()) {
            appendLine()
            appendLine("下面是搜到的真实做法原文。配料只能从原文里抽取，绝对不要凭记忆补充原文没有的配料。")
            references.forEachIndexed { index, text ->
                appendLine("【参考 ${index + 1}】$text")
            }
            appendLine("原文没写克数的，按常见家常量估，并把这个配料名写进 missing。")
            appendLine()
        }
        appendLine("可选烹饪方式：${CookingMethods.ALL.joinToString("、") { "${it.id}=${it.label}" }}")
        appendLine("要求：食材 2-6 种且必须来自下面的表；单种 5-400g；总重量 100-1200g。")
        appendLine("可用食材（id|名称）：")
        catalog.forEach { appendLine("${it.id}|${it.name}") }
        appendLine()
        appendLine("只输出这种 JSON：")
        appendLine("""{"name":"菜名","method":"C04","ingredients":[{"id":"F001","grams":120}],"oilGrams":8,"missing":["木耳"]}""")
        appendLine("missing 里写这道菜需要、但上表里没有的配料名称；没有就写空数组。绝对不要为了凑数换一个近似食材。")
    }
}
