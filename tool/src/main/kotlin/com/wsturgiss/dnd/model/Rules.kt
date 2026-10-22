package com.wsturgiss.dnd.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * The bits of the SRD 5.2 character rules that are numbers rather than text: what each class,
 * species and background gives you, and how a level changes a sheet. Building and levelling
 * a character both read from here.
 */

enum class Caster { None, Full, Half, Pact }

data class ClassInfo(
    val name: String,
    val hitDie: Int,
    val saves: Set<Ability>,
    val skillCount: Int,
    val skillChoices: List<Skill>,
    val caster: Caster,
    /** Where the standard array goes, best score first: the SRD's suggestion for the class. */
    val priority: List<Ability>,
    /** Unarmored Defense adds this score's modifier to 10 + DEX. */
    val unarmored: Ability? = null,
    /** Levels that bring an Ability Score Improvement. */
    val asiLevels: Set<Int> = setOf(4, 8, 12, 16),
    /** Levels that bring Expertise, and in how many skills. */
    val expertise: Map<Int, Int> = emptyMap(),
) {
    val averageHit: Int get() = hitDie / 2 + 1
}

private val A = Ability.entries.associateBy { it.short }
private fun abilities(vararg s: String) = s.map { A.getValue(it) }
private fun skills(vararg s: Skill) = s.toList()

val CLASS_INFO: List<ClassInfo> = listOf(
    ClassInfo(
        "Barbarian", 12, setOf(Ability.STR, Ability.CON), 2,
        skills(Skill.AnimalHandling, Skill.Athletics, Skill.Intimidation, Skill.Nature, Skill.Perception, Skill.Survival),
        Caster.None, abilities("STR", "CON", "DEX", "WIS", "CHA", "INT"), unarmored = Ability.CON,
    ),
    ClassInfo(
        "Bard", 8, setOf(Ability.DEX, Ability.CHA), 3, Skill.entries,
        Caster.Full, abilities("CHA", "DEX", "CON", "WIS", "INT", "STR"), expertise = mapOf(2 to 2, 9 to 2),
    ),
    ClassInfo(
        "Cleric", 8, setOf(Ability.WIS, Ability.CHA), 2,
        skills(Skill.History, Skill.Insight, Skill.Medicine, Skill.Persuasion, Skill.Religion),
        Caster.Full, abilities("WIS", "CON", "STR", "CHA", "INT", "DEX"),
    ),
    ClassInfo(
        "Druid", 8, setOf(Ability.INT, Ability.WIS), 2,
        skills(
            Skill.Arcana, Skill.AnimalHandling, Skill.Insight, Skill.Medicine,
            Skill.Nature, Skill.Perception, Skill.Religion, Skill.Survival,
        ),
        Caster.Full, abilities("WIS", "CON", "DEX", "INT", "CHA", "STR"),
    ),
    ClassInfo(
        "Fighter", 10, setOf(Ability.STR, Ability.CON), 2,
        skills(
            Skill.Acrobatics, Skill.AnimalHandling, Skill.Athletics, Skill.History, Skill.Insight,
            Skill.Intimidation, Skill.Perception, Skill.Survival,
        ),
        Caster.None, abilities("STR", "CON", "DEX", "WIS", "CHA", "INT"), asiLevels = setOf(4, 6, 8, 12, 14, 16),
    ),
    ClassInfo(
        "Monk", 8, setOf(Ability.STR, Ability.DEX), 2,
        skills(Skill.Acrobatics, Skill.Athletics, Skill.History, Skill.Insight, Skill.Religion, Skill.Stealth),
        Caster.None, abilities("DEX", "WIS", "CON", "STR", "CHA", "INT"), unarmored = Ability.WIS,
    ),
    ClassInfo(
        "Paladin", 10, setOf(Ability.WIS, Ability.CHA), 2,
        skills(Skill.Athletics, Skill.Insight, Skill.Intimidation, Skill.Medicine, Skill.Persuasion, Skill.Religion),
        Caster.Half, abilities("STR", "CHA", "CON", "WIS", "INT", "DEX"),
    ),
    ClassInfo(
        "Ranger", 10, setOf(Ability.STR, Ability.DEX), 3,
        skills(
            Skill.AnimalHandling, Skill.Athletics, Skill.Insight, Skill.Investigation,
            Skill.Nature, Skill.Perception, Skill.Stealth, Skill.Survival,
        ),
        Caster.Half, abilities("DEX", "WIS", "CON", "STR", "INT", "CHA"), expertise = mapOf(2 to 1, 9 to 2),
    ),
    ClassInfo(
        "Rogue", 8, setOf(Ability.DEX, Ability.INT), 4,
        skills(
            Skill.Acrobatics, Skill.Athletics, Skill.Deception, Skill.Insight, Skill.Intimidation,
            Skill.Investigation, Skill.Perception, Skill.Performance, Skill.Persuasion, Skill.SleightOfHand, Skill.Stealth,
        ),
        Caster.None, abilities("DEX", "CON", "WIS", "INT", "CHA", "STR"),
        asiLevels = setOf(4, 8, 10, 12, 16), expertise = mapOf(1 to 2, 6 to 2),
    ),
    ClassInfo(
        "Sorcerer", 6, setOf(Ability.CON, Ability.CHA), 2,
        skills(Skill.Arcana, Skill.Deception, Skill.Insight, Skill.Intimidation, Skill.Persuasion, Skill.Religion),
        Caster.Full, abilities("CHA", "CON", "DEX", "WIS", "INT", "STR"),
    ),
    ClassInfo(
        "Warlock", 8, setOf(Ability.WIS, Ability.CHA), 2,
        skills(
            Skill.Arcana, Skill.Deception, Skill.History, Skill.Intimidation,
            Skill.Investigation, Skill.Nature, Skill.Religion,
        ),
        Caster.Pact, abilities("CHA", "CON", "DEX", "WIS", "INT", "STR"),
    ),
    ClassInfo(
        "Wizard", 6, setOf(Ability.INT, Ability.WIS), 2,
        skills(
            Skill.Arcana, Skill.History, Skill.Insight, Skill.Investigation,
            Skill.Medicine, Skill.Religion,
        ),
        Caster.Full, abilities("INT", "CON", "DEX", "WIS", "CHA", "STR"),
    ),
)

