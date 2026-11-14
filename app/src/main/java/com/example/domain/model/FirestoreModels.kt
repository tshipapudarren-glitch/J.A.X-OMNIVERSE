package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserWorkspaceProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val role: String = "analyst",
    val executionMode: String = "ANALYSIS_ONLY",
    val emergencyStop: Boolean = false,
    val accountBalance: Double = 100000.0,
    val riskPerTradePct: Double = 1.0,
    val maxDailyLossPct: Double = 4.0,
    val maxConcurrentPositions: Long = 3L,
    val preferredInstruments: String = "BTC-USD,ETH-USD,PAXG-USD,EUR-USD",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WatchlistInstrument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val displayName: String = "",
    val assetClass: String = "CRYPTO",
    val providerId: String = "coinbase_public",
    val providerSymbol: String = "",
    val pipSize: Double = 1.0,
    val contractSize: Double = 1.0,
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ApiConnectionConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val providerName: String = "",
    val category: String = "MARKET_DATA",
    val baseUrl: String = "",
    val status: String = "DISCONNECTED",
    val credentialConfigured: Boolean = false,
    val rateLimitInfo: String = "",
    val lastLatencyMs: Long = 0L,
    val lastError: String = "",
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TradingSetup(
    @DocumentId val id: String = "",
    val userId: String = "",
    val instrument: String = "",
    val direction: String = "NEUTRAL",
    val setupType: String = "",
    val status: String = "DEVELOPING",
    val timeframe: String = "1H",
    val higherTimeframeBias: String = "NEUTRAL",
    val entryZoneLow: Double = 0.0,
    val entryZoneHigh: Double = 0.0,
    val stopLoss: Double = 0.0,
    val tp1: Double = 0.0,
    val tp2: Double = 0.0,
    val tp3: Double = 0.0,
    val rr1: Double = 0.0,
    val score: Long = 0L,
    val evidenceSummary: String = "",
    val confirmationConditions: String = "",
    val invalidationConditions: String = "",
    val dataQualityStatus: String = "UNVERIFIED",
    val newsExposure: String = "NONE",
    val aiSummary: String = "",
    val version: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AiInvestigationReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val instrument: String = "",
    val timeframe: String = "1H",
    val regime: String = "UNKNOWN",
    val bullishArgument: String = "",
    val bearishArgument: String = "",
    val liquidityInterpretation: String = "",
    val volumeInterpretation: String = "",
    val macroInterpretation: String = "",
    val riskAssessment: String = "",
    val verificationReport: String = "",
    val chiefAnalystVerdict: String = "",
    val disagreements: String = "",
    val confidenceScore: Long = 0L,
    val verified: Boolean = false,
    val modelRouterInfo: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AlertRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "OPPORTUNITY",
    val priority: String = "MEDIUM",
    val instrument: String = "",
    val title: String = "",
    val message: String = "",
    val acknowledged: Boolean = false,
    val sourceEngine: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ExecutionOrderRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val instrument: String = "",
    val direction: String = "BUY",
    val volumeLots: Double = 0.01,
    val entryPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val riskPercent: Double = 1.0,
    val executionMode: String = "ANALYSIS_ONLY",
    val status: String = "BLOCKED",
    val riskCheckPassed: Boolean = false,
    val rejectionReason: String = "",
    val requestNonce: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BacktestRunRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val strategyName: String = "",
    val instrument: String = "",
    val timeframe: String = "1H",
    val totalTrades: Long = 0L,
    val winRatePct: Double = 0.0,
    val profitFactor: Double = 0.0,
    val expectancyR: Double = 0.0,
    val maxDrawdownPct: Double = 0.0,
    val netReturnPct: Double = 0.0,
    val spreadAssumed: Double = 0.0,
    val slippageAssumed: Double = 0.0,
    val walkForwardPassed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLogRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val action: String = "",
    val category: String = "",
    val details: String = "",
    val severity: String = "INFO",
    @ServerTimestamp val createdAt: Timestamp? = null
)
