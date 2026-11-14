package com.example.domain.engine

import com.example.domain.model.Candle
import com.example.domain.model.StrategyVariantParams
import com.example.domain.model.StrategyVariantResult
import kotlin.math.roundToInt

/**
 * STRATEGY DNA MUTATION LAB
 * Creates controlled parameter variations of a baseline strategy, tests each variant
 * independently using zero-lookahead chronological bar-by-bar walk-forward simulation,
 * compares robustness, and issues deterministic PROMOTE / SHADOW_TEST / REJECT verdicts.
 */
object StrategyMutationEngine {

    private data class MutationSpec(
        val id: String,
        val version: String,
        val name: String,
        val params: StrategyVariantParams
    )

    fun runMutationLab(
        candles: List<Candle>,
        baseFastEma: Int = 10,
        baseSlowEma: Int = 21,
        baseAtrStopMult: Double = 1.4,
        baseAtrTargetMult: Double = 2.2,
        spreadPct: Double = 0.04,
        slippagePct: Double = 0.02,
        riskPerTradePct: Double = 1.0
    ): List<StrategyVariantResult> {
        if (candles.size < 45) return emptyList()

        val baseParams = StrategyVariantParams(
            fastEma = baseFastEma,
            slowEma = baseSlowEma,
            atrStopMult = baseAtrStopMult,
            atrTargetMult = baseAtrTargetMult,
            requireRsiFilter = false,
            maxHoldBars = 14
        )

        val variants = listOf(
            MutationSpec(
                id = "dna_v1_baseline",
                version = "v1.0-BASE",
                name = "Baseline EMA Displacement",
                params = baseParams
            ),
            MutationSpec(
                id = "dna_v2_tight_scalp",
                version = "v1.1-MUT-A",
                name = "Fast Momentum Mutation",
                params = baseParams.copy(
                    fastEma = maxOf(5, baseFastEma - 3),
                    slowEma = maxOf(12, baseSlowEma - 6),
                    atrStopMult = 1.1,
                    atrTargetMult = 1.8,
                    maxHoldBars = 10
                )
            ),
            MutationSpec(
                id = "dna_v3_asymmetric_rr",
                version = "v1.2-MUT-B",
                name = "Asymmetric R:R Expansion",
                params = baseParams.copy(
                    atrStopMult = 1.25,
                    atrTargetMult = 3.0,
                    maxHoldBars = 20
                )
            ),
            MutationSpec(
                id = "dna_v4_rsi_guarded",
                version = "v1.3-MUT-C",
                name = "RSI Momentum-Filtered DNA",
                params = baseParams.copy(
                    requireRsiFilter = true,
                    atrStopMult = 1.5,
                    atrTargetMult = 2.4
                )
            ),
            MutationSpec(
                id = "dna_v5_trend_anchor",
                version = "v1.4-MUT-D",
                name = "Slow Structural Swing DNA",
                params = baseParams.copy(
                    fastEma = baseFastEma + 4,
                    slowEma = baseSlowEma + 9,
                    atrStopMult = 1.8,
                    atrTargetMult = 3.2,
                    requireRsiFilter = true,
                    maxHoldBars = 24
                )
            )
        )

        val evaluated = variants.map { spec ->
            val sim = QuantBacktestEngine.runParameterizedSimulation(
                candles = candles,
                spreadPct = spreadPct,
                slippagePct = slippagePct,
                riskPerTradePct = riskPerTradePct,
                fastEma = spec.params.fastEma,
                slowEma = spec.params.slowEma,
                atrStopMult = spec.params.atrStopMult,
                atrTargetMult = spec.params.atrTargetMult,
                requireRsiFilter = spec.params.requireRsiFilter,
                maxHoldBars = spec.params.maxHoldBars
            )

            val diffs = buildParamDiffs(baseParams, spec.params)
            val robustness = computeRobustnessScore(sim)
            val recommendation = when {
                sim.trades < 3 -> "REJECT"
                sim.walkForwardPassed && robustness >= 62 && sim.maxDrawdownPct <= 8.0 && sim.profitFactor >= 1.25 -> "PROMOTE"
                sim.expectancyR > 0.0 && robustness >= 45 -> "SHADOW_TEST"
                else -> "REJECT"
            }

            val rationale = buildString {
                if (sim.trades < 3) {
                    append("Insufficient sample (${sim.trades} trades < 3 minimum).")
                } else {
                    append("In-sample ${"%+.2f".format(sim.inSampleExpectancyR)}R vs Out-of-sample ${"%+.2f".format(sim.outSampleExpectancyR)}R. ")
                    append(if (sim.walkForwardPassed) "Walk-forward validated. " else "Walk-forward failed stability threshold. ")
                    append("Profit Factor ${"%.2f".format(sim.profitFactor)}, Max DD ${"%.2f".format(sim.maxDrawdownPct)}%.")
                }
            }

            StrategyVariantResult(
                variantId = spec.id,
                versionTag = spec.version,
                variantName = spec.name,
                parameterChanges = diffs,
                params = spec.params,
                totalTrades = sim.trades,
                winRatePct = sim.winRatePct,
                profitFactor = sim.profitFactor,
                expectancyR = sim.expectancyR,
                maxDrawdownPct = sim.maxDrawdownPct,
                netReturnPct = sim.netReturnPct,
                inSampleExpectancyR = sim.inSampleExpectancyR,
                outSampleExpectancyR = sim.outSampleExpectancyR,
                robustnessScore = robustness,
                recommendation = recommendation,
                rationale = rationale
            )
        }

        return evaluated.sortedByDescending { it.robustnessScore }
    }

    private fun buildParamDiffs(base: StrategyVariantParams, mut: StrategyVariantParams): List<String> {
        val diffs = mutableListOf<String>()
        if (mut.fastEma != base.fastEma || mut.slowEma != base.slowEma) {
            diffs += "EMA(${base.fastEma}/${base.slowEma}) -> EMA(${mut.fastEma}/${mut.slowEma})"
        }
        if (mut.atrStopMult != base.atrStopMult) {
            diffs += "SL ${"%.2f".format(base.atrStopMult)}x ATR -> ${"%.2f".format(mut.atrStopMult)}x ATR"
        }
        if (mut.atrTargetMult != base.atrTargetMult) {
            diffs += "TP ${"%.2f".format(base.atrTargetMult)}x ATR -> ${"%.2f".format(mut.atrTargetMult)}x ATR"
        }
        if (mut.requireRsiFilter != base.requireRsiFilter) {
            diffs += "RSI Filter: ${if (mut.requireRsiFilter) "ENABLED (45-68 / 32-55)" else "OFF"}"
        }
        if (mut.maxHoldBars != base.maxHoldBars) {
            diffs += "Max Hold: ${base.maxHoldBars} -> ${mut.maxHoldBars} bars"
        }
        if (diffs.isEmpty()) diffs += "Baseline Reference Control (No Parameter Mutation)"
        return diffs
    }

    private fun computeRobustnessScore(sim: QuantBacktestEngine.SimSummary): Int {
        if (sim.trades == 0) return 0
        var score = 30.0
        if (sim.walkForwardPassed) score += 25.0
        score += (sim.profitFactor.coerceIn(0.0, 3.0) * 10.0)
        score += (sim.expectancyR.coerceIn(-1.0, 1.5) * 15.0)
        score -= (sim.maxDrawdownPct.coerceIn(0.0, 20.0) * 1.2)
        if (sim.trades >= 5) score += 8.0
        return score.roundToInt().coerceIn(0, 100)
    }
}
