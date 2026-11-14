package com.example.data.network

import com.example.domain.model.Candle
import com.example.domain.model.ConnectionHealth
import com.example.domain.model.MarketQuote
import com.example.domain.model.WatchlistInstrument
import com.example.security.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class MarketFeedBundle(
    val quote: MarketQuote,
    val candles: List<Candle>
)

/**
 * Real multi-provider market data ingestion engine.
 * Connects to Coinbase Exchange, Kraken Public API, Frankfurter ECB FX, Binance Spot,
 * Yahoo Finance, Finnhub, Financial Modeling Prep (FMP), Alpha Vantage, FRED, and TwelveData.
 * NEVER fabricates prices or candles when offline or disconnected.
 */
class MarketDataService(
    private val secretVault: SecretVault,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchInstrumentData(
        instrument: WatchlistInstrument,
        timeframe: String = "1H"
    ): MarketFeedBundle = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            when (instrument.providerId) {
                "coinbase_public" -> fetchCoinbase(instrument, timeframe, startMs)
                "kraken_public" -> fetchKraken(instrument, timeframe, startMs)
                "frankfurter_ecb" -> fetchFrankfurterFx(instrument, startMs)
                "binance" -> fetchBinance(instrument, timeframe, startMs)
                "yahoo" -> fetchYahooFinance(instrument, timeframe, startMs)
                "finnhub" -> fetchFinnhub(instrument, timeframe, startMs)
                "fmp" -> fetchFmp(instrument, timeframe, startMs)
                "alpha_vantage" -> fetchAlphaVantage(instrument, timeframe, startMs)
                "fred" -> fetchFredSeries(instrument, startMs)
                "twelvedata" -> fetchTwelveData(instrument, timeframe, startMs)
                else -> fetchCoinbase(instrument, timeframe, startMs)
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startMs
            MarketFeedBundle(
                quote = MarketQuote(
                    symbol = instrument.symbol,
                    displayName = instrument.displayName,
                    providerId = instrument.providerId,
                    bid = 0.0,
                    ask = 0.0,
                    lastPrice = 0.0,
                    change24hPct = 0.0,
                    high24h = 0.0,
                    low24h = 0.0,
                    volume24h = 0.0,
                    spread = 0.0,
                    spreadPct = 0.0,
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    latencyMs = latency,
                    health = ConnectionHealth.ERROR,
                    errorMessage = e.localizedMessage ?: "Provider request failed"
                ),
                candles = emptyList()
            )
        }
    }

    suspend fun testProviderEndpoint(providerId: String, baseUrl: String): Pair<Boolean, Pair<Long, String>> =
        withContext(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            try {
                when (providerId) {
                    "coinbase_public" -> simpleGetProbe("https://api.exchange.coinbase.com/products/BTC-USD/ticker", startMs)
                    "kraken_public" -> simpleGetProbe("https://api.kraken.com/0/public/Time", startMs)
                    "frankfurter_ecb" -> simpleGetProbe("https://api.frankfurter.app/latest?from=EUR&to=USD", startMs)
                    "institutional_rss" -> simpleGetProbe("https://www.federalreserve.gov/feeds/press_all.xml", startMs)
                    "yahoo" -> {
                        val req = Request.Builder()
                            .url("https://query1.finance.yahoo.com/v8/finance/chart/SPY?interval=1d&range=5d")
                            .header("User-Agent", YAHOO_USER_AGENT)
                            .build()
                        executeProbeRequest(req, startMs)
                    }
                    "binance" -> probeBinance(startMs)
                    "finnhub" -> {
                        val key = secretVault.resolveProviderSecret("finnhub")
                            ?: return@withContext false to (0L to "FINNHUB_API_KEY not configured (Secrets panel or API Vault)")
                        val req = Request.Builder()
                            .url("https://finnhub.io/api/v1/quote?symbol=AAPL&token=${enc(key)}")
                            .build()
                        executeProbeRequest(req, startMs) { body ->
                            val obj = JSONObject(body)
                            if (obj.has("error")) error(obj.getString("error"))
                            val price = obj.optDouble("c", 0.0)
                            "Finnhub AAPL=$price"
                        }
                    }
                    "fmp" -> {
                        val key = secretVault.resolveProviderSecret("fmp")
                            ?: return@withContext false to (0L to "FMP_API_KEY not configured (Secrets panel or API Vault)")
                        val stableReq = Request.Builder()
                            .url("https://financialmodelingprep.com/stable/quote?symbol=AAPL&apikey=${enc(key)}")
                            .build()
                        val res = executeProbeRequest(stableReq, startMs) { body ->
                            if (body.trimStart().startsWith("{")) {
                                val obj = JSONObject(body)
                                val err = obj.optString("Error Message")
                                if (err.isNotBlank()) error(err)
                            }
                            "FMP Quote OK"
                        }
                        if (res.first) res else {
                            val v3Req = Request.Builder()
                                .url("https://financialmodelingprep.com/api/v3/quote/AAPL?apikey=${enc(key)}")
                                .build()
                            executeProbeRequest(v3Req, startMs) { "FMP v3 Quote OK" }
                        }
                    }
                    "alpha_vantage" -> {
                        val key = secretVault.resolveProviderSecret("alpha_vantage")
                            ?: return@withContext false to (0L to "ALPHA_VANTAGE_API_KEY not configured (Secrets panel or API Vault)")
                        val req = Request.Builder()
                            .url("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=IBM&apikey=${enc(key)}")
                            .build()
                        executeProbeRequest(req, startMs) { body ->
                            val obj = JSONObject(body)
                            val err = obj.optString("Error Message").ifBlank { obj.optString("Note") }.ifBlank { obj.optString("Information") }
                            if (err.isNotBlank()) error(err)
                            "Alpha Vantage OK"
                        }
                    }
                    "fred" -> {
                        val key = secretVault.resolveProviderSecret("fred")
                            ?: return@withContext false to (0L to "FRED_API_KEY not configured (Secrets panel or API Vault)")
                        val req = Request.Builder()
                            .url("https://api.stlouisfed.org/fred/series/observations?series_id=FEDFUNDS&api_key=${enc(key)}&file_type=json&limit=2")
                            .build()
                        executeProbeRequest(req, startMs) { body ->
                            val obj = JSONObject(body)
                            if (obj.has("error_message")) error(obj.getString("error_message"))
                            "FRED Macro OK"
                        }
                    }
                    "twelvedata" -> {
                        val key = secretVault.resolveProviderSecret("twelvedata")
                            ?: return@withContext false to (0L to "TWELVEDATA_API_KEY not configured (Secrets panel or API Vault)")
                        val req = Request.Builder()
                            .url("https://api.twelvedata.com/price?symbol=AAPL&apikey=${enc(key)}")
                            .build()
                        executeProbeRequest(req, startMs) { body ->
                            val obj = JSONObject(body)
                            if (obj.optString("status") == "error") error(obj.optString("message", "TwelveData error"))
                            "TwelveData AAPL=${obj.optString("price")}"
                        }
                    }
                    else -> {
                        val cleanUrl = baseUrl.ifBlank { return@withContext false to (0L to "No endpoint URL configured") }
                        simpleGetProbe(cleanUrl, startMs)
                    }
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startMs
                false to (latency to (e.localizedMessage ?: "Connection unreachable"))
            }
        }

    private fun simpleGetProbe(url: String, startMs: Long): Pair<Boolean, Pair<Long, String>> {
        val req = Request.Builder().url(url).header("User-Agent", "JAX-Omniverse-Android/1.0").build()
        return executeProbeRequest(req, startMs)
    }

    private fun executeProbeRequest(
        req: Request,
        startMs: Long,
        validator: ((String) -> String)? = null
    ): Pair<Boolean, Pair<Long, String>> {
        client.newCall(req).execute().use { resp ->
            val latency = System.currentTimeMillis() - startMs
            val body = resp.body?.string().orEmpty()
            return if (resp.isSuccessful) {
                try {
                    val label = validator?.invoke(body) ?: "HTTP ${resp.code} OK"
                    true to (latency to label)
                } catch (e: Exception) {
                    false to (latency to (e.localizedMessage ?: "Invalid response payload"))
                }
            } else {
                false to (latency to "HTTP ${resp.code}: ${resp.message}")
            }
        }
    }

    private fun probeBinance(startMs: Long): Pair<Boolean, Pair<Long, String>> {
        val apiKey = secretVault.resolveProviderSecret("binance")
        val apiSecret = secretVault.resolveProviderSecret("binance_secret")
        if (!apiKey.isNullOrBlank() && !apiSecret.isNullOrBlank()) {
            val ts = System.currentTimeMillis()
            val query = "timestamp=$ts"
            val sig = hmacSha256(query, apiSecret)
            val req = Request.Builder()
                .url("https://api.binance.com/api/v3/account?$query&signature=$sig")
                .header("X-MBX-APIKEY", apiKey)
                .build()
            return executeProbeRequest(req, startMs) { body ->
                val obj = JSONObject(body)
                if (obj.has("code") && obj.optInt("code") < 0) {
                    error(obj.optString("msg", "Binance auth error"))
                }
                "Binance Auth Account OK (canTrade=${obj.optBoolean("canTrade")})"
            }
        }
        val reqBuilder = Request.Builder().url("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT")
        if (!apiKey.isNullOrBlank()) {
            reqBuilder.header("X-MBX-APIKEY", apiKey)
        }
        return executeProbeRequest(reqBuilder.build(), startMs) { body ->
            val obj = JSONObject(body)
            val keyNote = if (!apiKey.isNullOrBlank()) "API Key attached" else "Public Spot"
            "Binance $keyNote (BTCUSDT=${obj.optString("price")})"
        }
    }

    private fun fetchBinance(
        instrument: WatchlistInstrument,
        timeframe: String,
        startMs: Long
    ): MarketFeedBundle {
        val rawSym = instrument.providerSymbol.ifBlank { instrument.symbol }.uppercase()
        val sym = when {
            rawSym.contains("-USD") -> rawSym.replace("-USD", "USDT")
            rawSym.contains("/") -> rawSym.replace("/", "")
            else -> rawSym.replace("-", "")
        }
        val interval = when (timeframe) {
            "15M" -> "15m"
            "1H" -> "1h"
            "4H" -> "4h"
            "1D" -> "1d"
            else -> "1h"
        }
        val apiKey = secretVault.resolveProviderSecret("binance")

        val tickerReq = Request.Builder()
            .url("https://api.binance.com/api/v3/ticker/24hr?symbol=$sym")
            .apply { if (!apiKey.isNullOrBlank()) header("X-MBX-APIKEY", apiKey) }
            .build()
        val tObj = client.newCall(tickerReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Binance 24hr HTTP ${resp.code}: $body")
            JSONObject(body)
        }

        val bookReq = Request.Builder()
            .url("https://api.binance.com/api/v3/ticker/bookTicker?symbol=$sym")
            .apply { if (!apiKey.isNullOrBlank()) header("X-MBX-APIKEY", apiKey) }
            .build()
        val bObj = client.newCall(bookReq).execute().use { resp ->
            if (resp.isSuccessful) JSONObject(resp.body?.string().orEmpty()) else null
        }

        val last = tObj.optString("lastPrice", "0").toDoubleOrNull() ?: 0.0
        val bid = bObj?.optString("bidPrice")?.toDoubleOrNull() ?: tObj.optString("bidPrice", "0").toDoubleOrNull() ?: last
        val ask = bObj?.optString("askPrice")?.toDoubleOrNull() ?: tObj.optString("askPrice", "0").toDoubleOrNull() ?: last
        val changePct = tObj.optString("priceChangePercent", "0").toDoubleOrNull() ?: 0.0
        val high24 = tObj.optString("highPrice", "0").toDoubleOrNull() ?: last
        val low24 = tObj.optString("lowPrice", "0").toDoubleOrNull() ?: last
        val vol24 = tObj.optString("volume", "0").toDoubleOrNull() ?: 0.0

        val klinesReq = Request.Builder()
            .url("https://api.binance.com/api/v3/klines?symbol=$sym&interval=$interval&limit=120")
            .apply { if (!apiKey.isNullOrBlank()) header("X-MBX-APIKEY", apiKey) }
            .build()
        val kArr = client.newCall(klinesReq).execute().use { resp ->
            if (!resp.isSuccessful) error("Binance klines HTTP ${resp.code}")
            JSONArray(resp.body?.string().orEmpty())
        }
        val candles = mutableListOf<Candle>()
        for (i in 0 until kArr.length()) {
            val row = kArr.getJSONArray(i)
            candles += Candle(
                timestampEpochSec = row.getLong(0) / 1000L,
                open = row.getString(1).toDouble(),
                high = row.getString(2).toDouble(),
                low = row.getString(3).toDouble(),
                close = row.getString(4).toDouble(),
                volume = row.getString(5).toDouble()
            )
        }
        val spread = if (ask > bid && bid > 0.0) ask - bid else 0.0
        val spreadPct = if (last > 0.0) (spread / last) * 100.0 else 0.0

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = bid,
                ask = ask,
                lastPrice = last,
                change24hPct = changePct,
                high24h = high24,
                low24h = low24,
                volume24h = vol24,
                spread = spread,
                spreadPct = spreadPct,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE
            ),
            candles = candles
        )
    }

    private fun fetchYahooFinance(
        instrument: WatchlistInstrument,
        timeframe: String,
        startMs: Long
    ): MarketFeedBundle {
        val sym = instrument.providerSymbol.ifBlank { instrument.symbol }
        val (interval, range) = when (timeframe) {
            "15M" -> "15m" to "5d"
            "1H" -> "60m" to "1mo"
            "4H" -> "60m" to "3mo"
            "1D" -> "1d" to "6mo"
            else -> "60m" to "1mo"
        }
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/${enc(sym)}?interval=$interval&range=$range"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", YAHOO_USER_AGENT)
            .build()

        val root = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Yahoo Finance HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty())
        }
        val chart = root.getJSONObject("chart")
        if (!chart.isNull("error")) {
            error(chart.getJSONObject("error").optString("description", "Yahoo Finance chart error"))
        }
        val resObj = chart.getJSONArray("result").getJSONObject(0)
        val meta = resObj.getJSONObject("meta")
        val timestamps = resObj.optJSONArray("timestamp") ?: JSONArray()
        val quoteArr = resObj.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val opens = quoteArr.optJSONArray("open") ?: JSONArray()
        val highs = quoteArr.optJSONArray("high") ?: JSONArray()
        val lows = quoteArr.optJSONArray("low") ?: JSONArray()
        val closes = quoteArr.optJSONArray("close") ?: JSONArray()
        val volumes = quoteArr.optJSONArray("volume") ?: JSONArray()

        val candles = mutableListOf<Candle>()
        for (i in 0 until timestamps.length()) {
            if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i)) continue
            candles += Candle(
                timestampEpochSec = timestamps.getLong(i),
                open = opens.getDouble(i),
                high = highs.getDouble(i),
                low = lows.getDouble(i),
                close = closes.getDouble(i),
                volume = if (!volumes.isNull(i)) volumes.optDouble(i, 0.0) else 0.0
            )
        }
        val trimmed = candles.takeLast(120)
        val last = meta.optDouble("regularMarketPrice", trimmed.lastOrNull()?.close ?: 0.0)
        val prevClose = meta.optDouble("chartPreviousClose", trimmed.dropLast(1).lastOrNull()?.close ?: last)
        val changePct = if (prevClose > 0.0) ((last - prevClose) / prevClose) * 100.0 else 0.0
        val high24 = meta.optDouble("regularMarketDayHigh", trimmed.takeLast(24).maxOfOrNull { it.high } ?: last)
        val low24 = meta.optDouble("regularMarketDayLow", trimmed.takeLast(24).minOfOrNull { it.low } ?: last)
        val vol24 = meta.optDouble("regularMarketVolume", trimmed.takeLast(24).sumOf { it.volume })

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = last,
                ask = last,
                lastPrice = last,
                change24hPct = changePct,
                high24h = high24,
                low24h = low24,
                volume24h = vol24,
                spread = 0.0,
                spreadPct = 0.0,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE
            ),
            candles = trimmed
        )
    }

    private fun fetchFinnhub(
        instrument: WatchlistInstrument,
        timeframe: String,
        startMs: Long
    ): MarketFeedBundle {
        val apiKey = secretVault.resolveProviderSecret("finnhub")
            ?: error("FINNHUB_API_KEY not configured (Secrets panel or API Vault)")
        val sym = instrument.providerSymbol.ifBlank { instrument.symbol }

        val quoteReq = Request.Builder()
            .url("https://finnhub.io/api/v1/quote?symbol=${enc(sym)}&token=${enc(apiKey)}")
            .build()
        val qObj = client.newCall(quoteReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Finnhub quote HTTP ${resp.code}: $body")
            JSONObject(body)
        }
        if (qObj.has("error")) error(qObj.getString("error"))
        val last = qObj.optDouble("c", 0.0)
        if (last <= 0.0) error("Finnhub returned empty quote for $sym")
        val high = qObj.optDouble("h", last)
        val low = qObj.optDouble("l", last)
        val changePct = qObj.optDouble("dp", 0.0)

        val (res, lookbackSec) = when (timeframe) {
            "15M" -> "15" to 5L * 86400L
            "1H" -> "60" to 14L * 86400L
            "4H" -> "240" to 45L * 86400L
            "1D" -> "D" to 180L * 86400L
            else -> "60" to 14L * 86400L
        }
        val toSec = System.currentTimeMillis() / 1000L
        val fromSec = toSec - lookbackSec
        val candles = mutableListOf<Candle>()
        var candleNote: String? = null

        val candleReq = Request.Builder()
            .url("https://finnhub.io/api/v1/stock/candle?symbol=${enc(sym)}&resolution=$res&from=$fromSec&to=$toSec&token=${enc(apiKey)}")
            .build()
        client.newCall(candleReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val cObj = JSONObject(resp.body?.string().orEmpty())
                if (cObj.optString("s") == "ok") {
                    val tArr = cObj.getJSONArray("t")
                    val oArr = cObj.getJSONArray("o")
                    val hArr = cObj.getJSONArray("h")
                    val lArr = cObj.getJSONArray("l")
                    val clArr = cObj.getJSONArray("c")
                    val vArr = cObj.optJSONArray("v")
                    for (i in 0 until tArr.length()) {
                        candles += Candle(
                            timestampEpochSec = tArr.getLong(i),
                            open = oArr.getDouble(i),
                            high = hArr.getDouble(i),
                            low = lArr.getDouble(i),
                            close = clArr.getDouble(i),
                            volume = vArr?.optDouble(i, 0.0) ?: 0.0
                        )
                    }
                } else {
                    candleNote = "Quote live (Finnhub candles unavailable: ${cObj.optString("s")})"
                }
            } else {
                candleNote = "Quote live (Finnhub candles HTTP ${resp.code})"
            }
        }

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = last,
                ask = last,
                lastPrice = last,
                change24hPct = changePct,
                high24h = high,
                low24h = low,
                volume24h = candles.takeLast(24).sumOf { it.volume },
                spread = 0.0,
                spreadPct = 0.0,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE,
                errorMessage = candleNote
            ),
            candles = candles.takeLast(120)
        )
    }

    private fun fetchFmp(
        instrument: WatchlistInstrument,
        timeframe: String,
        startMs: Long
    ): MarketFeedBundle {
        val apiKey = secretVault.resolveProviderSecret("fmp")
            ?: error("FMP_API_KEY not configured (Secrets panel or API Vault)")
        val sym = instrument.providerSymbol.ifBlank { instrument.symbol }

        val quoteJsonText = executeWithFallback(
            primaryUrl = "https://financialmodelingprep.com/stable/quote?symbol=${enc(sym)}&apikey=${enc(apiKey)}",
            fallbackUrl = "https://financialmodelingprep.com/api/v3/quote/${enc(sym)}?apikey=${enc(apiKey)}"
        )
        val qArr = JSONArray(quoteJsonText)
        if (qArr.length() == 0) error("FMP returned no quote for $sym")
        val qObj = qArr.getJSONObject(0)
        val last = qObj.optDouble("price", 0.0)
        val changePct = qObj.optDouble("changePercentage", qObj.optDouble("changesPercentage", 0.0))
        val high24 = qObj.optDouble("dayHigh", last)
        val low24 = qObj.optDouble("dayLow", last)
        val vol24 = qObj.optDouble("volume", 0.0)

        val interval = when (timeframe) {
            "15M" -> "15min"
            "1H" -> "1hour"
            "4H" -> "4hour"
            else -> "1hour"
        }
        val histText = executeWithFallback(
            primaryUrl = "https://financialmodelingprep.com/stable/historical-chart/$interval?symbol=${enc(sym)}&apikey=${enc(apiKey)}",
            fallbackUrl = "https://financialmodelingprep.com/api/v3/historical-chart/$interval/${enc(sym)}?apikey=${enc(apiKey)}"
        )
        val hArr = JSONArray(histText)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
        }
        val candles = mutableListOf<Candle>()
        val limit = minOf(120, hArr.length())
        for (i in 0 until limit) {
            val row = hArr.getJSONObject(i)
            val epoch = ((sdf.parse(row.optString("date"))?.time ?: (System.currentTimeMillis() - i * 3600_000L)) / 1000L)
            candles += Candle(
                timestampEpochSec = epoch,
                open = row.getDouble("open"),
                high = row.getDouble("high"),
                low = row.getDouble("low"),
                close = row.getDouble("close"),
                volume = row.optDouble("volume", 0.0)
            )
        }
        candles.sortBy { it.timestampEpochSec }

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = last,
                ask = last,
                lastPrice = last,
                change24hPct = changePct,
                high24h = high24,
                low24h = low24,
                volume24h = vol24,
                spread = 0.0,
                spreadPct = 0.0,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE
            ),
            candles = candles
        )
    }

    private fun executeWithFallback(primaryUrl: String, fallbackUrl: String): String {
        val req1 = Request.Builder().url(primaryUrl).build()
        client.newCall(req1).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful && !body.trimStart().startsWith("{\"Error")) {
                return body
            }
        }
        val req2 = Request.Builder().url(fallbackUrl).build()
        client.newCall(req2).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: $body")
            if (body.trimStart().startsWith("{")) {
                val obj = JSONObject(body)
                val msg = obj.optString("Error Message")
                if (msg.isNotBlank()) error(msg)
            }
            return body
        }
    }

    private fun fetchAlphaVantage(
        instrument: WatchlistInstrument,
        timeframe: String,
        startMs: Long
    ): MarketFeedBundle {
        val apiKey = secretVault.resolveProviderSecret("alpha_vantage")
            ?: error("ALPHA_VANTAGE_API_KEY not configured (Secrets panel or API Vault)")
        val sym = instrument.providerSymbol.ifBlank { instrument.symbol }

        val (fn, extraParam, seriesKey) = when (timeframe) {
            "15M" -> Triple("TIME_SERIES_INTRADAY", "&interval=15min", "Time Series (15min)")
            "1H" -> Triple("TIME_SERIES_INTRADAY", "&interval=60min", "Time Series (60min)")
            else -> Triple("TIME_SERIES_DAILY", "", "Time Series (Daily)")
        }
        val url = "https://www.alphavantage.co/query?function=$fn&symbol=${enc(sym)}$extraParam&outputsize=compact&apikey=${enc(apiKey)}"
        val json = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Alpha Vantage HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty())
        }
        val errMsg = json.optString("Error Message").ifBlank { json.optString("Note") }.ifBlank { json.optString("Information") }
        if (errMsg.isNotBlank()) error(errMsg)

        val seriesObj = json.optJSONObject(seriesKey)
            ?: error("Alpha Vantage missing '$seriesKey' for $sym")

        val fmtLong = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
        }
        val fmtShort = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val keys = seriesObj.keys().asSequence().toList().sorted()
        val candles = mutableListOf<Candle>()
        keys.takeLast(120).forEach { dt ->
            val bar = seriesObj.getJSONObject(dt)
            val parsedMs = (if (dt.length > 10) fmtLong.parse(dt) else fmtShort.parse(dt))?.time ?: 0L
            candles += Candle(
                timestampEpochSec = parsedMs / 1000L,
                open = bar.getString("1. open").toDouble(),
                high = bar.getString("2. high").toDouble(),
                low = bar.getString("3. low").toDouble(),
                close = bar.getString("4. close").toDouble(),
                volume = bar.optString("5. volume", "0").toDoubleOrNull() ?: 0.0
            )
        }
        val last = candles.lastOrNull()?.close ?: 0.0
        val prev = candles.dropLast(1).lastOrNull()?.close ?: last
        val changePct = if (prev > 0.0) ((last - prev) / prev) * 100.0 else 0.0

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = last,
                ask = last,
                lastPrice = last,
                change24hPct = changePct,
                high24h = candles.takeLast(24).maxOfOrNull { it.high } ?: last,
                low24h = candles.takeLast(24).minOfOrNull { it.low } ?: last,
                volume24h = candles.takeLast(24).sumOf { it.volume },
                spread = 0.0,
                spreadPct = 0.0,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE
            ),
            candles = candles
        )
    }

    private fun fetchFredSeries(
        instrument: WatchlistInstrument,
        startMs: Long
    ): MarketFeedBundle {
        val apiKey = secretVault.resolveProviderSecret("fred")
            ?: error("FRED_API_KEY not configured (Secrets panel or API Vault)")
        val seriesId = instrument.providerSymbol.ifBlank { instrument.symbol }.uppercase()
        val url = "https://api.stlouisfed.org/fred/series/observations?series_id=${enc(seriesId)}&api_key=${enc(apiKey)}&file_type=json&sort_order=desc&limit=120"
        val json = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("FRED HTTP ${resp.code}: $body")
            JSONObject(body)
        }
        if (json.has("error_message")) error(json.getString("error_message"))

        val obsArr = json.getJSONArray("observations")
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val points = mutableListOf<Pair<Long, Double>>()
        for (i in 0 until obsArr.length()) {
            val row = obsArr.getJSONObject(i)
            val valStr = row.optString("value", ".")
            val num = valStr.toDoubleOrNull() ?: continue
            val epoch = (fmt.parse(row.optString("date"))?.time ?: 0L) / 1000L
            points += epoch to num
        }
        points.sortBy { it.first }
        if (points.isEmpty()) error("FRED series $seriesId returned no numeric observations")

        val candles = mutableListOf<Candle>()
        var prevVal = points.first().second
        points.forEach { (epoch, value) ->
            candles += Candle(
                timestampEpochSec = epoch,
                open = prevVal,
                high = maxOf(prevVal, value),
                low = minOf(prevVal, value),
                close = value,
                volume = 0.0
            )
            prevVal = value
        }
        val last = candles.last().close
        val prev = candles.dropLast(1).lastOrNull()?.close ?: last
        val changePct = if (prev != 0.0) ((last - prev) / kotlin.math.abs(prev)) * 100.0 else 0.0

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = last,
                ask = last,
                lastPrice = last,
                change24hPct = changePct,
                high24h = candles.takeLast(12).maxOfOrNull { it.high } ?: last,
                low24h = candles.takeLast(12).minOfOrNull { it.low } ?: last,
                volume24h = 0.0,
                spread = 0.0,
                spreadPct = 0.0,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE
            ),
            candles = candles
        )
    }

    private fun fetchCoinbase(
        instrument: WatchlistInstrument,
        timeframe: String,
        startMs: Long
    ): MarketFeedBundle {
        val product = instrument.providerSymbol.ifBlank { instrument.symbol }
        val granularity = when (timeframe) {
            "15M" -> 900
            "1H" -> 3600
            "4H" -> 21600
            "1D" -> 86400
            else -> 3600
        }

        val tickerReq = Request.Builder()
            .url("https://api.exchange.coinbase.com/products/$product/ticker")
            .header("User-Agent", "JAX-Omniverse/1.0")
            .build()
        val tickerJson = client.newCall(tickerReq).execute().use { resp ->
            if (!resp.isSuccessful) error("Coinbase ticker HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty())
        }

        val bid = tickerJson.optString("bid", "0").toDoubleOrNull() ?: 0.0
        val ask = tickerJson.optString("ask", "0").toDoubleOrNull() ?: 0.0
        val last = tickerJson.optString("price", "0").toDoubleOrNull() ?: 0.0
        val vol = tickerJson.optString("volume", "0").toDoubleOrNull() ?: 0.0

        val candlesReq = Request.Builder()
            .url("https://api.exchange.coinbase.com/products/$product/candles?granularity=$granularity")
            .header("User-Agent", "JAX-Omniverse/1.0")
            .build()
        val candlesArr = client.newCall(candlesReq).execute().use { resp ->
            if (!resp.isSuccessful) error("Coinbase candles HTTP ${resp.code}")
            JSONArray(resp.body?.string().orEmpty())
        }

        val candles = mutableListOf<Candle>()
        val limit = minOf(120, candlesArr.length())
        for (i in 0 until limit) {
            val row = candlesArr.getJSONArray(i)
            candles += Candle(
                timestampEpochSec = row.getLong(0),
                low = row.getDouble(1),
                high = row.getDouble(2),
                open = row.getDouble(3),
                close = row.getDouble(4),
                volume = row.getDouble(5)
            )
        }
        candles.sortBy { it.timestampEpochSec }

        val prevClose = candles.dropLast(1).takeLast(24).firstOrNull()?.close ?: candles.firstOrNull()?.open ?: last
        val changePct = if (prevClose > 0.0) ((last - prevClose) / prevClose) * 100.0 else 0.0
        val high24 = candles.takeLast(24).maxOfOrNull { it.high } ?: last
        val low24 = candles.takeLast(24).minOfOrNull { it.low } ?: last
        val spread = if (ask > bid && bid > 0.0) ask - bid else 0.0
        val spreadPct = if (last > 0.0) (spread / last) * 100.0 else 0.0

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = bid,
                ask = ask,
                lastPrice = last,
                change24hPct = changePct,
                high24h = high24,
                low24h = low24,
                volume24h = vol,
                spread = spread,
                spreadPct = spreadPct,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE
            ),
            candles = candles
        )
    }

    private fun fetchKraken(
        instrument: WatchlistInstrument,
        timeframe: String,
        startMs: Long
    ): MarketFeedBundle {
        val pair = instrument.providerSymbol.ifBlank { "PAXGUSD" }
        val interval = when (timeframe) {
            "15M" -> 15
            "1H" -> 60
            "4H" -> 240
            "1D" -> 1440
            else -> 60
        }

        val tickerReq = Request.Builder()
            .url("https://api.kraken.com/0/public/Ticker?pair=$pair")
            .build()
        val tickerRoot = client.newCall(tickerReq).execute().use { resp ->
            if (!resp.isSuccessful) error("Kraken ticker HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty())
        }
        val resultObj = tickerRoot.optJSONObject("result") ?: error("Kraken ticker missing result")
        val firstKey = resultObj.keys().next()
        val tObj = resultObj.getJSONObject(firstKey)
        val ask = tObj.getJSONArray("a").getString(0).toDoubleOrNull() ?: 0.0
        val bid = tObj.getJSONArray("b").getString(0).toDoubleOrNull() ?: 0.0
        val last = tObj.getJSONArray("c").getString(0).toDoubleOrNull() ?: 0.0
        val vol = tObj.getJSONArray("v").getString(1).toDoubleOrNull() ?: 0.0
        val high24 = tObj.getJSONArray("h").getString(1).toDoubleOrNull() ?: last
        val low24 = tObj.getJSONArray("l").getString(1).toDoubleOrNull() ?: last
        val open24 = tObj.optString("o", "0").toDoubleOrNull() ?: last

        val ohlcReq = Request.Builder()
            .url("https://api.kraken.com/0/public/OHLC?pair=$pair&interval=$interval")
            .build()
        val ohlcRoot = client.newCall(ohlcReq).execute().use { resp ->
            if (!resp.isSuccessful) error("Kraken OHLC HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty())
        }
        val ohlcRes = ohlcRoot.optJSONObject("result") ?: error("Kraken OHLC missing result")
        val ohlcKey = ohlcRes.keys().asSequence().firstOrNull { it != "last" } ?: error("No OHLC series")
        val arr = ohlcRes.getJSONArray(ohlcKey)
        val candles = mutableListOf<Candle>()
        val startIdx = maxOf(0, arr.length() - 120)
        for (i in startIdx until arr.length()) {
            val row = arr.getJSONArray(i)
            candles += Candle(
                timestampEpochSec = row.getLong(0),
                open = row.getString(1).toDouble(),
                high = row.getString(2).toDouble(),
                low = row.getString(3).toDouble(),
                close = row.getString(4).toDouble(),
                volume = row.getString(6).toDouble()
            )
        }

        val spread = if (ask > bid && bid > 0.0) ask - bid else 0.0
        val spreadPct = if (last > 0.0) (spread / last) * 100.0 else 0.0
        val changePct = if (open24 > 0.0) ((last - open24) / open24) * 100.0 else 0.0

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = bid,
                ask = ask,
                lastPrice = last,
                change24hPct = changePct,
                high24h = high24,
                low24h = low24,
                volume24h = vol,
                spread = spread,
                spreadPct = spreadPct,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE
            ),
            candles = candles
        )
    }

    private fun fetchFrankfurterFx(
        instrument: WatchlistInstrument,
        startMs: Long
    ): MarketFeedBundle {
        val isUsdBase = instrument.symbol.startsWith("USD-")
        val quoteCode = instrument.providerSymbol.ifBlank { "EUR" }
        val base = if (isUsdBase) "USD" else quoteCode
        val target = if (isUsdBase) quoteCode else "USD"

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, -110)
        }
        val rangeUrl = "https://api.frankfurter.app/${fmt.format(startCal.time)}..${fmt.format(endCal.time)}?from=$base&to=$target"
        val req = Request.Builder().url(rangeUrl).build()
        val json = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Frankfurter ECB HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty())
        }
        val ratesObj = json.getJSONObject("rates")
        val dates = ratesObj.keys().asSequence().toList().sorted()
        val candles = mutableListOf<Candle>()
        var prevClose: Double? = null

        dates.forEach { dateStr ->
            val dayRate = ratesObj.getJSONObject(dateStr).optDouble(target, 0.0)
            if (dayRate > 0.0) {
                val epochSec = (fmt.parse(dateStr)?.time ?: 0L) / 1000L
                val open = prevClose ?: dayRate
                val high = maxOf(open, dayRate)
                val low = minOf(open, dayRate)
                candles += Candle(epochSec, open, high, low, dayRate, 0.0)
                prevClose = dayRate
            }
        }
        val last = candles.lastOrNull()?.close ?: 0.0
        val prev = candles.dropLast(1).lastOrNull()?.close ?: last
        val changePct = if (prev > 0.0) ((last - prev) / prev) * 100.0 else 0.0
        val high24 = candles.takeLast(5).maxOfOrNull { it.high } ?: last
        val low24 = candles.takeLast(5).minOfOrNull { it.low } ?: last

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = last,
                ask = last,
                lastPrice = last,
                change24hPct = changePct,
                high24h = high24,
                low24h = low24,
                volume24h = 0.0,
                spread = 0.0,
                spreadPct = 0.0,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE
            ),
            candles = candles
        )
    }

    private fun fetchTwelveData(
        instrument: WatchlistInstrument,
        timeframe: String,
        startMs: Long
    ): MarketFeedBundle {
        val apiKey = secretVault.resolveProviderSecret("twelvedata")
            ?: error("TWELVEDATA_API_KEY not configured (Secrets panel or API Vault)")
        val interval = when (timeframe) {
            "15M" -> "15min"
            "1H" -> "1h"
            "4H" -> "4h"
            "1D" -> "1day"
            else -> "1h"
        }
        val sym = instrument.providerSymbol.ifBlank { instrument.symbol.replace("-", "/") }
        val url = "https://api.twelvedata.com/time_series?symbol=${enc(sym)}&interval=$interval&outputsize=100&apikey=${enc(apiKey)}"
        val json = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("TwelveData HTTP ${resp.code}")
            JSONObject(resp.body?.string().orEmpty())
        }
        if (json.optString("status") == "error") {
            error(json.optString("message", "TwelveData error"))
        }
        val values = json.getJSONArray("values")
        val candles = mutableListOf<Candle>()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (i in 0 until values.length()) {
            val row = values.getJSONObject(i)
            val dtStr = row.getString("datetime")
            val epoch = ((sdf.parse(dtStr)?.time ?: (System.currentTimeMillis() - i * 3600_000L)) / 1000L)
            candles += Candle(
                timestampEpochSec = epoch,
                open = row.getString("open").toDouble(),
                high = row.getString("high").toDouble(),
                low = row.getString("low").toDouble(),
                close = row.getString("close").toDouble(),
                volume = row.optString("volume", "0").toDoubleOrNull() ?: 0.0
            )
        }
        candles.sortBy { it.timestampEpochSec }
        val last = candles.lastOrNull()?.close ?: 0.0
        val prev = candles.dropLast(1).lastOrNull()?.close ?: last
        val changePct = if (prev > 0.0) ((last - prev) / prev) * 100.0 else 0.0

        return MarketFeedBundle(
            quote = MarketQuote(
                symbol = instrument.symbol,
                displayName = instrument.displayName,
                providerId = instrument.providerId,
                bid = last,
                ask = last,
                lastPrice = last,
                change24hPct = changePct,
                high24h = candles.takeLast(24).maxOfOrNull { it.high } ?: last,
                low24h = candles.takeLast(24).minOfOrNull { it.low } ?: last,
                volume24h = candles.takeLast(24).sumOf { it.volume },
                spread = 0.0,
                spreadPct = 0.0,
                fetchedAtEpochMs = System.currentTimeMillis(),
                latencyMs = System.currentTimeMillis() - startMs,
                health = ConnectionHealth.LIVE
            ),
            candles = candles
        )
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val YAHOO_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) JAXOmniverse/1.0"
    }
}
