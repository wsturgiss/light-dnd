package com.wsturgiss.dnd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.wsturgiss.dnd.model.Ability
import com.wsturgiss.dnd.model.Character
import com.wsturgiss.dnd.model.D20Test
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Skill
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.Training
import com.wsturgiss.dnd.model.signed
import com.wsturgiss.dnd.ui.KeyValueRow
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/**
 * The character sheet, laid out like a typed one. Everything with a bonus is a
 * roll: tap an ability for a check, its save for a save (P: proficient), a skill for
 * that skill. HP opens the damage/heal pad.
 */
class SheetScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character

        ScrollPage(
            scroll,
            title = "Sheet",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.PENCIL, onClick = { navigateTo(screenFactory = { EditScreen(it) }) }),
        ) {
            T(c.name, Style.Banner, Modifier.padding(top = 0.5f.gridUnitsAsDp()))
            T(
                listOf("Level ${c.level}", c.species, c.classLabel).filter { it.isNotEmpty() }.joinToString(" ") +
                    "  ·  proficiency ${signed(c.proficiency)}",
                Style.Small,
                dim = true,
            )

            TapRow(onClick = ::adjustHp, Modifier.padding(top = 0.5f.gridUnitsAsDp())) {
                T("HP", Style.Heading, Modifier.weight(1f))
                T("${c.hp} / ${c.maxHp}", Style.MonoBig)
                if (c.tempHp > 0) T("  +${c.tempHp}", Style.Mono, dim = true)
            }
            Row(Modifier.fillMaxWidth()) {
                Stat("AC", c.ac.toString(), Modifier.weight(1f), null)
                Stat("INIT", signed(c.initiative), Modifier.weight(1f)) { roll(c.request("d20${signed(c.initiative)}", "Initiative", D20Test.Initiative)) }
                Stat("SPEED", "${c.speed}", Modifier.weight(1f), null)
                Stat("PASSIVE", "${c.passivePerception}", Modifier.weight(1f), null)
            }
            c.tooHeavy?.let { T("Speed -10: ${it.name} needs STR ${it.strength}.", Style.Small, dim = true) }
            if (c.untrainedShield) T("Shield: no training, so no +2 AC.", Style.Small, dim = true)
            KeyValueRow(
                "Conditions",
                c.conditions.sorted().joinToString(", ").ifEmpty { "none" },
                onClick = { navigateTo(screenFactory = { ConditionsScreen(it) }) },
                valueStyle = Style.Small,
            )
            KeyValueRow("Rest", "long · short", onClick = { navigateTo(screenFactory = { RestScreen(it) }) }, valueStyle = Style.Small)

            Section("Abilities")
            // three equal columns: ability and score, check, save; the numbers right-aligned under their headers
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                T("check", Style.Small, dim = true, modifier = Modifier.weight(1f), align = TextAlign.End)
                T("save", Style.Small, dim = true, modifier = Modifier.weight(1f), align = TextAlign.End)
            }
            Ability.entries.forEach { a ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TapRow(onClick = { roll(c.request("d20${signed(c.mod(a))}", "${a.long} check", D20Test.Check(a))) }, Modifier.weight(2f)) {
                        T("${a.short} ${c.score(a).toString().padStart(2)}", Style.Mono, Modifier.weight(1f))
                        T(signed(c.mod(a)), Style.Mono, Modifier.weight(1f), align = TextAlign.End)
                    }
                    TapRow(onClick = { roll(c.request("d20${signed(c.save(a))}", "${a.long} save", D20Test.Save(a))) }, Modifier.weight(1f)) {
                        // P: proficient, so the save adds the proficiency bonus
                        T(
                            "${if (a in c.saves) "P " else ""}${signed(c.save(a))}",
                            Style.Mono,
                            Modifier.weight(1f),
                            align = TextAlign.End,
                        )
                    }
                }
            }
            c.untrainedArmor?.let { T("DIS on STR and DEX: $it, untrained. No spellcasting in it.", Style.Small, dim = true) }

            Section("Skills")
            val stealthDis = c.stealthDisadvantageFrom
            Skill.entries.forEach { s ->
                val dis = c.disadvantageFrom(D20Test.Check(s.ability, s))
                val adv = c.advantageFrom(D20Test.Check(s.ability, s))
                TapRow(onClick = { roll(c.request("d20${signed(c.skill(s))}", s.label, D20Test.Check(s.ability, s))) }) {
                    // the same marks as the saves; Edit keeps the checkboxes, since there they're toggles
                    val mark = when (c.training(s)) {
                        Training.None -> " "
                        Training.Proficient -> "P"
                        Training.Expertise -> "E"
                    }
                    T("$mark ", Style.Mono)
                    T(s.label, Style.Body, Modifier.weight(1f), maxLines = 1)
                    if (adv.isNotEmpty()) T("ADV ", Style.MonoSmall)
                    if (dis.isNotEmpty()) T("DIS ", Style.MonoSmall)
                    T(s.ability.short.lowercase() + " ", Style.MonoSmall, dim = true)
                    T(signed(c.skill(s)).padStart(3), Style.Mono)
                }
            }
            T("P proficient   E expertise", Style.Small, dim = true, modifier = Modifier.padding(top = 0.4f.gridUnitsAsDp()))
            if (stealthDis.isNotEmpty()) T("DIS on Stealth: ${stealthDis.joinToString(", ")}", Style.Small, dim = true)
            if (c.remarkableAthlete) T("ADV on Initiative and Athletics: Remarkable Athlete", Style.Small, dim = true)
            if (c.jackOfAllTrades) T("Jack of All Trades: ${signed(c.proficiency / 2)} on skills without P", Style.Small, dim = true)
        }
    }

    /** A roll of [test], with what gives it advantage or disadvantage. */
    private fun Character.request(expr: String, label: String, test: D20Test) =
        RollRequest(expr, label, disadvantageFrom = disadvantageFrom(test), advantageFrom = advantageFrom(test))

    @Composable
    private fun Stat(label: String, value: String, modifier: Modifier, onClick: (() -> Unit)?) {
        Column(
            modifier
                .then(if (onClick != null) Modifier.lightClickable(onClick = onClick) else Modifier)
                .padding(vertical = 0.4f.gridUnitsAsDp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(value, Style.MonoBig)
            T(if (onClick != null) "$label ›" else label, Style.Small, dim = true)
        }
    }

    private fun adjustHp() {
        val c = Store.character
        askNumber(
            NumberRequest(
                title = "Hit points",
                context = "HP ${c.hp}/${c.maxHp}" + if (c.tempHp > 0) "  ·  ${c.tempHp} temporary" else "",
                actions = listOf("Damage", "Heal", "Temp"),
            ),
        ) { r ->
            Store.edit {
                when (r.action) {
                    0 -> it.damage(r.value)
                    1 -> it.heal(r.value)
                    else -> it.withTemp(r.value)
                }
            }
        }
    }
}

