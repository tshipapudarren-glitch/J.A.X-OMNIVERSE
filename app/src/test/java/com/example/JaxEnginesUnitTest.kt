package com.example

import com.example.domain.engine.ApexRadarEngine
import com.example.domain.engine.MarketDnaEngine
import com.example.domain.engine.StrategyMutationEngine
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.model.Candle
import com.example.domain.model.ConnectionHealth
import com.example.domain.model.FredMacroIndicator
import com.example.domain.model.HistoricalRealitySimulation
import com.example.domain.model.MarketQuote
import com.example.domain.model.NewsArticleItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class JaxEnginesUnitTest {

    private fun buildSyntheticCandles(count: Int = 90): List<Candle> {
        var price = 100.0
        return (0 until count).map { idx ->
            val wave = sin(idx * 0.35) * 1.8 + 0.25
            val open = price
            val close = (open + wave).coerceAtLeast(10.0)
            val high = maxOf(open, close) + 0.8
            val low = minOf(open, close) - 0.7
            price = close
            Candle(
                timestampEpochSec = 1_700_000_000L + idx * 3600L,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = 1000.0 + idx * 10.0
            )
        }
    }

    @Test
    fun strategyMutationEngineGeneratesAndBenchmarks5Variants() {
        val candles = buildSyntheticCandles(95)
        val results = StrategyMutationEngine.runMutationLab(candles)
        assertEquals(5, results.size)
        assertTrue(results.any { it.versionTag == "v1.0-BASE" })
        assertTrue(results.all { it.recommendation in setOf("PROMOTE", "SHADOW_TEST", "REJECT") })
        assertTrue(results.all { it.parameterChanges.isNotEmpty() })
    }

    @Test
    fun patternFingerprintScannerReturnsSimilarityAndDifferences() {
        val candles = buildSyntheticCandles(90)
        val matches = MarketDnaEngine.scanPatternFingerprints(candles)
        assertTrue("Expected fingerprint matches on 90 candles", matches.isNotEmpty())
        val top = matches.first()
        assertTrue(top.similarityPct in 0.0..100.0)
        assertTrue(top.differencesFromCurrent.size >= 3)
        assertTrue(top.currentFingerprintSummary.isNotBlank())
    }

    @Test
    fun historicalRealitySimulatorEnforcesBlindCutoffIsolationAndGradesOutcome() {
        val candles = buildSyntheticCandles(80)
        val pair = ApexRadarEngine.buildBlindHistoricalSlice("BTC-USD", "1H", candles, cutoffPct = 0.5f, horizonBars = 8)
        assertNotNull(pair)
        val (blindSlice, blindSnap) = pair!!
        assertTrue(blindSlice.size < candles.size - 7)

        val sim = HistoricalRealitySimulation(
            instrument = "BTC-USD",
            timeframe = "1H",
            totalCandles = candles.size,
            cutoffIndex = blindSlice.size - 1,
            cutoffTimestampSec = blindSlice.last().timestampEpochSec,
            cutoffClose = blindSlice.last().close,
            horizonBars = 8,
            blindBias = blindSnap.structureBias,
            blindReasoning = "Test",
            blindConfidence = blindSnap.opportunityScore,
            blindKeyLevel = blindSnap.swingHigh,
            blindInvalidation = blindSnap.swingLow,
            futureRevealed = false
        )
        assertFalse(sim.futureRevealed)

        val graded = ApexRadarEngine.revealAndGradeHistoricalReality(sim, candles)
        assertTrue(graded.futureRevealed)
        assertNotNull(graded.forecastCorrect)
        assertTrue(graded.gradingNotes.contains("Actual 8-bar move"))
    }

    @Test
    fun sentimentDivergenceAndTimelineReconstructionWorkDeterministically() {
        val candles = buildSyntheticCandles(60)
        val snap = TechnicalAnalysisEngine.analyze("BTC-USD", "1H", candles)!!.copy(
            structureBias = "BEARISH",
            rsi14 = 38.0
        )
        val quote = MarketQuote(
            symbol = "BTC-USD",
            displayName = "Bitcoin",
            providerId = "coinbase_public",
            bid = 90.0,
            ask = 90.1,
            lastPrice = 90.0,
            change24hPct = -2.4,
            high24h = 93.0,
            low24h = 89.0,
            volume24h = 10000.0,
            spread = 0.1,
            spreadPct = 0.11,
            fetchedAtEpochMs = 1L,
            latencyMs = 50L,
            health = ConnectionHealth.LIVE
        )
        val bullishNews = NewsArticleItem(
            id = "n1",
            title = "Major BTC inflow surge approved",
            summary = "Positive rally catalyst",
            sourceName = "CoinDesk",
            articleUrl = "https://example.com",
            publishedAt = "2026-11-10T10:00:00Z",
            retrievedAtEpochMs = 1000L,
            affectedInstruments = listOf("BTC-USD"),
            sentimentLabel = "POSITIVE / HAWKISH",
            verificationStatus = "PRIMARY_SOURCE_RSS"
        )
        val divs = ApexRadarEngine.detectSentimentDivergences(
            quotes = mapOf("BTC-USD" to quote),
            technicals = mapOf("BTC-USD" to snap),
            news = listOf(bullishNews),
            macroIndicators = listOf(FredMacroIndicator("T10Y2Y", "Spread", "+0.20%", "%", "2026-11-10"))
        )
        assertEquals(1, divs.size)
        assertEquals("CRITICAL_CONFLICT", divs.first().conflictSeverity)
        assertTrue(divs.first().requiresInvestigation)

        val timeline = ApexRadarEngine.reconstructNewsTimeline(listOf(bullishNews), mapOf("BTC-USD" to quote))
        assertEquals(1, timeline.size)
        assertTrue(timeline.first().marketReactionNote.contains("BTC-USD"))
    }
}
