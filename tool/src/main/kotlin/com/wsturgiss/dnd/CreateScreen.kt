package com.wsturgiss.dnd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.ARMOR
import com.wsturgiss.dnd.model.Ability
import com.wsturgiss.dnd.model.BACKGROUNDS
import com.wsturgiss.dnd.model.CLASS_INFO
import com.wsturgiss.dnd.model.Caster
import com.wsturgiss.dnd.model.ClassInfo
import com.wsturgiss.dnd.model.Building
import com.wsturgiss.dnd.model.Draft
import com.wsturgiss.dnd.model.INITIATE_FEAT
import com.wsturgiss.dnd.model.INITIATE_FROM_BACKGROUND
import com.wsturgiss.dnd.model.INITIATE_FROM_SPECIES
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.LANGUAGES_TO_PICK
import com.wsturgiss.dnd.model.choiceName
import com.wsturgiss.dnd.model.GearItem
import com.wsturgiss.dnd.model.GearOption
import com.wsturgiss.dnd.model.Srd
import com.wsturgiss.dnd.model.gearNames
import com.wsturgiss.dnd.model.POINT_BUY_BUDGET
import com.wsturgiss.dnd.model.SPECIES
import com.wsturgiss.dnd.model.STANDARD_ARRAY
import com.wsturgiss.dnd.model.HP_RANGE
import com.wsturgiss.dnd.model.SCORE_RANGE
import com.wsturgiss.dnd.model.validHp
import com.wsturgiss.dnd.model.validScore
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.backgroundBonuses
import com.wsturgiss.dnd.model.bonusLabel
import com.wsturgiss.dnd.model.keptTotal
import com.wsturgiss.dnd.model.pointCost
import com.wsturgiss.dnd.model.rollScores
import com.wsturgiss.dnd.model.signed
import com.wsturgiss.dnd.model.slotsFor
import com.wsturgiss.dnd.ui.KeyValueRow
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/** The lists Magic Initiate can draw on. */
val INITIATE_LISTS = listOf("Cleric", "Druid", "Wizard")

/** One line about a class, for pickers and the builder. */
fun classLine(c: ClassInfo): String =
    "d${c.hitDie}  ·  ${c.saves.sortedBy { it.ordinal }.joinToString(" ") { it.short }} saves" +
        when (c.caster) {
            Caster.None -> ""
            Caster.Full -> "  ·  spells"
            Caster.Half -> "  ·  half caster"
            Caster.Pact -> "  ·  pact magic"
        }

/**
 * A new character as a checklist: each row opens a picker, and the sheet it will make is
 * underneath, worked out as you go. Scores, bonuses and armor start on the SRD's suggestion
 * for the class, so a name and skills are all you must choose. Returns true once created.
 */
class CreateScreen(sealedActivity: SealedLightActivity) : DndScreen<Boolean>(sealedActivity) {

    // the draft lives on the screen, not in Compose, so it survives the pickers on top of it
    private val gear = Srd.startingGear(lightContext)
    private val made = Srd.creation(lightContext)
    private var draft by mutableStateOf(Draft().dressed(gear))

    /** Changes the draft, dropping expertise in skills it no longer trains and spells past the count. */
    private fun change(f: (Draft) -> Draft) {
        val d = f(draft)
        val b = Building(d, made)
        draft = d.copy(
            expertise = d.expertise intersect d.trained,
            cantrips = d.cantrips.take(b.cantripCount).toSet(),
            prepared = d.prepared.take(b.preparedCount).toSet(),
        )
    }