fun classInfo(name: String): ClassInfo? = CLASS_INFO.find { it.name == name }

/** Level 19 brings an Epic Boon for every class: +1 to one score, which may go to 30. */
const val EPIC_BOON_LEVEL = 19

data class SpeciesInfo(
    val name: String,
    val speed: Int = 30,
    /** Dwarven Toughness. */
    val hpPerLevel: Int = 0,
    /** A skill to pick: Elves' Keen Senses, Humans' Skillful. */
    val skillChoices: List<Skill> = emptyList(),
)

val SPECIES: List<SpeciesInfo> = listOf(
    SpeciesInfo("Dragonborn"),
    SpeciesInfo("Dwarf", hpPerLevel = 1),
    SpeciesInfo("Elf", skillChoices = listOf(Skill.Insight, Skill.Perception, Skill.Survival)),
    SpeciesInfo("Gnome"),
    SpeciesInfo("Goliath", speed = 35),
    SpeciesInfo("Halfling"),
    SpeciesInfo("Human", skillChoices = Skill.entries),
    SpeciesInfo("Orc"),
    SpeciesInfo("Tiefling"),
)

fun speciesInfo(name: String): SpeciesInfo? = SPECIES.find { it.name == name }

/** A background raises three scores (+2/+1 or +1/+1/+1) and trains two skills. Custom: any of each. */
data class BackgroundInfo(val name: String, val abilities: List<Ability>, val skills: Set<Skill>) {
    val custom: Boolean get() = skills.isEmpty()
}

val BACKGROUNDS: List<BackgroundInfo> = listOf(
    BackgroundInfo("Acolyte", abilities("INT", "WIS", "CHA"), setOf(Skill.Insight, Skill.Religion)),
    BackgroundInfo("Criminal", abilities("DEX", "CON", "INT"), setOf(Skill.SleightOfHand, Skill.Stealth)),
    BackgroundInfo("Sage", abilities("CON", "INT", "WIS"), setOf(Skill.Arcana, Skill.History)),
    BackgroundInfo("Soldier", abilities("STR", "DEX", "CON"), setOf(Skill.Athletics, Skill.Intimidation)),
    BackgroundInfo("Custom", Ability.entries, emptySet()),
)

