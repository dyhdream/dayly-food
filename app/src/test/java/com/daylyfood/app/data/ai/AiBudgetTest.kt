package com.daylyfood.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBudgetTest {

    @Test
    fun `call allowed under the limit`() {
        assertTrue(CallBudget.canCall(used = 0, limit = 20))
        assertTrue(CallBudget.canCall(used = 19, limit = 20))
    }

    @Test
    fun `call blocked at the limit`() {
        assertFalse(CallBudget.canCall(used = 20, limit = 20))
        assertFalse(CallBudget.canCall(used = 25, limit = 20))
    }

    @Test
    fun `zero limit blocks everything instead of allowing infinity`() {
        assertFalse(CallBudget.canCall(used = 0, limit = 0))
        assertEquals(0, CallBudget.remaining(0, 0))
    }

    @Test
    fun `remaining never goes negative`() {
        assertEquals(5, CallBudget.remaining(15, 20))
        assertEquals(0, CallBudget.remaining(30, 20))
    }

    @Test
    fun `provider presets are consistent`() {
        assertEquals("zhipu", AiProviders.DEFAULT.id)
        assertTrue(AiProviders.ZHIPU.supportsVision)
        assertFalse(AiProviders.DEEPSEEK.supportsVision)
        // unknown ids fall back to the default rather than crashing
        assertEquals(AiProviders.DEFAULT.id, AiProviders.byId("nope").id)
    }

    @Test
    fun `config defaults keep paid agents off`() {
        val config = AiConfig()
        assertFalse(config.visionEnabled)
        assertFalse(config.summaryEnabled)
        assertFalse(config.weeklyEnabled)
        assertFalse(config.hasKey)
        assertFalse(config.canCall)
    }
}
