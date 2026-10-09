package com.wsturgiss.dnd.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.thelightphone.sdk.ui.LightThemeTokens
import com.wsturgiss.dnd.R

/**
 * Three faces, each with one job: MEK Sans (MEK.txt, CC0) for text, IBM VGA 8x16
 * (VileR's Oldschool PC Font Pack, CC BY-SA 4.0) for numbers and columns, and
 * Jacquard 24 (Sarah Cadigan-Fried, OFL) for headings and names. MEK Dings is ornament.
 *
 * They are pixel fonts, so they are sized in *font pixels*, not sp: a size of 4
 * means each font pixel is drawn 4 screen pixels wide. Whole numbers keep every
 * stroke on the screen's pixel grid, which is what makes them look sharp.
 *
 * Only IBM VGA has box-drawing characters, so rules and boxes are drawn in Compose.
 */
object Fonts {
    val Sans = FontFamily(Font(R.font.mek_sans))
    val Mono = FontFamily(Font(R.font.ibm_vga))
    val Dings = FontFamily(Font(R.font.mek_dings))
    val Jacquard = FontFamily(Font(R.font.jacquard24))

    // Font pixels per em, read off each font's outlines (1000 units per em).
    const val SANS_PX_PER_EM = 15f
    // IBM VGA 8x16: 100-unit pixels, 1600 units per em.
    const val MONO_PX_PER_EM = 16f
    const val DINGS_PX_PER_EM = 12f
    // Jacquard 24 is drawn on a 30-unit grid at 1290 units per em.
    const val JACQUARD_PX_PER_EM = 43f
}

/** Font-pixel scale of [Style.Total]; the roller uses it to even out the box around a die. */
const val TOTAL_SCALE = 10

enum class Style { Ornament, Banner, Total, Heading, Body, Small, Mono, MonoSmall, MonoBig }

/** Screen pixels → sp. Goes through LocalDensity so it holds at any font scale. */
@Composable
private fun pxToSp(px: Float): TextUnit = with(LocalDensity.current) { px.toSp() }

@Composable
fun textStyle(style: Style): TextStyle {
    fun s(family: FontFamily, perEm: Float, scale: Int, lineHeight: Float) = Triple(family, perEm * scale, lineHeight)
    val (family, emPx, line) = when (style) {
        // Blackletter for headings and names. Its digits are old-style, so never for numbers.
        Style.Banner -> s(Fonts.Jacquard, Fonts.JACQUARD_PX_PER_EM, 2, 1.1f)
        // A big number read at a glance: roll totals, the number pad.
        Style.Total -> s(Fonts.Mono, Fonts.MONO_PX_PER_EM, TOTAL_SCALE, 1.0f)
        Style.Heading -> s(Fonts.Sans, Fonts.SANS_PX_PER_EM, 5, 1.2f)
        Style.Body -> s(Fonts.Sans, Fonts.SANS_PX_PER_EM, 4, 1.4f)
        Style.Small -> s(Fonts.Sans, Fonts.SANS_PX_PER_EM, 3, 1.4f)
        Style.Mono -> s(Fonts.Mono, Fonts.MONO_PX_PER_EM, 4, 1.3f)
        Style.MonoSmall -> s(Fonts.Mono, Fonts.MONO_PX_PER_EM, 3, 1.3f)
        Style.MonoBig -> s(Fonts.Mono, Fonts.MONO_PX_PER_EM, 5, 1.2f)
        Style.Ornament -> s(Fonts.Dings, Fonts.DINGS_PX_PER_EM, 5, 1.0f)
    }
    return TextStyle(
        fontFamily = family,
        fontSize = pxToSp(emPx),
        lineHeight = pxToSp(emPx * line),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

@Composable
fun T(
    text: String,
    style: Style = Style.Body,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    color: Color? = null,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) = T(AnnotatedString(text), style, modifier, dim, color, align, maxLines)

@Composable
fun T(
    text: AnnotatedString,
    style: Style = Style.Body,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    color: Color? = null,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = LightThemeTokens.colors
    BasicText(
        text = text,
        modifier = modifier,
        style = textStyle(style).copy(
            color = color ?: if (dim) colors.contentSecondary else colors.content,
            textAlign = align ?: TextAlign.Unspecified,
        ),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}
