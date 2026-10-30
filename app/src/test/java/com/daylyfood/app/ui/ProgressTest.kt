package com.daylyfood.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTest {

    @Test
    fun `normal progress is the ratio`() {
        assertEquals(0.5f, progressOf(1000, 2000), 0.0001f)
    }

    @Test
    fun `over target clamps to one`() {
        assertEquals(1f, progressOf(2600, 2000), 0.0001f)
    }

    @Test
    fun `zero target yields zero instead of NaN`() {
        assertEquals(0f, progressOf(500, 0), 0.0001f)
        assertEquals(0f, progressOf(500, -10), 0.0001f)
    }

    @Test
    fun `empty intake is zero`() {
        assertEquals(0f, progressOf(0, 2000), 0.0001f)
    }

    @Test
    fun `double inputs work for macros`() {
        assertEquals(0.25f, progressOf(40.0, 160.0), 0.0001f)
    }
}
