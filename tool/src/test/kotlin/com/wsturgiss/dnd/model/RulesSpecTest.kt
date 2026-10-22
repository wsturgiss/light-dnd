package com.wsturgiss.dnd.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules spec (qa-dnd-shots/rules-spec.md), one test per case, named by its id. 2024 rules.
 */
class RulesSpecTest {

    private val hagar: Character by lazy {
        DdbImport.parse(java.io.File("src/test/resources/ddb/hagar-141442128.json").readText())
    }

    private fun scores(vararg s: Pair<Ability, Int>): Map<Ability, Int> = Ability.entries.associateWith { 10 } + s
    private fun mod(score: Int) = Character(scores = scores(Ability.STR to score)).mod(Ability.STR)
    private fun draft(cls: String, level: Int, con: Int, species: String = "Human") =
        Draft(className = cls, species = species, level = level, manualScores = scores(Ability.CON to con))
    private fun worn(armor: String?, vararg s: Pair<Ability, Int>, cls: String = "Fighter", shield: Boolean = false) =
        Character(className = cls, wearing = armor, shield = shield, scores = scores(*s))

    /** Hands out the given faces in order, so a roll is known in advance. */
    private class Faces(vararg val v: Int) : Random() {
        private var i = 0
        override fun nextBits(bitCount: Int): Int = error("unused")
        override fun nextInt(from: Int, until: Int): Int = v[i++].also { require(it in from until until) }
    }

    private fun roll(expr: String, edge: Edge, vararg faces: Int) = DiceExpr.parse(expr)!!.roll(edge, Faces(*faces))

    // A. Ability modifiers

    @Test fun A1_score1IsMinus5() = assertEquals(-5, mod(1))
    @Test fun A2_score8IsMinus1() = assertEquals(-1, mod(8))
    @Test fun A3_score9IsMinus1Floored() = assertEquals(-1, mod(9))
    @Test fun A4_score10IsZero() = assertEquals(0, mod(10))
    @Test fun A5_score11IsZero() = assertEquals(0, mod(11))
    @Test fun A6_score15IsPlus2() = assertEquals(2, mod(15))
    @Test fun A7_score20IsPlus5() = assertEquals(5, mod(20))
    @Test fun A8_score30IsPlus10() = assertEquals(10, mod(30))

    // B. Manual stat limits

    @Test
    fun B1_scores0And31Rejected() {
        assertFalse(validScore(0))
        assertFalse(validScore(31))
        assertTrue(validScore(1) && validScore(30))
        assertTrue(Draft(manualScores = scores(Ability.STR to 0)).missing.any { "1 to 30" in it })
    }

    @Test
    fun B2_currentHp0AllowedMaxHpAtLeastOnePerLevel() {
        assertEquals(0, Character(maxHp = 10, hp = 5).damage(20).hp)
        // a 4th-level character can't have fewer than 4 max HP
        val typed = Draft(level = 4, manualHp = 3)
        assertTrue(typed.missing.any { "HP" in it }, "level 4 with max HP 3 was accepted: missing=${typed.missing}")
    }

    // C. HP, fixed

    @Test fun C1_fighter4Con14Is36() = assertEquals(36, draft("Fighter", 4, 14).maxHp)
    @Test fun C2_wizard1Con10Is6() = assertEquals(6, draft("Wizard", 1, 10).maxHp)
    @Test fun C3_barbarian5Con16Is55() = assertEquals(55, draft("Barbarian", 5, 16).maxHp)
    @Test fun C4_rogue3Con8Is15() = assertEquals(15, draft("Rogue", 3, 8).maxHp)
    @Test fun C5_wizard2Con3IsAtLeastOnePerLevel() = assertEquals(3, draft("Wizard", 2, 3).maxHp)

