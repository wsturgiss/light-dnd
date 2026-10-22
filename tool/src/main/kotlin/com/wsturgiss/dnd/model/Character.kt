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

/** Which rules the sheet follows where the 2014 and 2024 books differ. 2014 unless set. */
@Serializable
enum class Edition(val label: String) {
    @SerialName("2014") E2014("2014"),
    @SerialName("2024") E2024("2024"),
    ;

    fun next(): Edition = entries[(ordinal + 1) % entries.size]
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
    /** In hand or on, as D&D Beyond had it. Armor and shields go by [Character.wearing] and [Character.shield]. */
    val equipped: Boolean = false,
    /** The SRD weapon it is, for a magic weapon not named for one ("Weapon +1"); null goes by the name. */
    val weapon: String? = null,
    /** +N to attack and damage rolls; null reads it from the name ("Longsword, +1"). */
    val magicBonus: Int? = null,
    /** Damage dice on top of the weapon's on a hit ("2d6"), doubled on a critical hit like the rest. */
    val extraDamage: String = "",
)

/** A roll of a d20 the sheet makes, so it can say what gives it advantage or disadvantage. */
sealed interface D20Test {
    val ability: Ability

    data class Check(override val ability: Ability, val skill: Skill? = null) : D20Test
    data class Save(override val ability: Ability) : D20Test
    data class AttackRoll(override val ability: Ability) : D20Test
    /** A Dexterity check, with features of its own. */
    data object Initiative : D20Test {
        override val ability = Ability.DEX
    }
}

