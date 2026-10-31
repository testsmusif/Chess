package com.example.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ChessPieceType {
  KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN
}

data class Face3D(
  val vertices: List<Vec3>,
  val normal: Vec3,
  val baseColorHex: Long,
  val isEmissive: Boolean = false,
  val metallic: Float = 0.5f
)

data class ChessPieceObstacle(
  val id: String,
  val type: ChessPieceType,
  val isWhite: Boolean,
  val boardRow: Int,
  val boardCol: Int,
  val center: Vec3,
  val radius: Float,
  val height: Float,
  val faces: List<Face3D>
)

data class TrainingDrone(
  val id: String,
  val callsign: String,
  val pieceType: ChessPieceType,
  var pos: Vec3,
  val startPos: Vec3,
  val patrolAxisX: Boolean,
  val patrolRange: Float,
  val speed: Float,
  var health: Int = 100,
  var maxHealth: Int = 100,
  var respawnTimer: Float = 0f,
  var hitFlashTimer: Float = 0f
)

data class Particle3D(
  var pos: Vec3,
  var vel: Vec3,
  var colorHex: Long,
  var life: Float,
  val maxLife: Float,
  val size: Float
)

data class BulletTracer3D(
  val start: Vec3,
  val end: Vec3,
  val colorHex: Long,
  var life: Float,
  val maxLife: Float = 0.16f
)

data class ImpactDecal3D(
  val pos: Vec3,
  val normal: Vec3,
  val colorHex: Long,
  var life: Float = 5.0f
)

/**
 * Generates and manages the 3D Chess Arena (8x8 volumetric board, procedural 3D chess piece
 * meshes, collision boundaries, particle pools, and raycast combat queries).
 */
class ChessArenaWorld {
  companion object {
    const val BOARD_CELLS = 8
    const val TILE_SIZE = 3.5f // 8 * 3.5 = 28m x 28m board
    const val HALF_BOARD = (BOARD_CELLS * TILE_SIZE) / 2f // 14.0f
    const val ARENA_LIMIT = 15.6f // Outer perimeter deck
    const val WALL_HEIGHT = 4.2f
    const val PLAYER_RADIUS = 0.52f
    const val PLAYER_HEIGHT = 1.75f
    const val EYE_HEIGHT = 1.58f
  }

  val chessPieces: List<ChessPieceObstacle> = buildTacticalChessPieces()

  fun createInitialTrainingDrones(): MutableList<TrainingDrone> = mutableListOf(
    TrainingDrone(
      id = "drone_knight_1",
      callsign = "KNIGHT-GUARD α",
      pieceType = ChessPieceType.KNIGHT,
      pos = Vec3(-5.25f, 0f, 3.5f),
      startPos = Vec3(-5.25f, 0f, 3.5f),
      patrolAxisX = true,
      patrolRange = 4.5f,
      speed = 2.4f
    ),
    TrainingDrone(
      id = "drone_rook_2",
      callsign = "ROOK-SENTINEL β",
      pieceType = ChessPieceType.ROOK,
      pos = Vec3(5.25f, 0f, 5.25f),
      startPos = Vec3(5.25f, 0f, 5.25f),
      patrolAxisX = false,
      patrolRange = 4.0f,
      speed = 2.1f
    ),
    TrainingDrone(
      id = "drone_queen_3",
      callsign = "QUEEN-VALKYRIE Ω",
      pieceType = ChessPieceType.QUEEN,
      pos = Vec3(0f, 0f, 8.75f),
      startPos = Vec3(0f, 0f, 8.75f),
      patrolAxisX = true,
      patrolRange = 6.0f,
      speed = 3.1f
    ),
    TrainingDrone(
      id = "drone_pawn_4",
      callsign = "PAWN-SCOUT δ",
      pieceType = ChessPieceType.PAWN,
      pos = Vec3(0f, 0f, 0f),
      startPos = Vec3(0f, 0f, 0f),
      patrolAxisX = true,
      patrolRange = 3.2f,
      speed = 2.8f
    )
  )