/** A long rest puts HP and spell slots back. */
class RestScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character
        ScrollPage(
            scroll,
            title = "Rest",
            onBack = { goBack(null) },
        ) {
            Section("Long rest")
            T("Eight hours. You regain all hit points and spell slots, and temporary hit points end.", Style.Body)
            TapRow(onClick = {
                Store.edit { it.longRest() }
                goBack(null)
            }) {
                T("Take a long rest", Style.Body, Modifier.weight(1f))
                T("›", Style.Heading, dim = true)
            }
            T(
                "Now: HP ${c.hp}/${c.maxHp}" +
                    c.slots.withIndex().filter { it.value.max > 0 }.joinToString("") { "  ·  ${ordinal(it.index + 1)} ${it.value.left}/${it.value.max}" },
                Style.Small,
                dim = true,
                modifier = Modifier.padding(top = 0.6f.gridUnitsAsDp()),
            )
            Section("Short rest")
            T("An hour. Spend Hit Dice to heal: roll them on the dice screen, then Heal from the HP pad on the sheet.", Style.Body)
            TapRow(onClick = { roll("d${hitDie(c.className)}${signed(c.mod(Ability.CON))}", "Hit Die") }) {
                T("Roll a Hit Die", Style.Body, Modifier.weight(1f))
                T("d${hitDie(c.className)}${signed(c.mod(Ability.CON))}", Style.Mono)
            }
        }
    }
}

fun ordinal(n: Int) = when (n) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th" }

fun hitDie(className: String): Int = com.wsturgiss.dnd.model.classInfo(className)?.hitDie ?: 8

/** Toggle conditions on yourself; the right column looks up what one does. */
class ConditionsScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val active = save.character.conditions
        val all = com.wsturgiss.dnd.model.Srd.load(lightContext, Kind.Conditions)
        ScrollPage(scroll, title = "Conditions", onBack = { goBack(null) }) {
            T("Tap to set or clear. › to read the rule.", Style.Small, dim = true, modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()))
            all.forEach { e ->
                val on = e.name in active
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TapRow(onClick = {
                        Store.edit { it.copy(conditions = if (on) it.conditions - e.name else it.conditions + e.name) }
                    }, Modifier.weight(1f)) {
                        T(if (on) "[x] " else "[ ] ", Style.Mono, dim = !on)
                        T(e.name, Style.Body)
                    }
                    TapRow(onClick = { openEntry(Kind.Conditions, e.name) }, Modifier.weight(0.25f)) {
                        T("›", Style.Heading, dim = true)
                    }
                }
            }
        }
    }
}
