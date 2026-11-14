package com.example.data.ai

import android.util.Log
import com.example.domain.model.AiNewsInvestigationResult
import com.example.domain.model.AlertRecord
import com.example.domain.model.ApiConnectionConfig
import com.example.domain.model.Candle
import com.example.domain.model.CentralBankFeedStatus
import com.example.domain.model.ExecutionOrderRecord
import com.example.domain.model.FredMacroIndicator
import com.example.domain.model.NewsArticleItem
import com.example.domain.model.TechnicalSnapshot
import com.example.domain.model.TradingSetup
import com.example.domain.model.TribunalChallengeRecord
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Specialized Institutional Firebase AI Logic Orchestrator:
 *  - Supreme AI Intelligence Tribunal (Adversarial Prosecution vs Defense vs Cross-Examination vs Judge)
 *  - AI News Investigation Team (Investigative Lead + Fact Verifier & Conflicting Source Audit)
 *  - Historical Reality Blind Analyst (Zero-Hindsight cutoff reasoning)
 *  - Central Bank Policy Analyzer (Fed, ECB, BoE, BoJ, SNB)
 *  - Executive Intelligence Briefing (Priorities, Unfinished Missions, Alerts, API Projects, Actions)
 */
class ApexAiSpecializedOrchestrator {

    private val tribunalSchema = Schema.obj(
        properties = mapOf(
            "prosecutionCase" to Schema.string(description = "Bearish / Risk Prosecutor attacking the setup and highlighting failure modes"),
            "defenseCase" to Schema.string(description = "Bullish / Opportunity Advocate presenting structural evidence"),
            "crossExaminationChallenges" to Schema.string(description = "Point-by-point adversarial rebuttal between prosecution and defense"),
            "verificationAudit" to Schema.string(description = "Independent Auditor checking whether every claim strictly matches provided numbers"),
            "supremeVerdict" to Schema.string(description = "Supreme Tribunal Judge final conditional ruling"),
            "consensusScore" to Schema.integer(description = "0 to 100 conviction score"),
            "weakAssumptionsExposed" to Schema.array(Schema.string(description = "Specific weak assumptions exposed"))
        )
    )

    private val newsInvestigationSchema = Schema.obj(
        properties = mapOf(
            "leadInvestigatorFindings" to Schema.string(description = "Lead investigative journalist synthesis of primary facts"),
            "factVerificationAudit" to Schema.string(description = "Second model cross-verification separating confirmed facts from speculation"),
            "conflictingReports" to Schema.string(description = "Where different market narratives or sources diverge or remain unverified"),
            "credibilityAssessment" to Schema.string(description = "Source provenance and factual confidence rating")
        )
    )

    private val blindRealitySchema = Schema.obj(
        properties = mapOf(
            "blindBias" to Schema.string(description = "BULLISH, BEARISH, or NEUTRAL strictly from pre-cutoff candles"),
            "blindReasoning" to Schema.string(description = "Analytical rationale grounded strictly in pre-cutoff candles"),
            "blindConfidence" to Schema.integer(description = "0 to 100 confidence"),
            "blindKeyLevel" to Schema.double(description = "Key price level/target"),
            "blindInvalidation" to Schema.double(description = "Invalidation price level")
        )
    )

