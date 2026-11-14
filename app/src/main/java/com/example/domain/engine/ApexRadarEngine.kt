package com.example.domain.engine

import com.example.domain.model.ApiConnectionConfig
import com.example.domain.model.Candle
import com.example.domain.model.ConnectionHealth
import com.example.domain.model.ExecutionOrderRecord
import com.example.domain.model.FredMacroIndicator
import com.example.domain.model.HistoricalRealitySimulation
import com.example.domain.model.MarketQuote
import com.example.domain.model.NewsArticleItem
import com.example.domain.model.NewsTimelineEvent
import com.example.domain.model.PerfectStormSignal
import com.example.domain.model.SentimentDivergenceItem
import com.example.domain.model.TechnicalSnapshot
import com.example.domain.model.TradingSetup
import com.example.domain.model.UserWorkspaceProfile
import com.example.domain.model.WatchtowerAnomaly
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Deterministic Institutional Intelligence Radars:
 *  - Global Sentiment Divergence Radar
 *  - Perfect Storm Detector
 *  - News Timeline Reconstruction
 *  - Historical Reality Simulator (strict cutoff isolation, zero hindsight)
 *  - Omniscient Watchtower Anomaly Sweep
 */
object ApexRadarEngine {

    fun detectSentimentDivergences(
        quotes: Map<String, MarketQuote>,
        technicals: Map<String, TechnicalSnapshot>,
        news: List<NewsArticleItem>,
        macroIndicators: List<FredMacroIndicator>
    ): List<SentimentDivergenceItem> {
        if (technicals.isEmpty()) return emptyList()

        val yieldCurveSpread = macroIndicators.firstOrNull { it.seriesId == "T10Y2Y" }
            ?.value?.replace("%", "")?.toDoubleOrNull()

        return technicals.values.map { snap ->
            val quote = quotes[snap.instrument]
            val linkedNews = news.filter {
                snap.instrument in it.affectedInstruments || "MACRO" in it.affectedInstruments
            }
            val newsBiasScore = if (linkedNews.isEmpty()) 0 else {
                val raw = linkedNews.sumOf { art ->
                    when {
                        "POSITIVE" in art.sentimentLabel || "BULLISH" in art.sentimentLabel || "HAWKISH" in art.sentimentLabel -> 35
                        "CAUTIOUS" in art.sentimentLabel || "BEARISH" in art.sentimentLabel || "RISK-OFF" in art.sentimentLabel -> -35
                        else -> 0
                    }
                }
                raw.coerceIn(-100, 100)
            }
            val newsLabel = when {
                linkedNews.isEmpty() -> "NO LINKED NEWS"
                newsBiasScore >= 25 -> "NEWS BULLISH ($newsBiasScore)"
                newsBiasScore <= -25 -> "NEWS BEARISH ($newsBiasScore)"
                else -> "NEWS MIXED ($newsBiasScore)"
            }

            // Price behavior score from EMA alignment, RSI, and 24h change
            var priceScore = 0
            if (snap.structureBias == "BULLISH") priceScore += 40
            else if (snap.structureBias == "BEARISH") priceScore -= 40
            if (snap.rsi14 >= 58.0) priceScore += 25
            else if (snap.rsi14 <= 42.0) priceScore -= 25
            if (quote != null && quote.health == ConnectionHealth.LIVE) {
                if (quote.change24hPct >= 1.0) priceScore += 20
                else if (quote.change24hPct <= -1.0) priceScore -= 20
            }
            priceScore = priceScore.coerceIn(-100, 100)

            val positioningProxy = buildString {
                append("RSI ${"%.1f".format(snap.rsi14)} • ${snap.liquiditySweepType}")
                if (yieldCurveSpread != null) {
                    append(" • Curve T10Y2Y=${"%+.2f".format(yieldCurveSpread)}%")
                }
            }

            val oppositeDirections = (newsBiasScore >= 25 && priceScore <= -25) ||
                (newsBiasScore <= -25 && priceScore >= 25)
            val extremeDivergence = oppositeDirections ||
                (abs(newsBiasScore - priceScore) >= 75 && linkedNews.isNotEmpty()) ||
                (snap.rsi14 >= 70.0 && snap.structureBias == "BEARISH") ||
                (snap.rsi14 <= 30.0 && snap.structureBias == "BULLISH")

            val severity = when {
                oppositeDirections -> "CRITICAL_CONFLICT"
                extremeDivergence -> "MODERATE_CONFLICT"
                linkedNews.isEmpty() && quote?.change24hPct?.let { abs(it) > 2.5 } == true -> "MODERATE_CONFLICT"
                else -> "ALIGNED"
            }

            val explanation = when {
                oppositeDirections ->
                    "Direct conflict: News narrative ($newsLabel) opposes observed price action (${snap.structureBias}, RSI ${"%.1f".format(snap.rsi14)}). Possible liquidity trap or absorption."
                extremeDivergence ->
                    "Divergence gap (${abs(newsBiasScore - priceScore)} pts) between sentiment/macro positioning and price structure."
                linkedNews.isEmpty() ->
                    "No linked news catalyst retrieved yet; price driven purely by technical flow (${snap.regime.label})."
                else ->
                    "News sentiment and observed market price behavior are broadly consistent."
            }

            SentimentDivergenceItem(
                instrument = snap.instrument,
                newsSentimentLabel = newsLabel,
                newsBiasScore = newsBiasScore,
                positioningProxy = positioningProxy,
                actualPriceBehavior = "${snap.structureBias} (${quote?.let { "%+.2f".format(it.change24hPct) + "%" } ?: "No 24h delta"})",
                priceBiasScore = priceScore,
                conflictSeverity = severity,
                requiresInvestigation = severity != "ALIGNED",
                explanation = explanation
            )
        }.sortedByDescending {
            when (it.conflictSeverity) {
                "CRITICAL_CONFLICT" -> 2
                "MODERATE_CONFLICT" -> 1
                else -> 0
            }
        }
    }

