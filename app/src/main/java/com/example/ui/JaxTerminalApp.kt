package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ConnectionHealth
import com.example.domain.model.WorkspaceDestination
import com.example.domain.model.WorkspaceGroup
import com.example.ui.auth.signOutUser
import com.example.ui.components.StatusPill
import com.example.ui.screens.ApiConnectionsAndMt5Screen
import com.example.ui.screens.AutonomousOpsVoiceAndAdminScreen
import com.example.ui.screens.ChartIntelligenceScreen
import com.example.ui.screens.GlobalCommandCentreScreen
import com.example.ui.screens.LiquidityAndDnaScreen
import com.example.ui.screens.MarketOverviewScreen
import com.example.ui.screens.MultiAiCouncilAndBureauScreen
import com.example.ui.screens.NewsAndCalendarScreen
import com.example.ui.screens.OpportunityRadarAndScannerScreen
import com.example.ui.screens.QuantLabAndHistoricalScreen
import com.example.ui.screens.RiskAndExecutionScreen
import com.example.ui.screens.SetupGeneratorScreen
import com.example.ui.screens.StrategyArchitectScreen
import com.example.ui.screens.WatchTowerScreen
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
import com.example.ui.viewmodel.JaxViewModel
import kotlinx.coroutines.launch

private data class QuickTab(
    val destination: WorkspaceDestination,
    val label: String,
    val icon: ImageVector
)

