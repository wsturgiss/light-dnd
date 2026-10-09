package com.wsturgiss.dnd.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.wsturgiss.dnd.model.DiceCamera
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * One die on the stage: its type, the face it lands on (an index into one of the strips in
 * `assets/dice/faces/`: `d20`, or `pct10` / `pct1` for a percentile pair), and whether it is the one that
 * doesn't count.
 */
data class StageDie(val sides: Int, val faces: String, val face: Int, val dim: Boolean = false)

/** One die's throw: per frame, how long it shows, how far the die still has to travel, and how high it is. */
@Serializable
internal data class ThrowPlan(val ms: List<Int>, val dist: List<Float>, val h: List<Float>)

@Serializable
internal data class Consts(
    @SerialName("REACH") val reach: Int,
    @SerialName("SPACING") val spacing: Int,
    @SerialName("ROW") val row: Int,
    @SerialName("ZOOM") val zoom: Float,
    @SerialName("SLACK") val slack: Int,
    @SerialName("H_TOP") val hTop: Int,
    @SerialName("FRAME_MS") val frameMs: Int,
    @SerialName("TRAVEL_H") val travelH: Int,
    @SerialName("SCREEN_W") val screenW: Int,
    @SerialName("MAX_DICE") val maxDice: Int,
)

@Serializable
internal data class DiceIndex(
    val sprite: Int,
    val bins: Int,
    /** From above, per die type: per direction bin, the throws. */
    val top: Map<String, List<List<ThrowPlan>>>,
    /** From the side, per die type: thrown right, then left; the throws. */
    val side: Map<String, List<List<ThrowPlan>>>,
    val face: Int,
    /** How far a landed die reaches from its centre. */
    val landedHalf: Int,
    val consts: Consts,
)

/**
 * The roll animation's art, made by `data/dice/export.py`: each die's throw as sprites, and the
 * landed faces. The scene itself (where each die is, the camera, the stacking) is worked out here,
 * a port of `scene()` in `data/dice/tumble.py`, so the two must change together.
 */
object DiceClips {
    private var read: ((String) -> ByteArray)? = null
    private val bitmaps = HashMap<String, ImageBitmap>()
    private val index by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString<DiceIndex>(read!!("dice/index.json").decodeToString())
    }

    /** The die types there's art for; a d100 is a pair of d10s. */
    val SIDES = setOf(4, 6, 8, 10, 12, 20)

    /** More dice than this show their results without animation. */
    val maxDice get() = index.consts.maxDice

    /** Idempotent, like `Store.init`. */
    fun init(readAsset: (String) -> ByteArray) {
        if (read == null) read = readAsset
    }

    internal fun bitmap(path: String) = bitmaps.getOrPut(path) {
        val bytes = read!!(path)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
    }

    internal val art: DiceIndex get() = index

    /**
     * Where [n] landed dice go, and at what scale, in a stage [maxH] px tall where they must fit the
     * [room] px left of [landEnd]. Text beside them can centre on [Placement.groupMidY].
     */
    fun placement(n: Int, camera: DiceCamera, room: Int, maxH: Int, landEnd: Int): Placement {
        val shape = SceneShape(n, camera, index)
        val box = shape.landedBox
        // the landed dice decide the scale, up to 5x; a throw may run off the stage, which crops it
        val s = (MAX_SCALE downTo 1).first { it == 1 || (box.width * it <= room && box.height * it <= maxH) }
        val ox = landEnd - box.right * s
        // centre the landed dice, but keep the whole canvas on the stage when it fits
        val want = maxH / 2 - (box.top + box.bottom) * s / 2
        val spare = maxH - shape.h * s
        val oy = want.coerceIn(min(0, spare), max(0, spare))
        return Placement(s, IntOffset(ox, oy), box.width * s, oy + (box.top + box.bottom) * s / 2)
    }
}

/** The biggest whole-number scale dice are drawn at. */
private const val MAX_SCALE = 5

