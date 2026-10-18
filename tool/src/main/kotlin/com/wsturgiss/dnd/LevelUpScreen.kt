package com.wsturgiss.dnd

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.Ability
import com.wsturgiss.dnd.model.DiceExpr
import com.wsturgiss.dnd.model.Skill
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.Training
import com.wsturgiss.dnd.model.Character
import com.wsturgiss.dnd.model.Feat
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Srd
import com.wsturgiss.dnd.model.fightingStyle
import com.wsturgiss.dnd.model.plainName
import com.wsturgiss.dnd.model.levelPlan
import com.wsturgiss.dnd.model.levelUp
import com.wsturgiss.dnd.model.signed
import com.wsturgiss.dnd.model.speciesInfo
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow

/**
 * One level up, as a page: hit points (average or a roll), a score increase where the class
 * gets one, Expertise where it comes, and the new spell slots. Nothing changes until Level up.
 */
class LevelUpScreen(sealedActivity: SealedLightActivity) : DndScreen<Unit>(sealedActivity) {

    /** The hit die as rolled, once rolled. Until then the average is used. */
    private var rolledHit by mutableStateOf<Int?>(null)
    private var useRoll by mutableStateOf(false)
    private var raise by mutableStateOf(mapOf<Ability, Int>())
    private var expertise by mutableStateOf(setOf<Skill>())
    /** Null takes the SRD's subclass; set only when you type another. */
    private var subclass by mutableStateOf<String?>(null)
    private var fightingStyle by mutableStateOf<String?>(null)
    private var newSpells by mutableStateOf(setOf<String>())
    private var newCantrips by mutableStateOf(setOf<String>())

    @Composable
    override fun Content() {
        val save by Store.state.collectAsState()
        val c = save.character
        val plan = c.levelPlan()

        if (plan == null) {
            ScrollPage(scroll, title = "Level up", onBack = { goBack(null) }) {
                T("Level 20 is as high as it goes.", Style.Heading, Modifier.padding(top = 2f.gridUnitsAsDp()))
            }
            return
        }

        val die = plan.cls?.hitDie ?: hitDie(c.className)
        val average = die / 2 + 1
        val hit = if (useRoll) rolledHit ?: average else average
        val boonCap = if (plan.epicBoon) 30 else 20
        val budget = when {
            plan.asi -> 2
            plan.epicBoon -> 1
            else -> 0
        }
        val made = Srd.creation(lightContext)
        val data = made.classes[c.className]
        val was = data?.levels?.getOrNull(plan.from - 1)
        val now = data?.levels?.getOrNull(plan.to - 1)
        val subclassName = if (plan.to == 3 && c.subclass.isEmpty()) subclass ?: data?.subclass.orEmpty() else c.subclass
        val styleDue = now?.fightingStyle() == true && c.feats.none { it.note == "Fighting Style" }
        val moreCantrips = ((now?.cantrips ?: 0) - (was?.cantrips ?: 0)).coerceAtLeast(0)
        val moreSpells = ((now?.prepared ?: 0) - (was?.prepared ?: 0)).coerceAtLeast(0)
        fun finish(ch: Character) = ch.copy(
            subclass = subclassName,
            feats = ch.feats + listOfNotNull(fightingStyle?.takeIf { styleDue }?.let { Feat(it, "Fighting Style") }),
            spells = (ch.spells + newCantrips + newSpells).distinct(),
        )
        val after = c.levelUp(hit, raise, expertise, boonCap)
        val hpGain = after.maxHp - c.maxHp

        ScrollPage(
            scroll,
            title = "Level up",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.ACCEPT, contentDescription = "Level up", onClick = {
                Store.edit { finish(it.levelUp(hit, raise, expertise, boonCap)) }
                goBack(null)
            }),
        ) {
            T(c.name, Style.Banner, Modifier.padding(top = 0.5f.gridUnitsAsDp()))
            T("${c.className}, level ${plan.from} to ${plan.to}", Style.Heading)
            if (plan.newProficiency != plan.oldProficiency) {
                T("Proficiency bonus ${signed(plan.oldProficiency)} to ${signed(plan.newProficiency)}", Style.Small)
            }

            if (now != null) {
                val withSub = subclassName.isNotEmpty() && subclassName == data.subclass
                val gained = now.features + if (withSub) now.subclassFeatures else emptyList()
                if (gained.isNotEmpty()) {
                    Section("New at level ${plan.to}")
                    gained.forEach { name ->
                        TapRow(onClick = { openEntry(Kind.Features, name) }) {
                            T(plainName(name), Style.Body, Modifier.weight(1f), maxLines = 1)
                            T("›", Style.Heading, dim = true)
                        }
                    }
                }
                if (plan.to == 3 && c.subclass.isEmpty()) {
                    TapRow(onClick = {
                        val srd = data.subclass.orEmpty()
                        navigateTo(
                            screenFactory = {
                                PickScreen(
                                    it, "Subclass", listOf(Option(srd, "in the SRD"), Option("Something else", "type its name")),
                                    if (subclassName == srd) 0 else 1, "The SRD has one subclass per class; others are in the book.",
                                    Kind.Features, readName = { i -> if (i == 0) srd else subclassName },
                                )
                            },
                            resultCallback = { i -> if (i == 0) subclass = null else askText("Subclass", "") { n -> subclass = n } },
                        )
                    }) {
                        T("Subclass", Style.Body, Modifier.weight(1f))
                        T(subclassName.ifEmpty { "choose" }, Style.Body)
                    }
                }
                if (styleDue) {
                    TapRow(onClick = {
                        val styles = made.fightingStyles
                        navigateTo(
                            screenFactory = { PickScreen(it, "Fighting style", styles.map { n -> Option(n) }, styles.indexOf(fightingStyle).takeIf { i -> i >= 0 }, null, Kind.Feats) },
                            resultCallback = { i -> fightingStyle = styles[i] },
                        )
                    }) {
                        T("Fighting style", Style.Body, Modifier.weight(1f))
                        T(fightingStyle ?: "choose", Style.Body)
                    }
                }
                if (moreCantrips > 0) {
                    SpellRow("Cantrips: learn $moreCantrips", moreCantrips, 0..0, newCantrips, c) { newCantrips = it }
                }
                if (moreSpells > 0) {
                    val top = com.wsturgiss.dnd.model.slotsFor(plan.cls, plan.to).size
                    SpellRow("Spells: prepare $moreSpells more", moreSpells, 1..top, newSpells, c) { newSpells = it }
                }
            }

            Section("Hit points")
            TapRow(onClick = { useRoll = false }) {
                T(if (!useRoll) "> " else "  ", Style.Mono)
                T("Take the average", Style.Body, Modifier.weight(1f))
                T("$average", Style.Mono)
            }
            TapRow(onClick = {
                // one roll a level: once it's rolled, this row just picks it again
                if (rolledHit == null) {
                    val roll = DiceExpr.parse("d$die")!!.roll()
                    Store.record("Hit points, level ${plan.to}", roll)
                    rolledHit = roll.total
                }
                useRoll = true
            }) {
                T(if (useRoll) "> " else "  ", Style.Mono)
                T(if (rolledHit == null) "Roll d$die" else "Rolled d$die", Style.Body, Modifier.weight(1f))
                T(rolledHit?.toString() ?: "?", Style.Mono, dim = rolledHit == null)
            }
            val con = after.mod(Ability.CON)
            val dwarf = speciesInfo(c.species)?.hpPerLevel ?: 0
            T(
                "$hit ${signed(con)} CON" + (if (dwarf > 0) " +$dwarf ${c.species}" else "") +
                    (if (con != c.mod(Ability.CON)) " +${(con - c.mod(Ability.CON)) * plan.from} for earlier levels" else "") +
                    "  =  +$hpGain HP, max ${after.maxHp}",
                Style.Small,
                dim = true,
                modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()),
            )

