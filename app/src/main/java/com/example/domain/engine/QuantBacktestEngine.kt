package com.example.domain.engine

import com.example.domain.model.BacktestRunRecord
import com.example.domain.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic Historical Backtesting & Walk-Forward Engine.
 * Enforces strict chronological bar-by-bar evaluation (zero look-ahead bias)
 * with realistic spread and slippage assumptions.
 */
object QuantBacktestEngine {

    fun runBacktest(
        userId: String,
        strategyName: String,
        instrument: String,
        timeframe: String,
        candles: List<Candle>,
        spreadPct: Double = 0.04,
        slippagePct: Double = 0.02,
        riskPerTradePct: Double = 1.0
    ): BacktestRunRecord? {
        if (candles.size < 40) return null

        val costFraction = (spreadPct + slippagePct) / 100.0
        val splitIndex = (candles.size * 0.7).toInt()
        val inSampleMetrics = simulateSlice(candles.subList(0, splitIndex), costFraction, riskPerTradePct)
        val outSampleMetrics = simulateSlice(candles.subList(max(0, splitIndex - 22), candles.size), costFraction, riskPerTradePct)
        val fullMetrics = simulateSlice(candles, costFraction, riskPerTradePct)

        val walkForwardPassed = outSampleMetrics.trades >= 2 &&
            outSampleMetrics.expectancyR > 0.0 &&
            inSampleMetrics.expectancyR > -0.2

        return BacktestRunRecord(
            userId = userId,
            strategyName = strategyName,
            instrument = instrument,
            timeframe = timeframe,
            totalTrades = fullMetrics.trades.toLong(),
            winRatePct = fullMetrics.winRatePct,
            profitFactor = fullMetrics.profitFactor,
            expectancyR = fullMetrics.expectancyR,
            maxDrawdownPct = fullMetrics.maxDrawdownPct,
            netReturnPct = fullMetrics.netReturnPct,
            spreadAssumed = spreadPct,
            slippageAssumed = slippagePct,
            walkForwardPassed = walkForwardPassed
        )
    }

    private data class SimSummary(
        val trades: Int,
        val winRatePct: Double,
        val profitFactor: Double,
        val expectancyR: Double,
        val maxDrawdownPct: Double,
        val netReturnPct: Double
    )

    private fun simulateSlice(
        candles: List<Candle>,
        costFraction: Double,
        riskPct: Double
    ): SimSummary {
        if (candles.size < 25) return SimSummary(0, 0.0, 0.0, 0.0, 0.0, 0.0)

        var equity = 100.0
        var peakEquity = 100.0
        var maxDrawdown = 0.0
        val rResults = mutableListOf<Double>()

        var i = 21
        while (i < candles.size - 2) {
            // Compute EMA20 strictly using bars <= i (zero future data!)
            val window = candles.subList(0, i + 1)
            val emaFast = ema(window.map { it.close }, 10)
            val emaSlow = ema(window.map { it.close }, 21)
            val prevClose = candles[i - 1].close
            val currClose = candles[i].close
            val atr = avgRange(window.takeLast(14))
            if (atr <= 0.0) {
                i++
                continue
            }

            val bullCross = emaFast > emaSlow && currClose > emaFast && prevClose <= emaFast
            val bearCross = emaFast < emaSlow && currClose < emaFast && prevClose >= emaFast

            if (bullCross || bearCross) {
                // Enter at next bar's open + realistic transaction cost
                val nextOpen = candles[i + 1].open
                val entry = if (bullCross) nextOpen * (1.0 + costFraction) else nextOpen * (1.0 - costFraction)
                val stop = if (bullCross) entry - atr * 1.4 else entry + atr * 1.4
                val target = if (bullCross) entry + atr * 2.2 else entry - atr * 2.2
                val riskDist = abs(entry - stop)
                var exitBar = min(candles.size - 1, i + 12)
                var realizedR = 0.0

                for (j in (i + 1) until min(candles.size, i + 15)) {
                    val bar = candles[j]
                    if (bullCross) {
                        if (bar.low <= stop) {
                            realizedR = -1.0
                            exitBar = j
                            break
                        }
                        if (bar.high >= target) {
                            realizedR = (target - entry) / riskDist
                            exitBar = j
                            break
                        }
                    } else {
                        if (bar.high >= stop) {
                            realizedR = -1.0
                            exitBar = j
                            break
                        }
                        if (bar.low <= target) {
                            realizedR = (entry - target) / riskDist
                            exitBar = j
                            break
                        }
                    }
                    if (j == min(candles.size - 1, i + 14)) {
                        val exitPrice = if (bullCross) bar.close * (1.0 - costFraction) else bar.close * (1.0 + costFraction)
                        realizedR = if (bullCross) (exitPrice - entry) / riskDist else (entry - exitPrice) / riskDist
                        exitBar = j
                    }
                }

                rResults += realizedR
                equity *= (1.0 + (realizedR * riskPct / 100.0))
                if (equity > peakEquity) peakEquity = equity
                val dd = if (peakEquity > 0) ((peakEquity - equity) / peakEquity) * 100.0 else 0.0
                if (dd > maxDrawdown) maxDrawdown = dd
                i = exitBar + 1
            } else {
                i++
            }
        }

        if (rResults.isEmpty()) return SimSummary(0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val wins = rResults.filter { it > 0.0 }
        val losses = rResults.filter { it <= 0.0 }
        val winRate = (wins.size.toDouble() / rResults.size) * 100.0
        val grossWin = wins.sum()
        val grossLoss = abs(losses.sum())
        val pf = if (grossLoss > 0.0) grossWin / grossLoss else if (grossWin > 0.0) 9.99 else 0.0
        val expectancy = rResults.average()

        return SimSummary(
            trades = rResults.size,
            winRatePct = winRate,
            profitFactor = pf.coerceAtMost(99.0),
            expectancyR = expectancy,
            maxDrawdownPct = maxDrawdown,
            netReturnPct = equity - 100.0
        )
    }

    private fun ema(values: List<Double>, period: Int): Double {
        val k = 2.0 / (period + 1.0)
        var result = values.first()
        for (i in 1 until values.size) {
            result = values[i] * k + result * (1.0 - k)
        }
        return result
    }

    private fun avgRange(candles: List<Candle>): Double =
        if (candles.isEmpty()) 0.0 else candles.map { it.high - it.low }.average()
}
