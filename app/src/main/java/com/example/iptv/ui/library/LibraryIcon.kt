package com.example.iptv.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

internal enum class LibrarySymbol { MOVIES, SERIES, CONTINUE, HISTORY, FOLDER, PLAY, WATCHED, UNWATCHED,
    HEART, SORT, FILTER, RETRY, PREVIOUS, NEXT, BEGINNING, NEXT_EPISODE }

/** Small app-owned vectors; decorative with text, while icon-only controls name their action. */
@Composable
internal fun LibraryIcon(symbol: LibrarySymbol, tint: Color, modifier: Modifier, filled: Boolean = false) {
    Canvas(modifier.testTag("library_icon_${symbol.name}")) {
        withTransform({ scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) }) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x, y), Offset(x2, y2), 1.7f, StrokeCap.Round)
            fun triangle(x: Float = 9f) = drawPath(Path().apply { moveTo(x, 6f); lineTo(x + 8, 12f); lineTo(x, 18f); close() }, tint)
            when (symbol) {
                LibrarySymbol.MOVIES -> {
                    drawRect(tint, Offset(3f, 8f), Size(18f, 12f), style = stroke)
                    drawPath(Path().apply { moveTo(3f, 8f); lineTo(2f, 4f); lineTo(20f, 2f); lineTo(21f, 6f); close() }, tint, style = stroke)
                    line(7f, 4f, 9f, 7f); line(13f, 3f, 15f, 6f)
                }
                LibrarySymbol.SERIES -> {
                    drawRect(tint, Offset(3f, 8f), Size(18f, 13f), style = stroke)
                    line(5f, 5f, 19f, 5f); line(7f, 2f, 17f, 2f)
                    drawPath(Path().apply { moveTo(10f, 11f); lineTo(16f, 15f); lineTo(10f, 18f); close() }, tint)
                }
                LibrarySymbol.CONTINUE -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    line(12f, 5f, 12f, 12f); line(12f, 12f, 17f, 15f)
                    drawPath(Path().apply { moveTo(5f, 14f); lineTo(10f, 17f); lineTo(5f, 20f); close() }, tint)
                }
                LibrarySymbol.HISTORY -> {
                    drawArc(tint, -135f, 300f, false, Offset(4f, 4f), Size(17f, 17f), style = stroke)
                    line(3f, 3f, 3f, 9f); line(3f, 9f, 9f, 9f); line(12f, 7f, 12f, 13f); line(12f, 13f, 16f, 15f)
                }
                LibrarySymbol.FOLDER -> {
                    drawPath(Path().apply { moveTo(2f, 6f); lineTo(9f, 6f); lineTo(11f, 9f); lineTo(22f, 9f); lineTo(22f, 20f); lineTo(2f, 20f); close() }, tint, style = stroke)
                    line(6f, 12f, 18f, 12f); line(6f, 16f, 18f, 16f)
                }
                LibrarySymbol.PLAY -> triangle()
                LibrarySymbol.WATCHED, LibrarySymbol.UNWATCHED -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    if (symbol == LibrarySymbol.WATCHED) { line(7f, 12f, 11f, 16f); line(11f, 16f, 18f, 8f) }
                    else line(8f, 12f, 16f, 12f)
                }
                LibrarySymbol.HEART -> drawPath(Path().apply {
                    moveTo(12f, 21f); cubicTo(9f, 18f, 2f, 13f, 2f, 7f)
                    cubicTo(2f, 1f, 9f, 1f, 12f, 6f); cubicTo(15f, 1f, 22f, 1f, 22f, 7f)
                    cubicTo(22f, 13f, 15f, 18f, 12f, 21f); close()
                }, tint, style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else stroke)
                LibrarySymbol.SORT -> { line(7f, 3f, 7f, 21f); line(3f, 7f, 7f, 3f); line(7f, 3f, 11f, 7f); line(17f, 3f, 17f, 21f); line(13f, 17f, 17f, 21f); line(17f, 21f, 21f, 17f) }
                LibrarySymbol.FILTER -> { line(3f, 5f, 21f, 5f); line(6f, 12f, 18f, 12f); line(10f, 19f, 14f, 19f) }
                LibrarySymbol.RETRY -> { drawArc(tint, -30f, 300f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke); line(21f, 2f, 21f, 9f); line(21f, 9f, 15f, 9f) }
                LibrarySymbol.PREVIOUS -> { line(16f, 4f, 7f, 12f); line(7f, 12f, 16f, 20f) }
                LibrarySymbol.NEXT -> { line(8f, 4f, 17f, 12f); line(17f, 12f, 8f, 20f) }
                LibrarySymbol.BEGINNING -> { line(5f, 5f, 5f, 19f); drawPath(Path().apply { moveTo(18f, 5f); lineTo(7f, 12f); lineTo(18f, 19f); close() }, tint) }
                LibrarySymbol.NEXT_EPISODE -> { triangle(5f); line(19f, 5f, 19f, 19f) }
            }
        }
    }
}