    fun detectPerfectStorms(
        technicals: Map<String, TechnicalSnapshot>,
        quotes: Map<String, MarketQuote>,
        news: List<NewsArticleItem>,
        setups: List<TradingSetup>
    ): List<PerfectStormSignal> {
        return technicals.values.map { snap ->
            val pillars = mutableListOf<String>()
            val isBull = snap.structureBias == "BULLISH"
            val isBear = snap.structureBias == "BEARISH"

            // Pillar 1: Structural Displacement (BOS/CHoCH + clear bias)
            if ((isBull || isBear) && (snap.bosDetected || snap.chochDetected)) {
                pillars += "Structure: ${snap.structureBias} ${if (snap.bosDetected) "BOS" else "CHoCH"}"
            }
            // Pillar 2: Liquidity Sweep
            if ((isBull && snap.liquiditySweepType == "SELL_SIDE_SWEEP") ||
                (isBear && snap.liquiditySweepType == "BUY_SIDE_SWEEP")
            ) {
                pillars += "Liquidity: ${snap.liquiditySweepType}"
            }
            // Pillar 3: Imbalance / Order Block Confluence
            val hasBullZone = isBull && (snap.bullishFvgZone != null || snap.orderBlockZone != null)
            val hasBearZone = isBear && (snap.bearishFvgZone != null || snap.orderBlockZone != null)
            if (hasBullZone || hasBearZone) {
                pillars += "Institutional Zone: ${if (snap.orderBlockZone != null) "Order Block + FVG" else "FVG Active"}"
            }
            // Pillar 4: Volatility Expansion / Momentum Regime
            if (snap.volatilityRatio >= 1.15 && (if (isBull) snap.rsi14 in 52.0..74.0 else if (isBear) snap.rsi14 in 26.0..48.0 else false)) {
                pillars += "Expansion: Vol ${"%.2f".format(snap.volatilityRatio)}x + RSI ${"%.1f".format(snap.rsi14)}"
            }
            // Pillar 5: Independent News / Macro Catalyst Alignment
            val matchingNews = news.filter { snap.instrument in it.affectedInstruments }
            val newsAligned = matchingNews.any { art ->
                (isBull && ("POSITIVE" in art.sentimentLabel || "BULLISH" in art.sentimentLabel)) ||
                    (isBear && ("CAUTIOUS" in art.sentimentLabel || "BEARISH" in art.sentimentLabel))
            }
            if (newsAligned) {
                pillars += "News Alignment: ${matchingNews.first().sourceName}"
            }
            // Pillar 6: Active Confirmed Setup
            val activeConfirmed = setups.any { it.instrument == snap.instrument && it.status == "CONFIRMED" }
            if (activeConfirmed) {
                pillars += "Confirmed Setup Active"
            }

            val score = (pillars.size * 18 + (snap.opportunityScore * 0.28).toInt()).coerceIn(0, 100)
            val rarity = when {
                pillars.size >= 4 -> "PERFECT_STORM"
                pillars.size == 3 -> "HIGH_CONFLUENCE"
                else -> "WATCHLIST"
            }
            val quoteStr = quotes[snap.instrument]?.let { TechnicalAnalysisEngine.fmt(it.lastPrice) } ?: "--"

            PerfectStormSignal(
                instrument = snap.instrument,
                timeframe = snap.timeframe,
                direction = snap.structureBias,
                stormScore = score,
                independentPillarsMatched = pillars,
                pillarCount = pillars.size,
                summary = if (pillars.isEmpty()) {
                    "Price $quoteStr — fewer than 2 independent pillars aligned."
                } else {
                    "Price $quoteStr — ${pillars.size} independent evidence pillars converging."
                },
                rarityLabel = rarity
            )
        }.sortedByDescending { it.pillarCount * 100 + it.stormScore }
    }

