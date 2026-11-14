package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.model.HistoricalRealitySimulation
import com.example.domain.model.PatternFingerprintMatch
import com.example.domain.model.StrategyVariantResult
import com.example.ui.components.GlassPanel
import com.example.ui.components.MetricTile
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoolGrey
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.DeepRedBearish
import com.example.ui.theme.EmeraldBullish
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.ObsidianBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrategyDnaMutationLabPanel(
    instrument: String,
    timeframe: String,
    variants: List<StrategyVariantResult>,
    onRunMutationLab: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "STRATEGY DNA MUTATION LAB",
                subtitle = "Controlled parameter variations tested walk-forward with PROMOTE / SHADOW / REJECT scoring",
                badge = "$instrument $timeframe"
            )
            Button(
                onClick = onRunMutationLab,
                modifier = Modifier.testTag("run_mutation_lab_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Icon(Icons.Default.Biotech, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Mutate & Benchmark 5 DNA Variants")
            }
        }

        if (variants.isEmpty()) {
            GlassPanel {
                Text(
                    "No strategy mutations benchmarked for $instrument yet. Load 45+ live candles via Omniscanner X, then tap Mutate above.",
                    color = CoolGrey
                )
            }
        } else {
            variants.forEach { v ->
                val recColor = when (v.recommendation) {
                    "PROMOTE" -> EmeraldBullish
                    "SHADOW_TEST" -> AmberWarning
                    else -> DeepRedBearish
                }
                GlassPanel(goldAccent = v.recommendation == "PROMOTE") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${v.versionTag} • ${v.variantName}", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            Text("Robustness ${v.robustnessScore}/100", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                        }
                        StatusPill(v.recommendation, recColor)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Trades", "${v.totalTrades}", modifier = Modifier.weight(1f))
                        MetricTile("Win Rate", "${"%.1f".format(v.winRatePct)}%", modifier = Modifier.weight(1f))
                        MetricTile("Profit Factor", "${"%.2f".format(v.profitFactor)}", MetallicGold, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("In-Sample", "${"%+.2f".format(v.inSampleExpectancyR)}R", modifier = Modifier.weight(1f))
                        MetricTile("Out-Sample", "${"%+.2f".format(v.outSampleExpectancyR)}R", if (v.outSampleExpectancyR > 0) EmeraldBullish else DeepRedBearish, modifier = Modifier.weight(1f))
                        MetricTile("Max DD", "${"%.2f".format(v.maxDrawdownPct)}%", DeepRedBearish, modifier = Modifier.weight(1f))
                    }
                    Text("Parameter Mutations:", style = MaterialTheme.typography.labelMedium, color = CyanInfo)
                    v.parameterChanges.forEach { diff ->
                        Text("• $diff", style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                    }
                    Text(v.rationale, style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                }
            }
        }
    }
}

