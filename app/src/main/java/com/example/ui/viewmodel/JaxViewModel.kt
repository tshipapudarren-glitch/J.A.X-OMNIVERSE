package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ApexAiSpecializedOrchestrator
import com.example.data.ai.MultiAiOrchestrator
import com.example.data.network.MarketDataService
import com.example.data.network.Mt5BridgeClient
import com.example.data.network.NewsIngestionService
import com.example.data.repository.JaxFirestoreRepository
import com.example.domain.engine.ApexRadarEngine
import com.example.domain.engine.MarketDnaEngine
import com.example.domain.engine.QuantBacktestEngine
import com.example.domain.engine.RiskEngine
import com.example.domain.engine.StrategyMutationEngine
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.engine.VoiceCommandParser
import com.example.domain.engine.VoiceIntent
import com.example.domain.model.AiInvestigationReport
import com.example.domain.model.AiNewsInvestigationResult
import com.example.domain.model.AlertRecord
import com.example.domain.model.ApiConnectionConfig
import com.example.domain.model.AuditLogRecord
import com.example.domain.model.BacktestRunRecord
import com.example.domain.model.Candle
import com.example.domain.model.CentralBankFeedStatus
import com.example.domain.model.ChartAnnotation
import com.example.domain.model.ConnectionHealth
import com.example.domain.model.EconomicCalendarItem
import com.example.domain.model.ExecutionMode
import com.example.domain.model.ExecutionOrderRecord
import com.example.domain.model.FredMacroIndicator
import com.example.domain.model.HistoricalAnalogue
import com.example.domain.model.HistoricalRealitySimulation
import com.example.domain.model.MarketQuote
import com.example.domain.model.Mt5TelemetrySnapshot
import com.example.domain.model.NewsArticleItem
import com.example.domain.model.NewsTimelineEvent
import com.example.domain.model.PatternFingerprintMatch
import com.example.domain.model.PerfectStormSignal
import com.example.domain.model.RiskEvaluationResult
import com.example.domain.model.ScannerCriteriaConfig
import com.example.domain.model.SentimentDivergenceItem
import com.example.domain.model.SpecialistAgentStatus
import com.example.domain.model.StrategyVariantResult
import com.example.domain.model.SwarmMarketReport
import com.example.domain.model.TechnicalSnapshot
import com.example.domain.model.TradingSetup
import com.example.domain.model.TribunalChallengeRecord
import com.example.domain.model.UserWorkspaceProfile
import com.example.domain.model.WatchlistInstrument
import com.example.domain.model.WatchtowerAnomaly
import com.example.domain.model.WorkspaceDestination
import com.example.security.SecretVault
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class JaxUiState(
    val selectedWorkspace: WorkspaceDestination = WorkspaceDestination.GLOBAL_COMMAND_CENTRE,
    val selectedInstrument: String = "BTC-USD",
    val selectedTimeframe: String = "1H",
    val quotes: Map<String, MarketQuote> = emptyMap(),
    val candlesBySymbol: Map<String, List<Candle>> = emptyMap(),
    val technicalBySymbol: Map<String, TechnicalSnapshot> = emptyMap(),
    val analoguesBySymbol: Map<String, List<HistoricalAnalogue>> = emptyMap(),
    val annotations: List<ChartAnnotation> = emptyList(),
    val newsArticles: List<NewsArticleItem> = emptyList(),
    val newsErrors: List<String> = emptyList(),
    val calendarEvents: List<EconomicCalendarItem> = emptyList(),
    val macroIndicators: List<FredMacroIndicator> = emptyList(),
    val calendarErrors: List<String> = emptyList(),
    val mt5Telemetry: Mt5TelemetrySnapshot = Mt5TelemetrySnapshot(
        connected = false,
        endpointUrl = "",
        maskedAccountId = "Not Connected",
        broker = "Disconnected",
        server = "Offline",
        eaStatus = "OFFLINE",
        balance = 0.0,
        equity = 0.0,
        marginLevelPct = 0.0,
        lastHeartbeatEpochMs = 0L,
        lastError = "No MT5 Bridge URL configured"
    ),
    val agentRoster: List<SpecialistAgentStatus> = emptyList(),
    val lastRiskEvaluation: RiskEvaluationResult? = null,
    val dailyBriefing: String? = null,
    val visionAnalysis: String? = null,
    val strategyDraft: String? = null,
    val voiceTranscriptResponse: String? = null,
    val isScanning: Boolean = false,
    val isNewsFetching: Boolean = false,
    val isCalendarFetching: Boolean = false,
    val isAiRunning: Boolean = false,
    val isSwarmRunning: Boolean = false,
    val isTribunalRunning: Boolean = false,
    val isSimRunning: Boolean = false,
    val ghostMonitorActive: Boolean = false,
    val statusBanner: String? = null,
    val errorBanner: String? = null,
    val lastScannerDurationMs: Long = 0L,
    // 12 Apex Intelligence Extensions
    val strategyVariantsBySymbol: Map<String, List<StrategyVariantResult>> = emptyMap(),
    val patternFingerprintsBySymbol: Map<String, List<PatternFingerprintMatch>> = emptyMap(),
    val sentimentDivergences: List<SentimentDivergenceItem> = emptyList(),
    val perfectStormSignals: List<PerfectStormSignal> = emptyList(),
    val swarmReports: List<SwarmMarketReport> = emptyList(),
    val tribunalRecords: List<TribunalChallengeRecord> = emptyList(),
    val historicalSimulation: HistoricalRealitySimulation? = null,
    val newsTimeline: List<NewsTimelineEvent> = emptyList(),
    val newsInvestigations: List<AiNewsInvestigationResult> = emptyList(),
    val centralBankStatuses: List<CentralBankFeedStatus> = emptyList(),
    val centralBankBrief: String? = null,
    val executiveBriefing: String? = null,
    val scannerCriteria: ScannerCriteriaConfig = ScannerCriteriaConfig(),
    val watchtowerAnomalies: List<WatchtowerAnomaly> = emptyList(),
    val operatorEmail: String = "",
    val operatorUid: String = "",
    val isNewGoogleSignup: Boolean = false
)

