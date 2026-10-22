package com.wsturgiss.dnd

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.Ability
import com.wsturgiss.dnd.model.BACKGROUNDS
import com.wsturgiss.dnd.model.CLASS_INFO
import com.wsturgiss.dnd.model.Caster
import com.wsturgiss.dnd.model.SPECIES
import com.wsturgiss.dnd.model.classInfo
import com.wsturgiss.dnd.model.slotsFor
import com.wsturgiss.dnd.model.Character
import com.wsturgiss.dnd.model.FULL_CASTER_SLOTS
import com.wsturgiss.dnd.model.hpRange
import com.wsturgiss.dnd.model.SCORE_RANGE
import com.wsturgiss.dnd.model.Skill
import com.wsturgiss.dnd.model.Slots
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.Srd
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Feat
import com.wsturgiss.dnd.model.TOUGH_FEAT
import com.wsturgiss.dnd.model.choiceName
import com.wsturgiss.dnd.model.Training
import com.wsturgiss.dnd.model.signed
import com.wsturgiss.dnd.ui.KeyValueRow
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/**
 * Corrections to the sheet. The builder and Level up keep it right as you play, so the
 * proficiencies and slots sit one tap down, each behind a row that sums them up.
 */
class EditScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character

        ScrollPage(scroll, title = "Edit", onBack = { goBack(null) }) {
            Section("Who")
            KeyValueRow("Name", c.name, { askText("Name", c.name) { n -> Store.edit { it.copy(name = n) } } }, valueStyle = Style.Body)
            KeyValueRow("Class", c.className, {
                pick("Class", CLASS_INFO.map { Option(it.name, classLine(it)) }, CLASS_INFO.indexOfFirst { it.name == c.className }) { i ->
                    Store.edit { it.copy(className = CLASS_INFO[i].name) }
                }
            }, valueStyle = Style.Body)
            KeyValueRow("Species", c.species.ifEmpty { "—" }, {
                pick("Species", SPECIES.map { Option(it.name) }, SPECIES.indexOfFirst { it.name == c.species }) { i ->
                    Store.edit { it.copy(species = SPECIES[i].name) }
                }
            }, valueStyle = Style.Body)
            KeyValueRow("Background", c.background.ifEmpty { "—" }, {
                pick("Background", BACKGROUNDS.map { Option(it.name) }, BACKGROUNDS.indexOfFirst { it.name == c.background }) { i ->
                    Store.edit { it.copy(background = BACKGROUNDS[i].name) }
                }
            }, valueStyle = Style.Body)
            NumberRow("Level", c.level, 1..20) { v -> Store.edit { it.copy(level = v) } }
            if (c.level < 20) {
                KeyValueRow("Level up", "${c.level} → ${c.level + 1}", { navigateTo(screenFactory = { LevelUpScreen(it) }) })
            }
            // where the books differ: Jack of All Trades, Remarkable Athlete, shields, dwarves in heavy armor
            KeyValueRow("Rules", c.rules.label, { Store.edit { it.copy(rules = it.rules.next()) } }, valueStyle = Style.Body)

            Section("Scores")
            Ability.entries.forEach { a ->
                NumberRow(a.long, c.baseScore(a), SCORE_RANGE) { v -> Store.edit { it.copy(scores = it.scores + (a to v)) } }
            }

            Section("Combat")
            NumberRow("Max HP", c.maxHp, hpRange(c.level)) { v -> Store.edit { it.copy(maxHp = v, hp = minOf(it.hp, v)) } }
            KeyValueRow("AC bonus", signed(c.acBonus), {
                askNumber(
                    NumberRequest(
                        title = "AC bonus",
                        context = "AC is ${c.ac}: ${c.wearing ?: "no armor"}${if (c.shield) " and a shield" else ""}" +
                            ", worked out. Add magic armor, rings and the like here.",
                        initial = c.acBonus,
                        allowNegative = true,
                    ),
                ) { r -> Store.edit { it.copy(acBonus = r.value.coerceIn(-10, 20)) } }
            })
            NumberRow(if (c.tooHeavy != null) "Speed, out of armor" else "Speed", c.baseSpeed, 0..200) { v -> Store.edit { it.copy(baseSpeed = v) } }

            Section("Proficiencies")
            KeyValueRow(
                "Saves",
                c.saves.sortedBy { it.ordinal }.joinToString(" ") { it.short }.ifEmpty { "none" },
                { navigateTo(screenFactory = { SavesEditScreen(it) }) },
                valueStyle = Style.MonoSmall,
            )
            KeyValueRow(
                "Skills",
                c.skills.count { it.value != Training.None }.let { n -> if (n == 0) "none" else "$n trained" },
                { navigateTo(screenFactory = { SkillsEditScreen(it) }) },
                valueStyle = Style.MonoSmall,
            )
            KeyValueRow(
                "Spell slots",
                c.slots.filter { it.max > 0 }.joinToString(" ") { "${it.max}" }.ifEmpty { "none" },
                { navigateTo(screenFactory = { SlotsEditScreen(it) }) },
                valueStyle = Style.MonoSmall,
            )

            Section("Features")
            val made = Srd.creation(lightContext)
            made.species[c.species]?.let { sd ->
                sd.choice?.let { label ->
                    KeyValueRow(label, c.lineage.takeIf { it.isNotEmpty() }?.let(::choiceName) ?: "—", valueStyle = Style.Body, onClick = {
                        navigateTo(
                            screenFactory = {
                                PickScreen(
                                    it, label, sd.options.map { o -> Option(choiceName(o)) }, sd.options.indexOf(c.lineage).takeIf { i -> i >= 0 },
                                    null, Kind.Species, readName = { i -> sd.options[i] },
                                )
                            },
                            resultCallback = { i -> Store.edit { it.copy(lineage = sd.options[i]) } },
                        )
                    })
                }
            }
            KeyValueRow("Subclass", c.subclass.ifEmpty { "—" }, valueStyle = Style.Body, onClick = {
                val srd = made.classes[c.className]?.subclass
                val options = listOfNotNull(srd?.let { Option(it, "in the SRD") }) + Option("Something else", "type its name") + Option("None yet")
                pick("Subclass", options, options.indexOfFirst { it.label == c.subclass }) { i ->
                    when (options[i].label) {
                        "Something else" -> askText("Subclass", c.subclass) { n -> Store.edit { it.copy(subclass = n) } }
                        "None yet" -> Store.edit { it.copy(subclass = "") }
                        else -> Store.edit { it.copy(subclass = options[i].label) }
                    }
                }
            })
            KeyValueRow("Feats", c.feats.joinToString(", ") { it.name }.ifEmpty { "none" }, valueStyle = Style.Small, onClick = {
                val all = made.feats.values.flatten().map { n -> Option(n) } + Option(TOUGH_FEAT, "not in the SRD: +2 max HP a level, every level so far")
                navigateTo(
                    screenFactory = { MultiPickScreen(it, "Feats", null, all, c.feats.map { f -> f.name }.toSet(), Kind.Feats) },
                    resultCallback = { names ->
                        // keep what each kept feat was taken for (Magic Initiate's list); Tough changes max HP
                        Store.edit { ch -> ch.withFeats(names.map { n -> ch.feats.find { it.name == n } ?: Feat(n) }) }
                    },
                )
            })
            KeyValueRow("Languages", c.languages.joinToString(", ").ifEmpty { "none" }, valueStyle = Style.Small, onClick = {
                val all = made.languages["standard"].orEmpty() + made.languages["rare"].orEmpty()
                navigateTo(
                    screenFactory = { MultiPickScreen(it, "Languages", null, all.map { n -> Option(n) }, c.languages.toSet()) },
                    resultCallback = { names -> Store.edit { ch -> ch.copy(languages = all.filter { it in names }) } },
                )
            })
            KeyValueRow("Training", c.training.size.let { if (it == 0) "none" else "$it" }, onClick = {
                askText("Training, comma between", c.training.joinToString(", ")) { t ->
                    Store.edit { ch -> ch.copy(training = t.split(",").map { it.trim() }.filter { it.isNotEmpty() }) }
                }
            })

            Section("Delete")
            TapRow(onClick = {
                navigateTo(
                    screenFactory = { ConfirmScreen(it, "Delete", "Delete ${c.name}, with their bag and spells? The roll log stays.", "Delete") },
                    resultCallback = { yes ->
                        if (yes == true) {
                            Store.deleteActive()
                            goBack(null)
                        }
                    },
                )
            }) {
                T("Delete ${c.name}", Style.Body)
            }
        }
    }

    private fun pick(title: String, options: List<Option>, selected: Int, onPick: (Int) -> Unit) =
        navigateTo(screenFactory = { PickScreen(it, title, options, selected.takeIf { i -> i >= 0 }) }, resultCallback = onPick)
}

