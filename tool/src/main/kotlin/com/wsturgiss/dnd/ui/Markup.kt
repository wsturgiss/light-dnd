package com.wsturgiss.dnd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * Renders the line markup `data/build_srd.py` writes:
 *
 * - `# Heading` → a section heading
 * - `@ text` → a monospaced line
 * - `**Bold.**` → bold run, as the SRD writes trait and action names
 *
 * Dice in the text (`8d6`, `5 (1d6 + 2)`) and attack bonuses (`Attack Roll: +4`)
 * are underlined, and tapping one rolls it — [onRoll] gets the expression.
 */
@Composable
fun Markup(text: String, onRoll: ((expr: String) -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier) {
        for (line in text.lines()) {
            when {
                line.isBlank() -> Spacer(Modifier.height(0.6f.gridUnitsAsDp()))
                line.startsWith("# ") -> T(
                    line.removePrefix("# ").replaceFirstChar { it.uppercase() },
                    Style.Banner,
                    Modifier.padding(top = 0.6f.gridUnitsAsDp(), bottom = 0.4f.gridUnitsAsDp()),
                )
                line.startsWith("@ ") -> T(rich(line.removePrefix("@ "), onRoll), Style.MonoSmall)
                // Rules text runs long: the smaller size fits about twice the words per screen.
                else -> T(rich(line, onRoll), Style.Small, Modifier.padding(bottom = 0.25f.gridUnitsAsDp()))
            }
        }
    }
}

private val BOLD = Regex("""\*\*(.+?)\*\*""")

/** Dice like `2d6`, `1d6 + 2`, `d20`; and an attack bonus like `Attack Roll: +4`. */
private val ROLLABLE = Regex("""(\d*d\d+(?:\s*[+−-]\s*\d+(?!d))?)|(Attack Roll: ([+−-]\d+))""")

private fun rich(line: String, onRoll: ((String) -> Unit)?): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (b in BOLD.findAll(line)) {
        appendRollable(line.substring(last, b.range.first), onRoll)
        withStyle(SpanStyle(textDecoration = null)) {
            // MEK Sans has one weight; set bold runs in capitals instead of faking a bold.
            append(b.groupValues[1].uppercase())
        }
        last = b.range.last + 1
    }
    appendRollable(line.substring(last), onRoll)
}

private fun AnnotatedString.Builder.appendRollable(text: String, onRoll: ((String) -> Unit)?) {
    if (onRoll == null) {
        append(text)
        return
    }
    var last = 0
    for (m in ROLLABLE.findAll(text)) {
        append(text.substring(last, m.range.first))
        val expr = if (m.groupValues[1].isNotEmpty()) m.groupValues[1] else "d20${m.groupValues[3]}"
        withLink(
            LinkAnnotation.Clickable(
                tag = expr,
                styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
                linkInteractionListener = { onRoll(expr) },
            ),
        ) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
