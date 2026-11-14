package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.TechnicalSnapshot
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlackGlass
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
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    goldAccent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BlackGlass.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, if (goldAccent) GlassBorderGold else GlassBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    valueColor: Color = CrispWhite,
    subLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BlackGlassElevated, RoundedCornerShape(10.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = CoolGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subLabel.isNullOrBlank()) {
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = CoolGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = MetallicGold
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = CrispWhite)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = CoolGrey)
        }
        if (badge != null) {
            StatusPill(text = badge, color = badgeColor)
        }
    }
}

@Composable
fun AnimatedMarketRadar(
    snapshots: List<TechnicalSnapshot>,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(ObsidianBlack, RoundedCornerShape(12.dp))
            .border(1.dp, GlassBorderGold, RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) * 0.44f
            val center = Offset(size.width / 2f, size.height / 2f)

            listOf(0.33f, 0.66f, 1.0f).forEach { factor ->
                drawCircle(
                    color = GlassBorder,
                    radius = radius * factor,
                    center = center,
                    style = Stroke(width = 1.2f)
                )
            }
            drawLine(GlassBorder, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1f)
            drawLine(GlassBorder, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1f)

            val rad = Math.toRadians(sweepAngle.toDouble())
            val endPt = Offset(
                center.x + (radius * cos(rad)).toFloat(),
                center.y + (radius * sin(rad)).toFloat()
            )
            drawLine(
                brush = Brush.linearGradient(listOf(MetallicGold.copy(alpha = 0.1f), MetallicGold), center, endPt),
                start = center,
                end = endPt,
                strokeWidth = 3f
            )

            snapshots.forEachIndexed { index, snap ->
                val angle = Math.toRadians((index * (360.0 / maxOf(1, snapshots.size))) + 25.0)
                val distFactor = (1.0 - (snap.opportunityScore / 105.0)).toFloat().coerceIn(0.18f, 0.92f)
                val px = center.x + (radius * distFactor * cos(angle)).toFloat()
                val py = center.y + (radius * distFactor * sin(angle)).toFloat()
                val blipColor = when (snap.structureBias) {
                    "BULLISH" -> EmeraldBullish
                    "BEARISH" -> DeepRedBearish
                    else -> AmberWarning
                }
                drawCircle(color = blipColor.copy(alpha = 0.25f), radius = 14f, center = Offset(px, py))
                drawCircle(color = blipColor, radius = 6f, center = Offset(px, py))
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("APEX RADAR", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
            Text(
                text = if (snapshots.isEmpty()) "0 Active Targets" else "${snapshots.size} Tracked",
                style = MaterialTheme.typography.labelSmall,
                color = CoolGrey
            )
        }
        Text(
            text = "BULL / BEAR / RANGE",
            style = MaterialTheme.typography.labelSmall,
            color = CyanInfo,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}