fun backgroundInfo(name: String): BackgroundInfo? = BACKGROUNDS.find { it.name == name }

/** Every way to spend a background's three scores: each +2/+1 pair, then +1 to all three. */
fun backgroundBonuses(from: List<Ability>): List<Map<Ability, Int>> {
    val pairs = from.flatMap { two -> from.filter { it != two }.map { one -> mapOf(two to 2, one to 1) } }
    val spread = if (from.size == 3) listOf(from.associateWith { 1 }) else emptyList()
    return pairs + spread
}

fun bonusLabel(bonus: Map<Ability, Int>): String =
    bonus.entries.sortedByDescending { it.value }.joinToString(" ") { "+${it.value} ${it.key.short}" }

enum class ArmorWeight { None, Light, Medium, Heavy }

data class Armor(
    val name: String,
    val base: Int,
    val weight: ArmorWeight,
    val pounds: Double,
    /** Disadvantage on Stealth checks while worn, as the SRD marks it. */
    val stealthDisadvantage: Boolean = false,
)

val ARMOR: List<Armor> = listOf(
    Armor("No armor", 10, ArmorWeight.None, 0.0),
    Armor("Padded Armor", 11, ArmorWeight.Light, 8.0, stealthDisadvantage = true),
    Armor("Leather Armor", 11, ArmorWeight.Light, 10.0),
    Armor("Studded Leather Armor", 12, ArmorWeight.Light, 13.0),
    Armor("Hide Armor", 12, ArmorWeight.Medium, 12.0),
    Armor("Chain Shirt", 13, ArmorWeight.Medium, 14.0),
    Armor("Scale Mail", 14, ArmorWeight.Medium, 45.0, stealthDisadvantage = true),
    Armor("Breastplate", 14, ArmorWeight.Medium, 20.0),
    Armor("Half-Plate Armor", 15, ArmorWeight.Medium, 40.0, stealthDisadvantage = true),
    Armor("Ring Mail", 14, ArmorWeight.Heavy, 40.0, stealthDisadvantage = true),
    Armor("Chain Mail", 16, ArmorWeight.Heavy, 55.0, stealthDisadvantage = true),
    Armor("Splint Armor", 17, ArmorWeight.Heavy, 60.0, stealthDisadvantage = true),
    Armor("Plate Armor", 18, ArmorWeight.Heavy, 65.0, stealthDisadvantage = true),
)

/** AC from armor and scores. Unarmored Defense only counts with no armor (a Monk's also needs no shield). */
fun armorClass(armor: Armor, shield: Boolean, scores: Map<Ability, Int>, cls: ClassInfo?): Int {
    fun mod(a: Ability) = Math.floorDiv((scores[a] ?: 10) - 10, 2)
    val dex = mod(Ability.DEX)
    val body = when (armor.weight) {
        ArmorWeight.None -> {
            val extra = cls?.unarmored?.takeIf { !(shield && it == Ability.WIS) }?.let(::mod) ?: 0
            10 + dex + extra
        }
        ArmorWeight.Light -> armor.base + dex
        ArmorWeight.Medium -> armor.base + minOf(dex, 2)
        ArmorWeight.Heavy -> armor.base
    }
    return body + if (shield) 2 else 0
}

/** Spell slots by spell level (index 0 is 1st) for a single-class character. */
fun slotsFor(cls: ClassInfo?, level: Int): List<Int> {
    val l = level.coerceIn(1, 20)
    return when (cls?.caster) {
        Caster.Full -> FULL_CASTER_SLOTS[l - 1]
        // the SRD 5.2 half-caster table is the full one at half the level, rounded up
        Caster.Half -> FULL_CASTER_SLOTS[(l + 1) / 2 - 1]
        Caster.Pact -> {
            val count = when { l == 1 -> 1; l <= 10 -> 2; l <= 16 -> 3; else -> 4 }
            val slotLevel = minOf((l + 1) / 2, 5)
            List(slotLevel) { if (it == slotLevel - 1) count else 0 }
        }
        else -> emptyList()
    }
}

