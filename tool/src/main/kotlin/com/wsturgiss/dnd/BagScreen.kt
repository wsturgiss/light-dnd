package com.wsturgiss.dnd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.thelightphone.sdk.ui.lightClickable
import com.wsturgiss.dnd.model.Coin
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Srd
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.ui.Markup
import com.wsturgiss.dnd.ui.Rule
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/** Coins across the top, then everything you carry. */
class BagScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character

        ScrollPage(
            scroll,
            title = "Bag",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.ADD, onClick = {
                navigateTo(screenFactory = { EntryListScreen(it, listOf(Kind.Equipment, Kind.MagicItems), "Add to bag", pickForBag = true) })
            }),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 0.6f.gridUnitsAsDp())) {
                Coin.entries.reversed().forEach { coin ->
                    Column(
                        Modifier
                            .weight(1f)
                            .lightClickable { adjustCoin(coin) }
                            .padding(vertical = 0.4f.gridUnitsAsDp()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        T((c.coins[coin] ?: 0).toString(), Style.MonoBig, dim = (c.coins[coin] ?: 0) == 0)
                        T(coin.label, Style.Small, dim = true)
                    }
                }
            }
            T(
                "worth ${"%.1f".format(c.wealthInGp)} gp  ·  carrying ${"%.1f".format(c.carried)} lb",
                Style.Small,
                dim = true,
            )
            Rule(Modifier.padding(vertical = 0.6f.gridUnitsAsDp()))

            if (c.items.isEmpty()) T("Empty. + to add something.", Style.Body, dim = true)
            c.items.forEach { item ->
                TapRow(onClick = { navigateTo(screenFactory = { ItemScreen(it, item.id) }) }) {
                    T(item.name, Style.Body, Modifier.weight(1f), maxLines = 1)
                    if (c.isWorn(item)) T("worn  ", Style.MonoSmall, dim = true)
                    T("x${item.qty}", Style.Mono, dim = item.qty == 1)
                }
            }
        }
    }

    private fun adjustCoin(coin: Coin) {
        val have = Store.character.coins[coin] ?: 0
        askNumber(NumberRequest(title = coin.label.uppercase(), context = "You have $have ${coin.label}", actions = listOf("Add", "Spend", "Set"))) { r ->
            Store.edit {
                val now = it.coins[coin] ?: 0
                val next = when (r.action) {
                    0 -> now + r.value
                    1 -> (now - r.value).coerceAtLeast(0)
                    else -> r.value
                }
                it.copy(coins = it.coins + (coin to next))
            }
        }
    }
}

/** One thing in the bag: how many, and what the SRD says about it. */
class ItemScreen(sealedActivity: SealedLightActivity, private val id: Long) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val item = save.character.items.find { it.id == id }
        if (item == null) {
            ScrollPage(scroll, title = "Gone", onBack = { goBack(null) }) {}
            return
        }
        val kind = Kind.ofFile(item.srdKind)
        val entry = kind?.let { Srd.find(lightContext, it, item.name) }

        fun setQty(q: Int) = Store.edit { c -> c.copy(items = c.items.map { if (it.id == id) it.copy(qty = q.coerceAtLeast(0)) else it }) }

        ScrollPage(
            scroll,
            title = item.name,
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.PENCIL, contentDescription = "Rename", onClick = {
                askText("Rename", item.name) { n ->
                    // renamed, it's no longer armor the sheet knows: take it off rather than keep a stale AC
                    Store.edit { c -> (if (c.isWorn(item)) c.toggleWorn(item) else c).copy(items = c.items.map { if (it.id == id) it.copy(name = n) else it }) }
                }
            }),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 0.8f.gridUnitsAsDp()), verticalAlignment = Alignment.CenterVertically) {
                PadKey("-", Modifier.weight(1f)) { setQty(item.qty - 1) }
                Column(
                    Modifier
                        .weight(1.4f)
                        .lightClickable {
                            askNumber(NumberRequest("Quantity", item.name, item.qty)) { r -> setQty(r.value) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    T(item.qty.toString(), Style.MonoBig)
                    T("carried", Style.Small, dim = true)
                }
                PadKey("+", Modifier.weight(1f)) { setQty(item.qty + 1) }
            }
            if (item.weight > 0) T("${"%.2f".format(item.weight).trimEnd('0').trimEnd('.')} lb each", Style.Small, dim = true)
            val c = save.character
            if (c.wearable(item)) {
                val worn = c.isWorn(item)
                TapRow(onClick = { Store.edit { it.toggleWorn(item) } }, Modifier.padding(top = 0.4f.gridUnitsAsDp())) {
                    T(if (worn) "[x] " else "[ ] ", Style.Mono, dim = !worn)
                    T(if (item.name == "Shield") "Carrying it" else "Wearing it", Style.Body, Modifier.weight(1f))
                    T("AC ${c.ac}", Style.Mono)
                }
            }
            val dis = c.givesStealthDisadvantage(item)
            TapRow(onClick = {
                Store.edit { ch -> ch.copy(items = ch.items.map { if (it.id == id) it.copy(stealthDisadvantage = !dis) else it }) }
            }, Modifier.padding(top = 0.4f.gridUnitsAsDp())) {
                T(if (dis) "[x] " else "[ ] ", Style.Mono, dim = !dis)
                T("Disadvantage on Stealth", Style.Body, Modifier.weight(1f))
            }
            if (dis) T(if (c.wearable(item)) "While worn." else "While carried.", Style.Small, dim = true)
            if (entry != null) {
                Section(kind.one)
                T(entry.meta, Style.Small, dim = true)
                Spacer(Modifier.height(0.4f.gridUnitsAsDp()))
                Markup(entry.body, onRoll = { roll(it, item.name) })
            }
            TapRow(onClick = {
                Store.edit { c -> (if (c.isWorn(item)) c.toggleWorn(item) else c).copy(items = c.items.filterNot { it.id == id }) }
                goBack(null)
            }, Modifier.padding(top = 1f.gridUnitsAsDp())) {
                T("Remove from bag", Style.Body)
            }
        }
    }
}
