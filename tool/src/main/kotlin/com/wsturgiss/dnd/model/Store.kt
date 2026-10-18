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
import kotlinx.serialization.SerialName
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
    val characters: List<Character> = emptyList(),
    /** Index into [characters] of the one being played. */
    val active: Int = 0,
    /** The roll log is the table's, shared by every character. */
    val log: List<LoggedRoll> = emptyList(),
    val nextItemId: Long = 1,
    val diceCamera: DiceCamera = DiceCamera.Follow,
    /** Saves from before there were several characters kept just one, here. Read once, then dropped. */
    @SerialName("character") val single: Character? = null,
) {
    val character: Character get() = characters.getOrNull(active) ?: characters.firstOrNull() ?: Character()

    /** This file with the active character changed. */
    fun withCharacter(change: (Character) -> Character): SaveFile {
        val list = characters.ifEmpty { listOf(Character()) }
        val i = active.coerceIn(list.indices)
        return copy(characters = list.mapIndexed { j, c -> if (j == i) change(c) else c }, active = i)
    }
}

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
 * One file rather than Room: a few characters, the whole thing is a few
 * KB, and a readable file is what LightOS backup can use (see light-training's
 * PERSISTENCE.md). Writes go through a temp file and a rename, so a kill
 * mid-write leaves the previous save intact.
 *
 * The file lives in `files/shared/save/`, the one place the Tool Manager (and so light-web's
 * sync and LightOS backup) can see. A sync can replace it underneath the tool; [reload] reads it
 * back in (see ToolEntryPoint).
 */
object Store {
    private const val TAG = "DndStore"
    private const val LOG_LIMIT = 50

    /** The Tool Manager leaf: a folder under `files/shared`. */
    const val SHARED_DIR = "save"
    private const val FILE_NAME = "character.json"

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
        val dir = File(File(filesDir, "shared"), SHARED_DIR)
        dir.mkdirs()
        val f = File(dir, FILE_NAME)
        val old = File(filesDir, FILE_NAME)
        if (!f.exists() && old.exists() && !old.renameTo(f)) Log.e(TAG, "Couldn't move $FILE_NAME into shared/$SHARED_DIR")
        file = f
        _state.value = read(f) ?: SaveFile(characters = listOf(SAMPLE))
    }

    /**
     * Reads the save file again, after something other than this tool (a sync) has replaced it.
     * Waits for saves already on their way, so none of them lands after it and brings back the old
     * state. Does nothing before [init]: the first screen will read the new file anyway.
     */
    suspend fun reload() {
        val f = file ?: return
        writeLock.withLock { read(f)?.let { _state.value = it } }
    }

    private fun read(f: File): SaveFile? {
        if (!f.exists()) return null
        return runCatching { migrate(json.decodeFromString<SaveFile>(f.readText())) }
            .onFailure { Log.e(TAG, "Couldn't read ${f.name}; starting fresh", it) }
            .getOrNull()
    }

    private fun migrate(s: SaveFile): SaveFile {
        val list = when {
            s.single != null && s.characters.isEmpty() -> s.copy(characters = listOf(s.single), active = 0, single = null)
            s.characters.isEmpty() -> s.copy(characters = listOf(SAMPLE), active = 0)
            else -> s.copy(active = s.active.coerceIn(s.characters.indices), single = null)
        }
        return list.copy(characters = list.characters.map { it.fromTypedAc() })
    }

    fun edit(change: (Character) -> Character) {
        _state.update { it.withCharacter(change) }
        save()
    }

    /** Adds [c] and plays as them. */
    fun addCharacter(c: Character) {
        _state.update { it.copy(characters = it.characters + c, active = it.characters.size) }
        save()
    }

    /**
     * Puts [c] in place of the active character. The save as it stood is kept first, beside it, as
     * `character.json.bak-<time>`; [reload] only ever reads `character.json`, so a sync leaves it be.
     */
    fun replaceActive(c: Character) {
        val before = _state.value
        _state.update { s -> s.copy(characters = s.characters.mapIndexed { i, old -> if (i == s.active) c else old }.ifEmpty { listOf(c) }) }
        save(backup = before)
    }

    fun switchTo(index: Int) {
        _state.update { it.copy(active = index.coerceIn(it.characters.indices)) }
        save()
    }

    /** Removes the active character. The last one goes back to a blank sheet rather than nothing. */
    fun deleteActive() {
        _state.update { s ->
            val rest = s.characters.filterIndexed { i, _ -> i != s.active }
            if (rest.isEmpty()) s.copy(characters = listOf(Character(name = "New hero")), active = 0)
            else s.copy(characters = rest, active = (s.active - 1).coerceAtLeast(0))
        }
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
            s.withCharacter { it.copy(items = items) }.copy(nextItemId = s.nextItemId + 1)
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

    private fun save(backup: SaveFile? = null) {
        val f = file ?: return
        val snapshot = _state.value
        scope.launch {
            writeLock.withLock {
                if (backup != null) {
                    runCatching {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                        File(f.parentFile, "${f.name}.bak-$stamp").writeText(json.encodeToString(SaveFile.serializer(), backup))
                    }.onFailure {
                        // no backup, no replace: put the old save back unless something's changed since
                        Log.e(TAG, "Backup failed; not replacing", it)
                        _state.compareAndSet(snapshot, backup)
                        return@withLock
                    }
                }
                runCatching {
                    // Hidden, so the Tool Manager doesn't list a half-written file.
                    val tmp = File(f.parentFile, ".${f.name}.tmp")
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
        species = "Halfling",
        background = "Criminal",
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
        wearing = "Leather Armor",
        items = listOf(
            Item(-6, "Leather Armor", 1, 10.0, "equipment"),
            Item(-1, "Shortsword", 1, 2.0, "equipment"),
            Item(-2, "Shortbow", 1, 2.0, "equipment"),
            Item(-3, "Arrows", 20, 0.05),
            Item(-4, "Thieves' Tools", 1, 1.0, "equipment"),
            Item(-5, "Rope", 1, 5.0, "equipment"),
        ),
        coins = mapOf(Coin.GP to 23, Coin.SP to 7),
    )
}
