package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val nickname: String = "Commander",
  val kills: Long = 0L,
  val deaths: Long = 0L,
  val matchesPlayed: Long = 0L,
  val wins: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MatchRoom(
  @DocumentId val id: String = "",
  val roomCode: String = "",
  val hostId: String = "",
  val hostName: String = "Commander",
  val mode: String = "tdm", // "tdm" or "ffa"
  val status: String = "waiting", // "waiting", "playing", "finished"
  val isPublic: Boolean = true,
  val scoreLimit: Long = 15L,
  val whiteScore: Long = 0L,
  val blackScore: Long = 0L,
  val playerCount: Long = 1L,
  val winner: String = "",
  val arenaTheme: String = "cyber_obsidian", // "cyber_obsidian", "royal_marble", "neon_void"
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RoomPlayer(
  @DocumentId val id: String = "",
  val userId: String = "",
  val roomCode: String = "",
  val nickname: String = "Player",
  val team: String = "WHITE", // "WHITE", "BLACK", "FFA"
  val x: Double = 0.0,
  val y: Double = 0.0,
  val z: Double = -11.0,
  val yaw: Double = 0.0,
  val pitch: Double = 0.0,
  val health: Long = 100L,
  val ammo: Long = 30L,
  val kills: Long = 0L,
  val deaths: Long = 0L,
  val weapon: String = "PULSE_RIFLE", // "PULSE_RIFLE", "RAIL_PISTOL", "SHOTGUN_ROOK"
  val lastShotSeq: Long = 0L,
  val lastHitBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
