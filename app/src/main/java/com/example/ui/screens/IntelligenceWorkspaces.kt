package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.model.AiInvestigationReport
import com.example.domain.model.TradingSetup
import com.example.domain.model.WatchlistInstrument
import com.example.ui.components.GlassPanel
import com.example.ui.components.InstitutionalCandlestickChart
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
fun SetupGeneratorScreen(
    uiState: JaxUiState,
    instruments: List<WatchlistInstrument>,
    setups: List<TradingSetup>,
    onSelectInstrument: (String) -> Unit,
    onGenerateSetup: () -> Unit,
    onUpdateSetupStatus: (TradingSetup, String) -> Unit,
    onQueueToRiskGate: (TradingSetup) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "APEX SETUP GENERATOR & ENTRY LAB",
                subtitle = "Distinguishes DEVELOPING conditional plans from CONFIRMED setups",
                badge = uiState.selectedInstrument
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                instruments.filter { it.enabled }.forEach { inst ->
                    FilterChip(
                        selected = uiState.selectedInstrument == inst.symbol,
                        onClick = { onSelectInstrument(inst.symbol) },
                        label = { Text(inst.symbol) }
                    )
                }
            }
            Button(
                onClick = onGenerateSetup,
                modifier = Modifier.testTag("generate_setup_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Text("Generate Setup from Live ${uiState.selectedTimeframe} Data")
            }
        }

        if (setups.isEmpty()) {
            GlassPanel {
                Text(
                    "No setups persisted yet. Scan live market data and tap Generate Setup above.",
                    color = CoolGrey
                )
            }
        } else {
            setups.forEach { s ->
                val dirColor = if (s.direction == "BULLISH") EmeraldBullish else DeepRedBearish
                val statusColor = when (s.status) {
                    "CONFIRMED" -> EmeraldBullish
                    "DEVELOPING" -> AmberWarning
                    "INVALIDATED" -> DeepRedBearish
                    else -> CoolGrey
                }
                GlassPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${s.instrument} • ${s.setupType} (v${s.version})", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            Text("${s.timeframe} | HTF Bias: ${s.higherTimeframeBias} | Quality: ${s.dataQualityStatus}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(s.direction, dirColor)
                            StatusPill(s.status, statusColor)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Entry Zone", "${TechnicalAnalysisEngine.fmt(s.entryZoneLow)} - ${TechnicalAnalysisEngine.fmt(s.entryZoneHigh)}", MetallicGold, modifier = Modifier.weight(1.3f))
                        MetricTile("Stop Loss", TechnicalAnalysisEngine.fmt(s.stopLoss), DeepRedBearish, modifier = Modifier.weight(1f))
                        MetricTile("Score / R:R", "${s.score}/100", EmeraldBullish, "1 : ${"%.2f".format(s.rr1)}", modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("TP1", TechnicalAnalysisEngine.fmt(s.tp1), EmeraldBullish, modifier = Modifier.weight(1f))
                        MetricTile("TP2", TechnicalAnalysisEngine.fmt(s.tp2), EmeraldBullish, modifier = Modifier.weight(1f))
                        MetricTile("TP3", TechnicalAnalysisEngine.fmt(s.tp3), EmeraldBullish, modifier = Modifier.weight(1f))
                    }

                    Text("Evidence: ${s.evidenceSummary}", style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                    Text("Confirm: ${s.confirmationConditions}", style = MaterialTheme.typography.bodySmall, color = CyanInfo)
                    Text("Invalidate: ${s.invalidationConditions}", style = MaterialTheme.typography.bodySmall, color = DeepRedBearish)
                    Text("News Exposure: ${s.newsExposure}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.status == "DEVELOPING") {
                            OutlinedButton(onClick = { onUpdateSetupStatus(s, "CONFIRMED") }) {
                                Text("Confirm Setup")
                            }
                        }
                        if (s.status in listOf("DEVELOPING", "CONFIRMED")) {
                            OutlinedButton(onClick = { onUpdateSetupStatus(s, "INVALIDATED") }) {
                                Text("Invalidate", color = DeepRedBearish)
                            }
                            Button(
                                onClick = { onQueueToRiskGate(s) },
                                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                            ) {
                                Text("Evaluate in Risk Gate")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChartIntelligenceScreen(
    uiState: JaxUiState,
    instruments: List<WatchlistInstrument>,
    onSelectInstrument: (String) -> Unit,
    onSelectTimeframe: (String) -> Unit,
    onAddAnnotation: (String, Double) -> Unit,
    onRemoveAnnotation: (String) -> Unit,
    onAnalyzeScreenshot: (android.graphics.Bitmap) -> Unit
) {
    val context = LocalContext.current
    var levelLabel by remember { mutableStateOf("Custom S/R") }
    var levelPrice by remember { mutableStateOf("") }

    // Zero-permission Android Photo Picker per Google Play Policy
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) onAnalyzeScreenshot(bmp)
            }
        }
    }

    val candles = uiState.candlesBySymbol[uiState.selectedInstrument].orEmpty()
    val tech = uiState.technicalBySymbol[uiState.selectedInstrument]

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "CHART INTELLIGENCE & VISION AI",
                subtitle = "Live OHLC candlesticks, editable levels & static screenshot audit",
                badge = "${uiState.selectedInstrument} ${uiState.selectedTimeframe}"
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                instruments.filter { it.enabled }.forEach { inst ->
                    FilterChip(
                        selected = uiState.selectedInstrument == inst.symbol,
                        onClick = { onSelectInstrument(inst.symbol) },
                        label = { Text(inst.symbol) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("15M", "1H", "4H", "1D").forEach { tf ->
                    FilterChip(
                        selected = uiState.selectedTimeframe == tf,
                        onClick = { onSelectTimeframe(tf) },
                        label = { Text(tf) }
                    )
                }
            }

            InstitutionalCandlestickChart(
                candles = candles,
                annotations = uiState.annotations
            )

            if (tech != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("EMA20", TechnicalAnalysisEngine.fmt(tech.ema20), modifier = Modifier.weight(1f))
                    MetricTile("EMA50", TechnicalAnalysisEngine.fmt(tech.ema50), modifier = Modifier.weight(1f))
                    MetricTile("Regime", tech.regime.name, CyanInfo, modifier = Modifier.weight(1.2f))
                }
            }
        }

        GlassPanel {
            SectionHeader("EDITABLE CHART ANNOTATIONS", "Linked to calculated structure & setup levels")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = levelLabel,
                    onValueChange = { levelLabel = it },
                    label = { Text("Label") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = levelPrice,
                    onValueChange = { levelPrice = it },
                    label = { Text("Price") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val p = levelPrice.toDoubleOrNull()
                    if (p != null && p > 0.0) {
                        onAddAnnotation(levelLabel, p)
                        levelPrice = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Annotation", tint = MetallicGold)
                }
            }

            uiState.annotations.forEach { ann ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${ann.label} (${ann.type}): ${TechnicalAnalysisEngine.fmt(ann.priceStart)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrispWhite
                    )
                    if (ann.isEditable) {
                        IconButton(onClick = { onRemoveAnnotation(ann.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Level", tint = CoolGrey)
                        }
                    }
                }
            }
        }

        GlassPanel {
            SectionHeader("CHART VISION AI (UPLOADED SCREENSHOT)", "Separates visible price action from unavailable live tick/volume data")
            Button(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !uiState.isAiRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Select Chart Screenshot via Photo Picker")
            }
            if (uiState.visionAnalysis != null) {
                Text(uiState.visionAnalysis, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
            }
        }
    }
}

@Composable
fun MultiAiCouncilAndBureauScreen(
    isBureauMode: Boolean,
    uiState: JaxUiState,
    instruments: List<WatchlistInstrument>,
    investigations: List<AiInvestigationReport>,
    onSelectInstrument: (String) -> Unit,
    onRunCouncil: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = if (isBureauMode) "AI INVESTIGATION BUREAU" else "MULTI-AI COUNCIL & DEBATE CHAMBER",
                subtitle = if (isBureauMode) "Signal forensics, evidence verification & contradiction detection"
                else "12 Specialist AI Agents + Chief Analyst preserving real disagreements",
                badge = "FIREBASE AI LOGIC"
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                instruments.filter { it.enabled }.forEach { inst ->
                    FilterChip(
                        selected = uiState.selectedInstrument == inst.symbol,
                        onClick = { onSelectInstrument(inst.symbol) },
                        label = { Text(inst.symbol) }
                    )
                }
            }
            Button(
                onClick = onRunCouncil,
                enabled = !uiState.isAiRunning,
                modifier = Modifier.testTag("run_ai_council_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                if (uiState.isAiRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBlack)
                } else {
                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text("Convene Multi-AI Debate (${uiState.selectedInstrument})")
            }
        }

        GlassPanel {
            SectionHeader("12 SPECIALIST AGENT ROSTER", "Intelligent Model Router across Gemini 3.8 Flash & 3.5 Flash-Lite")
            uiState.agentRoster.forEach { agent ->
                val stateColor = when (agent.state) {
                    "COMPLETED" -> EmeraldBullish
                    "RUNNING" -> MetallicGold
                    "ERROR" -> DeepRedBearish
                    else -> CoolGrey
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(agent.name, style = MaterialTheme.typography.bodyMedium, color = CrispWhite)
                        Text("${agent.domain} • ${agent.assignedModel}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                    }
                    StatusPill(agent.state, stateColor)
                }
            }
        }

        investigations.forEach { rep ->
            GlassPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${rep.instrument} (${rep.timeframe}) • ${rep.regime}", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                    StatusPill(
                        text = if (rep.verified) "VERIFIED ${rep.confidenceScore}%" else "UNVERIFIED",
                        color = if (rep.verified) EmeraldBullish else DeepRedBearish
                    )
                }
                Text("Chief Analyst Verdict: ${rep.chiefAnalystVerdict}", style = MaterialTheme.typography.bodyMedium, color = MetallicGold)
                Text("Disagreements Preserved: ${rep.disagreements}", style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                Text("Bullish Case: ${rep.bullishArgument}", style = MaterialTheme.typography.bodySmall, color = EmeraldBullish)
                Text("Bearish Case: ${rep.bearishArgument}", style = MaterialTheme.typography.bodySmall, color = DeepRedBearish)
                Text("Liquidity AI: ${rep.liquidityInterpretation}", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                Text("Risk AI: ${rep.riskAssessment}", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                Text("Verification AI: ${rep.verificationReport}", style = MaterialTheme.typography.bodySmall, color = CyanInfo)
                Text(rep.modelRouterInfo, style = MaterialTheme.typography.labelSmall, color = CoolGrey)
            }
        }
    }
}

@Composable
fun LiquidityAndDnaScreen(
    isLiquidityView: Boolean,
    uiState: JaxUiState
) {
    val sym = uiState.selectedInstrument
    val tech = uiState.technicalBySymbol[sym]
    val analogues = uiState.analoguesBySymbol[sym].orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = if (isLiquidityView) "LIQUIDITY WARFARE & DESTINATIONS" else "MARKET DNA LABORATORY",
                subtitle = if (isLiquidityView) "Equal highs/lows, stop runs, FVG & Order Block mapping"
                else "Cosine similarity matching against historical candle windows",
                badge = sym
            )
            if (isLiquidityView) {
                if (tech == null) {
                    Text("Run Omniscanner X to compute liquidity pools for $sym.", color = CoolGrey)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Swing High (BSL)", TechnicalAnalysisEngine.fmt(tech.swingHigh), AmberWarning, modifier = Modifier.weight(1f))
                        MetricTile("Swing Low (SSL)", TechnicalAnalysisEngine.fmt(tech.swingLow), CyanInfo, modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Equal High Pool", tech.equalHighPool?.let { TechnicalAnalysisEngine.fmt(it) } ?: "None", modifier = Modifier.weight(1f))
                        MetricTile("Equal Low Pool", tech.equalLowPool?.let { TechnicalAnalysisEngine.fmt(it) } ?: "None", modifier = Modifier.weight(1f))
                    }
                    Text("Sweep Detector: ${tech.liquiditySweepType}", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                    tech.bullishFvgZone?.let {
                        Text("Bullish Fair Value Gap: ${TechnicalAnalysisEngine.fmt(it.first)} - ${TechnicalAnalysisEngine.fmt(it.second)}", color = EmeraldBullish)
                    }
                    tech.bearishFvgZone?.let {
                        Text("Bearish Fair Value Gap: ${TechnicalAnalysisEngine.fmt(it.first)} - ${TechnicalAnalysisEngine.fmt(it.second)}", color = DeepRedBearish)
                    }
                    tech.orderBlockZone?.let {
                        Text("Active Order Block: ${TechnicalAnalysisEngine.fmt(it.first)} - ${TechnicalAnalysisEngine.fmt(it.second)}", color = CrispWhite)
                    }
                }
            } else {
                if (analogues.isEmpty()) {
                    Text("Fetch live candles (40+ bars) via Omniscanner X to compare live Market DNA against historical windows.", color = CoolGrey)
                } else {
                    analogues.forEach { a ->
                        val retColor = if (a.subsequent10BarReturnPct >= 0) EmeraldBullish else DeepRedBearish
                        GlassPanel {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Similarity: ${"%.1f".format(a.similarityPct)}%", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                                StatusPill(a.historicalRegime, retColor)
                            }
                            Text(
                                "10-Bar Outcome: ${"%+.2f".format(a.subsequent10BarReturnPct)}% | MFE: +${"%.2f".format(a.maxFavorableExcursionPct)}% | MAE: -${"%.2f".format(a.maxAdverseExcursionPct)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = CrispWhite
                            )
                        }
                    }
                }
            }
        }
    }
}
