package com.daylyfood.app.domain

import com.daylyfood.app.data.db.FoodEntity

/**
 * Turns what a person types into database probes, and orders what comes back.
 *
 * The database only understands "name contains these characters", so "西红柿炒蛋" finds nothing
 * even though "番茄炒蛋" is right there. Synonyms fix the words; splitting fixes the spelling.
 */
object SearchQuery {

    /** Everyday alternates. Kept tiny on purpose: every pair here changes what users find. */
    private val SYNONYMS: Map<String, List<String>> = mapOf(
        "西红柿" to listOf("番茄"),
        "番茄" to listOf("西红柿"),
        "马铃薯" to listOf("土豆"),
        "土豆" to listOf("马铃薯"),
        "洋芋" to listOf("土豆"),
        "番薯" to listOf("红薯"),
        "地瓜" to listOf("红薯"),
        "苞谷" to listOf("玉米"),
        "包谷" to listOf("玉米"),
        "洋葱" to listOf("元葱"),
        "菜花" to listOf("西兰花", "花菜"),
        "花菜" to listOf("菜花"),
        "鸡胸" to listOf("鸡胸肉"),
        "鸡胸肉" to listOf("鸡胸"),
        "五花肉" to listOf("猪肉(五花)"),
        "瘦肉" to listOf("里脊"),
        "里脊" to listOf("猪里脊"),
        "酱油" to listOf("生抽"),
        "生抽" to listOf("酱油"),
        "米粉" to listOf("粉", "米线"),
        "米线" to listOf("米粉"),
        "面条" to listOf("挂面"),
        "大米" to listOf("米饭"),
        "米饭" to listOf("大米"),
        "酸奶" to listOf("原味酸奶"),
        "豆浆" to listOf("豆浆(无糖)"),
    )

    /** Filler words that only make the match worse. */
    private val NOISE = listOf("的", "了", "一个", "一份", "一碗", "一点", "些", "吃", "我想")

    /** Search probes to try: the phrase, its pieces, and known synonyms. */
    fun terms(raw: String): List<String> {
        var cleaned = raw.trim()
        if (cleaned.isEmpty()) return emptyList()
        NOISE.forEach { cleaned = cleaned.replace(it, " ") }
        val pieces = cleaned.split(' ', '　', '、', '，', ',', '+', '/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val out = LinkedHashSet<String>()
        pieces.forEach { piece ->
            out += piece
            // A whole dish name is often "X做Y"; also try the head noun so "番茄炒蛋" can match 番茄.
            if (piece.length > 3) out += piece.take(2)
            SYNONYMS[piece]?.let { out += it }
            SYNONYMS.forEach { (key, values) ->
                if (piece.contains(key)) {
                    out += piece.replace(key, values.first())
                    out += values
                }
            }
        }
        return out.filter { it.length >= 1 }.take(8)
    }

    /** Higher is better: exact name, then prefix, then substring; dishes edge out raw ingredients. */
    fun score(food: FoodEntity, raw: String): Int {
        val q = raw.trim()
        val name = food.name
        val base = when {
            name == q -> 120
            name.startsWith(q) -> 90
            name.contains(q) -> 70
            else -> 0
        }
        val kindBonus = if (food.kind == "DISH") 6 else 0
        val shortPenalty = if (name.length > q.length + 6) -8 else 0
        return base + kindBonus + shortPenalty
    }

    /** Best first, best-effort; anything scoring zero still shows up at the end. */
    fun rank(foods: List<FoodEntity>, raw: String): List<FoodEntity> =
        foods.distinctBy { it.id }
            .sortedWith(compareByDescending<FoodEntity> { score(it, raw) }.thenBy { it.name.length })
}
