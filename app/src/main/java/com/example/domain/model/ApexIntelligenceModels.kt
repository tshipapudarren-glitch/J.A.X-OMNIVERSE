package com.example.domain.model

data class StrategyVariantParams(
    val fastEma: Int,
    val slowEma: Int,
    val atrStopMult: Double,
    val atrTargetMult: Double,
    val requireRsiFilter: Boolean,
    val maxHoldBars: Int
)

data class StrategyVariantResult(
    val variantId: String,
    val versionTag: String,
    val variantName: String,
    val parameterChanges: List<String>,
    val params: StrategyVariantParams,
    val totalTrades: Int,
    val winRatePct: Double,
    val profitFactor: Double,
    val expectancyR: Double,
    val maxDrawdownPct: Double,
    val netReturnPct: Double,
    val inSampleExpectancyR: Double,
    val outSampleExpectancyR: Double,
    val robustnessScore: Int,
    val recommendation: String, // PROMOTE, SHADOW_TEST, REJECT
    val rationale: String
)

data class PatternFingerprintMatch(
    val matchId: String,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val similarityPct: Double,
    val historicalRegime: String,
    val subsequentReturnPct: Double,
    val maxFavorableExcursionPct: Double,
    val maxAdverseExcursionPct: Double,
    val currentFingerprintSummary: String,
    val matchedFingerprintSummary: String,
    val differencesFromCurrent: List<String>
)

data class SentimentDivergenceItem(
    val instrument: String,
    val newsSentimentLabel: String,
    val newsBiasScore: Int, // -100..+100
    val positioningProxy: String,
    val actualPriceBehavior: String,
    val priceBiasScore: Int, // -100..+100
    val conflictSeverity: String, // CRITICAL_CONFLICT, MODERATE_CONFLICT, ALIGNED
    val requiresInvestigation: Boolean,
    val explanation: String
)

data class PerfectStormSignal(
    val instrument: String,
    val timeframe: String,
    val direction: String,
    val stormScore: Int,
    val independentPillarsMatched: List<String>,
    val pillarCount: Int,
    val summary: String,
    val rarityLabel: String // PERFECT_STORM, HIGH_CONFLUENCE, WATCHLIST
)

data class SwarmMarketReport(
    val instrument: String,
    val timeframe: String,
    val providerId: String,
    val health: ConnectionHealth,
    val lastClose: Double,
    val opportunityScore: Int,
    val structureBias: String,
    val regimeLabel: String,
    val aiVerdictSummary: String,
    val swarmConfidence: Int,
    val latencyMs: Long,
    val error: String? = null
)

data class TribunalChallengeRecord(
    val instrument: String,
    val timeframe: String,
    val prosecutionCase: String,
    val defenseCase: String,
    val crossExaminationChallenges: String,
    val verificationAudit: String,
    val supremeVerdict: String,
    val consensusScore: Int,
    val weakAssumptionsExposed: List<String>,
    val generatedAtEpochMs: Long
)

data class HistoricalRealitySimulation(
    val instrument: String,
    val timeframe: String,
    val totalCandles: Int,
    val cutoffIndex: Int,
    val cutoffTimestampSec: Long,
    val cutoffClose: Double,
    val horizonBars: Int,
    val blindBias: String,
    val blindReasoning: String,
    val blindConfidence: Int,
    val blindKeyLevel: Double,
    val blindInvalidation: Double,
    val futureRevealed: Boolean,
    val actualOutcomeReturnPct: Double = 0.0,
    val actualHighAfterCutoff: Double = 0.0,
    val actualLowAfterCutoff: Double = 0.0,
    val forecastCorrect: Boolean? = null,
    val gradingNotes: String = ""
)

data class NewsTimelineEvent(
    val id: String,
    val chronologicalIndex: Int,
    val timestampDisplay: String,
    val epochMs: Long,
    val sourceName: String,
    val headline: String,
    val summary: String,
    val articleUrl: String,
    val sentimentLabel: String,
    val linkedInstruments: List<String>,
    val marketReactionNote: String
)

data class AiNewsInvestigationResult(
    val articleId: String,
    val headline: String,
    val sourceName: String,
    val leadInvestigatorFindings: String,
    val factVerificationAudit: String,
    val conflictingReports: String,
    val affectedInstruments: List<String>,
    val credibilityAssessment: String,
    val verifiedAtEpochMs: Long
)

data class CentralBankFeedStatus(
    val bankCode: String, // FED, ECB, BOE, BOJ, SNB
    val bankName: String,
    val currency: String,
    val latestReleaseTitle: String,
    val latestReleaseDate: String,
    val inferredTone: String, // HAWKISH, DOVISH, NEUTRAL, NO_FEED_DATA
    val sourceUrl: String,
    val rateSeriesValue: String
)

data class ScannerCriteriaConfig(
    val minConfluenceScore: Int = 0,
    val minVolatilityRatio: Double = 0.0,
    val trendBiasFilter: String = "ALL", // ALL, BULLISH, BEARISH, NEUTRAL
    val maxAllowedSpreadPct: Double = 5.0
)

data class WatchtowerAnomaly(
    val id: String,
    val severity: String, // CRITICAL, HIGH, MEDIUM, INFO
    val domain: String, // API_FEED, AI_PROVIDER, WORKFLOW, MARKET_VOLATILITY, EXECUTION_RISK
    val title: String,
    val detail: String,
    val detectedAtEpochMs: Long
)
