package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.model.Candle
import com.example.domain.model.ChartAnnotation
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackGlass
import com.example.ui.theme.CoolGrey
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.DeepRedBearish
import com.example.ui.theme.EmeraldBullish
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.ObsidianBlack
import kotlin.math.max
import kotlin.math.min

/**
 * Institutional OHLC Candlestick Chart rendered deterministically on Compose Canvas.
 * Draws real candle wicks/bodies plus linked technical & trade-plan annotations.
 */
@Composable
fun InstitutionalCandlestickChart(
    candles: List<Candle>,
    annotations: List<ChartAnnotation>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(270.dp)
            .background(ObsidianBlack, RoundedCornerShape(12.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        if (candles.isEmpty()) {
            Text(
                text = "NO LIVE CANDLES LOADED\nRun Omniscanner X to stream verified market OHLCV data.",
                style = MaterialTheme.typography.labelMedium,
                color = CoolGrey,
                modifier = Modifier.align(Alignment.Center)
            )
            return
        }

        val visibleCandles = candles.takeLast(55)
        val rawMin = min(
            visibleCandles.minOf { it.low },
            annotations.map { min(it.priceStart, it.priceEnd) }.minOrNull() ?: Double.MAX_VALUE
        )
        val rawMax = max(
            visibleCandles.maxOf { it.high },
            annotations.map { max(it.priceStart, it.priceEnd) }.maxOrNull() ?: Double.MIN_VALUE
        )
        val padding = max((rawMax - rawMin) * 0.08, rawMax * 0.0005)
        val minP = rawMin - padding
        val maxP = rawMax + padding
        val range = max(1e-9, maxP - minP)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val rightAxisWidth = 72.dp.toPx()
            val chartW = (size.width - rightAxisWidth).coerceAtLeast(40f)
            val chartH = size.height

            fun priceToY(price: Double): Float {
                val ratio = ((price - minP) / range).toFloat().coerceIn(0f, 1f)
                return chartH - (ratio * chartH)
            }

            // Grid lines
            for (g in 1..4) {
                val y = chartH * (g / 5f)
                drawLine(
                    color = GlassBorder.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(chartW, y),
                    strokeWidth = 1f
                )
                val gridPrice = maxP - (range * (g / 5.0))
                drawLabelText(textMeasurer, TechnicalAnalysisEngine.fmt(gridPrice), chartW + 6f, y - 8f, CoolGrey)
            }

            // Zones (FVG / Order Block / Entry Zone)
            annotations.filter { it.priceEnd != it.priceStart }.forEach { zone ->
                val y1 = priceToY(max(zone.priceStart, zone.priceEnd))
                val y2 = priceToY(min(zone.priceStart, zone.priceEnd))
                val zoneColor = when (zone.type) {
                    "ENTRY_ZONE" -> MetallicGold.copy(alpha = 0.16f)
                    "FVG" -> CyanInfo.copy(alpha = 0.14f)
                    else -> AmberWarning.copy(alpha = 0.14f)
                }
                drawRect(
                    color = zoneColor,
                    topLeft = Offset(0f, y1),
                    size = Size(chartW, (y2 - y1).coerceAtLeast(4f))
                )
            }

            // Candlesticks
            val step = chartW / visibleCandles.size.toFloat()
            val bodyW = (step * 0.62f).coerceIn(3f, 16f)
            visibleCandles.forEachIndexed { idx, c ->
                val cx = idx * step + step / 2f
                val isBull = c.close >= c.open
                val color = if (isBull) EmeraldBullish else DeepRedBearish
                val yHigh = priceToY(c.high)
                val yLow = priceToY(c.low)
                val yOpen = priceToY(c.open)
                val yClose = priceToY(c.close)

                drawLine(
                    color = color,
                    start = Offset(cx, yHigh),
                    end = Offset(cx, yLow),
                    strokeWidth = 1.6f
                )
                val top = min(yOpen, yClose)
                val height = max(2f, kotlin.math.abs(yClose - yOpen))
                drawRect(
                    color = color,
                    topLeft = Offset(cx - bodyW / 2f, top),
                    size = Size(bodyW, height)
                )
            }

            // Horizontal Level Lines (SL, TP, Liquidity, Swing)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            annotations.forEach { ann ->
                val y = priceToY(ann.priceStart)
                val lineColor = when (ann.type) {
                    "SL" -> DeepRedBearish
                    "TP1", "TP2", "TP3" -> EmeraldBullish
                    "ENTRY_ZONE" -> MetallicGold
                    "LIQUIDITY" -> AmberWarning
                    "FVG" -> CyanInfo
                    else -> CoolGrey
                }
                drawLine(
                    color = lineColor.copy(alpha = 0.85f),
                    start = Offset(0f, y),
                    end = Offset(chartW, y),
                    strokeWidth = 1.5f,
                    pathEffect = if (ann.type in listOf("SL", "TP1", "TP2", "TP3")) null else dashEffect
                )
                drawLabelText(textMeasurer, ann.label.take(10), 4f, (y - 14f).coerceAtLeast(2f), lineColor)
            }

            // Current close highlight
            val lastClose = visibleCandles.last().close
            val lastY = priceToY(lastClose)
            drawRect(
                color = MetallicGold,
                topLeft = Offset(chartW + 2f, (lastY - 10f).coerceIn(0f, chartH - 20f)),
                size = Size(rightAxisWidth - 4f, 20f)
            )
            drawLabelText(
                textMeasurer,
                TechnicalAnalysisEngine.fmt(lastClose),
                chartW + 6f,
                (lastY - 7f).coerceIn(2f, chartH - 18f),
                BlackGlass
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabelText(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color
) {
    val result = measurer.measure(
        text = text,
        style = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            color = color
        )
    )
    drawText(result, topLeft = Offset(x, y))
}
