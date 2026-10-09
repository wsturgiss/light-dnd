package com.wsturgiss.dnd.model

import kotlin.random.Random

/** Rolling a d20 twice and keeping one. Only ever applied to a lone d20 term. */
enum class Edge { None, Advantage, Disadvantage }

/**
 * A dice expression: a sum of terms, each either `NdM` or a flat number.
 *
 * Accepts what people actually type and what the SRD prints: `2d6+3`, `d20 - 1`,
 * `1d6 + 2`, `d%`, `4d4+2d6+1`. Whitespace is ignored and `−` counts as minus.
 */
data class DiceExpr(val terms: List<Term>) {

    sealed interface Term {
        val sign: Int
    }

    data class Dice(val count: Int, val sides: Int, override val sign: Int = 1) : Term
    data class Flat(val value: Int, override val sign: Int = 1) : Term

    val isEmpty: Boolean get() = terms.isEmpty()

    /** True when the expression has exactly one die and it is a d20 — the only case edge applies to. */
    val isSingleD20: Boolean
        get() = terms.filterIsInstance<Dice>().let { it.size == 1 && it[0].count == 1 && it[0].sides == 20 }

    /** Canonical text: `2d6+3`, `d20-1`. */
    override fun toString(): String = buildString {
        terms.forEachIndexed { i, t ->
            if (t.sign < 0) append('-') else if (i > 0) append('+')
            when (t) {
                is Dice -> append(if (t.count == 1) "" else t.count).append('d').append(if (t.sides == 100) "100" else t.sides)
                is Flat -> append(t.value)
            }
        }
    }

    fun roll(edge: Edge = Edge.None, random: Random = Random.Default): Roll {
        val useEdge = edge != Edge.None && isSingleD20
        val parts = terms.map { t ->
            when (t) {
                is Flat -> Part(t, listOf(t.value), null)
                is Dice -> {
                    val faces = List(t.count) { random.nextInt(1, t.sides + 1) }
                    if (useEdge && t.sides == 20) {
                        val other = random.nextInt(1, 21)
                        Part(t, faces, other)
                    } else {
                        Part(t, faces, null)
                    }
                }
            }
        }
        return Roll(this, if (useEdge) edge else Edge.None, parts)
    }

    data class Part(val term: Term, val faces: List<Int>, val otherD20: Int?)

    companion object {
        private val TOKEN = Regex("""([+-]?)(\d*)d(\d+|%)|([+-]?)(\d+)""")

        fun parse(text: String): DiceExpr? {
            // Spaces are fine around a sign ("1d6 + 2") and nowhere else: "2d6 3" is not "2d63".
            val s = text.lowercase().replace('−', '-').trim().replace(Regex("""\s*([+-])\s*"""), "$1")
            if (s.isEmpty() || s.any { it.isWhitespace() }) return null
            val terms = mutableListOf<Term>()
            var pos = 0
            while (pos < s.length) {
                val m = TOKEN.matchAt(s, pos) ?: return null
                // Every term after the first needs an explicit sign: "2d6 3" is not "2d6+3".
                if (terms.isNotEmpty() && m.value[0] != '+' && m.value[0] != '-') return null
                if (m.groupValues[3].isNotEmpty()) {
                    val sign = if (m.groupValues[1] == "-") -1 else 1
                    val count = m.groupValues[2].ifEmpty { "1" }.toInt()
                    val sides = m.groupValues[3].let { if (it == "%") 100 else it.toInt() }
                    if (count !in 1..100 || sides !in 2..1000) return null
                    terms += Dice(count, sides, sign)
                } else {
                    val sign = if (m.groupValues[4] == "-") -1 else 1
                    terms += Flat(m.groupValues[5].toInt(), sign)
                }
                pos = m.range.last + 1
            }
            return DiceExpr(merge(terms))
        }

        /** `d6+d6+d6` → `3d6`, `+2+1` → `+3`; dice first, then the flat total. */
        fun merge(terms: List<Term>): List<Term> {
            val dice = terms.filterIsInstance<Dice>()
                .groupBy { it.sides to it.sign }
                .map { (key, ds) -> Dice(ds.sumOf { it.count }, key.first, key.second) }
                .sortedWith(compareBy({ it.sign < 0 }, { -it.sides }))
            val flat = terms.filterIsInstance<Flat>().sumOf { it.value * it.sign }
            return dice + if (flat != 0) listOf(Flat(kotlin.math.abs(flat), if (flat < 0) -1 else 1)) else emptyList()
        }
    }
}

data class Roll(val expr: DiceExpr, val edge: Edge, val parts: List<DiceExpr.Part>) {

    /** The d20 that counts, after advantage or disadvantage picked one. */
    private fun kept(part: DiceExpr.Part): Int {
        val other = part.otherD20 ?: return part.faces.sum()
        val first = part.faces.single()
        return if (edge == Edge.Advantage) maxOf(first, other) else minOf(first, other)
    }

    val total: Int get() = parts.sumOf { kept(it) * it.term.sign }

    /** Both d20s, in the order rolled, when advantage or disadvantage applied; else empty. */
    val edgeDice: List<Int>
        get() = parts.firstOrNull { it.otherD20 != null }?.let { listOf(it.faces.single(), it.otherD20!!) } ?: emptyList()

    /** The face of a lone d20 after edge, for spotting natural 1s and 20s. */
    val natural: Int?
        get() = if (expr.isSingleD20) parts.first { it.term is DiceExpr.Dice }.let { kept(it) } else null

    /** `[4 5] +3`, `[17|9] +5` for a d20 rolled twice. */
    val detail: String
        get() = parts.joinToString(" ") { p ->
            val sign = if (p.term.sign < 0) "-" else "+"
            when {
                p.term is DiceExpr.Flat -> "$sign${p.faces.single()}"
                p.otherD20 != null -> "[${p.faces.single()}|${p.otherD20}]"
                else -> p.faces.joinToString(" ", prefix = if (p.term.sign < 0) "-[" else "[", postfix = "]")
            }
        }.removePrefix("+")
}
