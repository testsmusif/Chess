package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessArenaRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureUserProfile_and_crossUserIsolation_enforcesRules() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = ChessArenaRepository(firestore)

    val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.ensureUserProfile("GrandmasterAlice")
    }
    assertTrue(profileResult.isSuccess)
    assertEquals(aliceUid, profileResult.getOrThrow().userId)

    // Switch to Bob and verify Bob cannot read Alice's private user profile document
    signInTestUser(BOB_EMAIL)
    val bobRepo = ChessArenaRepository(firestore)
    val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getUserProfile(aliceUid)
    }
    assertTrue(crossReadResult.isFailure)
    val firestoreEx = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(firestoreEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
  }

  @Test
  fun createRoom_joinByCode_syncTelemetry_and_combatHit_workEndToEnd() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = ChessArenaRepository(firestore)
    val rawCode = "RM" + UUID.randomUUID().toString().replace("-", "").take(4).uppercase()

    // Alice creates room
    val codeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createRoom(
        roomCode = rawCode,
        hostName = "Alice",
        mode = "tdm",
        isPublic = true,
        scoreLimit = 15,
        arenaTheme = "cyber_obsidian",
        weapon = "PULSE_RIFLE"
      )
    }
    assertTrue("createRoom failed: ${codeResult.exceptionOrNull()}", codeResult.isSuccess)
    val code = codeResult.getOrThrow()

    // Observe public rooms
    val publicRooms = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observePublicRooms().first { list -> list.any { it.roomCode == code } }
    }
    assertTrue(publicRooms.any { it.roomCode == code })

    // Bob joins room by code
    signInTestUser(BOB_EMAIL)
    val joinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.joinRoomByCode(code, "Bob", "RAIL_PISTOL")
    }
    assertTrue("joinRoomByCode failed: ${joinResult.exceptionOrNull()}", joinResult.isSuccess)

    // Bob syncs his own telemetry
    val syncResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.syncPlayerTelemetry(
        roomCode = code,
        x = 3.5,
        y = 0.0,
        z = 10.0,
        yaw = 180.0,
        pitch = -5.0,
        health = 100,
        ammo = 12,
        kills = 0,
        deaths = 0,
        weapon = "RAIL_PISTOL",
        lastShotSeq = 1L
      )
    }
    assertTrue("syncPlayerTelemetry failed: ${syncResult.exceptionOrNull()}", syncResult.isSuccess)

    // Bob registers a combat hit on Alice
    val hitResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.applyDamageToOpponent(
        roomCode = code,
        targetUserId = aliceUid,
        newHealth = 65,
        newDeaths = 0
      )
    }
    assertTrue("applyDamageToOpponent failed: ${hitResult.exceptionOrNull()}", hitResult.isSuccess)

    // Observe players in room
    val roomPlayers = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeRoomPlayers(code).first { players ->
        players.any { it.userId == aliceUid && it.health == 65L }
      }
    }
    assertEquals(2, roomPlayers.size)
  }

  @Test
  fun unauthenticatedCaller_cannotQueryRooms() = runBlocking {
    auth.signOut()
    var caught: Throwable? = null
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("rooms").whereEqualTo("isPublic", true).get().await()
      }
    } catch (t: Throwable) {
      caught = t
    }
    assertNotNull(caught)
    val firestoreEx = caught?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice@chessarena.test"
    const val BOB_EMAIL = "bob@chessarena.test"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
