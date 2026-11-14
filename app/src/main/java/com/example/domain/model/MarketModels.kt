package com.example.domain.model

enum class MarketRegime(val label: String) {
    TRENDING_UP("Trending Bullish"),
    TRENDING_DOWN("Trending Bearish"),
    RANGING("Ranging / Equilibrium"),
    EXPANDING("Volatility Expanding"),
    CONTRACTING("Range Contracting"),
    HIGH_VOLATILITY("High Volatility"),
    LOW_VOLATILITY("Low Volatility"),
    TRANSITIONING("Regime Transitioning"),
    DISCONNECTED("Feed Disconnected")
}

enum class ExecutionMode(val wireValue: String, val displayLabel: String) {
    ANALYSIS_ONLY("ANALYSIS_ONLY", "MODE 1 — ANALYSIS ONLY"),
    MANUAL_APPROVAL("MANUAL_APPROVAL", "MODE 2 — MANUAL APPROVAL"),
    AUTHORISED_AUTOMATION("AUTHORISED_AUTOMATION", "MODE 3 — AUTHORISED AUTOMATION");

    companion object {
        fun fromWire(value: String): ExecutionMode =
            entries.firstOrNull { it.wireValue == value } ?: ANALYSIS_ONLY
    }
}

data class Candle(
    val timestampEpochSec: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

enum class ConnectionHealth {
    LIVE, STALE, DISCONNECTED, ERROR
}

data class MarketQuote(
    val symbol: String,
    val displayName: String,
    val providerId: String,
    val bid: Double,
    val ask: Double,
    val lastPrice: Double,
    val change24hPct: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24h: Double,
    val spread: Double,
    val spreadPct: Double,
    val fetchedAtEpochMs: Long,
    val latencyMs: Long,
    val health: ConnectionHealth,
    val errorMessage: String? = null
)

data class TechnicalSnapshot(
    val instrument: String,
    val timeframe: String,
    val lastClose: Double,
    val ema20: Double,
    val ema50: Double,
    val atr14: Double,
    val rsi14: Double,
    val volatilityRatio: Double,
    val regime: MarketRegime,
    val structureBias: String,
    val bosDetected: Boolean,
    val chochDetected: Boolean,
    val swingHigh: Double,
    val swingLow: Double,
    val equalHighPool: Double?,
    val equalLowPool: Double?,
    val liquiditySweepType: String,
    val bullishFvgZone: Pair<Double, Double>?,
    val bearishFvgZone: Pair<Double, Double>?,
    val orderBlockZone: Pair<Double, Double>?,
    val opportunityScore: Int,
    val evidencePoints: List<String>,
    val computedAtEpochMs: Long
)

data class ChartAnnotation(
    val id: String,
    val label: String,
    val type: String, // ENTRY_ZONE, SL, TP1, TP2, TP3, OB, FVG, LIQUIDITY, SWING_HIGH, SWING_LOW, BOS, CHOCH
    val priceStart: Double,
    val priceEnd: Double = priceStart,
    val isEditable: Boolean = true
)

data class MarketSessionStatus(
    val name: String,
    val isOpen: Boolean,
    val windowUtc: String,
    val note: String
)

data class NewsArticleItem(
    val id: String,
    val title: String,
    val summary: String,
    val sourceName: String,
    val articleUrl: String,
    val publishedAt: String,
    val retrievedAtEpochMs: Long,
    val affectedInstruments: List<String>,
    val sentimentLabel: String,
    val verificationStatus: String,
    val aiInterpretation: String = ""
)

data class EconomicCalendarItem(
    val id: String,
    val title: String,
    val currency: String,
    val impact: String,
    val eventTime: String,
    val actual: String,
    val forecast: String,
    val previous: String,
    val revisedPrevious: String,
    val sourceName: String,
    val affectedInstruments: List<String>
)

data class FredMacroIndicator(
    val seriesId: String,
    val title: String,
    val value: String,
    val units: String,
    val observationDate: String
)

data class Mt5Position(
    val ticket: Long,
    val symbol: String,
    val type: String,
    val volume: Double,
    val openPrice: Double,
    val currentPrice: Double,
    val sl: Double,
    val tp: Double,
    val profit: Double
)

data class Mt5TelemetrySnapshot(
    val connected: Boolean,
    val endpointUrl: String,
    val maskedAccountId: String,
    val broker: String,
    val server: String,
    val eaStatus: String,
    val balance: Double,
    val equity: Double,
    val marginLevelPct: Double,
    val lastHeartbeatEpochMs: Long,
    val lastError: String? = null,
    val positions: List<Mt5Position> = emptyList()
)

data class SpecialistAgentStatus(
    val id: String,
    val name: String,
    val domain: String,
    val assignedModel: String,
    val state: String, // READY, RUNNING, COMPLETED, ERROR, IDLE
    val lastExecutionMs: Long,
    val lastConfidence: Int,
    val lastError: String? = null
)

data class RiskEvaluationResult(
    val passed: Boolean,
    val recommendedLots: Double,
    val riskAmountUsd: Double,
    val stopDistancePoints: Double,
    val rewardToRisk: Double,
    val gateResults: List<Pair<String, Boolean>>,
    val blockingReason: String? = null
)

data class HistoricalAnalogue(
    val startEpochSec: Long,
    val similarityPct: Double,
    val historicalRegime: String,
    val subsequent10BarReturnPct: Double,
    val maxFavorableExcursionPct: Double,
    val maxAdverseExcursionPct: Double
)
