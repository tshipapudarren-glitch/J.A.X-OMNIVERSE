package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.FredMacroIndicator
import com.example.domain.model.NewsTimelineEvent
import com.example.domain.model.SentimentDivergenceItem
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackGlassElevated
import com.example.ui.theme.CoolGrey
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.DeepRedBearish
import com.example.ui.theme.EmeraldBullish
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderGold
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.ObsidianBlack

@Composable
fun TreasuryYieldCurveCanvas(
    macroIndicators: List<FredMacroIndicator>,
    modifier: Modifier = Modifier
) {
    val curveTenors = listOf("FEDFUNDS" to "FF", "DGS2" to "2Y", "DGS5" to "5Y", "DGS10" to "10Y", "DGS30" to "30Y")
    val points = curveTenors.mapNotNull { (id, label) ->
        val raw = macroIndicators.firstOrNull { it.seriesId == id }
        val num = raw?.value?.replace("%", "")?.toDoubleOrNull()
        if (num != null) label to num else null
    }

    if (points.size < 2) {
        Surface(
            color = ObsidianBlack.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, GlassBorder),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = "U.S. Treasury Yield Curve: Sync FRED API (FEDFUNDS, DGS2, DGS5, DGS10, DGS30) to render real live curve points.",
                style = MaterialTheme.typography.bodySmall,
                color = CoolGrey,
                modifier = Modifier.padding(12.dp)
            )
        }
        return
    }

    val minY = (points.minOf { it.second } - 0.5).coerceAtLeast(0.0)
    val maxY = points.maxOf { it.second } + 0.5
    val rangeY = (maxY - minY).coerceAtLeast(0.5)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBlack.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .border(1.dp, GlassBorderGold, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("LIVE U.S. TREASURY YIELD CURVE (FRED)", style = MaterialTheme.typography.labelLarge, color = MetallicGold)
            val spread10_2 = macroIndicators.firstOrNull { it.seriesId == "T10Y2Y" }
            if (spread10_2 != null) {
                val neg = spread10_2.value.startsWith("-")
                StatusPill("10Y-2Y ${spread10_2.value}", if (neg) DeepRedBearish else EmeraldBullish)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val stepX = size.width / maxOf(1, points.size - 1)
            val path = Path()
            points.forEachIndexed { idx, (_, value) ->
                val x = idx * stepX
                val y = size.height - (((value - minY) / rangeY).toFloat() * size.height)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawLine(GlassBorder, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 1f)
            drawPath(path, color = CyanInfo, style = Stroke(width = 4f, cap = StrokeCap.Round))
            points.forEachIndexed { idx, (_, value) ->
                val x = idx * stepX
                val y = size.height - (((value - minY) / rangeY).toFloat() * size.height)
                drawCircle(color = MetallicGold, radius = 7f, center = Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { (label, value) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                    Text("${"%.2f".format(value)}%", style = MaterialTheme.typography.labelMedium, color = CrispWhite, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SentimentDivergenceCard(item: SentimentDivergenceItem) {
    val sevColor = when (item.conflictSeverity) {
        "CRITICAL_CONFLICT" -> DeepRedBearish
        "MODERATE_CONFLICT" -> AmberWarning
        else -> EmeraldBullish
    }
    GlassPanel(goldAccent = item.requiresInvestigation) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(item.instrument, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
            StatusPill(item.conflictSeverity.replace("_", " "), sevColor)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("News Narrative", item.newsSentimentLabel, if (item.newsBiasScore >= 0) EmeraldBullish else DeepRedBearish, modifier = Modifier.weight(1f))
            MetricTile("Observed Price", item.actualPriceBehavior, if (item.priceBiasScore >= 0) EmeraldBullish else DeepRedBearish, modifier = Modifier.weight(1f))
        }
        Text("Positioning & Flow: ${item.positioningProxy}", style = MaterialTheme.typography.labelSmall, color = CyanInfo)
        Text(item.explanation, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
        if (item.requiresInvestigation) {
            LinearProgressIndicator(
                progress = { ((kotlin.math.abs(item.newsBiasScore - item.priceBiasScore)) / 200f).coerceIn(0.15f, 1f) },
                color = sevColor,
                trackColor = BlackGlassElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
fun NewsTimelineRailItem(
    event: NewsTimelineEvent,
    onInvestigateClick: () -> Unit
) {
    val sentColor = when {
        "POSITIVE" in event.sentimentLabel || "HAWKISH" in event.sentimentLabel -> EmeraldBullish
        "CAUTIOUS" in event.sentimentLabel || "RISK-OFF" in event.sentimentLabel -> DeepRedBearish
        else -> CyanInfo
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(28.dp)
                .padding(top = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MetallicGold, CircleShape)
            )
            Spacer(
                modifier = Modifier
                    .width(2.dp)
                    .height(92.dp)
                    .background(GlassBorder)
            )
        }
        GlassPanel(modifier = Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("#${event.chronologicalIndex} • ${event.timestampDisplay}", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                StatusPill(event.sentimentLabel, sentColor)
            }
            Text(event.sourceName, style = MaterialTheme.typography.labelMedium, color = CyanInfo)
            Text(event.headline, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
            Text(event.summary, style = MaterialTheme.typography.bodySmall, color = CoolGrey)
            Text("Market Reaction Context: ${event.marketReactionNote}", style = MaterialTheme.typography.labelSmall, color = EmeraldBullish)
            if (event.articleUrl.isNotBlank()) {
                Text("Source: ${event.articleUrl}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
            }
            OutlinedButton(
                onClick = onInvestigateClick,
                modifier = Modifier.testTag("investigate_news_${event.chronologicalIndex}")
            ) {
                Text("AI Team Verify Story")
            }
        }
    }
}
