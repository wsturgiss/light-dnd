package com.wsturgiss.dnd.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Ability(val short: String, val long: String) {
    STR("STR", "Strength"),
    DEX("DEX", "Dexterity"),
    CON("CON", "Constitution"),
    INT("INT", "Intelligence"),
    WIS("WIS", "Wisdom"),
    CHA("CHA", "Charisma"),
}

enum class Skill(val label: String, val ability: Ability) {
    Acrobatics("Acrobatics", Ability.DEX),
    AnimalHandling("Animal Handling", Ability.WIS),
    Arcana("Arcana", Ability.INT),
    Athletics("Athletics", Ability.STR),
    Deception("Deception", Ability.CHA),
    History("History", Ability.INT),
    Insight("Insight", Ability.WIS),
    Intimidation("Intimidation", Ability.CHA),
    Investigation("Investigation", Ability.INT),
    Medicine("Medicine", Ability.WIS),
    Nature("Nature", Ability.INT),
    Perception("Perception", Ability.WIS),
    Performance("Performance", Ability.CHA),
    Persuasion("Persuasion", Ability.CHA),
    Religion("Religion", Ability.INT),
    SleightOfHand("Sleight of Hand", Ability.DEX),
    Stealth("Stealth", Ability.DEX),
    Survival("Survival", Ability.WIS),
}

/** None → proficient → expertise, in the order a tap cycles them. */
enum class Training(val mark: String, val multiplier: Int) {
    None("[ ]", 0),
    Proficient("[x]", 1),
    Expertise("[*]", 2),
    ;

    fun next(): Training = entries[(ordinal + 1) % entries.size]
}

enum class Coin(val label: String, val inCopper: Int) {
    CP("cp", 1),
    SP("sp", 10),
    EP("ep", 50),
    GP("gp", 100),
    PP("pp", 1000),
}

@Serializable
data class Item(
    val id: Long,
    val name: String,
    val qty: Int = 1,
    /** Weight of one, from the SRD when the item came from there. */
    val weight: Double = 0.0,
    /** Which SRD list it came from ("equipment", "magic-items"), so it can link back. */
    val srdKind: String? = null,
    /** Disadvantage on Stealth, set on the item; null follows the SRD for armor of this name. */
    val stealthDisadvantage: Boolean? = null,
)

/** A feat taken, by its Look up name, and what it was taken for ("Cleric" for Magic Initiate). */
@Serializable
data class Feat(val name: String, val note: String = "")

/** Spell slots for one level: how many there are and how many are spent. */
@Serializable
data class Slots(val max: Int = 0, val used: Int = 0) {
    val left: Int get() = (max - used).coerceAtLeast(0)
}

