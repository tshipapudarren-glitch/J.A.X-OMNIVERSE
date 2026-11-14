package com.example.domain.model

enum class WorkspaceGroup(val title: String) {
    COMMAND("Command & Markets"),
    INTELLIGENCE("Technical & AI Engines"),
    NEWS_RESEARCH("News & Quant Research"),
    RISK_EXECUTION("Risk, MT5 & Execution"),
    AUTONOMOUS_ADMIN("Autonomous Ops & System")
}

/**
 * All 25 Primary Workspaces from J.A.X. Omniverse Master Specification.
 */
enum class WorkspaceDestination(
    val route: String,
    val indexNumber: Int,
    val title: String,
    val shortTitle: String,
    val group: WorkspaceGroup,
    val purpose: String
) {
    GLOBAL_COMMAND_CENTRE("command_centre", 1, "Global Command Centre", "Command", WorkspaceGroup.COMMAND, "Central live market, opportunity leaderboard, risk & integration overview"),
    MARKET_OVERVIEW("market_overview", 2, "Market Overview", "Markets", WorkspaceGroup.COMMAND, "Cross-market watchlist, sessions, regimes & provider freshness"),
    OPPORTUNITY_RADAR("opportunity_radar", 3, "Opportunity Radar", "Radar", WorkspaceGroup.COMMAND, "Animated opportunity competition arena & early-formation alerts"),
    OMNISCANNER("omniscanner", 4, "Omniscanner X", "Scanner", WorkspaceGroup.COMMAND, "Continuous multi-instrument structural & volatility scanner"),
    SETUP_GENERATOR("setup_generator", 5, "Setup Generator", "Setups", WorkspaceGroup.INTELLIGENCE, "Reproducible developing vs confirmed trade plans & Entry Precision Lab"),
    CHART_INTELLIGENCE("chart_intelligence", 6, "Chart Intelligence", "Charts", WorkspaceGroup.INTELLIGENCE, "Interactive candlesticks, editable drawings & Vision AI screenshot analysis"),
    AI_INVESTIGATION_BUREAU("ai_bureau", 7, "AI Investigation Bureau", "Bureau", WorkspaceGroup.INTELLIGENCE, "Evidence verification, contradiction detector & signal forensics"),
    LIQUIDITY_INTELLIGENCE("liquidity_intel", 8, "Liquidity Intelligence", "Liquidity", WorkspaceGroup.INTELLIGENCE, "Equal highs/lows, stop-run sweeps, liquidity destinations & traps"),
    MARKET_DNA_LAB("market_dna", 9, "Market DNA Laboratory", "DNA Lab", WorkspaceGroup.INTELLIGENCE, "Live-to-historical analogue matching & causality graph"),
    NEWS_INTELLIGENCE("news_centre", 10, "News Intelligence Centre", "News", WorkspaceGroup.NEWS_RESEARCH, "Live RSS/API news verification, sentiment & impact mapping"),
    ECONOMIC_CALENDAR("economic_calendar", 11, "Economic Calendar", "Calendar", WorkspaceGroup.NEWS_RESEARCH, "Actual vs forecast surprise detector & central bank intelligence"),
    MARKET_WATCH_TOWER("watch_tower", 12, "Market Watch Tower", "Watch Tower", WorkspaceGroup.NEWS_RESEARCH, "24/7 rule-based anomaly, shock & setup lifecycle monitoring"),
    HISTORICAL_INTELLIGENCE("historical_intel", 13, "Historical Intelligence", "History", WorkspaceGroup.NEWS_RESEARCH, "Candle archive, setup expectancy explorer & regime timeline"),
    BACKTESTING_LAB("backtest_lab", 14, "Backtesting Laboratory", "Quant Lab", WorkspaceGroup.NEWS_RESEARCH, "No-lookahead bar-by-bar simulation, walk-forward & Monte Carlo"),
    RISK_COMMAND_CENTRE("risk_command", 15, "Risk Command Centre", "Risk", WorkspaceGroup.RISK_EXECUTION, "Deterministic position sizing, exposure, drawdown & stress-testing"),
    EXECUTION_INTELLIGENCE("execution_intel", 16, "Execution Intelligence", "Execution", WorkspaceGroup.RISK_EXECUTION, "3-mode permission gate, manual approvals & Global Emergency Stop"),
    MULTI_AI_COUNCIL("multi_ai_council", 17, "Multi-AI Council", "AI Council", WorkspaceGroup.INTELLIGENCE, "12 specialist agents, Debate Chamber & Chief Analyst verdict"),
    AUTONOMOUS_AGENT_CENTRE("agent_centre", 18, "Autonomous Agent Centre", "Agents", WorkspaceGroup.AUTONOMOUS_ADMIN, "Scanner network, Ghost Monitor & Mission Controller"),
    STRATEGY_ARCHITECT("strategy_architect", 19, "Strategy Architect", "Architect", WorkspaceGroup.AUTONOMOUS_ADMIN, "Natural language strategy rules & Shadow Mode evolution"),
    PERFORMANCE_OBSERVATORY("performance_obs", 20, "Performance Observatory", "Telemetry", WorkspaceGroup.AUTONOMOUS_ADMIN, "Latency, AI success rates, data quality & self-audit monitor"),
    API_CONNECTIONS("api_connections", 21, "API & Data Connections", "APIs", WorkspaceGroup.AUTONOMOUS_ADMIN, "Provider-independent endpoints, health tests & Keystore vault"),
    MT5_CONTROL_CENTRE("mt5_control", 22, "MT5 Control Centre", "MT5", WorkspaceGroup.RISK_EXECUTION, "Authenticated MetaTrader 5 bridge, heartbeat, positions & logs"),
    ALERT_CENTRE("alert_centre", 23, "Alert & Notification Centre", "Alerts", WorkspaceGroup.AUTONOMOUS_ADMIN, "Prioritized alerts, escalation & notification channel status"),
    VOICE_COMMAND_CENTRE("voice_centre", 24, "Voice Command Centre", "Voice", WorkspaceGroup.AUTONOMOUS_ADMIN, "Natural language J.A.X. voice commands with safety confirmation"),
    SYSTEM_ADMINISTRATION("system_admin", 25, "System Administration", "Admin", WorkspaceGroup.AUTONOMOUS_ADMIN, "RBAC permissions, security posture & immutable audit logs")
}
