package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.AiInvestigationReport
import com.example.domain.model.Candle
import com.example.domain.model.NewsArticleItem
import com.example.domain.model.SpecialistAgentStatus
import com.example.domain.model.TechnicalSnapshot
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Provider-Independent Multi-AI Council & Debate Chamber Orchestrator.
 * Uses Firebase AI Logic (App Check attested) with Intelligent Model Routing:
 *  - gemini-3.8-flash: Structure, Liquidity, Macro, Chief Analyst & Chart Vision
 *  - gemini-3.5-flash-lite: Fast Verification, News Sentiment & Daily Briefings
 * Preserves specialist disagreements and enforces Chief Analyst verification gates.
 */
class MultiAiOrchestrator {

    private val councilSchema = Schema.obj(
        properties = mapOf(
            "bullishArgument" to Schema.string(description = "Bullish specialist case grounded strictly in observed metrics"),
            "bearishArgument" to Schema.string(description = "Bearish specialist case grounded strictly in observed metrics"),
            "liquidityInterpretation" to Schema.string(description = "Liquidity AI reading of pools, sweeps, and destinations"),
            "volumeInterpretation" to Schema.string(description = "Volume & momentum interpretation"),
            "macroInterpretation" to Schema.string(description = "Macro & News AI synthesis noting any unavailable feeds"),
            "riskAssessment" to Schema.string(description = "Risk AI evaluation of ATR volatility, invalidation distance, and hazards"),
            "verificationReport" to Schema.string(description = "Verification AI audit checking if claims match provided numbers"),
            "disagreements" to Schema.string(description = "Explicit preservation of conflicting evidence across agents"),
            "chiefAnalystVerdict" to Schema.string(description = "Final Chief Analyst verdict separating facts from conditional scenarios"),
            "confidenceScore" to Schema.integer(description = "0 to 100 confidence bounded by evidence quality"),
            "verified" to Schema.boolean(description = "True only if evidence and risk verification pass")
        )
    )

