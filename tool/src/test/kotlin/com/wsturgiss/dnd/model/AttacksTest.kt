package com.wsturgiss.dnd.model

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttacksTest {

    /** Hands out the given faces in order, so a roll is known in advance. */
    private class Faces(vararg val v: Int) : Random() {
        private var i = 0
        override fun nextBits(bitCount: Int): Int = error("unused")
        override fun nextInt(from: Int, until: Int): Int = v[i++].also { require(it in from until until) }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun srd(file: String): List<Entry> = json.decodeFromString(java.io.File("src/main/assets/srd/$file.json").readText())
    private val equipment by lazy { srd("equipment") }
    private val spells by lazy { srd("spells") }
    private fun weapon(name: String) = Weapon.of(equipment.single { it.name == name })!!
    private fun spell(name: String) = spells.single { it.name == name }

    private fun fighter(vararg s: Pair<Ability, Int>) =
        Character(className = "Fighter", scores = Ability.entries.associateWith { 10 } + s, training = listOf("Simple Weapons", "Martial Weapons"))

    private fun e(s: String) = DiceExpr.parse(s)!!

    @Test
    fun aNatural20RollsDoubledDamageStraightAway() {
        // d20 20, then the damage 2d8+3: 4 and 6
        val out = rollAttack(e("d20+5"), e("1d8+3"), Edge.None, Faces(20, 4, 6))
        assertEquals(AttackResult.CriticalHit, out.result)
        val dmg = assertNotNull(out.damage)
        assertEquals("2d8+3", dmg.expr.toString())
        assertEquals(4 + 6 + 3, dmg.total)
        assertEquals("[4 6] +3", dmg.detail)
    }

    @Test
    fun aNatural1MissesWithNoDamage() {
        val out = rollAttack(e("d20+30"), e("1d8+3"), Edge.None, Faces(1))
        assertEquals(AttackResult.Miss, out.result)
        assertNull(out.damage)
        assertFalse(out.attack.hits(ac = 5))
    }

    @Test
    fun otherwiseTheDamageWaitsForAHit() {
        val out = rollAttack(e("d20+5"), e("1d8+3"), Edge.None, Faces(14))
        assertNull(out.result)
        assertNull(out.damage)
        assertEquals(19, out.attack.total)
        // rolled on a hit, not doubled
        assertEquals(5 + 3, rollDamage(e("1d8+3"), critical = false, Faces(5)).total)
        // no damage known (the plain spell attack): a crit is still a crit
        val bare = rollAttack(e("d20+5"), null, Edge.None, Faces(20))
        assertEquals(AttackResult.CriticalHit, bare.result)
        assertNull(bare.damage)
    }

    @Test
    fun advantageKeepsTheNatural20() {
        val out = rollAttack(e("d20+5"), e("1d6"), Edge.Advantage, Faces(5, 20, 3, 2))
        assertEquals(AttackResult.CriticalHit, out.result)
        assertEquals(5, out.damage!!.total)
        // with disadvantage the 20 isn't kept
        assertNull(rollAttack(e("d20+5"), e("1d6"), Edge.Disadvantage, Faces(5, 20)).damage)
    }

    @Test
    fun everyShippedWeaponReads() {
        val weapons = equipment.mapNotNull { Weapon.of(it) }
        // every weapon the SRD lists reads, Blowgun's flat 1 too
        assertEquals(equipment.count { Regex("^(Simple|Martial) (Melee|Ranged) Weapons").containsMatchIn(it.meta) }, weapons.size)
        assertTrue(weapons.size > 30)
        assertTrue(weapons.all { DiceExpr.parse(it.damage) != null }, weapons.toString())
        assertEquals(Weapon("Longsword", true, false, "1d8", "slashing", listOf("Versatile")), weapon("Longsword"))
        assertEquals("1", weapon("Blowgun").damage)
        assertTrue(weapon("Rapier").finesse)
        assertTrue(weapon("Longbow").ranged)
        assertNull(Weapon.of(equipment.single { it.name == "Rope" }))
    }

    @Test
    fun weaponAttackAddsAbilityProficiencyAndMagic() {
        val c = fighter(Ability.STR to 16, Ability.DEX to 14)
        val sword = Item(1, "Longsword", srdKind = "equipment")
        val a = c.weaponAttack(sword, weapon("Longsword"))
        assertEquals(3 + 2, a.toHit)
        assertEquals("d8+3", a.damage.toString())
        assertEquals(Ability.STR, a.ability)
        // finesse takes the better, ranged takes DEX
        assertEquals(Ability.STR, c.weaponAttack(Item(2, "Rapier"), weapon("Rapier")).ability)
        assertEquals(Ability.DEX, fighter(Ability.DEX to 18).weaponAttack(Item(2, "Rapier"), weapon("Rapier")).ability)
        assertEquals("d8+2", c.weaponAttack(Item(3, "Longbow"), weapon("Longbow")).damage.toString())
        // no training with it: no proficiency bonus
        val wizard = c.copy(training = listOf("Simple Weapons"))
        assertEquals(3, wizard.weaponAttack(sword, weapon("Longsword")).toHit)
        assertEquals(3 + 2, wizard.copy(training = listOf("Longswords")).weaponAttack(sword, weapon("Longsword")).toHit)
        // a negative modifier comes off the damage
        assertEquals("d8-1", fighter(Ability.STR to 8).weaponAttack(sword, weapon("Longsword")).damage.toString())
    }

    @Test
    fun aMagicWeaponsBonusComesFromItsNameOrTheItem() {
        val c = fighter(Ability.STR to 16)
        assertEquals(1, namedBonus("Longsword, +1"))
        assertEquals(2, namedBonus("Weapon +2"))
        assertEquals(0, namedBonus("Longsword"))
        assertEquals("Longsword", weaponName(Item(1, "Longsword, +1")))
        val plusOne = c.weaponAttack(Item(1, "Longsword, +1"), weapon("Longsword"))
        assertEquals(3 + 2 + 1, plusOne.toHit)
        assertEquals("d8+4", plusOne.damage.toString())
        // the SRD's "Weapon +1" is whichever weapon it's set to be
        val any = Item(2, "Weapon +1", srdKind = "magic-items", weapon = "Shortsword")
        assertEquals("Shortsword", weaponName(any))
        assertEquals("d6+4", c.weaponAttack(any, weapon("Shortsword")).damage.toString())
        // set on the item, it wins over the name (Holy Avenger is +3)
        assertEquals("d8+6", c.weaponAttack(Item(3, "Holy Avenger", weapon = "Longsword", magicBonus = 3), weapon("Longsword")).damage.toString())
    }

    @Test
    fun aCriticalDoublesExtraDamageDiceButNotFlatBonuses() {
        // a +1 Flame Tongue longsword: 1d8 + 2d6 fire + STR 3 + 1
        val c = fighter(Ability.STR to 16)
        val tongue = Item(1, "Flame Tongue, +1", weapon = "Longsword", extraDamage = "2d6")
        val a = c.weaponAttack(tongue, weapon("Longsword"))
        assertEquals("d8+2d6+4", a.damage.toString())
        assertEquals("2d8+4d6+4", a.damage!!.critical().toString())
        // d20 20, then 2d8: 1 8, then 4d6: 2 3 4 5
        val out = rollAttack(a.expr, a.damage, Edge.None, Faces(20, 1, 8, 2, 3, 4, 5))
        assertEquals(1 + 8 + 2 + 3 + 4 + 5 + 4, out.damage!!.total)
        // a hit that isn't a crit: dice once
        assertEquals(6 + 2 + 3 + 4, rollDamage(a.damage!!, critical = false, Faces(6, 2, 3)).total)
    }

    @Test
    fun spellAttacksUseTheCastingAbilityAndScaleCantrips() {
        val wiz = Character(className = "Wizard", level = 1, scores = Ability.entries.associateWith { 10 } + (Ability.INT to 16))
        val bolt = wiz.spellAttack(spell("Fire Bolt"))!!
        assertEquals(3 + 2, bolt.toHit)
        assertEquals("d10", bolt.damage.toString())
        assertEquals("fire", bolt.damageType)
        assertEquals(Ability.INT, bolt.ability)
        assertEquals("2d10", wiz.copy(level = 5).spellAttack(spell("Fire Bolt"))!!.damage.toString())
        assertEquals("4d10", wiz.copy(level = 17).spellAttack(spell("Fire Bolt"))!!.damage.toString())
        // Eldritch Blast grows in beams, not dice: each is still 1d10
        val lock = Character(className = "Warlock", level = 5)
        assertEquals("d10", lock.spellAttack(spell("Eldritch Blast"))!!.damage.toString())
        // a level 1 spell doesn't scale; one with no spell attack isn't one
        assertEquals("4d6", Character(className = "Cleric", level = 5).spellAttack(spell("Guiding Bolt"))!!.damage.toString())
        assertNull(wiz.spellAttack(spell("Magic Missile")))
        // a class that doesn't cast has no spell attack
        assertNull(fighter().spellAttack(spell("Fire Bolt")))
        // crit: the spell's dice twice
        assertEquals("2d10", bolt.damage!!.critical().toString())
    }

    @Test
    fun untrainedArmorGivesAttacksDisadvantage() {
        val wizard = Character(className = "Wizard", wearing = "Plate Armor")
        assertEquals(listOf("Plate Armor, untrained"), wizard.disadvantageFrom(D20Test.AttackRoll(Ability.STR)))
        assertEquals(emptyList(), wizard.disadvantageFrom(D20Test.AttackRoll(Ability.INT)))
    }

    @Test
    fun oldItemsLoadWithoutTheWeaponFields() {
        val item = json.decodeFromString<Item>("""{"id":1,"name":"Longsword","qty":1}""")
        assertNull(item.weapon)
        assertNull(item.magicBonus)
        assertEquals("", item.extraDamage)
        val roll = json.decodeFromString<LoggedRoll>("""{"label":"x","expr":"d20","detail":"[3]","total":3}""")
        assertFalse(roll.attack)
        assertNull(roll.damage)
    }
}
