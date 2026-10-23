package com.example.iptv.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** Outline assets in a shared 36-unit coordinate system. Parent controls name them. */
@Composable
internal fun BrowserIcon(kind: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 36f
        val stroke = Stroke(2.5f * unit)
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        when (kind) {
            0 -> {
                drawRoundRect(color, point(3f, 5f), Size(30f * unit, 22f * unit), CornerRadius(2f * unit), style = stroke)
                drawLine(color, point(18f, 27f), point(18f, 32f), stroke.width)
                drawLine(color, point(11f, 32f), point(25f, 32f), stroke.width)
            }
            1 -> {
                val path = Path().apply {
                    moveTo(18f * unit, 32f * unit)
                    cubicTo(10f * unit, 25f * unit, 2f * unit, 19f * unit, 3f * unit, 11f * unit)
                    cubicTo(4f * unit, 2f * unit, 14f * unit, 2f * unit, 18f * unit, 10f * unit)
                    cubicTo(22f * unit, 2f * unit, 32f * unit, 2f * unit, 33f * unit, 11f * unit)
                    cubicTo(34f * unit, 19f * unit, 26f * unit, 25f * unit, 18f * unit, 32f * unit)
                    close()
                }
                drawPath(path, color, style = stroke)
            }
            2 -> {
                drawCircle(color, 12f * unit, point(15f, 15f), style = stroke)
                drawLine(color, point(24f, 24f), point(34f, 34f), stroke.width)
            }
            3 -> {
                val path = Path()
                for (index in 0 until 32) {
                    val angle = index * Math.PI / 16 - Math.PI / 2
                    val radius = if (index % 4 in 1..2) 17f else 13f
                    val x = (18 + cos(angle) * radius).toFloat() * unit
                    val y = (18 + sin(angle) * radius).toFloat() * unit
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color, style = stroke)
                drawCircle(color, 6f * unit, point(18f, 18f), style = stroke)
            }
            5 -> {
                drawRoundRect(color, point(3f, 4f), Size(30f * unit, 28f * unit), CornerRadius(2f * unit), style = stroke)
                drawLine(color, point(10f, 4f), point(10f, 32f), stroke.width)
                drawLine(color, point(26f, 4f), point(26f, 32f), stroke.width)
                for (y in listOf(11f, 18f, 25f)) {
                    drawLine(color, point(3f, y), point(10f, y), stroke.width)
                    drawLine(color, point(26f, y), point(33f, y), stroke.width)
                }
            }
            else -> {
                val path = Path().apply {
                    moveTo(3f * unit, 9f * unit); lineTo(14f * unit, 9f * unit)
                    lineTo(18f * unit, 13f * unit); lineTo(33f * unit, 13f * unit)
                    lineTo(33f * unit, 30f * unit); lineTo(3f * unit, 30f * unit); close()
                }
                drawPath(path, color, style = stroke)
            }
        }
    }
}
