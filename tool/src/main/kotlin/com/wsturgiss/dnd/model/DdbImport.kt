package com.wsturgiss.dnd.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A character from D&D Beyond's character service (`/character/v5/character/<id>`), mapped onto
 * [Character]. Pure, so it's tested against a saved response. Every field is optional: what's
 * missing takes the app's default, and what the sheet can't hold goes in [Character.notes].
 */
object DdbImport {

    /** Where the app fetches a character from, by its D&D Beyond ID. */
    fun url(id: Long): String = "https://character-service.dndbeyond.com/character/v5/character/$id"

    /**
     * The character ID in what was typed: the ID itself, a share link
     * (`dndbeyond.com/characters/141442128/xSTvGo`), or a character-service URL. Null if none.
     */
    fun parseId(input: String): Long? {
        val s = input.trim()
        if (s.isEmpty()) return null
        if (s.all { it.isDigit() }) return s.toLongOrNull()?.takeIf { it > 0 }
        return Regex("""characters?/(\d+)""").find(s)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
    }

    class Failure(message: String) : Exception(message)

    /**
     * What to say when the character service won't send one. It answers 401/403 for a private
     * character and for an ID that doesn't exist alike, so the message covers both.
     */
    fun httpFailure(code: Int): String = when (code) {
        401, 403, 404 -> "Couldn't load that character. It may be private, or the ID may be wrong."
        else -> "D&D Beyond answered $code. Try again later."
    }

    /** The response as text; throws [Failure] with a plain message if it isn't a character. */
    fun parse(text: String): Character {
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw Failure("D&D Beyond didn't send a character.")
        if (root["success"].bool == false) {
            throw Failure(root["message"].str?.takeIf { it.isNotBlank() } ?: "D&D Beyond refused: is the character public?")
        }
        val data = root["data"] as? JsonObject ?: root.takeIf { "stats" in it || "name" in it }
            ?: throw Failure("D&D Beyond didn't send a character.")
        return character(data)
    }

