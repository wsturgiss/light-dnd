package com.wsturgiss.dnd

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.wsturgiss.dnd.ui.TOTAL_SCALE
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.DiceCamera
import com.wsturgiss.dnd.model.DiceExpr
import com.wsturgiss.dnd.model.Edge
import com.wsturgiss.dnd.model.LoggedRoll
import com.wsturgiss.dnd.model.Roll
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.signed
import com.wsturgiss.dnd.ui.DiceClips
import com.wsturgiss.dnd.ui.DiceStage
import com.wsturgiss.dnd.ui.GUTTER
import com.wsturgiss.dnd.ui.Page
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.StageDie
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow
import java.text.DateFormat
import java.util.Date

/**
 * Dice. Tap dice and modifiers to build an expression (`2d6+3`), then Roll.
 *
 * Opened with a [RollRequest] — from the sheet, or a die tapped in a spell — it
 * rolls straight away; Roll again rerolls the same thing, with ADV/DIS if set.
 * Tapping a die after a roll starts a fresh expression.
 */
class RollScreen(
    sealedActivity: SealedLightActivity,
    private val request: RollRequest?,
) : DndScreen<Unit>(sealedActivity) {

    private var expr by mutableStateOf(request?.expr?.let { DiceExpr.parse(it) })
    private var label by mutableStateOf(request?.label)
    private var edge by mutableStateOf(request?.edge ?: Edge.None)
    private var result by mutableStateOf<LoggedRoll?>(null)
    /** The roll behind [result], with every die's face, for the animation. */
    private var lastRoll by mutableStateOf<Roll?>(null)
    /** Counts rolls, so rolling again restarts the animation even when the result repeats. */
    private var rollId by mutableStateOf(0)
    /** False while the dice are still in the air: the numbers stay hidden until they land. */
    private var landed by mutableStateOf(true)
    /** Set after a roll, so the next die tapped starts over rather than adding on. */
    private var fresh by mutableStateOf(false)
    private var rolledOnOpen = false

    init {
        DiceClips.init(lightContext::readAsset)
    }

    private fun rollNow() {
        val e = expr ?: DiceExpr.parse("d20")!!
        expr = e
        val roll = e.roll(edge)
        result = Store.record(label ?: e.toString(), roll)
        lastRoll = roll
        rollId++
        landed = false
        fresh = true
    }

    /** Advantage and disadvantage apply to a lone d20 (with any modifier), as in 5e; with no dice yet, Roll rolls one. */
    private val edgeAllowed: Boolean get() = expr?.isSingleD20 ?: true

    private fun add(term: String) {
        val base = if (fresh) "" else expr?.toString().orEmpty()
        if (fresh) {
            label = null
            fresh = false
        }
        // A modifier with no dice yet applies to a d20: +5 then Roll is d20+5, not a flat 5.
        val start = if (base.isEmpty() && term.drop(1).first().isDigit()) "d20" else base
        expr = DiceExpr.parse(if (start.isEmpty()) term.removePrefix("+") else start + term) ?: expr
        if (!edgeAllowed) edge = Edge.None
    }

    private fun clear() {
        expr = null
        label = null
        result = null
        lastRoll = null
        fresh = false
        edge = Edge.None
    }

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            if (request != null && !rolledOnOpen) {
                rolledOnOpen = true
                rollNow()
            }
        }
        Page(
            title = "Roll",
            onBack = { goBack(null) },
            bottom = listOf(
                LightBarButton.Text("Clear", onClick = ::clear),
                LightBarButton.Text("Roll", onClick = ::rollNow),
                LightBarButton.LightIcon(LightIcons.LIST, onClick = { navigateTo(screenFactory = { LogScreen(it) }) }),
            ),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = GUTTER.gridUnitsAsDp()),
            ) {
                ResultArea(Modifier.weight(1f))
                Builder()
                Pad()
            }
        }
    }

    @Composable
    private fun ResultArea(modifier: Modifier) {
        val r = result
        val camera = Store.state.collectAsState().value.diceCamera
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (r == null) {
                T("tap dice, then Roll", Style.Small, dim = true)
                return@Column
            }
            val stage = lastRoll?.let(::stageDice)
            // what was rolled, always in the same place: top centre
            T(r.label, Style.Small, dim = true, maxLines = 1)
            if (stage != null && camera != DiceCamera.Off && r.edge != Edge.None) {
                T(if (r.edge == Edge.Advantage) "advantage" else "disadvantage", Style.Small, dim = true)
            }
            if (stage != null && camera != DiceCamera.Off) {
                Thrown(r, lastRoll!!, stage!!, camera, Modifier.weight(1f))
                return@Column
            }
            if (r.edgeDice.size == 2 && r.natural != null) {
                EdgeResult(r, r.natural)
                return@Column
            }
            T(r.total.toString(), Style.Total)
            val tag = when {
                r.natural == 20 -> "NATURAL 20"
                r.natural == 1 -> "NATURAL 1"
                r.edge == Edge.Advantage -> "advantage"
                r.edge == Edge.Disadvantage -> "disadvantage"
                else -> ""
            }
            // What you rolled and what was added: big enough to check at arm's length.
            // A long pool (8d6) steps down a size and wraps rather than being cut off.
            T(r.detail, if (r.detail.length <= 16) Style.MonoBig else Style.Mono, align = TextAlign.Center, maxLines = 3)
            if (tag.isNotEmpty()) T(tag, Style.Mono)
        }
    }

    /**
     * The roll animated, read left to right as a sum: dice + modifier = total. The modifier
     * is added once, to all the dice together, as in 5e. With no arithmetic (one die, or
     * advantage, and no modifier) the die is the answer, so there's no = or total.
     *
     * The dice and the text beside them are packed and centred as one group. The dice are
     * thrown across the whole area; the numbers appear when they land, their space kept so
     * nothing jumps.
     */
    @Composable
    private fun Thrown(r: LoggedRoll, roll: Roll, dice: List<StageDie>, camera: DiceCamera, stageModifier: Modifier) {
        val id = rollId
        val shown = if (landed) Modifier else Modifier.alpha(0f)
        val mod = roll.parts.sumOf { p -> (p.term as? DiceExpr.Flat)?.let { it.value * it.sign } ?: 0 }
        val counted = dice.count { !it.dim }
        val arithmetic = mod != 0 || counted > 1
        val tag = if (r.natural == 20 || r.natural == 1) "NATURAL ${r.natural}" else null
        val gap = 1f.gridUnitsAsDp()
        SubcomposeLayout(stageModifier.fillMaxWidth()) { cs ->
            val maxW = cs.maxWidth
            val maxH = cs.maxHeight
            val text = subcompose("text") {
                Column {
                    if (arithmetic) {
                        Row(
                            shown,
                            horizontalArrangement = Arrangement.spacedBy(0.5f.gridUnitsAsDp()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (mod != 0) T(signed(mod), Style.MonoBig)
                            T("=", Style.MonoBig)
                            T(r.total.toString(), Style.Total)
                        }
                    }
                    if (tag != null) T(tag, Style.Mono, modifier = shown)
                }
            }.single().measure(Constraints(maxWidth = maxW / 2, maxHeight = maxH))
            // nothing beside the dice (a lone die, no modifier): no gap, so the dice sit dead centre
            val gapPx = if (text.width == 0) 0 else gap.roundToPx()
            val room = maxW - text.width - gapPx
            val groupW = DiceClips.placement(dice.size, camera, room, maxH, room).groupWidth
            val landEnd = (maxW - groupW - gapPx - text.width) / 2 + groupW
            val midY = DiceClips.placement(dice.size, camera, room, maxH, landEnd).groupMidY
            val stage = subcompose("stage") {
                DiceStage(dice, camera, id, landEnd = landEnd, room = room, modifier = Modifier.fillMaxSize()) {
                    if (id == rollId) landed = true
                }
            }.single().measure(Constraints.fixed(maxW, maxH))
            layout(maxW, maxH) {
                stage.place(0, 0)
                // level with the middle of the landed dice
                text.place(landEnd + gapPx, (midY - text.height / 2).coerceIn(0, maxOf(0, maxH - text.height)))
            }
        }
    }

    /**
     * Each die as thrown, or null when the roll has no animation: a die there's no art for (d3),
     * or more dice than fit on screen. A d100 is thrown as a pair of d10s, tens (00–90) and units
     * (0–9); 00 and 0 read as 100.
     */
    private fun stageDice(roll: Roll): List<StageDie>? {
        val out = mutableListOf<StageDie>()
        for (p in roll.parts) {
            val t = p.term as? DiceExpr.Dice ?: continue
            val other = p.otherD20
            when {
                other != null -> {
                    // the die that doesn't count is dimmed; on a tie, the first counts, as in EdgeResult
                    val first = p.faces.single()
                    val keepFirst = if (roll.edge == Edge.Advantage) first >= other else first <= other
                    out += StageDie(20, "d20", first - 1, dim = !keepFirst)
                    out += StageDie(20, "d20", other - 1, dim = keepFirst)
                }
                t.sides == 100 -> p.faces.forEach { v ->
                    out += StageDie(10, "pct10", v / 10 % 10)
                    out += StageDie(10, "pct1", v % 10)
                }
                t.sides in DiceClips.SIDES -> p.faces.forEach { out += StageDie(t.sides, "d${t.sides}", it - 1) }
                else -> return null
            }
        }
        return out.takeIf { it.isNotEmpty() && it.size <= DiceClips.maxDice }
    }

    /**
     * Advantage or disadvantage: both d20s big, the one that counts underlined and the
     * other dimmed, then the sum that gives the total.
     */
    @Composable
    private fun EdgeResult(r: LoggedRoll, kept: Int) {
        T(if (r.edge == Edge.Advantage) "ADVANTAGE" else "DISADVANTAGE", Style.Mono, maxLines = 1)
        // Underline the die that counts. IBM VGA's baseline sits 12 font pixels down the em;
        // the bar is one font pixel thick, one font pixel below it, so it stays on the font's grid.
        val fontPx = TOTAL_SCALE.toFloat()
        val ink = LightThemeTokens.colors.content
        // When both dice match, underline the first one: it doesn't matter which.
        val keptIndex = r.edgeDice.indexOf(kept)
        Row(
            Modifier.padding(vertical = 0.3f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.spacedBy(2f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            r.edgeDice.forEachIndexed { i, face ->
                val counts = i == keptIndex
                T(
                    face.toString(),
                    Style.Total,
                    dim = !counts,
                    modifier = if (counts) Modifier.drawBehind {
                        drawRect(ink, topLeft = Offset(0f, 13 * fontPx), size = Size(size.width, fontPx))
                    } else Modifier,
                )
            }
        }
        val mod = r.total - kept
        T(
            buildString {
                append(kept)
                if (mod != 0) append(if (mod > 0) " + $mod" else " - ${-mod}")
                append(" = ").append(r.total)
            },
            Style.MonoBig,
        )
        if (kept == 20 || kept == 1) T("NATURAL $kept", Style.Mono)
    }

    @Composable
    private fun Builder() {
        val e = expr
        Row(Modifier.fillMaxWidth().padding(vertical = 0.3f.gridUnitsAsDp()), verticalAlignment = Alignment.CenterVertically) {
            T("> ", Style.Mono, dim = true)
            T(
                text = e?.toString() ?: "d20",
                style = Style.Mono,
                dim = e == null || fresh,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (edge != Edge.None) T(if (edge == Edge.Advantage) "ADV" else "DIS", Style.Mono)
        }
    }

    @Composable
    private fun Pad() {
        val gap = Arrangement.spacedBy(0.4f.gridUnitsAsDp())
        // two rows of six rather than three of four: the row saved goes to the dice
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            listOf("d4", "d6", "d8", "d10", "d12", "d20").forEach { d -> PadKey(d, Modifier.weight(1f)) { add("+$d") } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            PadKey("d100", Modifier.weight(1f)) { add("+d100") }
            // 5e: advantage and disadvantage only ever apply to a d20 test
            val edgeOk = edgeAllowed
            PadKey("ADV", Modifier.weight(1f), selected = edge == Edge.Advantage, enabled = edgeOk) {
                edge = if (edge == Edge.Advantage) Edge.None else Edge.Advantage
            }
            PadKey("DIS", Modifier.weight(1f), selected = edge == Edge.Disadvantage, enabled = edgeOk) {
                edge = if (edge == Edge.Disadvantage) Edge.None else Edge.Disadvantage
            }
            listOf("-1", "+1", "+5").forEach { m -> PadKey(m, Modifier.weight(1f)) { add(m) } }
        }
        Spacer(Modifier.height(0.5f.gridUnitsAsDp()))
    }
}

/** Every roll, newest first. Tap one to roll it again. */
class LogScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val time = DateFormat.getTimeInstance(DateFormat.SHORT)
        ScrollPage(
            scroll,
            title = "Roll log",
            onBack = { goBack(null) },
            bottom = listOf(
                LightBarButton.Text("Dice: ${save.diceCamera.label}", onClick = { Store.cycleDiceCamera() }),
                LightBarButton.Text("Clear log", onClick = { Store.clearLog() }),
            ),
        ) {
            if (save.log.isEmpty()) T("Nothing rolled yet.", Style.Body, dim = true, modifier = Modifier.padding(top = 1f.gridUnitsAsDp()))
            save.log.forEach { r ->
                TapRow(onClick = { roll(RollRequest(r.expr, r.label, r.edge)) }) {
                    Column(Modifier.weight(1f)) {
                        T(r.label, Style.Body, maxLines = 1)
                        T("${r.expr}  ${r.detail}", Style.MonoSmall, dim = true, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        T(r.total.toString(), Style.MonoBig, align = TextAlign.End)
                        T(time.format(Date(r.at)), Style.Small, dim = true)
                    }
                }
            }
        }
    }
}