@Serializable
data class Character(
    val name: String = "Nameless",
    val className: String = "Fighter",
    val level: Int = 1,
    /** From the builder; blank on characters made before it, or by hand. */
    val species: String = "",
    /** The species' sub-choice, as its Look up name ("Elven Lineage: High Elf"); blank for none. */
    val lineage: String = "",
    val background: String = "",
    /** Chosen at 3rd level; blank before. */
    val subclass: String = "",
    val feats: List<Feat> = emptyList(),
    val languages: List<String> = emptyList(),
    /** Armor, weapons and tools the character is trained with, as words ("Martial Weapons"). */
    val training: List<String> = emptyList(),
    val scores: Map<Ability, Int> = Ability.entries.associateWith { 10 },
    val saves: Set<Ability> = emptySet(),
    val skills: Map<Skill, Training> = emptyMap(),
    val maxHp: Int = 10,
    val hp: Int = 10,
    val tempHp: Int = 0,
    /** The worn armor, by name (one of [ARMOR]); null for none. */
    val wearing: String? = null,
    val shield: Boolean = false,
    /** AC from anything the sheet can't see: a magic ring, +1 armor, a spell. */
    val acBonus: Int = 0,
    /** AC as a typed number, from saves before AC was worked out. Read once by [Store], then dropped. */
    @SerialName("ac") val legacyAc: Int? = null,
    val speed: Int = 30,
    val conditions: Set<String> = emptySet(),
    val slots: List<Slots> = List(9) { Slots() },
    val spells: List<String> = emptyList(),
    val items: List<Item> = emptyList(),
    val coins: Map<Coin, Int> = emptyMap(),
) {
    val proficiency: Int get() = 2 + (level.coerceIn(1, 20) - 1) / 4

    fun score(a: Ability): Int = scores[a] ?: 10
    fun mod(a: Ability): Int = Math.floorDiv(score(a) - 10, 2)
    fun save(a: Ability): Int = mod(a) + if (a in saves) proficiency else 0
    fun training(s: Skill): Training = skills[s] ?: Training.None
    fun skill(s: Skill): Int = mod(s.ability) + proficiency * training(s).multiplier

    /** Worn armor, DEX, shield and Unarmored Defense, worked out; the Defense style's +1 in armor; plus [acBonus]. */
    val ac: Int
        get() = armorClass(ARMOR.find { it.name == wearing } ?: ARMOR.first(), shield, scores, classInfo(className)) +
            (if (wearing != null && feats.any { it.name == "Defense" }) 1 else 0) + acBonus

    /** The bag item is armor or a shield, so it can be worn. */
    fun wearable(item: Item): Boolean = item.name == "Shield" || ARMOR.any { it.name == item.name && it.weight != ArmorWeight.None }

    fun isWorn(item: Item): Boolean = item.name == wearing || (item.name == "Shield" && shield)

    /** Puts [item] on, or takes it off: one suit of armor at a time, and one shield. */
    fun toggleWorn(item: Item): Character = when {
        item.name == "Shield" -> copy(shield = !shield)
        item.name == wearing -> copy(wearing = null)
        else -> copy(wearing = item.name)
    }

    /** The item gives disadvantage on Stealth: as set on it, or as the SRD has armor of its name. */
    fun givesStealthDisadvantage(item: Item): Boolean =
        item.stealthDisadvantage ?: (ARMOR.find { it.name == item.name }?.stealthDisadvantage == true)

    /**
     * What gives disadvantage on Stealth now, by name: armor and shield only while worn (worn
     * armor that isn't in the bag goes by the SRD), anything else marked so while it's carried.
     */
    val stealthDisadvantageFrom: List<String>
        get() = buildList {
            if (wearing != null && items.none { it.name == wearing } && ARMOR.find { it.name == wearing }?.stealthDisadvantage == true) add(wearing)
            items.filter { givesStealthDisadvantage(it) && (if (wearable(it)) isWorn(it) else it.qty > 0) }.forEach { add(it.name) }
        }.distinct()

    val initiative: Int get() = mod(Ability.DEX)
    val passivePerception: Int get() = 10 + skill(Skill.Perception)

    val carried: Double get() = items.sumOf { it.weight * it.qty }
    val wealthInGp: Double get() = coins.entries.sumOf { (c, n) -> c.inCopper * n.toDouble() } / 100.0

    /** Damage eats temporary hit points first, and never takes HP below zero. */
    fun damage(amount: Int): Character {
        val fromTemp = minOf(tempHp, amount)
        return copy(tempHp = tempHp - fromTemp, hp = (hp - (amount - fromTemp)).coerceAtLeast(0))
    }

    fun heal(amount: Int): Character = copy(hp = (hp + amount).coerceAtMost(maxHp))

    /** Temporary hit points don't stack: you keep whichever is higher. */
    fun withTemp(amount: Int): Character = copy(tempHp = maxOf(tempHp, amount))

    fun longRest(): Character = copy(
        hp = maxHp,
        tempHp = 0,
        slots = slots.map { it.copy(used = 0) },
    )
}

/**
 * A character from before AC was worked out keeps the number it had. Whichever armor and shield
 * in the bag come closest to explaining it are put on; the rest becomes [Character.acBonus].
 */
fun Character.fromTypedAc(): Character {
    val typed = legacyAc ?: return this
    val bare = copy(legacyAc = null, acBonus = 0, wearing = null, shield = false)
    val armors = listOf<String?>(null) + items.map { it.name }.filter { n -> ARMOR.any { it.name == n && it.weight != ArmorWeight.None } }
    val shields = listOf(false) + listOfNotNull(true.takeIf { items.any { it.name == "Shield" } })
    val best = armors.flatMap { a -> shields.map { s -> bare.copy(wearing = a, shield = s) } }
        .minBy { kotlin.math.abs(typed - it.ac) }
    return best.copy(acBonus = typed - best.ac)
}

fun signed(n: Int): String = if (n >= 0) "+$n" else "$n"

/** Pact magic and multiclassing aside, this is the full caster table — a sensible default to edit from. */
val FULL_CASTER_SLOTS: List<List<Int>> = listOf(
    listOf(2), listOf(3), listOf(4, 2), listOf(4, 3), listOf(4, 3, 2),
    listOf(4, 3, 3), listOf(4, 3, 3, 1), listOf(4, 3, 3, 2), listOf(4, 3, 3, 3, 1), listOf(4, 3, 3, 3, 2),
    listOf(4, 3, 3, 3, 2, 1), listOf(4, 3, 3, 3, 2, 1), listOf(4, 3, 3, 3, 2, 1, 1), listOf(4, 3, 3, 3, 2, 1, 1),
    listOf(4, 3, 3, 3, 2, 1, 1, 1), listOf(4, 3, 3, 3, 2, 1, 1, 1), listOf(4, 3, 3, 3, 2, 1, 1, 1, 1),
    listOf(4, 3, 3, 3, 3, 1, 1, 1, 1), listOf(4, 3, 3, 3, 3, 2, 1, 1, 1), listOf(4, 3, 3, 3, 3, 2, 2, 1, 1),
)
