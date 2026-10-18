package com.wsturgiss.dnd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.wsturgiss.dnd.ui.GUTTER
import com.wsturgiss.dnd.ui.Page
import com.wsturgiss.dnd.ui.Style
import com.wsturgiss.dnd.ui.T

/**
 * A number to ask for. [actions] are what to do with it, a row of keys over the pad,
 * so one pad can do Damage / Heal / Temp, or Add / Spend / Set.
 */
data class NumberRequest(
    val title: String,
    val context: String = "",
    val initial: Int? = null,
    val actions: List<String> = listOf("Set"),
    val allowNegative: Boolean = false,
)

data class NumberResult(val action: Int, val value: Int)

class NumberPadScreen(
    sealedActivity: SealedLightActivity,
    private val request: NumberRequest,
) : DndScreen<NumberResult>(sealedActivity) {

    private var entry by mutableStateOf(request.initial?.toString() ?: "")

    private fun press(key: String) {
        entry = when (key) {
            "<" -> entry.dropLast(1)
            "-" -> if (entry.startsWith("-")) entry.drop(1) else "-$entry"
            else -> if (entry == "0" || entry == "-0") entry.dropLast(1) + key else (entry + key).take(5)
        }
    }

    @Composable
    override fun Content() {
        val value = entry.toIntOrNull()
        Page(title = request.title, onBack = { goBack(null) }) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = GUTTER.gridUnitsAsDp()),
            ) {
                if (request.context.isNotEmpty()) T(request.context, Style.Small, dim = true)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    T(entry.ifEmpty { "_" }, Style.Total, dim = entry.isEmpty())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.4f.gridUnitsAsDp())) {
                    request.actions.forEachIndexed { i, label ->
                        PadKey(label.uppercase(), Modifier.weight(1f), enabled = value != null) {
                            value?.let { goBack(NumberResult(i, it)) }
                        }
                    }
                }
                Box(Modifier.height(0.4f.gridUnitsAsDp()))
                val third = if (request.allowNegative) "-" else ""
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(third, "0", "<"))
                    .forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.4f.gridUnitsAsDp())) {
                            row.forEach { key -> PadKey(key, Modifier.weight(1f)) { press(key) } }
                        }
                    }
                Box(Modifier.height(0.6f.gridUnitsAsDp()))
            }
        }
    }
}

/** A key on a pad: a ruled box with a mono label. Empty label → a blank, untappable space. */
@Composable
fun PadKey(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** False shows the key dimmed and ignores taps. */
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    Box(
        modifier
            .padding(vertical = 0.2f.gridUnitsAsDp())
            .height(2.9f.gridUnitsAsDp())
            .then(
                if (label.isEmpty()) Modifier else Modifier
                    .border(1.dp, colors.contentSecondary)
                    .background(if (selected) colors.content else colors.background)
                    .then(if (enabled) Modifier.lightClickable(onClick = onClick) else Modifier),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            T(
                if (label == "<") "DEL" else label,
                Style.Mono,
                dim = !enabled,
                color = if (selected) colors.background else null,
                align = TextAlign.Center,
            )
        }
    }
}

/** The LP3 keyboard, for names and searches. */
class TextEntryScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String,
) : DndScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val keyboardOptions = rememberKeyboardOptions()
        val state = rememberTextFieldState(initial)
        val themeColors by LightThemeController.colors.collectAsState()
        LightTheme(colors = themeColors, typography = com.wsturgiss.dnd.ui.mekTypography()) {
            LightTextInputEditor(
                title = title,
                state = state,
                onSubmit = { goBack(it.toString()) },
                onBack = { goBack(null) },
                keyboardOptionsFlow = keyboardOptions,
                singleLine = true,
                initialCaps = true,
                submitLabel = "DONE",
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
