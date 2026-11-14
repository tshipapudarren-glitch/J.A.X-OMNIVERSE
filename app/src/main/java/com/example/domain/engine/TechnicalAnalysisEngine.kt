package com.example.domain.engine

import com.example.domain.model.Candle
import com.example.domain.model.ChartAnnotation
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSessionStatus
import com.example.domain.model.TechnicalSnapshot
import com.example.domain.model.TradingSetup
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic Technical Analysis, Market Structure, Liquidity, Regime & Setup Engine.
 * Never fabricates prices or setups when candle history is empty or insufficient.
 */
object TechnicalAnalysisEngine {

    fun analyze(instrument: String, timeframe: String, candles: List<Candle>): TechnicalSnapshot? {
        if (candles.size < 25) return null
        val closes = candles.map { it.close }
        val lastClose = closes.last()
        val ema20 = computeEma(closes, 20)
        val ema50 = computeEma(closes, min(50, closes.size))
        val atr14 = computeAtr(candles, 14)
        val atr50 = computeAtr(candles, min(50, candles.size - 1))
        val rsi14 = computeRsi(closes, 14)
        val volRatio = if (atr50 > 0.0) atr14 / atr50 else 1.0

        val lookback = candles.takeLast(min(30, candles.size))
        val swingHigh = lookback.maxOf { it.high }
        val swingLow = lookback.minOf { it.low }

        // Detect Equal Highs / Equal Lows (Liquidity Pools within 0.25 * ATR)
        val tolerance = max(atr14 * 0.25, lastClose * 0.0005)
        val recentHighs = lookback.dropLast(1).map { it.high }.sortedDescending()
        val equalHighPool = recentHighs.zipWithNext().firstOrNull { (a, b) -> abs(a - b) <= tolerance }?.first

        val recentLows = lookback.dropLast(1).map { it.low }.sorted()
        val equalLowPool = recentLows.zipWithNext().firstOrNull { (a, b) -> abs(a - b) <= tolerance }?.first

        // Liquidity sweep on latest 2 candles
        val latest = candles.last()
        val prev = candles[candles.size - 2]
        val priorHigh = candles.dropLast(2).takeLast(15).maxOfOrNull { it.high } ?: swingHigh
        val priorLow = candles.dropLast(2).takeLast(15).minOfOrNull { it.low } ?: swingLow
        val liquiditySweep = when {
            latest.high > priorHigh && latest.close < priorHigh -> "BEARISH_BSL_SWEEP"
            latest.low < priorLow && latest.close > priorLow -> "BULLISH_SSL_SWEEP"
            prev.high > priorHigh && prev.close < priorHigh -> "RECENT_BSL_SWEEP"
            prev.low < priorLow && prev.close > priorLow -> "RECENT_SSL_SWEEP"
            else -> "NONE"
        }

        // Fair Value Gaps (3-candle imbalance in last 15 bars)
        var bullishFvg: Pair<Double, Double>? = null
        var bearishFvg: Pair<Double, Double>? = null
        val fvgWindow = candles.takeLast(min(18, candles.size))
        for (i in 2 until fvgWindow.size) {
            val c0 = fvgWindow[i - 2]
            val c2 = fvgWindow[i]
            if (c2.low > c0.high) {
                bullishFvg = c0.high to c2.low
            }
            if (c2.high < c0.low) {
                bearishFvg = c2.high to c0.low
            }
        }

        // Order Block proxy: last opposite-colored candle prior to strongest displacement candle
        val displacementWindow = candles.takeLast(min(16, candles.size))
        var orderBlock: Pair<Double, Double>? = null
        for (i in displacementWindow.size - 1 downTo 1) {
            val curr = displacementWindow[i]
            val prevBar = displacementWindow[i - 1]
            val body = abs(curr.close - curr.open)
            if (body >= atr14 * 1.1) {
                if (curr.close > curr.open && prevBar.close < prevBar.open) {
                    orderBlock = prevBar.low to prevBar.high
                    break
                } else if (curr.close < curr.open && prevBar.close > prevBar.open) {
                    orderBlock = prevBar.low to prevBar.high
                    break
                }
            }
        }

        val bosDetected = latest.close > priorHigh || latest.close < priorLow
        val chochDetected = (ema20 < ema50 && latest.close > ema20 && latest.close > prev.high) ||
            (ema20 > ema50 && latest.close < ema20 && latest.close < prev.low)

        val regime = when {
            volRatio >= 1.35 -> MarketRegime.HIGH_VOLATILITY
            volRatio <= 0.72 -> MarketRegime.CONTRACTING
            chochDetected -> MarketRegime.TRANSITIONING
            ema20 > ema50 && lastClose > ema20 -> MarketRegime.TRENDING_UP
            ema20 < ema50 && lastClose < ema20 -> MarketRegime.TRENDING_DOWN
            else -> MarketRegime.RANGING
        }

        val structureBias = when {
            ema20 > ema50 && lastClose >= ema20 -> "BULLISH"
            ema20 < ema50 && lastClose <= ema20 -> "BEARISH"
            else -> "NEUTRAL"
        }

        val evidence = mutableListOf<String>()
        evidence += "EMA20 (${fmt(ema20)}) vs EMA50 (${fmt(ema50)}) -> $structureBias"
        evidence += "RSI(14) = ${"%.1f".format(rsi14)}, ATR(14) = ${fmt(atr14)} (Vol ratio ${"%.2f".format(volRatio)}x)"
        if (bosDetected) evidence += "Break of Structure detected against 15-bar pivot"
        if (chochDetected) evidence += "Change of Character (CHoCH) emerging"
        if (liquiditySweep != "NONE") evidence += "Liquidity Event: $liquiditySweep"
        if (bullishFvg != null) evidence += "Bullish FVG [${fmt(bullishFvg.first)} - ${fmt(bullishFvg.second)}]"
        if (bearishFvg != null) evidence += "Bearish FVG [${fmt(bearishFvg.first)} - ${fmt(bearishFvg.second)}]"
        if (orderBlock != null) evidence += "Order Block [${fmt(orderBlock.first)} - ${fmt(orderBlock.second)}]"

        var score = 45
        if (structureBias != "NEUTRAL") score += 12
        if (bosDetected) score += 10
        if (liquiditySweep != "NONE") score += 14
        if (bullishFvg != null || bearishFvg != null) score += 8
        if (orderBlock != null) score += 7
        if (rsi14 in 35.0..65.0) score += 4
        score = score.coerceIn(0, 96)

        return TechnicalSnapshot(
            instrument = instrument,
            timeframe = timeframe,
            lastClose = lastClose,
            ema20 = ema20,
            ema50 = ema50,
            atr14 = atr14,
            rsi14 = rsi14,
            volatilityRatio = volRatio,
            regime = regime,
            structureBias = structureBias,
            bosDetected = bosDetected,
            chochDetected = chochDetected,
            swingHigh = swingHigh,
            swingLow = swingLow,
            equalHighPool = equalHighPool,
            equalLowPool = equalLowPool,
            liquiditySweepType = liquiditySweep,
            bullishFvgZone = bullishFvg,
            bearishFvgZone = bearishFvg,
            orderBlockZone = orderBlock,
            opportunityScore = score,
            evidencePoints = evidence,
            computedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun generateSetup(
        userId: String,
        snapshot: TechnicalSnapshot,
        dataQualityStatus: String,
        newsExposure: String
    ): TradingSetup {
        val atr = max(snapshot.atr14, snapshot.lastClose * 0.001)
        val isBullish = when {
            snapshot.liquiditySweepType.contains("SSL") -> true
            snapshot.liquiditySweepType.contains("BSL") -> false
            snapshot.structureBias == "BULLISH" -> true
            snapshot.structureBias == "BEARISH" -> false
            else -> snapshot.rsi14 < 50.0
        }
        val direction = if (isBullish) "BULLISH" else "BEARISH"
        val setupType = when {
            snapshot.liquiditySweepType != "NONE" -> "Liquidity Sweep & Recovery"
            snapshot.chochDetected -> "Structural Reversal (CHoCH)"
            snapshot.bosDetected -> "Breakout & Retest Continuation"
            snapshot.regime == MarketRegime.CONTRACTING -> "Compression Breakout Watch"
            else -> "Order Block / FVG Reaction"
        }

        val entryLow: Double
        val entryHigh: Double
        val stopLoss: Double
        val tp1: Double
        val tp2: Double
        val tp3: Double

        if (isBullish) {
            val anchor = snapshot.bullishFvgZone?.second ?: snapshot.lastClose
            entryLow = min(anchor - atr * 0.35, snapshot.lastClose)
            entryHigh = max(anchor, entryLow + atr * 0.15)
            stopLoss = max(0.00001, entryLow - atr * 1.2)
            val risk = max(0.00001, entryHigh - stopLoss)
            tp1 = entryHigh + risk * 1.5
            tp2 = entryHigh + risk * 2.5
            tp3 = max(entryHigh + risk * 3.8, snapshot.swingHigh)
        } else {
            val anchor = snapshot.bearishFvgZone?.first ?: snapshot.lastClose
            entryHigh = max(anchor + atr * 0.35, snapshot.lastClose)
            entryLow = min(anchor, entryHigh - atr * 0.15)
            stopLoss = entryHigh + atr * 1.2
            val risk = max(0.00001, stopLoss - entryLow)
            tp1 = max(0.00001, entryLow - risk * 1.5)
            tp2 = max(0.00001, entryLow - risk * 2.5)
            tp3 = max(0.00001, min(entryLow - risk * 3.8, snapshot.swingLow))
        }

        val riskDist = if (isBullish) abs(entryHigh - stopLoss) else abs(stopLoss - entryLow)
        val rewardDist = if (isBullish) abs(tp1 - entryHigh) else abs(entryLow - tp1)
        val rr1 = if (riskDist > 0) (rewardDist / riskDist) else 1.5

        // Strict rule: never describe a developing setup as confirmed without BOS + high score + live data
        val isConfirmed = snapshot.bosDetected &&
            snapshot.opportunityScore >= 72 &&
            dataQualityStatus.startsWith("LIVE")
        val status = if (isConfirmed) "CONFIRMED" else "DEVELOPING"

        return TradingSetup(
            userId = userId,
            instrument = snapshot.instrument,
            direction = direction,
            setupType = setupType,
            status = status,
            timeframe = snapshot.timeframe,
            higherTimeframeBias = snapshot.structureBias,
            entryZoneLow = entryLow,
            entryZoneHigh = entryHigh,
            stopLoss = stopLoss,
            tp1 = tp1,
            tp2 = tp2,
            tp3 = tp3,
            rr1 = rr1,
            score = snapshot.opportunityScore.toLong(),
            evidenceSummary = snapshot.evidencePoints.joinToString(" | "),
            confirmationConditions = if (isBullish) {
                "Require candle close above ${fmt(entryHigh)} with spread < 0.25 ATR and no high-impact opposing release."
            } else {
                "Require candle close below ${fmt(entryLow)} with spread < 0.25 ATR and bearish displacement."
            },
            invalidationConditions = if (isBullish) {
                "Invalidated on H1 close below ${fmt(stopLoss)} or bearish CHoCH."
            } else {
                "Invalidated on H1 close above ${fmt(stopLoss)} or bullish CHoCH."
            },
            dataQualityStatus = dataQualityStatus,
            newsExposure = newsExposure,
            aiSummary = "Deterministic calculation from ${snapshot.timeframe} OHLCV; pending Chief Analyst verification.",
            version = 1L
        )
    }

    fun buildChartAnnotations(snapshot: TechnicalSnapshot, setup: TradingSetup?): List<ChartAnnotation> {
        val list = mutableListOf<ChartAnnotation>()
        list += ChartAnnotation("swing_high", "Swing High", "SWING_HIGH", snapshot.swingHigh)
        list += ChartAnnotation("swing_low", "Swing Low", "SWING_LOW", snapshot.swingLow)
        snapshot.equalHighPool?.let { list += ChartAnnotation("eqh", "EQH Liquidity Pool", "LIQUIDITY", it) }
        snapshot.equalLowPool?.let { list += ChartAnnotation("eql", "EQL Liquidity Pool", "LIQUIDITY", it) }
        snapshot.bullishFvgZone?.let { list += ChartAnnotation("bfvg", "Bullish FVG", "FVG", it.first, it.second) }
        snapshot.bearishFvgZone?.let { list += ChartAnnotation("sfvg", "Bearish FVG", "FVG", it.first, it.second) }
        snapshot.orderBlockZone?.let { list += ChartAnnotation("ob", "Order Block", "OB", it.first, it.second) }
        if (setup != null) {
            list += ChartAnnotation("entry", "Entry Zone", "ENTRY_ZONE", setup.entryZoneLow, setup.entryZoneHigh)
            list += ChartAnnotation("sl", "Stop Loss (Invalidation)", "SL", setup.stopLoss)
            list += ChartAnnotation("tp1", "TP1", "TP1", setup.tp1)
            list += ChartAnnotation("tp2", "TP2", "TP2", setup.tp2)
            list += ChartAnnotation("tp3", "TP3", "TP3", setup.tp3)
        }
        return list
    }

    fun currentMarketSessions(): List<MarketSessionStatus> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY
        val utcStr = "%02d:%02d UTC".format(hour, cal.get(Calendar.MINUTE))

        return listOf(
            MarketSessionStatus(
                name = "Sydney / Asia",
                isOpen = !isWeekend && (hour >= 22 || hour < 9),
                windowUtc = "22:00 - 09:00 UTC",
                note = if (isWeekend) "FX/Equities Weekend (Crypto 24/7 Active)" else "Tokyo liquidity window ($utcStr)"
            ),
            MarketSessionStatus(
                name = "London Session",
                isOpen = !isWeekend && hour in 7..15,
                windowUtc = "07:00 - 16:00 UTC",
                note = "Peak European FX & Gold liquidity ($utcStr)"
            ),
            MarketSessionStatus(
                name = "New York Session",
                isOpen = !isWeekend && hour in 12..20,
                windowUtc = "12:00 - 21:00 UTC",
                note = if (!isWeekend && hour in 12..15) "London/NY Overlap Active" else "US Macro & Indices ($utcStr)"
            ),
            MarketSessionStatus(
                name = "Crypto 24/7 Spot",
                isOpen = true,
                windowUtc = "Continuous 24/7",
                note = "Coinbase & Kraken live orderbooks ($utcStr)"
            )
        )
    }

    private fun computeEma(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return 0.0
        val k = 2.0 / (period + 1.0)
        var ema = values.first()
        for (i in 1 until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
        }
        return ema
    }

    private fun computeAtr(candles: List<Candle>, period: Int): Double {
        if (candles.size < 2) return 0.0
        val trs = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val curr = candles[i]
            val prev = candles[i - 1]
            val tr = max(
                curr.high - curr.low,
                max(abs(curr.high - prev.close), abs(curr.low - prev.close))
            )
            trs += tr
        }
        val slice = trs.takeLast(min(period, trs.size))
        return if (slice.isNotEmpty()) slice.average() else 0.0
    }

    fun computeRsi(closes: List<Double>, period: Int): Double {
        if (closes.size <= period) return 50.0
        var gains = 0.0
        var losses = 0.0
        val start = closes.size - period
        for (i in start until closes.size) {
            val diff = closes[i] - closes[i - 1]
            if (diff > 0) gains += diff else losses -= diff
        }
        val avgGain = gains / period
        val avgLoss = losses / period
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun fmt(v: Double): String = when {
        v >= 1000 -> "%.2f".format(v)
        v >= 10 -> "%.3f".format(v)
        else -> "%.5f".format(v)
    }
}