    private val tribunalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = tribunalSchema
            }
        )
    }

    private val newsInvestModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = newsInvestigationSchema
            }
        )
    }

    private val blindSimModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = blindRealitySchema
            }
        )
    }

    private val executiveModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val fastAuditModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    suspend fun runSupremeTribunal(
        snapshot: TechnicalSnapshot,
        candles: List<Candle>,
        relatedNews: List<NewsArticleItem>
    ): Result<TribunalChallengeRecord> {
        val barsText = candles.takeLast(14).joinToString("\n") { c ->
            "O=${c.open} H=${c.high} L=${c.low} C=${c.close} V=${c.volume}"
        }
        val newsText = if (relatedNews.isEmpty()) "No linked news catalysts available."
        else relatedNews.take(4).joinToString("\n") { "[${it.sourceName}] ${it.title} (${it.sentimentLabel})" }

        val prompt = """
            You are the SUPREME AI INTELLIGENCE TRIBUNAL for J.A.X. OMNIVERSE.
            Conduct a rigorous adversarial trial where agents challenge each other's conclusions.
            NEVER fabricate numbers or unseen data. Expose weak assumptions ruthlessly.

            Instrument: ${snapshot.instrument} | Timeframe: ${snapshot.timeframe}
            Regime: ${snapshot.regime.label} | Bias: ${snapshot.structureBias}
            Close: ${snapshot.lastClose} | EMA20: ${snapshot.ema20} | EMA50: ${snapshot.ema50}
            RSI(14): ${snapshot.rsi14} | ATR(14): ${snapshot.atr14} | VolRatio: ${snapshot.volatilityRatio}
            BOS: ${snapshot.bosDetected} | CHoCH: ${snapshot.chochDetected} | Sweep: ${snapshot.liquiditySweepType}
            Observed Evidence: ${snapshot.evidencePoints.joinToString("; ")}

            Recent Candles:
            $barsText

            News Context:
            $newsText
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) { tribunalModel.generateContent(prompt) }
            val json = JSONObject(response.text ?: error("Empty tribunal response"))
            val weakList = mutableListOf<String>()
            val arr: JSONArray? = json.optJSONArray("weakAssumptionsExposed")
            if (arr != null) {
                for (i in 0 until arr.length()) weakList += arr.optString(i)
            }
            Result.success(
                TribunalChallengeRecord(
                    instrument = snapshot.instrument,
                    timeframe = snapshot.timeframe,
                    prosecutionCase = json.optString("prosecutionCase"),
                    defenseCase = json.optString("defenseCase"),
                    crossExaminationChallenges = json.optString("crossExaminationChallenges"),
                    verificationAudit = json.optString("verificationAudit"),
                    supremeVerdict = json.optString("supremeVerdict"),
                    consensusScore = json.optInt("consensusScore", 50).coerceIn(0, 100),
                    weakAssumptionsExposed = weakList,
                    generatedAtEpochMs = System.currentTimeMillis()
                )
            )
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Supreme Tribunal timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Supreme Tribunal failed", e)
            Result.failure(e)
        }
    }

    suspend fun investigateNewsStory(
        article: NewsArticleItem,
        otherArticles: List<NewsArticleItem>
    ): Result<AiNewsInvestigationResult> {
        val relatedText = otherArticles
            .filter { it.id != article.id }
            .take(5)
            .joinToString("\n") { "- [${it.sourceName}] ${it.title} (${it.sentimentLabel})" }
            .ifBlank { "No secondary articles retrieved for cross-comparison yet." }

        val prompt = """
            You are the J.A.X. AI NEWS INVESTIGATION TEAM (Lead Investigator gemini-3.8-flash + Cross-Source Auditor).
            Investigate this verified release, separate primary facts from speculation, compare against other synced feeds, and flag any conflicting angles or missing evidence.

            PRIMARY ARTICLE:
            Source: ${article.sourceName}
            Published: ${article.publishedAt}
            Title: ${article.title}
            Summary: ${article.summary}
            URL: ${article.articleUrl}
            Affected Instruments: ${article.affectedInstruments.joinToString(", ")}

            OTHER SYNCED SOURCES IN FEED:
            $relatedText
        """.trimIndent()

        return try {
            val res = withTimeout(50_000L) { newsInvestModel.generateContent(prompt) }
            val json = JSONObject(res.text ?: error("Empty news investigation response"))
            Result.success(
                AiNewsInvestigationResult(
                    articleId = article.id,
                    headline = article.title,
                    sourceName = article.sourceName,
                    leadInvestigatorFindings = json.optString("leadInvestigatorFindings"),
                    factVerificationAudit = json.optString("factVerificationAudit"),
                    conflictingReports = json.optString("conflictingReports"),
                    affectedInstruments = article.affectedInstruments,
                    credibilityAssessment = json.optString("credibilityAssessment"),
                    verifiedAtEpochMs = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "News investigation failed", e)
            Result.failure(e)
        }
    }

    suspend fun evaluateHistoricalBlindSlice(
        snapshot: TechnicalSnapshot,
        blindCandles: List<Candle>
    ): Result<JSONObject> {
        val tail = blindCandles.takeLast(12).joinToString("\n") {
            "t=${it.timestampEpochSec} O=${it.open} H=${it.high} L=${it.low} C=${it.close}"
        }
        val prompt = """
            HISTORICAL REALITY SIMULATOR (STRICT ZERO-HINDSIGHT TEST).
            You are viewing ONLY candles up to the historical cutoff below. Predict directional bias for the next 8 bars based strictly on structure, EMAs, RSI, and sweep geometry.
            Instrument: ${snapshot.instrument} (${snapshot.timeframe})
            Cutoff Close: ${snapshot.lastClose} | EMA20: ${snapshot.ema20} | EMA50: ${snapshot.ema50}
            RSI(14): ${snapshot.rsi14} | ATR(14): ${snapshot.atr14} | Structure: ${snapshot.structureBias} | Sweep: ${snapshot.liquiditySweepType}
            Swing High: ${snapshot.swingHigh} | Swing Low: ${snapshot.swingLow}
            Pre-Cutoff Candles:
            $tail
        """.trimIndent()

        return try {
            val res = withTimeout(40_000L) { blindSimModel.generateContent(prompt) }
            Result.success(JSONObject(res.text ?: error("Empty blind simulation JSON")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeCentralBankPolicy(
        banks: List<CentralBankFeedStatus>,
        macroIndicators: List<FredMacroIndicator>
    ): Result<String> {
        val bankLines = banks.joinToString("\n") {
            "${it.bankCode} (${it.bankName}): Latest='${it.latestReleaseTitle}' (${it.latestReleaseDate}) | Tone=${it.inferredTone} | RateSeries=${it.rateSeriesValue}"
        }
        val fredLines = if (macroIndicators.isEmpty()) "FRED series not synced."
        else macroIndicators.joinToString("\n") { "${it.seriesId} (${it.observationDate}): ${it.value} — ${it.title}" }

        val prompt = """
            Produce a concise Central Bank Intelligence brief (Fed, ECB, BoE, BoJ, SNB) strictly from the synced releases and FRED macro series below.
            If a bank shows NO_FEED_DATA or unreachable, state that clearly instead of guessing policy stances.

            CENTRAL BANKS:
            $bankLines

            FRED POLICY & YIELD SERIES:
            $fredLines

            Cover:
            1. Policy Divergence & Rate Expectations Summary
            2. Treasury Curve & Term Premium Implications
            3. FX & Risk Asset Watchpoints (EUR-USD, GBP-USD, USD-JPY, USD-CHF, Gold)
        """.trimIndent()

        return try {
            val res = withTimeout(45_000L) { fastAuditModel.generateContent(prompt) }
            Result.success(res.text ?: "No central bank analysis returned.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateExecutiveBriefing(
        snapshots: List<TechnicalSnapshot>,
        setups: List<TradingSetup>,
        orders: List<ExecutionOrderRecord>,
        news: List<NewsArticleItem>,
        alerts: List<AlertRecord>,
        connections: List<ApiConnectionConfig>
    ): Result<String> {
        val snapText = if (snapshots.isEmpty()) "No market scans run yet."
        else snapshots.sortedByDescending { it.opportunityScore }.take(6).joinToString("\n") {
            "${it.instrument} (${it.timeframe}): Score=${it.opportunityScore}/100, Bias=${it.structureBias}, Regime=${it.regime.label}, RSI=${"%.1f".format(it.rsi14)}"
        }
        val devSetups = setups.filter { it.status == "DEVELOPING" || it.status == "CONFIRMED" }
        val pendingOrders = orders.filter { it.status == "PENDING_APPROVAL" }
        val activeAlerts = alerts.filter { !it.acknowledged }.take(5)
        val connHealth = connections.joinToString(", ") { "${it.providerName}:${it.status}" }
        val newsText = news.take(6).joinToString("\n") { "[${it.sourceName}] ${it.title} (${it.sentimentLabel})" }.ifBlank { "No news synced yet." }

        val prompt = """
            Produce the J.A.X. EXECUTIVE INTELLIGENCE BRIEFING strictly from the live workspace state below:

            MARKET SNAPSHOTS:
            $snapText

            UNFINISHED MISSIONS:
            - Developing/Active Setups: ${devSetups.size} (${devSetups.take(4).joinToString { "${it.instrument} ${it.status} (${it.score})" }.ifBlank { "None" }})
            - Orders Awaiting Manual Approval: ${pendingOrders.size}

            ACTIVE UNACKNOWLEDGED ALERTS:
            ${activeAlerts.joinToString("\n") { "[${it.priority}] ${it.title}: ${it.message}" }.ifBlank { "None" }}

            CONNECTED APIS & PROJECTS:
            $connHealth

            RECENT NEWS:
            $newsText

            Format cleanly with sections:
            1. Top Operator Priorities
            2. Unfinished Missions & Setups
            3. Important News & Macro Drivers
            4. System & API Health Alerts
            5. Recommended Next Actions (Advisory only — Risk Gate governs execution)
        """.trimIndent()

        return try {
            val res = withTimeout(50_000L) { executiveModel.generateContent(prompt) }
            Result.success(res.text ?: "No executive briefing generated.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "ApexAiSpecialized"
    }
}
