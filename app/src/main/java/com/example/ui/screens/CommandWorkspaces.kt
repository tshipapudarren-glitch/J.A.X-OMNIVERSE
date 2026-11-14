package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.model.AlertRecord
import com.example.domain.model.ApiConnectionConfig
import com.example.domain.model.ConnectionHealth
import com.example.domain.model.ScannerCriteriaConfig
import com.example.domain.model.TradingSetup
import com.example.domain.model.UserWorkspaceProfile
import com.example.domain.model.WatchlistInstrument
import com.example.domain.model.WorkspaceDestination
import com.example.ui.components.AnimatedMarketRadar
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalCommandCentreScreen(
    uiState: JaxUiState,
    profile: UserWorkspaceProfile?,
    instruments: List<WatchlistInstrument>,
    connections: List<ApiConnectionConfig>,
    setups: List<TradingSetup>,
    alerts: List<AlertRecord>,
    onRunScan: () -> Unit,
    onFetchNews: () -> Unit,
    onGenerateExecutiveBriefing: () -> Unit = {},
    onToggleEmergencyStop: () -> Unit,
    onSelectInstrument: (String) -> Unit,
    onNavigate: (WorkspaceDestination) -> Unit
) {
    val liveCount = uiState.quotes.values.count { it.health == ConnectionHealth.LIVE }
    val developingCount = setups.count { it.status == "DEVELOPING" }
    val confirmedCount = setups.count { it.status == "CONFIRMED" }
    val unackAlerts = alerts.count { !it.acknowledged }
    val leaderboard = uiState.technicalBySymbol.values.sortedByDescending { it.opportunityScore }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "APEX COMMAND CENTRE",
                subtitle = "Real-time multi-market status, opportunity leaderboard & safety controls",
                badge = if (profile?.emergencyStop == true) "EMERGENCY STOP" else (profile?.executionMode ?: "ANALYSIS_ONLY"),
                badgeColor = if (profile?.emergencyStop == true) DeepRedBearish else MetallicGold
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(
                    label = "Live Feeds",
                    value = "$liveCount / ${instruments.count { it.enabled }}",
                    valueColor = if (liveCount > 0) EmeraldBullish else AmberWarning,
                    subLabel = if (uiState.lastScannerDurationMs > 0) "${uiState.lastScannerDurationMs}ms" else "Tap Scan",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Setups (Dev/Conf)",
                    value = "$developingCount / $confirmedCount",
                    valueColor = MetallicGold,
                    subLabel = "${uiState.newsArticles.size} RSS News",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "MT5 Bridge",
                    value = if (uiState.mt5Telemetry.connected) "ONLINE" else "OFFLINE",
                    valueColor = if (uiState.mt5Telemetry.connected) EmeraldBullish else DeepRedBearish,
                    subLabel = "$unackAlerts Active Alerts",
                    modifier = Modifier.weight(1f)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunScan,
                    enabled = !uiState.isScanning,
                    modifier = Modifier.testTag("run_omniscanner_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                ) {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBlack)
                    } else {
                        Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.size(6.dp))
                    Text("Run Omniscanner X")
                }

                OutlinedButton(
                    onClick = onFetchNews,
                    enabled = !uiState.isNewsFetching,
                    modifier = Modifier.testTag("fetch_news_button")
                ) {
                    Icon(Icons.Default.Newspaper, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Sync News Feeds")
                }

                OutlinedButton(
                    onClick = onGenerateExecutiveBriefing,
                    enabled = !uiState.isAiRunning,
                    modifier = Modifier.testTag("executive_briefing_button")
                ) {
                    Text("Executive Briefing AI")
                }

                OutlinedButton(
                    onClick = onToggleEmergencyStop,
                    modifier = Modifier.testTag("emergency_stop_button")
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = DeepRedBearish, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = if (profile?.emergencyStop == true) "Clear Emergency Stop" else "Emergency Stop",
                        color = DeepRedBearish
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Perfect Storm & Divergence" to WorkspaceDestination.OPPORTUNITY_RADAR,
                    "Strategy DNA Mutation" to WorkspaceDestination.BACKTESTING_LAB,
                    "Pattern Fingerprints" to WorkspaceDestination.MARKET_DNA_LAB,
                    "Supreme AI Tribunal" to WorkspaceDestination.MULTI_AI_COUNCIL,
                    "Autonomous Swarm" to WorkspaceDestination.AUTONOMOUS_AGENT_CENTRE,
                    "Central Banks" to WorkspaceDestination.ECONOMIC_CALENDAR,
                    "Reality Simulator" to WorkspaceDestination.HISTORICAL_INTELLIGENCE,
                    "Omniscient Watchtower" to WorkspaceDestination.MARKET_WATCH_TOWER
                ).forEach { (label, dest) ->
                    FilterChip(
                        selected = false,
                        onClick = { onNavigate(dest) },
                        label = { Text(label) }
                    )
                }
            }
        }

        val briefingText = uiState.executiveBriefing ?: uiState.dailyBriefing
        if (!briefingText.isNullOrBlank()) {
            GlassPanel(goldAccent = true) {
                SectionHeader("EXECUTIVE INTELLIGENCE BRIEFING", "Priorities, unfinished missions, news, alerts & actions")
                Text(briefingText, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
            }
        }

        // Market Sessions
        GlassPanel {
            SectionHeader("GLOBAL MARKET SESSIONS", "Active institutional liquidity windows (UTC)")
            TechnicalAnalysisEngine.currentMarketSessions().forEach { sess ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sess.name, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                        Text("${sess.windowUtc} • ${sess.note}", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                    }
                    StatusPill(
                        text = if (sess.isOpen) "OPEN" else "CLOSED",
                        color = if (sess.isOpen) EmeraldBullish else CoolGrey
                    )
                }
            }
        }

        // Opportunity Leaderboard
        GlassPanel {
            SectionHeader(
                title = "GLOBAL OPPORTUNITY LEADERBOARD",
                subtitle = "Evidence-scored instruments from observed candles (no fabricated signals)",
                badge = "${leaderboard.size} SCANNED"
            )
            if (leaderboard.isEmpty()) {
                Text(
                    text = "No instruments scanned yet. Tap 'Run Omniscanner X' above to ingest live Coinbase, Kraken, and ECB FX data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolGrey
                )
            } else {
                leaderboard.forEach { snap ->
                    val quote = uiState.quotes[snap.instrument]
                    val biasColor = when (snap.structureBias) {
                        "BULLISH" -> EmeraldBullish
                        "BEARISH" -> DeepRedBearish
                        else -> AmberWarning
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectInstrument(snap.instrument)
                                onNavigate(WorkspaceDestination.CHART_INTELLIGENCE)
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(snap.instrument, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                                StatusPill(snap.structureBias, biasColor)
                            }
                            Text(
                                text = "${snap.regime.label} • RSI ${"%.1f".format(snap.rsi14)} • ${snap.liquiditySweepType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CoolGrey
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${snap.opportunityScore}/100",
                                style = MaterialTheme.typography.titleMedium,
                                color = MetallicGold
                            )
                            Text(
                                text = quote?.let { TechnicalAnalysisEngine.fmt(it.lastPrice) } ?: "--",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoolGrey
                            )
                        }
                    }
                }
            }
        }

        // Data Provider Health Summary
        GlassPanel {
            SectionHeader("INTEGRATION & DATA HEALTH", "Real endpoint status across configured services")
            connections.forEach { conn ->
                val color = when (conn.status) {
                    "CONNECTED" -> EmeraldBullish
                    "ERROR" -> DeepRedBearish
                    else -> AmberWarning
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(conn.providerName, style = MaterialTheme.typography.bodyMedium, color = CrispWhite)
                        Text(
                            text = if (conn.lastError.isNotBlank()) conn.lastError else "${conn.category} • ${conn.lastLatencyMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoolGrey
                        )
                    }
                    StatusPill(conn.status, color)
                }
            }
        }
    }
}