    fun character(d: JsonObject): Character {
        val notes = mutableListOf<String>()

        // classes: the starting class (or the highest) first, the rest as multiclass levels
        val classes = d["classes"].arr.mapNotNull { it as? JsonObject }
        val main = classes.firstOrNull { it["isStartingClass"].bool == true } ?: classes.maxByOrNull { it["level"].int ?: 0 }
        val level = classes.sumOf { it["level"].int ?: 0 }.coerceIn(1, 20)
        val className = main?.obj("definition")?.get("name").str ?: "Fighter"
        val subclass = main?.obj("subclassDefinition")?.get("name").str.orEmpty()
        val multiclass = classes.filter { it !== main }.mapNotNull { cl ->
            val n = cl.obj("definition")?.get("name").str ?: return@mapNotNull null
            val lv = cl["level"].int?.takeIf { it > 0 } ?: return@mapNotNull null
            ClassLevel(n, lv, cl.obj("subclassDefinition")?.get("name").str.orEmpty())
        }
        if (multiclass.isNotEmpty()) {
            notes += "Multiclass: spell slots and level-ups follow $className's table; check the slots under Edit."
        }

        val inventory = d["inventory"].arr.mapNotNull { it as? JsonObject }
        val mods = modifiers(d, inventory)

        val scores = scores(d, mods)
        val conMod = Math.floorDiv((scores[Ability.CON] ?: 10) - 10, 2)
        val perLevel = mods.filter { it.type == "bonus" && it.subType == "hit-points-per-level" }.sumOf { it.value }
        val override = d["overrideHitPoints"].int
        val hpBonus = d["bonusHitPoints"].int ?: 0
        val maxHp = (override ?: ((d["baseHitPoints"].int ?: 10) + conMod * level + hpBonus + perLevel * level))
            .coerceIn(HP_RANGE)
        val hp = (maxHp - (d["removedHitPoints"].int ?: 0)).coerceIn(0, maxHp)

        val saves = Ability.entries.filter { a -> mods.any { it.type == "proficiency" && it.subType == "${a.long.lowercase()}-saving-throws" } }.toSet()
        val skills = Skill.entries.mapNotNull { s ->
            val slug = slug(s.label)
            when {
                mods.any { it.type == "expertise" && it.subType == slug } -> s to Training.Expertise
                mods.any { it.type == "proficiency" && it.subType == slug } -> s to Training.Proficient
                else -> null
            }
        }.toMap()
        val skillSlugs = Skill.entries.map { slug(it.label) }.toSet()
        val training = (
            mods.filter {
                it.type == "proficiency" && it.subType !in skillSlugs && !it.subType.endsWith("-saving-throws") && !it.subType.startsWith("choose-")
            }.mapNotNull { it.name } +
                d["customProficiencies"].arr.mapNotNull { (it as? JsonObject)?.get("name").str }
            ).filter { it.isNotBlank() }.distinct()
        val languages = mods.filter { it.type == "language" && !it.subType.startsWith("choose-") }.mapNotNull { it.name }.distinct()

        val feats = d["feats"].arr.mapNotNull { f ->
            val def = (f as? JsonObject)?.obj("definition") ?: return@mapNotNull null
            val name = def["name"].str?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tags = def["categories"].arr.mapNotNull { (it as? JsonObject)?.get("tagName").str }
            // D&D Beyond's bookkeeping feats ("4: Weapon Mastery", "Soldier Ability Score Improvements")
            if (tags.any { it.startsWith("__") }) return@mapNotNull null
            Feat(name, if ("Fighting Style" in tags) "Fighting Style" else "")
        }.distinctBy { it.name }

        // what the player changed on an item: characterValues keyed to it by valueId (the inventory
        // entry's id) and valueTypeId (its entityTypeId); typeId 8 is a custom name, 9 its notes
        val custom = d["characterValues"].arr.mapNotNull { it as? JsonObject }
            .groupBy { (it["valueId"].str ?: it["valueId"].long?.toString()) to (it["valueTypeId"].str ?: it["valueTypeId"].long?.toString()) }
        fun customValue(inv: JsonObject, typeId: Int): String? {
            val id = inv["id"].long?.toString() ?: return null
            val type = inv["entityTypeId"].long?.toString()
            val values = custom[id to type] ?: custom.entries.filter { it.key.first == id }.flatMap { it.value }
            return values.firstOrNull { it["typeId"].int == typeId }?.get("value").str?.takeIf { it.isNotBlank() }
        }

        // the bag: everything, containers' contents too; armor named as the sheet knows it, so it can be worn
        val wornArmor = mutableListOf<String>()
        var shield = false
        var acBonus = 0
        val items = mutableListOf<Item>()
        inventory.forEach { inv ->
            val def = inv.obj("definition") ?: return@forEach
            val baseName = def["name"].str?.takeIf { it.isNotBlank() } ?: return@forEach
            val customName = customValue(inv, CUSTOM_NAME)
            val ddbName = customName ?: baseName
            val equipped = inv["equipped"].bool == true
            val armorType = def["armorTypeId"].int
            val armor = if (armorType != null && armorType in 1..3) armorFor(def["baseArmorName"].str ?: baseName) else null
            val isShield = armorType == 4
            val name = when {
                armor != null -> armor.name
                isShield -> "Shield"
                else -> ddbName
            }
            // a weapon is built on the one in definition.type ("Greatsword"), whatever it's called
            val isWeapon = def["filterType"].str == "Weapon" || def["damage"].obj != null && def["attackType"].int != null
            val weapon = if (isWeapon) def["type"].str?.takeIf { it.isNotBlank() } ?: weaponName(Item(0, baseName)) else null
            val magic = def["grantedModifiers"].arr.mapNotNull { it as? JsonObject }
                .filter { it["type"].str == "bonus" && it["subType"].str == "magic" }.sumOf { it["value"].int ?: it["fixedValue"].int ?: 0 }
            // the player's notes; a magic or homebrew item's description too (an SRD item's is in Look up)
            val description = def["description"].str?.takeIf { def["magic"].bool == true || def["isHomebrew"].bool == true }?.let(::plainText)
            val itemNotes = listOfNotNull(
                customValue(inv, CUSTOM_NOTES)?.trim(),
                description?.takeIf { it.isNotBlank() },
                customName?.takeIf { name != it }?.let { "Called $it on D&D Beyond." },
            ).joinToString("\n\n")
            val bonus = def["grantedModifiers"].arr.mapNotNull { it as? JsonObject }
                .filter { it["type"].str == "bonus" && it["subType"].str == "armor-class" }.sumOf { it["value"].int ?: it["fixedValue"].int ?: 0 }
            if (name != ddbName) {
                notes += "$ddbName is in the bag as $name" + (if (bonus != 0) "; its ${signed(bonus)} goes in AC bonus while worn." else ".")
            }
            if (equipped && armor != null) wornArmor += armor.name
            if (equipped && isShield) shield = true
            val stealth = when (def["stealthCheck"].int) {
                2 -> true
                1 -> false.takeIf { armor != null }
                else -> null
            }
            val bundle = (def["bundleSize"].int ?: 1).coerceAtLeast(1)
            items += Item(
                id = -1L - items.size,
                name = name,
                qty = (inv["quantity"].int ?: 1).coerceAtLeast(0),
                weight = (def["weight"].dbl ?: 0.0) / bundle,
                srdKind = if (def["isHomebrew"].bool == true) null else if (def["magic"].bool == true) "magic-items" else "equipment",
                stealthDisadvantage = stealth,
                equipped = equipped,
                weapon = weapon?.takeIf { it != name },
                magicBonus = magic.takeIf { it != 0 },
                notes = itemNotes,
            )
        }
        d["customItems"].arr.mapNotNull { it as? JsonObject }.forEach { ci ->
            val name = ci["name"].str?.takeIf { it.isNotBlank() } ?: return@forEach
            val itemNotes = listOfNotNull(ci["notes"].str, ci["description"].str?.let(::plainText)).filter { it.isNotBlank() }.joinToString("\n\n")
            items += Item(-1L - items.size, name, (ci["quantity"].int ?: 1).coerceAtLeast(0), ci["weight"].dbl ?: 0.0, notes = itemNotes)
        }
        // AC from equipped items: magic armor, rings; the sheet works out the armor itself
        acBonus += mods.filter { it.source == "item" && it.type == "bonus" && it.subType == "armor-class" }.sumOf { it.value }
        if (wornArmor.size > 1) notes += "More than one armor equipped on D&D Beyond; wearing ${wornArmor.first()}."

        val coins = d["currencies"].obj?.let { cur ->
            Coin.entries.mapNotNull { c -> cur[c.name.lowercase()].int?.takeIf { it > 0 }?.let { c to it } }.toMap()
        } ?: emptyMap()

        val spells = (
            d["classSpells"].arr.flatMap { (it as? JsonObject)?.get("spells").arr } +
                (d["spells"].obj?.values?.flatMap { it.arr } ?: emptyList())
            ).mapNotNull { (it as? JsonObject)?.obj("definition")?.get("name").str }.distinct()

        d["notes"].obj?.forEach { (k, v) -> v.str?.takeIf { it.isNotBlank() }?.let { notes += "${label(k)}:\n$it" } }
        d["traits"].obj?.forEach { (k, v) -> v.str?.takeIf { it.isNotBlank() }?.let { notes += "${label(k)}:\n$it" } }

        val race = d["race"].obj
        val speciesName = race?.get("baseName").str?.takeIf { speciesInfo(it) != null } ?: race?.get("fullName").str ?: race?.get("baseName").str
        val background = d["background"].obj?.let { b ->
            b.obj("definition")?.get("name").str ?: b.obj("customBackground")?.get("name").str
        }

        val c = Character(
            name = d["name"].str?.takeIf { it.isNotBlank() } ?: "Nameless",
            className = className,
            level = level,
            multiclass = multiclass,
            species = speciesName.orEmpty(),
            background = background.orEmpty(),
            subclass = if (level >= 3) subclass else "",
            feats = feats,
            languages = languages,
            training = training,
            scores = scores,
            saves = saves,
            skills = skills,
            maxHp = maxHp,
            hp = hp,
            tempHp = (d["temporaryHitPoints"].int ?: 0).coerceAtLeast(0),
            wearing = wornArmor.firstOrNull(),
            shield = shield,
            acBonus = acBonus,
            baseSpeed = race?.obj("weightSpeeds")?.obj("normal")?.get("walk").int ?: speciesInfo(speciesName.orEmpty())?.speed ?: 30,
            spells = spells,
            items = items,
            coins = coins,
            inspiration = d["inspiration"].bool == true,
        ).withClassSlots(classInfo(className), level)

        // D&D Beyond's HP is kept, rolled or adjusted; say how it differs from the fixed table
        val hpNote = c.hpBreakdown?.let { breakdown ->
            val why = listOfNotNull(
                "max HP is set by hand".takeIf { override != null },
                "hit points are rolled".takeIf { override == null && d.obj("preferences")?.get("hitPointType").int == 2 },
                "it has a ${signed(hpBonus)} HP modifier".takeIf { override == null && hpBonus != 0 },
            ).ifEmpty { listOf("it works it out differently") }
            "Max HP $breakdown: on D&D Beyond ${why.joinToString(" and ")}. The sheet keeps ${c.maxHp}."
        }
        return c.copy(notes = (listOfNotNull(hpNote) + notes).joinToString("\n\n"))
    }

