package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.engine.RiskEngine
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.model.AlertRecord
import com.example.domain.model.ApiConnectionConfig
import com.example.domain.model.AuditLogRecord
import com.example.domain.model.ExecutionMode
import com.example.domain.model.ExecutionOrderRecord
import com.example.domain.model.UserWorkspaceProfile
import com.example.domain.model.WatchlistInstrument
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
fun RiskAndExecutionScreen(
    isExecutionView: Boolean,
    uiState: JaxUiState,
    profile: UserWorkspaceProfile?,
    instruments: List<WatchlistInstrument>,
    orders: List<ExecutionOrderRecord>,
    onUpdatePolicy: (String, Boolean, Double, Double, Double, Long) -> Unit,
    onEvaluateAndQueueOrder: (String, String, Double, Double, Double, Double) -> Unit,
    onApproveOrCancelOrder: (ExecutionOrderRecord, Boolean) -> Unit
) {
    var balanceText by remember(profile) { mutableStateOf((profile?.accountBalance ?: 100000.0).toString()) }
    var riskPctText by remember(profile) { mutableStateOf((profile?.riskPerTradePct ?: 1.0).toString()) }
    var maxDailyLossText by remember(profile) { mutableStateOf((profile?.maxDailyLossPct ?: 4.0).toString()) }
    var maxPosText by remember(profile) { mutableStateOf((profile?.maxConcurrentPositions ?: 3L).toString()) }
    var selectedMode by remember(profile) { mutableStateOf(profile?.executionMode ?: "ANALYSIS_ONLY") }

    val selectedQuote = uiState.quotes[uiState.selectedInstrument]
    var orderDirection by remember { mutableStateOf("BUY") }
    var entryText by remember(selectedQuote) { mutableStateOf((selectedQuote?.lastPrice ?: 0.0).let { if (it > 0) it.toString() else "" }) }
    var slText by remember { mutableStateOf("") }
    var tpText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = "SOVEREIGN RISK GUARDIAN",
                subtitle = "Deterministic account protection strictly isolated from AI Council, Swarm & Voice",
                badge = if (profile?.emergencyStop == true) "LOCKED — EMERGENCY STOP" else "GUARDIAN ARMED",
                badgeColor = if (profile?.emergencyStop == true) DeepRedBearish else EmeraldBullish
            )
            Text(
                "Max Risk/Trade ${riskPctText}% | Max Daily DD ${maxDailyLossText}% | Max Open ${maxPosText} | Execution Permission: $selectedMode",
                style = MaterialTheme.typography.labelMedium,
                color = CyanInfo
            )
        }
        GlassPanel(goldAccent = true) {
            SectionHeader(
                title = if (isExecutionView) "EXECUTION INTELLIGENCE & PERMISSIONS" else "RISK COMMAND CENTRE",
                subtitle = "Deterministic Risk Guardian (cannot be bypassed by AI or Voice)",
                badge = if (profile?.emergencyStop == true) "EMERGENCY STOP" else selectedMode,
                badgeColor = if (profile?.emergencyStop == true) DeepRedBearish else MetallicGold
            )

            if (isExecutionView) {
                Text("Execution Permission Mode:", style = MaterialTheme.typography.labelMedium, color = CrispWhite)
                ExecutionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode.wireValue,
                        onClick = { selectedMode = mode.wireValue },
                        label = { Text(mode.displayLabel) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Balance ($)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = riskPctText,
                    onValueChange = { riskPctText = it },
                    label = { Text("Risk/Trade %") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = maxDailyLossText,
                    onValueChange = { maxDailyLossText = it },
                    label = { Text("Max Daily DD %") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxPosText,
                    onValueChange = { maxPosText = it },
                    label = { Text("Max Positions") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onUpdatePolicy(
                            selectedMode,
                            profile?.emergencyStop ?: false,
                            balanceText.toDoubleOrNull() ?: 100000.0,
                            riskPctText.toDoubleOrNull() ?: 1.0,
                            maxDailyLossText.toDoubleOrNull() ?: 4.0,
                            maxPosText.toLongOrNull() ?: 3L
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                ) {
                    Text("Save Policy")
                }
                OutlinedButton(onClick = {
                    val nextStop = !(profile?.emergencyStop ?: false)
                    onUpdatePolicy(
                        if (nextStop) "ANALYSIS_ONLY" else selectedMode,
                        nextStop,
                        balanceText.toDoubleOrNull() ?: 100000.0,
                        riskPctText.toDoubleOrNull() ?: 1.0,
                        maxDailyLossText.toDoubleOrNull() ?: 4.0,
                        maxPosText.toLongOrNull() ?: 3L
                    )
                }) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = DeepRedBearish, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(if (profile?.emergencyStop == true) "Clear Stop" else "Global Emergency Stop", color = DeepRedBearish)
                }
            }
        }

        // Order Gate & Position Sizing Calculator
        GlassPanel {
            SectionHeader("ORDER RISK GATE & POSITION SIZER", "Instrument: ${uiState.selectedInstrument}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BUY", "SELL").forEach { dir ->
                    FilterChip(
                        selected = orderDirection == dir,
                        onClick = { orderDirection = dir },
                        label = { Text(dir) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = entryText, onValueChange = { entryText = it }, label = { Text("Entry") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = slText, onValueChange = { slText = it }, label = { Text("Stop Loss") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = tpText, onValueChange = { tpText = it }, label = { Text("Take Profit") }, modifier = Modifier.weight(1f))
            }

            val e = entryText.toDoubleOrNull() ?: 0.0
            val s = slText.toDoubleOrNull() ?: 0.0
            val bal = balanceText.toDoubleOrNull() ?: 100000.0
            val rPct = riskPctText.toDoubleOrNull() ?: 1.0
            val (riskUsd, lots) = RiskEngine.calculatePositionSize(bal, rPct, e, s)
            Text(
                text = "Deterministic Sizing: Risk $${"%.2f".format(riskUsd)} | Calculated Size = ${"%.4f".format(lots)} units/lots",
                style = MaterialTheme.typography.labelLarge,
                color = MetallicGold
            )

            Button(
                onClick = {
                    val tp = tpText.toDoubleOrNull() ?: e
                    if (e > 0 && s > 0 && tp > 0) {
                        onEvaluateAndQueueOrder(uiState.selectedInstrument, orderDirection, e, s, tp, rPct)
                    }
                },
                modifier = Modifier.testTag("evaluate_order_gate_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
            ) {
                Text("Run 9-Gate Risk & Permission Check")
            }

            uiState.lastRiskEvaluation?.let { eval ->
                eval.gateResults.forEach { (gateName, ok) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(gateName, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                        StatusPill(if (ok) "PASS" else "BLOCK", if (ok) EmeraldBullish else DeepRedBearish)
                    }
                }
            }
        }

        if (isExecutionView) {
            GlassPanel {
                SectionHeader("EXECUTION APPROVAL QUEUE & LOGS", "Manual Approval required in Mode 2")
                if (orders.isEmpty()) {
                    Text("No order intents queued yet.", color = CoolGrey)
                } else {
                    orders.forEach { ord ->
                        val stColor = when (ord.status) {
                            "TRANSMITTED", "APPROVED" -> EmeraldBullish
                            "PENDING_APPROVAL" -> AmberWarning
                            else -> DeepRedBearish
                        }
                        GlassPanel {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${ord.instrument} ${ord.direction} ${"%.2f".format(ord.volumeLots)}", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                                StatusPill(ord.status, stColor)
                            }
                            Text(
                                "Entry ${TechnicalAnalysisEngine.fmt(ord.entryPrice)} | SL ${TechnicalAnalysisEngine.fmt(ord.stopLoss)} | TP ${TechnicalAnalysisEngine.fmt(ord.takeProfit)} | Mode ${ord.executionMode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CoolGrey
                            )
                            if (ord.rejectionReason.isNotBlank()) {
                                Text(ord.rejectionReason, style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                            }
                            if (ord.status == "PENDING_APPROVAL") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onApproveOrCancelOrder(ord, true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldBullish, contentColor = ObsidianBlack)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("Approve Order")
                                    }
                                    OutlinedButton(onClick = { onApproveOrCancelOrder(ord, false) }) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, tint = DeepRedBearish, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("Cancel", color = DeepRedBearish)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApiConnectionsAndMt5Screen(
    isMt5View: Boolean,
    uiState: JaxUiState,
    connections: List<ApiConnectionConfig>,
    onTestConnection: (ApiConnectionConfig) -> Unit,
    onSaveConnection: (String, String, String, String, String) -> Unit,
    onPollMt5: (String?) -> Unit
) {
    val existingMt5 = connections.firstOrNull { it.id == "mt5_bridge" }
    var mt5Url by remember(existingMt5) { mutableStateOf(existingMt5?.baseUrl.orEmpty()) }
    var mt5Secret by remember { mutableStateOf("") }

    var customId by remember { mutableStateOf("finnhub") }
    var customName by remember { mutableStateOf("Finnhub Equities & Calendar") }
    var customCategory by remember { mutableStateOf("CALENDAR") }
    var customUrl by remember { mutableStateOf("https://finnhub.io/api/v1") }
    var customKey by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isMt5View) {
            val mt5 = uiState.mt5Telemetry
            GlassPanel(goldAccent = true) {
                SectionHeader(
                    title = "MT5 CONTROL CENTRE",
                    subtitle = "Authenticated HMAC-SHA256 bridge with unique nonce replay protection",
                    badge = if (mt5.connected) "CONNECTED" else "DISCONNECTED",
                    badgeColor = if (mt5.connected) EmeraldBullish else DeepRedBearish
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Account", mt5.maskedAccountId, modifier = Modifier.weight(1f))
                    MetricTile("Balance", "$${"%.2f".format(mt5.balance)}", modifier = Modifier.weight(1f))
                    MetricTile("Equity", "$${"%.2f".format(mt5.equity)}", MetallicGold, modifier = Modifier.weight(1f))
                }
                Text("Broker: ${mt5.broker} | Server: ${mt5.server} | EA Status: ${mt5.eaStatus}", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                if (!mt5.lastError.isNullOrBlank()) {
                    Text("Bridge Status: ${mt5.lastError}", style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                }

                OutlinedTextField(
                    value = mt5Url,
                    onValueChange = { mt5Url = it },
                    label = { Text("MT5 Bridge HTTPS URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mt5Secret,
                    onValueChange = { mt5Secret = it },
                    label = { Text("Bridge HMAC Secret (Stored in Android Keystore)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveConnection("mt5_bridge", "MetaTrader 5 Bridge EA", "MT5_BRIDGE", mt5Url, mt5Secret)
                            mt5Secret = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                    ) {
                        Text("Save & Connect MT5")
                    }
                    OutlinedButton(onClick = { onPollMt5(mt5Url) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Check Heartbeat")
                    }
                }
            }
        } else {
            GlassPanel(goldAccent = true) {
                SectionHeader(
                    title = "API & DATA CONNECTION CENTRE",
                    subtitle = "Supports AI Studio Secrets (BuildConfig) & Android Keystore override vault"
                )
                connections.forEach { conn ->
                    val statusColor = when (conn.status) {
                        "CONNECTED" -> EmeraldBullish
                        "ERROR", "RATE_LIMITED" -> DeepRedBearish
                        else -> AmberWarning
                    }
                    GlassPanel {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conn.providerName, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                                Text("${conn.category} • ${conn.baseUrl.ifBlank { "No URL" }}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                                Text("Latency: ${conn.lastLatencyMs}ms • Key Configured: ${conn.credentialConfigured} (${conn.rateLimitInfo})", style = MaterialTheme.typography.labelSmall, color = CyanInfo)
                                if (conn.lastError.isNotBlank()) {
                                    Text(conn.lastError, style = MaterialTheme.typography.labelSmall, color = DeepRedBearish)
                                }
                            }
                            StatusPill(conn.status, statusColor)
                        }
                        OutlinedButton(onClick = { onTestConnection(conn) }) {
                            Text("Test Live Endpoint")
                        }
                    }
                }
            }

            GlassPanel {
                SectionHeader("CONFIGURE PROVIDER API KEY", "Overrides BuildConfig Secrets locally using Android Keystore AES-256-GCM")
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        Triple("finnhub", "Finnhub Equities & Calendar", "CALENDAR" to "https://finnhub.io/api/v1"),
                        Triple("fmp", "Financial Modeling Prep (FMP)", "CALENDAR" to "https://financialmodelingprep.com"),
                        Triple("alpha_vantage", "Alpha Vantage Market & Sentiment", "NEWS" to "https://www.alphavantage.co"),
                        Triple("fred", "FRED — St. Louis Fed Macro", "CALENDAR" to "https://api.stlouisfed.org/fred"),
                        Triple("yahoo", "Yahoo Finance Market Feed", "MARKET_DATA" to "https://query1.finance.yahoo.com"),
                        Triple("binance", "Binance Spot & Account API", "MARKET_DATA" to "https://api.binance.com"),
                        Triple("binance_secret", "Binance HMAC Secret", "MARKET_DATA" to "https://api.binance.com"),
                        Triple("twelvedata", "TwelveData Market API", "MARKET_DATA" to "https://api.twelvedata.com")
                    )
                    presets.forEach { (id, name, catUrl) ->
                        FilterChip(
                            selected = customId == id,
                            onClick = {
                                customId = id
                                customName = name
                                customCategory = catUrl.first
                                customUrl = catUrl.second
                            },
                            label = { Text(id.uppercase()) }
                        )
                    }
                }
                OutlinedTextField(value = customId, onValueChange = { customId = it }, label = { Text("Provider Key ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = customName, onValueChange = { customName = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = customUrl, onValueChange = { customUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = customKey,
                    onValueChange = { customKey = it },
                    label = { Text("API Key / Secret (Encrypted locally)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onSaveConnection(customId, customName, customCategory, customUrl, customKey)
                        customKey = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                ) {
                    Text("Save & Test Provider")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutonomousOpsVoiceAndAdminScreen(
    mode: String, // "AGENTS", "OBSERVATORY", "ALERTS", "VOICE", "ADMIN"
    uiState: JaxUiState,
    profile: UserWorkspaceProfile?,
    alerts: List<AlertRecord>,
    auditLogs: List<AuditLogRecord>,
    connections: List<ApiConnectionConfig>,
    onAckAlert: (String) -> Unit,
    onExecuteVoiceCommand: (String) -> Unit,
    onSignOut: () -> Unit,
    onDispatchSwarm: () -> Unit = {}
) {
    val context = LocalContext.current
    var commandText by remember { mutableStateOf("") }
    var micNotice by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            commandText = spoken
            onExecuteVoiceCommand(spoken)
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak J.A.X. Command")
                }
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                micNotice = "Device speech recognizer unavailable; type your command below."
            }
        } else {
            micNotice = "Microphone permission denied. Type voice commands manually below."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (mode) {
            "AGENTS" -> {
                AutonomousSwarmPanel(
                    isRunning = uiState.isSwarmRunning,
                    reports = uiState.swarmReports,
                    onDispatchSwarm = onDispatchSwarm
                )
                GlassPanel {
                    SectionHeader("12-AGENT SPECIALIST HEALTH ROSTER", "Scanner Network, Mission Controller & Health Monitor")
                    uiState.agentRoster.forEach { ag ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(ag.name, style = MaterialTheme.typography.bodyMedium, color = CrispWhite)
                                Text("Model: ${ag.assignedModel} | Last run: ${ag.lastExecutionMs}ms", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                            }
                            StatusPill(ag.state, if (ag.state == "ERROR") DeepRedBearish else EmeraldBullish)
                        }
                    }
                }
            }
            "OBSERVATORY" -> {
                GlassPanel(goldAccent = true) {
                    SectionHeader("PERFORMANCE OBSERVATORY & SELF-AUDIT", "Measured latency, provider errors & execution integrity")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Scan Time", "${uiState.lastScannerDurationMs}ms", MetallicGold, modifier = Modifier.weight(1f))
                        MetricTile("Quotes Live", "${uiState.quotes.values.count { it.health.name == "LIVE" }}", EmeraldBullish, modifier = Modifier.weight(1f))
                        MetricTile("Errors", "${connections.count { it.status == "ERROR" }}", DeepRedBearish, modifier = Modifier.weight(1f))
                    }
                    connections.forEach { c ->
                        Text("${c.providerName}: ${c.status} (${c.lastLatencyMs}ms) ${c.lastError}", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                    }
                }
            }
            "ALERTS" -> {
                GlassPanel(goldAccent = true) {
                    SectionHeader("ALERT & NOTIFICATION CENTRE", "Real-time Firestore alerts with duplicate & escalation tracking")
                    if (alerts.isEmpty()) {
                        Text("No active alerts recorded.", color = CoolGrey)
                    } else {
                        alerts.forEach { a ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("[${a.priority}] ${a.title}", style = MaterialTheme.typography.bodyMedium, color = CrispWhite)
                                    Text("${a.sourceEngine}: ${a.message}", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                                }
                                if (!a.acknowledged) {
                                    OutlinedButton(onClick = { onAckAlert(a.id) }) { Text("Ack") }
                                } else {
                                    StatusPill("ACK", EmeraldBullish)
                                }
                            }
                        }
                    }
                }
            }
            "VOICE" -> {
                GlassPanel(goldAccent = true) {
                    SectionHeader("J.A.X. VOICE COMMAND PRO", "Natural language intelligence control (never authorizes trades)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak J.A.X. Command")
                                        }
                                        speechLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        micNotice = "Speech activity not installed on emulator; use text/quick commands below."
                                    }
                                } else {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Voice Mic")
                        }
                    }
                    if (micNotice != null) {
                        Text(micNotice!!, style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                    }
                    OutlinedTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        label = { Text("Command Input (e.g. 'JAX, scan gold')") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { if (commandText.isNotBlank()) onExecuteVoiceCommand(commandText) },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack)
                    ) {
                        Text("Execute Command")
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "JAX, scan gold",
                            "Investigate Bitcoin",
                            "Investigate today's high-impact news",
                            "Show my current risk exposure",
                            "Give me the daily market briefing"
                        ).forEach { preset ->
                            OutlinedButton(onClick = {
                                commandText = preset
                                onExecuteVoiceCommand(preset)
                            }) {
                                Text(preset, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    uiState.voiceTranscriptResponse?.let {
                        Text("J.A.X. Response: $it", style = MaterialTheme.typography.bodyMedium, color = EmeraldBullish)
                    }
                }
            }
            "ADMIN" -> {
                GlassPanel(goldAccent = true) {
                    SectionHeader("SYSTEM ADMINISTRATION & GOOGLE IDENTITY", "RBAC role, Credential Manager session & zero-trust Firestore isolation")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Operator: ${profile?.displayName ?: "Authenticated"}", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                            if (uiState.operatorEmail.isNotBlank()) {
                                Text("Google Account: ${uiState.operatorEmail}", style = MaterialTheme.typography.bodySmall, color = EmeraldBullish)
                            }
                            if (uiState.operatorUid.isNotBlank()) {
                                Text("Workspace UID: ${uiState.operatorUid}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                            }
                            Text("Role: ${profile?.role ?: "analyst"} | Execution: ${profile?.executionMode ?: "ANALYSIS_ONLY"}", style = MaterialTheme.typography.bodySmall, color = CoolGrey)
                        }
                        Icon(Icons.Default.Security, contentDescription = "Google Zero Trust Active", tint = EmeraldBullish)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = ObsidianBlack),
                            modifier = Modifier.testTag("admin_switch_account_button")
                        ) {
                            Text("Switch Google Account")
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("admin_sign_out_button")
                        ) {
                            Text("Sign Out", color = DeepRedBearish)
                        }
                    }
                }
                GlassPanel {
                    SectionHeader("IMMUTABLE AUDIT LOGS", "Append-only Firestore security & execution trail")
                    auditLogs.take(20).forEach { log ->
                        Text("[${log.severity}] ${log.action} (${log.category}) — ${log.details}", style = MaterialTheme.typography.labelSmall, color = CoolGrey)
                    }
                    if (auditLogs.isEmpty()) {
                        Text("No audit entries recorded yet.", color = CoolGrey)
                    }
                }
            }
        }
    }
}