    @Test
    fun C6_rolledHpDiePlusConMin1FirstLevelMax() {
        assertEquals(10 + 2, draft("Fighter", 1, 14).maxHp)
        val fighter = draft("Fighter", 1, 14).build()
        assertEquals(12 + 7 + 2, fighter.levelUp(7, emptyMap(), emptySet()).maxHp)
        val frail = draft("Wizard", 1, 3).build()
        assertEquals(2 + 1, frail.levelUp(1, emptyMap(), emptySet()).maxHp)
    }

    @Test
    fun C7_dwarfPlusOnePerLevel() {
        assertEquals(11 + 2 * 7, draft("Fighter", 3, 10, species = "Dwarf").maxHp)
        val dwarf = draft("Fighter", 3, 10, species = "Dwarf").build()
        assertEquals(25 + 6 + 1, dwarf.levelUp(6, emptyMap(), emptySet()).maxHp)
    }

    @Test
    fun C8_toughPlusTwoPerLevel() {
        val tough = draft("Fighter", 1, 10).build().withFeats(listOf(Feat("Tough")))
        assertEquals(10 + 2, tough.maxHp)
        assertEquals(10 + 2 + 6 + 2, tough.levelUp(6, emptyMap(), emptySet()).maxHp)
        // taken later, it counts for the levels before it too; dropped, they go
        val third = draft("Fighter", 3, 10).build()
        assertEquals(third.maxHp + 6, third.withFeats(listOf(Feat("Tough"))).maxHp)
        assertEquals(third.maxHp, third.withFeats(listOf(Feat("Tough"))).withFeats(emptyList()).maxHp)
        assertEquals(10 + 2, draft("Fighter", 1, 10).copy(versatileFeat = "Tough").maxHp)
        assertEquals(10 + 2 + 2 * (6 + 2), third.withFeats(listOf(Feat("Tough"))).fixedMaxHp)
    }

    @Test
    fun C9_conChangeIsRetroactive() {
        val f = draft("Fighter", 3, 13).build()
        assertEquals(25, f.maxHp)
        // 13 -> 14 at 4th: the new level at +2, and +1 for each of the three before it
        assertEquals(36, f.levelUp(6, mapOf(Ability.CON to 1), emptySet()).maxHp)
    }

    @Test
    fun C10_multiclassOnlyFirstClassLevelIsMax() {
        // Fighter 1 / Wizard 1, CON 10: 10 + (6/2+1) = 14, not 10 + 6
        assertEquals(14, Character(className = "Fighter", level = 2, multiclass = listOf(ClassLevel("Wizard", 1))).fixedMaxHp)
        // Wizard first: 6 + (10/2+1) = 12
        assertEquals(12, Character(className = "Wizard", level = 2, multiclass = listOf(ClassLevel("Fighter", 1))).fixedMaxHp)
        // from D&D Beyond: the starting class first, the rest as multiclass levels
        val mc = DdbImport.parse(
            """{"data":{"name":"MC","classes":[
               {"level":2,"definition":{"name":"Wizard"}},
               {"level":3,"isStartingClass":true,"definition":{"name":"Fighter"}}]}}""",
        )
        assertEquals("Fighter", mc.className)
        assertEquals(listOf(ClassLevel("Wizard", 2)), mc.multiclass)
        assertEquals(3, mc.mainLevel)
        assertEquals(10 + 2 * 6 + 2 * 4, mc.fixedMaxHp)
        assertEquals("Fighter 3 / Wizard 2", mc.classLabel)
    }

    @Test
    fun C11_importShowsDdbHp() {
        // D&D Beyond's own number (base 28 + 2 CON x 4 - 6 bonus), not the fixed calculation (36)
        assertEquals(30, hagar.maxHp)
    }