/** A label and a number that opens the pad, kept within [range]. */
@Composable
fun DndScreen<*>.NumberRow(label: String, value: Int, range: IntRange, onSet: (Int) -> Unit) {
    KeyValueRow(label, value.toString(), {
        askNumber(NumberRequest(title = label, context = "${range.first} to ${range.last}", initial = value)) { r ->
            onSet(r.value.coerceIn(range))
        }
    })
}

/** Which saving throws add proficiency. */
class SavesEditScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character
        ScrollPage(scroll, title = "Saves", onBack = { goBack(null) }) {
            classInfo(c.className)?.let { cls ->
                T("${cls.name}: ${cls.saves.sortedBy { it.ordinal }.joinToString(" and ") { it.long }}.", Style.Small, dim = true, modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()))
            }
            Ability.entries.forEach { a ->
                val on = a in c.saves
                TapRow(onClick = { Store.edit { it.copy(saves = if (on) it.saves - a else it.saves + a) } }) {
                    T(if (on) "[x] " else "[ ] ", Style.Mono, dim = !on)
                    T(a.long, Style.Body, Modifier.weight(1f))
                    T(signed(c.save(a)), Style.Mono)
                }
            }
        }
    }
}

/** Each skill's training; a tap cycles none, proficient, expertise. */
class SkillsEditScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character
        ScrollPage(scroll, title = "Skills", onBack = { goBack(null) }) {
            T("Tap to cycle: none, proficient, expertise.", Style.Small, dim = true, modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()))
            Skill.entries.forEach { s ->
                val t = c.training(s)
                TapRow(onClick = { Store.edit { it.copy(skills = it.skills + (s to t.next())) } }) {
                    T("${t.mark} ", Style.Mono, dim = t.multiplier == 0)
                    T(s.label, Style.Body, Modifier.weight(1f), maxLines = 1)
                    T(signed(c.skill(s)), Style.Mono)
                }
            }
        }
    }
}

