package com.wsturgiss.dnd.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DdbImportTest {

    private val hagar: Character by lazy {
        DdbImport.parse(java.io.File("src/test/resources/ddb/hagar-141442128.json").readText())
    }

    @Test
    fun hagarComesAcross() {
        val c = hagar
        assertEquals("Hagar the Horrible", c.name)
        assertEquals("Fighter", c.className)
        assertEquals("Champion", c.subclass)
        assertEquals(4, c.level)
        assertEquals("Human", c.species)
        assertEquals("Soldier", c.background)
        // 18 STR +2 (Soldier); 11 CON +1 +1 (ASI) +1 (Soldier)
        assertEquals(
            mapOf(Ability.STR to 20, Ability.DEX to 11, Ability.CON to 14, Ability.INT to 9, Ability.WIS to 8, Ability.CHA to 10),
            c.scores,
        )
        // 28 base + 2 CON × 4 levels − 6 bonus
        assertEquals(30, c.maxHp)
        assertEquals(30, c.hp)
        assertEquals(0, c.tempHp)
        assertEquals(setOf(Ability.STR, Ability.CON), c.saves)
        assertEquals(
            mapOf(Skill.Athletics to Training.Proficient, Skill.Acrobatics to Training.Proficient, Skill.Intimidation to Training.Proficient, Skill.Performance to Training.Proficient),
            c.skills,
        )
        assertEquals(listOf("Ability Score Improvement", "Great Weapon Fighting", "Savage Attacker", "Skilled"), c.feats.map { it.name })
        assertEquals("Fighting Style", c.feats.single { it.name == "Great Weapon Fighting" }.note)
        assertEquals(listOf("Common", "Elvish", "Dwarvish"), c.languages)
        assertTrue("Martial Weapons" in c.training && "Thieves' Tools" in c.training)
        assertEquals(30, c.speed)
        assertTrue(c.inspiration)
        assertTrue("Sword gets stuck" in c.notes, c.notes)
    }

    @Test
    fun hagarsArmorWorksTheSheetsWay() {
        val c = hagar
        // Plate on; the +1 Chain Mail is in the backpack, so its +1 doesn't count
        assertEquals("Plate Armor", c.wearing)
        assertFalse(c.shield)
        assertEquals(0, c.acBonus)
        assertEquals(18, c.ac)
        val mail = c.items.single { it.name == "Chain Mail" }
        assertEquals(true, mail.stealthDisadvantage)
        assertFalse(mail.equipped)
        assertEquals(true, c.items.single { it.name == "Plate Armor" }.stealthDisadvantage)
        assertEquals(listOf("Plate Armor"), c.stealthDisadvantageFrom)
        assertTrue("Chain Mail, +1 is in the bag as Chain Mail; its +1 goes in AC bonus while worn." in c.notes, c.notes)
    }

    @Test
    fun hagarsBagAndCoins() {
        val c = hagar
        assertEquals(setOf("Greatsword", "Plate Armor"), c.items.filter { it.equipped }.map { it.name }.toSet())
        assertEquals(1, c.items.count { it.equipped && it.name == "Greatsword" })
        // the backpack's contents and the custom items too
        assertEquals(10, c.items.single { it.name == "Torch" }.qty)
        assertEquals(20, c.items.single { it.name == "Caltrops" }.qty)
        assertEquals(4, c.items.single { it.name == "Magic Beans" }.qty)
        assertEquals(19, c.items.size)
        assertEquals(c.items.size, c.items.map { it.id }.toSet().size)
        assertEquals(mapOf(Coin.GP to 7, Coin.EP to 1), c.coins)
    }

    @Test
    fun idFromANumberOrALink() {
        assertEquals(141442128L, DdbImport.parseId("141442128"))
        assertEquals(141442128L, DdbImport.parseId(" https://www.dndbeyond.com/characters/141442128/xSTvGo "))
        assertEquals(141442128L, DdbImport.parseId("Https://dndbeyond.com/characters/141442128"))
        assertEquals(141442128L, DdbImport.parseId("https://character-service.dndbeyond.com/character/v5/character/141442128"))
        assertEquals(141442128L, DdbImport.parseId("www.dndbeyond.com/profile/someone/characters/141442128"))
        assertEquals(9876543210L, DdbImport.parseId("9876543210"))
        assertNull(DdbImport.parseId(""))
        assertNull(DdbImport.parseId("0"))
        assertNull(DdbImport.parseId("https://www.dndbeyond.com/characters"))
        assertNull(DdbImport.parseId("hagar"))
    }

    @Test
    fun missingFieldsTakeDefaults() {
        val c = DdbImport.parse("""{"success":true,"data":{"name":"Bare"}}""")
        assertEquals("Bare", c.name)
        assertEquals(1, c.level)
        assertTrue(c.scores.values.all { it == 10 })
        assertTrue(c.items.isEmpty() && c.coins.isEmpty() && c.feats.isEmpty())
        assertNull(c.wearing)
        assertTrue(c.maxHp >= 1 && c.hp == c.maxHp)

        // odd shapes and nulls everywhere don't throw
        val odd = DdbImport.parse(
            """{"data":{"name":null,"stats":[{"id":1,"value":null},{"id":"x"}],"classes":[{"level":3}],""" +
                """"inventory":[{"definition":null},{"definition":{"name":"Plate","armorTypeId":3},"equipped":true}],""" +
                """"modifiers":{"race":"nope","feat":[{"type":"bonus","subType":"dexterity-score","value":2}]},"currencies":null,"race":{}}}""",
        )
        assertEquals("Nameless", odd.name)
        assertEquals(3, odd.level)
        assertEquals(12, odd.score(Ability.DEX))
        assertEquals("Plate Armor", odd.wearing)
    }

    @Test
    fun anOverrideWinsAndTheCapHolds() {
        val c = DdbImport.parse(
            """{"data":{"stats":[{"id":1,"value":19},{"id":2,"value":15}],"bonusStats":[{"id":2,"value":1}],""" +
                """"overrideStats":[{"id":2,"value":7}],"overrideHitPoints":44,"removedHitPoints":50,"temporaryHitPoints":5,""" +
                """"modifiers":{"feat":[{"type":"bonus","subType":"strength-score","value":2}]}}}""",
        )
        assertEquals(20, c.score(Ability.STR))
        assertEquals(7, c.score(Ability.DEX))
        assertEquals(44, c.maxHp)
        assertEquals(0, c.hp)
        assertEquals(5, c.tempHp)
    }

    @Test
    fun aRefusalIsAPlainMessage() {
        val e = assertFailsWith<DdbImport.Failure> { DdbImport.parse("""{"success":false,"message":"Character is private"}""") }
        assertEquals("Character is private", e.message)
        assertFailsWith<DdbImport.Failure> { DdbImport.parse("<html>not json</html>") }
        assertFailsWith<DdbImport.Failure> { DdbImport.parse("[]") }
    }

    @Test
    fun oldSavesLoadWithoutTheNewFields() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = """{"characters":[{"name":"Will","items":[{"id":1,"name":"Rope","qty":1}]}],"active":0}"""
        val c = json.decodeFromString<SaveFile>(old).characters.single()
        assertEquals("", c.notes)
        assertFalse(c.inspiration)
        assertFalse(c.items.single().equipped)
    }
}