/** A scene on the stage: its scale, the canvas's top left, and the landed dice's width and middle. */
data class Placement(val scale: Int, val origin: IntOffset, val groupWidth: Int, val groupMidY: Int)

private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Python's round(): halves to even, as tumble.py rounds. */
private fun pyRound(v: Double) = round(v).toInt()
private fun pyRound(v: Float) = round(v.toDouble()).toInt()

/** What depends only on how many dice and which camera: the canvas, and where the dice rest. */
private class SceneShape(n: Int, camera: DiceCamera, private val index: DiceIndex) {
    private val c = index.consts
    val top = camera != DiceCamera.Travel
    val follow = camera == DiceCamera.Follow
    /** Top row first: one row for 1 or 2 dice, then two over one, two over two. */
    private val rows: List<List<Int>> = if (n <= 2) listOf((0 until n).toList()) else listOf(listOf(0, 1), (2 until n).toList())
    /** Each die's resting place, relative to the canvas's middle column and [restY]. */
    val rest = Array(n) { IntOffset.Zero }
    val w: Int
    val h: Int
    val restY: Int
    var slackX = 0f
        private set
    var slackY = 0f
        private set

    init {
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { j, k ->
                val ry = if (top) pyRound((r - (rows.size - 1) / 2.0) * c.row) else (r - (rows.size - 1)) * c.row
                rest[k] = IntOffset(pyRound((j - (row.size - 1) / 2.0) * c.spacing), ry)
            }
        }
        val spanY = c.row * (rows.size - 1)
        val reach = 2 * ceil(c.reach * (1 + c.zoom)).toInt()
        when {
            follow -> {
                val spanX = c.spacing * (rows.maxOf { it.size } - 1)
                w = min(c.screenW, reach + 6 + 2 * c.slack + spanX)
                h = if (rows.size == 1) 132 else 190
                slackX = min(c.slack.toFloat(), (w - spanX - reach) / 2f - 2)
                slackY = min(c.slack.toFloat(), (h - spanY - reach) / 2f - 2)
            }
            top -> {
                w = if (n == 1) 176 else c.screenW
                h = reach + 6 + spanY
            }
            else -> {
                w = if (n == 1) 176 else c.screenW
                h = c.travelH + spanY
            }
        }
        restY = if (top) h / 2 else h - 3 - c.reach
    }

    /** Where the landed dice sit on the canvas. */
    val landedBox: Box
        get() = index.landedHalf.let { half ->
            Box(
                w / 2 + rest.minOf { it.x } - half,
                restY + rest.minOf { it.y } - half,
                w / 2 + rest.maxOf { it.x } + half,
                restY + rest.maxOf { it.y } + half,
            )
        }
}

/** One roll: each die's throw, direction and clock, and the follow camera's path. */
private class Scene(private val dice: List<StageDie>, camera: DiceCamera, random: Random) {
    private val index = DiceClips.art
    private val c = index.consts
    val shape = SceneShape(dice.size, camera, index)
    private val n = dice.size
    val step = c.frameMs
    val sprite = index.sprite

    private val dirX = FloatArray(n)
    private val dirY = FloatArray(n)
    private val plans: List<ThrowPlan>
    val strips: List<ImageBitmap>
    /** Per die, when each frame starts; the last entry is when it lands. */
    private val starts: List<IntArray>
    /** When the last die lands. */
    val end: Int
    /** Drawing order, fixed for the whole throw. */
    val stack: List<Int>
    private val offX: FloatArray
    private val offY: FloatArray