  fun boardCellCenter(row: Int, col: Int): Vec3 {
    val x = -HALF_BOARD + (col + 0.5f) * TILE_SIZE
    val z = -HALF_BOARD + (row + 0.5f) * TILE_SIZE
    return Vec3(x, 0f, z)
  }

  private fun buildTacticalChessPieces(): List<ChessPieceObstacle> {
    // Strategic mid-game / endgame tactical cover layout on the 8x8 board
    // Leaving spawn lanes open while providing monument-scale 3D chess sculptures for cover & verticality
    data class Placement(
      val id: String,
      val type: ChessPieceType,
      val isWhite: Boolean,
      val row: Int,
      val col: Int
    )

    val placements = listOf(
      // White Pieces (South / Negative Z side & Midfield)
      Placement("w_king", ChessPieceType.KING, true, 0, 4),
      Placement("w_queen", ChessPieceType.QUEEN, true, 1, 2),
      Placement("w_rook_l", ChessPieceType.ROOK, true, 1, 0),
      Placement("w_rook_r", ChessPieceType.ROOK, true, 1, 7),
      Placement("w_bishop", ChessPieceType.BISHOP, true, 2, 5),
      Placement("w_knight", ChessPieceType.KNIGHT, true, 3, 1),
      Placement("w_pawn_1", ChessPieceType.PAWN, true, 2, 3),
      Placement("w_pawn_2", ChessPieceType.PAWN, true, 3, 6),

      // Black Pieces (North / Positive Z side & Midfield)
      Placement("b_king", ChessPieceType.KING, false, 7, 3),
      Placement("b_queen", ChessPieceType.QUEEN, false, 6, 5),
      Placement("b_rook_l", ChessPieceType.ROOK, false, 6, 0),
      Placement("b_rook_r", ChessPieceType.ROOK, false, 6, 7),
      Placement("b_bishop", ChessPieceType.BISHOP, false, 5, 2),
      Placement("b_knight", ChessPieceType.KNIGHT, false, 4, 6),
      Placement("b_pawn_1", ChessPieceType.PAWN, false, 5, 4),
      Placement("b_pawn_2", ChessPieceType.PAWN, false, 4, 1)
    )

    return placements.map { p ->
      val center = boardCellCenter(p.row, p.col)
      val (radius, height) = when (p.type) {
        ChessPieceType.KING -> 1.05f to 4.1f
        ChessPieceType.QUEEN -> 1.02f to 3.85f
        ChessPieceType.ROOK -> 0.98f to 3.15f
        ChessPieceType.BISHOP -> 0.92f to 3.35f
        ChessPieceType.KNIGHT -> 0.94f to 3.25f
        ChessPieceType.PAWN -> 0.80f to 2.35f
      }
      val faces = generateChessPieceMesh(center, p.type, p.isWhite)
      ChessPieceObstacle(
        id = p.id,
        type = p.type,
        isWhite = p.isWhite,
        boardRow = p.row,
        boardCol = p.col,
        center = center,
        radius = radius,
        height = height,
        faces = faces
      )
    }
  }

