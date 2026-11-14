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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.model.CentralBankFeedStatus
import com.example.domain.model.ConnectionHealth
import com.example.domain.model.FredMacroIndicator
import com.example.domain.model.PerfectStormSignal
import com.example.domain.model.ScannerCriteriaConfig
import com.example.domain.model.SentimentDivergenceItem
import com.example.domain.model.SwarmMarketReport
import com.example.domain.model.TribunalChallengeRecord
import com.example.domain.model.WatchtowerAnomaly
import com.example.ui.components.GlassPanel
import com.example.ui.components.MetricTile
import com.example.ui.components.SectionHeader
import com.example.ui.components.SentimentDivergenceCard
import com.example.ui.components.StatusPill
import com.example.ui.components.TreasuryYieldCurveCanvas
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoolGrey
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.DeepRedBearish
import com.example.ui.theme.EmeraldBullish
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.ObsidianBlack

@Composable
fun SupremeTribunalPanel(
    instrument: String,
    isRunning: Boolean,
    records: List<TribunalChallengeRecord>,
    onRunTribunal: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "SUPREME AI INTELLIGENCE TRIBUNAL",
                subtitle = "Adversarial Prosecution vs Defense cross-examination exposing weak assumptions",
                badge = instrument
            )
            Button(
                onClick = onRunTribunal,
                enabled = !isRunning,
                modifier = Modifier.testTag("run_supreme_tribunal_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBlack)
                } else {
                    Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text("Convene Supreme AI Tribunal ($instrument)")
            }
        }

        if (records.isEmpty()) {
            GlassPanel {
                Text(
                    "No Supreme Tribunal trials recorded yet. Scan $instrument via Omniscanner X and tap Convene Tribunal above.",
                    color = CoolGrey
                )
            }
        } else {
            records.forEach { rec ->
                GlassPanel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${rec.instrument} (${rec.timeframe}) Trial", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                        StatusPill("CONSENSUS ${rec.consensusScore}%", if (rec.consensusScore >= 65) EmeraldBullish else AmberWarning)
                    }
                    Text("Supreme Tribunal Verdict:", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                    Text(rec.supremeVerdict, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                    HorizontalDivider(color = GlassBorder)
                    Text("Defense Case (Bullish / Opportunity):", style = MaterialTheme.typography.labelMedium, color = EmeraldBullish)
                    Text(rec.defenseCase, style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                    Text("Prosecution Case (Bearish / Failure Risk):", style = MaterialTheme.typography.labelMedium, color = DeepRedBearish)
                    Text(rec.prosecutionCase, style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                    Text("Cross-Examination Rebuttal:", style = MaterialTheme.typography.labelMedium, color = CyanInfo)
                    Text(rec.crossExaminationChallenges, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                    Text("Data-to-Claim Audit: ${rec.verificationAudit}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                    if (rec.weakAssumptionsExposed.isNotEmpty()) {
                        Text("Weak Assumptions Exposed:", style = MaterialTheme.typography.labelMedium, color = AmberWarning)
                        rec.weakAssumptionsExposed.forEach { w ->
                            Text("• $w", style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutonomousSwarmPanel(
    isRunning: Boolean,
    reports: List<SwarmMarketReport>,
    onDispatchSwarm: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "AUTONOMOUS INTELLIGENCE SWARM",
                subtitle = "Concurrent parallel investigation across all enabled watchlist markets",
                badge = "${reports.size} TARGETS"
            )
            Button(
                onClick = onDispatchSwarm,
                enabled = !isRunning,
                modifier = Modifier.testTag("dispatch_swarm_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBlack)
                } else {
                    Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text("Dispatch Parallel Market Swarm")
            }
        }

        if (reports.isEmpty()) {
            GlassPanel {
                Text("Tap 'Dispatch Parallel Market Swarm' to investigate all enabled instruments simultaneously.", color = CoolGrey)
            }
        } else {
            reports.forEach { r ->
                val color = when {
                    r.health != ConnectionHealth.LIVE -> DeepRedBearish
                    r.structureBias == "BULLISH" -> EmeraldBullish
                    r.structureBias == "BEARISH" -> DeepRedBearish
                    else -> AmberWarning
                }
                GlassPanel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.instrument} (${r.providerId})", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            Text("${r.regimeLabel} • ${r.latencyMs}ms", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                        }
                        StatusPill("${r.opportunityScore}/100", color)
                    }
                    Text("Close: ${TechnicalAnalysisEngine.fmt(r.lastClose)} | Bias: ${r.structureBias}", style = MaterialTheme.typography.bodySmall, color = CyanInfo)
                    Text(r.aiVerdictSummary, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                }
            }
        }
    }
}

@Composable
fun SentimentDivergenceAndStormPanel(
    divergences: List<SentimentDivergenceItem>,
    storms: List<PerfectStormSignal>,
    onScanAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "1. PERFECT STORM DETECTOR",
                subtitle = "Rare convergence of ≥3–4 independent evidence pillars (Structure + Sweep + Zone + Volatility + News)",
                badge = "${storms.count { it.pillarCount >= 3 }} STORMS"
            )
            if (storms.isEmpty()) {
                Text("Run Omniscanner X & Sync News Feeds to evaluate cross-market Perfect Storm alignments.", color = CoolGrey)
                OutlinedButton(onClick = onScanAll, modifier = Modifier.testTag("storm_scan_button")) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Run Market Scan")
                }
            } else {
                storms.forEach { st ->
                    val color = when (st.rarityLabel) {
                        "PERFECT_STORM" -> MetallicGold
                        "HIGH_CONFLUENCE" -> EmeraldBullish
                        else -> CoolGrey
                    }
                    GlassPanel(goldAccent = st.rarityLabel == "PERFECT_STORM") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${st.instrument} (${st.direction})", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            StatusPill("${st.pillarCount} PILLARS • ${st.rarityLabel}", color)
                        }
                        Text(st.summary, style = MaterialTheme.typography.bodySmall, color = CyanInfo)
                        st.independentPillarsMatched.forEach { p ->
                            Text("✓ $p", style = MaterialTheme.typography.bodySmall, color = EmeraldBullish)
                        }
                    }
                }
            }
        }

        GlassPanel {
            SectionHeader(
                title = "GLOBAL SENTIMENT DIVERGENCE RADAR",
                subtitle = "Compares news sentiment, positioning proxies & observed price action to surface conflicts",
                badge = "${divergences.count { it.requiresInvestigation }} CONFLICTS",
                badgeColor = if (divergences.any { it.conflictSeverity == "CRITICAL_CONFLICT" }) DeepRedBearish else AmberWarning
            )
            if (divergences.isEmpty()) {
                Text("Scan markets & sync news feeds to populate live divergence comparisons.", color = CoolGrey)
            }
        }
        divergences.forEach { SentimentDivergenceCard(it) }
    }
}

