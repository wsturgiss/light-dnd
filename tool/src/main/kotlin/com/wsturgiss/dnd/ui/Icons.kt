package com.wsturgiss.dnd.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * A d20 seen face-on, as a bottom-bar icon: the hexagon outline, the front face, and the edges
 * between them. Drawn on LightOS's 40-unit icon grid with strokes about as heavy as its own
 * icons, in the theme's content colour (the bar doesn't tint custom icons).
 */
@Composable
fun d20Icon(): Painter {
    val color = LightThemeTokens.colors.content
    val vector = remember(color) {
        val hex = listOf(20f to 3f, 34.7f to 11.5f, 34.7f to 28.5f, 20f to 37f, 5.3f to 28.5f, 5.3f to 11.5f)
        val face = listOf(20f to 12f, 11f to 26f, 29f to 26f)
        // each corner of the front face to the outline corners it touches
        val edges = listOf(0 to 0, 0 to 5, 0 to 1, 1 to 5, 1 to 4, 1 to 3, 2 to 1, 2 to 2, 2 to 3)
        ImageVector.Builder("d20", 40.dp, 40.dp, 40f, 40f).path(
            stroke = SolidColor(color),
            strokeLineWidth = 2.4f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            listOf(hex, face).forEach { loop ->
                moveTo(loop[0].first, loop[0].second)
                loop.drop(1).forEach { (x, y) -> lineTo(x, y) }
                close()
            }
            edges.forEach { (f, h) ->
                moveTo(face[f].first, face[f].second)
                lineTo(hex[h].first, hex[h].second)
            }
        }.build()
    }
    return rememberVectorPainter(vector)
}
