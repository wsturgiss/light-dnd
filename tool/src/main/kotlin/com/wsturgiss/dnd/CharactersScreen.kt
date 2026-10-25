package com.wsturgiss.dnd

import androidx.compose.foundation.layout.Column
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
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.ui.Fleuron
import com.wsturgiss.dnd.ui.Illuminated
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/** Everyone you play. Tap one to play them; + builds a new one. Delete is under Sheet › Edit. */
class CharactersScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        ScrollPage(
            scroll,
            title = "Characters",
            onBack = { goBack(null) },
            folio = "1v",
            action = LightBarButton.LightIcon(LightIcons.ADD, onClick = {
                navigateTo(screenFactory = { CreateScreen(it) }, resultCallback = { made -> if (made) goBack(null) })
            }),
        ) {
            save.characters.forEachIndexed { i, c ->
                TapRow(onClick = {
                    Store.switchTo(i)
                    goBack(null)
                }, Modifier.padding(vertical = 0.2f.gridUnitsAsDp())) {
                    T(if (i == save.active) "► " else "  ", Style.Mono)
                    Column(Modifier.weight(1f).padding(end = 0.4f.gridUnitsAsDp())) {
                        Illuminated(c.name)
                        T(
                            listOf("Level ${c.level}", c.species, c.className).filter { it.isNotEmpty() }.joinToString(" "),
                            Style.Small,
                            dim = true,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        T("${c.hp}/${c.maxHp}", Style.Mono)
                        T("HP", Style.Small, dim = true)
                    }
                }
            }
            Fleuron(Modifier.padding(top = 0.6f.gridUnitsAsDp()))
            TapRow(onClick = {
                navigateTo(screenFactory = { DdbImportScreen(it) }, resultCallback = { done -> if (done) goBack(null) })
            }) {
                T("Import from D&D Beyond", Style.Body, Modifier.weight(1f))
                T("›", Style.Heading, dim = true)
            }
        }
    }
}
