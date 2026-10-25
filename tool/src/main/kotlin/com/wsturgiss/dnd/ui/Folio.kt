package com.wsturgiss.dnd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.roundToInt

/**
 * The "illuminated folio" look: each page is a manuscript leaf. A heavy rule and a hairline
 * inside it frame the page, with a ♦ at each corner; the title sits between two ♦ over a double
 * rule; sections open on a large first letter over a rule with a ♦ in the middle; and a page can
 * end in a landscape and its folio number ("fol. 2r").
 *
 * Everything is ink or dim ink on the ground — LightOS's own two colours, so the light theme
 * inverts it for free. Measures are in LightOS grid units (40 px on the LP3) and lines are
 * drawn in whole pixels so they stay sharp on the e-ink-like screen.
 */
object Folio {
    /** From the screen edge to the heavy frame rule, and to the hairline inside it. */
    const val OUTER = 0.35f
    const val INNER = 0.65f

    /** The heavy rule is 3 px on a 40 px unit; the hairline and the dotted rules 1 and 2. */
    const val HEAVY = 0.075f
    const val DOTTED = 0.05f

    /** Each corner ♦ sits on a square of ground this wide, which breaks the frame there. */
    const val CORNER = 1f
}

/** Pixels per LightOS grid unit, for drawing lines on whole pixels. */
@Composable
fun unitPx(): Float = with(LocalDensity.current) { 1f.gridUnitsAsDp().toPx() }

private fun px(unit: Float, f: Float) = max(1, (unit * f).roundToInt()).toFloat()

/**
 * The page frame, drawn over everything and never taking a tap. Put it last in a full-screen Box.
 */
@Composable
fun BoxScope.FolioFrame() {
    val colors = LightThemeTokens.colors
    val u = unitPx()
    Canvas(Modifier.matchParentSize()) {
        val heavy = px(u, Folio.HEAVY)
        val outer = (u * Folio.OUTER).roundToInt().toFloat()
        val inner = (u * Folio.INNER).roundToInt().toFloat()
        // stroke centred on the line, so step in by half its width
        drawRect(
            colors.content,
            topLeft = Offset(outer + heavy / 2, outer + heavy / 2),
            size = Size(size.width - 2 * outer - heavy, size.height - 2 * outer - heavy),
            style = Stroke(heavy),
        )
        drawRect(
            colors.contentSecondary,
            topLeft = Offset(inner + 0.5f, inner + 0.5f),
            size = Size(size.width - 2 * inner - 1, size.height - 2 * inner - 1),
            style = Stroke(1f),
        )
        // the corners: a square of ground, so the ♦ stands clear of the rules
        val c = (u * Folio.CORNER).roundToInt().toFloat()
        listOf(Offset(0f, 0f), Offset(size.width - c, 0f), Offset(0f, size.height - c), Offset(size.width - c, size.height - c))
            .forEach { drawRect(colors.background, topLeft = it, size = Size(c, c)) }
    }
    listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { corner ->
        Box(Modifier.align(corner).size(Folio.CORNER.gridUnitsAsDp()), contentAlignment = Alignment.Center) {
            T("♦", Style.Mark)
        }
    }
}

/** Two hairlines a pixel apart: under the title, over the bottom bar. */
fun DrawScope.doubleRule(y: Float, from: Float, to: Float, color: Color) {
    drawRect(color, Offset(from, y), Size(to - from, 1f))
    drawRect(color, Offset(from, y + 2f), Size(to - from, 1f))
}

/**
 * A double rule across the inside of the frame, at the top or bottom of what it's drawn on.
 * Use on the title strip and the bottom bar, which reach the frame's hairline on each side.
 */
@Composable
fun Modifier.ruledAcross(atTop: Boolean): Modifier {
    val color = LightThemeTokens.colors.contentSecondary
    val u = unitPx()
    return drawBehind {
        val inner = (u * Folio.INNER).roundToInt().toFloat()
        doubleRule(if (atTop) 0f else size.height - 3f, inner, size.width - inner, color)
    }
}

/** A double rule along the whole width of what it's drawn on, top or bottom. */
@Composable
fun Modifier.ruledAcrossFull(atTop: Boolean): Modifier {
    val color = LightThemeTokens.colors.contentSecondary
    return drawBehind { doubleRule(if (atTop) 0f else size.height - 3f, 0f, size.width, color) }
}

/** The title between two dim ♦, as on every folio's header. */
@Composable
fun FolioTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.5f.gridUnitsAsDp())) {
        T("♦", Style.Mark, dim = true)
        T(title, Style.Body, maxLines = 1)
        T("♦", Style.Mark, dim = true)
    }
}

/** A double rule broken by a ♦ in the middle: between a name and what follows, under a section. */
@Composable
fun Fleuron(modifier: Modifier = Modifier) {
    val color = LightThemeTokens.colors.contentSecondary
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f).height(3.dpPx()).drawBehind { doubleRule(0f, 0f, size.width, color) })
        T("♦", Style.Mark, dim = true, modifier = Modifier.padding(horizontal = 0.5f.gridUnitsAsDp()))
        Spacer(Modifier.weight(1f).height(3.dpPx()).drawBehind { doubleRule(0f, 0f, size.width, color) })
    }
}

/** [n] screen pixels as dp. */
@Composable
private fun Int.dpPx() = with(LocalDensity.current) { this@dpPx.toDp() }