    @Composable
    override fun Content() {
        val d = draft
        val cls = d.cls
        val bg = d.backgroundInfo
        val b = Building(d, made)
        val missing = b.missing

        ScrollPage(
            scroll,
            title = "New character",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.ACCEPT, contentDescription = "Create", onClick = if (missing.isEmpty()) ({
                Store.addCharacter(b.build(gear))
                goBack(true)
            }) else null),
        ) {
            Section("Who")
            KeyValueRow("Name", d.name.ifBlank { "—" }, { askText("Name", d.name) { n -> change { it.copy(name = n) } } }, valueStyle = Style.Body)
            KeyValueRow("Class", d.className, ::pickClass, valueStyle = Style.Body)
            KeyValueRow("Species", d.species, ::pickSpecies, valueStyle = Style.Body)
            KeyValueRow("Background", d.background, ::pickBackground, valueStyle = Style.Body)
            KeyValueRow("Starting level", d.level.toString(), {
                askNumber(NumberRequest("Starting level", "1 to 20. Past 1st, HP takes the average each level.", d.level)) { r ->
                    change { it.copy(level = r.value.coerceIn(1, 20)) }
                }
            })

            Section("Scores")
            val typed = d.manualScores != null
            KeyValueRow("Base", d.baseMethod, {
                navigateTo(
                    screenFactory = { ScoresScreen(it, cls) },
                    resultCallback = { r -> change { it.withBase(r.scores, r.method) } },
                )
            }, valueStyle = Style.Body)
            val typedLabel = listOfNotNull("scores".takeIf { typed }, "HP".takeIf { d.manualHp != null })
            KeyValueRow("Type my own", if (typedLabel.isEmpty()) "scores, HP" else typedLabel.joinToString(", ") + " typed", {
                navigateTo(
                    screenFactory = { ManualStatsScreen(it, d) },
                    resultCallback = { r -> change { it.copy(manualScores = r.scores, manualHp = r.hp) } },
                )
            }, valueStyle = Style.Body)
            if (bg != null && !typed) {
                KeyValueRow("${bg.name} bonus", bonusLabel(d.bonusOrSuggested), ::pickBonus, valueStyle = Style.MonoSmall)
            }
            Row(Modifier.fillMaxWidth().padding(top = 0.4f.gridUnitsAsDp())) {
                T("", Style.Small, Modifier.weight(1f))
                (if (typed) listOf("", "", "score", "mod") else listOf("base", "bonus", "score", "mod")).forEach {
                    T(it, Style.Small, Modifier.weight(1f), dim = true, align = TextAlign.End)
                }
            }
            Ability.entries.forEach { a ->
                val bonus = d.bonusOrSuggested[a] ?: 0
                Row(Modifier.fillMaxWidth()) {
                    T(a.short, Style.Mono, Modifier.weight(1f))
                    T(if (typed) "" else "${d.baseScores[a] ?: 10}", Style.Mono, Modifier.weight(1f), align = TextAlign.End)
                    T(if (bonus > 0 && !typed) "+$bonus" else "", Style.Mono, Modifier.weight(1f), dim = true, align = TextAlign.End)
                    T("${d.scores[a]}", Style.Mono, Modifier.weight(1f), align = TextAlign.End)
                    T(signed(d.mod(a)), Style.Mono, Modifier.weight(1f), align = TextAlign.End)
                }
            }

            Section("Species")
            b.speciesData?.let { sd ->
                sd.choice?.let { label ->
                    KeyValueRow(label, d.lineage?.let(::choiceName) ?: "choose", valueStyle = Style.Body, onClick = {
                        navigateTo(
                            screenFactory = {
                                PickScreen(
                                    it, label, sd.options.map { o -> Option(choiceName(o)) }, sd.options.indexOf(d.lineage).takeIf { i -> i >= 0 },
                                    "› to read each.", Kind.Species, readName = { i -> sd.options[i] },
                                )
                            },
                            resultCallback = { i -> change { it.copy(lineage = sd.options[i]) } },
                        )
                    })
                }
                TapRow(onClick = { openEntry(Kind.Species, d.species) }) {
                    T("${sd.traits.joinToString(", ")}  ·  ${sd.size}", Style.Small, Modifier.weight(1f))
                    T("›", Style.Heading, dim = true)
                }
            }

            Section("Feats")
            b.backgroundData?.let { bd ->
                KeyValueRow("${d.background}", bd.feat + (bd.note?.takeIf { bd.feat == INITIATE_FEAT }?.let { " ($it)" } ?: ""), valueStyle = Style.Body, onClick = {
                    openEntry(Kind.Feats, bd.feat)
                })
            }
            if (d.species == "Human") {
                KeyValueRow("Human, Versatile", d.versatileFeat ?: "choose", valueStyle = Style.Body, onClick = {
                    pickRead("Origin feat", made.originFeats, d.versatileFeat, Kind.Feats, "Humans take one origin feat.") { name ->
                        change { it.copy(versatileFeat = name, picks = it.picks - "skilled") }
                    }
                })
                if (d.versatileFeat == INITIATE_FEAT) {
                    KeyValueRow("Magic Initiate list", d.initiateList, valueStyle = Style.Body, onClick = {
                        pickRead("Spell list", INITIATE_LISTS, d.initiateList, null, "Where its two cantrips and one spell come from.") { list ->
                            change { it.copy(initiateList = list, initiateCantrips = it.initiateCantrips - INITIATE_FROM_SPECIES, initiateSpell = it.initiateSpell - INITIATE_FROM_SPECIES) }
                        }
                    })
                }
            }
            if (b.needsFightingStyle) {
                KeyValueRow("Fighting style", d.fightingStyle ?: "choose", valueStyle = Style.Body, onClick = {
                    pickRead("Fighting style", made.fightingStyles, d.fightingStyle, Kind.Feats, null) { name ->
                        change { it.copy(fightingStyle = name) }
                    }
                })
            }
            b.initiates.forEach { (from, list) ->
                val mine = d.initiateCantrips[from].orEmpty()
                KeyValueRow("Initiate ($list) cantrips", "${mine.size}/2", {
                    pickSpells("$list cantrips", 2, list, 0..0, mine) { set ->
                        change { it.copy(initiateCantrips = it.initiateCantrips + (from to set)) }
                    }
                })
                KeyValueRow("Initiate ($list) spell", d.initiateSpell[from] ?: "choose", valueStyle = Style.Body, onClick = {
                    pickSpells("$list level 1 spell", 1, list, 1..1, setOfNotNull(d.initiateSpell[from])) { set ->
                        change { it.copy(initiateSpell = set.firstOrNull()?.let { s -> it.initiateSpell + (from to s) } ?: (it.initiateSpell - from)) }
                    }
                })
            }

            Section("Skills")
            bg?.skills?.takeIf { it.isNotEmpty() }?.let { fixed ->
                T("${bg.name}: ${fixed.joinToString(", ") { it.label }}", Style.Small, dim = true)
            }
            d.pools.forEach { pool ->
                val mine = d.picks[pool.key] ?: emptySet()
                KeyValueRow("${pool.label}: choose ${pool.count}", "${mine.size}/${pool.count}", {
                    navigateTo(
                        screenFactory = { SkillPickScreen(it, "${pool.label} skills", pool.count, pool.from, mine, d.trained - mine) },
                        resultCallback = { set -> change { it.copy(picks = it.picks + (pool.key to set)) } },
                    )
                })
            }
            if (d.expertiseCount > 0) {
                KeyValueRow("Expertise: choose ${d.expertiseCount}", "${d.expertise.size}/${d.expertiseCount}", {
                    navigateTo(
                        screenFactory = { SkillPickScreen(it, "Expertise", d.expertiseCount, d.trained.sortedBy { s -> s.ordinal }, d.expertise) },
                        resultCallback = { set -> change { it.copy(expertise = set) } },
                    )
                })
            }
            if (d.trained.isNotEmpty()) {
                T(
                    d.trained.sortedBy { it.ordinal }.joinToString(", ") { if (it in d.expertise) "${it.label} (E)" else it.label },
                    Style.Small,
                    modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()),
                )
            }

