package com.example.domain.engine

import com.example.domain.model.Candle
import com.example.domain.model.HistoricalAnalogue
import com.example.domain.model.PatternFingerprintMatch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * APEX MARKET DNA X & PATTERN FINGERPRINT SCANNER
 * Compares current candle sequence against earlier historical windows inside the fetched
 * OHLCV archive using normalized return vectors, volatility signatures, wick geometry, and RSI state.
 */
object MarketDnaEngine {

    fun findHistoricalAnalogues(candles: List<Candle>, windowSize: Int = 10, topK: Int = 4): List<HistoricalAnalogue> {
        if (candles.size < windowSize * 3 + 12) return emptyList()

        val currentSlice = candles.takeLast(windowSize)
        val currentVec = normalizeReturns(currentSlice)
        val candidates = mutableListOf<HistoricalAnalogue>()

        var i = 0
        while (i <= candles.size - windowSize * 2 - 11) {
            val histWindow = candles.subList(i, i + windowSize)
            val histVec = normalizeReturns(histWindow)
            val sim = cosineSimilarity(currentVec, histVec) * 100.0
            if (sim > 55.0) {
                val baseClose = histWindow.last().close
                val outcomeSlice = candles.subList(i + windowSize, i + windowSize + 10)
                val endClose = outcomeSlice.last().close
                val retPct = if (baseClose > 0) ((endClose - baseClose) / baseClose) * 100.0 else 0.0
                val maxHigh = outcomeSlice.maxOf { it.high }
                val minLow = outcomeSlice.minOf { it.low }
                val mfe = if (baseClose > 0) ((maxHigh - baseClose) / baseClose) * 100.0 else 0.0
                val mae = if (baseClose > 0) ((baseClose - minLow) / baseClose) * 100.0 else 0.0
                val regime = if (retPct > 0.6) "Bullish Expansion" else if (retPct < -0.6) "Bearish Distribution" else "Mean-Reverting Range"
                candidates += HistoricalAnalogue(
                    startEpochSec = histWindow.first().timestampEpochSec,
                    similarityPct = sim.coerceIn(0.0, 99.9),
                    historicalRegime = regime,
                    subsequent10BarReturnPct = retPct,
                    maxFavorableExcursionPct = mfe,
                    maxAdverseExcursionPct = mae
                )
                i += windowSize / 2
            } else {
                i++
            }
        }
        return candidates.sortedByDescending { it.similarityPct }.take(topK)
    }