/** [slotsFor] as the sheet stores it: nine levels, keeping what's been spent where it still fits. */
fun Character.withClassSlots(cls: ClassInfo?, level: Int): Character {
    if (cls == null || cls.caster == Caster.None) return this
    val table = slotsFor(cls, level)
    return copy(slots = List(9) { i ->
        val max = table.getOrElse(i) { 0 }
        Slots(max, minOf(slots.getOrNull(i)?.used ?: 0, max))
    })
}

val STANDARD_ARRAY = listOf(15, 14, 13, 12, 10, 8)

const val POINT_BUY_BUDGET = 27

/** Point-buy cost of a score from 8 to 15; null outside that range. */
fun pointCost(score: Int): Int? = when (score) {
    in 8..13 -> score - 8
    14 -> 7
    15 -> 9
    else -> null
}

fun suggestedScores(cls: ClassInfo?): Map<Ability, Int> =
    (cls?.priority ?: Ability.entries).zip(STANDARD_ARRAY).toMap()

/** +2 to the class's best of the background's three, +1 to the next. */
fun suggestedBonus(cls: ClassInfo?, bg: BackgroundInfo?): Map<Ability, Int> {
    val from = bg?.abilities ?: return emptyMap()
    val order = (cls?.priority ?: Ability.entries).filter { it in from }
    return mapOf(order[0] to 2, order[1] to 1)
}

/**
 * One line of a starting-equipment option: how many, and the weight of each. An item "of your
 * choice" (a gaming set, an instrument) is named for the kind, and [choices] lists what it can be.
 */
@Serializable(with = GearItemSerializer::class)
data class GearItem(
    val name: String,
    val count: Int,
    val weight: Double,
    val inSrd: Boolean,
    val choices: List<GearItem> = emptyList(),
)

/** `["Arrows", 20, 0.05, true]`, or `["Gaming Set", 1, 0, false, [["Dice", 0], ...]]`, as `data/build_srd.py` writes it. */
object GearItemSerializer : KSerializer<GearItem> {
    private val list = ListSerializer(JsonElement.serializer())
    override val descriptor = list.descriptor
    override fun deserialize(decoder: Decoder): GearItem {
        val parts = list.deserialize(decoder)
        val (n, c, w, s) = parts.take(4).map { it.jsonPrimitive }
        val choices = parts.getOrNull(4)?.jsonArray?.map { pair ->
            val (cn, cw) = pair.jsonArray.map { it.jsonPrimitive }
            GearItem(cn.content, 1, cw.double, true)
        }
        return GearItem(n.content, c.int, w.double, s.boolean, choices ?: emptyList())
    }
    override fun serialize(encoder: Encoder, value: GearItem) = list.serialize(
        encoder,
        listOf(JsonPrimitive(value.name), JsonPrimitive(value.count), JsonPrimitive(value.weight), JsonPrimitive(value.inSrd)) +
            listOfNotNull(
                value.choices.takeIf { it.isNotEmpty() }
                    ?.let { cs -> JsonArray(cs.map { JsonArray(listOf(JsonPrimitive(it.name), JsonPrimitive(it.weight))) }) },
            ),
    )
}

/** One lettered choice of starting equipment: some items and some gold, or just gold. */
@Serializable
data class GearOption(@SerialName("i") val items: List<GearItem>, val gp: Int) {
    val label: String
        get() = (items.map { it.counted } + listOfNotNull(gp.takeIf { it > 0 }?.let { "$it GP" })).joinToString(", ")
}

/** "Dagger", "2 Daggers", "20 Arrows", "10 Parchment". */
val GearItem.counted: String
    get() = when {
        count == 1 -> name
        name.endsWith("s") || name == "Parchment" -> "$count $name"
        name.endsWith("ch") || name.endsWith("sh") || name.endsWith("x") -> "$count ${name}es"
        else -> "$count ${name}s"
    }

/**
 * What to call each option, short enough for a row: a kit by its armor if it has any, else its
 * first item, "etc." for the rest ("Chain Mail, etc.", "Greataxe, etc."); gold by the amount.
 */