            if (b.cantripCount > 0 || b.preparedCount > 0) {
                Section("Spells")
                if (b.cantripCount > 0) {
                    KeyValueRow("Cantrips: choose ${b.cantripCount}", "${d.cantrips.size}/${b.cantripCount}", {
                        pickSpells("Cantrips", b.cantripCount, d.className, 0..0, d.cantrips) { set -> change { it.copy(cantrips = set) } }
                    })
                }
                if (b.preparedCount > 0) {
                    val top = b.maxSpellLevel
                    KeyValueRow("Spells: choose ${b.preparedCount}", "${d.prepared.size}/${b.preparedCount}", {
                        pickSpells("Spells", b.preparedCount, d.className, 1..top, d.prepared) { set -> change { it.copy(prepared = set) } }
                    })
                    T("Prepared, up to level $top. They go in Spells, starred.", Style.Small, dim = true)
                }
            }

            Section("Languages")
            KeyValueRow("Common, and choose $LANGUAGES_TO_PICK", d.languages.sorted().joinToString(", ").ifEmpty { "—" }, valueStyle = Style.Small, onClick = {
                val options = made.languages["standard"].orEmpty().filter { it != "Common" }.map { Option(it) } +
                    made.languages["rare"].orEmpty().map { Option(it, "rare: ask your DM") }
                navigateTo(
                    screenFactory = { MultiPickScreen(it, "Languages", LANGUAGES_TO_PICK, options, d.languages, have = setOf("Common")) },
                    resultCallback = { set -> change { it.copy(languages = set) } },
                )
            })