  /**
   * Builds a multi-segmented 3D lathe / revolved mesh + crown details for each chess piece.
   */
  fun generateChessPieceMesh(
    center: Vec3,
    type: ChessPieceType,
    isWhite: Boolean,
    overrideAccentHex: Long? = null
  ): List<Face3D> {
    val faces = mutableListOf<Face3D>()
    val bodyColor = if (isWhite) 0xFFE2E8F0 else 0xFF1E293B
    val trimColor = if (isWhite) 0xFF94A3B8 else 0xFF0F172A
    val glowColor = overrideAccentHex ?: if (isWhite) 0xFF00E5FF else 0xFFFFAB00

    // Profile pairs: (radius, heightY)
    val profile: List<Pair<Float, Float>> = when (type) {
      ChessPieceType.PAWN -> listOf(
        0.82f to 0.0f,
        0.78f to 0.25f,
        0.52f to 0.50f,
        0.34f to 1.35f,
        0.50f to 1.50f,
        0.30f to 1.62f,
        0.52f to 1.98f,
        0.38f to 2.28f,
        0.05f to 2.38f
      )
      ChessPieceType.ROOK -> listOf(
        0.98f to 0.0f,
        0.92f to 0.32f,
        0.68f to 0.65f,
        0.58f to 2.25f,
        0.78f to 2.55f,
        0.78f to 3.15f,
        0.10f to 3.15f
      )
      ChessPieceType.BISHOP -> listOf(
        0.92f to 0.0f,
        0.86f to 0.30f,
        0.56f to 0.65f,
        0.38f to 1.95f,
        0.58f to 2.12f,
        0.64f to 2.65f,
        0.44f to 3.12f,
        0.18f to 3.35f
      )
      ChessPieceType.KNIGHT -> listOf(
        0.94f to 0.0f,
        0.88f to 0.32f,
        0.62f to 0.68f,
        0.54f to 1.45f,
        0.42f to 1.65f
      )
      ChessPieceType.QUEEN -> listOf(
        1.02f to 0.0f,
        0.95f to 0.35f,
        0.64f to 0.75f,
        0.42f to 2.35f,
        0.68f to 2.65f,
        0.54f to 2.95f,
        0.82f to 3.55f,
        0.24f to 3.85f
      )
      ChessPieceType.KING -> listOf(
        1.05f to 0.0f,
        0.98f to 0.38f,
        0.68f to 0.80f,
        0.46f to 2.55f,
        0.74f to 2.85f,
        0.58f to 3.10f,
        0.76f to 3.55f,
        0.20f to 3.65f
      )
    }

    val segments = 8
    for (ring in 0 until profile.size - 1) {
      val (r0, y0) = profile[ring]
      val (r1, y1) = profile[ring + 1]
      val isGlowBand = (ring == 1 || ring == 4)
      val ringColor = when {
        isGlowBand -> glowColor
        ring % 2 == 0 -> bodyColor
        else -> trimColor
      }

      for (s in 0 until segments) {
        val a0 = (2.0 * PI * s / segments).toFloat()
        val a1 = (2.0 * PI * (s + 1) / segments).toFloat()

        val p00 = Vec3(center.x + r0 * cos(a0), y0, center.z + r0 * sin(a0))
        val p10 = Vec3(center.x + r0 * cos(a1), y0, center.z + r0 * sin(a1))
        val p11 = Vec3(center.x + r1 * cos(a1), y1, center.z + r1 * sin(a1))
        val p01 = Vec3(center.x + r1 * cos(a0), y1, center.z + r1 * sin(a0))

        val midAngle = (a0 + a1) * 0.5f
        val normal = Vec3(cos(midAngle), 0.2f, sin(midAngle)).normalized()
        faces.add(
          Face3D(
            vertices = listOf(p00, p10, p11, p01),
            normal = normal,
            baseColorHex = ringColor,
            isEmissive = isGlowBand,
            metallic = 0.75f
          )
        )
      }
    }

    // Special 3D sculptural head for KNIGHT
    if (type == ChessPieceType.KNIGHT) {
      val dirZ = if (isWhite) 1f else -1f
      val bx = center.x
      val bz = center.z
      // Sculpted neck + horse snout + mane prism
      val w = 0.34f
      val neckFrontBottom = Vec3(bx, 1.55f, bz + 0.35f * dirZ)
      val neckBackBottom = Vec3(bx, 1.55f, bz - 0.42f * dirZ)
      val headCrown = Vec3(bx, 3.22f, bz - 0.15f * dirZ)
      val snoutTipTop = Vec3(bx, 2.65f, bz + 0.82f * dirZ)
      val snoutTipBot = Vec3(bx, 2.25f, bz + 0.72f * dirZ)

      // Left and right side plates of Knight head
      val leftOffset = Vec3(-w, 0f, 0f)
      val rightOffset = Vec3(w, 0f, 0f)

      faces.add(
        Face3D(
          vertices = listOf(
            neckBackBottom + leftOffset,
            neckFrontBottom + leftOffset,
            snoutTipBot + leftOffset,
            snoutTipTop + leftOffset,
            headCrown + leftOffset
          ),
          normal = Vec3(-1f, 0.1f, 0f).normalized(),
          baseColorHex = bodyColor,
          metallic = 0.8f
        )
      )
      faces.add(
        Face3D(
          vertices = listOf(
            neckBackBottom + rightOffset,
            headCrown + rightOffset,
            snoutTipTop + rightOffset,
            snoutTipBot + rightOffset,
            neckFrontBottom + rightOffset
          ),
          normal = Vec3(1f, 0.1f, 0f).normalized(),
          baseColorHex = bodyColor,
          metallic = 0.8f
        )
      )
      // Glowing cybernetic mane & visor bridge
      faces.add(
        Face3D(
          vertices = listOf(
            headCrown + leftOffset,
            headCrown + rightOffset,
            snoutTipTop + rightOffset,
            snoutTipTop + leftOffset
          ),
          normal = Vec3(0f, 0.7f, 0.7f * dirZ).normalized(),
          baseColorHex = glowColor,
          isEmissive = true
        )
      )
      faces.add(
        Face3D(
          vertices = listOf(
            neckBackBottom + leftOffset,
            headCrown + leftOffset,
            headCrown + rightOffset,
            neckBackBottom + rightOffset
          ),
          normal = Vec3(0f, 0.2f, -dirZ).normalized(),
          baseColorHex = trimColor
        )
      )
    }

    // Special 3D Royal Cross Finial for KING
    if (type == ChessPieceType.KING) {
      val cx = center.x
      val cz = center.z
      val cy = 3.65f
      val cw = 0.14f
      val ch = 0.52f
      val armW = 0.36f
      // Vertical bar front/back
      faces.add(
        Face3D(
          vertices = listOf(
            Vec3(cx - cw, cy, cz + cw),
            Vec3(cx + cw, cy, cz + cw),
            Vec3(cx + cw, cy + ch, cz + cw),
            Vec3(cx - cw, cy + ch, cz + cw)
          ),
          normal = Vec3(0f, 0f, 1f),
          baseColorHex = glowColor,
          isEmissive = true
        )
      )
      // Crossbar front/back
      faces.add(
        Face3D(
          vertices = listOf(
            Vec3(cx - armW, cy + 0.22f, cz),
            Vec3(cx + armW, cy + 0.22f, cz),
            Vec3(cx + armW, cy + 0.38f, cz),
            Vec3(cx - armW, cy + 0.38f, cz)
          ),
          normal = Vec3(0f, 0f, 1f),
          baseColorHex = glowColor,
          isEmissive = true
        )
      )
    }

    return faces
  }

