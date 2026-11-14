package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.BacktestRunRecord
import com.example.domain.model.TradingSetup
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
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.ObsidianBlack
import com.example.ui.viewmodel.JaxUiState

@Composable
fun NewsAndCalendarScreen(
    isCalendarView: Boolean,
    uiState: JaxUiState,
    onFetchLiveNews: () -> Unit,
    onFetchCalendar: () -> Unit,
    onGenerateDailyBriefing: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = if (isCalendarView) "ECONOMIC CALENDAR & FRED MACRO" else "NEWS INTELLIGENCE CENTRE",
                subtitle = if (isCalendarView) "Finnhub / FMP Economic Calendar & FRED St. Louis Fed Macro Series"
                else "Live Fed, ECB, CoinDesk RSS + Finnhub News & Alpha Vantage Sentiment",
                badge = if (isCalendarView) "${uiState.calendarEvents.size} EVENTS" else "${uiState.newsArticles.size} VERIFIED"
            )
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
                        Text("Sync Fed/ECB RSS")
                    }
                } else {
                    OutlinedButton(onClick = onGenerateDailyBriefing, enabled = !uiState.isAiRunning) {
                        Text("Daily Briefing AI")
                    }
                }
            }
            val errs = if (isCalendarView) uiState.calendarErrors else uiState.newsErrors
            if (errs.isNotEmpty()) {
                Text(
                    "Feed Status: ${errs.joinToString(" | ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarning
                )
            }
        }

        if (isCalendarView) {
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
                        "Tap 'Sync Calendar & FRED' above. Configure FINNHUB_API_KEY, FMP_API_KEY, or FRED_API_KEY in AI Studio Secrets or API Connections to retrieve structured calendar & Fed macro telemetry (no mock releases shown).",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning
                    )
                }
            }
        }

        if (uiState.dailyBriefing != null && !isCalendarView) {
            GlassPanel(goldAccent = true) {
                SectionHeader("APEX DAILY INTELLIGENCE BRIEFING", "Grounded in observed candles & live releases")
                Text(uiState.dailyBriefing, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
            }
        }

        val items = if (isCalendarView) {
            uiState.newsArticles.filter { it.sourceName in listOf("Federal Reserve", "European Central Bank") }
        } else {
            uiState.newsArticles
        }

        if (items.isEmpty() && !isCalendarView) {
            GlassPanel {
                Text("Tap 'Fetch Live News Feeds' to retrieve real-time articles with original source URLs and timestamps.", color = CoolGrey)
            }
        } else {
            items.forEach { art ->
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
    onRunBacktest: (String, Double, Double) -> Unit
) {
    var strategyName by remember { mutableStateOf("EMA Displacement Walk-Forward") }
    var spreadPct by remember { mutableStateOf("0.04") }
    var slippagePct by remember { mutableStateOf("0.02") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isBacktestView) {
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
        } else {
            GlassPanel(goldAccent = true) {
                SectionHeader("HISTORICAL INTELLIGENCE & ARCHIVE", "Candle archive stats & Setup Outcome Expectancy Explorer")
                val candles = uiState.candlesBySymbol[uiState.selectedInstrument].orEmpty()
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