    /**
     * Final scores: rolled + D&D Beyond's bonus box + every "+N to a score" modifier, no higher than
     * 20 (the rules' cap); then item bonuses and "set to N" items (a belt) past it; an override wins.
     */
    private fun scores(d: JsonObject, mods: List<Mod>): Map<Ability, Int> {
        fun byId(key: String): Map<Int, Int> =
            d[key].arr.mapNotNull { s -> (s as? JsonObject)?.let { o -> o["id"].int?.let { id -> o["value"].int?.let { id to it } } } }.toMap()
        val base = byId("stats")
        val bonus = byId("bonusStats")
        val override = byId("overrideStats")
        return Ability.entries.mapIndexed { i, a ->
            val sub = "${a.long.lowercase()}-score"
            val id = i + 1
            val fromRules = mods.filter { it.type == "bonus" && it.subType == sub && it.source != "item" }.sumOf { it.value }
            val fromItems = mods.filter { it.type == "bonus" && it.subType == sub && it.source == "item" }.sumOf { it.value }
            val set = mods.filter { it.type == "set" && it.subType == sub }.maxOfOrNull { it.value }
            val total = ((base[id] ?: 10) + (bonus[id] ?: 0) + fromRules).coerceAtMost(20) + fromItems
            a to (override[id] ?: maxOf(total, set ?: 0)).coerceIn(SCORE_RANGE)
        }.toMap()
    }

