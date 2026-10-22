package com.wsturgiss.dnd

import androidx.compose.foundation.ScrollState
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.wsturgiss.dnd.model.Edge
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Store

/** What to roll when a screen asks for one: an expression and what it was for. */
data class RollRequest(
    val expr: String,
    val label: String,
    val edge: Edge = Edge.None,
    /** What imposes disadvantage on this roll ("Chain Mail"), on top of any ADV/DIS tapped. */
    val disadvantageFrom: List<String> = emptyList(),
    /** What gives it advantage ("Remarkable Athlete"); against [disadvantageFrom] they cancel. */
    val advantageFrom: List<String> = emptyList(),
)

/** Base for every screen here: makes sure the save file is loaded, and holds the common jumps. */
abstract class DndScreen<R>(sealedActivity: SealedLightActivity) : SimpleLightScreen<R>(sealedActivity) {

    init {
        Store.init(lightContext.filesDir)
    }

    /**
     * The page's scroll position. The SDK drops a screen's Compose state while another screen is
     * on top of it, so this lives on the screen: back from a roll, the page is where you left it.
     */
    val scroll = ScrollState(0)

    /** Opens the dice screen, which rolls [request] straight away and logs it. */
    fun roll(request: RollRequest) = navigateTo(screenFactory = { RollScreen(it, request) })

    fun roll(expr: String, label: String) = roll(RollRequest(expr, label))

    fun openEntry(kind: Kind, name: String) = navigateTo(screenFactory = { EntryScreen(it, kind, name) })

    fun askNumber(request: NumberRequest, onResult: (NumberResult) -> Unit) =
        navigateTo(screenFactory = { NumberPadScreen(it, request) }, resultCallback = { r -> r?.let(onResult) })

    fun askText(title: String, initial: String = "", onResult: (String) -> Unit) =
        navigateTo(
            screenFactory = { TextEntryScreen(it, title, initial) },
            resultCallback = { r -> r?.trim()?.takeIf { it.isNotEmpty() }?.let(onResult) },
        )
}
