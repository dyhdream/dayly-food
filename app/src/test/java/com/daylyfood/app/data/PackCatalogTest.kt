package com.daylyfood.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PackCatalogTest {

    @Test
    fun `index without packs yields empty list`() {
        val json = """{"dataVersion":1,"file":"data-v1.json","sha256":"x"}"""
        assertTrue(PackCatalog.parse(json).isEmpty())
    }

    @Test
    fun `packs are parsed with size and file`() {
        val json = """
            {"dataVersion":1,"foods":428,"packs":[
              {"id":"hengyang","name":"衡阳家常","desc":"米粉",
               "version":2,"file":"pack-hengyang-v2.json","sha256":"abc","bytes":2048,"foods":6}
            ]}
        """
        val packs = PackCatalog.parse(json)
        assertEquals(1, packs.size)
        val pack = packs.first()
        assertEquals("hengyang", pack.id)
        assertEquals("衡阳家常", pack.name)
        assertEquals(2, pack.version)
        assertEquals(2048L, pack.bytes)
        assertEquals(6, pack.foods)
    }

    @Test
    fun `pack with unsafe id or missing checksum is skipped`() {
        val json = """
            {"packs":[
              {"id":"../etc","file":"a.json","sha256":"abc"},
              {"id":"ok-but-no-sha","file":"a.json"},
              {"id":"fine","file":"pack-fine-v1.json","sha256":"abc"}
            ]}
        """
        val packs = PackCatalog.parse(json)
        assertEquals(listOf("fine"), packs.map { it.id })
    }
}