fun gearNames(options: List<GearOption>): List<String> = options.map { o ->
    val lead = o.items.firstOrNull { a -> ARMOR.any { it.name == a.name } } ?: o.items.firstOrNull()
    when {
        lead == null -> "${o.gp} GP"
        else -> lead.counted + if (o.items.size > 1) ", etc." else ""
    }
}

/** A source of skill picks while building: the class's list, a species trait, a custom background. */
data class SkillPool(val key: String, val label: String, val count: Int, val from: List<Skill>)

/** What an ability score may be, typed in or edited (the rules' 1 to 30). */
val SCORE_RANGE = 1..30

/** What max HP may be, typed in or edited. */
val HP_RANGE = 1..999

/** Max HP at [level]: at least 1 a level, the fewest the rules can give. */
fun hpRange(level: Int): IntRange = maxOf(HP_RANGE.first, level)..HP_RANGE.last

fun validScore(v: Int): Boolean = v in SCORE_RANGE

fun validHp(v: Int, level: Int = 1): Boolean = v in hpRange(level)

/**
 * A character being built. Fields left null follow the suggestion for the class and background,
 * so changing class re-deals the scores until you set them yourself.
 */
data class Draft(
    val name: String = "",
    val className: String = "Fighter",
    val species: String = "Human",
    val background: String = "Soldier",
    val level: Int = 1,
    val base: Map<Ability, Int>? = null,
    /** How [base] was made, to show: "standard array", "point buy", "rolled". */
    val baseMethod: String = "suggested",
    val bonus: Map<Ability, Int>? = null,
    val picks: Map<String, Set<Skill>> = emptyMap(),
    val expertise: Set<Skill> = emptySet(),
    /** Worn from the class's starting equipment (see [dressed]), never picked freely. */
    val armor: String = "No armor",
    val shield: Boolean = false,
    /** Which starting-equipment option, A = 0, for the class and the background. */
    val classGear: Int = 0,
    val backgroundGear: Int = 0,
    /** What each "of your choice" item became, by its generic name ("Gaming Set" → Dice). */
    val gearPicks: Map<String, GearItem> = emptyMap(),
    /** The species' sub-choice, by Look up name ("Elven Lineage: High Elf"). */
    val lineage: String? = null,
    /** Human's Versatile: an origin feat. */
    val versatileFeat: String? = null,
    val fightingStyle: String? = null,
    /** Which list Versatile's Magic Initiate draws on; the background's names its own. */
    val initiateList: String = "Wizard",
    /** Magic Initiate's two cantrips and one level 1 spell, by where the feat came from ([INITIATE_FROM_BACKGROUND], [INITIATE_FROM_SPECIES]). */
    val initiateCantrips: Map<String, Set<String>> = emptyMap(),
    val initiateSpell: Map<String, String> = emptyMap(),
    val cantrips: Set<String> = emptySet(),
    val prepared: Set<String> = emptySet(),
    /** Beyond Common. */
    val languages: Set<String> = emptySet(),
    /** Null takes the SRD's subclass for the class, from 3rd level. */
    val subclass: String? = null,
    /**
     * Final scores typed in, for a character made elsewhere: they replace base and background
     * bonus (bonuses already in, no cap at 20). Null works them out.
     */
    val manualScores: Map<Ability, Int>? = null,
    /** Max HP typed in, for a character made elsewhere. Null works it out. */
    val manualHp: Int? = null,
) {
    val cls: ClassInfo? get() = classInfo(className)
    val speciesInfo: SpeciesInfo? get() = speciesInfo(species)
    val backgroundInfo: BackgroundInfo? get() = backgroundInfo(background)
    val armorInfo: Armor get() = ARMOR.find { it.name == armor } ?: ARMOR.first()

    val baseScores: Map<Ability, Int> get() = base ?: suggestedScores(cls)
    val bonusOrSuggested: Map<Ability, Int> get() = bonus ?: suggestedBonus(cls, backgroundInfo)

    /** Typed in, or base plus background no higher than 20. */
    val scores: Map<Ability, Int>
        get() = manualScores?.let { m -> Ability.entries.associateWith { a -> m[a] ?: 10 } }
            ?: Ability.entries.associateWith { a -> ((baseScores[a] ?: 10) + (bonusOrSuggested[a] ?: 0)).coerceAtMost(20) }

    fun mod(a: Ability): Int = Math.floorDiv((scores[a] ?: 10) - 10, 2)

    val pools: List<SkillPool>
        get() = buildList {
            cls?.let { add(SkillPool("class", it.name, it.skillCount, it.skillChoices)) }
            speciesInfo?.takeIf { it.skillChoices.isNotEmpty() }?.let { add(SkillPool("species", it.name, 1, it.skillChoices)) }
            backgroundInfo?.takeIf { it.custom }?.let { add(SkillPool("background", "Background", 2, Skill.entries)) }
            if (versatileFeat == "Skilled") add(SkillPool("skilled", "Skilled", 3, Skill.entries))
        }

    /** Every skill trained, fixed or picked. */
    val trained: Set<Skill>
        get() = (backgroundInfo?.skills ?: emptySet()) + pools.flatMap { picks[it.key] ?: emptySet() }

    /** Expertise slots open at this level, by the class's table. */
    val expertiseCount: Int get() = cls?.expertise?.filterKeys { it <= level }?.values?.sum() ?: 0

    val hpPerLevelBonus: Int get() = (speciesInfo?.hpPerLevel ?: 0) + if (versatileFeat == TOUGH_FEAT) TOUGH_HP_PER_LEVEL else 0

    /** Full hit die at 1st level; the fixed average for each level after. Never less than 1 a level. */
    val maxHp: Int
        get() {
            manualHp?.let { return it }
            val die = cls?.hitDie ?: 8
            val con = mod(Ability.CON)
            val first = maxOf(1, die + con) + hpPerLevelBonus
            val rest = (level - 1) * (maxOf(1, (cls?.averageHit ?: 5) + con) + hpPerLevelBonus)
            return first + rest
        }

    val ac: Int get() = armorClass(armorInfo, shield, scores, cls)

    /** What still needs choosing, in words; empty when the character can be made. */
    val missing: List<String>
        get() = buildList {
            if (name.isBlank()) add("a name")
            pools.forEach { p ->
                val n = (picks[p.key] ?: emptySet()).size
                if (n < p.count) add("${p.count - n} ${p.label.lowercase()} skill${if (p.count - n == 1) "" else "s"}")
            }
            if (expertise.size < expertiseCount) add("${expertiseCount - expertise.size} expertise")
            if (manualScores != null && (manualScores.size < Ability.entries.size || !manualScores.values.all(::validScore))) {
                add("scores of ${SCORE_RANGE.first} to ${SCORE_RANGE.last}")
            }
            if (manualHp != null && !validHp(manualHp, level)) hpRange(level).let { add("HP of ${it.first} to ${it.last}") }
        }

    /** Scores from a base method, back on the rules: anything typed in goes, HP too. */
    fun withBase(scores: Map<Ability, Int>, method: String): Draft =
        copy(base = scores, baseMethod = method, manualScores = null, manualHp = null)

    fun classGearOption(gear: Map<String, List<GearOption>>): GearOption? = gear[className]?.getOrNull(classGear)
    fun backgroundGearOption(gear: Map<String, List<GearOption>>): GearOption? = gear[background]?.getOrNull(backgroundGear)

    /** Wears what the class's starting equipment brings: its armor, its shield. */
    fun dressed(gear: Map<String, List<GearOption>>): Draft {
        val names = classGearOption(gear)?.items?.map { it.name }?.toSet() ?: emptySet()
        return copy(armor = ARMOR.find { it.name in names }?.name ?: ARMOR.first().name, shield = "Shield" in names)
    }

    /** The "of your choice" items in the chosen options, still to be (or already) picked. */
    fun gearChoices(gear: Map<String, List<GearOption>>): List<GearItem> =
        listOfNotNull(classGearOption(gear), backgroundGearOption(gear)).flatMap { it.items }.filter { it.choices.isNotEmpty() }

    /**
     * The starting equipment from both options, each choice as picked (unpicked, it keeps its
     * generic name); the same thing from each option is one line.
     */
    fun bag(gear: Map<String, List<GearOption>>): List<GearItem> =
        listOfNotNull(classGearOption(gear), backgroundGearOption(gear)).flatMap { it.items }
            .map { item -> gearPicks[item.name]?.takeIf { item.choices.isNotEmpty() }?.copy(count = item.count) ?: item.copy(choices = emptyList()) }
            .groupBy { it.name }.map { (_, same) -> same.first().copy(count = same.sumOf { it.count }) }

    fun gold(gear: Map<String, List<GearOption>>): Int =
        (classGearOption(gear)?.gp ?: 0) + (backgroundGearOption(gear)?.gp ?: 0)

    fun build(gear: Map<String, List<GearOption>> = emptyMap()): Character {
        val t = trained
        val items = bag(gear).mapIndexed { i, g -> Item(-1L - i, g.name, g.count, g.weight, "equipment".takeIf { g.inSrd }) }
        val gp = gold(gear)
        return Character(
            name = name.trim(),
            className = className,
            level = level,
            species = species,
            background = background,
            scores = scores,
            saves = cls?.saves ?: emptySet(),
            skills = t.associateWith { if (it in expertise) Training.Expertise else Training.Proficient },
            maxHp = maxHp,
            hp = maxHp,
            wearing = armorInfo.name.takeIf { armorInfo.weight != ArmorWeight.None },
            shield = shield,
            speed = speciesInfo?.speed ?: 30,
            items = items,
            coins = if (gp > 0) mapOf(Coin.GP to gp) else emptyMap(),
        ).withClassSlots(cls, level)
    }
}