/** Slots per spell level. Level up sets them from the class table; this is for everything else. */
class SlotsEditScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character
        ScrollPage(scroll, title = "Spell slots", onBack = { goBack(null) }) {
            TapRow(onClick = { Store.edit(::fillSlots) }) {
                T("Fill from the ${c.className} table for level ${c.level}", Style.Body, Modifier.weight(1f))
            }
            c.slots.forEachIndexed { i, s ->
                NumberRow("${ordinal(i + 1)} level", s.max, 0..9) { v ->
                    Store.edit { ch -> ch.copy(slots = ch.slots.mapIndexed { j, old -> if (j == i) Slots(v, minOf(old.used, v)) else old }) }
                }
            }
        }
    }

    /** The class's own table; a class without one gets the full-caster table as a start. */
    private fun fillSlots(c: Character): Character {
        val cls = classInfo(c.className)
        val table = if (cls != null && cls.caster != Caster.None) slotsFor(cls, c.level) else FULL_CASTER_SLOTS[c.level.coerceIn(1, 20) - 1]
        return c.copy(slots = List(9) { i -> Slots(table.getOrElse(i) { 0 }, 0) })
    }
}

/** A yes/no question. Returns true for yes, null for back. */
class ConfirmScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val question: String,
    private val yes: String,
) : DndScreen<Boolean>(sealedActivity) {

    @Composable
    override fun Content() {
        ScrollPage(scroll, title = title, onBack = { goBack(null) }) {
            T(question, Style.Heading, Modifier.padding(top = 2f.gridUnitsAsDp(), bottom = 1.5f.gridUnitsAsDp()))
            PadKey(yes.uppercase(), Modifier.fillMaxWidth()) { goBack(true) }
        }
    }
}