@Composable
fun PatternFingerprintScannerPanel(
    instrument: String,
    matches: List<PatternFingerprintMatch>,
    onScanFingerprints: () -> Unit
) {
    val sdf = remember {
        SimpleDateFormat("MMM dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "MARKET PATTERN FINGERPRINT SCANNER",
                subtitle = "Behavioral vector matching + historical outcomes & explicit structural differences",
                badge = "${matches.size} MATCHES"
            )
            Button(
                onClick = onScanFingerprints,
                modifier = Modifier.testTag("scan_fingerprints_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Scan Behavior Fingerprints ($instrument)")
            }
        }

        if (matches.isEmpty()) {
            GlassPanel {
                Text(
                    "Scan 40+ live candles via Omniscanner X and tap Scan Behavior Fingerprints to rank comparable historical price sequences.",
                    color = CoolGrey
                )
            }
        } else {
            GlassPanel {
                Text("Current Window Signature:", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                Text(matches.first().currentFingerprintSummary, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
            }
            matches.forEachIndexed { idx, m ->
                val retColor = if (m.subsequentReturnPct >= 0) EmeraldBullish else DeepRedBearish
                GlassPanel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${idx + 1} • Similarity ${"%.1f".format(m.similarityPct)}%", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                            Text("Window: ${sdf.format(Date(m.startEpochSec * 1000L))} UTC", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                        }
                        StatusPill(m.historicalRegime, retColor)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("10-Bar Outcome", "${"%+.2f".format(m.subsequentReturnPct)}%", retColor, modifier = Modifier.weight(1f))
                        MetricTile("MFE", "+${"%.2f".format(m.maxFavorableExcursionPct)}%", EmeraldBullish, modifier = Modifier.weight(1f))
                        MetricTile("MAE", "-${"%.2f".format(m.maxAdverseExcursionPct)}%", DeepRedBearish, modifier = Modifier.weight(1f))
                    }
                    Text("Matched Window: ${m.matchedFingerprintSummary}", style = MaterialTheme.typography.labelSmall, color = CyanInfo)
                    HorizontalDivider(color = GlassBorder)
                    Text("Differences vs Current Market:", style = MaterialTheme.typography.labelMedium, color = AmberWarning)
                    m.differencesFromCurrent.forEach { d ->
                        Text("• $d", style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoricalRealitySimulatorPanel(
    instrument: String,
    timeframe: String,
    candleCount: Int,
    simulation: HistoricalRealitySimulation?,
    isBusy: Boolean,
    onRunBlindSim: (Float, Boolean) -> Unit,
    onRevealOutcome: () -> Unit
) {
    var cutoffPct by remember { mutableFloatStateOf(0.55f) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "HISTORICAL REALITY SIMULATOR",
                subtitle = "Tests quantitative & AI reasoning strictly without hindsight before revealing outcomes",
                badge = "$candleCount BARS"
            )
            Text(
                "Cutoff Slice Position: ${(cutoffPct * 100).toInt()}% through loaded $instrument ($timeframe) archive",
                style = MaterialTheme.typography.bodySmall,
                color = CrispWhite
            )
            Slider(
                value = cutoffPct,
                onValueChange = { cutoffPct = it },
                valueRange = 0.2f..0.85f,
                colors = SliderDefaults.colors(thumbColor = MetallicGold, activeTrackColor = MetallicGold)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onRunBlindSim(cutoffPct, false) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f).testTag("run_blind_det_sim_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Lock Blind Rule Test")
                }
                OutlinedButton(
                    onClick = { onRunBlindSim(cutoffPct, true) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f).testTag("run_blind_ai_sim_button")
                ) {
                    if (isBusy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Lock Blind AI Test")
                }
            }
        }

        if (simulation != null) {
            GlassPanel(goldAccent = simulation.futureRevealed) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Cutoff Bar #${simulation.cutoffIndex + 1} / ${simulation.totalCandles}", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                    StatusPill(
                        text = if (simulation.futureRevealed) {
                            if (simulation.forecastCorrect == true) "FORESIGHT VERIFIED" else "FORESIGHT MISSED"
                        } else "FUTURE LOCKED",
                        color = when {
                            !simulation.futureRevealed -> CyanInfo
                            simulation.forecastCorrect == true -> EmeraldBullish
                            else -> DeepRedBearish
                        }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Blind Bias", simulation.blindBias, MetallicGold, modifier = Modifier.weight(1f))
                    MetricTile("Cutoff Close", TechnicalAnalysisEngine.fmt(simulation.cutoffClose), modifier = Modifier.weight(1f))
                    MetricTile("Confidence", "${simulation.blindConfidence}%", modifier = Modifier.weight(1f))
                }
                Text("Blind Reasoning (Zero Hindsight):", style = MaterialTheme.typography.labelMedium, color = CyanInfo)
                Text(simulation.blindReasoning, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                Text(
                    "Locked Levels: Target ${TechnicalAnalysisEngine.fmt(simulation.blindKeyLevel)} | Invalidation ${TechnicalAnalysisEngine.fmt(simulation.blindInvalidation)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolGrey
                )

                if (!simulation.futureRevealed) {
                    Button(
                        onClick = onRevealOutcome,
                        modifier = Modifier.testTag("reveal_future_reality_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanInfo, contentColor = ObsidianBlack)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Reveal Subsequent ${simulation.horizonBars} Bars & Grade Reality")
                    }
                } else {
                    HorizontalDivider(color = GlassBorder)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Actual Return", "${"%+.2f".format(simulation.actualOutcomeReturnPct)}%", if (simulation.actualOutcomeReturnPct >= 0) EmeraldBullish else DeepRedBearish, modifier = Modifier.weight(1f))
                        MetricTile("Post-Cutoff High", TechnicalAnalysisEngine.fmt(simulation.actualHighAfterCutoff), EmeraldBullish, modifier = Modifier.weight(1f))
                        MetricTile("Post-Cutoff Low", TechnicalAnalysisEngine.fmt(simulation.actualLowAfterCutoff), DeepRedBearish, modifier = Modifier.weight(1f))
                    }
                    Text(simulation.gradingNotes, style = MaterialTheme.typography.bodyMedium, color = if (simulation.forecastCorrect == true) EmeraldBullish else AmberWarning)
                }
            }
        }
    }
}
