package com.wsturgiss.dnd.model

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {

    @Test
    fun suggestedFighterIsReadyButForNameAndSkills() {
        val d = Draft()
        assertEquals(listOf("a name", "2 fighter skills", "1 human skill"), d.missing)
        // standard array by the fighter's priority, then Soldier's +2 STR +1 CON
        assertEquals(17, d.scores[Ability.STR])
        assertEquals(15, d.scores[Ability.CON])
        assertEquals(10 + 2, d.maxHp)
    }

    @Test
    fun buildsTheSheet() {
        val d = Draft(
            name = "Tor",
            className = "Barbarian",
            species = "Dwarf",
            background = "Soldier",
            picks = mapOf("class" to setOf(Skill.Perception, Skill.Survival)),
        )
        assertTrue(d.missing.isEmpty(), d.missing.toString())
        val c = d.build()
        assertEquals(setOf(Ability.STR, Ability.CON), c.saves)
        assertEquals(setOf(Skill.Athletics, Skill.Intimidation, Skill.Perception, Skill.Survival), c.skills.keys)
        // d12 + CON +2 + Dwarven Toughness
        assertEquals(15, c.maxHp)
        // Unarmored Defense: 10 + DEX +1 + CON +2
        assertEquals(13, c.ac)
        assertTrue(c.slots.all { it.max == 0 })
    }

    @Test
    fun higherStartingLevelTakesTheAverage() {
        val d = Draft(className = "Wizard", level = 3, base = Ability.entries.associateWith { 10 }, bonus = emptyMap())
        assertEquals(6 + 2 * 4, d.maxHp)
        assertEquals(listOf(4, 2), slotsFor(d.cls, 3))
    }

    @Test
    fun armor() {
        val scores = mapOf(Ability.DEX to 18, Ability.WIS to 16)
        val monk = classInfo("Monk")
        assertEquals(10 + 4 + 3, armorClass(ARMOR.first(), false, scores, monk))
        assertEquals(10 + 4 + 2, armorClass(ARMOR.first(), true, scores, monk))
        assertEquals(15 + 2, armorClass(ARMOR.first { it.name == "Half-Plate Armor" }, false, scores, null))
        assertEquals(18 + 2, armorClass(ARMOR.first { it.name == "Plate Armor" }, true, scores, null))
    }

    @Test
    fun slotTables() {
        val paladin = classInfo("Paladin")
        assertEquals(listOf(2), slotsFor(paladin, 1))
        assertEquals(listOf(4, 2), slotsFor(paladin, 5))
        assertEquals(listOf(4, 3, 3, 3, 2), slotsFor(paladin, 20))
        val warlock = classInfo("Warlock")
        assertEquals(listOf(1), slotsFor(warlock, 1))
        assertEquals(listOf(0, 0, 2), slotsFor(warlock, 5))
        assertEquals(listOf(0, 0, 0, 0, 4), slotsFor(warlock, 17))
        assertEquals(emptyList(), slotsFor(classInfo("Rogue"), 5))
    }

    @Test
    fun pointBuy() {
        assertEquals(27, listOf(15, 15, 15, 8, 8, 8).sumOf { pointCost(it)!! })
        assertNull(pointCost(16))
    }

    @Test
    fun rolledScoresDropTheLowest() {
        repeat(50) {
            rollScores(Random(it)).forEach { dice ->
                assertEquals(dice.sortedDescending(), dice)
                assertEquals(dice.take(3).sum(), keptTotal(dice))
            }
        }
    }

    @Test
    fun backgroundBonusOptions() {
        val opts = backgroundBonuses(listOf(Ability.STR, Ability.DEX, Ability.CON))
        assertEquals(7, opts.size)
        assertTrue(opts.all { it.values.sum() == 3 })
    }

    private val rogue = Character(
        className = "Rogue",
        level = 3,
        scores = Ability.entries.associateWith { 10 } + (Ability.CON to 13),
        skills = mapOf(Skill.Stealth to Training.Proficient, Skill.Perception to Training.Proficient),
        maxHp = 20,
        hp = 12,
    )

    @Test
    fun levelPlanKnowsWhatComes() {
        val four = rogue.levelPlan()!!
        assertTrue(four.asi)
        assertEquals(0, four.expertise)
        val six = rogue.copy(level = 5).levelPlan()!!
        assertEquals(2, six.expertise)
        assertEquals(3, six.newProficiency)
        assertTrue(rogue.copy(level = 9).levelPlan()!!.asi)
        assertFalse(classInfo("Wizard")!!.let { rogue.copy(className = it.name, level = 9).levelPlan()!!.asi })
        assertNull(rogue.copy(level = 20).levelPlan())
    }

    @Test
    fun levelUpAddsHpScoresAndExpertise() {
        val up = rogue.levelUp(hitRoll = 5, raise = mapOf(Ability.CON to 1), expertise = setOf(Skill.Stealth))
        assertEquals(4, up.level)
        assertEquals(14, up.score(Ability.CON))
        // 5 + CON +2, and +1 for each of the three earlier levels now CON's modifier is up
        assertEquals(20 + 7 + 3, up.maxHp)
        assertEquals(12 + 10, up.hp)
        assertEquals(Training.Expertise, up.training(Skill.Stealth))
    }

    @Test
    fun levelUpCapsAndKeepsSpentSlots() {
        val cleric = Character(
            className = "Cleric",
            level = 2,
            scores = Ability.entries.associateWith { 20 },
            slots = listOf(Slots(3, 2)) + List(8) { Slots() },
        )
        val up = cleric.levelUp(1, mapOf(Ability.WIS to 2), emptySet())
        assertEquals(20, up.score(Ability.WIS))
        assertEquals(Slots(4, 2), up.slots[0])
        assertEquals(Slots(2, 0), up.slots[1])
        // never less than one a level
        assertEquals(cleric.maxHp + 6, up.maxHp)
        // an Epic Boon may go past 20
        assertEquals(21, cleric.copy(level = 18).levelUp(1, mapOf(Ability.STR to 1), emptySet(), cap = 30).score(Ability.STR))
    }

    private val gear: Map<String, List<GearOption>> = Json.decodeFromString(
        """{
        "Fighter": [
          {"i": [["Chain Mail", 1, 55.0, true], ["Greatsword", 1, 6.0, true]], "gp": 4},
          {"i": [["Studded Leather Armor", 1, 13.0, true], ["Arrows", 20, 0.05, true], ["Quiver", 1, 1.0, true]], "gp": 11},
          {"i": [], "gp": 155}],
        "Soldier": [
          {"i": [["Arrows", 20, 0.05, true], ["Gaming Set", 1, 0.0, false, [["Dice", 0], ["Playing Cards", 0]]], ["Quiver", 1, 1.0, true]], "gp": 14},
          {"i": [], "gp": 50}]
        }""",
    )

    @Test
    fun startingGearDressesAndFillsTheBag() {
        val a = Draft(name = "Hagar").dressed(gear)
        assertEquals("Chain Mail", a.armor)
        assertEquals(16, a.ac)
        val c = a.build(gear)
        assertEquals(listOf("Chain Mail", "Greatsword", "Arrows", "Gaming Set", "Quiver"), c.items.map { it.name })
        assertEquals(null, c.items.first { it.name == "Gaming Set" }.srdKind)
        assertEquals(mapOf(Coin.GP to 18), c.coins)
        assertEquals("Chain Mail, Greatsword, 4 GP", gear.getValue("Fighter")[0].label)
        assertEquals(listOf("Chain Mail, etc.", "Studded Leather Armor, etc.", "155 GP"), gearNames(gear.getValue("Fighter")))
        assertEquals(listOf("20 Arrows, etc.", "50 GP"), gearNames(gear.getValue("Soldier")))
        assertEquals("2 Daggers, 10 Parchment, 2 Pouches", GearOption(
            listOf(GearItem("Dagger", 2, 1.0, true), GearItem("Parchment", 10, 0.0, true), GearItem("Pouch", 2, 1.0, true)), 0,
        ).label)
    }

    @Test
    fun startingGearChoicesBecomeWhatYouPick() {
        val d = Draft()
        val set = d.gearChoices(gear).single()
        assertEquals("Gaming Set", set.name)
        assertEquals(listOf("Dice", "Playing Cards"), set.choices.map { it.name })
        assertTrue("Gaming Set" in d.bag(gear).map { it.name })
        val picked = d.copy(gearPicks = mapOf("Gaming Set" to set.choices[0])).build(gear)
        assertEquals("equipment", picked.items.single { it.name == "Dice" }.srdKind)
        assertFalse(picked.items.any { it.name == "Gaming Set" })
        val typed = d.copy(gearPicks = mapOf("Gaming Set" to GearItem("Birdpipes", 1, 0.0, false))).build(gear)
        assertEquals(null, typed.items.single { it.name == "Birdpipes" }.srdKind)
    }

    @Test
    fun theShippedGearFileReads() {
        val shipped: Map<String, List<GearOption>> =
            Json.decodeFromString(java.io.File("src/main/assets/srd/starting-gear.json").readText())
        assertEquals((CLASS_INFO.map { it.name } + BACKGROUNDS.filterNot { it.custom }.map { it.name }).toSet(), shipped.keys)
        assertEquals(listOf("Musical Instrument"), Draft(className = "Bard", background = "Sage").gearChoices(shipped).map { it.name })
        // every class's kit is armor it can wear: the Sorcerer's spear fix
        assertEquals("No armor", Draft(className = "Sorcerer").dressed(shipped).armor)
    }

    @Test
    fun startingGearMergesAndGoldBringsNothing() {
        val b = Draft(classGear = 1).dressed(gear)
        assertEquals("Studded Leather Armor", b.armor)
        val bag = b.bag(gear).associate { it.name to it.count }
        assertEquals(40, bag["Arrows"])
        assertEquals(2, bag["Quiver"])
        // gold only: nothing to wear, nothing in the bag
        val gold = Draft(classGear = 2, backgroundGear = 1).dressed(gear)
        assertEquals("No armor", gold.armor)
        assertFalse(gold.shield)
        assertTrue(gold.bag(gear).isEmpty())
        assertEquals(205, gold.gold(gear))
    }

    @Test
    fun acFollowsWhatYouWear() {
        val leather = Item(1, "Leather Armor", 1, 10.0, "equipment")
        val shield = Item(2, "Shield", 1, 6.0, "equipment")
        val c = Character(scores = Ability.entries.associateWith { 10 } + (Ability.DEX to 16), items = listOf(leather, shield))
        assertEquals(13, c.ac)
        val dressed = c.toggleWorn(leather).toggleWorn(shield)
        assertEquals(11 + 3 + 2, dressed.ac)
        assertTrue(dressed.isWorn(leather))
        assertEquals(13, dressed.toggleWorn(leather).toggleWorn(shield).ac)
        // a DEX increase at level up shows straight away
        assertEquals(17, dressed.copy(level = 3).levelUp(5, mapOf(Ability.DEX to 2), emptySet()).ac)
        assertFalse(c.wearable(Item(3, "Rope", 1, 5.0)))
    }

    @Test
    fun aTypedAcIsKept() {
        // AC 14 typed, DEX 17, no armor recorded: 13 from DEX, the other 1 becomes the bonus
        val old = Character(scores = Ability.entries.associateWith { 10 } + (Ability.DEX to 17), legacyAc = 14)
        val now = old.fromTypedAc()
        assertEquals(14, now.ac)
        assertEquals(1, now.acBonus)
        assertNull(now.legacyAc)
        assertEquals(now, now.fromTypedAc())
        // made by the builder before: chain mail and a shield in the bag, 18 typed
        val built = Character(
            scores = Ability.entries.associateWith { 10 },
            items = listOf(Item(-1, "Chain Mail", 1, 55.0), Item(-2, "Shield", 1, 6.0)),
            legacyAc = 18,
        ).fromTypedAc()
        assertEquals("Chain Mail", built.wearing)
        assertTrue(built.shield)
        assertEquals(0, built.acBonus)
    }

    private val made: Creation by lazy { Json.decodeFromString(java.io.File("src/main/assets/srd/creation.json").readText()) }

    @Test
    fun theClassTableMatchesTheSrd() {
        CLASS_INFO.forEach { c ->
            val srd = made.classes.getValue(c.name)
            assertEquals(srd.hitDie, c.hitDie, c.name)
            assertEquals(srd.saves.toSet(), c.saves.map { it.short }.toSet(), c.name)
            assertEquals(srd.skills.toSet(), c.skillChoices.map { it.label }.toSet(), c.name)
            assertEquals(srd.skillCount, c.skillCount, c.name)
        }
    }

    @Test
    fun aHumanSoldierFighterIsBuiltWhole() {
        val base = Draft(
            name = "Hagar", className = "Fighter", species = "Human", background = "Soldier",
            picks = mapOf("class" to setOf(Skill.Acrobatics, Skill.Perception), "species" to setOf(Skill.Performance)),
        )
        val missing = Building(base, made).missing
        assertTrue("an origin feat" in missing, missing.toString())
        assertTrue("a fighting style" in missing, missing.toString())
        assertTrue("2 languages" in missing, missing.toString())

        val done = Building(base.copy(versatileFeat = "Skilled", fightingStyle = "Defense", languages = setOf("Dwarvish", "Elvish"),
            picks = base.picks + ("skilled" to setOf(Skill.History, Skill.Insight, Skill.Survival))), made)
        assertEquals(emptyList(), done.missing)
        val c = done.build(emptyMap())
        assertEquals(listOf("Savage Attacker", "Skilled", "Defense"), c.feats.map { it.name })
        assertEquals(listOf("Common", "Dwarvish", "Elvish"), c.languages)
        assertTrue("Martial Weapons" in c.training)
        assertEquals(8, c.skills.size)
        // Defense: +1 only in armor
        assertEquals(c.ac, c.copy(feats = emptyList()).ac)
        assertEquals(c.copy(wearing = "Chain Mail").ac, c.copy(wearing = "Chain Mail", feats = emptyList()).ac + 1)
    }

    @Test
    fun casterCountsAndMagicInitiate() {
        val wizard = Building(Draft(className = "Wizard", background = "Sage"), made)
        assertEquals(3, wizard.cantripCount)
        assertEquals(4, wizard.preparedCount)
        assertEquals(mapOf(INITIATE_FROM_BACKGROUND to "Wizard"), wizard.initiates)
        assertTrue("3 Magic Initiate spells" in wizard.missing, wizard.missing.toString())
        val fighter3 = Building(Draft(className = "Fighter", level = 3), made)
        assertEquals("Champion", fighter3.subclass)
        assertNull(Building(Draft(className = "Fighter", level = 2), made).subclass)
        assertTrue(Building(Draft(species = "Elf"), made).missing.contains("elven lineage"))
    }

    @Test
    fun oldSavesBecomeACharacterList() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = """{"character":{"name":"Wren","level":3},"log":[],"nextItemId":4}"""
        val s = json.decodeFromString<SaveFile>(old)
        assertEquals("Wren", s.single?.name)
        assertTrue(s.characters.isEmpty())
        val two = SaveFile(characters = listOf(Character(name = "A"), Character(name = "B")), active = 1)
        assertEquals("B", two.character.name)
        val edited = two.withCharacter { it.copy(hp = 1) }
        assertEquals(listOf(10, 1), edited.characters.map { it.hp })
    }
}