/** Rolls 4d6 and drops the lowest, six times. Each entry is the four dice, highest first. */
fun rollScores(random: kotlin.random.Random = kotlin.random.Random.Default): List<List<Int>> =
    List(6) { List(4) { random.nextInt(1, 7) }.sortedDescending() }

fun keptTotal(dice: List<Int>): Int = dice.sortedDescending().take(3).sum()

/** What a level brings, before you choose anything. */
data class LevelPlan(
    val from: Int,
    val to: Int,
    val cls: ClassInfo?,
    val asi: Boolean,
    val epicBoon: Boolean,
    val expertise: Int,
    val oldSlots: List<Int>,
    val newSlots: List<Int>,
    val oldProficiency: Int,
    val newProficiency: Int,
)

fun Character.levelPlan(): LevelPlan? {
    if (level >= 20) return null
    val cls = classInfo(className)
    val to = level + 1
    return LevelPlan(
        from = level,
        to = to,
        cls = cls,
        asi = cls == null && to in setOf(4, 8, 12, 16) || cls != null && to in cls.asiLevels,
        epicBoon = to == EPIC_BOON_LEVEL,
        expertise = cls?.expertise?.get(to) ?: 0,
        oldSlots = slots.map { it.max },
        newSlots = if (cls == null || cls.caster == Caster.None) slots.map { it.max }
        else List(9) { slotsFor(cls, to).getOrElse(it) { 0 } },
        oldProficiency = proficiency,
        newProficiency = copy(level = to).proficiency,
    )
}