    @Test
    fun C11_flagsHpThatDiffersFromFixed() {
        assertTrue("36" in hagar.notes, hagar.notes)
        assertEquals(36, hagar.fixedMaxHp)
        assertEquals("30 (36 base, -6 adjustment)", hagar.hpBreakdown)
        assertTrue("Max HP 30 (36 base, -6 adjustment): on D&D Beyond it has a -6 HP modifier." in hagar.notes, hagar.notes)
        // HP that matches the table isn't flagged
        val plain = DdbImport.parse(
            """{"data":{"name":"X","classes":[{"level":4,"definition":{"name":"Fighter"}}],"stats":[{"id":3,"value":14}],"baseHitPoints":28}}""",
        )
        assertNull(plain.hpBreakdown)
        assertFalse("Max HP" in plain.notes, plain.notes)
        // an override says so
        val set = DdbImport.parse("""{"data":{"name":"X","classes":[{"level":1,"definition":{"name":"Wizard"}}],"overrideHitPoints":9}}""")
        assertTrue("Max HP 9 (6 base, +3 adjustment): on D&D Beyond max HP is set by hand." in set.notes, set.notes)
    }

    // D. AC

    @Test
    fun D1_plateIgnoresDexShieldAddsTwo() {
        assertEquals(18, worn("Plate Armor", Ability.DEX to 11).ac)
        assertEquals(20, worn("Plate Armor", Ability.DEX to 11, shield = true).ac)
    }

    @Test fun D2_halfPlateCapsDexAt2() = assertEquals(17, worn("Half-Plate Armor", Ability.DEX to 18).ac)
    @Test fun D3_leatherAddsFullDex() = assertEquals(15, worn("Leather Armor", Ability.DEX to 18).ac)
    @Test fun D4_unarmoredIs10PlusDex() = assertEquals(12, worn(null, Ability.DEX to 14).ac)
    @Test fun D5_chainMailNoDexPenalty() = assertEquals(16, worn("Chain Mail", Ability.DEX to 8).ac)

    @Test
    fun D6_barbarianUnarmoredAddsConShieldOk() {
        assertEquals(15, worn(null, Ability.DEX to 14, Ability.CON to 16, cls = "Barbarian").ac)
        assertEquals(17, worn(null, Ability.DEX to 14, Ability.CON to 16, cls = "Barbarian", shield = true).ac)
    }

    @Test
    fun D7_monkUnarmoredAddsWisNotWithArmorOrShield() {
        assertEquals(15, worn(null, Ability.DEX to 14, Ability.WIS to 16, cls = "Monk").ac)
        assertEquals(10 + 2 + 2, worn(null, Ability.DEX to 14, Ability.WIS to 16, cls = "Monk", shield = true).ac)
        assertEquals(11 + 2, worn("Leather Armor", Ability.DEX to 14, Ability.WIS to 16, cls = "Monk").ac)
    }

    // E. Stealth disadvantage

    @Test
    fun E_stealthDisadvantageByArmor() {
        val dis = listOf("Padded Armor", "Scale Mail", "Half-Plate Armor", "Ring Mail", "Chain Mail", "Splint Armor", "Plate Armor")
        val none = listOf("Leather Armor", "Studded Leather Armor", "Hide Armor", "Chain Shirt", "Breastplate")
        dis.forEach { n -> assertTrue(ARMOR.single { it.name == n }.stealthDisadvantage, n) }
        none.forEach { n -> assertFalse(ARMOR.single { it.name == n }.stealthDisadvantage, n) }
        dis.forEach { n -> assertEquals(listOf(n), worn(n).stealthDisadvantageFrom, n) }
        none.forEach { n -> assertEquals(emptyList(), worn(n).stealthDisadvantageFrom, n) }
    }

    // F. Heavy armor Strength

    @Test
    fun F1_plateStr14Speed20() {
        val c = worn("Plate Armor", Ability.STR to 14)
        assertEquals(20, c.speed)
        assertEquals(30, c.baseSpeed)
        assertEquals("Plate Armor", c.tooHeavy?.name)
        assertEquals(20, worn("Splint Armor", Ability.STR to 14).speed)
        assertEquals(20, worn("Chain Mail", Ability.STR to 12).speed)
        // Ring Mail has no Strength requirement; out of the armor, speed comes back
        assertEquals(30, worn("Ring Mail", Ability.STR to 3).speed)
        assertEquals(30, c.copy(wearing = null).speed)
        // saves keep "speed" as the base
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        assertEquals(25, json.decodeFromString<Character>("""{"name":"Old","speed":25}""").baseSpeed)
        assertTrue("\"speed\":20" in json.encodeToString(Character.serializer(), c.copy(baseSpeed = 20)))
    }