    fun reconstructNewsTimeline(
        articles: List<NewsArticleItem>,
        quotes: Map<String, MarketQuote>
    ): List<NewsTimelineEvent> {
        if (articles.isEmpty()) return emptyList()
        val parsed = articles.map { art ->
            val epoch = parseFlexibleTimestamp(art.publishedAt) ?: art.retrievedAtEpochMs
            Triple(art, epoch, formatUtc(epoch))
        }.sortedBy { it.second } // Chronological: earliest -> latest

        return parsed.mapIndexed { idx, (art, epoch, displayTime) ->
            val reaction = art.affectedInstruments.mapNotNull { sym ->
                quotes[sym]?.takeIf { it.health == ConnectionHealth.LIVE }?.let { q ->
                    "$sym ${"%+.2f".format(q.change24hPct)}% (${TechnicalAnalysisEngine.fmt(q.lastPrice)})"
                }
            }.joinToString(" | ").ifBlank { "No live linked price quote loaded yet — run Omniscanner X." }

            NewsTimelineEvent(
                id = art.id,
                chronologicalIndex = idx + 1,
                timestampDisplay = if (parseFlexibleTimestamp(art.publishedAt) != null) displayTime else "${art.publishedAt} (order)",
                epochMs = epoch,
                sourceName = art.sourceName,
                headline = art.title,
                summary = art.summary,
                articleUrl = art.articleUrl,
                sentimentLabel = art.sentimentLabel,
                linkedInstruments = art.affectedInstruments,
                marketReactionNote = reaction
            )
        }
    }

    fun buildBlindHistoricalSlice(
        instrument: String,
        timeframe: String,
        candles: List<Candle>,
        cutoffPct: Float,
        horizonBars: Int = 8
    ): Pair<List<Candle>, TechnicalSnapshot>? {
        if (candles.size < 35) return null
        val minIdx = 25
        val maxIdx = candles.size - horizonBars - 1
        if (maxIdx <= minIdx) return null
        val cutoffIndex = (minIdx + ((maxIdx - minIdx) * cutoffPct.coerceIn(0.1f, 0.9f))).toInt()
            .coerceIn(minIdx, maxIdx)
        val blindSlice = candles.subList(0, cutoffIndex + 1)
        val snap = TechnicalAnalysisEngine.analyze(instrument, timeframe, blindSlice) ?: return null
        return blindSlice to snap
    }

    fun revealAndGradeHistoricalReality(
        sim: HistoricalRealitySimulation,
        fullCandles: List<Candle>
    ): HistoricalRealitySimulation {
        val start = sim.cutoffIndex + 1
        val endExclusive = minOf(fullCandles.size, sim.cutoffIndex + 1 + sim.horizonBars)
        if (start >= fullCandles.size || endExclusive <= start) return sim

        val futureBars = fullCandles.subList(start, endExclusive)
        val entry = sim.cutoffClose
        val endClose = futureBars.last().close
        val actualRet = if (entry > 0) ((endClose - entry) / entry) * 100.0 else 0.0
        val highAfter = futureBars.maxOf { it.high }
        val lowAfter = futureBars.minOf { it.low }

        val biasUp = sim.blindBias.contains("BULL", ignoreCase = true)
        val biasDown = sim.blindBias.contains("BEAR", ignoreCase = true)
        val isCorrect = when {
            biasUp -> actualRet > 0.05
            biasDown -> actualRet < -0.05
            else -> abs(actualRet) <= 0.65
        }
        val notes = "Actual ${futureBars.size}-bar move: ${"%+.2f".format(actualRet)}% " +
            "(High ${TechnicalAnalysisEngine.fmt(highAfter)} / Low ${TechnicalAnalysisEngine.fmt(lowAfter)}). " +
            "Prediction (${sim.blindBias}) graded ${if (isCorrect) "ACCURATE" else "INCORRECT"}."

        return sim.copy(
            futureRevealed = true,
            actualOutcomeReturnPct = actualRet,
            actualHighAfterCutoff = highAfter,
            actualLowAfterCutoff = lowAfter,
            forecastCorrect = isCorrect,
            gradingNotes = notes
        )
    }

