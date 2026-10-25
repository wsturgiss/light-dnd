package com.wsturgiss.dnd.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.rememberLightTypography

/** Horizontal page margin, in LightOS grid units (the screen is 27 wide): clear of the frame. */
const val GUTTER = 1.6f

/** The roller's narrower margin: its two rows of six keys need the width. */
const val PAD_GUTTER = 1.25f

/** LightOS's own type scale, with MEK Sans in place of Akkurat, so the SDK's bars match. */
@Composable
fun mekTypography() = rememberLightTypography().let { t ->
    fun f(s: androidx.compose.ui.text.TextStyle) = s.copy(fontFamily = Fonts.Sans, fontWeight = null, letterSpacing = s.letterSpacing)
    t.copy(
        title = f(t.title), subtitle = f(t.subtitle), heading = f(t.heading), subheading = f(t.subheading),
        copy = f(t.copy), button = f(t.button), paragraph = f(t.paragraph), paragraphWide = f(t.paragraphWide),
        detail = f(t.detail), fine = f(t.fine), superfine = f(t.superfine), micro = f(t.micro),
    )
}

/**
 * Every screen, as a folio leaf: the frame, then the top bar (back on the left, the title between
 * two ♦, the page's one action on the right) over a double rule, then the content. Back is always
 * in the same corner. Any other action lives in the content, so most pages have no bottom bar;
 * [bottom] is for the one that earns it (Roll's die), centred under the thumb, with the page's
 * [folio] number beside it.
 */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)?,
    action: LightBarButton? = null,
    bottom: LightBarButton? = null,
    folio: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()
    LightTheme(colors = themeColors, typography = mekTypography()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .ruledAcross(atTop = false)
                        .padding(top = Folio.INNER.gridUnitsAsDp(), bottom = 0.1f.gridUnitsAsDp())
                        .padding(horizontal = Folio.INNER.gridUnitsAsDp()),
                    contentAlignment = Alignment.Center,
                ) {
                    LightTopBar(
                        leftButton = onBack?.let { LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = it) },
                        center = null,
                        rightButton = action,
                    )
                    FolioTitle(title, Modifier.widthIn(max = TITLE_MAX.gridUnitsAsDp()))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // the content stops at the frame's hairline rather than running under it
                        .padding(bottom = if (bottom == null) Folio.INNER.gridUnitsAsDp() + 1.dp else 0.dp)
                        .clipToBounds(),
                    content = content,
                )
                if (bottom != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .ruledAcross(atTop = true)
                            .padding(start = Folio.INNER.gridUnitsAsDp(), end = Folio.INNER.gridUnitsAsDp(), bottom = Folio.INNER.gridUnitsAsDp()),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        LightBottomBar(items = listOf(bottom))
                        if (folio != null) T("fol. $folio", Style.MonoSmall, dim = true, modifier = Modifier.padding(start = 1f.gridUnitsAsDp()))
                    }
                }
            }
            FolioFrame()
        }
    }
}

/** The widest the title gets, in grid units, so it never runs under the bar's buttons. */
private const val TITLE_MAX = 17f

/**
 * A page whose content scrolls, with the usual side margins. With a [folio] number, the page ends
 * in the folio's foot: a fleuron, the landscape, and the number.
 */
@Composable
fun ScrollPage(
    scroll: ScrollState,
    title: String,
    onBack: (() -> Unit)?,
    action: LightBarButton? = null,
    folio: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Page(title, onBack, action) {
    LightScrollView(modifier = Modifier.fillMaxSize(), scrollState = scroll) {
        Column(modifier = Modifier.padding(horizontal = GUTTER.gridUnitsAsDp())) {
            content()
            if (folio != null) FolioFoot(folio, Modifier.padding(top = 1f.gridUnitsAsDp()))
            else Spacer(Modifier.height(1.5f.gridUnitsAsDp()))
        }
    }
}

/** A full-width tap target with comfortable height for a thumb. */
@Composable
fun TapRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(vertical = 0.45f.gridUnitsAsDp()),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 2.4f.gridUnitsAsDp())
            .then(if (onClick != null) Modifier.lightClickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A label on the left, a value on the right: the shape of most sheet rows. */
@Composable
fun KeyValueRow(key: String, value: String, onClick: (() -> Unit)?, keyStyle: Style = Style.Body, valueStyle: Style = Style.Mono) {
    TapRow(onClick) {
        T(key, keyStyle, Modifier.weight(1f), maxLines = 1)
        T(value, valueStyle)
    }
}

/** A section heading in Jacquard, opening on an illuminated capital, over a fleuron. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 1.25f.gridUnitsAsDp(), bottom = 0.35f.gridUnitsAsDp())) {
        Illuminated(title, maxLines = 2)
        Fleuron(Modifier.padding(top = 0.3f.gridUnitsAsDp()))
    }
}

@Composable
fun Rule(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LightThemeTokens.colors.contentSecondary),
    )
}

/** MEK Dings' digits are a row of huts, trees and towers. */
const val LANDSCAPE = "1 2 0 4 3 7 5 8 2 6 1 9 0 3"