            if (d.level >= 3 && b.classData?.subclass != null) {
                Section("Subclass")
                val srd = b.classData!!.subclass!!
                KeyValueRow("${d.className} subclass", b.subclass.orEmpty(), valueStyle = Style.Body, onClick = {
                    val current = b.subclass
                    navigateTo(
                        screenFactory = {
                            PickScreen(
                                it, "Subclass", listOf(Option(srd, "in the SRD"), Option("Something else", if (current != srd) current.orEmpty() else "type its name")),
                                if (current == srd) 0 else 1, "The SRD has one subclass per class; others are in the book.", Kind.Features,
                                readName = { i -> if (i == 0) srd else current.orEmpty() },
                            )
                        },
                        resultCallback = { i ->
                            if (i == 0) change { it.copy(subclass = null) }
                            else askText("Subclass", if (current != srd) current.orEmpty() else "") { n -> change { it.copy(subclass = n) } }
                        },
                    )
                })
            }

            Section("Gear")
            gear[d.className]?.let { options ->
                KeyValueRow("${d.className} gear", gearNames(options)[d.classGear], valueStyle = Style.Body, onClick = {
                    pickGear(d.className, options, d.classGear) { i -> change { it.copy(classGear = i).dressed(gear) } }
                })
            }
            gear[d.background]?.let { options ->
                KeyValueRow("${d.background} gear", gearNames(options)[d.backgroundGear], valueStyle = Style.Body, onClick = {
                    pickGear(d.background, options, d.backgroundGear) { i -> change { it.copy(backgroundGear = i) } }
                })
            }
            d.gearChoices(gear).forEach { choice ->
                KeyValueRow(choice.name, d.gearPicks[choice.name]?.name ?: "choose", valueStyle = Style.Body, onClick = { pickChoice(choice) })
            }
            T(
                "Wearing: " + listOfNotNull(d.armorInfo.takeIf { d.armor != ARMOR.first().name }?.name, "Shield".takeIf { d.shield })
                    .joinToString(" and ").ifEmpty { d.cls?.unarmored?.let { "no armor (Unarmored Defense)" } ?: "no armor" },
                Style.Small,
                modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()),
            )
            val bag = d.bag(gear)
            T(
                (bag.map { if (it.count > 1) "${it.count} ${it.name}" else it.name } + "${d.gold(gear)} GP").joinToString(", "),
                Style.Small,
                modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()),
            )
            T("All of it goes in the bag. Taking gold? Buy armor there and wear it; AC follows.", Style.Small, dim = true)

            Section("Sheet")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(if (d.manualHp != null) "HP (TYPED)" else "HP", "${d.maxHp}")
                Stat("AC", "${d.ac}")
                Stat("SPEED", "${d.speciesInfo?.speed ?: 30}")
                Stat("INIT", signed(d.mod(Ability.DEX)))
            }
            cls?.let { c ->
                T("Saves: ${c.saves.sortedBy { it.ordinal }.joinToString(", ") { it.long }}", Style.Small)
                val slots = slotsFor(c, d.level).withIndex().filter { it.value > 0 }
                if (slots.isNotEmpty()) {
                    T("Slots: " + slots.joinToString("  ") { "${ordinal(it.index + 1)} ${it.value}" }, Style.Small)
                }
            }

            if (missing.isNotEmpty()) {
                T(
                    "Still to choose: ${missing.joinToString(", ")}. Then the tick, top right.",
                    Style.Body,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
            }
        }
    }

    @Composable
    private fun Stat(label: String, value: String) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            T(value, Style.MonoBig)
            T(label, Style.Small, dim = true)
        }
    }

    private fun pick(title: String, options: List<Option>, selected: Int?, note: String? = null, onPick: (Int) -> Unit) =
        navigateTo(screenFactory = { PickScreen(it, title, options, selected, note) }, resultCallback = onPick)

    /** One name from [names], each readable in [kind] first. */
    private fun pickRead(title: String, names: List<String>, current: String?, kind: Kind?, note: String?, onPick: (String) -> Unit) =
        navigateTo(
            screenFactory = { PickScreen(it, title, names.map { n -> Option(n) }, names.indexOf(current).takeIf { i -> i >= 0 }, note, kind) },
            resultCallback = { i -> onPick(names[i]) },
        )

    /** [count] spells from [list]'s spells of [levels], each readable first. */
    private fun pickSpells(title: String, count: Int, list: String, levels: IntRange, current: Set<String>, onPick: (Set<String>) -> Unit) {
        val options = Srd.load(lightContext, Kind.Spells)
            .filter { list in it.classes && (it.level ?: 0) in levels }
            .sortedWith(compareBy({ it.level }, { it.name }))
            .map { Option(it.name, it.meta) }
        navigateTo(
            screenFactory = { MultiPickScreen(it, title, count, options, current, Kind.Spells, note = "› to read a spell.") },
            resultCallback = onPick,
        )
    }

    private fun pickClass() = pick(
        "Class",
        CLASS_INFO.map { Option(it.name, classLine(it)) },
        CLASS_INFO.indexOfFirst { it.name == draft.className },
    ) { i ->
        change {
            it.copy(
                className = CLASS_INFO[i].name, picks = it.picks - "class", classGear = 0,
                cantrips = emptySet(), prepared = emptySet(), fightingStyle = null, subclass = null,
            ).dressed(gear)
        }
    }

    private fun pickSpecies() = navigateTo(
        screenFactory = {
            PickScreen(
                it,
                "Species",
                SPECIES.map { sp ->
                    val traits = made.species[sp.name]?.traits.orEmpty()
                    Option(sp.name, (listOf("speed ${sp.speed}") + traits).joinToString("  ·  "))
                },
                SPECIES.indexOfFirst { sp -> sp.name == draft.species },
                "› to read a species in full.",
                Kind.Species,
            )
        },
        resultCallback = { i ->
            change { it.copy(species = SPECIES[i].name, picks = it.picks - "species" - "skilled", lineage = null, versatileFeat = null) }
        },
    )

    private fun pickBackground() = pick(
        "Background",
        BACKGROUNDS.map { b ->
            Option(
                b.name,
                if (b.custom) "any scores, any two skills"
                else "${b.abilities.joinToString(" ") { it.short }}  ·  ${b.skills.joinToString(", ") { it.label }}",
            )
        },
        BACKGROUNDS.indexOfFirst { it.name == draft.background },
    ) { i ->
        change {
            it.copy(
                background = BACKGROUNDS[i].name, bonus = null, picks = it.picks - "background", backgroundGear = 0,
                initiateCantrips = it.initiateCantrips - INITIATE_FROM_BACKGROUND, initiateSpell = it.initiateSpell - INITIATE_FROM_BACKGROUND,
            )
        }
    }

    private fun pickGear(who: String, options: List<GearOption>, selected: Int, onPick: (Int) -> Unit) = pick(
        "$who gear",
        gearNames(options).zip(options) { name, o -> Option(name, o.label) },
        selected,
        "Starting equipment. Ask your DM before changing it.",
        onPick,
    )

    /** One "of your choice" item: the SRD's list, then anything else, typed. */
    private fun pickChoice(choice: GearItem) {
        val picked = draft.gearPicks[choice.name]
        pick(
            choice.name,
            choice.choices.map { Option(it.name, if (it.weight > 0) "${"%.2f".format(it.weight).trimEnd('0').trimEnd('.')} lb" else "") } +
                Option("Something else", picked?.takeIf { p -> choice.choices.none { it.name == p.name } }?.name ?: "type its name"),
            choice.choices.indexOfFirst { it.name == picked?.name }.takeIf { it >= 0 }
                ?: choice.choices.size.takeIf { picked != null },
            "From your starting equipment.",
        ) { i ->
            val set = { item: GearItem -> change { it.copy(gearPicks = it.gearPicks + (choice.name to item)) } }
            if (i < choice.choices.size) set(choice.choices[i])
            else askText(choice.name, picked?.name ?: "") { name -> set(GearItem(name, 1, 0.0, false)) }
        }
    }

    private fun pickBonus() {
        val from = draft.backgroundInfo?.abilities ?: return
        val options = backgroundBonuses(from)
        pick(
            "Bonus",
            options.map { Option(bonusLabel(it)) },
            options.indexOf(draft.bonusOrSuggested).takeIf { it >= 0 },
            "Your background raises its scores by +2 and +1, or +1 each. None above 20.",
        ) { i -> change { it.copy(bonus = options[i]) } }
    }
}

