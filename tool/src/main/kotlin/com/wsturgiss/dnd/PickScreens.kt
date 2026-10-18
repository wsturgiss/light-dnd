package com.wsturgiss.dnd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Skill
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

data class Option(val label: String, val detail: String = "")

/** One choice from a list, with a line of detail under each. Returns the index picked. */
class PickScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val options: List<Option>,
    private val selected: Int?,
    private val note: String? = null,
    /** With it, a › on each row opens the option's Look up entry. */
    private val readKind: Kind? = null,
    /** The Look up name for an option, when its label is shortened ("High Elf"). */
    private val readName: (Int) -> String = { options[it].label },
) : DndScreen<Int>(sealedActivity) {

    @Composable
    override fun Content() {
        ScrollPage(scroll, title = title, onBack = { goBack(null) }) {
            note?.let { T(it, Style.Small, dim = true, modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp())) }
            options.forEachIndexed { i, o ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TapRow(onClick = { goBack(i) }, Modifier.weight(1f)) {
                        T(if (i == selected) "> " else "  ", Style.Mono)
                        Column(Modifier.weight(1f)) {
                            T(o.label, Style.Heading, maxLines = 1)
                            if (o.detail.isNotEmpty()) T(o.detail, Style.Small, dim = true)
                        }
                    }
                    readKind?.let { k ->
                        TapRow(onClick = { openEntry(k, readName(i)) }, Modifier.weight(0.2f)) {
                            T("›", Style.Heading, dim = true)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pick up to [count] skills from [from]. Skills in [have] are already trained some other way:
 * shown, but not pickable. Done returns the set; back leaves it as it was.
 */
class SkillPickScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val count: Int,
    private val from: List<Skill>,
    initial: Set<Skill>,
    private val have: Set<Skill> = emptySet(),
) : DndScreen<Set<Skill>>(sealedActivity) {

    private var picked by mutableStateOf(initial intersect from.toSet())

    @Composable
    override fun Content() {
        ScrollPage(
            scroll,
            title = title,
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.ACCEPT, contentDescription = "Done", onClick = { goBack(picked) }),
        ) {
            T("Choose $count  ·  ${picked.size} picked", Style.Small, dim = true, modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()))
            from.forEach { s ->
                val on = s in picked
                val taken = s in have && !on
                // already trained elsewhere: faded past dim, which alone reads too close to the rest
                TapRow(onClick = if (taken) null else ({
                    picked = when {
                        on -> picked - s
                        // full: swap out the oldest pick, so a change of mind is one tap
                        picked.size >= count -> picked.drop(1).toSet() + s
                        else -> picked + s
                    }
                }), if (taken) Modifier.alpha(0.55f) else Modifier) {
                    T(if (on) "[x] " else "[ ] ", Style.Mono, dim = !on)
                    T(s.label, Style.Body, Modifier.weight(1f), dim = taken, maxLines = 1)
                    T(if (taken) "have" else s.ability.short.lowercase(), Style.MonoSmall, dim = true)
                }
            }
        }
    }
}

/**
 * Pick up to [count] of [options] by name, or any number when it's null; [have] are shown but
 * not pickable. With [readKind], a › on each row opens its Look up entry, so you can read a
 * spell or feat before choosing it. Done returns the names; back leaves them as they were.
 */
class MultiPickScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val count: Int?,
    private val options: List<Option>,
    initial: Set<String>,
    private val readKind: Kind? = null,
    private val have: Set<String> = emptySet(),
    private val note: String? = null,
) : DndScreen<Set<String>>(sealedActivity) {

    private var picked by mutableStateOf(initial.filter { n -> options.any { it.label == n } })

    @Composable
    override fun Content() {
        ScrollPage(
            scroll,
            title = title,
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.ACCEPT, contentDescription = "Done", onClick = { goBack(picked.toSet()) }),
        ) {
            T(
                (if (count != null) "Choose $count  ·  ${picked.size} picked" else "${picked.size} picked") +
                    (note?.let { "\n$it" } ?: ""),
                Style.Small,
                dim = true,
                modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
            )
            if (options.isEmpty()) T("Nothing to choose from.", Style.Body, dim = true)
            options.forEach { o ->
                val on = o.label in picked
                val taken = o.label in have && !on
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TapRow(onClick = if (taken) null else ({
                        picked = when {
                            on -> picked - o.label
                            // full: swap out the oldest pick, so a change of mind is one tap
                            count != null && picked.size >= count -> picked.drop(1) + o.label
                            else -> picked + o.label
                        }
                    }), Modifier.weight(1f).then(if (taken) Modifier.alpha(0.55f) else Modifier)) {
                        T(if (on) "[x] " else "[ ] ", Style.Mono, dim = !on)
                        Column(Modifier.weight(1f)) {
                            T(o.label, Style.Body, maxLines = 1)
                            if (o.detail.isNotEmpty() || taken) T(if (taken) "have" else o.detail, Style.Small, dim = true, maxLines = 1)
                        }
                    }
                    readKind?.let { k ->
                        TapRow(onClick = { openEntry(k, o.label) }, Modifier.weight(0.2f)) {
                            T("›", Style.Heading, dim = true)
                        }
                    }
                }
            }
        }
    }
}
