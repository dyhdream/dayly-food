package com.daylyfood.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebSearchTest {

    @Test
    fun `search url keeps the host and uses the tools path`() {
        assertEquals(
            "https://api.deepseek.com/v1/tools/search",
            WebSearch.searchUrl("https://api.deepseek.com/v1/chat/completions"),
        )
        assertEquals(
            "https://api.deepseek.com/v1/tools/search",
            WebSearch.searchUrl("api.deepseek.com"),
        )
    }

    @Test
    fun `blank endpoint has no search url`() {
        assertNull(WebSearch.searchUrl(""))
    }

    @Test
    fun `results prefer full content over summary and drop thin hits`() {
        val json = """
            {"results":[
              {"title":"辣椒炒肉做法","url":"https://a.example","rerank_score":2.2,
               "summary":"短摘要","content":"青椒 200g、猪里脊 150g、生抽 10ml，热锅快炒至断生即可出锅装盘。"},
              {"title":"空的","url":"https://b.example","rerank_score":2.9,"summary":"","content":""},
              {"title":"次优","url":"https://c.example","rerank_score":1.1,
               "summary":"五花肉切片，与青椒同炒，加豆豉提味，出锅前淋少许生抽翻匀。"}
            ]}
        """.trimIndent()

        val refs = WebSearch.parse(json)

        assertEquals(2, refs.size)
        assertEquals("https://a.example", refs.first().url)
        assertTrue(refs.first().text.contains("猪里脊"))
        assertTrue(refs.none { it.text == "短摘要" })
    }

    @Test
    fun `malformed payload yields nothing instead of throwing`() {
        assertTrue(WebSearch.parse("not json").isEmpty())
        assertTrue(WebSearch.parse("""{"results":[]}""").isEmpty())
    }

    @Test
    fun `limit is respected`() {
        val rows = (1..5).joinToString(",") {
            """{"title":"t$it","url":"https://x$it.example","rerank_score":1.0,
                "content":"这是一段足够长的菜谱正文，包含食材与用量说明，可以用于抽取，第 $it 条。"}"""
        }
        assertEquals(2, WebSearch.parse("""{"results":[$rows]}""", limit = 2).size)
    }
}
