package com.wsturgiss.dnd.model

import kotlin.random.Random

/** The ability each class casts with. Classes without spells aren't listed. */
fun castingAbility(className: String): Ability? = when (className) {
    "Bard", "Paladin", "Sorcerer", "Warlock" -> Ability.CHA
    "Cleric", "Druid", "Ranger" -> Ability.WIS
    "Wizard" -> Ability.INT
    else -> null
}

/** An attack the sheet can make: what the d20 adds, and the damage on a hit. */
data class Attack(
    val name: String,
    val toHit: Int,
    /** Every die and modifier of the damage; null when the sheet can't tell. */
    val damage: DiceExpr?,
    /** The ability behind the roll, for what gives it advantage or disadvantage. */
    val ability: Ability,
    val damageType: String = "",
) {
    val expr: DiceExpr get() = DiceExpr.parse("d20${signed(toHit)}")!!
}

/** An attack roll and what came of it: on a critical hit, the damage too. */
data class AttackOutcome(val attack: Roll, val damage: Roll?) {
    val result: AttackResult? get() = attack.attackResult
}

/**
 * Rolls an attack. A natural 20 is a critical hit, and its [damage] is rolled straight away with
 * every die twice and the modifiers once; a natural 1 misses and has no damage. Otherwise the
 * damage waits for the table to say whether it hit ([rollDamage]).
 */
fun rollAttack(attack: DiceExpr, damage: DiceExpr?, edge: Edge = Edge.None, random: Random = Random.Default): AttackOutcome {
    val roll = attack.roll(edge, random)
    val crit = damage?.takeIf { roll.attackResult == AttackResult.CriticalHit }?.let { rollDamage(it, critical = true, random) }
    return AttackOutcome(roll, crit)
}

/** Damage on a hit; on a critical hit every die twice, flat modifiers once. */
fun rollDamage(damage: DiceExpr, critical: Boolean, random: Random = Random.Default): Roll =
    (if (critical) damage.critical() else damage).roll(Edge.None, random)

/** A weapon as the SRD lists it: `Martial Melee Weapons`, `@ 1d8 slashing`, `Properties: Finesse, ...`. */
data class Weapon(
    val name: String,
    val martial: Boolean,
    val ranged: Boolean,
    /** `1d8`; a Blowgun's `1`. */
    val damage: String,
    val damageType: String,
    val properties: List<String>,
) {
    val finesse: Boolean get() = properties.any { it.equals("Finesse", ignoreCase = true) }

    companion object {
        private val META = Regex("""^(Simple|Martial) (Melee|Ranged) Weapons""")
        private val DAMAGE = Regex("""^@ (\d+(?:d\d+)?) (\w+)""", RegexOption.MULTILINE)

        /** The weapon in an Equipment entry; null if the entry isn't one. */
        fun of(e: Entry): Weapon? {
            val kind = META.find(e.meta) ?: return null
            val dmg = DAMAGE.find(e.body) ?: return null
            val props = e.body.lines().firstOrNull { it.startsWith("Properties:") }
                ?.removePrefix("Properties:")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            return Weapon(e.name, kind.groupValues[1] == "Martial", kind.groupValues[2] == "Ranged", dmg.groupValues[1], dmg.groupValues[2], props)
        }
    }
}

private val NAMED_BONUS = Regex("""(?:,\s*|\s+)\+(\d)$""")

/** "Longsword, +1" and "Weapon +1" → +1: the bonus a magic weapon has in its name. */
fun namedBonus(name: String): Int = NAMED_BONUS.find(name.trim())?.groupValues?.get(1)?.toInt() ?: 0

/** The SRD weapon an item is: as set on it, or its name less a "+1". */
fun weaponName(item: Item): String = item.weapon ?: item.name.trim().replace(NAMED_BONUS, "")

/** Proficient with [w]: Simple or Martial Weapons, or the weapon by name ("Rapiers"); nothing recorded counts as yes. */
fun Character.proficientWith(w: Weapon): Boolean {
    if (training.isEmpty()) return true
    val t = training.map { it.trim().lowercase() }
    val group = if (w.martial) "martial weapons" else "simple weapons"
    return group in t || w.name.lowercase() in t || "${w.name.lowercase()}s" in t
}

/**
 * Attacking with [item], which is [w]: STR, or DEX for a ranged weapon, the better of the two with
 * Finesse; the proficiency bonus if trained; the item's magic bonus to hit and to damage; and its
 * extra damage dice (a Flame Tongue's 2d6), which a critical hit doubles like the weapon's own.
 */
fun Character.weaponAttack(item: Item, w: Weapon): Attack {
    val ability = when {
        w.ranged -> Ability.DEX
        w.finesse -> if (mod(Ability.DEX) > mod(Ability.STR)) Ability.DEX else Ability.STR
        else -> Ability.STR
    }
    val magic = item.magicBonus ?: namedBonus(item.name)
    val toHit = mod(ability) + (if (proficientWith(w)) proficiency else 0) + magic
    val extra = DiceExpr.parse(item.extraDamage)?.terms.orEmpty()
    val terms = DiceExpr.parse(w.damage)!!.terms + extra + DiceExpr.Flat(mod(ability)) + DiceExpr.Flat(magic)
    return Attack(item.name, toHit, DiceExpr(DiceExpr.merge(terms)), ability, w.damageType)
}

private val SPELL_DAMAGE = Regex("""(\d+)d(\d+) (?:(\w+) )?damage""")
private val CANTRIP_UPGRADE = Regex("""Cantrip Upgrade\. The damage increases by""")

/**
 * A spell that makes a spell attack, as [Attack]: the casting ability plus proficiency to hit, and
 * the first damage after "spell attack", scaled for a cantrip whose damage grows at levels 5, 11
 * and 17. Null if it isn't an attack, or the class doesn't cast.
 */
fun Character.spellAttack(spell: Entry): Attack? {
    val ability = castingAbility(className) ?: return null
    val at = spell.body.indexOf("spell attack", ignoreCase = true).takeIf { it >= 0 } ?: return null
    val toHit = mod(ability) + proficiency
    val dmg = SPELL_DAMAGE.find(spell.body, at)
    val damage = dmg?.let { m ->
        val tier = if (spell.level == 0 && CANTRIP_UPGRADE.containsMatchIn(spell.body)) 1 + listOf(5, 11, 17).count { level >= it } else 1
        DiceExpr(listOf(DiceExpr.Dice(m.groupValues[1].toInt() * tier, m.groupValues[2].toInt())))
    }
    return Attack(spell.name, toHit, damage, ability, dmg?.groupValues?.get(3)?.lowercase().orEmpty())
}
