package com.daylyfood.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `higher patch is newer`() {
        assertTrue(UpdateChecker.isNewer("0.2.1", "0.2.0"))
    }

    @Test
    fun `lower version is not newer`() {
        assertFalse(UpdateChecker.isNewer("0.1.9", "0.2.0"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(UpdateChecker.isNewer("0.2.0", "0.2.0"))
    }

    @Test
    fun `minor bump outranks patch bump`() {
        assertTrue(UpdateChecker.isNewer("0.3.0", "0.2.9"))
    }

    @Test
    fun `major bump outranks everything`() {
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertFalse(UpdateChecker.isNewer("0.2", "0.2.0"))
        assertTrue(UpdateChecker.isNewer("0.2.1", "0.2"))
    }

    @Test
    fun `suffixes are ignored`() {
        assertFalse(UpdateChecker.isNewer("0.2.0-beta", "0.2.0"))
    }
}
