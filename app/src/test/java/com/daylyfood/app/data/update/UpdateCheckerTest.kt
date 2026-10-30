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

    @Test
    fun `release marked in its notes cannot be postponed`() {
        assertTrue(UpdateChecker.isMandatory("本版修复了数据迁移 bug 重要更新 请立即安装", "0.9.2", "0.9.1"))
        assertTrue(UpdateChecker.isMandatory("MANDATORY: fix broken migration", "0.9.2", "0.9.1"))
    }

    @Test
    fun `ordinary release can be postponed`() {
        assertFalse(UpdateChecker.isMandatory("新增拍照记功能，修了几处细节", "0.9.2", "0.9.1"))
    }

    @Test
    fun `major version change is mandatory on its own`() {
        assertTrue(UpdateChecker.isMandatory("常规更新", "1.0.0", "0.9.9"))
        assertFalse(UpdateChecker.isMandatory("常规更新", "0.10.0", "0.9.9"))
    }
}