    init {
        val phi = when (camera) {
            DiceCamera.Follow -> random.nextDouble(0.0, 2 * PI)
            // thrown in from the left or right edge, give or take 15°
            DiceCamera.TravelTop -> (if (random.nextBoolean()) 0.0 else PI) + Math.toRadians(random.nextDouble(-15.0, 15.0))
            else -> if (random.nextBoolean()) 0.0 else PI
        }
        val ps = ArrayList<ThrowPlan>(n)
        val bs = ArrayList<ImageBitmap>(n)
        dice.forEachIndexed { k, die ->
            if (shape.top) {
                // each die heads its own way, up to 25° off the throw (15° when it must cross the screen),
                // snapped to the nearest direction there's art for
                val spread = if (shape.follow) 25.0 else 15.0
                val a = if (n > 1) phi + Math.toRadians(random.nextDouble(-spread, spread)) else phi
                val bin = Math.floorMod((a / (2 * PI / index.bins)).roundToInt(), index.bins)
                val snapped = 2 * PI * bin / index.bins
                dirX[k] = cos(snapped).toFloat()
                dirY[k] = sin(snapped).toFloat()
                val throws = index.top.getValue(die.sides.toString())[bin]
                val v = random.nextInt(throws.size)
                ps += throws[v]
                bs += DiceClips.bitmap("dice/top/d${die.sides}-b$bin-$v.png")
            } else {
                val right = phi == 0.0
                dirX[k] = if (right) 1f else -1f
                val throws = index.side.getValue(die.sides.toString())[if (right) 0 else 1]
                val v = random.nextInt(throws.size)
                ps += throws[v]
                bs += DiceClips.bitmap("dice/side/d${die.sides}-${if (right) "r" else "l"}-$v.png")
            }
        }
        plans = ps
        strips = bs
        starts = plans.map { p -> IntArray(p.ms.size + 1).also { s -> p.ms.forEachIndexed { i, ms -> s[i + 1] = s[i] + ms } } }
        end = starts.maxOf { it.last() }
        // from above, the die highest at the start is nearest, so it goes on top; from the side, back row first
        stack = if (shape.top) (0 until n).sortedBy { plans[it].h[0] } else (0 until n).toList()

        // follow: a camera operator, a slightly underdamped spring chasing the middle of the dice, never letting
        // them get more than the slack off-centre, smoothstepping fully onto them over the last 0.7 s
        val ticks = (end + step) / step
        offX = FloatArray(ticks)
        offY = FloatArray(ticks)
        if (shape.follow) {
            val w0 = random.nextDouble(0.28, 0.4).toFloat()
            val zeta = random.nextDouble(0.55, 0.75).toFloat()
            val (m0x, m0y) = mid(0)
            val along = random.nextDouble(-4.0, 4.0).toFloat()
            var cx = m0x + cos(phi).toFloat() * along + random.nextDouble(-3.0, 3.0).toFloat()
            var cy = m0y + sin(phi).toFloat() * along + random.nextDouble(-3.0, 3.0).toFloat()
            var vx = 0f
            var vy = 0f
            for (ti in 0 until ticks) {
                val t = ti * step
                val (px, py) = mid(t)
                vx += w0 * w0 * (px - cx) - 2 * zeta * w0 * vx
                vy += w0 * w0 * (py - cy) - 2 * zeta * w0 * vy
                cx += vx
                cy += vy
                val u = ((t - (end - 700)) / 700f).coerceIn(0f, 1f)
                val settle = u * u * (3 - 2 * u)
                offX[ti] = ((px - cx) * (1 - settle)).coerceIn(-shape.slackX, shape.slackX)
                offY[ti] = ((py - cy) * (1 - settle)).coerceIn(-shape.slackY, shape.slackY)
            }
        }
    }

    /** Die k's frame at time t, or -1 once it has landed. */
    fun frame(k: Int, t: Int): Int {
        val s = starts[k]
        var i = 0
        while (i + 1 < s.size && s[i + 1] <= t) i++
        return if (i >= plans[k].ms.size) -1 else i
    }

    /** Where die k is relative to its resting place: it comes in from behind along its travel. */
    private fun pos(k: Int, t: Int): Pair<Float, Float> {
        val f = frame(k, t)
        val d = if (f < 0) 0f else plans[k].dist[f]
        return -dirX[k] * d to -dirY[k] * d
    }

    private fun mid(t: Int): Pair<Float, Float> {
        var x = 0f
        var y = 0f
        for (k in 0 until n) pos(k, t).let { x += it.first; y += it.second }
        return x / n to y / n
    }