    private data class Mod(val source: String, val type: String, val subType: String, val value: Int, val name: String?)

    /** Every modifier, by where it's from; an item's count only while it's equipped (and attuned, if it needs to be). */
    private fun modifiers(d: JsonObject, inventory: List<JsonObject>): List<Mod> {
        val active = inventory.filter { inv ->
            val def = inv.obj("definition")
            inv["equipped"].bool == true && (def?.get("canAttune").bool != true || inv["isAttuned"].bool == true)
        }.mapNotNull { it.obj("definition")?.get("id").long }.toSet()
        return d["modifiers"].obj?.entries?.flatMap { (source, list) ->
            list.arr.mapNotNull { m ->
                val o = m as? JsonObject ?: return@mapNotNull null
                if (source == "item" && (o["componentId"].long ?: -1L) !in active) return@mapNotNull null
                Mod(
                    source,
                    o["type"].str ?: return@mapNotNull null,
                    o["subType"].str.orEmpty(),
                    o["value"].int ?: o["fixedValue"].int ?: 0,
                    o["friendlySubtypeName"].str,
                )
            }
        } ?: emptyList()
    }

    /** "Plate" → Plate Armor, "Half Plate" → Half-Plate Armor: the sheet's armor of that name. */
    private fun armorFor(name: String): Armor? {
        fun norm(s: String) = s.lowercase().replace("armor", "").filter { it.isLetter() }
        val n = norm(name.substringBefore(","))
        return ARMOR.drop(1).firstOrNull { norm(it.name) == n }
    }

    private fun slug(label: String) = label.lowercase().replace(' ', '-')

    /** characterValues typeIds, as the saved Hagar response has them on his renamed Greatsword. */
    private const val CUSTOM_NAME = 8
    private const val CUSTOM_NOTES = 9

    /** D&D Beyond's HTML description as plain text: paragraphs kept, tags and entities gone. */
    fun plainText(html: String): String = html
        .replace(Regex("""(?i)<br\s*/?>|</p>|</div>|</li>|<hr\s*/?>"""), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("""\[/?\w+]"""), "")
        .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /** "otherNotes" → "Other notes". */
    private fun label(key: String) =
        key.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replaceFirstChar { it.uppercase() }

    private val JsonElement?.prim: JsonPrimitive? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private val JsonElement?.str: String? get() = prim?.takeIf { it.isString }?.content
    private val JsonElement?.int: Int? get() = prim?.intOrNull ?: prim?.doubleOrNull?.toInt()
    private val JsonElement?.long: Long? get() = prim?.longOrNull
    private val JsonElement?.dbl: Double? get() = prim?.doubleOrNull
    private val JsonElement?.bool: Boolean? get() = prim?.booleanOrNull
    private val JsonElement?.arr: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()
    private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
}
