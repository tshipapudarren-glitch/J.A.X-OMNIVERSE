package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM vault for storing third-party API keys,
 * webhook secrets, and MT5 bridge tokens strictly on-device. Never persisted to Firestore or logs.
 * Also resolves build-time Secrets Gradle Plugin keys from BuildConfig when configured in AI Studio Secrets.
 */
class SecretVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun saveSecret(connectionKey: String, plaintext: String) {
        if (plaintext.isBlank()) {
            deleteSecret(connectionKey)
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(connectionKey, encoded).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt secret for connection", e)
        }
    }

    fun getSecret(connectionKey: String): String? {
        val encoded = prefs.getString(connectionKey, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= IV_SIZE_BYTES) return null
            val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
            val ciphertext = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt secret for connection", e)
            null
        }
    }

    fun hasSecret(connectionKey: String): Boolean = !resolveProviderSecret(connectionKey).isNullOrBlank()

    fun resolveProviderSecret(providerId: String): String? {
        val localSecret = getSecret(providerId)?.takeIf { it.isNotBlank() }
        if (localSecret != null) return localSecret

        val buildSecret = when (providerId) {
            "finnhub" -> BuildConfig.FINNHUB_API_KEY
            "fmp" -> BuildConfig.FMP_API_KEY
            "alpha_vantage" -> BuildConfig.ALPHA_VANTAGE_API_KEY
            "fred" -> BuildConfig.FRED_API_KEY
            "yahoo" -> BuildConfig.YAHOO_API_KEY
            "binance" -> BuildConfig.BINANCE_API_KEY
            "binance_secret" -> BuildConfig.BINANCE_API_SECRET
            "twelvedata" -> BuildConfig.TWELVEDATA_API_KEY
            else -> ""
        }
        return sanitizeBuildSecret(buildSecret)
    }

    fun getSecretSourceLabel(providerId: String): String {
        if (!prefs.getString(providerId, null).isNullOrBlank()) {
            return "Android Keystore Vault"
        }
        val fromBuild = resolveProviderSecret(providerId)
        if (!fromBuild.isNullOrBlank()) {
            return "AI Studio Secrets (BuildConfig)"
        }
        return when (providerId) {
            "coinbase_public", "kraken_public", "frankfurter_ecb", "institutional_rss", "yahoo", "binance" ->
                "Public Feed (Optional Key)"
            "firebase_ai_logic" -> "App Check Attested"
            else -> "Not Configured"
        }
    }

    fun getConfiguredProviderIds(): Set<String> {
        val keyedIds = listOf(
            "finnhub", "fmp", "alpha_vantage", "fred",
            "yahoo", "binance", "binance_secret", "twelvedata", "mt5_bridge"
        )
        val result = mutableSetOf("coinbase_public", "kraken_public", "frankfurter_ecb", "institutional_rss", "firebase_ai_logic", "yahoo", "binance")
        keyedIds.forEach { id ->
            if (!resolveProviderSecret(id).isNullOrBlank()) {
                result += id
            }
        }
        return result
    }

    fun deleteSecret(connectionKey: String) {
        prefs.edit().remove(connectionKey).apply()
    }

    private fun sanitizeBuildSecret(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        val upper = trimmed.uppercase()
        if (upper.startsWith("YOUR_") || upper.startsWith("OPTIONAL_") || upper.startsWith("MY_") || upper.contains("PLACEHOLDER")) {
            return null
        }
        return trimmed
    }

    private companion object {
        const val TAG = "SecretVault"
        const val PREFS_NAME = "jax_secure_vault"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "jax_omniverse_vault_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
