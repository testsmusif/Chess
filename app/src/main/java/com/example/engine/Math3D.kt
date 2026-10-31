package com.example.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
  operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
  operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
  operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

  fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

  fun cross(o: Vec3): Vec3 = Vec3(
    y * o.z - z * o.y,
    z * o.x - x * o.z,
    x * o.y - y * o.x
  )

  fun length(): Float = sqrt(x * x + y * y + z * z)

  fun lengthSq(): Float = x * x + y * y + z * z

  fun normalized(): Vec3 {
    val len = length()
    return if (len > 1e-5f) Vec3(x / len, y / len, z / len) else Vec3(0f, 0f, 1f)
  }

  fun lerp(target: Vec3, alpha: Float): Vec3 {
    val t = alpha.coerceIn(0f, 1f)
    return Vec3(
      x + (target.x - x) * t,
      y + (target.y - y) * t,
      z + (target.z - z) * t
    )
  }
}

data class Ray3D(val origin: Vec3, val dir: Vec3)

data class RaycastHit(
  val distance: Float,
  val point: Vec3,
  val normal: Vec3,
  val hitType: HitSurfaceType,
  val targetId: String = ""
)

enum class HitSurfaceType {
  NONE,
  FLOOR,
  WALL,
  CHESS_PIECE,
  PLAYER_OR_DRONE
}

object Math3D {
  private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

  fun forwardFromYawPitch(yawDeg: Float, pitchDeg: Float): Vec3 {
    val yawRad = yawDeg * DEG_TO_RAD
    val pitchRad = pitchDeg * DEG_TO_RAD
    val cp = cos(pitchRad)
    return Vec3(
      x = sin(yawRad) * cp,
      y = sin(pitchRad),
      z = cos(yawRad) * cp
    ).normalized()
  }

  fun rightFromYaw(yawDeg: Float): Vec3 {
    val yawRad = yawDeg * DEG_TO_RAD
    return Vec3(cos(yawRad), 0f, -sin(yawRad)).normalized()
  }

  /**
   * Intersects a 3D ray with a vertical finite cylinder centered at (cx, cz) with y in [minY, maxY].
   */
  fun intersectRayCylinder(
    ray: Ray3D,
    cx: Float,
    cz: Float,
    minY: Float,
    maxY: Float,
    radius: Float,
    maxDist: Float
  ): Pair<Float, Vec3>? {
    val ox = ray.origin.x - cx
    val oz = ray.origin.z - cz
    val dx = ray.dir.x
    val dz = ray.dir.z

    val a = dx * dx + dz * dz
    val b = 2f * (ox * dx + oz * dz)
    val c = ox * ox + oz * oz - radius * radius

    if (a > 1e-6f) {
      val disc = b * b - 4f * a * c
      if (disc >= 0f) {
        val sqrtDisc = sqrt(disc)
        val t1 = (-b - sqrtDisc) / (2f * a)
        val t2 = (-b + sqrtDisc) / (2f * a)
        val candidates = floatArrayOf(t1, t2)
        for (t in candidates) {
          if (t in 0.05f..maxDist) {
            val hitY = ray.origin.y + ray.dir.y * t
            if (hitY in minY..maxY) {
              val hitPt = ray.origin + ray.dir * t
              val normal = Vec3(hitPt.x - cx, 0f, hitPt.z - cz).normalized()
              return t to normal
            }
          }
        }
      }
    }

    // Check top cap
    if (kotlin.math.abs(ray.dir.y) > 1e-5f) {
      val tTop = (maxY - ray.origin.y) / ray.dir.y
      if (tTop in 0.05f..maxDist) {
        val hx = ox + dx * tTop
        val hz = oz + dz * tTop
        if (hx * hx + hz * hz <= radius * radius) {
          return tTop to Vec3(0f, 1f, 0f)
        }
      }
    }
    return null
  }

  /**
   * Intersects a 3D ray with the y = 0 floor plane within [-halfSize, halfSize].
   */
  fun intersectRayFloor(ray: Ray3D, halfSize: Float, maxDist: Float): Float? {
    if (kotlin.math.abs(ray.dir.y) < 1e-5f) return null
    val t = -ray.origin.y / ray.dir.y
    if (t !in 0.05f..maxDist) return null
    val hx = ray.origin.x + ray.dir.x * t
    val hz = ray.origin.z + ray.dir.z * t
    return if (hx in -halfSize..halfSize && hz in -halfSize..halfSize) t else null
  }

  /**
   * Intersects a 3D ray with the 4 perimeter boundary walls at +/- halfSize.
   */
  fun intersectRayWalls(ray: Ray3D, halfSize: Float, wallHeight: Float, maxDist: Float): Pair<Float, Vec3>? {
    var bestT = maxDist + 1f
    var bestNormal = Vec3(0f, 1f, 0f)

    // X walls (+/- halfSize)
    if (kotlin.math.abs(ray.dir.x) > 1e-5f) {
      for (sign in intArrayOf(-1, 1)) {
        val planeX = sign * halfSize
        val t = (planeX - ray.origin.x) / ray.dir.x
        if (t in 0.05f..bestT) {
          val hy = ray.origin.y + ray.dir.y * t
          val hz = ray.origin.z + ray.dir.z * t
          if (hy in 0f..wallHeight && hz in -halfSize..halfSize) {
            bestT = t
            bestNormal = Vec3(-sign.toFloat(), 0f, 0f)
          }
        }
      }
    }

    // Z walls (+/- halfSize)
    if (kotlin.math.abs(ray.dir.z) > 1e-5f) {
      for (sign in intArrayOf(-1, 1)) {
        val planeZ = sign * halfSize
        val t = (planeZ - ray.origin.z) / ray.dir.z
        if (t in 0.05f..bestT) {
          val hy = ray.origin.y + ray.dir.y * t
          val hx = ray.origin.x + ray.dir.x * t
          if (hy in 0f..wallHeight && hx in -halfSize..halfSize) {
            bestT = t
            bestNormal = Vec3(0f, 0f, -sign.toFloat())
          }
        }
      }
    }

    return if (bestT <= maxDist) bestT to bestNormal else null
  }
}
