package com.xlabsx27.naveeoffline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xlabsx27.naveeoffline.R

/** Minimal area chart for one series over time. Null values leave a gap. */
@Composable
fun LineChart(
    title: String,
    xs: List<Long>,
    ys: List<Double?>,
    color: Color,
    label: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val values = ys.filterNotNull()
    if (xs.size < 2 || values.size < 2) return
    val minY = minOf(0.0, values.min())
    val maxY = values.max().takeIf { it > minY } ?: (minY + 1)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.chart_max, title, label(values.max())),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp)) {
            val x0 = xs.first()
            val span = (xs.last() - x0).coerceAtLeast(1).toFloat()
            fun px(i: Int) = (xs[i] - x0) / span * size.width
            fun py(v: Double) = size.height - ((v - minY) / (maxY - minY)).toFloat() * size.height

            for (k in 0..2) {
                val y = size.height * k / 2f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val line = Path()
            val area = Path()
            var started = false
            var firstX = 0f
            var lastX = 0f
            for (i in xs.indices) {
                val v = ys[i] ?: continue
                val x = px(i)
                val y = py(v)
                if (!started) {
                    line.moveTo(x, y); area.moveTo(x, size.height); area.lineTo(x, y)
                    firstX = x; started = true
                } else {
                    line.lineTo(x, y); area.lineTo(x, y)
                }
                lastX = x
            }
            area.lineTo(lastX, size.height)
            area.lineTo(firstX, size.height)
            area.close()
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
