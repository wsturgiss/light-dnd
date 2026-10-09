package com.wsturgiss.dnd.model

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One line in the roll log. */
@Serializable
data class LoggedRoll(
    val label: String,
    val expr: String,
    val detail: String,
    val total: Int,
    val edge: Edge = Edge.None,
    val natural: Int? = null,
    /** Both d20s when rolled with advantage or disadvantage; [natural] is the one kept. */
    val edgeDice: List<Int> = emptyList(),
    val at: Long = System.currentTimeMillis(),
)

@Serializable
data class SaveFile(
    val character: Character = Character(),
    val log: List<LoggedRoll> = emptyList(),
    val nextItemId: Long = 1,
    val diceCamera: DiceCamera = DiceCamera.Follow,
)

/** How the roller animates a roll. Off shows the result straight away. */
@Serializable
enum class DiceCamera(val label: String) {
    /** Seen from above, the camera chasing the die and settling on it. */
    Follow("follow"),
    /** A fixed camera from the side: thrown in from one edge, bouncing up the screen. */
    Travel("bounce"),
    /** A fixed camera from above: thrown in from one edge, bounces coming toward you. */
    TravelTop("travel"),
    Off("off"),
}

/**
 * Everything the tool remembers, in one small JSON file.
 *
 * One file rather than Room: it is a single character, the whole thing is a few
 * KB, and a readable file is what LightOS backup can use (see light-training's
 * PERSISTENCE.md). Writes go through a temp file and a rename, so a kill
 * mid-write leaves the previous save intact.
 */
object Store {
    private const val TAG = "DndStore"
    private const val LOG_LIMIT = 50

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private var file: File? = null

    private val _state = MutableStateFlow(SaveFile())
    val state: StateFlow<SaveFile> = _state.asStateFlow()

    val character: Character get() = _state.value.character

    /** Idempotent; every screen calls it, the first one loads. */
    fun init(filesDir: File) {
        if (file != null) return
        val f = File(filesDir, "character.json")
        file = f
        if (f.exists()) {
            runCatching { json.decodeFromString<SaveFile>(f.readText()) }
                .onSuccess { _state.value = it }
                .onFailure { Log.e(TAG, "Couldn't read ${f.name}; starting fresh", it) }
        } else {
            _state.value = SaveFile(character = SAMPLE)
        }
    }

    fun edit(change: (Character) -> Character) {
        _state.update { it.copy(character = change(it.character)) }
        save()
    }

    fun addItem(name: String, weight: Double = 0.0, srdKind: String? = null) {
        _state.update { s ->
            val existing = s.character.items.find { it.name.equals(name, ignoreCase = true) }
            val items = if (existing != null) {
                s.character.items.map { if (it.id == existing.id) it.copy(qty = it.qty + 1) else it }
            } else {
                s.character.items + Item(s.nextItemId, name, 1, weight, srdKind)
            }
            s.copy(character = s.character.copy(items = items), nextItemId = s.nextItemId + 1)
        }
        save()
    }

    fun record(label: String, roll: Roll): LoggedRoll {
        val entry = LoggedRoll(label, roll.expr.toString(), roll.detail, roll.total, roll.edge, roll.natural, roll.edgeDice)
        _state.update { it.copy(log = (listOf(entry) + it.log).take(LOG_LIMIT)) }
        save()
        return entry
    }

    fun cycleDiceCamera() {
        _state.update { it.copy(diceCamera = DiceCamera.entries[(it.diceCamera.ordinal + 1) % DiceCamera.entries.size]) }
        save()
    }

    fun clearLog() {
        _state.update { it.copy(log = emptyList()) }
        save()
    }

    private fun save() {
        val f = file ?: return
        val snapshot = _state.value
        scope.launch {
            writeLock.withLock {
                runCatching {
                    val tmp = File(f.parentFile, "${f.name}.tmp")
                    tmp.writeText(json.encodeToString(SaveFile.serializer(), snapshot))
                    check(tmp.renameTo(f)) { "rename failed" }
                }.onFailure { Log.e(TAG, "Save failed", it) }
            }
        }
    }

    /** A first character to poke at, so no screen starts empty. Edit or overwrite it. */
    private val SAMPLE = Character(
        name = "Wren Ashdown",
        className = "Rogue",
        level = 3,
        scores = mapOf(
            Ability.STR to 10, Ability.DEX to 17, Ability.CON to 14,
            Ability.INT to 12, Ability.WIS to 13, Ability.CHA to 8,
        ),
        saves = setOf(Ability.DEX, Ability.INT),
        skills = mapOf(
            Skill.Stealth to Training.Expertise,
            Skill.SleightOfHand to Training.Expertise,
            Skill.Perception to Training.Proficient,
            Skill.Acrobatics to Training.Proficient,
            Skill.Investigation to Training.Proficient,
            Skill.Deception to Training.Proficient,
        ),
        maxHp = 24,
        hp = 24,
        ac = 14,
        items = listOf(
            Item(-1, "Shortsword", 1, 2.0, "equipment"),
            Item(-2, "Shortbow", 1, 2.0, "equipment"),
            Item(-3, "Arrows", 20, 0.05),
            Item(-4, "Thieves' Tools", 1, 1.0, "equipment"),
            Item(-5, "Rope", 1, 5.0, "equipment"),
        ),
        coins = mapOf(Coin.GP to 23, Coin.SP to 7),
    )
}