    /** Die k's centre on the canvas at time t. */
    fun centre(k: Int, t: Int): IntOffset {
        val f = frame(k, t)
        var (px, py) = pos(k, t)
        if (shape.follow) {
            // the camera is aimed at the middle of the dice, a spring's lag behind it
            val ti = t / step
            val (mx, my) = mid(t)
            px -= mx - (if (ti < offX.size) offX[ti] else 0f)
            py -= my - (if (ti < offY.size) offY[ti] else 0f)
        }
        val h = if (f < 0) 0f else plans[k].h[f]
        val r = shape.rest[k]
        var cx = shape.w / 2 + pyRound(r.x + px)
        var cy = shape.restY + pyRound(r.y + py) - (if (f >= 0 && !shape.top) pyRound(h) else 0)
        if (shape.top && f >= 0) {
            // follow: one die can run ahead of the group, so keep it in shot. travel-top: it may come in over the
            // side edge, but its angled throw mustn't leave the top or bottom
            val m = ceil(c.reach * (1 + c.zoom * h / c.hTop)).toInt() + 2
            if (shape.follow) cx = cx.coerceIn(m, shape.w - 1 - m)
            cy = cy.coerceIn(m, shape.h - 1 - m)
        }
        return IntOffset(cx, cy)
    }
}

/**
 * Plays a roll of up to [DiceClips.maxDice] dice thrown together, each showing its number as it
 * lands, then one haptic tick when the last lands. Tap to skip. A new [rollKey] starts a new throw.
 *
 * Drawn at the whole-number scale [DiceClips.placement] picks, so pixels stay square. The dice land
 * up against [landEnd] px, within the [room] px left of it; the throws may cross the rest of the
 * stage, so lay text over it there, hidden until they land.
 */
@Composable
fun DiceStage(
    dice: List<StageDie>,
    camera: DiceCamera,
    rollKey: Any,
    landEnd: Int,
    room: Int,
    modifier: Modifier = Modifier,
    onLanded: () -> Unit,
) {
    val scene = remember(rollKey, camera) { Scene(dice, camera, Random.Default) }
    var elapsed by remember(rollKey, camera) { mutableLongStateOf(0L) }
    val landed = elapsed >= scene.end
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(rollKey, camera) {
        val start = withFrameMillis { it }
        while (elapsed < scene.end) {
            // tumble.py's clock: everything moves on 45 ms ticks
            val t = withFrameMillis { it } - start
            elapsed = t - t % scene.step
        }
    }
    LaunchedEffect(rollKey, camera, landed) {
        if (landed) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onLanded()
        }
    }

    Canvas(
        modifier
            .clipToBounds()
            .pointerInput(rollKey) { detectTapGestures { elapsed = scene.end.toLong() } },
    ) {
        val p = DiceClips.placement(dice.size, camera, room, size.height.toInt(), landEnd)
        val s = p.scale
        val t = min(elapsed, scene.end.toLong()).toInt()
        val sprite = scene.sprite
        val face = DiceClips.art.face
        for (k in scene.stack) {
            val die = dice[k]
            val f = scene.frame(k, t)
            val (cx, cy) = scene.centre(k, t)
            if (f >= 0) {
                drawImage(
                    scene.strips[k],
                    srcOffset = IntOffset(0, f * sprite),
                    srcSize = IntSize(sprite, sprite),
                    dstOffset = IntOffset(p.origin.x + (cx - sprite / 2) * s, p.origin.y + (cy - sprite / 2) * s),
                    dstSize = IntSize(sprite * s, sprite * s),
                    filterQuality = FilterQuality.None,
                )
            } else {
                drawImage(
                    DiceClips.bitmap("dice/faces/${die.faces}.png"),
                    srcOffset = IntOffset(0, die.face * face),
                    srcSize = IntSize(face, face),
                    dstOffset = IntOffset(p.origin.x + (cx - face / 2) * s, p.origin.y + (cy - face / 2) * s),
                    dstSize = IntSize(face * s, face * s),
                    alpha = if (landed && die.dim) 0.35f else 1f,
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}
