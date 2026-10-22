package com.wsturgiss.dnd

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.castingAbility
import com.wsturgiss.dnd.model.spellAttack
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Slots
import com.wsturgiss.dnd.model.Srd
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.signed
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/**
 * Your spells and slots. Tap a slot row to spend one; tap its count to get one
 * back. The spells are the ones starred in Look up, grouped by level.
 */
class SpellsScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character
        val known = c.spells.mapNotNull { Srd.find(lightContext, Kind.Spells, it) }

        ScrollPage(
            scroll,
            title = "Spells",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.ADD, onClick = {
                navigateTo(screenFactory = { EntryListScreen(it, listOf(Kind.Spells), "Add spells", classFilter = c.className) })
            }),
        ) {
            c.untrainedArmor?.let { T("No spellcasting: wearing $it without training.", Style.Body, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp())) }
            castingAbility(c.className)?.let { a ->
                val attack = c.mod(a) + c.proficiency
                Row(Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp())) {
                    TapRow(onClick = null, Modifier.weight(1f)) {
                        T("save DC ", Style.Small, dim = true)
                        T("${8 + attack}", Style.MonoBig)
                    }
                    TapRow(onClick = { roll(RollRequest("d20${signed(attack)}", "Spell attack", attack = true)) }, Modifier.weight(1f)) {
                        T("attack ", Style.Small, dim = true)
                        T(signed(attack), Style.MonoBig)
                    }
                }
                T("${a.long} · tap attack to roll", Style.Small, dim = true)
            }
            val attacks = known.mapNotNull { c.spellAttack(it) }
            if (attacks.isNotEmpty()) {
                Section("Spell attacks")
                attacks.forEach { a -> AttackRow(a) { attack(c, a) } }
                T("A natural 20 rolls the damage too, its dice doubled.", Style.Small, dim = true)
            }

            Section("Slots")
            val levels = c.slots.withIndex().filter { it.value.max > 0 }
            if (levels.isEmpty()) T("No slots. Set them under Sheet › Edit.", Style.Body, dim = true)
            levels.forEach { (i, s) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TapRow(onClick = { setUsed(i, s.used + 1) }, Modifier.weight(1f)) {
                        T(ordinal(i + 1).padEnd(5), Style.Mono)
                        T(pips(s), Style.Mono, maxLines = 1)
                    }
                    TapRow(onClick = { setUsed(i, s.used - 1) }, Modifier.weight(0.45f)) {
                        T("${s.left}/${s.max} +", Style.MonoSmall, dim = true)
                    }
                }
            }

            Section("Known")
            if (known.isEmpty()) T("None yet. + to find spells for your class, then star them.", Style.Body, dim = true)
            known.groupBy { it.level ?: 0 }.toSortedMap().forEach { (level, spells) ->
                T(if (level == 0) "Cantrips" else "${ordinal(level)} level", Style.Small, dim = true, modifier = Modifier.padding(top = 0.6f.gridUnitsAsDp()))
                spells.forEach { e ->
                    TapRow(onClick = { openEntry(Kind.Spells, e.name) }) {
                        T(e.name, Style.Body, Modifier.weight(1f), maxLines = 1)
                        T(e.meta.substringAfter(" · ", ""), Style.MonoSmall, dim = true)
                    }
                }
            }
        }
    }

    /** `[x][x][ ][ ]`: spent on the left, so what's left reads as the empty boxes. */
    private fun pips(s: Slots) = "[x]".repeat(s.used.coerceAtMost(s.max)) + "[ ]".repeat(s.left)

    private fun setUsed(level: Int, used: Int) = Store.edit { c ->
        c.copy(slots = c.slots.mapIndexed { i, s -> if (i == level) s.copy(used = used.coerceIn(0, s.max)) else s })
    }
}
