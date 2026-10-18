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

        // classes: the app keeps one; the starting class (or the highest) takes the total level
        val classes = d["classes"].arr.mapNotNull { it as? JsonObject }
        val main = classes.firstOrNull { it["isStartingClass"].bool == true } ?: classes.maxByOrNull { it["level"].int ?: 0 }
        val level = classes.sumOf { it["level"].int ?: 0 }.coerceIn(1, 20)
        val className = main?.obj("definition")?.get("name").str ?: "Fighter"
        val subclass = main?.obj("subclassDefinition")?.get("name").str.orEmpty()
        if (classes.size > 1) {
            notes += "Multiclass on D&D Beyond: " + classes.joinToString(", ") {
                "${it.obj("definition")?.get("name").str ?: "?"} ${it["level"].int ?: 0}" +
                    (it.obj("subclassDefinition")?.get("name").str?.let { s -> " ($s)" } ?: "")
            } + ". Imported as $className $level."
        }

        val inventory = d["inventory"].arr.mapNotNull { it as? JsonObject }
        val mods = modifiers(d, inventory)

        val scores = scores(d, mods)
        val conMod = Math.floorDiv((scores[Ability.CON] ?: 10) - 10, 2)
        val perLevel = mods.filter { it.type == "bonus" && it.subType == "hit-points-per-level" }.sumOf { it.value }
        val maxHp = (d["overrideHitPoints"].int ?: ((d["baseHitPoints"].int ?: 10) + conMod * level + (d["bonusHitPoints"].int ?: 0) + perLevel * level))
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

        // the bag: everything, containers' contents too; armor named as the sheet knows it, so it can be worn
        val wornArmor = mutableListOf<String>()
        var shield = false
        var acBonus = 0
        val items = mutableListOf<Item>()
        inventory.forEach { inv ->
            val def = inv.obj("definition") ?: return@forEach
            val ddbName = def["name"].str?.takeIf { it.isNotBlank() } ?: return@forEach
            val equipped = inv["equipped"].bool == true
            val armorType = def["armorTypeId"].int
            val armor = if (armorType != null && armorType in 1..3) armorFor(def["baseArmorName"].str ?: ddbName) else null
            val isShield = armorType == 4
            val name = when {
                armor != null -> armor.name
                isShield -> "Shield"
                else -> ddbName
            }
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
            )
        }
        d["customItems"].arr.mapNotNull { it as? JsonObject }.forEach { ci ->
            val name = ci["name"].str?.takeIf { it.isNotBlank() } ?: return@forEach
            items += Item(-1L - items.size, name, (ci["quantity"].int ?: 1).coerceAtLeast(0), ci["weight"].dbl ?: 0.0)
            ci["notes"].str?.takeIf { it.isNotBlank() }?.let { notes += "$name: $it" }
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

        return Character(
            name = d["name"].str?.takeIf { it.isNotBlank() } ?: "Nameless",
            className = className,
            level = level,
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
            speed = race?.obj("weightSpeeds")?.obj("normal")?.get("walk").int ?: speciesInfo(speciesName.orEmpty())?.speed ?: 30,
            spells = spells,
            items = items,
            coins = coins,
            inspiration = d["inspiration"].bool == true,
            notes = notes.joinToString("\n\n"),
        ).withClassSlots(classInfo(className), level)
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