    fun scanPatternFingerprints(
        candles: List<Candle>,
        windowSize: Int = 10,
        horizonBars: Int = 10,
        topK: Int = 5
    ): List<PatternFingerprintMatch> {
        if (candles.size < windowSize * 3 + horizonBars) return emptyList()

        val currentSlice = candles.takeLast(windowSize)
        val currVec = normalizeReturns(currentSlice)
        val currProfile = computeBehaviorProfile(candles, candles.size - windowSize, candles.size)
        val matches = mutableListOf<PatternFingerprintMatch>()

        var i = 0
        while (i <= candles.size - windowSize * 2 - horizonBars) {
            val histWindow = candles.subList(i, i + windowSize)
            val histVec = normalizeReturns(histWindow)
            val shapeSim = ((cosineSimilarity(currVec, histVec) + 1.0) / 2.0) * 100.0
            val histProfile = computeBehaviorProfile(candles, i, i + windowSize)

            // Composite similarity penalizing large volatility/RSI regime mismatch
            val volGap = abs(currProfile.avgRangePct - histProfile.avgRangePct)
            val rsiGap = abs(currProfile.endRsi - histProfile.endRsi)
            val compositeSim = (shapeSim * 0.72 + (100.0 - (volGap * 20.0)).coerceIn(0.0, 100.0) * 0.16 +
                (100.0 - (rsiGap * 1.4)).coerceIn(0.0, 100.0) * 0.12).coerceIn(0.0, 99.8)

            if (compositeSim >= 58.0) {
                val baseClose = histWindow.last().close
                val outcomeSlice = candles.subList(i + windowSize, i + windowSize + horizonBars)
                val endClose = outcomeSlice.last().close
                val retPct = if (baseClose > 0) ((endClose - baseClose) / baseClose) * 100.0 else 0.0
                val maxHigh = outcomeSlice.maxOf { it.high }
                val minLow = outcomeSlice.minOf { it.low }
                val mfe = if (baseClose > 0) ((maxHigh - baseClose) / baseClose) * 100.0 else 0.0
                val mae = if (baseClose > 0) ((baseClose - minLow) / baseClose) * 100.0 else 0.0
                val regime = when {
                    retPct > 0.6 -> "Bullish Expansion"
                    retPct < -0.6 -> "Bearish Distribution"
                    else -> "Mean-Reverting Range"
                }

                val diffs = buildList {
                    add("Volatility: Current ${"%.2f".format(currProfile.avgRangePct)}% vs Match ${"%.2f".format(histProfile.avgRangePct)}% avg bar range")
                    add("Momentum RSI(14): Current ${"%.1f".format(currProfile.endRsi)} vs Match ${"%.1f".format(histProfile.endRsi)}")
                    add("Bullish Candles: Current ${(currProfile.bullRatio * 100).toInt()}% vs Match ${(histProfile.bullRatio * 100).toInt()}%")
                    add("Upper/Lower Wick Ratio: Current ${"%.2f".format(currProfile.wickSkew)} vs Match ${"%.2f".format(histProfile.wickSkew)}")
                }

                matches += PatternFingerprintMatch(
                    matchId = "fp_${histWindow.first().timestampEpochSec}",
                    startEpochSec = histWindow.first().timestampEpochSec,
                    endEpochSec = histWindow.last().timestampEpochSec,
                    similarityPct = compositeSim,
                    historicalRegime = regime,
                    subsequentReturnPct = retPct,
                    maxFavorableExcursionPct = mfe,
                    maxAdverseExcursionPct = mae,
                    currentFingerprintSummary = currProfile.summary,
                    matchedFingerprintSummary = histProfile.summary,
                    differencesFromCurrent = diffs
                )
                i += windowSize / 2
            } else {
                i++
            }
        }
        return matches.sortedByDescending { it.similarityPct }.take(topK)
    }

    private data class BehaviorProfile(
        val netMovePct: Double,
        val avgRangePct: Double,
        val bullRatio: Double,
        val wickSkew: Double,
        val endRsi: Double,
        val summary: String
    )

    private fun computeBehaviorProfile(allCandles: List<Candle>, startIdx: Int, endIdx: Int): BehaviorProfile {
        val slice = allCandles.subList(startIdx, endIdx)
        val firstOpen = slice.firstOrNull()?.open ?: 1.0
        val lastClose = slice.lastOrNull()?.close ?: 1.0
        val netMovePct = if (firstOpen > 0) ((lastClose - firstOpen) / firstOpen) * 100.0 else 0.0
        val avgRangePct = slice.map { if (it.open > 0) ((it.high - it.low) / it.open) * 100.0 else 0.0 }.average()
        val bullRatio = slice.count { it.close >= it.open }.toDouble() / maxOf(1, slice.size)
        val upperWicks = slice.sumOf { (it.high - maxOf(it.open, it.close)).coerceAtLeast(0.0) }
        val lowerWicks = slice.sumOf { (minOf(it.open, it.close) - it.low).coerceAtLeast(0.0) }
        val wickSkew = if (lowerWicks > 0) upperWicks / lowerWicks else if (upperWicks > 0) 2.0 else 1.0
        val closesUpToEnd = allCandles.subList(0, endIdx).map { it.close }
        val rsi = TechnicalAnalysisEngine.computeRsi(closesUpToEnd, 14)
        val summary = "Move ${"%+.2f".format(netMovePct)}% | BarVol ${"%.2f".format(avgRangePct)}% | Bulls ${(bullRatio * 100).toInt()}% | RSI ${"%.1f".format(rsi)}"
        return BehaviorProfile(netMovePct, avgRangePct, bullRatio, wickSkew, rsi, summary)
    }

    private fun normalizeReturns(window: List<Candle>): List<Double> {
        val base = window.firstOrNull()?.open ?: return emptyList()
        if (base <= 0.0) return emptyList()
        return window.map { (it.close - base) / base }
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0.0) (dot / denom).coerceIn(-1.0, 1.0) else 0.0
    }
}