    @Test fun F2_plateStr15Speed30() = assertEquals(30, worn("Plate Armor", Ability.STR to 15).speed)
    @Test fun F3_chainMailStr13Speed30() = assertEquals(30, worn("Chain Mail", Ability.STR to 13).speed)

    @Test
    fun F4_armorWithoutTrainingPenalised() {
        val wizard = worn("Plate Armor", cls = "Wizard")
        assertEquals("Plate Armor", wizard.untrainedArmor)
        assertFalse(wizard.canCastSpells)
        assertEquals(listOf("Plate Armor, untrained"), wizard.disadvantageFrom(D20Test.Check(Ability.STR)))
        assertEquals(listOf("Plate Armor, untrained"), wizard.disadvantageFrom(D20Test.Save(Ability.DEX)))
        assertEquals(listOf("Plate Armor, untrained"), wizard.disadvantageFrom(D20Test.Initiative))
        assertEquals(listOf("Plate Armor, untrained"), wizard.disadvantageFrom(D20Test.Check(Ability.DEX, Skill.Stealth)))
        assertEquals(emptyList(), wizard.disadvantageFrom(D20Test.Check(Ability.INT, Skill.Arcana)))
        assertEquals(emptyList(), wizard.disadvantageFrom(D20Test.Save(Ability.CON)))
        // trained: only Stealth, from the armor itself
        val fighter = worn("Plate Armor")
        assertNull(fighter.untrainedArmor)
        assertTrue(fighter.canCastSpells)
        assertEquals(emptyList(), fighter.disadvantageFrom(D20Test.Check(Ability.STR)))
        assertEquals(listOf("Plate Armor"), fighter.disadvantageFrom(D20Test.Check(Ability.DEX, Skill.Stealth)))
        // training as recorded wins over the class's: Rogue light only; "All armor" as the SRD has it
        assertEquals("Chain Mail", worn("Chain Mail", cls = "Rogue").untrainedArmor)
        assertNull(worn("Leather Armor", cls = "Rogue").untrainedArmor)
        assertEquals("Leather Armor", worn("Leather Armor").copy(training = listOf("Simple Weapons")).untrainedArmor)
        assertNull(worn("Plate Armor", cls = "Wizard").copy(training = listOf("All armor")).untrainedArmor)
        assertNull(hagar.untrainedArmor)
        assertNull(worn(null, cls = "Wizard").untrainedArmor)
    }

    // G. Proficiency

    @Test
    fun G1_proficiencyTableSkillAndExpertise() {
        val table = mapOf(1 to 2, 4 to 2, 5 to 3, 8 to 3, 9 to 4, 12 to 4, 13 to 5, 16 to 5, 17 to 6, 20 to 6)
        table.forEach { (lvl, pb) -> assertEquals(pb, Character(level = lvl).proficiency, "level $lvl") }
        val c = Character(level = 5, scores = scores(Ability.DEX to 16),
            skills = mapOf(Skill.Acrobatics to Training.Proficient, Skill.Stealth to Training.Expertise))
        assertEquals(3 + 3, c.skill(Skill.Acrobatics))
        assertEquals(3 + 6, c.skill(Skill.Stealth))
        assertEquals(3, c.skill(Skill.SleightOfHand))
    }

    @Test
    fun G2_saveProficiency() {
        val c = Character(level = 5, scores = scores(Ability.DEX to 14), saves = setOf(Ability.DEX))
        assertEquals(2 + 3, c.save(Ability.DEX))
        assertEquals(0, c.save(Ability.STR))
    }

