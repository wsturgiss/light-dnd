package com.wsturgiss.dnd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.wsturgiss.dnd.model.Entry
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Srd
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.ui.Fleuron
import com.wsturgiss.dnd.ui.Illuminated
import com.wsturgiss.dnd.ui.LandscapeStrip
import com.wsturgiss.dnd.ui.GUTTER
import com.wsturgiss.dnd.ui.Markup
import com.wsturgiss.dnd.ui.Page
import com.wsturgiss.dnd.ui.Rule
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/** The six SRD lists, and a search across all of them. */
class LookupScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        ScrollPage(
            scroll,
            title = "Look up",
            folio = "6r",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.SEARCH, onClick = {
                askText("Search everything") { q ->
                    navigateTo(screenFactory = { EntryListScreen(it, Kind.entries, "Search", initialQuery = q) })
                }
            }),
        ) {
            Spacer(Modifier.height(0.5f.gridUnitsAsDp()))
            Kind.entries.forEach { kind ->
                TapRow(onClick = { navigateTo(screenFactory = { EntryListScreen(it, listOf(kind), kind.label) }) }) {
                    T(kind.label, Style.Heading, Modifier.weight(1f))
                    T(Srd.load(lightContext, kind).size.toString(), Style.MonoSmall, dim = true)
                }
            }
            Fleuron(Modifier.padding(vertical = 0.8f.gridUnitsAsDp()))
            TapRow(onClick = { navigateTo(screenFactory = { AboutScreen(it) }) }) {
                T("About & credits", Style.Body, dim = true)
            }
        }
    }
}

private val CR_BUCKETS = listOf<Pair<String, ClosedFloatingPointRange<Double>?>>(
    "all" to null, "0-1" to 0.0..1.0, "2-4" to 2.0..4.0, "5-10" to 5.0..10.0, "11+" to 11.0..99.0,
)

/**
 * A list of SRD entries with search, and the filters that matter on a phone:
 * class and level for spells, a CR band for monsters.
 *
 * With [pickForBag], tapping an entry puts one in the bag instead of opening it.
 */
class EntryListScreen(
    sealedActivity: SealedLightActivity,
    private val kinds: List<Kind>,
    private val title: String,
    initialQuery: String = "",
    classFilter: String? = null,
    private val pickForBag: Boolean = false,
) : DndScreen<Unit>(sealedActivity) {

    private var query by mutableStateOf(initialQuery)
    private var spellClass by mutableStateOf(classFilter)
    /** -1 for every level. */
    private var spellLevel by mutableStateOf(-1)
    private var crBucket by mutableStateOf(0)
    /** Kept on the screen, like [scroll], so the list is where you left it after opening an entry. */
    private val listScroll = LazyListState()

    private val isSpells get() = kinds == listOf(Kind.Spells)
    private val isMonsters get() = kinds == listOf(Kind.Monsters)

    private fun results(): List<Pair<Kind, Entry>> {
        val base = if (query.isBlank()) kinds.flatMap { k -> Srd.load(lightContext, k).map { k to it } }
        else Srd.search(lightContext, kinds, query)
        return base.filter { (_, e) ->
            (!isSpells || spellClass == null || spellClass in e.classes) &&
                (!isSpells || spellLevel < 0 || e.level == spellLevel) &&
                (!isMonsters || CR_BUCKETS[crBucket].second?.let { r -> (e.cr ?: 0.0) in r } ?: true)
        }
    }

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val list = remember(query, spellClass, spellLevel, crBucket) { results() }
        val starred = save.character.spells.toSet()

        Page(
            title = title,
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.SEARCH, onClick = { askText("Search ${title.lowercase()}", query) { query = it } }),
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = GUTTER.gridUnitsAsDp())) {
                    if (query.isNotBlank()) {
                        TapRow(onClick = { query = "" }) {
                            T("\"$query\"", Style.Body, Modifier.weight(1f), maxLines = 1)
                            T("clear", Style.Small, dim = true)
                        }
                    }
                    if (isSpells) FilterRow()
                    if (isMonsters) {
                        Filter("CR", CR_BUCKETS[crBucket].first) { crBucket = (crBucket + 1) % CR_BUCKETS.size }
                    }
                    if (pickForBag) {
                        TapRow(onClick = {
                            askText("Item name", query) { name ->
                                Store.addItem(name)
                                goBack(null)
                            }
                        }) {
                            T("+ Something else (type a name)", Style.Body)
                        }
                    }
                    T("${list.size} found", Style.Small, dim = true)
                    Rule(Modifier.padding(top = 0.3f.gridUnitsAsDp()))
                }
                LightLazyScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = GUTTER.gridUnitsAsDp()),
                    listState = listScroll,
                    uniformItemHeightGridUnits = ROW_UNITS,
                ) {
                    items(list, key = { "${it.first}/${it.second.name}" }) { (kind, e) ->
                        EntryRow(kind, e, starred = kind == Kind.Spells && e.name in starred)
                    }
                }
            }
        }
    }

    @Composable
    private fun FilterRow() {
        Row(Modifier.fillMaxWidth()) {
            Filter("class", spellClass ?: "all", Modifier.weight(1f)) {
                spellClass = if (spellClass == null) Store.character.className else null
            }
            Filter("level", if (spellLevel < 0) "all" else if (spellLevel == 0) "cantrip" else spellLevel.toString(), Modifier.weight(1f)) {
                spellLevel = if (spellLevel >= 9) -1 else spellLevel + 1
            }
        }
    }

    @Composable
    private fun Filter(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
        TapRow(onClick = onClick, modifier) {
            T("$label ", Style.Small, dim = true)
            T(value, Style.Mono)
            T(" ›", Style.Mono, dim = true)
        }
    }

    @Composable
    private fun EntryRow(kind: Kind, e: Entry, starred: Boolean) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(ROW_UNITS.gridUnitsAsDp())
                .lightClickable {
                    if (pickForBag) {
                        Store.addItem(e.name, e.weight ?: 0.0, kind.file)
                        goBack(null)
                    } else {
                        openEntry(kind, e.name)
                    }
                },
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(e.name, Style.Body, Modifier.weight(1f), maxLines = 1)
                if (starred) T("*", Style.Mono)
            }
            T(if (kinds.size > 1) "${kind.one} · ${e.meta}" else e.meta, Style.Small, dim = true, maxLines = 1)
        }
    }

    private companion object {
        const val ROW_UNITS = 4.2f
    }
}