@Composable
fun JaxTerminalApp(
    viewModel: JaxViewModel,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val instruments by viewModel.instruments.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val setups by viewModel.setups.collectAsStateWithLifecycle()
    val investigations by viewModel.investigations.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val backtests by viewModel.backtests.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.selectedWorkspace != WorkspaceDestination.GLOBAL_COMMAND_CENTRE) {
        viewModel.selectWorkspace(WorkspaceDestination.GLOBAL_COMMAND_CENTRE)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val quickTabs = remember {
        listOf(
            QuickTab(WorkspaceDestination.GLOBAL_COMMAND_CENTRE, "Command", Icons.Default.Dashboard),
            QuickTab(WorkspaceDestination.OMNISCANNER, "Scanner", Icons.Default.Radar),
            QuickTab(WorkspaceDestination.CHART_INTELLIGENCE, "Charts", Icons.AutoMirrored.Filled.ShowChart),
            QuickTab(WorkspaceDestination.MULTI_AI_COUNCIL, "Council", Icons.Default.Psychology),
            QuickTab(WorkspaceDestination.RISK_COMMAND_CENTRE, "Risk", Icons.Default.Security)
        )
    }

    val onSignOutAction: () -> Unit = {
        signOutUser(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = onSignedOut
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        val isExpanded = maxWidth >= 840.dp

        val drawerContent: @Composable () -> Unit = {
            WorkspaceDrawerContent(
                selectedWorkspace = uiState.selectedWorkspace,
                operatorName = profile?.displayName ?: "Authenticated Operator",
                operatorEmail = uiState.operatorEmail,
                executionMode = if (profile?.emergencyStop == true) "EMERGENCY STOP" else (profile?.executionMode ?: "ANALYSIS_ONLY"),
                emergencyStop = profile?.emergencyStop == true,
                onSelectWorkspace = { dest ->
                    viewModel.selectWorkspace(dest)
                    if (!isExpanded) {
                        scope.launch { drawerState.close() }
                    }
                },
                onSignOut = {
                    if (!isExpanded) {
                        scope.launch { drawerState.close() }
                    }
                    onSignOutAction()
                }
            )
        }

        val mainScaffold: @Composable () -> Unit = {
            Scaffold(
                containerColor = ObsidianBlack,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TerminalTopHeader(
                        isExpanded = isExpanded,
                        workspace = uiState.selectedWorkspace,
                        selectedInstrument = uiState.selectedInstrument,
                        liveFeedsCount = uiState.quotes.values.count { it.health == ConnectionHealth.LIVE },
                        unackAlerts = alerts.count { !it.acknowledged },
                        executionBadge = if (profile?.emergencyStop == true) "HALTED" else (profile?.executionMode ?: "ANALYSIS_ONLY"),
                        emergencyStop = profile?.emergencyStop == true,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onQuickScan = { viewModel.runOmniscanner() },
                        onOpenAlerts = { viewModel.selectWorkspace(WorkspaceDestination.ALERT_CENTRE) }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = BlackGlass,
                        contentColor = CrispWhite,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        quickTabs.forEach { tab ->
                            val selected = uiState.selectedWorkspace == tab.destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectWorkspace(tab.destination) },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ObsidianBlack,
                                    selectedTextColor = MetallicGold,
                                    indicatorColor = MetallicGold,
                                    unselectedIconColor = CoolGrey,
                                    unselectedTextColor = CoolGrey
                                ),
                                modifier = Modifier.testTag("bottom_nav_${tab.destination.route}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    val bannerText = uiState.errorBanner ?: uiState.statusBanner
                    val isErr = uiState.errorBanner != null
                    if (!bannerText.isNullOrBlank()) {
                        Surface(
                            color = if (isErr) DeepRedBearish.copy(alpha = 0.16f) else BlackGlassElevated,
                            border = BorderStroke(1.dp, if (isErr) DeepRedBearish else GlassBorderGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = bannerText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isErr) DeepRedBearish else MetallicGold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.dismissBanner() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss Banner",
                                        tint = CoolGrey,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 980.dp)
                        ) {
                            when (uiState.selectedWorkspace) {
                                WorkspaceDestination.GLOBAL_COMMAND_CENTRE -> GlobalCommandCentreScreen(
                                    uiState = uiState,
                                    profile = profile,
                                    instruments = instruments,
                                    connections = connections,
                                    setups = setups,
                                    alerts = alerts,
                                    onRunScan = { viewModel.runOmniscanner() },
                                    onFetchNews = { viewModel.fetchLiveNews() },
                                    onGenerateExecutiveBriefing = { viewModel.generateDailyBriefing() },
                                    onToggleEmergencyStop = { viewModel.toggleEmergencyStop() },
                                    onSelectInstrument = { viewModel.selectInstrument(it) },
                                    onNavigate = { viewModel.selectWorkspace(it) }
                                )

                                WorkspaceDestination.MARKET_OVERVIEW -> MarketOverviewScreen(
                                    uiState = uiState,
                                    instruments = instruments,
                                    onToggleInstrument = { viewModel.toggleWatchlistInstrument(it) },
                                    onAddInstrument = { sym, name, asset, prov, provSym ->
                                        viewModel.addWatchlistInstrument(sym, name, asset, prov, provSym)
                                    },
                                    onRefreshAll = { viewModel.runOmniscanner() },
                                    onSelectAndChart = { sym ->
                                        viewModel.selectInstrument(sym)
                                        viewModel.selectWorkspace(WorkspaceDestination.CHART_INTELLIGENCE)
                                    }
                                )

                                WorkspaceDestination.OPPORTUNITY_RADAR -> OpportunityRadarAndScannerScreen(
                                    isRadarView = true,
                                    uiState = uiState,
                                    onSelectTimeframe = { viewModel.selectTimeframe(it) },
                                    onRunScan = { viewModel.runOmniscanner() },
                                    onSelectInstrument = { viewModel.selectInstrument(it) },
                                    onGenerateSetup = { viewModel.generateSetupForSelected() },
                                    onUpdateScannerCriteria = { viewModel.updateScannerCriteria(it) }
                                )

                                WorkspaceDestination.OMNISCANNER -> OpportunityRadarAndScannerScreen(
                                    isRadarView = false,
                                    uiState = uiState,
                                    onSelectTimeframe = { viewModel.selectTimeframe(it) },
                                    onRunScan = { viewModel.runOmniscanner() },
                                    onSelectInstrument = { viewModel.selectInstrument(it) },
                                    onGenerateSetup = { viewModel.generateSetupForSelected() },
                                    onUpdateScannerCriteria = { viewModel.updateScannerCriteria(it) }
                                )

                                WorkspaceDestination.SETUP_GENERATOR -> SetupGeneratorScreen(
                                    uiState = uiState,
                                    instruments = instruments,
                                    setups = setups,
                                    onSelectInstrument = { viewModel.selectInstrument(it) },
                                    onGenerateSetup = { viewModel.generateSetupForSelected() },
                                    onUpdateSetupStatus = { s, status -> viewModel.updateSetupLifecycle(s, status) },
                                    onQueueToRiskGate = { s ->
                                        viewModel.selectInstrument(s.instrument)
                                        viewModel.evaluateAndQueueOrder(
                                            instrumentSymbol = s.instrument,
                                            direction = if (s.direction == "BEARISH") "SELL" else "BUY",
                                            entryPrice = (s.entryZoneLow + s.entryZoneHigh) / 2.0,
                                            stopLoss = s.stopLoss,
                                            takeProfit = s.tp1,
                                            riskPct = profile?.riskPerTradePct ?: 1.0
                                        )
                                        viewModel.selectWorkspace(WorkspaceDestination.EXECUTION_INTELLIGENCE)
                                    }
                                )

                                WorkspaceDestination.CHART_INTELLIGENCE -> ChartIntelligenceScreen(
                                    uiState = uiState,
                                    instruments = instruments,
                                    onSelectInstrument = { viewModel.selectInstrument(it) },
                                    onSelectTimeframe = { viewModel.selectTimeframe(it) },
                                    onAddAnnotation = { label, price -> viewModel.addCustomAnnotation(label, price) },
                                    onRemoveAnnotation = { viewModel.removeAnnotation(it) },
                                    onAnalyzeScreenshot = { bmp -> viewModel.analyzeChartBitmap(bmp) }
                                )

                                WorkspaceDestination.AI_INVESTIGATION_BUREAU -> MultiAiCouncilAndBureauScreen(
                                    isBureauMode = true,
                                    uiState = uiState,
                                    instruments = instruments,
                                    investigations = investigations,
                                    onSelectInstrument = { viewModel.selectInstrument(it) },
                                    onRunCouncil = { viewModel.runMultiAiInvestigation() },
                                    onRunTribunal = { viewModel.runSupremeTribunal() }
                                )

                                WorkspaceDestination.MULTI_AI_COUNCIL -> MultiAiCouncilAndBureauScreen(
                                    isBureauMode = false,
                                    uiState = uiState,
                                    instruments = instruments,
                                    investigations = investigations,
                                    onSelectInstrument = { viewModel.selectInstrument(it) },
                                    onRunCouncil = { viewModel.runMultiAiInvestigation() },
                                    onRunTribunal = { viewModel.runSupremeTribunal() }
                                )

                                WorkspaceDestination.LIQUIDITY_INTELLIGENCE -> LiquidityAndDnaScreen(
                                    isLiquidityView = true,
                                    uiState = uiState,
                                    onScanFingerprints = { viewModel.scanPatternFingerprints() }
                                )

                                WorkspaceDestination.MARKET_DNA_LAB -> LiquidityAndDnaScreen(
                                    isLiquidityView = false,
                                    uiState = uiState,
                                    onScanFingerprints = { viewModel.scanPatternFingerprints() }
                                )

                                WorkspaceDestination.NEWS_INTELLIGENCE -> NewsAndCalendarScreen(
                                    isCalendarView = false,
                                    uiState = uiState,
                                    onFetchLiveNews = { viewModel.fetchLiveNews() },
                                    onFetchCalendar = { viewModel.fetchEconomicCalendar() },
                                    onGenerateDailyBriefing = { viewModel.generateDailyBriefing() },
                                    onInvestigateNews = { viewModel.investigateNewsArticle(it) },
                                    onAnalyzeCentralBanks = { viewModel.analyzeCentralBanksWithAi() }
                                )

                                WorkspaceDestination.ECONOMIC_CALENDAR -> NewsAndCalendarScreen(
                                    isCalendarView = true,
                                    uiState = uiState,
                                    onFetchLiveNews = { viewModel.fetchLiveNews() },
                                    onFetchCalendar = { viewModel.fetchEconomicCalendar() },
                                    onGenerateDailyBriefing = { viewModel.generateDailyBriefing() },
                                    onInvestigateNews = { viewModel.investigateNewsArticle(it) },
                                    onAnalyzeCentralBanks = { viewModel.analyzeCentralBanksWithAi() }
                                )

                                WorkspaceDestination.MARKET_WATCH_TOWER -> WatchTowerScreen(
                                    uiState = uiState,
                                    alerts = alerts,
                                    onToggleGhostMonitor = { viewModel.toggleGhostMonitor() },
                                    onRunScan = { viewModel.runOmniscanner() },
                                    onRunAnomalySweep = { viewModel.runWatchtowerAnomalySweep() }
                                )

                                WorkspaceDestination.HISTORICAL_INTELLIGENCE -> QuantLabAndHistoricalScreen(
                                    isBacktestView = false,
                                    uiState = uiState,
                                    backtests = backtests,
                                    setups = setups,
                                    onRunBacktest = { name, sp, sl -> viewModel.runQuantBacktest(name, sp, sl) },
                                    onRunMutationLab = { viewModel.runStrategyMutationLab() },
                                    onRunBlindSim = { pct, useAi -> viewModel.runHistoricalRealitySimulation(pct, useAi) },
                                    onRevealSimOutcome = { viewModel.revealHistoricalRealityOutcome() }
                                )

                                WorkspaceDestination.BACKTESTING_LAB -> QuantLabAndHistoricalScreen(
                                    isBacktestView = true,
                                    uiState = uiState,
                                    backtests = backtests,
                                    setups = setups,
                                    onRunBacktest = { name, sp, sl -> viewModel.runQuantBacktest(name, sp, sl) },
                                    onRunMutationLab = { viewModel.runStrategyMutationLab() },
                                    onRunBlindSim = { pct, useAi -> viewModel.runHistoricalRealitySimulation(pct, useAi) },
                                    onRevealSimOutcome = { viewModel.revealHistoricalRealityOutcome() }
                                )

                                WorkspaceDestination.RISK_COMMAND_CENTRE -> RiskAndExecutionScreen(
                                    isExecutionView = false,
                                    uiState = uiState,
                                    profile = profile,
                                    instruments = instruments,
                                    orders = orders,
                                    onUpdatePolicy = { mode, stop, bal, risk, dd, pos ->
                                        viewModel.updateRiskAndExecutionSettings(mode, stop, bal, risk, dd, pos)
                                    },
                                    onEvaluateAndQueueOrder = { sym, dir, entry, sl, tp, rPct ->
                                        viewModel.evaluateAndQueueOrder(sym, dir, entry, sl, tp, rPct)
                                    },
                                    onApproveOrCancelOrder = { ord, ok -> viewModel.approveOrCancelOrder(ord, ok) }
                                )

                                WorkspaceDestination.EXECUTION_INTELLIGENCE -> RiskAndExecutionScreen(
                                    isExecutionView = true,
                                    uiState = uiState,
                                    profile = profile,
                                    instruments = instruments,
                                    orders = orders,
                                    onUpdatePolicy = { mode, stop, bal, risk, dd, pos ->
                                        viewModel.updateRiskAndExecutionSettings(mode, stop, bal, risk, dd, pos)
                                    },
                                    onEvaluateAndQueueOrder = { sym, dir, entry, sl, tp, rPct ->
                                        viewModel.evaluateAndQueueOrder(sym, dir, entry, sl, tp, rPct)
                                    },
                                    onApproveOrCancelOrder = { ord, ok -> viewModel.approveOrCancelOrder(ord, ok) }
                                )

                                WorkspaceDestination.AUTONOMOUS_AGENT_CENTRE -> AutonomousOpsVoiceAndAdminScreen(
                                    mode = "AGENTS",
                                    uiState = uiState,
                                    profile = profile,
                                    alerts = alerts,
                                    auditLogs = auditLogs,
                                    connections = connections,
                                    onAckAlert = { viewModel.acknowledgeAlert(it) },
                                    onExecuteVoiceCommand = { viewModel.executeVoiceCommand(it) },
                                    onSignOut = onSignOutAction,
                                    onDispatchSwarm = { viewModel.runAutonomousSwarm() }
                                )

                                WorkspaceDestination.STRATEGY_ARCHITECT -> StrategyArchitectScreen(
                                    uiState = uiState,
                                    onCompileStrategy = { viewModel.buildStrategyFromNaturalLanguage(it) }
                                )

                                WorkspaceDestination.PERFORMANCE_OBSERVATORY -> AutonomousOpsVoiceAndAdminScreen(
                                    mode = "OBSERVATORY",
                                    uiState = uiState,
                                    profile = profile,
                                    alerts = alerts,
                                    auditLogs = auditLogs,
                                    connections = connections,
                                    onAckAlert = { viewModel.acknowledgeAlert(it) },
                                    onExecuteVoiceCommand = { viewModel.executeVoiceCommand(it) },
                                    onSignOut = onSignOutAction
                                )

                                WorkspaceDestination.API_CONNECTIONS -> ApiConnectionsAndMt5Screen(
                                    isMt5View = false,
                                    uiState = uiState,
                                    connections = connections,
                                    onTestConnection = { viewModel.testConnection(it) },
                                    onSaveConnection = { id, name, cat, url, key ->
                                        viewModel.saveApiConnection(id, name, cat, url, key)
                                    },
                                    onPollMt5 = { viewModel.pollMt5Bridge(it) }
                                )

                                WorkspaceDestination.MT5_CONTROL_CENTRE -> ApiConnectionsAndMt5Screen(
                                    isMt5View = true,
                                    uiState = uiState,
                                    connections = connections,
                                    onTestConnection = { viewModel.testConnection(it) },
                                    onSaveConnection = { id, name, cat, url, key ->
                                        viewModel.saveApiConnection(id, name, cat, url, key)
                                    },
                                    onPollMt5 = { viewModel.pollMt5Bridge(it) }
                                )

                                WorkspaceDestination.ALERT_CENTRE -> AutonomousOpsVoiceAndAdminScreen(
                                    mode = "ALERTS",
                                    uiState = uiState,
                                    profile = profile,
                                    alerts = alerts,
                                    auditLogs = auditLogs,
                                    connections = connections,
                                    onAckAlert = { viewModel.acknowledgeAlert(it) },
                                    onExecuteVoiceCommand = { viewModel.executeVoiceCommand(it) },
                                    onSignOut = onSignOutAction
                                )

                                WorkspaceDestination.VOICE_COMMAND_CENTRE -> AutonomousOpsVoiceAndAdminScreen(
                                    mode = "VOICE",
                                    uiState = uiState,
                                    profile = profile,
                                    alerts = alerts,
                                    auditLogs = auditLogs,
                                    connections = connections,
                                    onAckAlert = { viewModel.acknowledgeAlert(it) },
                                    onExecuteVoiceCommand = { viewModel.executeVoiceCommand(it) },
                                    onSignOut = onSignOutAction
                                )

                                WorkspaceDestination.SYSTEM_ADMINISTRATION -> AutonomousOpsVoiceAndAdminScreen(
                                    mode = "ADMIN",
                                    uiState = uiState,
                                    profile = profile,
                                    alerts = alerts,
                                    auditLogs = auditLogs,
                                    connections = connections,
                                    onAckAlert = { viewModel.acknowledgeAlert(it) },
                                    onExecuteVoiceCommand = { viewModel.executeVoiceCommand(it) },
                                    onSignOut = onSignOutAction
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

        if (isExpanded) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(280.dp),
                        drawerContainerColor = BlackGlass,
                        drawerContentColor = CrispWhite
                    ) {
                        drawerContent()
                    }
                },
                content = mainScaffold
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(300.dp),
                        drawerContainerColor = BlackGlass,
                        drawerContentColor = CrispWhite
                    ) {
                        drawerContent()
                    }
                },
                content = mainScaffold
            )
        }
    }
}

@Composable
private fun TerminalTopHeader(
    isExpanded: Boolean,
    workspace: WorkspaceDestination,
    selectedInstrument: String,
    liveFeedsCount: Int,
    unackAlerts: Int,
    executionBadge: String,
    emergencyStop: Boolean,
    onOpenMenu: () -> Unit,
    onQuickScan: () -> Unit,
    onOpenAlerts: () -> Unit
) {
    Surface(
        color = BlackGlass,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!isExpanded) {
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier.testTag("drawer_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open 25 Workspaces Menu",
                            tint = MetallicGold
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = if (isExpanded) 8.dp else 2.dp)) {
                    Text(
                        text = "${workspace.indexNumber}. ${workspace.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$selectedInstrument • $liveFeedsCount Live • $executionBadge",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (emergencyStop) DeepRedBearish else CoolGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusPill(
                    text = if (emergencyStop) "HALT" else "SAFE",
                    color = if (emergencyStop) DeepRedBearish else EmeraldBullish
                )
                IconButton(
                    onClick = onQuickScan,
                    modifier = Modifier.testTag("topbar_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Quick Scan Markets",
                        tint = MetallicGold
                    )
                }
                IconButton(
                    onClick = onOpenAlerts,
                    modifier = Modifier.testTag("topbar_alerts_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alerts ($unackAlerts unread)",
                        tint = if (unackAlerts > 0) AmberWarning else CoolGrey
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceDrawerContent(
    selectedWorkspace: WorkspaceDestination,
    operatorName: String,
    operatorEmail: String,
    executionMode: String,
    emergencyStop: Boolean,
    onSelectWorkspace: (WorkspaceDestination) -> Unit,
    onSignOut: () -> Unit
) {
    val grouped = remember { WorkspaceGroup.entries.associateWith { grp -> WorkspaceDestination.entries.filter { it.group == grp } } }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "J.A.X. OMNIVERSE",
                style = MaterialTheme.typography.titleLarge,
                color = MetallicGold,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "APEX COMMAND INTELLIGENCE X",
                style = MaterialTheme.typography.labelSmall,
                color = CyanInfo
            )
            Text(
                text = "$operatorName • $executionMode",
                style = MaterialTheme.typography.labelSmall,
                color = if (emergencyStop) DeepRedBearish else CoolGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (operatorEmail.isNotBlank()) {
                Text(
                    text = operatorEmail,
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldBullish,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WorkspaceGroup.entries.forEach { group ->
                item(key = "header_${group.name}") {
                    Text(
                        text = group.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MetallicGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                val itemsInGroup = grouped[group].orEmpty()
                items(itemsInGroup, key = { it.route }) { dest ->
                    val isSelected = selectedWorkspace == dest
                    Surface(
                        color = if (isSelected) MetallicGold.copy(alpha = 0.16f) else ObsidianBlack.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (isSelected) MetallicGold else GlassBorder.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectWorkspace(dest) }
                            .testTag("drawer_item_${dest.route}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = if (isSelected) MetallicGold else CoolGrey,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${dest.indexNumber}. ${dest.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MetallicGold else CrispWhite,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = dest.purpose,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CoolGrey,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 6.dp))

        Surface(
            color = ObsidianBlack.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, GlassBorderGold),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clickable(onClick = onSignOut)
                .testTag("drawer_sign_out_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Switch Google Account / Sign Out",
                    style = MaterialTheme.typography.labelMedium,
                    color = MetallicGold,
                    fontWeight = FontWeight.SemiBold
                )
                StatusPill(text = "AUTH", color = EmeraldBullish)
            }
        }
    }
}