class JaxViewModel(
    private val repository: JaxFirestoreRepository,
    private val marketDataService: MarketDataService,
    private val newsIngestionService: NewsIngestionService,
    private val mt5BridgeClient: Mt5BridgeClient,
    private val aiOrchestrator: MultiAiOrchestrator,
    private val secretVault: SecretVault,
    private val currentUserId: String,
    private val operatorDisplayName: String,
    private val operatorEmail: String = "",
    private val isNewUserSession: Boolean = false,
    private val apexAiOrchestrator: ApexAiSpecializedOrchestrator = ApexAiSpecializedOrchestrator()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        JaxUiState(
            agentRoster = aiOrchestrator.initialAgentRoster(),
            centralBankStatuses = newsIngestionService.buildCentralBankStatuses(emptyList(), emptyList()),
            operatorEmail = operatorEmail,
            operatorUid = currentUserId,
            isNewGoogleSignup = isNewUserSession,
            statusBanner = if (isNewUserSession) {
                "Google Sign-Up complete! Welcome $operatorDisplayName — workspace provisioned in safe ANALYSIS_ONLY mode."
            } else {
                null
            }
        )
    )
    val uiState: StateFlow<JaxUiState> = _uiState.asStateFlow()

    private var ghostMonitorJob: Job? = null

    private fun <T> unwrapFirestoreFlow(
        flow: kotlinx.coroutines.flow.Flow<T>,
        fallback: T,
        label: String
    ): StateFlow<T> = flow
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Stream error on $label", root)
            _uiState.update { it.copy(errorBanner = "$label: ${root.localizedMessage ?: "Firestore error"}") }
            emit(fallback)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), fallback)

    val profile: StateFlow<UserWorkspaceProfile?> =
        unwrapFirestoreFlow(repository.observeProfile(currentUserId), null, "profile")

    val instruments: StateFlow<List<WatchlistInstrument>> =
        unwrapFirestoreFlow(repository.observeInstruments(currentUserId), emptyList(), "instruments")

    val connections: StateFlow<List<ApiConnectionConfig>> =
        unwrapFirestoreFlow(repository.observeConnections(currentUserId), emptyList(), "connections")

    val setups: StateFlow<List<TradingSetup>> =
        unwrapFirestoreFlow(repository.observeSetups(currentUserId), emptyList(), "setups")

    val investigations: StateFlow<List<AiInvestigationReport>> =
        unwrapFirestoreFlow(repository.observeInvestigations(currentUserId), emptyList(), "investigations")

    val alerts: StateFlow<List<AlertRecord>> =
        unwrapFirestoreFlow(repository.observeAlerts(currentUserId), emptyList(), "alerts")

    val orders: StateFlow<List<ExecutionOrderRecord>> =
        unwrapFirestoreFlow(repository.observeOrders(currentUserId), emptyList(), "orders")

    val backtests: StateFlow<List<BacktestRunRecord>> =
        unwrapFirestoreFlow(repository.observeBacktests(currentUserId), emptyList(), "backtests")

    val auditLogs: StateFlow<List<AuditLogRecord>> =
        unwrapFirestoreFlow(repository.observeAuditLogs(currentUserId), emptyList(), "audit_logs")

    init {
        viewModelScope.launch {
            try {
                repository.ensureProfileInitialized(
                    displayName = operatorDisplayName,
                    configuredProviders = secretVault.getConfiguredProviderIds()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "Profile init failed: ${e.localizedMessage}") }
            }
        }
    }

    fun selectWorkspace(destination: WorkspaceDestination) {
        _uiState.update { it.copy(selectedWorkspace = destination, errorBanner = null) }
    }

    fun selectInstrument(symbol: String) {
        _uiState.update { state ->
            val tech = state.technicalBySymbol[symbol]
            val latestSetup = setups.value.firstOrNull { it.instrument == symbol }
            val ann = if (tech != null) TechnicalAnalysisEngine.buildChartAnnotations(tech, latestSetup) else emptyList()
            state.copy(selectedInstrument = symbol, annotations = ann)
        }
    }

    fun selectTimeframe(tf: String) {
        _uiState.update { it.copy(selectedTimeframe = tf) }
        runOmniscanner(singleSymbol = _uiState.value.selectedInstrument)
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBanner = null, errorBanner = null) }
    }

    fun runOmniscanner(singleSymbol: String? = null) {
        val list = instruments.value.filter { it.enabled }
            .let { enabled -> if (singleSymbol != null) enabled.filter { it.symbol == singleSymbol } else enabled }
        if (list.isEmpty()) {
            _uiState.update { it.copy(errorBanner = "No enabled instruments found in watchlist.") }
            return
        }

        viewModelScope.launch {
            val scanStart = System.currentTimeMillis()
            _uiState.update { it.copy(isScanning = true, errorBanner = null, statusBanner = "APEX Omniscanner X querying live feeds...") }
            val tf = _uiState.value.selectedTimeframe
            val newQuotes = _uiState.value.quotes.toMutableMap()
            val newCandles = _uiState.value.candlesBySymbol.toMutableMap()
            val newTech = _uiState.value.technicalBySymbol.toMutableMap()
            val newAnalogues = _uiState.value.analoguesBySymbol.toMutableMap()

            val results = list.map { inst ->
                async { inst to marketDataService.fetchInstrumentData(inst, tf) }
            }.awaitAll()

            val providerHealthSummary = mutableMapOf<String, Pair<Boolean, Long>>()
            var liveCount = 0

            results.forEach { (inst, bundle) ->
                newQuotes[inst.symbol] = bundle.quote
                if (bundle.quote.health == ConnectionHealth.LIVE) {
                    liveCount++
                    if (bundle.candles.isNotEmpty()) {
                        newCandles[inst.symbol] = bundle.candles
                        val tech = TechnicalAnalysisEngine.analyze(inst.symbol, tf, bundle.candles)
                        if (tech != null) {
                            newTech[inst.symbol] = tech
                            newAnalogues[inst.symbol] = MarketDnaEngine.findHistoricalAnalogues(bundle.candles)
                        }
                    }
                    providerHealthSummary[inst.providerId] = true to bundle.quote.latencyMs
                } else {
                    providerHealthSummary[inst.providerId] = false to bundle.quote.latencyMs
                }
            }

            // Update Firestore connection statuses with real measured latency/errors
            providerHealthSummary.forEach { (provId, resultPair) ->
                val existingConn = connections.value.firstOrNull { it.id == provId }
                if (existingConn != null) {
                    try {
                        repository.upsertConnection(
                            connectionId = provId,
                            providerName = existingConn.providerName,
                            category = existingConn.category,
                            baseUrl = existingConn.baseUrl,
                            status = if (resultPair.first) "CONNECTED" else "ERROR",
                            credentialConfigured = existingConn.credentialConfigured,
                            rateLimitInfo = existingConn.rateLimitInfo,
                            lastLatencyMs = resultPair.second,
                            lastError = if (resultPair.first) "" else "Feed probe failed",
                            enabled = existingConn.enabled
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            val selectedSym = _uiState.value.selectedInstrument
            val selTech = newTech[selectedSym]
            val selSetup = setups.value.firstOrNull { it.instrument == selectedSym }
            val ann = if (selTech != null) TechnicalAnalysisEngine.buildChartAnnotations(selTech, selSetup) else _uiState.value.annotations
            val newFingerprints = _uiState.value.patternFingerprintsBySymbol.toMutableMap()
            newCandles.forEach { (sym, cList) ->
                if (cList.size >= 40) {
                    newFingerprints[sym] = MarketDnaEngine.scanPatternFingerprints(cList)
                }
            }

            val elapsed = System.currentTimeMillis() - scanStart
            _uiState.update { cur ->
                val divergences = ApexRadarEngine.detectSentimentDivergences(newQuotes, newTech, cur.newsArticles, cur.macroIndicators)
                val storms = ApexRadarEngine.detectPerfectStorms(newTech, newQuotes, cur.newsArticles, setups.value)
                val timeline = ApexRadarEngine.reconstructNewsTimeline(cur.newsArticles, newQuotes)
                val anomalies = ApexRadarEngine.runWatchtowerSweep(
                    connections.value, newTech, newQuotes, setups.value, orders.value, profile.value, cur.ghostMonitorActive
                )
                cur.copy(
                    isScanning = false,
                    quotes = newQuotes,
                    candlesBySymbol = newCandles,
                    technicalBySymbol = newTech,
                    analoguesBySymbol = newAnalogues,
                    patternFingerprintsBySymbol = newFingerprints,
                    sentimentDivergences = divergences,
                    perfectStormSignals = storms,
                    newsTimeline = timeline,
                    watchtowerAnomalies = anomalies,
                    annotations = ann,
                    lastScannerDurationMs = elapsed,
                    statusBanner = "Omniscanner completed: $liveCount/${results.size} live feeds in ${elapsed}ms"
                )
            }
        }
    }

    fun generateSetupForSelected() {
        val sym = _uiState.value.selectedInstrument
        val tech = _uiState.value.technicalBySymbol[sym]
        val quote = _uiState.value.quotes[sym]
        if (tech == null || quote == null || quote.health != ConnectionHealth.LIVE) {
            _uiState.update { it.copy(errorBanner = "Cannot generate setup for $sym without live market data. Run Omniscanner first.") }
            return
        }

        viewModelScope.launch {
            try {
                val relatedNews = _uiState.value.newsArticles.filter { sym in it.affectedInstruments }
                val newsSummary = if (relatedNews.isEmpty()) "No active instrument-linked RSS news"
                else "${relatedNews.size} linked releases (${relatedNews.first().sentimentLabel})"
                val setup = TechnicalAnalysisEngine.generateSetup(
                    userId = currentUserId,
                    snapshot = tech,
                    dataQualityStatus = "LIVE_${quote.providerId.uppercase()}",
                    newsExposure = newsSummary
                )
                repository.saveSetup(setup)
                val ann = TechnicalAnalysisEngine.buildChartAnnotations(tech, setup)
                repository.createAlert(
                    category = if (setup.status == "CONFIRMED") "SETUP_CONFIRMATION" else "NEW_OPPORTUNITY",
                    priority = if (setup.score >= 75) "HIGH" else "MEDIUM",
                    instrument = sym,
                    title = "${setup.instrument} ${setup.direction} (${setup.status})",
                    message = "${setup.setupType} | Score ${setup.score} | R:R ${"%.2f".format(setup.rr1)}",
                    sourceEngine = "APEX Setup Generator"
                )
                _uiState.update {
                    it.copy(
                        annotations = ann,
                        statusBanner = "Generated ${setup.status} ${setup.direction} setup for $sym (Score ${setup.score}/100)"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "Setup save failed: ${e.localizedMessage}") }
            }
        }
    }

    fun updateSetupLifecycle(setup: TradingSetup, newStatus: String) {
        viewModelScope.launch {
            try {
                repository.updateSetupStatus(setup, newStatus)
                _uiState.update { it.copy(statusBanner = "${setup.instrument} setup transitioned to $newStatus") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage) }
            }
        }
    }

    fun addCustomAnnotation(label: String, price: Double) {
        if (price <= 0.0 || label.isBlank()) return
        val newItem = ChartAnnotation(
            id = "user_${System.currentTimeMillis()}",
            label = label.trim(),
            type = "USER_LEVEL",
            priceStart = price,
            isEditable = true
        )
        _uiState.update { it.copy(annotations = it.annotations + newItem) }
    }

    fun removeAnnotation(id: String) {
        _uiState.update { it.copy(annotations = it.annotations.filterNot { a -> a.id == id }) }
    }

    fun runMultiAiInvestigation(symbol: String = _uiState.value.selectedInstrument) {
        val tech = _uiState.value.technicalBySymbol[symbol]
        val candles = _uiState.value.candlesBySymbol[symbol].orEmpty()
        val quote = _uiState.value.quotes[symbol]
        if (tech == null || candles.isEmpty() || quote == null || quote.health != ConnectionHealth.LIVE) {
            _uiState.update { it.copy(errorBanner = "Run Omniscanner on $symbol first so AI Council has verified live candles.") }
            return
        }

        viewModelScope.launch {
            val start = System.currentTimeMillis()
            _uiState.update { state ->
                state.copy(
                    isAiRunning = true,
                    errorBanner = null,
                    agentRoster = state.agentRoster.map { it.copy(state = "RUNNING") },
                    statusBanner = "Multi-AI Council investigating $symbol..."
                )
            }

            val relatedNews = _uiState.value.newsArticles.filter { symbol in it.affectedInstruments }
            val result = aiOrchestrator.runMultiAiDebate(
                userId = currentUserId,
                snapshot = tech,
                candles = candles,
                relatedNews = relatedNews,
                dataQualityNote = "LIVE (${quote.providerId}, latency=${quote.latencyMs}ms)"
            )

            val duration = System.currentTimeMillis() - start
            result.fold(
                onSuccess = { report ->
                    try {
                        repository.saveInvestigation(report)
                        _uiState.update { state ->
                            state.copy(
                                isAiRunning = false,
                                agentRoster = state.agentRoster.map {
                                    it.copy(
                                        state = "COMPLETED",
                                        lastExecutionMs = duration,
                                        lastConfidence = report.confidenceScore.toInt(),
                                        lastError = null
                                    )
                                },
                                statusBanner = "Chief Analyst verdict stored for $symbol (Verified=${report.verified}, Conf=${report.confidenceScore}%)"
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isAiRunning = false, errorBanner = "Save investigation failed: ${e.localizedMessage}") }
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(
                            isAiRunning = false,
                            agentRoster = state.agentRoster.map {
                                it.copy(state = "ERROR", lastExecutionMs = duration, lastError = err.localizedMessage)
                            },
                            errorBanner = "Multi-AI Council Error: ${err.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun analyzeChartBitmap(bitmap: Bitmap) {
        val sym = _uiState.value.selectedInstrument
        viewModelScope.launch {
            _uiState.update { it.copy(isAiRunning = true, errorBanner = null, statusBanner = "Chart Vision AI inspecting screenshot...") }
            val res = aiOrchestrator.analyzeChartScreenshot(bitmap, sym)
            res.fold(
                onSuccess = { text ->
                    _uiState.update { it.copy(isAiRunning = false, visionAnalysis = text, statusBanner = "Chart Vision AI analysis complete") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isAiRunning = false, errorBanner = "Chart Vision AI failed: ${err.localizedMessage}") }
                }
            )
        }
    }

    fun fetchLiveNews() {
        viewModelScope.launch {
            _uiState.update { it.copy(isNewsFetching = true, errorBanner = null) }
            val res = newsIngestionService.fetchLiveNews()
            try {
                val conn = connections.value.firstOrNull { it.id == "institutional_rss" }
                if (conn != null) {
                    repository.upsertConnection(
                        connectionId = "institutional_rss",
                        providerName = conn.providerName,
                        category = conn.category,
                        baseUrl = conn.baseUrl,
                        status = if (res.articles.isNotEmpty()) "CONNECTED" else "ERROR",
                        credentialConfigured = true,
                        rateLimitInfo = conn.rateLimitInfo,
                        lastLatencyMs = res.latencyMs,
                        lastError = res.sourceErrors.joinToString("; "),
                        enabled = true
                    )
                }
            } catch (_: Exception) {
            }

            _uiState.update { cur ->
                val timeline = ApexRadarEngine.reconstructNewsTimeline(res.articles, cur.quotes)
                val divergences = ApexRadarEngine.detectSentimentDivergences(cur.quotes, cur.technicalBySymbol, res.articles, cur.macroIndicators)
                val storms = ApexRadarEngine.detectPerfectStorms(cur.technicalBySymbol, cur.quotes, res.articles, setups.value)
                val cbStatuses = newsIngestionService.buildCentralBankStatuses(res.articles, cur.macroIndicators)
                cur.copy(
                    isNewsFetching = false,
                    newsArticles = res.articles,
                    newsErrors = res.sourceErrors,
                    newsTimeline = timeline,
                    sentimentDivergences = divergences,
                    perfectStormSignals = storms,
                    centralBankStatuses = cbStatuses,
                    statusBanner = "News Radar: ${res.articles.size} verified articles fetched (${res.latencyMs}ms)"
                )
            }
        }
    }

    fun fetchEconomicCalendar() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCalendarFetching = true, errorBanner = null) }
            val res = newsIngestionService.fetchEconomicCalendarAndMacro()
            _uiState.update { cur ->
                val divergences = ApexRadarEngine.detectSentimentDivergences(cur.quotes, cur.technicalBySymbol, cur.newsArticles, res.macroIndicators)
                val cbStatuses = newsIngestionService.buildCentralBankStatuses(cur.newsArticles, res.macroIndicators)
                cur.copy(
                    isCalendarFetching = false,
                    calendarEvents = res.calendarEvents,
                    macroIndicators = res.macroIndicators,
                    calendarErrors = res.sourceErrors,
                    sentimentDivergences = divergences,
                    centralBankStatuses = cbStatuses,
                    statusBanner = "Macro & Calendar: ${res.calendarEvents.size} events, ${res.macroIndicators.size} FRED series (${res.latencyMs}ms)"
                )
            }
        }
    }

    fun generateDailyBriefing() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiRunning = true, errorBanner = null) }
            val snaps = _uiState.value.technicalBySymbol.values.toList()
            val news = _uiState.value.newsArticles
            val res = apexAiOrchestrator.generateExecutiveBriefing(
                snapshots = snaps,
                setups = setups.value,
                orders = orders.value,
                news = news,
                alerts = alerts.value,
                connections = connections.value
            )
            res.fold(
                onSuccess = { text ->
                    _uiState.update {
                        it.copy(
                            isAiRunning = false,
                            dailyBriefing = text,
                            executiveBriefing = text,
                            statusBanner = "Executive Intelligence Briefing generated"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isAiRunning = false, errorBanner = "Briefing failed: ${err.localizedMessage}") }
                }
            )
        }
    }

    fun runQuantBacktest(strategyName: String, spreadPct: Double, slippagePct: Double) {
        val sym = _uiState.value.selectedInstrument
        val tf = _uiState.value.selectedTimeframe
        val candles = _uiState.value.candlesBySymbol[sym].orEmpty()
        if (candles.size < 40) {
            _uiState.update { it.copy(errorBanner = "Need at least 40 live candles on $sym ($tf). Run Omniscanner first.") }
            return
        }
        val run = QuantBacktestEngine.runBacktest(
            userId = currentUserId,
            strategyName = strategyName.ifBlank { "EMA Displacement Walk-Forward" },
            instrument = sym,
            timeframe = tf,
            candles = candles,
            spreadPct = spreadPct,
            slippagePct = slippagePct,
            riskPerTradePct = profile.value?.riskPerTradePct ?: 1.0
        ) ?: return

        viewModelScope.launch {
            try {
                repository.saveBacktest(run)
                _uiState.update {
                    it.copy(statusBanner = "Backtest saved: ${run.totalTrades} trades | PF ${"%.2f".format(run.profitFactor)} | WF=${run.walkForwardPassed}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage) }
            }
        }
    }

    fun evaluateAndQueueOrder(
        instrumentSymbol: String,
        direction: String,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        riskPct: Double
    ) {
        val prof = profile.value ?: return
        val inst = instruments.value.firstOrNull { it.symbol == instrumentSymbol }
        val quote = _uiState.value.quotes[instrumentSymbol]

        val eval = RiskEngine.evaluateOrder(
            profile = prof,
            instrument = inst,
            quote = quote,
            direction = direction,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            requestedRiskPct = riskPct,
            activeOrders = orders.value
        )
        _uiState.update { it.copy(lastRiskEvaluation = eval) }

        val mode = ExecutionMode.fromWire(prof.executionMode)
        val initialStatus = when {
            !eval.passed -> "BLOCKED"
            mode == ExecutionMode.MANUAL_APPROVAL -> "PENDING_APPROVAL"
            mode == ExecutionMode.AUTHORISED_AUTOMATION -> "APPROVED"
            else -> "BLOCKED"
        }

        viewModelScope.launch {
            try {
                val order = ExecutionOrderRecord(
                    userId = currentUserId,
                    instrument = instrumentSymbol,
                    direction = direction,
                    volumeLots = eval.recommendedLots,
                    entryPrice = entryPrice,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    riskPercent = riskPct,
                    executionMode = prof.executionMode,
                    status = initialStatus,
                    riskCheckPassed = eval.passed,
                    rejectionReason = eval.blockingReason ?: "",
                    requestNonce = UUID.randomUUID().toString()
                )
                repository.createOrder(order)
                if (!eval.passed) {
                    repository.createAlert(
                        category = "RISK_WARNING",
                        priority = "HIGH",
                        instrument = instrumentSymbol,
                        title = "Order Blocked by Risk Guardian",
                        message = eval.blockingReason ?: "Risk validation failed",
                        sourceEngine = "Risk Engine"
                    )
                }
                _uiState.update {
                    it.copy(statusBanner = if (eval.passed) "Order queued as $initialStatus (${"%.2f".format(eval.recommendedLots)} lots)" else eval.blockingReason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage) }
            }
        }
    }

    fun approveOrCancelOrder(order: ExecutionOrderRecord, approve: Boolean) {
        val prof = profile.value ?: return
        if (approve && prof.emergencyStop) {
            _uiState.update { it.copy(errorBanner = "Global Emergency Stop is ACTIVE. Clear it before approving orders.") }
            return
        }
        viewModelScope.launch {
            try {
                if (!approve) {
                    repository.updateOrderStatus(order.id, "CANCELLED", "Cancelled by operator")
                    return@launch
                }
                val mt5Url = connections.value.firstOrNull { it.id == "mt5_bridge" }?.baseUrl.orEmpty()
                if (mt5Url.isBlank()) {
                    repository.updateOrderStatus(order.id, "APPROVED", "Approved locally (MT5 Bridge disconnected)")
                    _uiState.update { it.copy(statusBanner = "Order approved. Configure MT5 Bridge to transmit live.") }
                } else {
                    val (ok, msg) = mt5BridgeClient.transmitOrder(mt5Url, order)
                    repository.updateOrderStatus(
                        order.id,
                        if (ok) "TRANSMITTED" else "REJECTED",
                        msg
                    )
                    _uiState.update { it.copy(statusBanner = msg) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage) }
            }
        }
    }

    fun updateRiskAndExecutionSettings(
        executionMode: String,
        emergencyStop: Boolean,
        accountBalance: Double,
        riskPerTradePct: Double,
        maxDailyLossPct: Double,
        maxConcurrentPositions: Long
    ) {
        val prof = profile.value ?: return
        viewModelScope.launch {
            try {
                repository.updateProfileRiskAndExecution(
                    profile = prof,
                    executionMode = executionMode,
                    emergencyStop = emergencyStop,
                    accountBalance = accountBalance,
                    riskPerTradePct = riskPerTradePct,
                    maxDailyLossPct = maxDailyLossPct,
                    maxConcurrentPositions = maxConcurrentPositions
                )
                if (emergencyStop) {
                    repository.createAlert(
                        category = "RISK_WARNING",
                        priority = "CRITICAL",
                        instrument = "ALL",
                        title = "GLOBAL EMERGENCY STOP ENGAGED",
                        message = "All execution pathways halted by operator.",
                        sourceEngine = "Execution Permission System"
                    )
                }
                _uiState.update { it.copy(statusBanner = "Execution Policy updated: $executionMode (EmergencyStop=$emergencyStop)") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage) }
            }
        }
    }

    fun toggleEmergencyStop() {
        val prof = profile.value ?: return
        val nextStop = !prof.emergencyStop
        updateRiskAndExecutionSettings(
            executionMode = if (nextStop) "ANALYSIS_ONLY" else prof.executionMode,
            emergencyStop = nextStop,
            accountBalance = prof.accountBalance,
            riskPerTradePct = prof.riskPerTradePct,
            maxDailyLossPct = prof.maxDailyLossPct,
            maxConcurrentPositions = prof.maxConcurrentPositions
        )
    }

    fun getSecretSource(providerId: String): String = secretVault.getSecretSourceLabel(providerId)

    fun saveApiConnection(
        connectionId: String,
        providerName: String,
        category: String,
        baseUrl: String,
        apiSecret: String
    ) {
        viewModelScope.launch {
            if (apiSecret.isNotBlank()) {
                secretVault.saveSecret(connectionId, apiSecret.trim())
            }
            if (connectionId == "binance_secret") {
                val hasKey = secretVault.hasSecret("binance")
                val (ok, info) = marketDataService.testProviderEndpoint("binance", "https://api.binance.com")
                try {
                    repository.upsertConnection(
                        connectionId = "binance",
                        providerName = "Binance Spot & Account API",
                        category = "MARKET_DATA",
                        baseUrl = "https://api.binance.com",
                        status = if (ok) "CONNECTED" else "DISCONNECTED",
                        credentialConfigured = hasKey,
                        rateLimitInfo = "Public Klines + HMAC Account Key",
                        lastLatencyMs = info.first,
                        lastError = if (ok) "" else info.second,
                        enabled = true
                    )
                    repository.logAudit("API_CONNECTION_UPDATED", "CONNECTIONS", "Updated Binance API Secret status=${if (ok) "CONNECTED" else "DISCONNECTED"}", "INFO")
                    _uiState.update { it.copy(statusBanner = "Binance Secret saved: ${info.second}") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorBanner = e.localizedMessage) }
                }
                return@launch
            }

            val hasSecret = secretVault.hasSecret(connectionId) ||
                connectionId in setOf("coinbase_public", "kraken_public", "frankfurter_ecb", "institutional_rss", "yahoo", "binance")
            val (ok, info) = when (category) {
                "MARKET_DATA", "NEWS", "CALENDAR" -> marketDataService.testProviderEndpoint(connectionId, baseUrl)
                "MT5_BRIDGE" -> if (baseUrl.isNotBlank()) {
                    val snap = mt5BridgeClient.pollTelemetry(baseUrl)
                    snap.connected to (0L to (snap.lastError ?: "Connected"))
                } else {
                    false to (0L to "Bridge URL not configured")
                }
                else -> false to (0L to "Saved configuration")
            }

            try {
                repository.upsertConnection(
                    connectionId = connectionId,
                    providerName = providerName,
                    category = category,
                    baseUrl = baseUrl,
                    status = if (ok) "CONNECTED" else "DISCONNECTED",
                    credentialConfigured = hasSecret,
                    rateLimitInfo = secretVault.getSecretSourceLabel(connectionId),
                    lastLatencyMs = info.first,
                    lastError = if (ok) "" else info.second,
                    enabled = true
                )
                repository.logAudit("API_CONNECTION_UPDATED", "CONNECTIONS", "Updated $providerName ($category) status=${if (ok) "CONNECTED" else "DISCONNECTED"}", "INFO")
                _uiState.update { it.copy(statusBanner = "$providerName: ${info.second}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage) }
            }
        }
    }

    fun testConnection(conn: ApiConnectionConfig) {
        viewModelScope.launch {
            if (conn.id == "mt5_bridge") {
                pollMt5Bridge(conn.baseUrl)
                return@launch
            }
            val (ok, result) = marketDataService.testProviderEndpoint(conn.id, conn.baseUrl)
            try {
                repository.upsertConnection(
                    connectionId = conn.id,
                    providerName = conn.providerName,
                    category = conn.category,
                    baseUrl = conn.baseUrl,
                    status = if (ok) "CONNECTED" else "ERROR",
                    credentialConfigured = conn.credentialConfigured,
                    rateLimitInfo = conn.rateLimitInfo,
                    lastLatencyMs = result.first,
                    lastError = if (ok) "" else result.second,
                    enabled = conn.enabled
                )
                _uiState.update { it.copy(statusBanner = "${conn.providerName}: ${result.second} (${result.first}ms)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage) }
            }
        }
    }

    fun pollMt5Bridge(overrideUrl: String? = null) {
        val url = overrideUrl ?: connections.value.firstOrNull { it.id == "mt5_bridge" }?.baseUrl.orEmpty()
        viewModelScope.launch {
            val snap = mt5BridgeClient.pollTelemetry(url)
            _uiState.update { it.copy(mt5Telemetry = snap) }
            try {
                repository.upsertConnection(
                    connectionId = "mt5_bridge",
                    providerName = "MetaTrader 5 Bridge EA",
                    category = "MT5_BRIDGE",
                    baseUrl = url,
                    status = if (snap.connected) "CONNECTED" else "DISCONNECTED",
                    credentialConfigured = secretVault.hasSecret("mt5_bridge"),
                    rateLimitInfo = "HMAC Signed + Replay Nonce",
                    lastLatencyMs = 0L,
                    lastError = snap.lastError.orEmpty(),
                    enabled = snap.connected
                )
            } catch (_: Exception) {
            }
        }
    }

    fun addWatchlistInstrument(symbol: String, displayName: String, assetClass: String, providerId: String, providerSymbol: String) {
        viewModelScope.launch {
            try {
                val pipSize = when (assetClass) {
                    "FOREX" -> 0.0001
                    "COMMODITY" -> 0.1
                    "EQUITY", "INDEX" -> 0.01
                    else -> 1.0
                }
                repository.addInstrument(symbol, displayName, assetClass, providerId, providerSymbol, pipSize)
                _uiState.update { it.copy(statusBanner = "Added $symbol ($providerId) to watchlist") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage) }
            }
        }
    }

    fun toggleWatchlistInstrument(inst: WatchlistInstrument) {
        viewModelScope.launch {
            try {
                repository.toggleInstrument(inst, !inst.enabled)
            } catch (_: Exception) {
            }
        }
    }

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            try {
                repository.acknowledgeAlert(alertId)
            } catch (_: Exception) {
            }
        }
    }

    fun buildStrategyFromNaturalLanguage(instruction: String) {
        if (instruction.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiRunning = true, errorBanner = null) }
            val res = aiOrchestrator.translateNaturalLanguageStrategy(instruction)
            res.fold(
                onSuccess = { spec ->
                    _uiState.update { it.copy(isAiRunning = false, strategyDraft = spec, statusBanner = "Strategy specification compiled") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isAiRunning = false, errorBanner = err.localizedMessage) }
                }
            )
        }
    }

    fun toggleGhostMonitor() {
        val next = !_uiState.value.ghostMonitorActive
        _uiState.update { it.copy(ghostMonitorActive = next) }
        ghostMonitorJob?.cancel()
        if (next) {
            ghostMonitorJob = viewModelScope.launch {
                while (isActive) {
                    runOmniscanner()
                    delay(45_000L)
                }
            }
        }
    }

    fun executeVoiceCommand(rawCommand: String) {
        val intent = VoiceCommandParser.parse(rawCommand)
        when (intent) {
            is VoiceIntent.ScanInstrument -> {
                selectInstrument(intent.symbolHint)
                runOmniscanner(singleSymbol = intent.symbolHint)
                _uiState.update { it.copy(voiceTranscriptResponse = "Executing Omniscanner on ${intent.symbolHint}.") }
            }
            is VoiceIntent.InvestigateInstrument -> {
                selectInstrument(intent.symbolHint)
                selectWorkspace(WorkspaceDestination.MULTI_AI_COUNCIL)
                runMultiAiInvestigation(intent.symbolHint)
                _uiState.update { it.copy(voiceTranscriptResponse = "Convening Multi-AI Council on ${intent.symbolHint}.") }
            }
            is VoiceIntent.RunGlobalScan -> {
                selectWorkspace(WorkspaceDestination.OPPORTUNITY_RADAR)
                runOmniscanner()
                _uiState.update { it.copy(voiceTranscriptResponse = "Scanning all enabled instruments across live feeds.") }
            }
            is VoiceIntent.ShowBullishSetups -> {
                selectWorkspace(WorkspaceDestination.SETUP_GENERATOR)
                _uiState.update { it.copy(voiceTranscriptResponse = "Opened Setup Generator filtered to active setups.") }
            }
            is VoiceIntent.InvestigateNews -> {
                selectWorkspace(WorkspaceDestination.NEWS_INTELLIGENCE)
                fetchLiveNews()
                _uiState.update { it.copy(voiceTranscriptResponse = "Fetching Federal Reserve, ECB, and CoinDesk RSS intelligence.") }
            }
            is VoiceIntent.GenerateDailyBriefing -> {
                selectWorkspace(WorkspaceDestination.NEWS_INTELLIGENCE)
                generateDailyBriefing()
                _uiState.update { it.copy(voiceTranscriptResponse = "Generating APEX Daily Intelligence Briefing.") }
            }
            is VoiceIntent.ShowRiskExposure -> {
                selectWorkspace(WorkspaceDestination.RISK_COMMAND_CENTRE)
                _uiState.update {
                    val p = profile.value
                    it.copy(voiceTranscriptResponse = "Risk Command: Balance $${p?.accountBalance ?: 0.0}, Max Risk/Trade ${p?.riskPerTradePct ?: 0.0}%, Mode ${p?.executionMode ?: "ANALYSIS_ONLY"}")
                }
            }
            is VoiceIntent.Navigate -> {
                selectWorkspace(intent.destination)
                _uiState.update { it.copy(voiceTranscriptResponse = "Navigated to ${intent.destination.title}.") }
            }
            is VoiceIntent.AskChiefAnalyst -> {
                selectWorkspace(WorkspaceDestination.MULTI_AI_COUNCIL)
                runMultiAiInvestigation()
                _uiState.update { it.copy(voiceTranscriptResponse = "Routing query to Chief Analyst AI (Note: Voice never authorizes trade execution).") }
            }
        }
    }

    // ---- 12 Institutional Intelligence Extensions ----

    fun runStrategyMutationLab(
        fastEma: Int = 10,
        slowEma: Int = 21,
        spreadPct: Double = 0.04,
        slippagePct: Double = 0.02
    ) {
        val sym = _uiState.value.selectedInstrument
        val candles = _uiState.value.candlesBySymbol[sym].orEmpty()
        if (candles.size < 45) {
            _uiState.update { it.copy(errorBanner = "Strategy DNA Mutation Lab needs 45+ live candles on $sym. Run Omniscanner X first.") }
            return
        }
        val variants = StrategyMutationEngine.runMutationLab(
            candles = candles,
            baseFastEma = fastEma,
            baseSlowEma = slowEma,
            spreadPct = spreadPct,
            slippagePct = slippagePct,
            riskPerTradePct = profile.value?.riskPerTradePct ?: 1.0
        )
        _uiState.update { cur ->
            val updated = cur.strategyVariantsBySymbol.toMutableMap()
            updated[sym] = variants
            val promoted = variants.count { it.recommendation == "PROMOTE" }
            cur.copy(
                strategyVariantsBySymbol = updated,
                errorBanner = null,
                statusBanner = "Strategy DNA Mutation Lab: Tested ${variants.size} versions on $sym ($promoted PROMOTE)"
            )
        }
    }

    fun scanPatternFingerprints() {
        val sym = _uiState.value.selectedInstrument
        val candles = _uiState.value.candlesBySymbol[sym].orEmpty()
        if (candles.size < 40) {
            _uiState.update { it.copy(errorBanner = "Pattern Fingerprint Scanner needs 40+ live candles on $sym. Run Omniscanner X first.") }
            return
        }
        val matches = MarketDnaEngine.scanPatternFingerprints(candles)
        _uiState.update { cur ->
            val updated = cur.patternFingerprintsBySymbol.toMutableMap()
            updated[sym] = matches
            cur.copy(
                patternFingerprintsBySymbol = updated,
                errorBanner = null,
                statusBanner = "Fingerprint Scanner matched ${matches.size} comparable historical sequences on $sym"
            )
        }
    }

    fun runSupremeTribunal(symbol: String = _uiState.value.selectedInstrument) {
        val tech = _uiState.value.technicalBySymbol[symbol]
        val candles = _uiState.value.candlesBySymbol[symbol].orEmpty()
        if (tech == null || candles.isEmpty()) {
            _uiState.update { it.copy(errorBanner = "Run Omniscanner X on $symbol before convening Supreme AI Tribunal.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTribunalRunning = true, errorBanner = null, statusBanner = "Supreme AI Tribunal cross-examining $symbol...") }
            val news = _uiState.value.newsArticles.filter { symbol in it.affectedInstruments }
            val res = apexAiOrchestrator.runSupremeTribunal(tech, candles, news)
            res.fold(
                onSuccess = { record ->
                    _uiState.update { cur ->
                        cur.copy(
                            isTribunalRunning = false,
                            tribunalRecords = (listOf(record) + cur.tribunalRecords).take(10),
                            statusBanner = "Supreme Tribunal Verdict issued for $symbol (Consensus ${record.consensusScore}%)"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isTribunalRunning = false, errorBanner = "Supreme Tribunal failed: ${err.localizedMessage}") }
                }
            )
        }
    }

    fun runAutonomousSwarm() {
        val enabled = instruments.value.filter { it.enabled }
        if (enabled.isEmpty()) {
            _uiState.update { it.copy(errorBanner = "No enabled watchlist instruments for Autonomous Intelligence Swarm.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSwarmRunning = true, errorBanner = null, statusBanner = "Dispatching Autonomous Swarm across ${enabled.size} markets...") }
            val tf = _uiState.value.selectedTimeframe
            val swarmJobs = enabled.map { inst ->
                async {
                    val bundle = marketDataService.fetchInstrumentData(inst, tf)
                    val snap = if (bundle.quote.health == ConnectionHealth.LIVE && bundle.candles.isNotEmpty()) {
                        TechnicalAnalysisEngine.analyze(inst.symbol, tf, bundle.candles)
                    } else null
                    Triple(inst, bundle, snap)
                }
            }.awaitAll()

            val newQuotes = _uiState.value.quotes.toMutableMap()
            val newCandles = _uiState.value.candlesBySymbol.toMutableMap()
            val newTech = _uiState.value.technicalBySymbol.toMutableMap()

            val reports = swarmJobs.map { (inst, bundle, snap) ->
                newQuotes[inst.symbol] = bundle.quote
                if (bundle.candles.isNotEmpty()) newCandles[inst.symbol] = bundle.candles
                if (snap != null) newTech[inst.symbol] = snap
                val verdict = if (snap == null) {
                    bundle.quote.errorMessage ?: "Feed offline or insufficient candles"
                } else {
                    "${snap.regime.label} | ${snap.structureBias} | RSI ${"%.1f".format(snap.rsi14)} | ${snap.liquiditySweepType}"
                }
                SwarmMarketReport(
                    instrument = inst.symbol,
                    timeframe = tf,
                    providerId = inst.providerId,
                    health = bundle.quote.health,
                    lastClose = snap?.lastClose ?: bundle.quote.lastPrice,
                    opportunityScore = snap?.opportunityScore ?: 0,
                    structureBias = snap?.structureBias ?: "DISCONNECTED",
                    regimeLabel = snap?.regime?.label ?: "Offline",
                    aiVerdictSummary = verdict,
                    swarmConfidence = snap?.opportunityScore ?: 0,
                    latencyMs = bundle.quote.latencyMs,
                    error = bundle.quote.errorMessage
                )
            }.sortedByDescending { it.opportunityScore }

            _uiState.update { cur ->
                val divergences = ApexRadarEngine.detectSentimentDivergences(newQuotes, newTech, cur.newsArticles, cur.macroIndicators)
                val storms = ApexRadarEngine.detectPerfectStorms(newTech, newQuotes, cur.newsArticles, setups.value)
                cur.copy(
                    isSwarmRunning = false,
                    quotes = newQuotes,
                    candlesBySymbol = newCandles,
                    technicalBySymbol = newTech,
                    swarmReports = reports,
                    sentimentDivergences = divergences,
                    perfectStormSignals = storms,
                    statusBanner = "Autonomous Swarm finished: ${reports.count { it.health == ConnectionHealth.LIVE }}/${reports.size} markets investigated"
                )
            }
        }
    }

    fun runHistoricalRealitySimulation(cutoffPct: Float, useAiReasoning: Boolean) {
        val sym = _uiState.value.selectedInstrument
        val tf = _uiState.value.selectedTimeframe
        val candles = _uiState.value.candlesBySymbol[sym].orEmpty()
        val pair = ApexRadarEngine.buildBlindHistoricalSlice(sym, tf, candles, cutoffPct)
        if (pair == null) {
            _uiState.update { it.copy(errorBanner = "Need at least 35 live candles on $sym ($tf) for Historical Reality Simulator.") }
            return
        }
        val (blindSlice, blindSnap) = pair
        val cutoffBar = blindSlice.last()

        if (!useAiReasoning) {
            val sim = HistoricalRealitySimulation(
                instrument = sym,
                timeframe = tf,
                totalCandles = candles.size,
                cutoffIndex = blindSlice.size - 1,
                cutoffTimestampSec = cutoffBar.timestampEpochSec,
                cutoffClose = cutoffBar.close,
                horizonBars = 8,
                blindBias = blindSnap.structureBias,
                blindReasoning = "Deterministic Zero-Hindsight Assessment: ${blindSnap.regime.label}, RSI ${"%.1f".format(blindSnap.rsi14)}, ${blindSnap.liquiditySweepType}.",
                blindConfidence = blindSnap.opportunityScore,
                blindKeyLevel = if (blindSnap.structureBias == "BEARISH") blindSnap.swingLow else blindSnap.swingHigh,
                blindInvalidation = if (blindSnap.structureBias == "BEARISH") blindSnap.swingHigh else blindSnap.swingLow,
                futureRevealed = false
            )
            _uiState.update { it.copy(historicalSimulation = sim, errorBanner = null, statusBanner = "Blind Cutoff Locked at bar #${blindSlice.size}/${candles.size} (Zero Hindsight)") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSimRunning = true, errorBanner = null) }
            val aiRes = apexAiOrchestrator.evaluateHistoricalBlindSlice(blindSnap, blindSlice)
            aiRes.fold(
                onSuccess = { json ->
                    val sim = HistoricalRealitySimulation(
                        instrument = sym,
                        timeframe = tf,
                        totalCandles = candles.size,
                        cutoffIndex = blindSlice.size - 1,
                        cutoffTimestampSec = cutoffBar.timestampEpochSec,
                        cutoffClose = cutoffBar.close,
                        horizonBars = 8,
                        blindBias = json.optString("blindBias", blindSnap.structureBias),
                        blindReasoning = json.optString("blindReasoning", "AI zero-hindsight reasoning complete"),
                        blindConfidence = json.optInt("blindConfidence", blindSnap.opportunityScore).coerceIn(0, 100),
                        blindKeyLevel = json.optDouble("blindKeyLevel", blindSnap.swingHigh),
                        blindInvalidation = json.optDouble("blindInvalidation", blindSnap.swingLow),
                        futureRevealed = false
                    )
                    _uiState.update { it.copy(isSimRunning = false, historicalSimulation = sim, statusBanner = "Blind AI forecast locked (Future candles still hidden)") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSimRunning = false, errorBanner = "Blind AI Simulator failed: ${err.localizedMessage}") }
                }
            )
        }
    }

    fun revealHistoricalRealityOutcome() {
        val current = _uiState.value.historicalSimulation ?: return
        val fullCandles = _uiState.value.candlesBySymbol[current.instrument].orEmpty()
        val revealed = ApexRadarEngine.revealAndGradeHistoricalReality(current, fullCandles)
        _uiState.update { it.copy(historicalSimulation = revealed, statusBanner = revealed.gradingNotes) }
    }

    fun investigateNewsArticle(article: NewsArticleItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiRunning = true, errorBanner = null, statusBanner = "AI News Team verifying '${article.title.take(36)}...'") }
            val res = apexAiOrchestrator.investigateNewsStory(article, _uiState.value.newsArticles)
            res.fold(
                onSuccess = { report ->
                    _uiState.update { cur ->
                        val filtered = cur.newsInvestigations.filterNot { it.articleId == report.articleId }
                        cur.copy(
                            isAiRunning = false,
                            newsInvestigations = listOf(report) + filtered,
                            statusBanner = "News Investigation Team audit complete for ${article.sourceName}"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isAiRunning = false, errorBanner = "News Investigation failed: ${err.localizedMessage}") }
                }
            )
        }
    }

    fun analyzeCentralBanksWithAi() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiRunning = true, errorBanner = null) }
            val cur = _uiState.value
            val banks = newsIngestionService.buildCentralBankStatuses(cur.newsArticles, cur.macroIndicators)
            val res = apexAiOrchestrator.analyzeCentralBankPolicy(banks, cur.macroIndicators)
            res.fold(
                onSuccess = { brief ->
                    _uiState.update { it.copy(isAiRunning = false, centralBankStatuses = banks, centralBankBrief = brief, statusBanner = "Central Bank Policy Intelligence updated") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isAiRunning = false, errorBanner = "Central Bank AI failed: ${err.localizedMessage}") }
                }
            )
        }
    }

    fun updateScannerCriteria(config: ScannerCriteriaConfig) {
        _uiState.update { it.copy(scannerCriteria = config) }
    }

    fun runWatchtowerAnomalySweep() {
        val cur = _uiState.value
        val anomalies = ApexRadarEngine.runWatchtowerSweep(
            connections = connections.value,
            technicals = cur.technicalBySymbol,
            quotes = cur.quotes,
            setups = setups.value,
            orders = orders.value,
            profile = profile.value,
            ghostActive = cur.ghostMonitorActive
        )
        _uiState.update {
            it.copy(
                watchtowerAnomalies = anomalies,
                statusBanner = "Omniscient Watchtower Sweep: ${anomalies.size} prioritized item(s) detected"
            )
        }
    }

    private companion object {
        const val TAG = "JaxViewModel"
    }
}
