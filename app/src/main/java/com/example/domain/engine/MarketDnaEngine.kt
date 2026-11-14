package com.example.domain.engine

import com.example.domain.model.Candle
import com.example.domain.model.HistoricalAnalogue
import kotlin.math.sqrt

/**
 * APEX MARKET DNA X — Compares current candle sequence against earlier historical windows
 * inside the fetched OHLCV archive using normalized cosine similarity.
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
