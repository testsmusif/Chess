package com.example.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.data.RoomPlayer
import com.example.settings.CameraPerspective
import com.example.settings.GameSettings
import com.example.settings.GraphicsQuality
import com.example.settings.ReflectionQuality
import com.example.settings.ShadowQuality
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class RenderPolygon(
  val screenPoints: List<Offset>,
  val depth: Float,
  val fillColor: Color,
  val strokeColor: Color? = null,
  val strokeWidth: Float = 1f
)

data class PlayerRenderState(
  val pos: Vec3,
  val yaw: Float,
  val pitch: Float,
  val camPos: Vec3,
  val camYaw: Float,
  val camPitch: Float,
  val perspective: CameraPerspective,
  val isAiming: Boolean,
  val recoilOffset: Float,
  val reloadProgress: Float,
  val isReloading: Boolean,
  val walkPhase: Float,
  val muzzleFlashIntensity: Float,
  val hurtFlashIntensity: Float,
  val equippedWeapon: String,
  val team: String,
  val arenaTheme: String
)

/**
 * Full 3D Perspective Renderer for CHESS ARENA.
 * Performs 3D camera view transformation, near-plane clipping, painter's depth sorting,
 * directional + distance fog shading, ground planar reflections, soft contact shadows,
 * 3D character & weapon rendering, bullet tracers, and particle effects.
 */