    @Test
    fun G3_fighterSavesHagarStrSaveAndAthletics() {
        assertEquals(setOf(Ability.STR, Ability.CON), classInfo("Fighter")!!.saves)
        assertEquals(setOf(Ability.STR, Ability.CON), hagar.saves)
        assertEquals(7, hagar.save(Ability.STR))
        assertEquals(Training.Proficient, hagar.training(Skill.Athletics))
        assertEquals(7, hagar.skill(Skill.Athletics))
    }

    @Test
    fun G4_jackOfAllTradesHalfProficiency() {
        val bard = Character(className = "Bard", level = 5)
        assertEquals(1, bard.skill(Skill.Athletics))
        // not on top of proficiency; not at 1st level; a multiclass bard's bard levels count
        assertEquals(3, bard.copy(skills = mapOf(Skill.Athletics to Training.Proficient)).skill(Skill.Athletics))
        assertEquals(0, Character(className = "Bard", level = 1).skill(Skill.Athletics))
        assertEquals(1, Character(className = "Fighter", level = 5, multiclass = listOf(ClassLevel("Bard", 2))).skill(Skill.Athletics))
        assertEquals(0, Character(className = "Fighter", level = 5).skill(Skill.Athletics))
        // passive Perception is a check, so it counts there too
        assertEquals(11, bard.passivePerception)
    }

    @Test
    fun G4_remarkableAthleteAdvantageOnInitiativeAndAthletics() {
        // 2024: advantage, not half proficiency, so the numbers don't change
        assertTrue(hagar.remarkableAthlete)
        assertEquals(listOf("Remarkable Athlete"), hagar.advantageFrom(D20Test.Initiative))
        assertEquals(listOf("Remarkable Athlete"), hagar.advantageFrom(D20Test.Check(Ability.STR, Skill.Athletics)))
        assertEquals(emptyList(), hagar.advantageFrom(D20Test.Check(Ability.STR)))
        assertEquals(emptyList(), hagar.advantageFrom(D20Test.Check(Ability.DEX, Skill.Acrobatics)))
        assertEquals(hagar.mod(Ability.DEX), hagar.initiative)
        assertFalse(Character(className = "Fighter", level = 2, subclass = "Champion").remarkableAthlete)
        assertFalse(Character(className = "Fighter", level = 5, subclass = "Battle Master").remarkableAthlete)
    }

    // H. Dice

    @Test
    fun H1_advantageKeepsHighDisadvantageLow() {
        assertEquals(15, roll("d20", Edge.Advantage, 7, 15).total)
        assertEquals(7, roll("d20", Edge.Disadvantage, 7, 15).total)
        assertEquals(listOf(7, 15), roll("d20", Edge.Advantage, 7, 15).edgeDice)
    }

    @Test
    fun H2_advantageAndDisadvantageCancel() {
        assertEquals(Edge.None, combinedEdge(listOf(Edge.Advantage, Edge.Disadvantage)))
        assertEquals(Edge.None, combinedEdge(listOf(Edge.Advantage, Edge.Disadvantage, Edge.Disadvantage, Edge.Disadvantage)))
        val straight = roll("d20", combinedEdge(listOf(Edge.Advantage, Edge.Disadvantage)), 9)
        assertEquals(Edge.None, straight.edge)
        assertTrue(straight.edgeDice.isEmpty())
    }

    @Test
    fun H3_attackNat20CritsNat1Misses() {
        val twenty = roll("d20+0", Edge.None, 20)
        assertEquals(AttackResult.CriticalHit, twenty.attackResult)
        assertTrue(twenty.hits(ac = 30))
        val one = roll("d20+30", Edge.None, 1)
        assertEquals(AttackResult.Miss, one.attackResult)
        assertFalse(one.hits(ac = 10))
        // otherwise the total against AC decides
        assertNull(roll("d20+5", Edge.None, 12).attackResult)
        assertTrue(roll("d20+5", Edge.None, 12).hits(ac = 17))
        assertFalse(roll("d20+5", Edge.None, 11).hits(ac = 17))
        // with advantage it's the kept die that counts
        assertEquals(AttackResult.CriticalHit, roll("d20+5", Edge.Advantage, 3, 20).attackResult)
        assertEquals(AttackResult.Miss, roll("d20+5", Edge.Disadvantage, 1, 20).attackResult)
    }

