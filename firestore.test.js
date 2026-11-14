const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const pastTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_operator_1";
const BOB_UID = "bob_operator_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated caller cannot read or write user profile or instruments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("instruments").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("audit_logs").get()
  );
});

test("Authenticated user can create valid workspace profile but cannot escalate role", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      displayName: "Alice Quant",
      role: "analyst",
      executionMode: "ANALYSIS_ONLY",
      emergencyStop: false,
      accountBalance: 100000,
      riskPerTradePct: 1.0,
      maxDailyLossPct: 4.0,
      maxConcurrentPositions: 3,
      preferredInstruments: "BTC-USD,XAUUSD",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Role escalation must fail
  await assertFails(
    profileRef.update({
      role: "admin",
      updatedAt: pastTimestamp(),
    })
  );

  // Shadow ghost field must fail
  await assertFails(
    profileRef.update({
      isVerified: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or mutate Alice's instruments or orders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("instruments")
      .doc("btc_usd")
      .set({
        userId: ALICE_UID,
        symbol: "BTC-USD",
        displayName: "Bitcoin / USD",
        assetClass: "CRYPTO",
        providerId: "coinbase_public",
        providerSymbol: "BTC-USD",
        pipSize: 1.0,
        contractSize: 1.0,
        enabled: true,
        createdAt: pastTimestamp(),
        updatedAt: pastTimestamp(),
      })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("instruments")
      .doc("btc_usd")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("instruments")
      .get()
  );
});

test("Order terminal state lock & audit log immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const orderRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("orders")
    .doc("order_1");

  await assertSucceeds(
    orderRef.set({
      userId: ALICE_UID,
      instrument: "BTC-USD",
      direction: "BUY",
      volumeLots: 0.25,
      entryPrice: 91500,
      stopLoss: 90200,
      takeProfit: 94100,
      riskPercent: 1.0,
      executionMode: "MANUAL_APPROVAL",
      status: "PENDING_APPROVAL",
      riskCheckPassed: true,
      rejectionReason: "",
      requestNonce: "nonce_abc_123456",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(
    orderRef.update({
      status: "TRANSMITTED",
      updatedAt: pastTimestamp(),
    })
  );

  // Terminal state cannot be reverted
  await assertFails(
    orderRef.update({
      status: "PENDING_APPROVAL",
      updatedAt: pastTimestamp(),
    })
  );

  const logRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("audit_logs")
    .doc("log_1");

  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      action: "ORDER_APPROVED",
      category: "EXECUTION",
      details: "Manual approval verified by operator",
      severity: "INFO",
      createdAt: pastTimestamp(),
    })
  );

  // Audit logs are immutable
  await assertFails(
    logRef.update({
      details: "Tampered",
    })
  );
  await assertFails(logRef.delete());
});
