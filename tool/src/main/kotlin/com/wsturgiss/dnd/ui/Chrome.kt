package com.wsturgiss.dnd.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.rememberLightTypography

/** Horizontal page margin, in LightOS grid units (the screen is 27 wide). */
const val GUTTER = 1.25f

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
 * Every screen: the top bar (back on the left, the title, the page's one action on the right),
 * then the content. Back is always in the same corner. Any other action lives in the content,
 * so most pages have no bottom bar; [bottom] is for the one that earns it (Roll's die),
 * centred under the thumb.
 */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)?,
    action: LightBarButton? = null,
    bottom: LightBarButton? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()
    LightTheme(colors = themeColors, typography = mekTypography()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            LightTopBar(
                leftButton = onBack?.let { LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = it) },
                center = LightTopBarCenter.Text(title),
                rightButton = action,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = content,
            )
            if (bottom != null) LightBottomBar(items = listOf(bottom))
        }
    }
}

/** A page whose content scrolls, with the usual side margins. */
@Composable
fun ScrollPage(
    scroll: ScrollState,
    title: String,
    onBack: (() -> Unit)?,
    action: LightBarButton? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Page(title, onBack, action) {
    LightScrollView(modifier = Modifier.fillMaxSize(), scrollState = scroll) {
        Column(modifier = Modifier.padding(horizontal = GUTTER.gridUnitsAsDp())) {
            content()
            Spacer(Modifier.height(1.5f.gridUnitsAsDp()))
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

/**
 * A label on the left, a value on the right: the shape of most sheet rows. A long value (a list
 * of feats) stops at [VALUE_MAX_WIDTH], two lines and then "…", so the label always shows.
 */
@Composable
fun KeyValueRow(key: String, value: String, onClick: (() -> Unit)?, keyStyle: Style = Style.Body, valueStyle: Style = Style.Mono) {
    TapRow(onClick) {
        T(key, keyStyle, Modifier.weight(1f), maxLines = 1)
        T(
            value,
            valueStyle,
            Modifier.padding(start = 0.5f.gridUnitsAsDp()).widthIn(max = VALUE_MAX_WIDTH.gridUnitsAsDp()),
            align = TextAlign.End,
            maxLines = 2,
        )
    }
}

/** The widest a [KeyValueRow]'s value gets, in grid units: a bit over half the 24.5 between the margins. */
private const val VALUE_MAX_WIDTH = 14f

/**
 * What the pad refused, under the row it's about, scrolled into view: back from the pad the page
 * is where it was, and a row near the bottom would leave it under the edge. Null shows nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Refusal(text: String?) {
    if (text == null) return
    val bring = remember { BringIntoViewRequester() }
    T(text, Style.Body, modifier = Modifier.bringIntoViewRequester(bring).padding(vertical = 0.3f.gridUnitsAsDp()))
    LaunchedEffect(text) {
        withFrameNanos { }
        bring.bringIntoView()
    }
}

/** A section heading in Jacquard, with a rule under it. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 1.25f.gridUnitsAsDp(), bottom = 0.35f.gridUnitsAsDp())) {
        T(title, Style.Banner)
        Rule(Modifier.padding(top = 0.3f.gridUnitsAsDp()))
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
