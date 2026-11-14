package com.example.domain.engine

import com.example.domain.model.WorkspaceDestination

sealed interface VoiceIntent {
    data class ScanInstrument(val symbolHint: String) : VoiceIntent
    data class InvestigateInstrument(val symbolHint: String) : VoiceIntent
    data class Navigate(val destination: WorkspaceDestination) : VoiceIntent
    data object RunGlobalScan : VoiceIntent
    data object ShowBullishSetups : VoiceIntent
    data object InvestigateNews : VoiceIntent
    data object GenerateDailyBriefing : VoiceIntent
    data object ShowRiskExposure : VoiceIntent
    data class AskChiefAnalyst(val query: String) : VoiceIntent
}

/**
 * Natural-language router for J.A.X. Voice Command Pro.
 * Never interprets voice commands as trade execution authorization.
 */
object VoiceCommandParser {

    fun parse(rawCommand: String): VoiceIntent {
        val text = rawCommand.trim().lowercase()
        val symbolHint = when {
            "gold" in text || "xau" in text || "paxg" in text -> "PAXG-USD"
            "bitcoin" in text || "btc" in text -> "BTC-USD"
            "ethereum" in text || "eth" in text -> "ETH-USD"
            "euro" in text || "eur" in text -> "EUR-USD"
            "pound" in text || "gbp" in text -> "GBP-USD"
            "yen" in text || "jpy" in text || "dollar" in text || "dxy" in text -> "USD-JPY"
            else -> ""
        }

        return when {
            "briefing" in text || "daily" in text -> VoiceIntent.GenerateDailyBriefing
            "risk" in text || "exposure" in text || "drawdown" in text -> VoiceIntent.ShowRiskExposure
            "news" in text || "calendar" in text || "fed" in text || "ecb" in text -> VoiceIntent.InvestigateNews
            "bullish" in text || "setup" in text -> VoiceIntent.ShowBullishSetups
            "investigate" in text && symbolHint.isNotEmpty() -> VoiceIntent.InvestigateInstrument(symbolHint)
            "scan" in text && symbolHint.isNotEmpty() -> VoiceIntent.ScanInstrument(symbolHint)
            "opportunit" in text || "radar" in text || "scan" in text -> VoiceIntent.RunGlobalScan
            "mt5" in text || "metatrader" in text -> VoiceIntent.Navigate(WorkspaceDestination.MT5_CONTROL_CENTRE)
            "chart" in text -> VoiceIntent.Navigate(WorkspaceDestination.CHART_INTELLIGENCE)
            "backtest" in text || "quant" in text -> VoiceIntent.Navigate(WorkspaceDestination.BACKTESTING_LAB)
            "monitor" in text || "watch" in text -> VoiceIntent.Navigate(WorkspaceDestination.MARKET_WATCH_TOWER)
            else -> VoiceIntent.AskChiefAnalyst(rawCommand)
        }
    }
}