/** A dotted rule, as under the roller's expression. */
@Composable
fun DottedRule(modifier: Modifier = Modifier) {
    val color = LightThemeTokens.colors.contentSecondary
    val u = unitPx()
    Spacer(
        modifier.fillMaxWidth().height(2.dpPx()).drawBehind {
            val dot = px(u, Folio.DOTTED)
            var x = 0f
            while (x < size.width) {
                drawRect(color, Offset(x, 0f), Size(dot, dot))
                x += 2 * dot
            }
        },
    )
}

/**
 * A name or heading in Jacquard with an illuminated first letter, the way a manuscript opens a
 * passage. The capital shares the line's baseline, so the row is no taller than it needs to be.
 */
@Composable
fun Illuminated(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    if (text.isEmpty()) return
    // a name that starts with a digit or mark isn't worth illuminating
    if (!text.first().isLetter()) return T(text, Style.Banner, modifier, maxLines = maxLines)
    Row(modifier) {
        T(text.take(1), Style.Capital, Modifier.alignByBaseline())
        T(text.drop(1), Style.Banner, Modifier.alignByBaseline(), maxLines = maxLines)
    }
}

/** A dim note opened with a pilcrow, as the folios mark asides: "¶ Stealth". */
@Composable
fun Pilcrow(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        T("¶ ", Style.MonoSmall, dim = true)
        T(text, Style.Small, dim = true, maxLines = maxLines)
    }
}

/** The foot of a folio: a fleuron, the landscape, and the folio number. */
@Composable
fun FolioFoot(folio: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Fleuron(Modifier.padding(vertical = 0.6f.gridUnitsAsDp()))
        LandscapeStrip()
        Row(Modifier.fillMaxWidth().padding(top = 0.3f.gridUnitsAsDp(), bottom = 0.6f.gridUnitsAsDp())) {
            T("fol. $folio", Style.MonoSmall, dim = true)
        }
    }
}

/** MEK Dings' digits as a row of huts and trees. */
@Composable
fun LandscapeStrip(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        T(LANDSCAPE, Style.Ornament, dim = true, maxLines = 1, align = TextAlign.Center)
    }
}

/**
 * Ink across the width with the words cut out of it: the crit banner. [word] is in Jacquard,
 * [number] in VGA, since Jacquard's digits are old-style.
 */
@Composable
fun InvertedBanner(word: String, number: String?, modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.content)
            .padding(vertical = 0.1f.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("♦", Style.Mark, color = colors.background)
        Spacer(Modifier.width(0.6f.gridUnitsAsDp()))
        T(word, Style.Banner, color = colors.background, maxLines = 1, modifier = Modifier.alignByBaseline())
        if (number != null) T(" $number", Style.MonoBig, color = colors.background, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(0.6f.gridUnitsAsDp()))
        T("♦", Style.Mark, color = colors.background)
    }
}

/**
 * Lines around a die that landed on a natural 20 (straight rays) or a natural 1 (jagged ones),
 * as on the crit folios. The art is on a d20 [HEX_W] by [HEX_H] from its centre; it is drawn
 * around a box [halfW] by [halfH] about [centre], with the parts beyond the box at no more than
 * [scale] (at most 1) times the art's size, so they reach no more than [CRIT_REACH] px beyond it.
 */
fun DrawScope.critBurst(natural: Int, centre: Offset, halfW: Float, halfH: Float, scale: Float, stroke: Float, ink: Color, dim: Color) {
    fun map(p: Pair<Float, Float>): Offset {
        val (dx, dy) = p
        val x = if (abs(dx) <= HEX_W) dx * halfW / HEX_W else sign(dx) * (halfW + (abs(dx) - HEX_W) * scale)
        val y = if (abs(dy) <= HEX_H) dy * halfH / HEX_H else sign(dy) * (halfH + (abs(dy) - HEX_H) * scale)
        return centre + Offset(x, y)
    }
    fun line(points: List<Pair<Float, Float>>, color: Color, above: Boolean = true) {
        // the art is drawn for one quadrant and mirrored into the others
        for (sx in listOf(1f, -1f)) for (sy in if (above) listOf(1f, -1f) else listOf(1f)) {
            val pts = points.map { (x, y) -> map(x * sx to y * sy) }
            pts.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, strokeWidth = stroke, cap = StrokeCap.Square) }
        }
    }
    val end = HEX_W + CRIT_REACH
    if (natural == 20) {
        line(listOf(110f to 0f, end to 0f), ink, above = false)
        // below only: above the die is the roll's label
        line(listOf(0f to 124f, 0f to 154f), ink, above = false)
        line(listOf(125f to 68f, end to 112f), dim)
    } else {
        // a zigzag isn't symmetric top to bottom, so it's drawn once each side, not mirrored
        line(listOf(110f to 0f, 126f to -16f, 142f to 16f, 158f to -16f, end to 0f), ink, above = false)
        line(listOf(125f to 68f, 146f to 78f, 140f to 92f, 160f to 102f, end to 112f), dim)
    }
}

/** How far a crit's rays reach beyond the die, in px at most: leave this much room beside it. */
const val CRIT_REACH = 80f

private const val HEX_W = 94f
private const val HEX_H = 108f

/** A dotted rule down the left edge: between columns, as between the Bag's coins. */
@Composable
fun Modifier.dottedLeft(): Modifier {
    val color = LightThemeTokens.colors.contentSecondary
    val u = unitPx()
    return drawBehind {
        val dot = px(u, Folio.DOTTED)
        var y = 0f
        while (y < size.height) {
            drawRect(color, Offset(0f, y), Size(dot, dot))
            y += 2 * dot
        }
    }
}