            if (budget > 0) {
                Section(if (plan.epicBoon) "Epic Boon" else "Ability scores")
                T(
                    if (plan.epicBoon) "+1 to one score, up to 30. Taking a different boon? Leave these and note it."
                    else "+2 to one score or +1 to two, up to 20. Taking a feat instead? Leave these and note it.",
                    Style.Small,
                    dim = true,
                )
                val spent = raise.values.sum()
                T("Points left: ${budget - spent}", Style.Mono, modifier = Modifier.padding(vertical = 0.3f.gridUnitsAsDp()))
                Ability.entries.forEach { a ->
                    val now = c.score(a)
                    val r = raise[a] ?: 0
                    TapRow(onClick = {
                        // each tap adds one, up to the budget and the cap; past either it goes back to none
                        val next = r + 1
                        raise = if (spent - r + next <= budget && now + next <= boonCap) raise + (a to next) else raise - a
                    }) {
                        T(a.short + "  ", Style.Mono)
                        T(a.long, Style.Body, Modifier.weight(1f))
                        T(if (r > 0) "$now → ${now + r}" else "$now", Style.Mono, dim = r == 0)
                    }
                }
            }

            if (plan.expertise > 0) {
                Section("Expertise")
                val can = Skill.entries.filter { c.training(it) == Training.Proficient }
                TapRow(onClick = {
                    navigateTo(
                        screenFactory = { SkillPickScreen(it, "Expertise", plan.expertise, can, expertise) },
                        resultCallback = { set -> expertise = set },
                    )
                }) {
                    T("Choose ${plan.expertise}", Style.Body, Modifier.weight(1f))
                    T("${expertise.size}/${plan.expertise}", Style.Mono)
                }
                if (expertise.isNotEmpty()) T(expertise.sortedBy { it.ordinal }.joinToString(", ") { it.label }, Style.Small)
            }

            if (plan.newSlots != plan.oldSlots) {
                Section("Spell slots")
                plan.newSlots.indices.filter { plan.newSlots[it] > 0 || plan.oldSlots.getOrElse(it) { 0 } > 0 }.forEach { i ->
                    val was = plan.oldSlots.getOrElse(i) { 0 }
                    val now = plan.newSlots[i]
                    TapRow(onClick = null) {
                        T(ordinal(i + 1), Style.Body, Modifier.weight(1f))
                        T(if (was == now) "$now" else "$was → $now", Style.Mono, dim = was == now)
                    }
                }
            }

            T(
                "New class features: see the ${c.className} table in the book for level ${plan.to}.",
                Style.Small,
                dim = true,
                modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
            )
        }
    }

    /** A row that opens the class's spells of [levels] to pick [count] more; ones already known show as had. */
    @Composable
    private fun SpellRow(label: String, count: Int, levels: IntRange, picked: Set<String>, c: Character, onPick: (Set<String>) -> Unit) {
        TapRow(onClick = {
            val options = Srd.load(lightContext, Kind.Spells)
                .filter { c.className in it.classes && (it.level ?: 0) in levels }
                .sortedWith(compareBy({ it.level }, { it.name }))
                .map { Option(it.name, it.meta) }
            navigateTo(
                screenFactory = { MultiPickScreen(it, label.substringBefore(":"), count, options, picked, Kind.Spells, have = c.spells.toSet()) },
                resultCallback = onPick,
            )
        }) {
            T(label, Style.Body, Modifier.weight(1f))
            T("${picked.size}/$count", Style.Mono)
        }
    }
}
