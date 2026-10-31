package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChessArenaRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  // --- USER PROFILE ---

  suspend fun ensureUserProfile(defaultNickname: String): Result<UserProfile> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    try {
      val snap = docRef.get().await()
      if (snap.exists()) {
        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          ?: UserProfile(id = uid, userId = uid, nickname = defaultNickname)
      } else {
        val cleanName = defaultNickname.trim().take(30).ifEmpty { "Commander" }
        val payload = mapOf(
          "userId" to uid,
          "nickname" to cleanName,
          "kills" to 0L,
          "deaths" to 0L,
          "matchesPlayed" to 0L,
          "wins" to 0L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        UserProfile(id = uid, userId = uid, nickname = cleanName)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun getUserProfile(userId: String): Result<UserProfile?> = runCatching {
    val docRef = db.collection("users").document(userId)
    try {
      val snap = docRef.get().await()
      snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  fun observeOwnProfile(): Flow<UserProfile?> = flow {
    val uid = requireUserId()
    val path = "users/$uid"
    emitAll(
      db.collection("users").document(uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
          throw error
        }
    )
  }

  suspend fun updateNickname(newNickname: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val cleanName = newNickname.trim().take(30).ifEmpty { "Commander" }
    val docRef = db.collection("users").document(uid)
    try {
      docRef.update(
        mapOf(
          "nickname" to cleanName,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun recordMatchStats(killsDelta: Int, deathsDelta: Int, won: Boolean): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    try {
      docRef.update(
        mapOf(
          "kills" to FieldValue.increment(killsDelta.toLong().coerceAtLeast(0L)),
          "deaths" to FieldValue.increment(deathsDelta.toLong().coerceAtLeast(0L)),
          "matchesPlayed" to FieldValue.increment(1L),
          "wins" to FieldValue.increment(if (won) 1L else 0L),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  // --- ROOMS & MATCHMAKING ---

  suspend fun createRoom(
    roomCode: String,
    hostName: String,
    mode: String,
    isPublic: Boolean,
    scoreLimit: Int,
    arenaTheme: String,
    weapon: String
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val cleanCode = roomCode.uppercase().filter { it.isLetterOrDigit() }.take(8).padEnd(6, 'X')
    val cleanHostName = hostName.trim().take(30).ifEmpty { "Commander" }
    val validMode = if (mode == "ffa") "ffa" else "tdm"
    val validTheme = if (arenaTheme in listOf("cyber_obsidian", "royal_marble", "neon_void")) arenaTheme else "cyber_obsidian"
    val validWeapon = if (weapon in listOf("PULSE_RIFLE", "RAIL_PISTOL", "SHOTGUN_ROOK")) weapon else "PULSE_RIFLE"

    val roomRef = db.collection("rooms").document(cleanCode)
    try {
      val roomPayload = mapOf(
        "roomCode" to cleanCode,
        "hostId" to uid,
        "hostName" to cleanHostName,
        "mode" to validMode,
        "status" to "waiting",
        "isPublic" to isPublic,
        "scoreLimit" to scoreLimit.coerceIn(5, 50).toLong(),
        "whiteScore" to 0L,
        "blackScore" to 0L,
        "playerCount" to 1L,
        "winner" to "",
        "arenaTheme" to validTheme,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      roomRef.set(roomPayload).await()

      val playerRef = roomRef.collection("players").document(uid)
      val team = if (validMode == "ffa") "FFA" else "WHITE"
      val playerPayload = mapOf(
        "userId" to uid,
        "roomCode" to cleanCode,
        "nickname" to cleanHostName,
        "team" to team,
        "x" to -6.0,
        "y" to 0.0,
        "z" to -11.0,
        "yaw" to 0.0,
        "pitch" to 0.0,
        "health" to 100L,
        "ammo" to 30L,
        "kills" to 0L,
        "deaths" to 0L,
        "weapon" to validWeapon,
        "lastShotSeq" to 0L,
        "lastHitBy" to "",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      playerRef.set(playerPayload).await()
      cleanCode
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, roomRef.path)
      throw e
    }
  }

  suspend fun joinRoomByCode(
    rawCode: String,
    nickname: String,
    weapon: String
  ): Result<MatchRoom> = runCatching {
    val uid = requireUserId()
    val code = rawCode.trim().uppercase()
    require(code.length in 4..12) { "Некорректный код комнаты" }
    val roomRef = db.collection("rooms").document(code)
    try {
      val snap = roomRef.get().await()
      if (!snap.exists()) {
        throw IllegalArgumentException("Комната $code не найдена")
      }
      val room = snap.toObject(MatchRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: throw IllegalStateException("Ошибка чтения комнаты $code")

      if (room.status == "finished") {
        throw IllegalStateException("Матч в комнате $code уже завершён")
      }

      val existingPlayersSnap = roomRef.collection("players")
        .whereEqualTo("roomCode", code)
        .get()
        .await()
      val existingPlayers = existingPlayersSnap.toObjects(
        RoomPlayer::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      )

      val whiteCount = existingPlayers.count { it.team == "WHITE" }
      val blackCount = existingPlayers.count { it.team == "BLACK" }
      val assignedTeam = when {
        room.mode == "ffa" -> "FFA"
        whiteCount <= blackCount -> "WHITE"
        else -> "BLACK"
      }
      val spawnZ = if (assignedTeam == "BLACK") 11.0 else -11.0
      val spawnYaw = if (assignedTeam == "BLACK") 180.0 else 0.0
      val spawnX = ((existingPlayers.size % 4) - 1.5) * 3.0
      val validWeapon = if (weapon in listOf("PULSE_RIFLE", "RAIL_PISTOL", "SHOTGUN_ROOK")) weapon else "PULSE_RIFLE"
      val cleanName = nickname.trim().take(30).ifEmpty { "Operator" }

      val playerRef = roomRef.collection("players").document(uid)
      val playerPayload = mapOf(
        "userId" to uid,
        "roomCode" to code,
        "nickname" to cleanName,
        "team" to assignedTeam,
        "x" to spawnX.coerceIn(-13.0, 13.0),
        "y" to 0.0,
        "z" to spawnZ,
        "yaw" to spawnYaw,
        "pitch" to 0.0,
        "health" to 100L,
        "ammo" to 30L,
        "kills" to 0L,
        "deaths" to 0L,
        "weapon" to validWeapon,
        "lastShotSeq" to 0L,
        "lastHitBy" to "",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      playerRef.set(playerPayload).await()

      val newCount = (existingPlayers.map { it.userId }.toSet() + uid).size.coerceIn(1, 16)
      roomRef.update(
        mapOf(
          "playerCount" to newCount.toLong(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      room.copy(playerCount = newCount.toLong())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, roomRef.path)
      throw e
    }
  }

  fun observePublicRooms(): Flow<List<MatchRoom>> = flow {
    requireUserId()
    val path = "rooms"
    emitAll(
      db.collection(path)
        .whereEqualTo("isPublic", true)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(MatchRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .filter { it.status != "finished" }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
          throw error
        }
    )
  }

  fun observeRoom(roomCode: String): Flow<MatchRoom?> = flow {
    requireUserId()
    val path = "rooms/$roomCode"
    emitAll(
      db.collection("rooms").document(roomCode)
        .snapshots()
        .map { snapshot ->
          snapshot.toObject(MatchRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
          throw error
        }
    )
  }

  fun observeRoomPlayers(roomCode: String): Flow<List<RoomPlayer>> = flow {
    requireUserId()
    val path = "rooms/$roomCode/players"
    emitAll(
      db.collection("rooms").document(roomCode).collection("players")
        .whereEqualTo("roomCode", roomCode)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(RoomPlayer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
          throw error
        }
    )
  }

  suspend fun startMatch(roomCode: String): Result<Unit> = runCatching {
    requireUserId()
    val roomRef = db.collection("rooms").document(roomCode)
    try {
      roomRef.update(
        mapOf(
          "status" to "playing",
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
      throw e
    }
  }

  suspend fun syncPlayerTelemetry(
    roomCode: String,
    x: Double,
    y: Double,
    z: Double,
    yaw: Double,
    pitch: Double,
    health: Int,
    ammo: Int,
    kills: Int,
    deaths: Int,
    weapon: String,
    lastShotSeq: Long
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val playerRef = db.collection("rooms").document(roomCode).collection("players").document(uid)
    try {
      playerRef.update(
        mapOf(
          "x" to x.coerceIn(-29.5, 29.5),
          "y" to y.coerceIn(-4.5, 24.5),
          "z" to z.coerceIn(-29.5, 29.5),
          "yaw" to yaw.coerceIn(-360.0, 360.0),
          "pitch" to pitch.coerceIn(-89.0, 89.0),
          "health" to health.coerceIn(0, 100).toLong(),
          "ammo" to ammo.coerceIn(0, 60).toLong(),
          "kills" to kills.coerceIn(0, 1000).toLong(),
          "deaths" to deaths.coerceIn(0, 1000).toLong(),
          "weapon" to weapon,
          "lastShotSeq" to lastShotSeq.coerceAtLeast(0L),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, playerRef.path)
      throw e
    }
  }

  suspend fun applyDamageToOpponent(
    roomCode: String,
    targetUserId: String,
    newHealth: Int,
    newDeaths: Int
  ): Result<Unit> = runCatching {
    val attackerUid = requireUserId()
    require(attackerUid != targetUserId) { "Cannot apply opponent damage to self" }
    val targetRef = db.collection("rooms").document(roomCode).collection("players").document(targetUserId)
    try {
      targetRef.update(
        mapOf(
          "health" to newHealth.coerceIn(0, 99).toLong(),
          "deaths" to newDeaths.coerceIn(0, 1000).toLong(),
          "lastHitBy" to attackerUid,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, targetRef.path)
      throw e
    }
  }

  suspend fun incrementTeamScoreAndCheckWin(
    roomCode: String,
    scoringTeam: String,
    newTeamScore: Int,
    scoreLimit: Int,
    winnerLabel: String
  ): Result<Unit> = runCatching {
    requireUserId()
    val roomRef = db.collection("rooms").document(roomCode)
    try {
      val updates = mutableMapOf<String, Any>(
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (scoringTeam == "WHITE") {
        updates["whiteScore"] = newTeamScore.coerceIn(0, 1000).toLong()
      } else if (scoringTeam == "BLACK") {
        updates["blackScore"] = newTeamScore.coerceIn(0, 1000).toLong()
      }
      if (newTeamScore >= scoreLimit) {
        updates["status"] = "finished"
        updates["winner"] = winnerLabel.take(64)
      }
      roomRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
      throw e
    }
  }

  suspend fun leaveRoom(roomCode: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val playerRef = db.collection("rooms").document(roomCode).collection("players").document(uid)
    try {
      playerRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, playerRef.path)
      throw e
    }
  }
}
