const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";
const ADMIN_EMAIL = "phonedata9955@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate() {
  return new Date(Date.now() - 1000);
}

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

test("Unauthenticated user cannot read public_profiles or conversations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("public_profiles").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("conversations").get());
  await assertFails(unauthDb.collection("statuses").get());
});

test("User can create own public_profile and private account, but cannot read another user's private PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      uid: ALICE_UID,
      rikzenId: "RIKZEN-ALICE1",
      rikzenIdLower: "rikzen-alice1",
      displayName: "Alice",
      displayNameLower: "alice",
      bio: "Hello Rikzen",
      isDiscoverableByName: true,
      isDiscoverableByPhone: false,
      isOnline: true,
      showOnlineStatus: true,
      showLastSeen: true,
      photoVisibility: "everyone",
      bioVisibility: "everyone",
      whoCanRequest: "everyone",
      statusSuspended: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      uid: ALICE_UID,
      email: "alice@example.com",
      phoneNumber: "+15551234567",
      emailVisible: false,
      readReceiptsEnabled: true,
      blockedUids: [],
      linkedDevices: ["Pixel 8"],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can read Alice's public profile
  await assertSucceeds(bobDb.collection("public_profiles").doc(ALICE_UID).get());
  // Bob CANNOT read Alice's private PII
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get());
  // Bob CANNOT escalate statusSuspended on Alice or himself
  await assertFails(
    bobDb.collection("public_profiles").doc(BOB_UID).set({
      uid: BOB_UID,
      rikzenId: "RIKZEN-BOB111",
      displayName: "Bob",
      statusSuspended: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("RIKZEN ID lookup allows exact get() but forbids scraping via list()", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("rikzen_ids").doc("RIKZEN-RK7M92").set({
      uid: ALICE_UID,
      rikzenId: "RIKZEN-RK7M92",
      createdAt: now,
    })
  );

  // Exact lookup works for Bob
  await assertSucceeds(bobDb.collection("rikzen_ids").doc("RIKZEN-RK7M92").get());
  // Blanket list is denied
  await assertFails(bobDb.collection("rikzen_ids").get());
});

test("Friend request prevents self-request, enforces participant isolation, and locks terminal state", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  // Self-request fails
  await assertFails(
    aliceDb.collection("friend_requests").doc(`${ALICE_UID}_${ALICE_UID}`).set({
      fromUid: ALICE_UID,
      toUid: ALICE_UID,
      status: "pending",
      participantUids: [ALICE_UID, ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid request Alice -> Bob
  const reqId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("friend_requests").doc(reqId).set({
      fromUid: ALICE_UID,
      toUid: BOB_UID,
      fromName: "Alice",
      fromRikzenId: "RIKZEN-ALICE1",
      toName: "Bob",
      toRikzenId: "RIKZEN-BOB222",
      status: "pending",
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Charlie cannot read Alice->Bob request
  await assertFails(charlieDb.collection("friend_requests").doc(reqId).get());

  // Bob accepts
  await assertSucceeds(
    bobDb.collection("friend_requests").doc(reqId).update({
      status: "accepted",
      updatedAt: pastDate(),
    })
  );

  // Terminal state lock: Bob cannot flip back to pending
  await assertFails(
    bobDb.collection("friend_requests").doc(reqId).update({
      status: "pending",
      updatedAt: pastDate(),
    })
  );
});

test("Conversations and messages isolate non-participants", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  const convId = "dm_alice_bob";
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      type: "dm",
      title: "",
      participantUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      lastMessageText: "",
      lastMessageSenderId: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg1").set({
      conversationId: convId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Welcome to Rikzen Chat!",
      messageType: "text",
      participantUids: [ALICE_UID, BOB_UID],
      deliveredTo: [ALICE_UID],
      readBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can read and mark readBy
  await assertSucceeds(bobDb.collection("conversations").doc(convId).collection("messages").doc("msg1").get());
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg1").update({
      deliveredTo: [ALICE_UID, BOB_UID],
      readBy: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(),
    })
  );

  // Charlie cannot read or write
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
  await assertFails(charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg1").get());
});

test("Email spoofing test: unverified admin email cannot access admin collections", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofedAdminDb.collection("reports").get());

  const realAdminDb = testEnv.authenticatedContext("real_admin_uid", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(realAdminDb.collection("reports").get());
});