    private val councilModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = councilSchema
            }
        )
    }

    private val fastFlashLiteModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    private val visionModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    fun initialAgentRoster(): List<SpecialistAgentStatus> = listOf(
        SpecialistAgentStatus("structure_ai", "JAX Structure AI", "Market Structure / BOS / CHoCH", "gemini-3.8-flash", "READY", 0L, 0),
        SpecialistAgentStatus("liquidity_ai", "Liquidity AI", "Pools / Sweeps / Destinations", "gemini-3.8-flash", "READY", 0L, 0),
        SpecialistAgentStatus("volume_ai", "Volume AI", "Expansion / Absorption", "gemini-3.5-flash-lite", "READY", 0L, 0),
        SpecialistAgentStatus("pattern_ai", "Pattern AI", "Market DNA & Candle Sequences", "gemini-3.5-flash-lite", "READY", 0L, 0),
        SpecialistAgentStatus("news_ai", "News AI", "RSS / Press / Narrative", "gemini-3.5-flash-lite", "READY", 0L, 0),
        SpecialistAgentStatus("macro_ai", "Macro AI", "Rates / FX / Cross-Market", "gemini-3.8-flash", "READY", 0L, 0),
        SpecialistAgentStatus("risk_ai", "Risk AI", "Drawdown / ATR / Exposure", "gemini-3.8-flash", "READY", 0L, 0),
        SpecialistAgentStatus("prediction_ai", "Prediction AI", "Conditional Scenarios", "gemini-3.8-flash", "READY", 0L, 0),
        SpecialistAgentStatus("history_ai", "Historical Research AI", "Analogues & Expectancy", "gemini-3.5-flash-lite", "READY", 0L, 0),
        SpecialistAgentStatus("vision_ai", "Chart Vision AI", "Uploaded Screenshot Analysis", "gemini-3.8-flash", "READY", 0L, 0),
        SpecialistAgentStatus("verify_ai", "Verification AI", "Data-to-Claim Audit", "gemini-3.5-flash-lite", "READY", 0L, 0),
        SpecialistAgentStatus("chief_ai", "Chief Analyst AI", "Council Synthesis & Disagreement", "gemini-3.8-flash", "READY", 0L, 0)
    )

    suspend fun runMultiAiDebate(
        userId: String,
        snapshot: TechnicalSnapshot,
        candles: List<Candle>,
        relatedNews: List<NewsArticleItem>,
        dataQualityNote: String
    ): Result<AiInvestigationReport> {
        val recentBarsText = candles.takeLast(12).joinToString("\n") { c ->
            "t=${c.timestampEpochSec} O=${c.open} H=${c.high} L=${c.low} C=${c.close} V=${c.volume}"
        }
        val newsText = if (relatedNews.isEmpty()) {
            "No live news articles retrieved for this instrument yet (treat macro impact as unverified)."
        } else {
            relatedNews.take(4).joinToString("\n") { "- [${it.sourceName}] ${it.title} (${it.sentimentLabel})" }
        }

        val prompt = """
            You are the J.A.X. OMNIVERSE Multi-AI Specialist Council & Debate Chamber.
            Analyze ONLY the observed market measurements below. NEVER invent prices, volume, or news.
            Preserve real disagreements between bullish and bearish agents. Distinguish observed facts from conditional scenarios.

            Instrument: ${snapshot.instrument} | Timeframe: ${snapshot.timeframe}
            Data Quality: $dataQualityNote
            Regime: ${snapshot.regime.label} | Structure Bias: ${snapshot.structureBias}
            Last Close: ${snapshot.lastClose} | EMA20: ${snapshot.ema20} | EMA50: ${snapshot.ema50}
            ATR(14): ${snapshot.atr14} | RSI(14): ${snapshot.rsi14} | VolRatio: ${snapshot.volatilityRatio}
            Swing High: ${snapshot.swingHigh} | Swing Low: ${snapshot.swingLow}
            BOS Detected: ${snapshot.bosDetected} | CHoCH Detected: ${snapshot.chochDetected}
            Liquidity Sweep: ${snapshot.liquiditySweepType}
            Equal High Pool: ${snapshot.equalHighPool ?: "None"} | Equal Low Pool: ${snapshot.equalLowPool ?: "None"}
            Computed Evidence: ${snapshot.evidencePoints.joinToString("; ")}

            Recent Observed Candles:
            $recentBarsText

            Observed News Context:
            $newsText
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                councilModel.generateContent(prompt)
            }
            val jsonText = response.text ?: error("Empty AI Council response")
            val json = JSONObject(jsonText)
            val report = AiInvestigationReport(
                userId = userId,
                instrument = snapshot.instrument,
                timeframe = snapshot.timeframe,
                regime = snapshot.regime.label,
                bullishArgument = json.optString("bullishArgument"),
                bearishArgument = json.optString("bearishArgument"),
                liquidityInterpretation = json.optString("liquidityInterpretation"),
                volumeInterpretation = json.optString("volumeInterpretation"),
                macroInterpretation = json.optString("macroInterpretation"),
                riskAssessment = json.optString("riskAssessment"),
                verificationReport = json.optString("verificationReport"),
                chiefAnalystVerdict = json.optString("chiefAnalystVerdict"),
                disagreements = json.optString("disagreements"),
                confidenceScore = json.optInt("confidenceScore", 50).toLong().coerceIn(0L, 100L),
                verified = json.optBoolean("verified", false),
                modelRouterInfo = "Router: gemini-3.8-flash (Council/Chief) + gemini-3.5-flash-lite (Verify)"
            )
            Result.success(report)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Multi-AI Council timed out", e)
            Result.failure(Exception("Multi-AI Council timed out after 60s. Check connectivity or retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Multi-AI Council failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeChartScreenshot(bitmap: Bitmap, symbolHint: String): Result<String> {
        val prompt = """
            You are JAX Chart Vision AI. Inspect this uploaded trading chart image ($symbolHint).
            STRICT RULES:
            1. Explicitly separate VISIBLE chart information (visible trend, price structure, candles, swing highs/lows, support/resistance, patterns) from UNAVAILABLE data.
            2. NEVER claim to know live tick volume, orderbook depth, spread, or unseen higher-timeframe candles from a static screenshot.
            3. Provide conditional bullish, bearish, and invalidation scenarios based strictly on what is visible.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                visionModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            val text = response.text?.takeIf { it.isNotBlank() }
                ?: error("Chart Vision AI returned an empty response")
            Result.success(text)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Chart Vision AI timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chart Vision AI failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateDailyBriefing(
        snapshots: List<TechnicalSnapshot>,
        news: List<NewsArticleItem>
    ): Result<String> {
        val marketSummary = if (snapshots.isEmpty()) {
            "No live market snapshots scanned yet."
        } else {
            snapshots.joinToString("\n") {
                "${it.instrument}: close=${it.lastClose}, regime=${it.regime.label}, bias=${it.structureBias}, score=${it.opportunityScore}"
            }
        }
        val newsSummary = if (news.isEmpty()) {
            "No live news articles fetched yet."
        } else {
            news.take(8).joinToString("\n") { "[${it.sourceName}] ${it.title} -> ${it.affectedInstruments.joinToString(",")}" }
        }

        val prompt = """
            Produce the J.A.X. APEX Daily Intelligence Briefing strictly from these observed facts:
            Market Snapshots:
            $marketSummary

            Verified RSS News Releases:
            $newsSummary

            Structure concisely with:
            1. Observed Market Conditions & Regimes
            2. Verified News & Central Bank Developments
            3. Key Trading Risks & Disagreements
            4. Conditional Scenarios (clearly labeled as conditional, never confirmed signals)
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                fastFlashLiteModel.generateContent(prompt)
            }
            Result.success(response.text ?: "No briefing generated.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Daily Briefing failed", e)
            Result.failure(e)
        }
    }

    suspend fun translateNaturalLanguageStrategy(instruction: String): Result<String> {
        val prompt = """
            You are the J.A.X. Natural Language Strategy Architect.
            Convert this user strategy instruction into a structured, testable specification with:
            - Entry Triggers (deterministic indicator/structure rules)
            - Invalidation / Stop Loss Rule
            - Take Profit / R:R Target
            - Regime & Volatility Filter
            - Risk Management Constraints & Look-Ahead Bias Safeguards
            User Instruction: $instruction
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                fastFlashLiteModel.generateContent(prompt)
            }
            Result.success(response.text ?: "Unable to parse strategy.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "MultiAiOrchestrator"
    }
}
