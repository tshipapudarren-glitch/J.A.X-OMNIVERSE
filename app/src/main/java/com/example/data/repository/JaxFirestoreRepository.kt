package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.domain.model.AiInvestigationReport
import com.example.domain.model.AlertRecord
import com.example.domain.model.ApiConnectionConfig
import com.example.domain.model.AuditLogRecord
import com.example.domain.model.BacktestRunRecord
import com.example.domain.model.ExecutionOrderRecord
import com.example.domain.model.TradingSetup
import com.example.domain.model.UserWorkspaceProfile
import com.example.domain.model.WatchlistInstrument
import com.example.security.OperationType
import com.example.security.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.tasks.await

class JaxFirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> handleStreamError(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    fun observeProfile(userId: String): Flow<UserWorkspaceProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserWorkspaceProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { e -> handleStreamError<UserWorkspaceProfile?>(e, OperationType.GET, docRef.path, userId) }
        )
    }

    suspend fun ensureProfileInitialized(
        displayName: String,
        configuredProviders: Set<String> = emptySet()
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.take(120).ifBlank { "JAX Operator" },
                    "role" to "analyst",
                    "executionMode" to "ANALYSIS_ONLY",
                    "emergencyStop" to false,
                    "accountBalance" to 100000.0,
                    "riskPerTradePct" to 1.0,
                    "maxDailyLossPct" to 4.0,
                    "maxConcurrentPositions" to 3L,
                    "preferredInstruments" to "BTC-USD,ETH-USD,PAXG-USD,EUR-USD",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                seedDefaultInstrumentsAndConnections(uid, configuredProviders)
                logAudit("OPERATOR_SESSION_INIT", "SECURITY", "Initialized operator workspace in ANALYSIS_ONLY mode", "INFO")
            } else {
                ensureDefaultConnectionsPresent(uid, configuredProviders)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    private suspend fun seedDefaultInstrumentsAndConnections(
        uid: String,
        configuredProviders: Set<String>
    ) {
        val instrumentsCol = db.collection("users").document(uid).collection("instruments")
        val existingInstruments = instrumentsCol.limit(1).get().await()
        if (existingInstruments.isEmpty) {
            val defaults = listOf(
                Triple("btc_usd", "BTC-USD", Triple("Bitcoin / USD", "CRYPTO", "coinbase_public")),
                Triple("eth_usd", "ETH-USD", Triple("Ethereum / USD", "CRYPTO", "coinbase_public")),
                Triple("paxg_usd", "PAXG-USD", Triple("Gold Proxy (PAXG/USD)", "COMMODITY", "kraken_public")),
                Triple("eur_usd", "EUR-USD", Triple("Euro / US Dollar", "FOREX", "frankfurter_ecb")),
                Triple("gbp_usd", "GBP-USD", Triple("British Pound / USD", "FOREX", "frankfurter_ecb")),
                Triple("usd_jpy", "USD-JPY", Triple("US Dollar / Yen", "FOREX", "frankfurter_ecb")),
                Triple("spy_yahoo", "SPY", Triple("S&P 500 ETF (Yahoo)", "INDEX", "yahoo")),
                Triple("sol_binance", "SOL-USDT", Triple("Solana / USDT (Binance)", "CRYPTO", "binance"))
            )
            defaults.forEach { (docId, symbol, meta) ->
                val providerSym = when (symbol) {
                    "PAXG-USD" -> "PAXGUSD"
                    "EUR-USD" -> "EUR"
                    "GBP-USD" -> "GBP"
                    "USD-JPY" -> "JPY"
                    "SOL-USDT" -> "SOLUSDT"
                    else -> symbol
                }
                val pipSize = when {
                    symbol == "USD-JPY" -> 0.01
                    meta.second == "FOREX" -> 0.0001
                    meta.second == "COMMODITY" -> 0.1
                    meta.second == "INDEX" -> 0.01
                    else -> 1.0
                }
                instrumentsCol.document(docId).set(
                    mapOf(
                        "userId" to uid,
                        "symbol" to symbol,
                        "displayName" to meta.first,
                        "assetClass" to meta.second,
                        "providerId" to meta.third,
                        "providerSymbol" to providerSym,
                        "pipSize" to pipSize,
                        "contractSize" to 1.0,
                        "enabled" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        ensureDefaultConnectionsPresent(uid, configuredProviders)
    }

    private suspend fun ensureDefaultConnectionsPresent(
        uid: String,
        configuredProviders: Set<String>
    ) {
        val connCol = db.collection("users").document(uid).collection("connections")
        val existingIds = connCol.get().await().documents.map { it.id }.toSet()

        fun connDoc(
            id: String,
            name: String,
            category: String,
            baseUrl: String,
            rateLimit: String,
            defaultStatus: String = "DISCONNECTED",
            defaultErr: String = "",
            defaultEnabled: Boolean = true
        ): Pair<String, Map<String, Any>> {
            val hasCred = id in configuredProviders
            return id to mapOf(
                "userId" to uid,
                "providerName" to name,
                "category" to category,
                "baseUrl" to baseUrl,
                "status" to defaultStatus,
                "credentialConfigured" to hasCred,
                "rateLimitInfo" to rateLimit,
                "lastLatencyMs" to 0L,
                "lastError" to if (hasCred || defaultErr.isBlank()) defaultErr else "API key not configured",
                "enabled" to defaultEnabled,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }

        val catalog = listOf(
            connDoc("coinbase_public", "Coinbase Exchange Spot", "MARKET_DATA", "https://api.exchange.coinbase.com", "Public REST (10 req/s)"),
            connDoc("kraken_public", "Kraken Public Market API", "MARKET_DATA", "https://api.kraken.com", "Public OHLC + Ticker"),
            connDoc("frankfurter_ecb", "Frankfurter ECB FX Feed", "MARKET_DATA", "https://api.frankfurter.app", "ECB Official FX Rates"),
            connDoc("binance", "Binance Spot & Account API", "MARKET_DATA", "https://api.binance.com", "Public Klines + Optional HMAC Key"),
            connDoc("yahoo", "Yahoo Finance Market Feed", "MARKET_DATA", "https://query1.finance.yahoo.com", "Equities, Indices, Futures & FX"),
            connDoc("finnhub", "Finnhub Equities & Calendar", "CALENDAR", "https://finnhub.io/api/v1", "60 req/min (Quotes, News, Calendar)"),
            connDoc("fmp", "Financial Modeling Prep (FMP)", "CALENDAR", "https://financialmodelingprep.com", "Equities, Historical & Economic Calendar"),
            connDoc("alpha_vantage", "Alpha Vantage Market & Sentiment", "NEWS", "https://www.alphavantage.co", "25 req/day Free (OHLCV + News Sentiment)"),
            connDoc("fred", "FRED — St. Louis Fed Macro", "CALENDAR", "https://api.stlouisfed.org/fred", "120 req/min (Rates, CPI, Yields, GDP)"),
            connDoc("twelvedata", "TwelveData Market API", "MARKET_DATA", "https://api.twelvedata.com", "800 req/day Free (Stocks/FX/Crypto)"),
            connDoc("institutional_rss", "Institutional RSS & Fed/ECB Feeds", "NEWS", "https://www.federalreserve.gov/feeds/press_all.xml", "Live XML Feed"),
            connDoc("firebase_ai_logic", "Firebase AI Logic (Gemini 3.8 / 3.5)", "AI_MODEL", "firebase.ai://appcheck-attested", "App Check Attested Router", defaultStatus = "CONNECTED"),
            connDoc("mt5_bridge", "MetaTrader 5 Bridge EA", "MT5_BRIDGE", "", "HMAC Signed + Replay Nonce", defaultErr = "Bridge URL not configured", defaultEnabled = false)
        )

        catalog.forEach { (id, payload) ->
            if (id !in existingIds) {
                connCol.document(id).set(payload).await()
            } else if (id in configuredProviders) {
                // Keep credentialConfigured synced when AI Studio Secrets are supplied
                val existingDoc = connCol.document(id).get().await()
                if (existingDoc.exists() && existingDoc.getBoolean("credentialConfigured") == false) {
                    connCol.document(id).update(
                        mapOf(
                            "credentialConfigured" to true,
                            "lastError" to "",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }
    }

    suspend fun updateProfileRiskAndExecution(
        profile: UserWorkspaceProfile,
        executionMode: String,
        emergencyStop: Boolean,
        accountBalance: Double,
        riskPerTradePct: Double,
        maxDailyLossPct: Double,
        maxConcurrentPositions: Long
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "executionMode" to executionMode,
                    "emergencyStop" to emergencyStop,
                    "accountBalance" to accountBalance,
                    "riskPerTradePct" to riskPerTradePct,
                    "maxDailyLossPct" to maxDailyLossPct,
                    "maxConcurrentPositions" to maxConcurrentPositions,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(
                action = "RISK_EXECUTION_POLICY_UPDATED",
                category = "RISK",
                details = "Mode=$executionMode, EmergencyStop=$emergencyStop, RiskPerTrade=${riskPerTradePct}%",
                severity = if (emergencyStop) "CRITICAL" else "WARNING"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeInstruments(userId: String): Flow<List<WatchlistInstrument>> = flow {
        val col = db.collection("users").document(userId).collection("instruments")
        emitAll(
            col.snapshots()
                .map { it.toObjects(WatchlistInstrument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e -> handleStreamError<List<WatchlistInstrument>>(e, OperationType.LIST, col.path, userId) }
        )
    }

    suspend fun addInstrument(
        symbol: String,
        displayName: String,
        assetClass: String,
        providerId: String,
        providerSymbol: String,
        pipSize: Double
    ): String {
        val uid = requireUserId()
        val cleanId = symbol.lowercase().replace(Regex("[^a-z0-9_\\-]"), "_")
        val docRef = db.collection("users").document(uid).collection("instruments").document(cleanId)
        val payload = mapOf(
            "userId" to uid,
            "symbol" to symbol.trim().uppercase().take(24),
            "displayName" to displayName.trim().take(80),
            "assetClass" to assetClass,
            "providerId" to providerId,
            "providerSymbol" to providerSymbol.trim().take(40),
            "pipSize" to pipSize.coerceIn(0.00001, 1000.0),
            "contractSize" to 1.0,
            "enabled" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            logAudit("INSTRUMENT_ADDED", "WATCHLIST", "Added $symbol ($providerId)", "INFO")
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleInstrument(instrument: WatchlistInstrument, enabled: Boolean) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("instruments").document(instrument.id)
        try {
            docRef.update(
                mapOf(
                    "enabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeConnections(userId: String): Flow<List<ApiConnectionConfig>> = flow {
        val col = db.collection("users").document(userId).collection("connections")
        emitAll(
            col.snapshots()
                .map { it.toObjects(ApiConnectionConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e -> handleStreamError<List<ApiConnectionConfig>>(e, OperationType.LIST, col.path, userId) }
        )
    }

    suspend fun upsertConnection(
        connectionId: String,
        providerName: String,
        category: String,
        baseUrl: String,
        status: String,
        credentialConfigured: Boolean,
        rateLimitInfo: String,
        lastLatencyMs: Long,
        lastError: String,
        enabled: Boolean
    ) {
        val uid = requireUserId()
        val safeId = connectionId.ifBlank { "conn_${System.currentTimeMillis()}" }
        val docRef = db.collection("users").document(uid).collection("connections").document(safeId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "providerName" to providerName.take(80),
                        "baseUrl" to baseUrl.take(300),
                        "status" to status,
                        "credentialConfigured" to credentialConfigured,
                        "rateLimitInfo" to rateLimitInfo.take(120),
                        "lastLatencyMs" to lastLatencyMs.coerceAtLeast(0L),
                        "lastError" to lastError.take(500),
                        "enabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "providerName" to providerName.take(80),
                        "category" to category,
                        "baseUrl" to baseUrl.take(300),
                        "status" to status,
                        "credentialConfigured" to credentialConfigured,
                        "rateLimitInfo" to rateLimitInfo.take(120),
                        "lastLatencyMs" to lastLatencyMs.coerceAtLeast(0L),
                        "lastError" to lastError.take(500),
                        "enabled" to enabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeSetups(userId: String): Flow<List<TradingSetup>> = flow {
        val col = db.collection("users").document(userId).collection("setups")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
        emitAll(
            col.snapshots()
                .map { it.toObjects(TradingSetup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e -> handleStreamError<List<TradingSetup>>(e, OperationType.LIST, "users/$userId/setups", userId) }
        )
    }

    suspend fun saveSetup(setup: TradingSetup): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("setups").document()
        val payload = mapOf(
            "userId" to uid,
            "instrument" to setup.instrument.take(24),
            "direction" to setup.direction,
            "setupType" to setup.setupType.take(80),
            "status" to setup.status,
            "timeframe" to setup.timeframe.take(16),
            "higherTimeframeBias" to setup.higherTimeframeBias.take(40),
            "entryZoneLow" to setup.entryZoneLow,
            "entryZoneHigh" to setup.entryZoneHigh.coerceAtLeast(setup.entryZoneLow),
            "stopLoss" to setup.stopLoss,
            "tp1" to setup.tp1,
            "tp2" to setup.tp2,
            "tp3" to setup.tp3,
            "rr1" to setup.rr1,
            "score" to setup.score.coerceIn(0L, 100L),
            "evidenceSummary" to setup.evidenceSummary.take(2000),
            "confirmationConditions" to setup.confirmationConditions.take(1000),
            "invalidationConditions" to setup.invalidationConditions.take(1000),
            "dataQualityStatus" to setup.dataQualityStatus.take(80),
            "newsExposure" to setup.newsExposure.take(300),
            "aiSummary" to setup.aiSummary.take(2000),
            "version" to setup.version.coerceAtLeast(1L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            logAudit("SETUP_GENERATED", "SETUP_ENGINE", "${setup.instrument} ${setup.direction} (${setup.status}) score=${setup.score}", "INFO")
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateSetupStatus(setup: TradingSetup, newStatus: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("setups").document(setup.id)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "version" to (setup.version + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("SETUP_STATUS_CHANGED", "SETUP_LIFECYCLE", "${setup.instrument} -> $newStatus (v${setup.version + 1})", "INFO")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeInvestigations(userId: String): Flow<List<AiInvestigationReport>> = flow {
        val col = db.collection("users").document(userId).collection("investigations")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(25)
        emitAll(
            col.snapshots()
                .map { it.toObjects(AiInvestigationReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e -> handleStreamError<List<AiInvestigationReport>>(e, OperationType.LIST, "users/$userId/investigations", userId) }
        )
    }

    suspend fun saveInvestigation(report: AiInvestigationReport): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("investigations").document()
        val payload = mapOf(
            "userId" to uid,
            "instrument" to report.instrument.take(24),
            "timeframe" to report.timeframe.take(16),
            "regime" to report.regime.take(80),
            "bullishArgument" to report.bullishArgument.take(3000),
            "bearishArgument" to report.bearishArgument.take(3000),
            "liquidityInterpretation" to report.liquidityInterpretation.take(2000),
            "volumeInterpretation" to report.volumeInterpretation.take(2000),
            "macroInterpretation" to report.macroInterpretation.take(2000),
            "riskAssessment" to report.riskAssessment.take(2000),
            "verificationReport" to report.verificationReport.take(2000),
            "chiefAnalystVerdict" to report.chiefAnalystVerdict.take(3000),
            "disagreements" to report.disagreements.take(2000),
            "confidenceScore" to report.confidenceScore.coerceIn(0L, 100L),
            "verified" to report.verified,
            "modelRouterInfo" to report.modelRouterInfo.take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            logAudit("AI_COUNCIL_INVESTIGATION", "MULTI_AI", "${report.instrument} verified=${report.verified} conf=${report.confidenceScore}", "INFO")
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeAlerts(userId: String): Flow<List<AlertRecord>> = flow {
        val col = db.collection("users").document(userId).collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
        emitAll(
            col.snapshots()
                .map { it.toObjects(AlertRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e -> handleStreamError<List<AlertRecord>>(e, OperationType.LIST, "users/$userId/alerts", userId) }
        )
    }

    suspend fun createAlert(
        category: String,
        priority: String,
        instrument: String,
        title: String,
        message: String,
        sourceEngine: String
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("alerts").document()
        try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "category" to category.take(64),
                    "priority" to priority,
                    "instrument" to instrument.take(24),
                    "title" to title.take(160),
                    "message" to message.take(1000),
                    "acknowledged" to false,
                    "sourceEngine" to sourceEngine.take(80),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun acknowledgeAlert(alertId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("alerts").document(alertId)
        try {
            docRef.update(
                mapOf(
                    "acknowledged" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeOrders(userId: String): Flow<List<ExecutionOrderRecord>> = flow {
        val col = db.collection("users").document(userId).collection("orders")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
        emitAll(
            col.snapshots()
                .map { it.toObjects(ExecutionOrderRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e -> handleStreamError<List<ExecutionOrderRecord>>(e, OperationType.LIST, "users/$userId/orders", userId) }
        )
    }

    suspend fun createOrder(order: ExecutionOrderRecord): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("orders").document()
        val payload = mapOf(
            "userId" to uid,
            "instrument" to order.instrument.take(24),
            "direction" to order.direction,
            "volumeLots" to order.volumeLots.coerceIn(0.01, 1000.0),
            "entryPrice" to order.entryPrice,
            "stopLoss" to order.stopLoss,
            "takeProfit" to order.takeProfit,
            "riskPercent" to order.riskPercent.coerceIn(0.0, 10.0),
            "executionMode" to order.executionMode,
            "status" to order.status,
            "riskCheckPassed" to order.riskCheckPassed,
            "rejectionReason" to order.rejectionReason.take(500),
            "requestNonce" to order.requestNonce.take(128),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            logAudit(
                action = "ORDER_RECORDED_${order.status}",
                category = "EXECUTION",
                details = "${order.instrument} ${order.direction} ${order.volumeLots} lots (${order.executionMode})",
                severity = if (order.riskCheckPassed) "INFO" else "WARNING"
            )
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateOrderStatus(orderId: String, newStatus: String, reason: String = "") {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("orders").document(orderId)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "rejectionReason" to reason.take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("ORDER_$newStatus", "EXECUTION", "Order $orderId -> $newStatus $reason", "WARNING")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeBacktests(userId: String): Flow<List<BacktestRunRecord>> = flow {
        val col = db.collection("users").document(userId).collection("backtests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
        emitAll(
            col.snapshots()
                .map { it.toObjects(BacktestRunRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e -> handleStreamError<List<BacktestRunRecord>>(e, OperationType.LIST, "users/$userId/backtests", userId) }
        )
    }

    suspend fun saveBacktest(run: BacktestRunRecord): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("backtests").document()
        try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "strategyName" to run.strategyName.take(100),
                    "instrument" to run.instrument.take(24),
                    "timeframe" to run.timeframe.take(16),
                    "totalTrades" to run.totalTrades,
                    "winRatePct" to run.winRatePct,
                    "profitFactor" to run.profitFactor,
                    "expectancyR" to run.expectancyR,
                    "maxDrawdownPct" to run.maxDrawdownPct,
                    "netReturnPct" to run.netReturnPct,
                    "spreadAssumed" to run.spreadAssumed,
                    "slippageAssumed" to run.slippageAssumed,
                    "walkForwardPassed" to run.walkForwardPassed,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("BACKTEST_RECORDED", "QUANT_LAB", "${run.strategyName} on ${run.instrument} (${run.totalTrades} trades)", "INFO")
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeAuditLogs(userId: String): Flow<List<AuditLogRecord>> = flow {
        val col = db.collection("users").document(userId).collection("audit_logs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
        emitAll(
            col.snapshots()
                .map { it.toObjects(AuditLogRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { e -> handleStreamError<List<AuditLogRecord>>(e, OperationType.LIST, "users/$userId/audit_logs", userId) }
        )
    }

    suspend fun logAudit(action: String, category: String, details: String, severity: String = "INFO") {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("users").document(uid).collection("audit_logs").document()
        try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "action" to action.take(120),
                    "category" to category.take(64),
                    "details" to details.take(1000),
                    "severity" to severity,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }
}
