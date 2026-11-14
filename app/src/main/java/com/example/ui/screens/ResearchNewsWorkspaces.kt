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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.BacktestRunRecord
import com.example.domain.model.NewsArticleItem
import com.example.domain.model.TradingSetup
import com.example.ui.components.GlassPanel
import com.example.ui.components.MetricTile
import com.example.ui.components.NewsTimelineRailItem
import com.example.ui.components.SectionHeader
import com.example.ui.components.SentimentDivergenceCard
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
import com.example.ui.viewmodel.JaxUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewsAndCalendarScreen(
    isCalendarView: Boolean,
    uiState: JaxUiState,
    onFetchLiveNews: () -> Unit,
    onFetchCalendar: () -> Unit,
    onGenerateDailyBriefing: () -> Unit,
    onInvestigateNews: (NewsArticleItem) -> Unit = {},
    onAnalyzeCentralBanks: () -> Unit = {}
) {
    var newsTab by remember { mutableIntStateOf(0) } // 0 = Feed & Briefing, 1 = Timeline, 2 = AI News Team, 3 = Sentiment Divergence
    var calTab by remember { mutableIntStateOf(0) } // 0 = Central Bank Intelligence, 1 = Economic Calendar & FRED

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = if (isCalendarView) "CENTRAL BANK & MACRO CALENDAR" else "NEWS INTELLIGENCE & RECONSTRUCTION",
                subtitle = if (isCalendarView) "Fed, ECB, BoE, BoJ & SNB policy intelligence + Finnhub/FMP/FRED macro"
                else "Chronological Timeline Reconstruction, Multi-AI News Investigation & Divergence Radar",
                badge = if (isCalendarView) "${uiState.calendarEvents.size} EVENTS" else "${uiState.newsArticles.size} VERIFIED"
            )
            if (isCalendarView) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = calTab == 0, onClick = { calTab = 0 }, label = { Text("Central Banks (5)") })
                    FilterChip(selected = calTab == 1, onClick = { calTab = 1 }, label = { Text("Economic Calendar & FRED") })
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = newsTab == 0, onClick = { newsTab = 0 }, label = { Text("Live Feed & Briefing") })
                    FilterChip(selected = newsTab == 1, onClick = { newsTab = 1 }, label = { Text("Timeline (${uiState.newsTimeline.size})") })
                    FilterChip(selected = newsTab == 2, onClick = { newsTab = 2 }, label = { Text("AI News Team (${uiState.newsInvestigations.size})") })
                    FilterChip(selected = newsTab == 3, onClick = { newsTab = 3 }, label = { Text("Divergence Radar") })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (isCalendarView) onFetchCalendar else onFetchLiveNews,
                    enabled = if (isCalendarView) !uiState.isCalendarFetching else !uiState.isNewsFetching,
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                ) {
                    val busy = if (isCalendarView) uiState.isCalendarFetching else uiState.isNewsFetching
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBlack)
                    } else {
                        Icon(Icons.Default.Newspaper, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(if (isCalendarView) "Sync Calendar & FRED" else "Fetch Live News Feeds")
                }
                if (isCalendarView) {
                    OutlinedButton(onClick = onFetchLiveNews, enabled = !uiState.isNewsFetching) {
                        Text("Sync Central Bank RSS")
                    }
                } else {
                    OutlinedButton(onClick = onGenerateDailyBriefing, enabled = !uiState.isAiRunning) {
                        Text("Executive Briefing AI")
                    }
                }
            }
            val errs = if (isCalendarView) uiState.calendarErrors else uiState.newsErrors
            if (errs.isNotEmpty()) {
                Text("Feed Status: ${errs.joinToString(" | ")}", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
            }
        }

        if (isCalendarView) {
            if (calTab == 0) {
                CentralBankIntelligencePanel(
                    banks = uiState.centralBankStatuses,
                    macroIndicators = uiState.macroIndicators,
                    centralBankBrief = uiState.centralBankBrief,
                    isAiRunning = uiState.isAiRunning,
                    onSyncAll = {
                        onFetchLiveNews()
                        onFetchCalendar()
                    },
                    onRunCentralBankAi = onAnalyzeCentralBanks
                )
            } else {
                if (uiState.macroIndicators.isNotEmpty()) {
                    GlassPanel(goldAccent = true) {
                        SectionHeader("FRED FEDERAL RESERVE MACRO SERIES", "Observed rates, Treasury yields & curve spread")
                        uiState.macroIndicators.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { ind ->
                                    MetricTile(
                                        label = "${ind.seriesId} (${ind.observationDate})",
                                        value = ind.value,
                                        valueColor = MetallicGold,
                                        subLabel = ind.title,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (uiState.calendarEvents.isNotEmpty()) {
                    uiState.calendarEvents.forEach { ev ->
                        val impColor = when (ev.impact) {
                            "HIGH" -> DeepRedBearish
                            "MEDIUM" -> AmberWarning
                            else -> CyanInfo
                        }
                        GlassPanel {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${ev.currency} • ${ev.sourceName}", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                                StatusPill(ev.impact, impColor)
                            }
                            Text(ev.title, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricTile("Actual", ev.actual, EmeraldBullish, modifier = Modifier.weight(1f))
                                MetricTile("Forecast", ev.forecast, modifier = Modifier.weight(1f))
                                MetricTile("Previous", ev.previous, modifier = Modifier.weight(1f))
                            }
                            Text("Scheduled: ${ev.eventTime}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                        }
                    }
                } else if (uiState.macroIndicators.isEmpty()) {
                    GlassPanel {
                        SectionHeader("ECONOMIC CALENDAR & MACRO FEED", "Actual vs Forecast vs Previous Engine")
                        Text(
                            "Tap 'Sync Calendar & FRED' above. Configure FINNHUB_API_KEY, FMP_API_KEY, or FRED_API_KEY in AI Studio Secrets or API Connections to retrieve structured calendar & Fed macro telemetry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberWarning
                        )
                    }
                }
            }
            return
        }

        when (newsTab) {
            1 -> {
                GlassPanel {
                    SectionHeader("NEWS TIMELINE RECONSTRUCTION", "Chronological sequence showing what happened, who reported it & market reaction")
                    if (uiState.newsTimeline.isEmpty()) {
                        Text("Fetch live news feeds above to reconstruct chronological timeline events.", color = CoolGrey)
                    }
                }
                uiState.newsTimeline.forEach { ev ->
                    val matchingArticle = uiState.newsArticles.firstOrNull { it.id == ev.id }
                    NewsTimelineRailItem(
                        event = ev,
                        onInvestigateClick = {
                            if (matchingArticle != null) {
                                onInvestigateNews(matchingArticle)
                                newsTab = 2
                            }
                        }
                    )
                }
            }
            2 -> {
                GlassPanel(goldAccent = true) {
                    SectionHeader("AI NEWS INVESTIGATION TEAM", "Multi-model Lead Investigator + Cross-Source Fact Verifier")
                    if (uiState.newsInvestigations.isEmpty()) {
                        Text("Tap 'AI Team Investigate' on any live news story to cross-verify claims and expose conflicting reports.", color = CoolGrey)
                    }
                }
                uiState.newsInvestigations.forEach { inv ->
                    GlassPanel {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(inv.sourceName, style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                            StatusPill("VERIFIED AUDIT", CyanInfo)
                        }
                        Text(inv.headline, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                        HorizontalDivider(color = GlassBorder)
                        Text("Lead Investigator Findings:", style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                        Text(inv.leadInvestigatorFindings, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                        Text("Fact Verification Audit:", style = MaterialTheme.typography.labelMedium, color = EmeraldBullish)
                        Text(inv.factVerificationAudit, style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                        Text("Conflicting Reports / Gaps:", style = MaterialTheme.typography.labelMedium, color = AmberWarning)
                        Text(inv.conflictingReports, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                        Text("Credibility: ${inv.credibilityAssessment}", style = MaterialTheme.typography.labelSmall, color = CyanInfo)
                    }
                }
            }
            3 -> {
                if (uiState.sentimentDivergences.isEmpty()) {
                    GlassPanel {
                        Text("Scan markets and sync news feeds to populate Global Sentiment Divergence Radar.", color = CoolGrey)
                    }
                } else {
                    uiState.sentimentDivergences.forEach { SentimentDivergenceCard(it) }
                }
            }
            else -> {
                val brief = uiState.executiveBriefing ?: uiState.dailyBriefing
                if (!brief.isNullOrBlank()) {
                    GlassPanel(goldAccent = true) {
                        SectionHeader("EXECUTIVE INTELLIGENCE BRIEFING", "Priorities, unfinished missions, news, alerts & actions")
                        Text(brief, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                    }
                }
                if (uiState.newsArticles.isEmpty()) {
                    GlassPanel {
                        Text("Tap 'Fetch Live News Feeds' to retrieve real-time articles with original source URLs and timestamps.", color = CoolGrey)
                    }
                } else {
                    uiState.newsArticles.forEach { art ->
                        val sentColor = when {
                            "POSITIVE" in art.sentimentLabel || "BULLISH" in art.sentimentLabel -> EmeraldBullish
                            "CAUTIOUS" in art.sentimentLabel || "BEARISH" in art.sentimentLabel -> DeepRedBearish
                            else -> CyanInfo
                        }
                        GlassPanel {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(art.sourceName, style = MaterialTheme.typography.labelMedium, color = MetallicGold)
                                StatusPill(art.sentimentLabel, sentColor)
                            }
                            Text(art.title, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            Text(art.summary, style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                            Text("Published: ${art.publishedAt} | Instruments: ${art.affectedInstruments.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = CyanInfo)
                            Text("Source URL: ${art.articleUrl}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                            OutlinedButton(
                                onClick = {
                                    onInvestigateNews(art)
                                    newsTab = 2
                                },
                                enabled = !uiState.isAiRunning
                            ) {
                                Text("AI Team Investigate Story")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuantLabAndHistoricalScreen(
    isBacktestView: Boolean,
    uiState: JaxUiState,
    backtests: List<BacktestRunRecord>,
    setups: List<TradingSetup>,
    onRunBacktest: (String, Double, Double) -> Unit,
    onRunMutationLab: () -> Unit = {},
    onRunBlindSim: (Float, Boolean) -> Unit = { _, _ -> },
    onRevealSimOutcome: () -> Unit = {}
) {
    var strategyName by remember { mutableStateOf("EMA Displacement Walk-Forward") }
    var spreadPct by remember { mutableStateOf("0.04") }
    var slippagePct by remember { mutableStateOf("0.02") }
    var labTab by remember { mutableIntStateOf(0) } // 0 = DNA Mutation Lab, 1 = Walk-Forward Backtest

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isBacktestView) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = labTab == 0, onClick = { labTab = 0 }, label = { Text("Strategy DNA Mutation Lab") })
                FilterChip(selected = labTab == 1, onClick = { labTab = 1 }, label = { Text("Single Walk-Forward") })
            }
            if (labTab == 0) {
                val variants = uiState.strategyVariantsBySymbol[uiState.selectedInstrument].orEmpty()
                StrategyDnaMutationLabPanel(
                    instrument = uiState.selectedInstrument,
                    timeframe = uiState.selectedTimeframe,
                    variants = variants,
                    onRunMutationLab = onRunMutationLab
                )
            } else {
                GlassPanel(goldAccent = true) {
                    SectionHeader(
                        title = "APEX QUANT LAB — BACKTESTING",
                        subtitle = "Chronological bar-by-bar simulation (zero look-ahead bias) with spread & slippage",
                        badge = "${uiState.selectedInstrument} ${uiState.selectedTimeframe}"
                    )
                    OutlinedTextField(
                        value = strategyName,
                        onValueChange = { strategyName = it },
                        label = { Text("Strategy Specification") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = spreadPct,
                            onValueChange = { spreadPct = it },
                            label = { Text("Spread %") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = slippagePct,
                            onValueChange = { slippagePct = it },
                            label = { Text("Slippage %") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            onRunBacktest(
                                strategyName,
                                spreadPct.toDoubleOrNull() ?: 0.04,
                                slippagePct.toDoubleOrNull() ?: 0.02
                            )
                        },
                        modifier = Modifier.testTag("run_backtest_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Run Walk-Forward Backtest")
                    }
                }

                backtests.forEach { bt ->
                    GlassPanel {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${bt.strategyName} (${bt.instrument})", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            StatusPill(
                                text = if (bt.walkForwardPassed) "WF PASSED" else "WF FAILED",
                                color = if (bt.walkForwardPassed) EmeraldBullish else DeepRedBearish
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("Trades", "${bt.totalTrades}", modifier = Modifier.weight(1f))
                            MetricTile("Win Rate", "${"%.1f".format(bt.winRatePct)}%", modifier = Modifier.weight(1f))
                            MetricTile("Profit Factor", "${"%.2f".format(bt.profitFactor)}", MetallicGold, modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("Expectancy", "${"%+.2f".format(bt.expectancyR)}R", if (bt.expectancyR >= 0) EmeraldBullish else DeepRedBearish, modifier = Modifier.weight(1f))
                            MetricTile("Max DD", "${"%.2f".format(bt.maxDrawdownPct)}%", DeepRedBearish, modifier = Modifier.weight(1f))
                            MetricTile("Net Return", "${"%+.2f".format(bt.netReturnPct)}%", if (bt.netReturnPct >= 0) EmeraldBullish else DeepRedBearish, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            val candles = uiState.candlesBySymbol[uiState.selectedInstrument].orEmpty()
            HistoricalRealitySimulatorPanel(
                instrument = uiState.selectedInstrument,
                timeframe = uiState.selectedTimeframe,
                candleCount = candles.size,
                simulation = uiState.historicalSimulation,
                isBusy = uiState.isSimRunning,
                onRunBlindSim = onRunBlindSim,
                onRevealOutcome = onRevealSimOutcome
            )
            GlassPanel {
                SectionHeader("ARCHIVE & PERSISTED SETUP LEDGER", "Candle archive count & setup lifecycle summary")
                Text(
                    "Active Candle Archive (${uiState.selectedInstrument} ${uiState.selectedTimeframe}): ${candles.size} bars loaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrispWhite
                )
                Text(
                    "Persisted Setups: ${setups.size} total | Confirmed: ${setups.count { it.status == "CONFIRMED" }} | Developing: ${setups.count { it.status == "DEVELOPING" }} | Invalidated: ${setups.count { it.status == "INVALIDATED" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolGrey
                )
            }
        }
    }
}

@Composable
fun StrategyArchitectScreen(
    uiState: JaxUiState,
    onCompileStrategy: (String) -> Unit
) {
    var instruction by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "NATURAL LANGUAGE STRATEGY ARCHITECT",
                subtitle = "Convert trading rules into structured, lookahead-safe test specifications",
                badge = "SHADOW MODE"
            )
            OutlinedTextField(
                value = instruction,
                onValueChange = { instruction = it },
                label = { Text("Describe strategy rules (e.g. London session SSL sweep + FVG retest on H1)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onCompileStrategy(instruction) },
                enabled = !uiState.isAiRunning && instruction.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Compile Strategy Specification")
            }
            if (uiState.strategyDraft != null) {
                Text(uiState.strategyDraft, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
            }
        }
    }
}
