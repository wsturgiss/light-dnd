package com.wsturgiss.dnd.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiceTest {

    @Test
    fun parsesWhatTheSrdPrints() {
        assertEquals("2d6+3", DiceExpr.parse("2d6 + 3").toString())
        assertEquals("d20-1", DiceExpr.parse("1d20 − 1").toString())
        assertEquals("d100", DiceExpr.parse("d%").toString())
        assertEquals("8d6", DiceExpr.parse("8d6").toString())
    }

    @Test
    fun mergesLikeTerms() {
        assertEquals("3d6+3", DiceExpr.parse("d6+d6+2+d6+1").toString())
        assertEquals("d8+d4", DiceExpr.parse("d4+d8").toString())
        assertEquals("d20", DiceExpr.parse("d20+2-2").toString())
    }

    @Test
    fun rejectsJunk() {
        assertNull(DiceExpr.parse(""))
        assertNull(DiceExpr.parse("2d6 3"))
        assertNull(DiceExpr.parse("fireball"))
        assertNull(DiceExpr.parse("0d6"))
    }

    @Test
    fun totalsStayInRange() {
        val expr = DiceExpr.parse("2d6+3")!!
        repeat(500) {
            val r = expr.roll(random = Random(it))
            assertTrue(r.total in 5..15, "got ${r.total}")
        }
    }

    @Test
    fun advantageKeepsTheHigherD20() {
        val expr = DiceExpr.parse("d20+5")!!
        repeat(200) {
            val r = expr.roll(Edge.Advantage, Random(it))
            val part = r.parts.first()
            assertEquals(maxOf(part.faces.single(), part.otherD20!!) + 5, r.total)
            assertEquals(r.total - 5, r.natural)
        }
    }

    @Test
    fun edgeIgnoredWhenNotASingleD20() {
        val r = DiceExpr.parse("2d20")!!.roll(Edge.Advantage, Random(1))
        assertEquals(Edge.None, r.edge)
        assertNull(r.parts.first().otherD20)
    }

    @Test
    fun detailReadsLikeALog() {
        val r = DiceExpr.parse("2d6-1")!!.roll(random = Random(3))
        assertTrue(Regex("""\[\d \d] -1""").matches(r.detail), r.detail)
    }

    @Test
    fun edgeDiceKeepsBothInOrder() {
        val r = DiceExpr.parse("d20+2")!!.roll(Edge.Disadvantage, Random(7))
        assertEquals(2, r.edgeDice.size)
        assertEquals(r.edgeDice.min(), r.natural)
        assertEquals(r.edgeDice.min() + 2, r.total)
        assertTrue(DiceExpr.parse("d20")!!.roll(random = Random(1)).edgeDice.isEmpty())
    }
}
