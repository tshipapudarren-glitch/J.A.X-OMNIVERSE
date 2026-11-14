package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.ExecutionOrderRecord
import com.example.domain.model.TradingSetup
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class JaxRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureProfileAndAddInstrument_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = JaxFirestoreRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureProfileInitialized("Alice Operator")
        }
        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProfile(uid).first { it != null }
        }
        assertNotNull(profile)
        assertEquals("ANALYSIS_ONLY", profile!!.executionMode)

        val instruments = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeInstruments(uid).first { it.isNotEmpty() }
        }
        assertTrue(instruments.any { it.symbol == "BTC-USD" })
    }

    @Test
    fun saveSetupAndOrder_authenticatedOwner_persistsAndStreams() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = JaxFirestoreRepository(firestore)

        val setupId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveSetup(
                TradingSetup(
                    userId = uid,
                    instrument = "BTC-USD",
                    direction = "BULLISH",
                    setupType = "Liquidity Sweep & Recovery",
                    status = "DEVELOPING",
                    timeframe = "1H",
                    entryZoneLow = 90000.0,
                    entryZoneHigh = 90400.0,
                    stopLoss = 88900.0,
                    tp1 = 92100.0,
                    tp2 = 93500.0,
                    tp3 = 95000.0,
                    rr1 = 1.54,
                    score = 78L,
                    evidenceSummary = "EMA20 > EMA50 | Bullish SSL sweep",
                    confirmationConditions = "H1 close > 90400",
                    invalidationConditions = "H1 close < 88900",
                    dataQualityStatus = "LIVE_COINBASE",
                    version = 1L
                )
            )
        }
        assertTrue(setupId.isNotBlank())

        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createOrder(
                ExecutionOrderRecord(
                    userId = uid,
                    instrument = "BTC-USD",
                    direction = "BUY",
                    volumeLots = 0.1,
                    entryPrice = 90400.0,
                    stopLoss = 88900.0,
                    takeProfit = 92100.0,
                    riskPercent = 1.0,
                    executionMode = "MANUAL_APPROVAL",
                    status = "PENDING_APPROVAL",
                    riskCheckPassed = true,
                    requestNonce = UUID.randomUUID().toString()
                )
            )
        }
        assertTrue(orderId.isNotBlank())
    }

    @Test
    fun observeInstruments_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = JaxFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureProfileInitialized("Alice Operator")
        }

        signInTestUser(BOB_EMAIL)
        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeInstruments(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeSetups_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = JaxFirestoreRepository(firestore)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeSetups("some_uid_123").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_jax@test.com"
        const val BOB_EMAIL = "bob_jax@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
