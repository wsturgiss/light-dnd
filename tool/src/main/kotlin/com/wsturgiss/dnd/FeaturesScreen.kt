package com.wsturgiss.dnd

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.Character
import com.wsturgiss.dnd.model.Creation
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Srd
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.choiceName
import com.wsturgiss.dnd.model.featuresTo
import com.wsturgiss.dnd.model.plainName
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/** Class features up to the character's level, with their levels; the subclass's once it's the SRD's own. */
fun classFeatures(c: Character, made: Creation): List<Pair<Int, String>> {
    val data = made.classes[c.className] ?: return emptyList()
    return data.featuresTo(c.level, withSubclass = c.subclass.isNotEmpty() && c.subclass == data.subclass)
}

/**
 * Everything the character can do that isn't a number: species traits, feats, class features,
 * languages and training. Each opens its full rules in Look up. Changed under Sheet › Edit.
 */
class FeaturesScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character
        val made = Srd.creation(lightContext)

        ScrollPage(
            scroll,
            title = "Features",
            folio = "3v",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.PENCIL, onClick = { navigateTo(screenFactory = { EditScreen(it) }) }),
        ) {
            Section(c.species.ifEmpty { "Species" })
            val species = made.species[c.species]
            if (species == null) {
                T("No species set. Pencil, top right.", Style.Body, dim = true)
            } else {
                species.traits.forEach { trait -> Link(trait, null) { openEntry(Kind.Species, c.species) } }
                if (c.lineage.isNotEmpty()) Link(choiceName(c.lineage), species.choice) { openEntry(Kind.Species, c.lineage) }
                else species.choice?.let { T("No ${it.lowercase()} chosen yet.", Style.Small, dim = true) }
            }

            Section("Feats")
            if (c.feats.isEmpty()) T("None recorded.", Style.Body, dim = true)
            c.feats.forEach { f -> Link(f.name, f.note.ifEmpty { null }) { openEntry(Kind.Feats, f.name) } }

            Section("${c.className} ${c.level}")
            if (c.subclass.isNotEmpty()) {
                Link(c.subclass, "subclass") { openEntry(Kind.Features, c.subclass) }
            }
            val features = classFeatures(c, made)
            if (features.isEmpty()) T("Nothing for this class in the SRD.", Style.Body, dim = true)
            features.forEach { (level, name) ->
                TapRow(onClick = { openEntry(Kind.Features, name) }) {
                    T(level.toString().padStart(2) + "  ", Style.Mono, dim = true)
                    T(plainName(name), Style.Body, Modifier.weight(1f), maxLines = 1)
                    T("›", Style.Heading, dim = true)
                }
            }

            Section("Languages")
            T(c.languages.joinToString(", ").ifEmpty { "None recorded." }, Style.Body, dim = c.languages.isEmpty())

            Section("Training")
            T(c.training.joinToString(", ").ifEmpty { "None recorded." }, Style.Small, dim = c.training.isEmpty())
            T("", Style.Small, modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()))
        }
    }

    @Composable
    private fun Link(name: String, detail: String?, onClick: () -> Unit) {
        TapRow(onClick = onClick) {
            T(name, Style.Body, Modifier.weight(1f), maxLines = 1)
            if (detail != null) T("$detail  ", Style.Small, dim = true, maxLines = 1)
            T("›", Style.Heading, dim = true)
        }
    }
}