@Composable
fun CentralBankIntelligencePanel(
    banks: List<CentralBankFeedStatus>,
    macroIndicators: List<FredMacroIndicator>,
    centralBankBrief: String?,
    isAiRunning: Boolean,
    onSyncAll: () -> Unit,
    onRunCentralBankAi: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "CENTRAL BANK INTELLIGENCE",
                subtitle = "Fed, ECB, BoE, BoJ & SNB live releases, policy tone & Treasury yield curve",
                badge = "${banks.count { it.inferredTone != "NO_FEED_DATA" }}/5 LIVE"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSyncAll,
                    modifier = Modifier.testTag("sync_central_banks_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Sync Central Banks & FRED")
                }
                OutlinedButton(
                    onClick = onRunCentralBankAi,
                    enabled = !isAiRunning,
                    modifier = Modifier.testTag("analyze_cb_ai_button")
                ) {
                    Text("AI Policy Synthesis")
                }
            }
            TreasuryYieldCurveCanvas(macroIndicators = macroIndicators)
        }

        banks.forEach { b ->
            val toneColor = when (b.inferredTone) {
                "HAWKISH" -> EmeraldBullish
                "DOVISH / CAUTIOUS" -> DeepRedBearish
                "NO_FEED_DATA" -> AmberWarning
                else -> CyanInfo
            }
            GlassPanel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${b.bankCode} — ${b.bankName}", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                        Text("${b.currency} • ${b.rateSeriesValue}", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                    }
                    StatusPill(b.inferredTone, toneColor)
                }
                Text(b.latestReleaseTitle, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                Text("Published: ${b.latestReleaseDate}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                if (b.sourceUrl.isNotBlank()) {
                    Text(b.sourceUrl, style = MaterialTheme.typography.labelSmall, color = CyanInfo)
                }
            }
        }

        if (!centralBankBrief.isNullOrBlank()) {
            GlassPanel(goldAccent = true) {
                SectionHeader("CENTRAL BANK AI POLICY BRIEF", "Rate expectations & cross-currency summary")
                Text(centralBankBrief, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
            }
        }
    }
}

