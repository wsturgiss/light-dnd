package com.wsturgiss.dnd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.signed
import com.wsturgiss.dnd.ui.Fleuron
import com.wsturgiss.dnd.ui.GUTTER
import com.wsturgiss.dnd.ui.Illuminated
import com.wsturgiss.dnd.ui.LandscapeStrip
import com.wsturgiss.dnd.ui.Page
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/**
 * The front door: who you are and how you're doing, then four places to go.
 * Big words in a column, the way LightOS lays out its own menus.
 */
@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character
        val last = save.lastRoll

        Page(
            title = "D&D",
            onBack = null,
            action = LightBarButton.LightIcon(LightIcons.CONTACTS, onClick = { navigateTo(screenFactory = { CharactersScreen(it) }) }),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = GUTTER.gridUnitsAsDp()),
            ) {
                TapRow(onClick = { navigateTo(screenFactory = { SheetScreen(it) }) }) {
                    Column(Modifier.weight(1f)) {
                        Illuminated(c.name)
                        T("Level ${c.level} ${c.className}", Style.Small, dim = true)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        T("${c.hp}/${c.maxHp}", Style.MonoBig)
                        T(if (c.tempHp > 0) "HP  +${c.tempHp} tmp" else "HP", Style.Small, dim = true)
                    }
                }
                if (c.conditions.isNotEmpty()) {
                    T(c.conditions.sorted().joinToString(" · "), Style.Small, maxLines = 1)
                }
                Fleuron(Modifier.padding(vertical = 0.6f.gridUnitsAsDp()))

                Menu("Roll", last?.let { "${it.label}  ${it.total}" }) {
                    navigateTo(screenFactory = { RollScreen(it, null) })
                }
                Menu("Sheet", "AC ${c.ac}  ·  init ${signed(c.initiative)}") {
                    navigateTo(screenFactory = { SheetScreen(it) })
                }
                Menu("Bag", "${c.items.size} items  ·  ${"%.0f".format(c.wealthInGp)} gp") {
                    navigateTo(screenFactory = { BagScreen(it) })
                }
                Menu("Spells", c.slots.withIndex().filter { it.value.max > 0 }.takeIf { it.isNotEmpty() }
                    ?.joinToString("  ") { "${it.index + 1}:${it.value.left}" }
                    ?: "${c.spells.size} known") {
                    navigateTo(screenFactory = { SpellsScreen(it) })
                }
                Menu("Features", c.feats.size.let { n -> "${c.species.ifEmpty { "traits" }}${if (n > 0) "  ·  $n feat${if (n == 1) "" else "s"}" else ""}" }) {
                    navigateTo(screenFactory = { FeaturesScreen(it) })
                }
                Menu("Look up", "spells, monsters, rules") {
                    navigateTo(screenFactory = { LookupScreen(it) })
                }

                Spacer(Modifier.weight(1f))
                // no room for the whole foot here: just the landscape, inside the frame
                LandscapeStrip(Modifier.padding(bottom = 0.4f.gridUnitsAsDp()))
            }
        }
    }

    @Composable
    private fun Menu(label: String, detail: String?, onClick: () -> Unit) {
        TapRow(onClick = onClick) {
            // the word first, whole; the detail gets what's left and is cut short if it must be
            T(label, Style.Heading, maxLines = 1)
            T(detail.orEmpty(), Style.MonoSmall, Modifier.weight(1f).padding(start = 0.5f.gridUnitsAsDp()), dim = true, align = TextAlign.End, maxLines = 1)
        }
    }
}
