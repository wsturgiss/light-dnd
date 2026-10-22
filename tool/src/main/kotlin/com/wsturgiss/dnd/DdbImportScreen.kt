package com.wsturgiss.dnd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.wsturgiss.dnd.model.Ability
import com.wsturgiss.dnd.model.Character
import com.wsturgiss.dnd.model.DdbImport
import com.wsturgiss.dnd.model.Store
import com.wsturgiss.dnd.model.signed
import com.wsturgiss.dnd.ui.ScrollPage
import com.wsturgiss.dnd.ui.Section
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T
import com.wsturgiss.dnd.ui.TapRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A public character from D&D Beyond, by its ID or its share link. Returns true once imported. */
class DdbImportScreen(sealedActivity: SealedLightActivity) : DndScreen<Boolean>(sealedActivity) {

    private var error by mutableStateOf<String?>(null)

    private fun preview(id: Long) {
        error = null
        navigateTo(screenFactory = { DdbPreviewScreen(it, id) }, resultCallback = { done -> if (done) goBack(true) })
    }

    @Composable
    override fun Content() {
        ScrollPage(scroll, title = "D&D Beyond", onBack = { goBack(null) }) {
            T("The character must be public on D&D Beyond. You'll see it before anything is saved.", Style.Small, dim = true)
            TapRow(onClick = {
                askNumber(NumberRequest("Character ID", "The number in its link: dndbeyond.com/characters/141442128", maxDigits = 12)) { r ->
                    DdbImport.parseId(r.long.toString())?.let(::preview) ?: run { error = "That isn't a character ID." }
                }
            }, Modifier.padding(top = 0.6f.gridUnitsAsDp())) {
                T("Type the ID", Style.Body, Modifier.weight(1f))
                T("›", Style.Heading, dim = true)
            }
            TapRow(onClick = {
                askText("Link or ID") { s -> DdbImport.parseId(s)?.let(::preview) ?: run { error = "No character ID in that." } }
            }) {
                T("Type or paste the link", Style.Body, Modifier.weight(1f))
                T("›", Style.Heading, dim = true)
            }
            error?.let { T(it, Style.Body, modifier = Modifier.padding(top = 0.6f.gridUnitsAsDp())) }
        }
    }
}

/** Fetches the character, shows what it'll be, and adds it or puts it in place of the current one. */
class DdbPreviewScreen(sealedActivity: SealedLightActivity, private val id: Long) : DndScreen<Boolean>(sealedActivity) {

    private var made by mutableStateOf<Character?>(null)
    private var error by mutableStateOf<String?>(null)
    private var started = false

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            if (started) return@LaunchedEffect
            started = true
            try {
                made = fetch(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (e as? DdbImport.Failure)?.message ?: "Couldn't import that character."
            }
        }
        val c = made
        ScrollPage(
            scroll,
            title = "Import",
            onBack = { goBack(null) },
            action = LightBarButton.LightIcon(LightIcons.ACCEPT, contentDescription = "Add", onClick = if (c != null) ({
                Store.addCharacter(c)
                goBack(true)
            }) else null),
        ) {
            when {
                error != null -> T(error!!, Style.Body, modifier = Modifier.padding(top = 0.6f.gridUnitsAsDp()))
                c == null -> T("Fetching $id…", Style.Body, dim = true, modifier = Modifier.padding(top = 0.6f.gridUnitsAsDp()))
                else -> Preview(c)
            }
        }
    }

    @Composable
    private fun Preview(c: Character) {
        T(c.name, Style.Banner, maxLines = 2)
        T(
            listOf("Level ${c.level}", c.species, c.className + (c.subclass.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""), c.background)
                .filter { it.isNotBlank() }.joinToString(" · "),
            Style.Small,
            dim = true,
        )
        Row(Modifier.fillMaxWidth().padding(top = 0.6f.gridUnitsAsDp()), horizontalArrangement = Arrangement.SpaceBetween) {
            Ability.entries.forEach { a ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T("${c.score(a)}", Style.Mono)
                    T(a.short, Style.Small, dim = true)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 0.6f.gridUnitsAsDp()), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("HP" to "${c.hp}/${c.maxHp}", "AC" to "${c.ac}", "INIT" to signed(c.initiative), "SPEED" to "${c.speed}").forEach { (k, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(v, Style.MonoBig)
                    T(k, Style.Small, dim = true)
                }
            }
        }
        c.hpBreakdown?.let { T("Max HP $it, as D&D Beyond has it.", Style.Small) }
        T("${c.items.size} things in the bag, wearing ${listOfNotNull(c.wearing, "Shield".takeIf { c.shield }).joinToString(" and ").ifEmpty { "no armor" }}.", Style.Small)
        if (c.notes.isNotBlank()) T("Anything the sheet can't hold goes in its notes.", Style.Small, dim = true)

        Section("Save")
        TapRow(onClick = {
            Store.addCharacter(c)
            goBack(true)
        }) {
            T("Add as a new character", Style.Body, Modifier.weight(1f))
            T("›", Style.Heading, dim = true)
        }
        TapRow(onClick = {
            Store.replaceActive(c)
            goBack(true)
        }) {
            T("Replace ${Store.character.name}", Style.Body, Modifier.weight(1f), maxLines = 1)
            T("›", Style.Heading, dim = true)
        }
        T("Replacing keeps a copy of the save beside it first.", Style.Small, dim = true)
    }

    private suspend fun fetch(id: Long): Character = withContext(Dispatchers.IO) {
        val conn = try {
            URL(DdbImport.url(id)).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw DdbImport.Failure("Couldn't reach D&D Beyond. Is the phone online?")
        }
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            when (val code = conn.responseCode) {
                200 -> DdbImport.parse(conn.inputStream.bufferedReader().use { it.readText() })
                else -> throw DdbImport.Failure(DdbImport.httpFailure(code))
            }
        } catch (e: IOException) {
            throw DdbImport.Failure("Couldn't reach D&D Beyond. Is the phone online?")
        } finally {
            conn.disconnect()
        }
    }
}