    fun runWatchtowerSweep(
        connections: List<ApiConnectionConfig>,
        technicals: Map<String, TechnicalSnapshot>,
        quotes: Map<String, MarketQuote>,
        setups: List<TradingSetup>,
        orders: List<ExecutionOrderRecord>,
        profile: UserWorkspaceProfile?,
        ghostActive: Boolean
    ): List<WatchtowerAnomaly> {
        val anomalies = mutableListOf<WatchtowerAnomaly>()
        val now = System.currentTimeMillis()

        if (profile?.emergencyStop == true) {
            anomalies += WatchtowerAnomaly(
                id = "wt_estop",
                severity = "CRITICAL",
                domain = "EXECUTION_RISK",
                title = "Global Emergency Stop Engaged",
                detail = "All order execution pathways are locked until operator clears Emergency Stop.",
                detectedAtEpochMs = now
            )
        }

        val failedConns = connections.filter { it.enabled && (it.status == "ERROR" || it.status == "RATE_LIMITED") }
        failedConns.forEach { c ->
            anomalies += WatchtowerAnomaly(
                id = "wt_conn_${c.id}",
                severity = "HIGH",
                domain = "API_FEED",
                title = "${c.providerName} Degraded (${c.status})",
                detail = c.lastError.ifBlank { "Endpoint returned ${c.status} (${c.lastLatencyMs}ms)" },
                detectedAtEpochMs = now
            )
        }

        val staleQuotes = quotes.values.filter { it.health == ConnectionHealth.LIVE && it.spreadPct > 0.8 }
        staleQuotes.forEach { q ->
            anomalies += WatchtowerAnomaly(
                id = "wt_spread_${q.symbol}",
                severity = "MEDIUM",
                domain = "API_FEED",
                title = "Wide Spread on ${q.symbol}",
                detail = "Spread is ${"%.3f".format(q.spreadPct)}% via ${q.providerId} (latency ${q.latencyMs}ms).",
                detectedAtEpochMs = now
            )
        }

        technicals.values.filter { it.volatilityRatio >= 1.35 }.forEach { snap ->
            anomalies += WatchtowerAnomaly(
                id = "wt_vol_${snap.instrument}",
                severity = "HIGH",
                domain = "MARKET_VOLATILITY",
                title = "Volatility Shock on ${snap.instrument}",
                detail = "ATR expansion ${"%.2f".format(snap.volatilityRatio)}x normal baseline (${snap.regime.label}).",
                detectedAtEpochMs = now
            )
        }

        val pendingOrders = orders.count { it.status == "PENDING_APPROVAL" }
        if (pendingOrders > 0) {
            anomalies += WatchtowerAnomaly(
                id = "wt_pending_orders",
                severity = "HIGH",
                domain = "WORKFLOW",
                title = "$pendingOrders Order(s) Awaiting Manual Approval",
                detail = "Review Mode 2 approval queue in Execution Intelligence before expiry.",
                detectedAtEpochMs = now
            )
        }

        val devSetups = setups.count { it.status == "DEVELOPING" }
        if (devSetups > 0 && !ghostActive) {
            anomalies += WatchtowerAnomaly(
                id = "wt_dev_setups",
                severity = "INFO",
                domain = "WORKFLOW",
                title = "$devSetups Developing Setup(s) Unmonitored",
                detail = "Ghost Monitor is paused; enable 45s watch cycle to track confirmation triggers.",
                detectedAtEpochMs = now
            )
        }

        return anomalies.sortedBy {
            when (it.severity) {
                "CRITICAL" -> 0
                "HIGH" -> 1
                "MEDIUM" -> 2
                else -> 3
            }
        }
    }

    private fun parseFlexibleTimestamp(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm 'UTC'",
            "yyyyMMdd'T'HHmmss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val d = sdf.parse(trimmed)
                if (d != null) return d.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun formatUtc(epochMs: Long): String {
        val sdf = SimpleDateFormat("MMM dd HH:mm 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(epochMs))
    }
}