/** A class taken after the first, multiclassing: how many levels in it, and its subclass. */
@Serializable
data class ClassLevel(val name: String, val level: Int, val subclass: String = "")

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
    /** The class taken first; with [multiclass], the one whose 1st level had the full hit die. */
    val className: String = "Fighter",
    /** Total, every class's levels together. */
    val level: Int = 1,
    /** Classes beyond [className], for a multiclassed character; empty for one class. */
    val multiclass: List<ClassLevel> = emptyList(),
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
    /** Walking speed as the species (or the sheet) has it; [speed] is after armor. */
    @SerialName("speed") val baseSpeed: Int = 30,
    val conditions: Set<String> = emptySet(),
    val slots: List<Slots> = List(9) { Slots() },
    val spells: List<String> = emptyList(),
    val items: List<Item> = emptyList(),
    val coins: Map<Coin, Int> = emptyMap(),
    val inspiration: Boolean = false,
    /** Free text: what an import couldn't fit on the sheet, backstory, anything. */
    val notes: String = "",
    val rules: Edition = Edition.E2014,
) {
    val proficiency: Int get() = 2 + (level.coerceIn(1, 20) - 1) / 4

    /** Levels in [className]: the total, less those in [multiclass]. */
    val mainLevel: Int get() = (level - multiclass.sumOf { it.level }).coerceAtLeast(1)

    fun levelIn(cls: String): Int = (if (cls == className) mainLevel else 0) + multiclass.filter { it.name == cls }.sumOf { it.level }

    fun subclassOf(cls: String): String = if (cls == className) subclass else multiclass.firstOrNull { it.name == cls }?.subclass.orEmpty()

    /** "Fighter", or "Fighter 3 / Wizard 2". */
    val classLabel: String
        get() = if (multiclass.isEmpty()) className else (listOf(ClassLevel(className, mainLevel)) + multiclass).joinToString(" / ") { "${it.name} ${it.level}" }

    /** Extra hit points every level: Dwarven Toughness, the Tough feat. */
    val hpPerLevelBonus: Int
        get() = (speciesInfo(species)?.hpPerLevel ?: 0) + if (feats.any { it.name == TOUGH_FEAT }) TOUGH_HP_PER_LEVEL else 0

    /**
     * Max HP by the fixed table: the first class's hit die in full at 1st level, then each level's
     * average (a multiclass's in its own die), CON every level, never less than 1 a level, plus
     * [hpPerLevelBonus]. What the sheet would have without rolled HP or anything typed in.
     */
    val fixedMaxHp: Int
        get() {
            val con = mod(Ability.CON)
            val dice = List(mainLevel) { classInfo(className) } + multiclass.flatMap { cl -> List(cl.level) { classInfo(cl.name) } }
            return dice.mapIndexed { i, cls ->
                maxOf(1, (if (i == 0) cls?.hitDie ?: 8 else cls?.averageHit ?: 5) + con) + hpPerLevelBonus
            }.sum()
        }

    /** Max HP against [fixedMaxHp] when they differ: "30 (36 base, -6 adjustment)". */
    val hpBreakdown: String?
        get() = (maxHp - fixedMaxHp).takeIf { it != 0 }?.let { "$maxHp ($fixedMaxHp base, ${signed(it)} adjustment)" }

    /** These feats instead: taking Tough adds 2 HP for every level so far, dropping it takes them off. */
    fun withFeats(new: List<Feat>): Character {
        fun tough(fs: List<Feat>) = fs.any { it.name == TOUGH_FEAT }
        val change = when {
            tough(new) && !tough(feats) -> TOUGH_HP_PER_LEVEL * level
            !tough(new) && tough(feats) -> -TOUGH_HP_PER_LEVEL * level
            else -> 0
        }
        val max = (maxHp + change).coerceAtLeast(1)
        return copy(feats = new, maxHp = max, hp = (hp + change).coerceIn(0, max))
    }

    fun score(a: Ability): Int = scores[a] ?: 10
    fun mod(a: Ability): Int = Math.floorDiv(score(a) - 10, 2)
    fun save(a: Ability): Int = mod(a) + if (a in saves) proficiency else 0
    fun training(s: Skill): Training = skills[s] ?: Training.None
    fun skill(s: Skill): Int =
        mod(s.ability) + proficiency * training(s).multiplier + if (training(s) == Training.None) untrainedBonus(D20Test.Check(s.ability, s)) else 0

    /** An ability check with no skill: the modifier, and [untrainedBonus]. */
    fun check(a: Ability): Int = mod(a) + untrainedBonus(D20Test.Check(a))

    /** Bard, 2nd level: half the proficiency bonus, rounded down, on checks without proficiency (2024: skill checks only). */
    val jackOfAllTrades: Boolean get() = levelIn("Bard") >= 2

    /**
     * Champion's Remarkable Athlete. 2014, 7th level: half the proficiency bonus, rounded up, on
     * STR, DEX and CON checks without proficiency. 2024, 3rd level: advantage on Initiative and Athletics.
     */
    val remarkableAthlete: Boolean
        get() = subclassOf("Fighter") == "Champion" && levelIn("Fighter") >= if (rules == Edition.E2014) 7 else 3

    /**
     * What a check without proficiency adds: Jack of All Trades, or 2014's Remarkable Athlete.
     * Both are the proficiency bonus, which a roll only ever adds once, so the better of the two.
     */
    fun untrainedBonus(test: D20Test): Int {
        if (test !is D20Test.Check && test != D20Test.Initiative) return 0
        val skill = (test as? D20Test.Check)?.skill
        val jack = if (jackOfAllTrades && (rules == Edition.E2014 || skill != null)) proficiency / 2 else 0
        val athlete = if (rules == Edition.E2014 && remarkableAthlete && test.ability in REMARKABLE_ABILITIES) (proficiency + 1) / 2 else 0
        return maxOf(jack, athlete)
    }

    /** What gives [test] advantage, by name. */
    fun advantageFrom(test: D20Test): List<String> = buildList {
        val athletic = test == D20Test.Initiative || test is D20Test.Check && test.skill == Skill.Athletics
        if (rules == Edition.E2024 && remarkableAthlete && athletic) add("Remarkable Athlete")
    }

    /**
     * The lowest d20 that's a critical hit on a weapon attack: 19 with the Champion's Improved
     * Critical (Fighter 3), 18 with Superior Critical (Fighter 15); else 20.
     */
    val critOn: Int
        get() = if (subclassOf("Fighter") != "Champion") 20 else when (levelIn("Fighter")) {
            in 15..20 -> 18
            in 3..14 -> 19
            else -> 20
        }

    /**
     * Worn armor, DEX, shield and Unarmored Defense, worked out; the Defense style's +1 in armor;
     * plus [acBonus]. A shield's +2 needs training in 2024, not in 2014.
     */
    val ac: Int
        get() = armorClass(
            ARMOR.find { it.name == wearing } ?: ARMOR.first(), shield, scores, classInfo(className),
            shieldAc = rules == Edition.E2014 || shieldTraining,
        ) + (if (wearing != null && feats.any { it.name == "Defense" }) 1 else 0) + acBonus

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

    /** The worn armor, if it needs more Strength than the wearer has. A 2014 dwarf's speed isn't slowed by heavy armor. */
    val tooHeavy: Armor?
        get() = ARMOR.find { it.name == wearing }
            ?.takeIf { it.strength > 0 && score(Ability.STR) < it.strength && !(rules == Edition.E2014 && isDwarf) }

    /** "Dwarf", or a subrace as D&D Beyond names it ("Hill Dwarf"). */
    val isDwarf: Boolean get() = "dwarf" in species.lowercase()

    /** Walking speed now: [baseSpeed], less 10 feet in armor too heavy for you. */
    val speed: Int get() = (baseSpeed - if (tooHeavy != null) HEAVY_ARMOR_SLOWDOWN else 0).coerceAtLeast(0)

    /** Armor trained with, from [training] ("Light Armor", "All armor"); with none recorded, the class's. */
    val armorTraining: Set<ArmorWeight>
        get() {
            if (training.isEmpty()) return classInfo(className)?.armor ?: emptySet()
            val t = training.map { it.trim().lowercase() }
            if ("all armor" in t) return setOf(ArmorWeight.Light, ArmorWeight.Medium, ArmorWeight.Heavy)
            return ArmorWeight.entries.filter { "${it.name.lowercase()} armor" in t }.toSet()
        }

    /** The worn armor, by name, if the character isn't trained with it: DIS on STR and DEX, no spells. */
    val untrainedArmor: String?
        get() = ARMOR.find { it.name == wearing }?.takeIf { it.weight != ArmorWeight.None && it.weight !in armorTraining }?.name

    /** Everything worn without training that brings DIS on STR and DEX and stops spells: armor, and in 2014 a shield. */
    val untrainedGear: List<String>
        get() = listOfNotNull(untrainedArmor, "Shield".takeIf { untrainedShield && rules == Edition.E2014 })

    val canCastSpells: Boolean get() = untrainedGear.isEmpty()

    /** Trained with shields: "Shields" in [training], or the class's when none is recorded. */
    val shieldTraining: Boolean
        get() = if (training.isEmpty()) classInfo(className)?.shields == true
        else training.any { it.trim().lowercase() in setOf("shield", "shields") }

    /** Holding a shield without training. 2014: the penalties of armor worn untrained, still +2 AC. 2024: no AC from it. */
    val untrainedShield: Boolean get() = shield && !shieldTraining

    /** What gives [test] disadvantage, by name: gear worn untrained ([untrainedGear]), and on Stealth [stealthDisadvantageFrom]. */
    fun disadvantageFrom(test: D20Test): List<String> = buildList {
        val untrained = untrainedGear.takeIf { test.ability == Ability.STR || test.ability == Ability.DEX }.orEmpty()
        untrained.forEach { add("$it, untrained") }
        if (test is D20Test.Check && test.skill == Skill.Stealth) addAll(stealthDisadvantageFrom - untrained.toSet())
    }

    /** A DEX check: the modifier, and in 2014 Jack of All Trades or Remarkable Athlete ([untrainedBonus]). */
    val initiative: Int get() = mod(Ability.DEX) + untrainedBonus(D20Test.Initiative)
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

/** The checks 2014's Remarkable Athlete adds to. */
private val REMARKABLE_ABILITIES = setOf(Ability.STR, Ability.DEX, Ability.CON)

/** Pact magic and multiclassing aside, this is the full caster table — a sensible default to edit from. */
val FULL_CASTER_SLOTS: List<List<Int>> = listOf(
    listOf(2), listOf(3), listOf(4, 2), listOf(4, 3), listOf(4, 3, 2),
    listOf(4, 3, 3), listOf(4, 3, 3, 1), listOf(4, 3, 3, 2), listOf(4, 3, 3, 3, 1), listOf(4, 3, 3, 3, 2),
    listOf(4, 3, 3, 3, 2, 1), listOf(4, 3, 3, 3, 2, 1), listOf(4, 3, 3, 3, 2, 1, 1), listOf(4, 3, 3, 3, 2, 1, 1),
    listOf(4, 3, 3, 3, 2, 1, 1, 1), listOf(4, 3, 3, 3, 2, 1, 1, 1), listOf(4, 3, 3, 3, 2, 1, 1, 1, 1),
    listOf(4, 3, 3, 3, 3, 1, 1, 1, 1), listOf(4, 3, 3, 3, 3, 2, 1, 1, 1), listOf(4, 3, 3, 3, 3, 2, 2, 1, 1),
)
