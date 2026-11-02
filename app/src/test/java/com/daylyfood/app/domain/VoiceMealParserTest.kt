package com.daylyfood.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMealParserTest {

    private val candidates = listOf(
        VoiceMealParser.Candidate("D001", "辣椒炒肉", listOf("辣椒炒肉")),
        VoiceMealParser.Candidate("F001", "辣椒(鲜)", listOf("辣椒", "青椒")),
        VoiceMealParser.Candidate("D002", "包子", listOf("包子", "肉包")),
        VoiceMealParser.Candidate("F002", "豆浆(无糖)", listOf("豆浆")),
        VoiceMealParser.Candidate("F003", "鸡胸肉", listOf("鸡胸", "鸡胸肉")),
    )

    @Test
    fun `finds foods by name in spoken order`() {
        val matches = VoiceMealParser.parse("早上吃了两个包子和一碗豆浆", candidates)

        assertEquals(listOf("D002", "F002"), matches.map { it.foodId })
    }

    @Test
    fun `longer name wins over a name it contains`() {
        val matches = VoiceMealParser.parse("中午辣椒炒肉", candidates)

        assertEquals(listOf("D001"), matches.map { it.foodId })
    }

    @Test
    fun `alias matches its food`() {
        val matches = VoiceMealParser.parse("来点青椒", candidates)

        assertEquals("F001", matches.single().foodId)
    }

    @Test
    fun `each food appears once even when repeated`() {
        val matches = VoiceMealParser.parse("豆浆加豆浆", candidates)

        assertEquals(1, matches.size)
    }

    @Test
    fun `unknown sentence yields nothing`() {
        assertTrue(VoiceMealParser.parse("今天天气不错", candidates).isEmpty())
    }

    @Test
    fun `blank input yields nothing`() {
        assertTrue(VoiceMealParser.parse("   ", candidates).isEmpty())
    }
}
