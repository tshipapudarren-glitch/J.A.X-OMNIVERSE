package com.example.data.network

import android.util.Xml
import com.example.domain.model.CentralBankFeedStatus
import com.example.domain.model.EconomicCalendarItem
import com.example.domain.model.FredMacroIndicator
import com.example.domain.model.NewsArticleItem
import com.example.security.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class NewsFetchResult(
    val articles: List<NewsArticleItem>,
    val sourceErrors: List<String>,
    val latencyMs: Long
)

data class CalendarMacroFetchResult(
    val calendarEvents: List<EconomicCalendarItem>,
    val macroIndicators: List<FredMacroIndicator>,
    val sourceErrors: List<String>,
    val latencyMs: Long
)

/**
 * Real institutional News, Economic Calendar & Macro Ingestion Service.
 * Fetches live Federal Reserve, ECB, and CoinDesk RSS feeds plus keyed Finnhub News/Calendar,
 * Alpha Vantage News & Sentiment, FMP Economic Calendar, and FRED Macro Series.
 * NEVER fabricates or invents items when unreachable or unconfigured.
 */
class NewsIngestionService(
    private val secretVault: SecretVault,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {

    private data class FeedSource(val name: String, val url: String, val bankCode: String? = null)

    private val defaultFeeds = listOf(
        FeedSource("Federal Reserve", "https://www.federalreserve.gov/feeds/press_all.xml", "FED"),
        FeedSource("European Central Bank", "https://www.ecb.europa.eu/rss/press.html", "ECB"),
        FeedSource("Bank of England", "https://www.bankofengland.co.uk/rss/news", "BOE"),
        FeedSource("Bank of Japan", "https://www.boj.or.jp/en/rss/whatsnew.xml", "BOJ"),
        FeedSource("Swiss National Bank", "https://www.snb.ch/en/mmr/rss/rss_all/xml", "SNB"),
        FeedSource("CoinDesk", "https://www.coindesk.com/arc/outboundfeeds/rss/", null)
    )

    suspend fun fetchLiveNews(): NewsFetchResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val allArticles = mutableListOf<NewsArticleItem>()
        val errors = mutableListOf<String>()

        defaultFeeds.forEach { source ->
            try {
                val req = Request.Builder()
                    .url(source.url)
                    .header("User-Agent", "JAX-Omniverse/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        errors += "${source.name}: HTTP ${resp.code}"
                        return@use
                    }
                    val body = resp.body?.string().orEmpty()
                    allArticles += parseRssXml(body, source.name).take(6)
                }
            } catch (e: Exception) {
                errors += "${source.name}: ${e.localizedMessage ?: "Unreachable"}"
            }
        }

        // Optional keyed Finnhub Market News
        secretVault.resolveProviderSecret("finnhub")?.let { fhKey ->
            try {
                val req = Request.Builder()
                    .url("https://finnhub.io/api/v1/news?category=general&token=${enc(fhKey)}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        errors += "Finnhub News: HTTP ${resp.code}"
                    } else if (body.trimStart().startsWith("[")) {
                        val arr = JSONArray(body)
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        for (i in 0 until minOf(6, arr.length())) {
                            val obj = arr.getJSONObject(i)
                            val headline = obj.optString("headline")
                            val summary = obj.optString("summary").take(260)
                            val dtSec = obj.optLong("datetime", 0L)
                            val pubStr = if (dtSec > 0L) sdf.format(Date(dtSec * 1000L)) else "Recent"
                            val combined = "$headline $summary".lowercase()
                            allArticles += NewsArticleItem(
                                id = "finnhub_${obj.optLong("id", headline.hashCode().toLong())}",
                                title = headline,
                                summary = summary.ifBlank { "Finnhub Market Release" },
                                sourceName = "Finnhub (${obj.optString("source", "Wire")})",
                                articleUrl = obj.optString("url"),
                                publishedAt = pubStr,
                                retrievedAtEpochMs = System.currentTimeMillis(),
                                affectedInstruments = mapAffectedInstruments(combined, "Finnhub"),
                                sentimentLabel = classifySentiment(combined),
                                verificationStatus = "FINNHUB_VERIFIED_API"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                errors += "Finnhub News: ${e.localizedMessage ?: "Error"}"
            }
        }

        // Optional keyed Alpha Vantage News & Sentiment
        secretVault.resolveProviderSecret("alpha_vantage")?.let { avKey ->
            try {
                val req = Request.Builder()
                    .url("https://www.alphavantage.co/query?function=NEWS_SENTIMENT&limit=8&apikey=${enc(avKey)}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        errors += "Alpha Vantage News: HTTP ${resp.code}"
                    } else {
                        val root = JSONObject(body)
                        val feed = root.optJSONArray("feed")
                        if (feed == null) {
                            val note = root.optString("Note").ifBlank { root.optString("Information") }
                            if (note.isNotBlank()) errors += "Alpha Vantage News: $note"
                        } else {
                            for (i in 0 until minOf(6, feed.length())) {
                                val item = feed.getJSONObject(i)
                                val title = item.optString("title")
                                val summary = item.optString("summary").take(260)
                                val avSent = item.optString("overall_sentiment_label", "Neutral").uppercase()
                                val tickers = mutableListOf<String>()
                                val tArr = item.optJSONArray("ticker_sentiment")
                                if (tArr != null) {
                                    for (j in 0 until minOf(4, tArr.length())) {
                                        tickers += tArr.getJSONObject(j).optString("ticker")
                                    }
                                }
                                allArticles += NewsArticleItem(
                                    id = "av_${title.hashCode()}",
                                    title = title,
                                    summary = summary.ifBlank { "Alpha Vantage Market Sentiment" },
                                    sourceName = "Alpha Vantage (${item.optString("source", "Feed")})",
                                    articleUrl = item.optString("url"),
                                    publishedAt = item.optString("time_published"),
                                    retrievedAtEpochMs = System.currentTimeMillis(),
                                    affectedInstruments = if (tickers.isNotEmpty()) tickers else mapAffectedInstruments("$title $summary".lowercase(), "AlphaVantage"),
                                    sentimentLabel = avSent,
                                    verificationStatus = "ALPHA_VANTAGE_SENTIMENT_API"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                errors += "Alpha Vantage News: ${e.localizedMessage ?: "Error"}"
            }
        }

        NewsFetchResult(
            articles = allArticles,
            sourceErrors = errors,
            latencyMs = System.currentTimeMillis() - startMs
        )
    }

    suspend fun fetchEconomicCalendarAndMacro(): CalendarMacroFetchResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val events = mutableListOf<EconomicCalendarItem>()
        val macroList = mutableListOf<FredMacroIndicator>()
        val errors = mutableListOf<String>()

        val fhKey = secretVault.resolveProviderSecret("finnhub")
        val fmpKey = secretVault.resolveProviderSecret("fmp")
        val fredKey = secretVault.resolveProviderSecret("fred")

        if (fhKey != null) {
            try {
                val req = Request.Builder()
                    .url("https://finnhub.io/api/v1/calendar/economic?token=${enc(fhKey)}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        errors += "Finnhub Calendar: HTTP ${resp.code} (Calendar may require paid Finnhub plan)"
                    } else {
                        val arr = JSONObject(body).optJSONArray("economicCalendar") ?: JSONArray()
                        for (i in 0 until minOf(15, arr.length())) {
                            val obj = arr.getJSONObject(i)
                            events += EconomicCalendarItem(
                                id = "fh_cal_$i",
                                title = obj.optString("event", "Economic Release"),
                                currency = obj.optString("country", "USD"),
                                impact = obj.optString("impact", "medium").uppercase(),
                                eventTime = obj.optString("time"),
                                actual = obj.opt("actual")?.takeIf { it != JSONObject.NULL }?.toString() ?: "Pending",
                                forecast = obj.opt("estimate")?.takeIf { it != JSONObject.NULL }?.toString() ?: "--",
                                previous = obj.opt("prev")?.takeIf { it != JSONObject.NULL }?.toString() ?: "--",
                                revisedPrevious = "--",
                                sourceName = "Finnhub Calendar",
                                affectedInstruments = listOf("EUR-USD", "USD-JPY", "SPY")
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                errors += "Finnhub Calendar: ${e.localizedMessage}"
            }
        }

        if (fmpKey != null && events.isEmpty()) {
            try {
                val req = Request.Builder()
                    .url("https://financialmodelingprep.com/stable/economic-calendar?apikey=${enc(fmpKey)}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && body.trimStart().startsWith("[")) {
                        val arr = JSONArray(body)
                        for (i in 0 until minOf(15, arr.length())) {
                            val obj = arr.getJSONObject(i)
                            events += EconomicCalendarItem(
                                id = "fmp_cal_$i",
                                title = obj.optString("event", "Macro Event"),
                                currency = obj.optString("currency", obj.optString("country", "USD")),
                                impact = obj.optString("impact", "Medium").uppercase(),
                                eventTime = obj.optString("date"),
                                actual = obj.opt("actual")?.takeIf { it != JSONObject.NULL }?.toString() ?: "Pending",
                                forecast = obj.opt("estimate")?.takeIf { it != JSONObject.NULL }?.toString() ?: "--",
                                previous = obj.opt("previous")?.takeIf { it != JSONObject.NULL }?.toString() ?: "--",
                                revisedPrevious = "--",
                                sourceName = "FMP Economic Calendar",
                                affectedInstruments = listOf("EUR-USD", "USD-JPY", "PAXG-USD")
                            )
                        }
                    } else if (!resp.isSuccessful) {
                        errors += "FMP Calendar: HTTP ${resp.code}"
                    }
                }
            } catch (e: Exception) {
                errors += "FMP Calendar: ${e.localizedMessage}"
            }
        }

        if (fredKey != null) {
            val coreSeries = listOf(
                Triple("FEDFUNDS", "Fed Funds Effective Rate", "%"),
                Triple("DGS2", "US 2Y Treasury Yield", "%"),
                Triple("DGS5", "US 5Y Treasury Yield", "%"),
                Triple("DGS10", "US 10Y Treasury Yield", "%"),
                Triple("DGS30", "US 30Y Treasury Yield", "%"),
                Triple("T10Y2Y", "10Y-2Y Yield Curve Spread", "%"),
                Triple("UNRATE", "US Unemployment Rate", "%")
            )
            coreSeries.forEach { (seriesId, title, units) ->
                try {
                    val req = Request.Builder()
                        .url("https://api.stlouisfed.org/fred/series/observations?series_id=$seriesId&api_key=${enc(fredKey)}&file_type=json&sort_order=desc&limit=5")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            errors += "FRED $seriesId: HTTP ${resp.code}"
                            return@use
                        }
                        val obsArr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("observations")
                        if (obsArr != null) {
                            for (i in 0 until obsArr.length()) {
                                val row = obsArr.getJSONObject(i)
                                val v = row.optString("value", ".")
                                if (v != ".") {
                                    macroList += FredMacroIndicator(
                                        seriesId = seriesId,
                                        title = title,
                                        value = "$v$units",
                                        units = units,
                                        observationDate = row.optString("date")
                                    )
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors += "FRED $seriesId: ${e.localizedMessage}"
                }
            }
        }

        if (fhKey == null && fmpKey == null && fredKey == null) {
            errors += "Finnhub, FMP, and FRED API keys are not configured. Set them in AI Studio Secrets or API Connections."
        }

        CalendarMacroFetchResult(
            calendarEvents = events,
            macroIndicators = macroList,
            sourceErrors = errors,
            latencyMs = System.currentTimeMillis() - startMs
        )
    }

    private fun parseRssXml(xmlContent: String, sourceName: String): List<NewsArticleItem> {
        val items = mutableListOf<NewsArticleItem>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlContent))

        var inItem = false
        var title = ""
        var link = ""
        var pubDate = ""
        var description = ""
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.lowercase()
                    if (currentTag == "item" || currentTag == "entry") {
                        inItem = true
                        title = ""
                        link = ""
                        pubDate = ""
                        description = ""
                    } else if (inItem && currentTag == "link") {
                        val href = parser.getAttributeValue(null, "href")
                        if (!href.isNullOrBlank()) link = href
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text.orEmpty().trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "title" -> title += text
                                "link" -> if (link.isBlank()) link += text
                                "pubdate", "updated", "published", "dc:date" -> pubDate += text
                                "description", "summary" -> description += text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val endName = parser.name.lowercase()
                    if ((endName == "item" || endName == "entry") && inItem) {
                        inItem = false
                        if (title.isNotBlank()) {
                            val cleanSummary = description.replace(Regex("<[^>]*>"), " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .take(280)
                            val combined = "$title $cleanSummary".lowercase()
                            val affected = mapAffectedInstruments(combined, sourceName)
                            val sentiment = classifySentiment(combined)
                            items += NewsArticleItem(
                                id = "${sourceName}_${title.hashCode()}",
                                title = title.trim(),
                                summary = cleanSummary.ifBlank { "Official release from $sourceName" },
                                sourceName = sourceName,
                                articleUrl = link.trim(),
                                publishedAt = pubDate.trim().ifBlank { "Timestamp unavailable in feed" },
                                retrievedAtEpochMs = System.currentTimeMillis(),
                                affectedInstruments = affected,
                                sentimentLabel = sentiment,
                                verificationStatus = "PRIMARY_SOURCE_RSS"
                            )
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return items
    }

    fun buildCentralBankStatuses(
        articles: List<NewsArticleItem>,
        macroIndicators: List<FredMacroIndicator>
    ): List<CentralBankFeedStatus> {
        val fedFunds = macroIndicators.firstOrNull { it.seriesId == "FEDFUNDS" }?.value ?: "FRED N/A"
        val specs = listOf(
            Triple("FED", "Federal Reserve (FOMC)", "USD" to "Federal Reserve"),
            Triple("ECB", "European Central Bank (ECB)", "EUR" to "European Central Bank"),
            Triple("BOE", "Bank of England (BoE)", "GBP" to "Bank of England"),
            Triple("BOJ", "Bank of Japan (BoJ)", "JPY" to "Bank of Japan"),
            Triple("SNB", "Swiss National Bank (SNB)", "CHF" to "Swiss National Bank")
        )
        return specs.map { (code, fullName, curSource) ->
            val (currency, sourceKey) = curSource
            val matched = articles.firstOrNull { it.sourceName == sourceKey }
            val tone = when {
                matched == null -> "NO_FEED_DATA"
                "HAWKISH" in matched.sentimentLabel || "POSITIVE" in matched.sentimentLabel -> "HAWKISH"
                "CAUTIOUS" in matched.sentimentLabel || "RISK-OFF" in matched.sentimentLabel -> "DOVISH / CAUTIOUS"
                else -> "NEUTRAL"
            }
            CentralBankFeedStatus(
                bankCode = code,
                bankName = fullName,
                currency = currency,
                latestReleaseTitle = matched?.title ?: "No live RSS release synced yet — tap Sync Central Banks",
                latestReleaseDate = matched?.publishedAt ?: "--",
                inferredTone = tone,
                sourceUrl = matched?.articleUrl.orEmpty(),
                rateSeriesValue = if (code == "FED") fedFunds else "Track RSS/Calendar"
            )
        }
    }

    private fun mapAffectedInstruments(text: String, sourceName: String): List<String> {
        val list = mutableSetOf<String>()
        if ("bitcoin" in text || "btc" in text || "crypto" in text || sourceName == "CoinDesk") {
            list += "BTC-USD"
            list += "ETH-USD"
        }
        if ("federal reserve" in text || "fomc" in text || "dollar" in text || "rate" in text || "inflation" in text || sourceName == "Federal Reserve") {
            list += "PAXG-USD"
            list += "EUR-USD"
            list += "USD-JPY"
        }
        if ("ecb" in text || "euro" in text || sourceName == "European Central Bank") {
            list += "EUR-USD"
            list += "GBP-USD"
        }
        if ("bank of england" in text || "boe" in text || "sterling" in text || "mpc" in text || sourceName == "Bank of England") {
            list += "GBP-USD"
            list += "EUR-USD"
        }
        if ("bank of japan" in text || "boj" in text || "yen" in text || sourceName == "Bank of Japan") {
            list += "USD-JPY"
        }
        if ("swiss national bank" in text || "snb" in text || "franc" in text || sourceName == "Swiss National Bank") {
            list += "USD-CHF"
            list += "EUR-USD"
        }
        if ("gold" in text || "xau" in text || "bullion" in text) {
            list += "PAXG-USD"
        }
        if (list.isEmpty()) list += "MACRO"
        return list.toList()
    }

    private fun classifySentiment(text: String): String {
        val hawkishOrBullish = listOf("surge", "rally", "hike", "tighten", "growth", "approval", "inflow", "beat")
        val dovishOrBearish = listOf("cut", "drop", "risk", "warning", "sanction", "conflict", "decline", "miss", "halt")
        val bullHits = hawkishOrBullish.count { it in text }
        val bearHits = dovishOrBearish.count { it in text }
        return when {
            bullHits > bearHits -> "POSITIVE / HAWKISH"
            bearHits > bullHits -> "CAUTIOUS / RISK-OFF"
            else -> "NEUTRAL / FACTUAL"
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
