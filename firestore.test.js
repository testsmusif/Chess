const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ROOM_CODE = "ARENA1";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

// --- DIRTY DOZEN & SECURITY BOUNDS TESTS ---

test("1. Unauthenticated user cannot read users or rooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").doc(ROOM_CODE).get());
});

test("2. Authenticated user can create and read own profile, but cannot read another user's profile", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "GrandmasterAlice",
      kills: 0,
      deaths: 0,
      matchesPlayed: 0,
      wins: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("3. Shadow update with ghost field on user profile is rejected", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "Alice",
      kills: 0,
      deaths: 0,
      matchesPlayed: 0,
      wins: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      nickname: "AliceHacked",
      isAdmin: true,
      updatedAt: new Date(),
    })
  );
});

test("4. Host can create valid room and non-host cannot spoof hostId", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Spoofed hostId fails
  await assertFails(
    aliceDb.collection("rooms").doc(ROOM_CODE).set({
      roomCode: ROOM_CODE,
      hostId: BOB_UID,
      hostName: "Bob",
      mode: "tdm",
      status: "waiting",
      isPublic: true,
      scoreLimit: 15,
      whiteScore: 0,
      blackScore: 0,
      playerCount: 1,
      winner: "",
      arenaTheme: "cyber_obsidian",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid creation succeeds
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).set({
      roomCode: ROOM_CODE,
      hostId: ALICE_UID,
      hostName: "Alice",
      mode: "tdm",
      status: "waiting",
      isPublic: true,
      scoreLimit: 15,
      whiteScore: 0,
      blackScore: 0,
      playerCount: 1,
      winner: "",
      arenaTheme: "cyber_obsidian",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("5. Client Query Alignment: listing public rooms requires isPublic == true filter", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc(ROOM_CODE).set({
      roomCode: ROOM_CODE,
      hostId: ALICE_UID,
      hostName: "Alice",
      mode: "tdm",
      status: "waiting",
      isPublic: true,
      scoreLimit: 15,
      whiteScore: 0,
      blackScore: 0,
      playerCount: 1,
      winner: "",
      arenaTheme: "cyber_obsidian",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("rooms").where("isPublic", "==", true).get()
  );
});

test("6. RoomPlayer creation requires parent room to exist and enforces arena coordinate & combat bounds", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Fails when parent room does not exist
  await assertFails(
    aliceDb.collection("rooms").doc("NOROOM").collection("players").doc(ALICE_UID).set({
      userId: ALICE_UID,
      roomCode: "NOROOM",
      nickname: "Alice",
      team: "WHITE",
      x: 0.0,
      y: 0.0,
      z: -10.0,
      yaw: 0.0,
      pitch: 0.0,
      health: 100,
      ammo: 30,
      kills: 0,
      deaths: 0,
      weapon: "PULSE_RIFLE",
      lastShotSeq: 0,
      lastHitBy: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create parent room
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc(ROOM_CODE).set({
      roomCode: ROOM_CODE,
      hostId: ALICE_UID,
      hostName: "Alice",
      mode: "tdm",
      status: "playing",
      isPublic: true,
      scoreLimit: 15,
      whiteScore: 0,
      blackScore: 0,
      playerCount: 2,
      winner: "",
      arenaTheme: "cyber_obsidian",
      createdAt: now,
      updatedAt: now,
    });
  });

  // Valid player creation succeeds
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(ALICE_UID).set({
      userId: ALICE_UID,
      roomCode: ROOM_CODE,
      nickname: "Alice",
      team: "WHITE",
      x: 2.0,
      y: 0.0,
      z: -8.0,
      yaw: 45.0,
      pitch: 0.0,
      health: 100,
      ammo: 30,
      kills: 0,
      deaths: 0,
      weapon: "PULSE_RIFLE",
      lastShotSeq: 0,
      lastHitBy: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Out-of-bounds teleport hack by owner fails
  await assertFails(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(ALICE_UID).update({
      x: 500.0,
      updatedAt: new Date(),
    })
  );

  // Opponent (Bob) cannot tamper with Alice's position
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(ALICE_UID).update({
      x: 0.0,
      updatedAt: new Date(),
    })
  );

  // Opponent (Bob) CAN register a valid combat hit decreasing Alice's health
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(ALICE_UID).update({
      health: 75,
      deaths: 0,
      lastHitBy: BOB_UID,
      updatedAt: new Date(),
    })
  );

  // Querying players in room with roomCode filter succeeds
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("players").where("roomCode", "==", ROOM_CODE).get()
  );
});
