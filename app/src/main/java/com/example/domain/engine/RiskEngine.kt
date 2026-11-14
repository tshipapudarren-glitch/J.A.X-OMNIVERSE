package com.example.domain.engine

import com.example.domain.model.ConnectionHealth
import com.example.domain.model.ExecutionMode
import com.example.domain.model.ExecutionOrderRecord
import com.example.domain.model.MarketQuote
import com.example.domain.model.RiskEvaluationResult
import com.example.domain.model.UserWorkspaceProfile
import com.example.domain.model.WatchlistInstrument
import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic Risk Guardian & Execution Permission Validator.
 * AI cannot bypass these gates.
 */
object RiskEngine {

    fun evaluateOrder(
        profile: UserWorkspaceProfile,
        instrument: WatchlistInstrument?,
        quote: MarketQuote?,
        direction: String,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        requestedRiskPct: Double,
        activeOrders: List<ExecutionOrderRecord>
    ): RiskEvaluationResult {
        val stopDist = abs(entryPrice - stopLoss)
        val rewardDist = abs(takeProfit - entryPrice)
        val rr = if (stopDist > 0.0) rewardDist / stopDist else 0.0

        val riskUsd = profile.accountBalance * (requestedRiskPct / 100.0)
        val contractSize = instrument?.contractSize ?: 1.0
        val rawLots = if (stopDist > 0.0 && contractSize > 0.0) {
            riskUsd / (stopDist * contractSize)
        } else 0.01
        val recommendedLots = rawLots.coerceIn(0.01, 50.0)

        val nowMs = System.currentTimeMillis()
        val priceFresh = quote != null &&
            quote.health == ConnectionHealth.LIVE &&
            (nowMs - quote.fetchedAtEpochMs) <= 120_000L

        val accountValid = profile.accountBalance > 0.0 && !profile.emergencyStop
        val instrumentValid = instrument != null && instrument.enabled
        val riskValid = requestedRiskPct > 0.0 && requestedRiskPct <= profile.riskPerTradePct && stopDist > 0.0
        val openCount = activeOrders.count { it.status in listOf("PENDING_APPROVAL", "APPROVED", "TRANSMITTED") }
        val positionLimitOk = openCount < profile.maxConcurrentPositions
        val spreadOk = quote == null || quote.spreadPct <= 0.65
        val duplicateOk = activeOrders.none {
            it.instrument == (instrument?.symbol ?: "") &&
                it.direction == direction &&
                it.status in listOf("PENDING_APPROVAL", "APPROVED")
        }
        val mode = ExecutionMode.fromWire(profile.executionMode)
        val permissionOk = mode != ExecutionMode.ANALYSIS_ONLY

        val gates = listOf(
            "Emergency Stop Clear" to !profile.emergencyStop,
            "Account Spec Valid" to accountValid,
            "Instrument Enabled" to instrumentValid,
            "Live Price Freshness" to priceFresh,
            "Risk Within ${profile.riskPerTradePct}%" to riskValid,
            "Position Limit ($openCount/${profile.maxConcurrentPositions})" to positionLimitOk,
            "Spread Tolerance" to spreadOk,
            "No Duplicate Order" to duplicateOk,
            "Execution Mode (${mode.name})" to permissionOk
        )

        val firstFailed = gates.firstOrNull { !it.second }?.first
        return RiskEvaluationResult(
            passed = firstFailed == null,
            recommendedLots = recommendedLots,
            riskAmountUsd = riskUsd,
            stopDistancePoints = stopDist,
            rewardToRisk = rr,
            gateResults = gates,
            blockingReason = firstFailed?.let { "Blocked by Risk Gate: $it" }
        )
    }

    fun calculatePositionSize(
        accountBalance: Double,
        riskPct: Double,
        entryPrice: Double,
        stopLoss: Double,
        contractSize: Double = 1.0
    ): Pair<Double, Double> {
        val riskUsd = max(0.0, accountBalance * (riskPct / 100.0))
        val dist = abs(entryPrice - stopLoss)
        if (dist <= 0.0 || contractSize <= 0.0) return riskUsd to 0.0
        return riskUsd to (riskUsd / (dist * contractSize))
    }
}