  /**
   * Resolves horizontal player collision against arena boundaries and 3D chess pieces.
   */
  fun resolvePlayerMovement(currentPos: Vec3, desiredPos: Vec3): Vec3 {
    val limit = ARENA_LIMIT - PLAYER_RADIUS
    var rx = desiredPos.x.coerceIn(-limit, limit)
    var rz = desiredPos.z.coerceIn(-limit, limit)

    // Push out of cylindrical chess piece colliders if player is below piece height
    for (piece in chessPieces) {
      if (desiredPos.y < piece.height - 0.1f) {
        val dx = rx - piece.center.x
        val dz = rz - piece.center.z
        val distSq = dx * dx + dz * dz
        val minDist = piece.radius + PLAYER_RADIUS
        if (distSq < minDist * minDist && distSq > 1e-6f) {
          val dist = sqrt(distSq)
          val push = (minDist - dist)
          rx += (dx / dist) * push
          rz += (dz / dist) * push
        }
      }
    }

    return Vec3(
      x = rx.coerceIn(-limit, limit),
      y = desiredPos.y.coerceAtLeast(0f),
      z = rz.coerceIn(-limit, limit)
    )
  }

  /**
   * Computes a collision-safe Third-Person Camera position so the camera never clips
   * through perimeter walls or 3D chess pieces.
   */
  fun computeSafeThirdPersonCamera(
    pivot: Vec3,
    desiredCamPos: Vec3
  ): Vec3 {
    val toCam = desiredCamPos - pivot
    val maxDist = toCam.length()
    if (maxDist < 0.1f) return desiredCamPos

    val ray = Ray3D(pivot, toCam * (1f / maxDist))
    var safeDist = maxDist

    // Check walls
    Math3D.intersectRayWalls(ray, ARENA_LIMIT, WALL_HEIGHT * 2f, maxDist)?.let { (t, _) ->
      if (t - 0.25f < safeDist) safeDist = (t - 0.25f).coerceAtLeast(0.6f)
    }

    // Check chess pieces
    for (piece in chessPieces) {
      Math3D.intersectRayCylinder(
        ray = ray,
        cx = piece.center.x,
        cz = piece.center.z,
        minY = 0f,
        maxY = piece.height,
        radius = piece.radius + 0.15f,
        maxDist = safeDist
      )?.let { (t, _) ->
        if (t - 0.2f < safeDist) safeDist = (t - 0.2f).coerceAtLeast(0.6f)
      }
    }

    val finalPos = pivot + ray.dir * safeDist
    return Vec3(
      x = finalPos.x.coerceIn(-(ARENA_LIMIT - 0.2f), ARENA_LIMIT - 0.2f),
      y = finalPos.y.coerceAtLeast(0.35f),
      z = finalPos.z.coerceIn(-(ARENA_LIMIT - 0.2f), ARENA_LIMIT - 0.2f)
    )
  }

