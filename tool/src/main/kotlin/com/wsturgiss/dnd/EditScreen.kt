package com.wsturgiss.dnd

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.Ability
import com.wsturgiss.dnd.model.CLASSES
import com.wsturgiss.dnd.model.Character
import com.wsturgiss.dnd.model.FULL_CASTER_SLOTS
import com.wsturgiss.dnd.model.Skill
import com.wsturgiss.dnd.model.Slots
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.signed
import com.wsturgiss.dnd.ui.KeyValueRow
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/** Every field on the sheet, one row each. Numbers open the pad, text opens the keyboard. */
class EditScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character

        ScrollPage(scroll, title = "Edit", onBack = { goBack(null) }) {
            Section("Who")
            KeyValueRow("Name", c.name, { askText("Name", c.name) { n -> Store.edit { it.copy(name = n) } } }, valueStyle = Style.Body)
            KeyValueRow("Class", c.className, { navigateTo(screenFactory = { ClassPickerScreen(it) }) }, valueStyle = Style.Body)
            NumberRow("Level", c.level, 1..20) { v -> Store.edit { it.copy(level = v) } }

            Section("Scores")
            Ability.entries.forEach { a ->
                NumberRow(a.long, c.score(a), 1..30) { v -> Store.edit { it.copy(scores = it.scores + (a to v)) } }
            }

            Section("Combat")
            NumberRow("Max HP", c.maxHp, 1..999) { v -> Store.edit { it.copy(maxHp = v, hp = minOf(it.hp, v)) } }
            NumberRow("Armor class", c.ac, 1..40) { v -> Store.edit { it.copy(ac = v) } }
            NumberRow("Speed", c.speed, 0..200) { v -> Store.edit { it.copy(speed = v) } }

            Section("Saves")
            Ability.entries.forEach { a ->
                val on = a in c.saves
                TapRow(onClick = { Store.edit { it.copy(saves = if (on) it.saves - a else it.saves + a) } }) {
                    T(if (on) "[x] " else "[ ] ", Style.Mono, dim = !on)
                    T(a.long, Style.Body, Modifier.weight(1f))
                    T(signed(c.save(a)), Style.Mono)
                }
            }

            Section("Skills")
            T("Tap to cycle: none, proficient, expertise.", Style.Small, dim = true)
            Skill.entries.forEach { s ->
                val t = c.training(s)
                TapRow(onClick = { Store.edit { it.copy(skills = it.skills + (s to t.next())) } }) {
                    T("${t.mark} ", Style.Mono, dim = t.multiplier == 0)
                    T(s.label, Style.Body, Modifier.weight(1f), maxLines = 1)
                    T(signed(c.skill(s)), Style.Mono)
                }
            }

            Section("Spell slots")
            TapRow(onClick = { Store.edit(::fillSlots) }) {
                T("Fill from the full-caster table for level ${c.level}", Style.Body, Modifier.weight(1f))
            }
            T("Half casters and warlocks: set each level by hand.", Style.Small, dim = true)
            c.slots.forEachIndexed { i, s ->
                NumberRow("${ordinal(i + 1)} level", s.max, 0..9) { v ->
                    Store.edit { ch -> ch.copy(slots = ch.slots.mapIndexed { j, old -> if (j == i) Slots(v, minOf(old.used, v)) else old }) }
                }
            }

            Section("Start over")
            TapRow(onClick = {
                navigateTo(
                    screenFactory = { ConfirmScreen(it, "New character", "Clear this sheet, bag and spells? The roll log stays.", "Clear") },
                    resultCallback = { yes -> if (yes == true) Store.edit { Character(name = "New hero") } },
                )
            }) {
                T("New blank character", Style.Body)
            }
            T("", Style.Small, modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()))
        }
    }

    @Composable
    private fun NumberRow(label: String, value: Int, range: IntRange, onSet: (Int) -> Unit) {
        KeyValueRow(label, value.toString(), {
            askNumber(NumberRequest(title = label, context = "${range.first} to ${range.last}", initial = value)) { r ->
                onSet(r.value.coerceIn(range))
            }
        })
    }

    private fun fillSlots(c: Character): Character {
        val table = FULL_CASTER_SLOTS[c.level.coerceIn(1, 20) - 1]
        return c.copy(slots = List(9) { i -> Slots(table.getOrElse(i) { 0 }, 0) })
    }
}

class ClassPickerScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        ScrollPage(scroll, title = "Class", onBack = { goBack(null) }) {
            CLASSES.forEach { name ->
                TapRow(onClick = {
                    Store.edit { it.copy(className = name) }
                    goBack(null)
                }) {
                    T(if (name == save.character.className) "> " else "  ", Style.Mono)
                    T(name, Style.Heading)
                }
            }
        }
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
        ScrollPage(
            scroll,
            title = title,
            onBack = { goBack(null) },
            bottom = listOf(
                com.thelightphone.sdk.ui.LightBarButton.Text("Cancel", onClick = { goBack(null) }),
                com.thelightphone.sdk.ui.LightBarButton.Text(yes, onClick = { goBack(true) }),
            ),
        ) {
            T(question, Style.Heading, Modifier.padding(top = 2f.gridUnitsAsDp()))
        }
    }
}
