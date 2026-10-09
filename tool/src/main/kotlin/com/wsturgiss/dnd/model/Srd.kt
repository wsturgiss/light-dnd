package com.wsturgiss.dnd.model

import com.thelightphone.sdk.SealedLightContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** One SRD entry as `data/build_srd.py` writes it. */
@Serializable
data class Entry(
    @SerialName("n") val name: String,
    @SerialName("m") val meta: String,
    @SerialName("b") val body: String,
    /** Spell level, 0 for cantrips. */
    @SerialName("l") val level: Int? = null,
    /** Classes that can cast it. */
    @SerialName("c") val classes: List<String> = emptyList(),
    /** Monster challenge rating. */
    @SerialName("cr") val cr: Double? = null,
    /** Equipment weight in pounds. */
    @SerialName("w") val weight: Double? = null,
)

enum class Kind(val file: String, val label: String, val one: String) {
    Spells("spells", "Spells", "spell"),
    Monsters("monsters", "Monsters", "monster"),
    Conditions("conditions", "Conditions", "condition"),
    MagicItems("magic-items", "Magic items", "magic item"),
    Equipment("equipment", "Equipment", "item"),
    Rules("rules", "Rules", "rule"),
    ;

    companion object {
        fun ofFile(file: String?): Kind? = entries.find { it.file == file }
    }
}

/** The bundled SRD 5.2, loaded a list at a time on first use and kept for the life of the process. */
object Srd {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<Kind, List<Entry>>()

    fun load(context: SealedLightContext, kind: Kind): List<Entry> = cache.getOrPut(kind) {
        json.decodeFromString(context.readAsset("srd/${kind.file}.json").decodeToString())
    }

    fun find(context: SealedLightContext, kind: Kind, name: String): Entry? =
        load(context, kind).find { it.name.equals(name, ignoreCase = true) }

    /**
     * Names that start with the query come first, then names containing it, then
     * entries that only mention it in their text.
     */
    fun search(context: SealedLightContext, kinds: List<Kind>, query: String): List<Pair<Kind, Entry>> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val all = kinds.flatMap { k -> load(context, k).map { k to it } }
        val starts = all.filter { it.second.name.startsWith(q, ignoreCase = true) }
        val contains = all.filter { it.second.name.contains(q, ignoreCase = true) } - starts.toSet()
        val inText = if (q.length >= 3) {
            all.filter { it.second.body.contains(q, ignoreCase = true) } - (starts + contains).toSet()
        } else {
            emptyList()
        }
        return starts + contains + inText
    }
}