  /**
   * Full 3D raycast against chess pieces, enemy players/drones, floor, and walls.
   */
  data class TargetActor(
    val id: String,
    val pos: Vec3,
    val radius: Float = 0.62f,
    val height: Float = 1.80f
  )

  fun raycastArena(
    ray: Ray3D,
    targets: List<TargetActor>,
    maxDistance: Float = 65f
  ): RaycastHit {
    var bestDist = maxDistance
    var bestPoint = ray.origin + ray.dir * maxDistance
    var bestNormal = ray.dir * -1f
    var bestType = HitSurfaceType.NONE
    var bestTargetId = ""

    // 1. Check Floor
    Math3D.intersectRayFloor(ray, ARENA_LIMIT, bestDist)?.let { t ->
      if (t < bestDist) {
        bestDist = t
        bestPoint = ray.origin + ray.dir * t
        bestNormal = Vec3(0f, 1f, 0f)
        bestType = HitSurfaceType.FLOOR
        bestTargetId = ""
      }
    }

    // 2. Check Perimeter Walls
    Math3D.intersectRayWalls(ray, ARENA_LIMIT, WALL_HEIGHT, bestDist)?.let { (t, normal) ->
      if (t < bestDist) {
        bestDist = t
        bestPoint = ray.origin + ray.dir * t
        bestNormal = normal
        bestType = HitSurfaceType.WALL
        bestTargetId = ""
      }
    }

    // 3. Check 3D Chess Pieces
    for (piece in chessPieces) {
      Math3D.intersectRayCylinder(
        ray = ray,
        cx = piece.center.x,
        cz = piece.center.z,
        minY = 0f,
        maxY = piece.height,
        radius = piece.radius,
        maxDist = bestDist
      )?.let { (t, normal) ->
        if (t < bestDist) {
          bestDist = t
          bestPoint = ray.origin + ray.dir * t
          bestNormal = normal
          bestType = HitSurfaceType.CHESS_PIECE
          bestTargetId = piece.id
        }
      }
    }

    // 4. Check Players & Training Drones
    for (target in targets) {
      Math3D.intersectRayCylinder(
        ray = ray,
        cx = target.pos.x,
        cz = target.pos.z,
        minY = target.pos.y,
        maxY = target.pos.y + target.height,
        radius = target.radius,
        maxDist = bestDist
      )?.let { (t, normal) ->
        if (t < bestDist) {
          bestDist = t
          bestPoint = ray.origin + ray.dir * t
          bestNormal = normal
          bestType = HitSurfaceType.PLAYER_OR_DRONE
          bestTargetId = target.id
        }
      }
    }

    return RaycastHit(
      distance = bestDist,
      point = bestPoint,
      normal = bestNormal,
      hitType = bestType,
      targetId = bestTargetId
    )
  }
}