data class ScoreResult(val scores: Map<com.wsturgiss.dnd.model.Ability, Int>, val method: String)

/**
 * Three ways to get six scores. Array and Roll deal values you place: the biggest one left is
 * always next, so tapping abilities best-first is the whole job. Point buy spends 27 points.
 */
class ScoresScreen(
    sealedActivity: SealedLightActivity,
    private val cls: ClassInfo?,
) : DndScreen<ScoreResult>(sealedActivity) {

    private enum class Mode(val label: String) { Array("Array"), Points("Point buy"), Roll("Roll") }

    private var mode by mutableStateOf(Mode.Array)
    private var assigned by mutableStateOf(mapOf<Ability, Int>())
    private var rolled by mutableStateOf<List<List<Int>>?>(null)
    private var points by mutableStateOf(Ability.entries.associateWith { 8 })

    private val pool: List<Int>
        get() = when (mode) {
            Mode.Array -> STANDARD_ARRAY
            Mode.Roll -> rolled?.map(::keptTotal)?.sortedDescending() ?: emptyList()
            Mode.Points -> emptyList()
        }

    /** The pool less what's been placed (by value, so duplicates count right). */
    private fun left(): List<Int> {
        val rest = pool.toMutableList()
        assigned.values.forEach { rest.remove(it) }
        return rest
    }

    private fun spent() = points.values.sumOf { pointCost(it) ?: 0 }

    @Composable
    override fun Content() {
        val placing = mode != Mode.Points
        val done = if (placing) assigned.size == 6 else spent() <= POINT_BUY_BUDGET
        ScrollPage(
            scroll,
            title = "Scores",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.ACCEPT, contentDescription = "Use", onClick = if (done) ({ finish() }) else null),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp()), horizontalArrangement = Arrangement.spacedBy(0.4f.gridUnitsAsDp())) {
                Mode.entries.forEach { m ->
                    PadKey(m.label, Modifier.weight(1f), selected = m == mode) {
                        mode = m
                        assigned = emptyMap()
                    }
                }
            }
            when (mode) {
                Mode.Array -> T("Place $STANDARD_ARRAY_LINE: tap abilities, best first.", Style.Small, dim = true)
                Mode.Roll -> RollPanel()
                Mode.Points -> T("Each score 8 to 15. $POINT_BUY_BUDGET points; 14 costs 7 and 15 costs 9.", Style.Small, dim = true)
            }
            if (placing && pool.isNotEmpty()) {
                TapRow(onClick = ::suggest) {
                    T("Suggest", Style.Body, Modifier.weight(1f))
                    T(cls?.let { c -> "${c.name}: ${c.priority.take(3).joinToString(" ") { it.short }} first" } ?: "in order", Style.Small, dim = true)
                }
            }
            if (placing) Placing() else PointBuy()
        }
    }

    @Composable
    private fun RollPanel() {
        val r = rolled
        TapRow(onClick = { rolled = rollScores(); assigned = emptyMap() }) {
            T(if (r == null) "Roll 4d6 six times" else "Roll again", Style.Body, Modifier.weight(1f))
            T("drop lowest", Style.MonoSmall, dim = true)
        }
        r?.forEach { dice ->
            T("[${dice.take(3).joinToString(" ")}] ${dice.last()}  = ${keptTotal(dice)}", Style.MonoSmall, dim = true)
        }
    }

    @Composable
    private fun Placing() {
        val left = left()
        val next = left.maxOrNull()
        if (pool.isNotEmpty()) {
            T(
                if (next != null) "Next: $next   left: ${left.joinToString(" ")}" else "All placed. Tap one to take it back.",
                Style.Mono,
                modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
            )
        }
        Ability.entries.forEach { a ->
            val v = assigned[a]
            TapRow(onClick = {
                assigned = when {
                    v != null -> assigned - a
                    next != null -> assigned + (a to next)
                    else -> assigned
                }
            }) {
                T(a.short + "  ", Style.Mono)
                T(a.long, Style.Body, Modifier.weight(1f))
                T(v?.toString() ?: "__", Style.MonoBig, dim = v == null)
            }
        }
    }

    @Composable
    private fun PointBuy() {
        T("Points left: ${POINT_BUY_BUDGET - spent()}", Style.Mono, modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()))
        Ability.entries.forEach { a ->
            val v = points[a] ?: 8
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                T(a.short, Style.Mono, Modifier.weight(1f))
                PadKey("-", Modifier.weight(1f), enabled = v > 8) { points = points + (a to v - 1) }
                T("$v", Style.MonoBig, Modifier.weight(1f), align = TextAlign.Center)
                val cost = (pointCost(v + 1) ?: 0) - (pointCost(v) ?: 0)
                PadKey("+", Modifier.weight(1f), enabled = v < 15 && spent() + cost <= POINT_BUY_BUDGET) {
                    points = points + (a to v + 1)
                }
                T("${pointCost(v)}", Style.MonoSmall, Modifier.weight(0.6f), dim = true, align = TextAlign.End)
            }
        }
    }

    private fun suggest() {
        val values = pool.sortedDescending()
        assigned = (cls?.priority ?: Ability.entries).zip(values).toMap()
    }

    private fun finish() = goBack(
        when (mode) {
            Mode.Array -> ScoreResult(assigned, "standard array")
            Mode.Roll -> ScoreResult(assigned, "rolled")
            Mode.Points -> ScoreResult(points, "point buy")
        },
    )

    private companion object {
        val STANDARD_ARRAY_LINE = STANDARD_ARRAY.joinToString(" ")
    }
}

