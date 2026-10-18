package com.wsturgiss.dnd.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a new character chooses from, as `data/build_srd.py` writes `creation.json`. Every name
 * is a Look up entry's name, so anything chosen can be read in full.
 */
@Serializable
data class Creation(
    val classes: Map<String, ClassData>,
    val species: Map<String, SpeciesData>,
    val backgrounds: Map<String, BackgroundData>,
    /** By kind: "origin", "general", "fighting-style", "epic-boon". */
    val feats: Map<String, List<String>>,
    /** "standard" and "rare". */
    val languages: Map<String, List<String>>,
) {
    val originFeats: List<String> get() = feats["origin"].orEmpty()
    val fightingStyles: List<String> get() = feats["fighting-style"].orEmpty()
}

@Serializable
data class ClassData(
    val hitDie: Int,
    val saves: List<String>,
    val skills: List<String>,
    val skillCount: Int,
    val training: List<String>,
    /** A tool or instrument to choose, in words ("Three musical instruments of your choice"). */
    val toolChoice: String? = null,
    /** The SRD's one subclass for the class. */
    val subclass: String? = null,
    /** Index 0 is level 1. */
    val levels: List<LevelData>,
)

@Serializable
data class LevelData(
    /** Class features gained at this level. */
    @SerialName("f") val features: List<String>,
    /** The SRD subclass's features gained at this level. */
    @SerialName("sf") val subclassFeatures: List<String>,
    @SerialName("c") val cantrips: Int,
    @SerialName("p") val prepared: Int,
)

@Serializable
data class SpeciesData(
    val size: String,
    val traits: List<String>,
    /** What the sub-choice is called ("Elven Lineage"), or null when there's none. */
    val choice: String? = null,
    val options: List<String> = emptyList(),
)

@Serializable
data class BackgroundData(
    val feat: String,
    /** Which list, for Magic Initiate. */
    val note: String? = null,
    val tools: List<String> = emptyList(),
    val toolChoice: String? = null,
)

/** A Look up name without the class or subclass a shared name was tagged with: "Spellcasting (Wizard)" → "Spellcasting". */
fun plainName(name: String): String = name.substringBefore(" (")

/** "Elven Lineage: High Elf" → "High Elf". */
fun choiceName(name: String): String = name.substringAfter(": ")

/** Features from level 1 to [level], with the subclass's once there is one. Pairs of (level, Look up name). */
fun ClassData.featuresTo(level: Int, withSubclass: Boolean): List<Pair<Int, String>> =
    levels.take(level).flatMapIndexed { i, l ->
        (l.features + if (withSubclass) l.subclassFeatures else emptyList()).map { i + 1 to it }
    }

/** The class level's feature that has you pick a fighting style, if this level has one. */
fun LevelData.fightingStyle(): Boolean = features.any { plainName(it) == "Fighting Style" }