    @Test
    fun H4_checkNat20And1AreJustNumbers() {
        val high = roll("d20+0", Edge.None, 20)
        assertEquals(20, high.natural)
        assertEquals(20, high.total)
        val low = roll("d20+10", Edge.None, 1)
        assertEquals(1, low.natural)
        assertEquals(11, low.total)
    }

    @Test
    fun H5_critDoublesDiceNotModifier() {
        val crit = DiceExpr.parse("2d6+3")!!.critical()
        assertEquals("4d6+3", crit.toString())
        assertEquals(2 + 5 + 3 + 4 + 3, crit.roll(Edge.None, Faces(2, 5, 3, 4)).total)
        assertEquals("2d8+2d6-1", DiceExpr.parse("1d8+1d6-1")!!.critical().toString())
    }

    @Test
    fun H6_modifierAddedAfterKeep() {
        assertEquals(18 + 5, roll("d20+5", Edge.Advantage, 3, 18).total)
        assertEquals(3 + 5, roll("d20+5", Edge.Disadvantage, 3, 18).total)
    }

    // I. D&D Beyond import

    @Test
    fun I1_identity() {
        assertEquals("Hagar the Horrible", hagar.name)
        assertEquals("Human", hagar.species)
        assertEquals("Fighter", hagar.className)
        assertEquals("Champion", hagar.subclass)
        assertEquals(4, hagar.level)
        assertEquals("Soldier", hagar.background)
    }

    @Test
    fun I2_finalScoresNotBase() {
        // base on D&D Beyond is STR 18, CON 11
        assertEquals(20, hagar.score(Ability.STR))
        assertEquals(14, hagar.score(Ability.CON))
    }

    @Test
    fun I3_maxCurrentAndTempHp() {
        assertEquals(30, hagar.maxHp)
        assertEquals(30, hagar.hp)
        assertEquals(0, hagar.tempHp)
        val hurt = DdbImport.parse(
            """{"data":{"name":"X","classes":[{"level":4,"definition":{"name":"Fighter"}}],
               "stats":[{"id":3,"value":14}],"baseHitPoints":28,"removedHitPoints":5,"temporaryHitPoints":3}}""",
        )
        assertEquals(36, hurt.maxHp)
        assertEquals(31, hurt.hp)
        assertEquals(3, hurt.tempHp)
    }

    @Test
    fun I4_equippedArmorDrivesAc() {
        assertEquals("Plate Armor", hagar.wearing)
        assertEquals(18, hagar.ac)
    }

    @Test
    fun I5_proficiencies() {
        assertEquals(setOf(Ability.STR, Ability.CON), hagar.saves)
        // the fixture's skill proficiencies: race Performance, class Athletics + Acrobatics, background Intimidation
        assertEquals(
            setOf(Skill.Performance, Skill.Athletics, Skill.Acrobatics, Skill.Intimidation),
            hagar.skills.filterValues { it == Training.Proficient }.keys,
        )
        assertTrue(listOf("Light Armor", "Medium Armor", "Heavy Armor").all { it in hagar.training }, hagar.training.toString())
    }

    @Test fun I6_speed() = assertEquals(30, hagar.speed)

    @Test
    fun I7_multiclassLevelsSummedForProficiency() {
        val mc = DdbImport.parse(
            """{"data":{"name":"MC","classes":[
               {"level":3,"isStartingClass":true,"definition":{"name":"Fighter"}},
               {"level":2,"definition":{"name":"Wizard"}}]}}""",
        )
        assertEquals(5, mc.level)
        assertEquals(3, mc.proficiency)
    }
}