@Composable
fun MarketOverviewScreen(
    uiState: JaxUiState,
    instruments: List<WatchlistInstrument>,
    onToggleInstrument: (WatchlistInstrument) -> Unit,
    onAddInstrument: (String, String, String, String, String) -> Unit,
    onRefreshAll: () -> Unit,
    onSelectAndChart: (String) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var providerId by remember { mutableStateOf("coinbase_public") }
    var providerSym by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader("MARKET OVERVIEW & WATCHLIST", "Configure instruments & inspect live bid/ask/spread telemetry")
            Button(
                onClick = onRefreshAll,
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Refresh Live Market Quotes")
            }

            instruments.forEach { inst ->
                val quote = uiState.quotes[inst.symbol]
                val healthColor = when (quote?.health) {
                    ConnectionHealth.LIVE -> EmeraldBullish
                    ConnectionHealth.ERROR -> DeepRedBearish
                    else -> CoolGrey
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectAndChart(inst.symbol) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(inst.symbol, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            StatusPill(quote?.health?.name ?: "IDLE", healthColor)
                        }
                        Text(
                            text = "${inst.displayName} (${inst.providerId})",
                            style = MaterialTheme.typography.bodySmall,
                            color = CoolGrey
                        )
                        if (quote != null && quote.health == ConnectionHealth.LIVE) {
                            val chgColor = if (quote.change24hPct >= 0) EmeraldBullish else DeepRedBearish
                            Text(
                                text = "Last ${TechnicalAnalysisEngine.fmt(quote.lastPrice)} (${"%+.2f".format(quote.change24hPct)}%) • Spread ${"%.3f".format(quote.spreadPct)}% • ${quote.latencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = chgColor
                            )
                        } else if (quote?.errorMessage != null) {
                            Text(quote.errorMessage, style = MaterialTheme.typography.labelSmall, color = DeepRedBearish)
                        }
                    }
                    Switch(checked = inst.enabled, onCheckedChange = { onToggleInstrument(inst) })
                }
            }
        }

        GlassPanel {
            SectionHeader("ADD CUSTOM INSTRUMENT", "Supports Binance, Yahoo, Finnhub, FMP, Alpha Vantage, FRED, TwelveData, Coinbase, Kraken")
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it },
                label = { Text("Display Symbol (e.g. AAPL, BTC-USDT, DGS10)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Instrument Name (e.g. Apple Inc. or US 10Y Yield)") },
                modifier = Modifier.fillMaxWidth()
            )
            val providerOptions = listOf(
                "coinbase_public" to "Coinbase",
                "binance" to "Binance",
                "yahoo" to "Yahoo",
                "finnhub" to "Finnhub",
                "fmp" to "FMP",
                "alpha_vantage" to "AlphaVantage",
                "fred" to "FRED",
                "twelvedata" to "TwelveData",
                "kraken_public" to "Kraken"
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                providerOptions.forEach { (id, label) ->
                    FilterChip(
                        selected = providerId == id,
                        onClick = { providerId = id },
                        label = { Text(label) }
                    )
                }
            }
            OutlinedTextField(
                value = providerSym,
                onValueChange = { providerSym = it },
                label = { Text("Provider Symbol (e.g. AAPL, BTCUSDT, DGS10, GC=F)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (symbol.isNotBlank()) {
                        val inferredAsset = when (providerId) {
                            "frankfurter_ecb" -> "FOREX"
                            "fred" -> "INDEX"
                            "yahoo", "finnhub", "fmp", "alpha_vantage", "twelvedata" -> "EQUITY"
                            else -> "CRYPTO"
                        }
                        onAddInstrument(
                            symbol.uppercase(),
                            name.ifBlank { symbol.uppercase() },
                            inferredAsset,
                            providerId,
                            providerSym.ifBlank { symbol.uppercase() }
                        )
                        symbol = ""
                        name = ""
                        providerSym = ""
                    }
                },
                modifier = Modifier.testTag("add_instrument_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Add to Watchlist")
            }
        }
    }
}