/**
 * One level up. [hitRoll] is the hit die as rolled (or the average); [raise] is the score
 * increase. A higher CON modifier is worth a hit point for every level, back to the first.
 */
fun Character.levelUp(hitRoll: Int, raise: Map<Ability, Int>, expertise: Set<Skill>, cap: Int = 20): Character {
    val cls = classInfo(className)
    val to = (level + 1).coerceAtMost(20)
    val newScores = scores + raise.mapValues { (a, n) -> (score(a) + n).coerceAtMost(maxOf(cap, score(a))) }
    val raised = copy(level = to, scores = newScores)
    val conGain = raised.mod(Ability.CON) - mod(Ability.CON)
    val hpGain = maxOf(1, hitRoll + raised.mod(Ability.CON)) + hpPerLevelBonus + conGain * level
    return raised.copy(
        maxHp = maxHp + hpGain,
        hp = (hp + hpGain).coerceAtLeast(0),
        skills = skills + expertise.associateWith { Training.Expertise },
    ).withClassSlots(cls, to)
}

const val INITIATE_FROM_BACKGROUND = "background"
const val INITIATE_FROM_SPECIES = "species"
const val INITIATE_FEAT = "Magic Initiate"
/** Not in the SRD, but common: +2 max HP a level, the levels before it too. */
const val TOUGH_FEAT = "Tough"
const val TOUGH_HP_PER_LEVEL = 2
const val LANGUAGES_TO_PICK = 2