/**
 * One SRD entry. Dice in the text roll when tapped. The top-right action does the one
 * thing that fits the kind: star a spell, bag an item. A condition gets a checkbox instead.
 */
class EntryScreen(
    sealedActivity: SealedLightActivity,
    private val kind: Kind,
    private val name: String,
) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character
        val e = remember { Srd.find(lightContext, kind, name) }
        if (e == null) {
            ScrollPage(scroll, title = kind.label, onBack = { goBack(null) }) { T("Not found: $name", Style.Body) }
            return
        }
        val action: LightBarButton? = when (kind) {
            Kind.Spells -> {
                val mine = e.name in c.spells
                LightBarButton.LightIcon(if (mine) LightIcons.STAR else LightIcons.STAR_OUTLINE, onClick = {
                    Store.edit { it.copy(spells = if (mine) it.spells - e.name else it.spells + e.name) }
                })
            }
            Kind.Equipment, Kind.MagicItems -> LightBarButton.LightIcon(LightIcons.ADD, contentDescription = "Add to bag", onClick = {
                Store.addItem(e.name, e.weight ?: 0.0, kind.file)
            })
            else -> null
        }
        val inBag = c.items.find { it.name.equals(e.name, ignoreCase = true) }?.qty

        ScrollPage(scroll, title = kind.label, onBack = { goBack(null) }, action = action) {
            Illuminated(e.name, Modifier.padding(top = 0.5f.gridUnitsAsDp()), maxLines = 2)
            T(e.meta, Style.Small, dim = true)
            if (kind == Kind.Conditions) {
                val on = e.name in c.conditions
                TapRow(onClick = {
                    Store.edit { it.copy(conditions = if (on) it.conditions - e.name else it.conditions + e.name) }
                }) {
                    T(if (on) "[x] " else "[ ] ", Style.Mono, dim = !on)
                    T("On you", Style.Body)
                }
            }
            when {
                kind == Kind.Spells && e.name in c.spells -> T("* in your spells", Style.MonoSmall)
                inBag != null && (kind == Kind.Equipment || kind == Kind.MagicItems) -> T("x$inBag in your bag", Style.MonoSmall)
            }
            Spacer(Modifier.height(0.8f.gridUnitsAsDp()))
            Markup(e.body, onRoll = { roll(it, e.name) })
        }
    }
}

class AboutScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        ScrollPage(scroll, title = "About", onBack = { goBack(null) }) {
            LandscapeStrip(Modifier.padding(top = 1f.gridUnitsAsDp()))
            Section("Rules text")
            T(
                "This work includes material from the System Reference Document 5.2 (\"SRD 5.2\") by Wizards of " +
                    "the Coast LLC, available at https://www.dndbeyond.com/srd. The SRD 5.2 is licensed under the " +
                    "Creative Commons Attribution 4.0 International License, available at " +
                    "https://creativecommons.org/licenses/by/4.0/legalcode.",
                Style.Small,
            )
            T("Converted from the 5e-bits/5e-database JSON (MIT).", Style.Small, dim = true, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()))
            Section("Type")
            T("MEK Sans and MEK Dings by MEK.txt (Michael Alexander), mek.gallery. Released CC0.", Style.Small)
            T("Jacquard 24 by Sarah Cadigan-Fried (The Soft Type Project), SIL Open Font License 1.1.", Style.Small, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()))
            T("PxPlus IBM VGA 8x16 from VileR's Oldschool PC Font Pack, int10h.org, CC BY-SA 4.0.", Style.Small, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()))
            Section("Look")
            T("After Script Wizards' typewriter character sheets and table-side CLIs; framed as illuminated folios.", Style.Small)
        }
    }
}