@Composable
fun OpportunityRadarAndScannerScreen(
    isRadarView: Boolean,
    uiState: JaxUiState,
    onSelectTimeframe: (String) -> Unit,
    onRunScan: () -> Unit,
    onSelectInstrument: (String) -> Unit,
    onGenerateSetup: () -> Unit,
    onUpdateScannerCriteria: (ScannerCriteriaConfig) -> Unit = {}
) {
    var radarTab by remember { mutableStateOf(0) } // 0 = Radar/Arena, 1 = Perfect Storm & Sentiment Divergence
    val criteria = uiState.scannerCriteria
    val snapshots = uiState.technicalBySymbol.values
        .filter { snap ->
            if (isRadarView) true
            else {
                val q = uiState.quotes[snap.instrument]
                snap.opportunityScore >= criteria.minConfluenceScore &&
                    snap.volatilityRatio >= criteria.minVolatilityRatio &&
                    (criteria.trendBiasFilter == "ALL" || snap.structureBias == criteria.trendBiasFilter) &&
                    ((q?.spreadPct ?: 0.0) <= criteria.maxAllowedSpreadPct)
            }
        }
        .sortedByDescending { it.opportunityScore }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = if (isRadarView) "OPPORTUNITY RADAR & PERFECT STORM" else "GLOBAL OPPORTUNITY SCANNER",
                subtitle = if (isRadarView) "Rare multi-evidence convergence & global sentiment conflict detection"
                else "Configurable confluence, volatility, trend & risk filter across live feeds",
                badge = uiState.selectedTimeframe
            )
            if (isRadarView) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = radarTab == 0, onClick = { radarTab = 0 }, label = { Text("Opportunity Arena") })
                    FilterChip(selected = radarTab == 1, onClick = { radarTab = 1 }, label = { Text("Perfect Storm & Divergence") })
                }
                if (radarTab == 0) {
                    AnimatedMarketRadar(snapshots = snapshots)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("15M", "1H", "4H", "1D").forEach { tf ->
                    FilterChip(
                        selected = uiState.selectedTimeframe == tf,
                        onClick = { onSelectTimeframe(tf) },
                        label = { Text(tf) }
                    )
                }
            }
            Button(
                onClick = onRunScan,
                enabled = !uiState.isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Scan All Enabled Markets (${uiState.selectedTimeframe})")
            }
        }

        if (isRadarView && radarTab == 1) {
            SentimentDivergenceAndStormPanel(
                divergences = uiState.sentimentDivergences,
                storms = uiState.perfectStormSignals,
                onScanAll = onRunScan
            )
            return
        }

        if (!isRadarView) {
            ConfigurableScannerCriteriaBar(
                criteria = criteria,
                onUpdateCriteria = onUpdateScannerCriteria
            )
        }

        if (snapshots.isEmpty()) {
            GlassPanel {
                Text(
                    "No scanner snapshots match active filters or no scan has run yet.",
                    color = CoolGrey
                )
            }
        } else {
            snapshots.forEach { snap ->
                val color = when (snap.structureBias) {
                    "BULLISH" -> EmeraldBullish
                    "BEARISH" -> DeepRedBearish
                    else -> AmberWarning
                }
                GlassPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${snap.instrument} (${snap.timeframe})", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            Text("Regime: ${snap.regime.label}", style = MaterialTheme.typography.bodySmall, color = CyanInfo)
                        }
                        StatusPill("SCORE ${snap.opportunityScore}", color)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Close", TechnicalAnalysisEngine.fmt(snap.lastClose), modifier = Modifier.weight(1f))
                        MetricTile("ATR(14)", TechnicalAnalysisEngine.fmt(snap.atr14), modifier = Modifier.weight(1f))
                        MetricTile("RSI(14)", "%.1f".format(snap.rsi14), modifier = Modifier.weight(1f))
                    }
                    snap.evidencePoints.forEach { pt ->
                        Text("• $pt", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            onSelectInstrument(snap.instrument)
                            onGenerateSetup()
                        }) {
                            Text("Generate Conditional Setup")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchTowerScreen(
    uiState: JaxUiState,
    alerts: List<AlertRecord>,
    onToggleGhostMonitor: () -> Unit,
    onRunScan: () -> Unit,
    onRunAnomalySweep: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "OMNISCIENT WATCHTOWER (24/7)",
                subtitle = "Monitors connected APIs, AI providers, feeds, workflows & prioritized anomalies",
                badge = if (uiState.ghostMonitorActive) "GHOST ACTIVE" else "MANUAL",
                badgeColor = if (uiState.ghostMonitorActive) EmeraldBullish else CoolGrey
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("APEX Ghost Monitor (45s Cycle)", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                    Text("Background multi-market polling while terminal is open", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                }
                Switch(checked = uiState.ghostMonitorActive, onCheckedChange = { onToggleGhostMonitor() })
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onRunScan,
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Text("Trigger Immediate Watch Sweep")
            }
        }

        OmniscientWatchtowerPanel(
            anomalies = uiState.watchtowerAnomalies,
            onRunSweep = onRunAnomalySweep
        )

        GlassPanel {
            SectionHeader("VOLATILITY & SHOCK MONITOR", "Instruments with ATR expansion > 1.25x baseline")
            val shocks = uiState.technicalBySymbol.values.filter { it.volatilityRatio >= 1.25 }
            if (shocks.isEmpty()) {
                Text("No volatility shock anomalies detected across scanned instruments.", color = CoolGrey)
            } else {
                shocks.forEach { s ->
                    Text(
                        "${s.instrument}: Volatility ${"%.2f".format(s.volatilityRatio)}x normal (${s.regime.label})",
                        color = AmberWarning,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        GlassPanel {
            SectionHeader("RECENT WATCH TOWER EVENTS", "Live system and scanner notifications")
            alerts.take(8).forEach { alert ->
                Text("[${alert.priority}] ${alert.title} — ${alert.message}", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
            }
            if (alerts.isEmpty()) {
                Text("No Watch Tower alerts recorded yet.", color = CoolGrey)
            }
        }
    }
}