/** The parts of a [Draft] that need the SRD's creation data. */
class Building(val draft: Draft, val made: Creation) {
    val classData: ClassData? get() = made.classes[draft.className]
    val speciesData: SpeciesData? get() = made.species[draft.species]
    val backgroundData: BackgroundData? get() = made.backgrounds[draft.background]
    private val levelData: LevelData? get() = classData?.levels?.getOrNull(draft.level - 1)

    val cantripCount: Int get() = levelData?.cantrips ?: 0
    val preparedCount: Int get() = levelData?.prepared ?: 0

    /** The highest spell level the class can prepare at this level: its highest slot. */
    val maxSpellLevel: Int get() = slotsFor(draft.cls, draft.level).size

    /** Fighter at 1st, Paladin and Ranger at 2nd. */
    val needsFightingStyle: Boolean get() = classData?.levels?.take(draft.level)?.any { it.fightingStyle() } == true

    /** Magic Initiate's lists, by where the feat came from. */
    val initiates: Map<String, String>
        get() = buildMap {
            backgroundData?.takeIf { it.feat == INITIATE_FEAT }?.let { put(INITIATE_FROM_BACKGROUND, it.note ?: "Wizard") }
            if (draft.versatileFeat == INITIATE_FEAT) put(INITIATE_FROM_SPECIES, draft.initiateList)
        }

    val subclass: String? get() = if (draft.level >= 3) draft.subclass ?: classData?.subclass else null

    val feats: List<Feat>
        get() = listOfNotNull(
            backgroundData?.let { Feat(it.feat, if (it.feat == INITIATE_FEAT) it.note ?: "" else "") },
            draft.versatileFeat?.let { Feat(it, if (it == INITIATE_FEAT) draft.initiateList else "") },
            draft.fightingStyle?.takeIf { needsFightingStyle }?.let { Feat(it, "Fighting Style") },
        )

    val missing: List<String>
        get() = buildList {
            addAll(draft.missing)
            speciesData?.choice?.takeIf { draft.lineage == null }?.let { add(it.lowercase()) }
            if (draft.species == "Human" && draft.versatileFeat == null) add("an origin feat")
            if (needsFightingStyle && draft.fightingStyle == null) add("a fighting style")
            initiates.keys.forEach { from ->
                val n = 2 - (draft.initiateCantrips[from]?.size ?: 0) + (if (draft.initiateSpell[from] == null) 1 else 0)
                if (n > 0) add("$n Magic Initiate spell${if (n == 1) "" else "s"}")
            }
            if (draft.cantrips.size < cantripCount) add("${cantripCount - draft.cantrips.size} cantrips")
            if (draft.prepared.size < preparedCount) add("${preparedCount - draft.prepared.size} spells")
            if (draft.languages.size < LANGUAGES_TO_PICK) add("${LANGUAGES_TO_PICK - draft.languages.size} languages")
        }

    /** Background tools, the class's training, and what's still "of your choice" in words. */
    val training: List<String>
        get() = (classData?.training.orEmpty() + backgroundData?.tools.orEmpty() +
            listOfNotNull(classData?.toolChoice, backgroundData?.toolChoice?.let { "One kind of Gaming Set" })).distinct()

    fun build(gear: Map<String, List<GearOption>>): Character {
        val d = draft
        val spells = (d.cantrips + d.prepared +
            initiates.keys.flatMap { d.initiateCantrips[it].orEmpty() + listOfNotNull(d.initiateSpell[it]) }).toList()
        return d.build(gear).copy(
            lineage = d.lineage.orEmpty(),
            subclass = subclass.orEmpty(),
            feats = feats,
            languages = listOf("Common") + d.languages.sorted(),
            training = training,
            spells = spells.distinct(),
        )
    }
}
