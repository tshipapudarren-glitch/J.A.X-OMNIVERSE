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

    data class SimSummary(
        val trades: Int,
        val winRatePct: Double,
        val profitFactor: Double,
        val expectancyR: Double,
        val maxDrawdownPct: Double,
        val netReturnPct: Double,
        val inSampleExpectancyR: Double = 0.0,
        val outSampleExpectancyR: Double = 0.0,
        val walkForwardPassed: Boolean = false
    )

    fun runParameterizedSimulation(
        candles: List<Candle>,
        spreadPct: Double = 0.04,
        slippagePct: Double = 0.02,
        riskPerTradePct: Double = 1.0,
        fastEma: Int = 10,
        slowEma: Int = 21,
        atrStopMult: Double = 1.4,
        atrTargetMult: Double = 2.2,
        requireRsiFilter: Boolean = false,
        maxHoldBars: Int = 14
    ): SimSummary {
        if (candles.size < max(40, slowEma + 15)) return SimSummary(0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val costFraction = (spreadPct + slippagePct) / 100.0
        val splitIndex = (candles.size * 0.7).toInt()
        val inSample = simulateSlice(
            candles.subList(0, splitIndex), costFraction, riskPerTradePct,
            fastEma, slowEma, atrStopMult, atrTargetMult, requireRsiFilter, maxHoldBars
        )
        val outSample = simulateSlice(
            candles.subList(max(0, splitIndex - slowEma - 2), candles.size), costFraction, riskPerTradePct,
            fastEma, slowEma, atrStopMult, atrTargetMult, requireRsiFilter, maxHoldBars
        )
        val full = simulateSlice(
            candles, costFraction, riskPerTradePct,
            fastEma, slowEma, atrStopMult, atrTargetMult, requireRsiFilter, maxHoldBars
        )
        val wfPassed = outSample.trades >= 2 &&
            outSample.expectancyR > 0.0 &&
            inSample.expectancyR > -0.2
        return full.copy(
            inSampleExpectancyR = inSample.expectancyR,
            outSampleExpectancyR = outSample.expectancyR,
            walkForwardPassed = wfPassed
        )
    }

    private fun simulateSlice(
        candles: List<Candle>,
        costFraction: Double,
        riskPct: Double,
        fastEmaPeriod: Int = 10,
        slowEmaPeriod: Int = 21,
        atrStopMult: Double = 1.4,
        atrTargetMult: Double = 2.2,
        requireRsiFilter: Boolean = false,
        maxHoldBars: Int = 14
    ): SimSummary {
        val warmup = max(21, slowEmaPeriod + 1)
        if (candles.size < warmup + 5) return SimSummary(0, 0.0, 0.0, 0.0, 0.0, 0.0)

        var equity = 100.0
        var peakEquity = 100.0
        var maxDrawdown = 0.0
        val rResults = mutableListOf<Double>()

        var i = warmup
        while (i < candles.size - 2) {
            // Compute EMAs strictly using bars <= i (zero future data!)
            val window = candles.subList(0, i + 1)
            val closes = window.map { it.close }
            val emaFast = ema(closes, fastEmaPeriod)
            val emaSlow = ema(closes, slowEmaPeriod)
            val prevClose = candles[i - 1].close
            val currClose = candles[i].close
            val atr = avgRange(window.takeLast(14))
            if (atr <= 0.0) {
                i++
                continue
            }

            val rsiOkBull = if (!requireRsiFilter) true else TechnicalAnalysisEngine.computeRsi(closes, 14) in 45.0..68.0
            val rsiOkBear = if (!requireRsiFilter) true else TechnicalAnalysisEngine.computeRsi(closes, 14) in 32.0..55.0

            val bullCross = emaFast > emaSlow && currClose > emaFast && prevClose <= emaFast && rsiOkBull
            val bearCross = emaFast < emaSlow && currClose < emaFast && prevClose >= emaFast && rsiOkBear

            if (bullCross || bearCross) {
                // Enter at next bar's open + realistic transaction cost
                val nextOpen = candles[i + 1].open
                val entry = if (bullCross) nextOpen * (1.0 + costFraction) else nextOpen * (1.0 - costFraction)
                val stop = if (bullCross) entry - atr * atrStopMult else entry + atr * atrStopMult
                val target = if (bullCross) entry + atr * atrTargetMult else entry - atr * atrTargetMult
                val riskDist = abs(entry - stop)
                if (riskDist <= 0.0) {
                    i++
                    continue
                }
                var exitBar = min(candles.size - 1, i + maxHoldBars)
                var realizedR = 0.0

                val endLoop = min(candles.size, i + maxHoldBars + 1)
                for (j in (i + 1) until endLoop) {
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
                    if (j == endLoop - 1) {
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
