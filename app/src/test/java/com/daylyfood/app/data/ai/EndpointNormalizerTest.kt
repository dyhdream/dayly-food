package com.daylyfood.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointNormalizerTest {

    @Test
    fun `bare host gets the completion path`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            EndpointNormalizer.normalize("https://api.deepseek.com"),
        )
    }

    @Test
    fun `missing scheme is added`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            EndpointNormalizer.normalize("api.deepseek.com/v1"),
        )
    }

    @Test
    fun `full endpoint is left alone`() {
        val url = "https://api.deepseek.com/v1/chat/completions"
        assertEquals(url, EndpointNormalizer.normalize(url))
    }

    @Test
    fun `trailing slash and stray spaces are cleaned`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            EndpointNormalizer.normalize("  https://api.deepseek.com/v1/  "),
        )
    }

    @Test
    fun `http is upgraded because post redirects are not followed`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            EndpointNormalizer.normalize("http://api.deepseek.com/chat/completions"),
        )
    }

    @Test
    fun `empty stays empty so the ui can validate`() {
        assertEquals("", EndpointNormalizer.normalize("   "))
    }

    @Test
    fun `display hides the scheme`() {
        assertEquals(
            "api.deepseek.com/v1/chat/completions",
            EndpointNormalizer.display("https://api.deepseek.com/v1/chat/completions"),
        )
    }
}
