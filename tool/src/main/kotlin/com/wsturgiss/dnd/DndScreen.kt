package com.wsturgiss.dnd

import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.wsturgiss.dnd.model.Edge
import com.wsturgiss.dnd.model.Kind
import com.wsturgiss.dnd.model.Store

/** What to roll when a screen asks for one: an expression and what it was for. */
data class RollRequest(val expr: String, val label: String, val edge: Edge = Edge.None)

/** Base for every screen here: makes sure the save file is loaded, and holds the common jumps. */
abstract class DndScreen<R>(sealedActivity: SealedLightActivity) : SimpleLightScreen<R>(sealedActivity) {

    init {
        Store.init(lightContext.filesDir)
    }

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