/** Typed-in scores and HP; null leaves that part to the rules, both null puts the draft back on them. */
data class ManualStats(val scores: Map<Ability, Int>?, val hp: Int?)

/**
 * Six scores and max HP typed in, for a character already made on paper. Each opens the number
 * pad; scores must be 1 to 30 (with every bonus in), HP 1 to 999. Modifiers follow the scores.
 * Only what you tap is typed in: until then scores and HP stay worked out, and HP follows a
 * typed CON.
 */
class ManualStatsScreen(
    sealedActivity: SealedLightActivity,
    private val draft: Draft,
) : DndScreen<ManualStats>(sealedActivity) {

    /** Something is typed in already, so offer to go back to the rules. */
    private val hasTyped = draft.manualScores != null || draft.manualHp != null

    private var scores by mutableStateOf(Ability.entries.associateWith { draft.scores[it] ?: 10 })
    private var scoresTyped by mutableStateOf(draft.manualScores != null)
    private var typedHp by mutableStateOf(draft.manualHp)
    private var error by mutableStateOf<String?>(null)

    /** Typed, or worked out by the rules from the scores as they stand here. */
    private val hp: Int get() = typedHp ?: draft.copy(manualScores = scores.takeIf { scoresTyped }, manualHp = null).maxHp

    @Composable
    override fun Content() {
        ScrollPage(
            scroll,
            title = "Your own",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.ACCEPT, contentDescription = "Use", onClick = {
                goBack(ManualStats(scores.takeIf { scoresTyped }, typedHp))
            }),
        ) {
            T("For a character made elsewhere: final scores, bonuses in. Tap one to change it.", Style.Small, dim = true)
            Ability.entries.forEach { a ->
                val v = scores[a] ?: 10
                TapRow(onClick = {
                    askNumber(NumberRequest(a.long, "${SCORE_RANGE.first} to ${SCORE_RANGE.last}.", v)) { r ->
                        if (validScore(r.value)) {
                            scores = scores + (a to r.value)
                            scoresTyped = true
                            error = null
                        } else {
                            error = "${a.short} must be ${SCORE_RANGE.first} to ${SCORE_RANGE.last}; ${r.value} wasn't kept."
                        }
                    }
                }) {
                    T(a.short + "  ", Style.Mono)
                    T(a.long, Style.Body, Modifier.weight(1f))
                    T(signed(Math.floorDiv(v - 10, 2)) + "  ", Style.MonoSmall, dim = true)
                    T("$v", Style.MonoBig)
                }
            }
            Section("Hit points")
            TapRow(onClick = {
                askNumber(NumberRequest("Max HP", "${HP_RANGE.first} to ${HP_RANGE.last}.", hp)) { r ->
                    if (validHp(r.value)) {
                        typedHp = r.value
                        error = null
                    } else {
                        error = "HP must be ${HP_RANGE.first} to ${HP_RANGE.last}; ${r.value} wasn't kept."
                    }
                }
            }) {
                T("Max HP", Style.Body, Modifier.weight(1f))
                if (typedHp == null) T("worked out  ", Style.Small, dim = true)
                T("$hp", Style.MonoBig)
            }
            error?.let { T(it, Style.Body, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp())) }
            if (hasTyped) {
                TapRow(onClick = { goBack(ManualStats(null, null)) }, Modifier.padding(top = 1f.gridUnitsAsDp())) {
                    T("Use the rules instead", Style.Body, Modifier.weight(1f))
                    T("›", Style.Heading, dim = true)
                }
            }
        }
    }
}
