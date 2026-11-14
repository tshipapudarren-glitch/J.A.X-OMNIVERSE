package com.example.data.network

import com.example.domain.model.ExecutionOrderRecord
import com.example.domain.model.Mt5Position
import com.example.domain.model.Mt5TelemetrySnapshot
import com.example.security.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated MetaTrader 5 Bridge HTTP Client.
 * Uses HMAC-SHA256 request signing, unique request IDs (nonce), and timestamp replay protection.
 * Clearly reports DISCONNECTED when no bridge endpoint is configured or reachable.
 */
class Mt5BridgeClient(
    private val secretVault: SecretVault,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    suspend fun pollTelemetry(baseUrl: String): Mt5TelemetrySnapshot = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            return@withContext Mt5TelemetrySnapshot(
                connected = false,
                endpointUrl = "",
                maskedAccountId = "Not Connected",
                broker = "No Bridge Configured",
                server = "Disconnected",
                eaStatus = "OFFLINE",
                balance = 0.0,
                equity = 0.0,
                marginLevelPct = 0.0,
                lastHeartbeatEpochMs = 0L,
                lastError = "Configure your MT5 Bridge URL & HMAC secret in API Connections or MT5 Control Centre."
            )
        }

        try {
            val req = buildSignedRequest("GET", "$trimmed/api/v1/telemetry", "")
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext disconnectedSnapshot(trimmed, "MT5 Bridge HTTP ${resp.code}: ${resp.message}")
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val rawAcc = json.optString("accountId", "****")
                val masked = if (rawAcc.length > 4) "****${rawAcc.takeLast(4)}" else "****"
                val posArr = json.optJSONArray("positions")
                val positions = mutableListOf<Mt5Position>()
                if (posArr != null) {
                    for (i in 0 until posArr.length()) {
                        val p = posArr.getJSONObject(i)
                        positions += Mt5Position(
                            ticket = p.optLong("ticket"),
                            symbol = p.optString("symbol"),
                            type = p.optString("type"),
                            volume = p.optDouble("volume"),
                            openPrice = p.optDouble("openPrice"),
                            currentPrice = p.optDouble("currentPrice"),
                            sl = p.optDouble("sl"),
                            tp = p.optDouble("tp"),
                            profit = p.optDouble("profit")
                        )
                    }
                }
                Mt5TelemetrySnapshot(
                    connected = true,
                    endpointUrl = trimmed,
                    maskedAccountId = masked,
                    broker = json.optString("broker", "MT5 Broker"),
                    server = json.optString("server", "Live Server"),
                    eaStatus = json.optString("eaStatus", "HEARTBEAT_OK"),
                    balance = json.optDouble("balance", 0.0),
                    equity = json.optDouble("equity", 0.0),
                    marginLevelPct = json.optDouble("marginLevelPct", 0.0),
                    lastHeartbeatEpochMs = System.currentTimeMillis(),
                    lastError = null,
                    positions = positions
                )
            }
        } catch (e: Exception) {
            disconnectedSnapshot(trimmed, e.localizedMessage ?: "Bridge unreachable")
        }
    }

    suspend fun transmitOrder(baseUrl: String, order: ExecutionOrderRecord): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isBlank()) {
                return@withContext false to "MT5 Bridge URL is not configured."
            }
            val payload = JSONObject().apply {
                put("requestId", order.requestNonce)
                put("instrument", order.instrument)
                put("direction", order.direction)
                put("volumeLots", order.volumeLots)
                put("entryPrice", order.entryPrice)
                put("stopLoss", order.stopLoss)
                put("takeProfit", order.takeProfit)
                put("executionMode", order.executionMode)
            }.toString()

            try {
                val req = buildSignedRequest("POST", "$trimmed/api/v1/orders", payload)
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        true to "MT5 Ack: $body"
                    } else {
                        false to "MT5 Bridge rejected (${resp.code}): $body"
                    }
                }
            } catch (e: Exception) {
                false to "Transmission failed: ${e.localizedMessage ?: "Unreachable"}"
            }
        }

    private fun buildSignedRequest(method: String, url: String, body: String): Request {
        val nonce = UUID.randomUUID().toString()
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val secret = secretVault.getSecret("mt5_bridge").orEmpty()
        val signature = if (secret.isNotBlank()) {
            hmacSha256("$timestamp.$nonce.$body", secret)
        } else ""

        val builder = Request.Builder()
            .url(url)
            .header("X-JAX-Request-Id", nonce)
            .header("X-JAX-Timestamp", timestamp)
            .header("X-JAX-Signature", signature)

        if (method == "POST") {
            builder.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        } else {
            builder.get()
        }
        return builder.build()
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun disconnectedSnapshot(url: String, error: String) = Mt5TelemetrySnapshot(
        connected = false,
        endpointUrl = url,
        maskedAccountId = "****",
        broker = "Disconnected",
        server = "Offline",
        eaStatus = "DISCONNECTED",
        balance = 0.0,
        equity = 0.0,
        marginLevelPct = 0.0,
        lastHeartbeatEpochMs = 0L,
        lastError = error
    )
}