@Composable
fun Arena3DViewport(
  world: ChessArenaWorld,
  renderState: PlayerRenderState,
  remotePlayers: List<RoomPlayer>,
  trainingDrones: List<TrainingDrone>,
  particles: List<Particle3D>,
  tracers: List<BulletTracer3D>,
  decals: List<ImpactDecal3D>,
  settings: GameSettings,
  localUserId: String,
  modifier: Modifier = Modifier
) {
  val textMeasurer = rememberTextMeasurer()
  val reusablePath = remember { Path() }

  Canvas(modifier = modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    if (w <= 10f || h <= 10f) return@Canvas

    // 1. Compute Camera Basis Vectors
    val effectiveFov = if (renderState.isAiming) (settings.fov * 0.72f) else settings.fov
    val fovRad = (effectiveFov.coerceIn(50f, 110f) * PI / 180.0).toFloat()
    val focal = (h * 0.5f) / tan(fovRad * 0.5f)

    val camForward = Math3D.forwardFromYawPitch(renderState.camYaw, renderState.camPitch)
    val worldUp = Vec3(0f, 1f, 0f)
    val camRight = camForward.cross(worldUp).normalized()
    val camUp = camRight.cross(camForward).normalized()
    val camOrigin = renderState.camPos

    val maxDrawDist = when (settings.qualityPreset) {
      GraphicsQuality.LOW -> 28f
      GraphicsQuality.MEDIUM -> 38f
      GraphicsQuality.HIGH -> 50f
      GraphicsQuality.ULTRA -> 65f
    }.coerceAtMost(settings.drawDistance)

    // 2. Render Atmospheric Skybox & Horizon Glow
    drawAtmosphericSky(w, h, renderState.camPitch, renderState.arenaTheme, settings.highContrastArena)

    // Helper: Transform World Vec3 -> Camera Space (x = right, y = up, z = forward depth)
    fun toCamSpace(v: Vec3): Vec3 {
      val rel = v - camOrigin
      return Vec3(
        x = rel.dot(camRight),
        y = rel.dot(camUp),
        z = rel.dot(camForward)
      )
    }

    // Helper: Project Camera Space Vec3 (with z > 0.15f) -> 2D Screen Offset
    fun toScreen(camPt: Vec3): Offset {
      val invZ = focal / camPt.z.coerceAtLeast(0.12f)
      return Offset(
        x = w * 0.5f + camPt.x * invZ,
        y = h * 0.5f - camPt.y * invZ
      )
    }

    // Helper: Clip polygon in camera space against near plane z = NEAR_Z and project to screen
    fun clipAndProjectPolygon(worldVerts: List<Vec3>): Pair<List<Offset>, Float>? {
      val nearZ = 0.22f
      val camVerts = worldVerts.map { toCamSpace(it) }
      var maxZ = -100f
      for (cv in camVerts) {
        if (cv.z > maxZ) maxZ = cv.z
      }
      if (maxZ < nearZ || maxZ > maxDrawDist) return null

      val clipped = ArrayList<Vec3>(camVerts.size + 2)
      for (i in camVerts.indices) {
        val curr = camVerts[i]
        val prev = camVerts[(i + camVerts.size - 1) % camVerts.size]
        val currIn = curr.z >= nearZ
        val prevIn = prev.z >= nearZ

        if (currIn != prevIn) {
          val t = (nearZ - prev.z) / (curr.z - prev.z)
          clipped.add(
            Vec3(
              x = prev.x + (curr.x - prev.x) * t,
              y = prev.y + (curr.y - prev.y) * t,
              z = nearZ
            )
          )
        }
        if (currIn) {
          clipped.add(curr)
        }
      }
      if (clipped.size < 3) return null

      var avgDepth = 0f
      val pts = ArrayList<Offset>(clipped.size)
      for (c in clipped) {
        avgDepth += c.z
        pts.add(toScreen(c))
      }
      avgDepth /= clipped.size
      return pts to avgDepth
    }

    val polygons = ArrayList<RenderPolygon>(700)
    val lightDir = Vec3(0.42f, 0.82f, -0.38f).normalized()
    val fogColor = when (renderState.arenaTheme) {
      "royal_marble" -> Color(0xFF101827)
      "neon_void" -> Color(0xFF090514)
      else -> Color(0xFF070B14)
    }

    fun shadeColor(
      baseHex: Long,
      normal: Vec3,
      depth: Float,
      isEmissive: Boolean = false,
      alpha: Float = 1f
    ): Color {
      val r0 = ((baseHex shr 16) and 0xFF) / 255f
      val g0 = ((baseHex shr 8) and 0xFF) / 255f
      val b0 = (baseHex and 0xFF) / 255f

      val ndotl = (normal.dot(lightDir) * 0.5f + 0.5f).coerceIn(0.25f, 1.0f)
      val lightMul = if (isEmissive) 1.15f else (0.38f + 0.68f * ndotl)

      val fogFactor = ((depth - 14f) / (maxDrawDist - 10f)).coerceIn(0f, 0.78f)
      val r = (r0 * lightMul * (1f - fogFactor) + fogColor.red * fogFactor).coerceIn(0f, 1f)
      val g = (g0 * lightMul * (1f - fogFactor) + fogColor.green * fogFactor).coerceIn(0f, 1f)
      val b = (b0 * lightMul * (1f - fogFactor) + fogColor.blue * fogFactor).coerceIn(0f, 1f)
      return Color(r, g, b, alpha.coerceIn(0f, 1f))
    }

    // 3. Build 8x8 Volumetric Chess Board Tiles & Outer Arena Deck
    val whiteTileHex = when {
      settings.highContrastArena -> 0xFFFFFFFF
      renderState.arenaTheme == "royal_marble" -> 0xFFF1F5F9
      renderState.arenaTheme == "neon_void" -> 0xFFD8B4FE
      else -> 0xFFE2E8F0
    }
    val blackTileHex = when {
      settings.highContrastArena -> 0xFF090D16
      renderState.arenaTheme == "royal_marble" -> 0xFF1E293B
      renderState.arenaTheme == "neon_void" -> 0xFF130924
      else -> 0xFF0F172A
    }
    val gridAccentHex = when (renderState.arenaTheme) {
      "royal_marble" -> 0xFFFFD54F
      "neon_void" -> 0xFFE040FB
      else -> 0xFF00E5FF
    }

    val halfB = ChessArenaWorld.HALF_BOARD
    val tileS = ChessArenaWorld.TILE_SIZE

    for (row in 0 until 8) {
      for (col in 0 until 8) {
        val x0 = -halfB + col * tileS
        val x1 = x0 + tileS
        val z0 = -halfB + row * tileS
        val z1 = z0 + tileS
        val isWhite = (row + col) % 2 == 0
        val hex = if (isWhite) whiteTileHex else blackTileHex

        val verts = listOf(
          Vec3(x0, 0f, z0),
          Vec3(x1, 0f, z0),
          Vec3(x1, 0f, z1),
          Vec3(x0, 0f, z1)
        )
        clipAndProjectPolygon(verts)?.let { (pts, depth) ->
          val fill = shadeColor(hex, Vec3(0f, 1f, 0f), depth)
          val border = if (settings.glowEffects) {
            shadeColor(gridAccentHex, Vec3(0f, 1f, 0f), depth, isEmissive = true, alpha = 0.32f)
          } else {
            Color(0x33475569)
          }
          polygons.add(
            RenderPolygon(
              screenPoints = pts,
              depth = depth + 4.5f, // Bias floor slightly behind standing objects
              fillColor = fill,
              strokeColor = border,
              strokeWidth = 1.2f
            )
          )
        }
      }
    }

    // 4. Volumetric Board Side Bevel Skirt & Glowing Perimeter Barrier Rails
    val limit = ChessArenaWorld.ARENA_LIMIT
    val railH = 1.15f
    val wallSegments = listOf(
      listOf(Vec3(-limit, 0f, -limit), Vec3(limit, 0f, -limit), Vec3(limit, railH, -limit), Vec3(-limit, railH, -limit)) to Vec3(0f, 0f, 1f),
      listOf(Vec3(limit, 0f, limit), Vec3(-limit, 0f, limit), Vec3(-limit, railH, limit), Vec3(limit, railH, limit)) to Vec3(0f, 0f, -1f),
      listOf(Vec3(-limit, 0f, limit), Vec3(-limit, 0f, -limit), Vec3(-limit, railH, -limit), Vec3(-limit, railH, limit)) to Vec3(1f, 0f, 0f),
      listOf(Vec3(limit, 0f, -limit), Vec3(limit, 0f, limit), Vec3(limit, railH, limit), Vec3(limit, railH, -limit)) to Vec3(-1f, 0f, 0f)
    )
    for ((verts, norm) in wallSegments) {
      clipAndProjectPolygon(verts)?.let { (pts, depth) ->
        polygons.add(
          RenderPolygon(
            screenPoints = pts,
            depth = depth + 1.5f,
            fillColor = shadeColor(0xFF0F172A, norm, depth, alpha = 0.78f),
            strokeColor = shadeColor(gridAccentHex, norm, depth, isEmissive = true, alpha = 0.75f),
            strokeWidth = 2.2f
          )
        )
      }
    }

    // 5. Render 3D Chess Pieces (with Planar Reflections & Contact Shadows if enabled)
    val detailStep = if (settings.qualityPreset == GraphicsQuality.LOW) 2 else 1
    for (piece in world.chessPieces) {
      val pieceCam = toCamSpace(piece.center)
      if (pieceCam.z < -piece.radius * 2f || pieceCam.z > maxDrawDist) continue

      // Soft Contact Shadow on Floor
      if (settings.shadowQuality != ShadowQuality.OFF) {
        val sr = piece.radius * 1.18f
        val shadowVerts = (0 until 8).map { idx ->
          val a = (2.0 * PI * idx / 8.0).toFloat()
          Vec3(piece.center.x + sr * cos(a), 0.02f, piece.center.z + sr * sin(a))
        }
        clipAndProjectPolygon(shadowVerts)?.let { (pts, depth) ->
          polygons.add(
            RenderPolygon(
              screenPoints = pts,
              depth = depth + 3.8f,
              fillColor = Color(0f, 0f, 0f, if (settings.shadowQuality == ShadowQuality.SHARP) 0.52f else 0.35f)
            )
          )
        }
      }

      // Planar Glossy Floor Reflection (inverted Y below floor)
      if (settings.reflectionQuality != ReflectionQuality.OFF && pieceCam.z < 26f) {
        for (i in piece.faces.indices step (detailStep * 2)) {
          val face = piece.faces[i]
          val reflVerts = face.vertices.map { Vec3(it.x, -it.y * 0.35f, it.z) }
          clipAndProjectPolygon(reflVerts)?.let { (pts, depth) ->
            polygons.add(
              RenderPolygon(
                screenPoints = pts,
                depth = depth + 4.1f,
                fillColor = shadeColor(face.baseColorHex, face.normal, depth, face.isEmissive, alpha = 0.16f)
              )
            )
          }
        }
      }

      // Main 3D Sculpted Mesh Faces
      for (i in piece.faces.indices step detailStep) {
        val face = piece.faces[i]
        clipAndProjectPolygon(face.vertices)?.let { (pts, depth) ->
          val fill = shadeColor(face.baseColorHex, face.normal, depth, face.isEmissive)
          val stroke = if (face.isEmissive && settings.glowEffects) {
            Color(1f, 1f, 1f, 0.45f)
          } else {
            Color(0f, 0f, 0f, 0.22f)
          }
          polygons.add(
            RenderPolygon(
              screenPoints = pts,
              depth = depth,
              fillColor = fill,
              strokeColor = stroke,
              strokeWidth = if (face.isEmissive) 1.5f else 0.7f
            )
          )
        }
      }
    }

    // 6. Helper to add 3D Character / Drone Model (Torso, Head, Visor, Arms, Legs, Weapon)
    fun addHumanoidMesh(
      pos: Vec3,
      yaw: Float,
      armorHex: Long,
      visorHex: Long,
      walkPhase: Float,
      isLocalPlayer: Boolean = false
    ) {
      val forward = Math3D.forwardFromYawPitch(yaw, 0f)
      val right = Math3D.rightFromYaw(yaw)
      val legSwing = sin(walkPhase) * 0.18f

      // Shadow
      if (settings.shadowQuality != ShadowQuality.OFF) {
        val r = 0.55f
        val sVerts = listOf(
          pos + Vec3(-r, 0.02f, -r),
          pos + Vec3(r, 0.02f, -r),
          pos + Vec3(r, 0.02f, r),
          pos + Vec3(-r, 0.02f, r)
        )
        clipAndProjectPolygon(sVerts)?.let { (pts, depth) ->
          polygons.add(
            RenderPolygon(pts, depth + 3.2f, Color(0f, 0f, 0f, 0.42f))
          )
        }
      }

      fun addBoxPart(center: Vec3, halfW: Float, halfH: Float, halfD: Float, hex: Long, emissive: Boolean = false) {
        val rVec = right * halfW
        val uVec = Vec3(0f, halfH, 0f)
        val fVec = forward * halfD

        val p000 = center - rVec - uVec - fVec
        val p100 = center + rVec - uVec - fVec
        val p110 = center + rVec + uVec - fVec
        val p010 = center - rVec + uVec - fVec
        val p001 = center - rVec - uVec + fVec
        val p101 = center + rVec - uVec + fVec
        val p111 = center + rVec + uVec + fVec
        val p011 = center - rVec + uVec + fVec

        val boxFaces = listOf(
          listOf(p001, p101, p111, p011) to forward,
          listOf(p100, p000, p010, p110) to (forward * -1f),
          listOf(p101, p100, p110, p111) to right,
          listOf(p000, p001, p011, p010) to (right * -1f),
          listOf(p011, p111, p110, p010) to Vec3(0f, 1f, 0f)
        )
        for ((verts, norm) in boxFaces) {
          clipAndProjectPolygon(verts)?.let { (pts, depth) ->
            polygons.add(
              RenderPolygon(
                screenPoints = pts,
                depth = if (isLocalPlayer) depth - 0.25f else depth,
                fillColor = shadeColor(hex, norm, depth, emissive),
                strokeColor = Color(0f, 0f, 0f, 0.35f),
                strokeWidth = 1f
              )
            )
          }
        }
      }

      // Left & Right Legs
      addBoxPart(pos + right * -0.16f + Vec3(0f, 0.42f, 0f) + forward * legSwing, 0.11f, 0.42f, 0.12f, 0xFF1E293B)
      addBoxPart(pos + right * 0.16f + Vec3(0f, 0.42f, 0f) - forward * legSwing, 0.11f, 0.42f, 0.12f, 0xFF1E293B)
      // Armored Torso + Chess Crest
      addBoxPart(pos + Vec3(0f, 1.12f, 0f), 0.28f, 0.34f, 0.18f, armorHex)
      addBoxPart(pos + Vec3(0f, 1.15f, 0f) + forward * 0.19f, 0.14f, 0.16f, 0.03f, visorHex, emissive = true)
      // Tactical Helmet & Glowing Visor
      addBoxPart(pos + Vec3(0f, 1.62f, 0f), 0.19f, 0.18f, 0.19f, armorHex)
      addBoxPart(pos + Vec3(0f, 1.63f, 0f) + forward * 0.19f, 0.16f, 0.06f, 0.03f, visorHex, emissive = true)
      // Right Arm + 3D Rifle in hands
      val gunCenter = pos + right * 0.34f + Vec3(0f, 1.18f, 0f) + forward * 0.38f
      addBoxPart(pos + right * 0.34f + Vec3(0f, 1.16f, 0f) + forward * 0.12f, 0.09f, 0.22f, 0.14f, armorHex)
      addBoxPart(gunCenter, 0.05f, 0.07f, 0.36f, 0xFF0F172A)
      addBoxPart(gunCenter + Vec3(0f, 0.05f, 0f), 0.03f, 0.02f, 0.28f, visorHex, emissive = true)
    }

    // 7. Render Local Player Character in Third-Person Mode
    if (renderState.perspective == CameraPerspective.THIRD_PERSON) {
      val localArmor = if (renderState.team == "BLACK") 0xFF1E293B else 0xFFE2E8F0
      val localVisor = if (renderState.team == "BLACK") 0xFFFFAB00 else 0xFF00E5FF
      addHumanoidMesh(
        pos = renderState.pos,
        yaw = renderState.yaw,
        armorHex = localArmor,
        visorHex = localVisor,
        walkPhase = renderState.walkPhase,
        isLocalPlayer = true
      )
    }

    // 8. Render Remote Multiplayer Players
    val enemyOverlayTags = mutableListOf<Triple<Offset, String, Int>>()
    for (rp in remotePlayers) {
      if (rp.userId == localUserId || rp.health <= 0L) continue
      val rpPos = Vec3(rp.x.toFloat(), rp.y.toFloat(), rp.z.toFloat())
      val armor = if (rp.team == "BLACK") 0xFF334155 else 0xFFF1F5F9
      val visor = if (rp.team == "BLACK") 0xFFFF1744 else 0xFF00E5FF
      addHumanoidMesh(
        pos = rpPos,
        yaw = rp.yaw.toFloat(),
        armorHex = armor,
        visorHex = visor,
        walkPhase = (rp.x.toFloat() + rp.z.toFloat()) * 1.8f
      )
      val headCam = toCamSpace(rpPos + Vec3(0f, 2.05f, 0f))
      if (headCam.z in 0.5f..maxDrawDist) {
        enemyOverlayTags.add(Triple(toScreen(headCam), rp.nickname, rp.health.toInt()))
      }
    }

    // 9. Render Training Drones (in Training Mode)
    for (drone in trainingDrones) {
      if (drone.health <= 0) continue
      val accent = if (drone.hitFlashTimer > 0f) 0xFFFFFFFF else 0xFFFF1744
      val droneFaces = world.generateChessPieceMesh(
        center = drone.pos,
        type = drone.pieceType,
        isWhite = false,
        overrideAccentHex = accent
      )
      for (face in droneFaces) {
        clipAndProjectPolygon(face.vertices)?.let { (pts, depth) ->
          polygons.add(
            RenderPolygon(
              screenPoints = pts,
              depth = depth,
              fillColor = shadeColor(face.baseColorHex, face.normal, depth, face.isEmissive),
              strokeColor = if (face.isEmissive) Color(0xFFFF5252) else Color(0x33000000),
              strokeWidth = if (face.isEmissive) 1.6f else 0.8f
            )
          )
        }
      }
      val tagCam = toCamSpace(drone.pos + Vec3(0f, 3.6f, 0f))
      if (tagCam.z in 0.5f..maxDrawDist) {
        enemyOverlayTags.add(Triple(toScreen(tagCam), drone.callsign, drone.health))
      }
    }

    // 10. Sort All 3D Polygons Back-to-Front (Painter's Algorithm) & Draw
    polygons.sortByDescending { it.depth }
    for (poly in polygons) {
      reusablePath.reset()
      val pts = poly.screenPoints
      reusablePath.moveTo(pts[0].x, pts[0].y)
      for (i in 1 until pts.size) {
        reusablePath.lineTo(pts[i].x, pts[i].y)
      }
      reusablePath.close()
      drawPath(reusablePath, color = poly.fillColor)
      if (poly.strokeColor != null) {
        drawPath(
          path = reusablePath,
          color = poly.strokeColor,
          style = Stroke(width = poly.strokeWidth)
        )
      }
    }

    // 11. Draw Impact Decals
    for (decal in decals) {
      val dc = toCamSpace(decal.pos)
      if (dc.z in 0.3f..35f) {
        val sp = toScreen(dc)
        val radius = (11f / dc.z).coerceIn(2f, 14f)
        drawCircle(
          color = Color(decal.colorHex).copy(alpha = (decal.life / 5f).coerceIn(0f, 0.85f)),
          radius = radius,
          center = sp
        )
      }
    }

    // 12. Draw 3D Bullet Tracers
    for (tracer in tracers) {
      val cStart = toCamSpace(tracer.start)
      val cEnd = toCamSpace(tracer.end)
      if (cStart.z > 0.25f && cEnd.z > 0.25f) {
        val s0 = toScreen(cStart)
        val s1 = toScreen(cEnd)
        val alpha = (tracer.life / tracer.maxLife).coerceIn(0f, 1f)
        drawLine(
          color = Color(tracer.colorHex).copy(alpha = alpha),
          start = s0,
          end = s1,
          strokeWidth = 4.5f * alpha,
          cap = StrokeCap.Round
        )
        drawLine(
          color = Color.White.copy(alpha = alpha),
          start = s0,
          end = s1,
          strokeWidth = 1.8f * alpha,
          cap = StrokeCap.Round
        )
      }
    }

    // 13. Draw 3D Spark / Smoke Particles
    if (!settings.simplifyVisualEffects) {
      for (pt in particles) {
        val cp = toCamSpace(pt.pos)
        if (cp.z in 0.25f..35f) {
          val sp = toScreen(cp)
          val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
          val rad = ((pt.size * focal) / cp.z).coerceIn(1.5f, 18f)
          drawCircle(
            color = Color(pt.colorHex).copy(alpha = alpha),
            radius = rad,
            center = sp
          )
        }
      }
    }

    // 14. Draw Floating Nameplates & Health Bars above Enemies / Drones
    for ((screenPt, name, hp) in enemyOverlayTags) {
      if (screenPt.x in 0f..w && screenPt.y in 0f..h) {
        val barW = 76f
        val barH = 7f
        val barLeft = screenPt.x - barW * 0.5f
        val barTop = screenPt.y - 16f
        drawRect(
          color = Color(0xAA0F172A),
          topLeft = Offset(barLeft - 2f, barTop - 2f),
          size = androidx.compose.ui.geometry.Size(barW + 4f, barH + 4f)
        )
        val hpFrac = (hp / 100f).coerceIn(0f, 1f)
        val hpColor = if (hpFrac > 0.5f) Color(0xFF00E5FF) else Color(0xFFFF1744)
        drawRect(
          color = hpColor,
          topLeft = Offset(barLeft, barTop),
          size = androidx.compose.ui.geometry.Size(barW * hpFrac, barH)
        )
        val textLayout = textMeasurer.measure(
          text = "$name [$hp]",
          style = TextStyle(
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
        )
        drawText(
          textLayoutResult = textLayout,
          topLeft = Offset(screenPt.x - textLayout.size.width * 0.5f, barTop - textLayout.size.height - 2f)
        )
      }
    }

    // 15. Render First-Person 3D Weapon Viewmodel (When in FIRST_PERSON mode)
    if (renderState.perspective == CameraPerspective.FIRST_PERSON) {
      drawFirstPersonWeapon(
        w = w,
        h = h,
        renderState = renderState,
        settings = settings,
        reusablePath = reusablePath
      )
    }

    // 16. Damage Vignette Overlay (when taking damage)
    if (renderState.hurtFlashIntensity > 0.01f) {
      val flashAlpha = (renderState.hurtFlashIntensity * (if (settings.reduceFlashIntensity) 0.25f else 0.5f))
        .coerceIn(0f, 0.6f)
      drawRect(
        brush = Brush.radialGradient(
          colors = listOf(Color.Transparent, Color(0xFFFF1744).copy(alpha = flashAlpha)),
          center = Offset(w * 0.5f, h * 0.5f),
          radius = w.coerceAtLeast(h) * 0.65f
        )
      )
    }
  }
}

private fun DrawScope.drawAtmosphericSky(
  w: Float,
  h: Float,
  camPitch: Float,
  arenaTheme: String,
  highContrast: Boolean
) {
  val horizonY = (h * 0.5f + (camPitch / 90f) * h * 0.45f).coerceIn(0f, h)
  val topSky = when {
    highContrast -> Color(0xFF020408)
    arenaTheme == "royal_marble" -> Color(0xFF0B132B)
    arenaTheme == "neon_void" -> Color(0xFF090314)
    else -> Color(0xFF050811)
  }
  val horizonGlow = when {
    highContrast -> Color(0xFF0F172A)
    arenaTheme == "royal_marble" -> Color(0xFF1C2541)
    arenaTheme == "neon_void" -> Color(0xFF240B36)
    else -> Color(0xFF0F203C)
  }
  val voidBelow = Color(0xFF03060C)

  drawRect(
    brush = Brush.verticalGradient(
      0.0f to topSky,
      (horizonY / h).coerceIn(0.05f, 0.95f) to horizonGlow,
      1.0f to voidBelow
    )
  )
}

private fun DrawScope.drawFirstPersonWeapon(
  w: Float,
  h: Float,
  renderState: PlayerRenderState,
  settings: GameSettings,
  reusablePath: Path
) {
  val handSign = if (settings.leftHandedMode) -1f else 1f
  val bobX = sin(renderState.walkPhase) * 9f
  val bobY = abs(cos(renderState.walkPhase)) * 7f
  val recoilKickY = renderState.recoilOffset * 38f
  val reloadDropY = if (renderState.isReloading) sin(renderState.reloadProgress * PI).toFloat() * 110f else 0f

  val aimLerp = if (renderState.isAiming) 1f else 0f
  val baseAnchorX = w * 0.5f + handSign * (w * 0.22f * (1f - aimLerp * 0.82f)) + bobX
  val baseAnchorY = h * (0.76f + aimLerp * 0.04f) + bobY + recoilKickY + reloadDropY

  val barrelTipX = w * 0.5f + handSign * (w * 0.05f * (1f - aimLerp * 0.9f)) + bobX * 0.4f
  val barrelTipY = h * (0.54f - aimLerp * 0.02f) + recoilKickY * 0.6f + reloadDropY * 0.7f

  val accentColor = when (renderState.equippedWeapon) {
    "RAIL_PISTOL" -> Color(0xFFFFAB00)
    "SHOTGUN_ROOK" -> Color(0xFFFF1744)
    else -> Color(0xFF00E5FF)
  }

  // Muzzle Flash at barrel tip
  if (renderState.muzzleFlashIntensity > 0.05f) {
    val flashMul = if (settings.reduceFlashIntensity) 0.45f else 1.0f
    val flashRadius = 56f * renderState.muzzleFlashIntensity * flashMul
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.9f * flashMul),
          accentColor.copy(alpha = 0.65f * flashMul),
          Color.Transparent
        ),
        center = Offset(barrelTipX, barrelTipY),
        radius = flashRadius
      ),
      radius = flashRadius,
      center = Offset(barrelTipX, barrelTipY)
    )
  }

  // 3D Weapon Receiver & Barrel Geometry
  val gunWidth = when (renderState.equippedWeapon) {
    "SHOTGUN_ROOK" -> 54f
    "RAIL_PISTOL" -> 34f
    else -> 44f
  }

  // Upper Receiver Body
  reusablePath.reset()
  reusablePath.moveTo(barrelTipX - gunWidth * 0.35f, barrelTipY)
  reusablePath.lineTo(barrelTipX + gunWidth * 0.35f, barrelTipY)
  reusablePath.lineTo(baseAnchorX + gunWidth * 0.95f, baseAnchorY)
  reusablePath.lineTo(baseAnchorX - gunWidth * 0.85f, baseAnchorY)
  reusablePath.close()
  drawPath(reusablePath, color = Color(0xFF1E293B))
  drawPath(reusablePath, color = Color(0xFF475569), style = Stroke(width = 2.5f))

  // Side Shroud Plate
  reusablePath.reset()
  reusablePath.moveTo(barrelTipX, barrelTipY + 12f)
  reusablePath.lineTo(barrelTipX + handSign * gunWidth * 0.45f, barrelTipY + 18f)
  reusablePath.lineTo(baseAnchorX + handSign * gunWidth * 1.15f, baseAnchorY + 80f)
  reusablePath.lineTo(baseAnchorX, baseAnchorY + 80f)
  reusablePath.close()
  drawPath(reusablePath, color = Color(0xFF0F172A))

  // Glowing Energy Core / Sight Rail
  drawLine(
    color = accentColor,
    start = Offset(barrelTipX, barrelTipY + 6f),
    end = Offset(baseAnchorX, baseAnchorY - 8f),
    strokeWidth = 5f,
    cap = StrokeCap.Round
  )

  // Holographic Iron Sight Ring
  val sightX = (barrelTipX + baseAnchorX) * 0.5f
  val sightY = (barrelTipY + baseAnchorY) * 0.5f - 18f
  drawCircle(
    color = accentColor.copy(alpha = 0.85f),
    radius = 12f,
    center = Offset(sightX, sightY),
    style = Stroke(width = 2.5f)
  )
  drawCircle(
    color = accentColor,
    radius = 2.5f,
    center = Offset(sightX, sightY)
  )
}