@Composable
fun OmniscientWatchtowerPanel(
    anomalies: List<WatchtowerAnomaly>,
    onRunSweep: () -> Unit
) {
    GlassPanel(goldAccent = true) {
        SectionHeader(
            title = "OMNISCIENT WATCHTOWER ALERTS",
            subtitle = "Monitors connected APIs, AI providers, feeds, workflows & risk anomalies",
            badge = "${anomalies.size} ALERTS",
            badgeColor = if (anomalies.any { it.severity == "CRITICAL" || it.severity == "HIGH" }) DeepRedBearish else EmeraldBullish
        )
        OutlinedButton(onClick = onRunSweep, modifier = Modifier.testTag("watchtower_sweep_button")) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("Run Omniscient System Sweep")
        }
        if (anomalies.isEmpty()) {
            Text("All connected feeds, workflows, and execution gates nominal.", color = EmeraldBullish)
        } else {
            anomalies.forEach { a ->
                val sevColor = when (a.severity) {
                    "CRITICAL" -> DeepRedBearish
                    "HIGH" -> AmberWarning
                    "MEDIUM" -> CyanInfo
                    else -> CoolGrey
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${a.domain} • ${a.title}", style = MaterialTheme.typography.bodyMedium, color = CrispWhite)
                        Text(a.detail, style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                    }
                    StatusPill(a.severity, sevColor)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigurableScannerCriteriaBar(
    criteria: ScannerCriteriaConfig,
    onUpdateCriteria: (ScannerCriteriaConfig) -> Unit
) {
    GlassPanel {
        SectionHeader("GLOBAL OPPORTUNITY SCANNER CRITERIA", "Filter ranked setups by confluence, volatility, trend bias & spread")
        Text("Min Confluence Score: ${criteria.minConfluenceScore} | Min Vol Ratio: ${"%.1f".format(criteria.minVolatilityRatio)}x", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 40, 60, 75).forEach { minScore ->
                FilterChip(
                    selected = criteria.minConfluenceScore == minScore,
                    onClick = { onUpdateCriteria(criteria.copy(minConfluenceScore = minScore)) },
                    label = { Text("Score ≥$minScore") }
                )
            }
            listOf(0.0, 1.0, 1.25).forEach { volMin ->
                FilterChip(
                    selected = criteria.minVolatilityRatio == volMin,
                    onClick = { onUpdateCriteria(criteria.copy(minVolatilityRatio = volMin)) },
                    label = { Text(if (volMin == 0.0) "Any Vol" else "Vol ≥${volMin}x") }
                )
            }
            listOf("ALL", "BULLISH", "BEARISH").forEach { bias ->
                FilterChip(
                    selected = criteria.trendBiasFilter == bias,
                    onClick = { onUpdateCriteria(criteria.copy(trendBiasFilter = bias)) },
                    label = { Text(bias) }
                )
            }
        }
    }
}
