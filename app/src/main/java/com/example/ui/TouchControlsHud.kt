package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.settings.CameraPerspective
import com.example.settings.CrosshairStyle
import com.example.settings.GameSettings
import kotlin.math.sqrt

/**
 * Multi-touch Mobile Shooter HUD & Controls Overlay.
 * Left half: Virtual movement joystick.
 * Right half: Independent touch look/aim area + Fire, Reload, Aim (ADS), Jump, Camera Mode (1P/3P),
 * Shoulder Swap, Weapon Selector, Scoreboard, and Telemetry HUD.
 */
@Composable
fun CombatHudOverlay(
  uiState: CombatUiState,
  settings: GameSettings,
  onJoystickMove: (Float, Float) -> Unit,
  onLookDelta: (Float, Float) -> Unit,
  onFireHeldChange: (Boolean) -> Unit,
  onReload: () -> Unit,
  onJump: () -> Unit,
  onToggleCamera: () -> Unit,
  onToggleShoulder: () -> Unit,
  onToggleAim: () -> Unit,
  onToggleSprint: () -> Unit,
  onSwitchWeapon: (WeaponSpec) -> Unit,
  onPauseToggle: (Boolean) -> Unit,
  onOpenSettingsFromPause: () -> Unit,
  onExitMatch: () -> Unit,
  modifier: Modifier = Modifier
) {
  val safeMod = if (settings.safeNotchPadding) {
    modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
  } else {
    modifier.fillMaxSize()
  }

  val btnScale = settings.buttonScale.coerceIn(0.75f, 1.35f)
  val btnAlpha = settings.buttonOpacity.coerceIn(0.35f, 1.0f)

  Box(modifier = safeMod) {
    // 1. Multi-Touch Split Zones (Left = Movement Joystick, Right = Free Camera Look)
    Row(modifier = Modifier.fillMaxSize()) {
      if (!settings.leftHandedMode) {
        VirtualJoystickZone(
          opacity = btnAlpha,
          scale = btnScale,
          onMove = onJoystickMove,
          modifier = Modifier
            .weight(0.45f)
            .fillMaxHeight()
            .testTag("virtual_joystick_zone")
        )
        TouchCameraLookZone(
          onLookDelta = onLookDelta,
          modifier = Modifier
            .weight(0.55f)
            .fillMaxHeight()
            .testTag("camera_look_zone")
        )
      } else {
        TouchCameraLookZone(
          onLookDelta = onLookDelta,
          modifier = Modifier
            .weight(0.55f)
            .fillMaxHeight()
            .testTag("camera_look_zone")
        )
        VirtualJoystickZone(
          opacity = btnAlpha,
          scale = btnScale,
          onMove = onJoystickMove,
          modifier = Modifier
            .weight(0.45f)
            .fillMaxHeight()
            .testTag("virtual_joystick_zone")
        )
      }
    }

    // 2. Center Dynamic Crosshair & Hit Marker
    CenterCrosshairAndHitMarker(
      uiState = uiState,
      settings = settings,
      modifier = Modifier.align(Alignment.Center)
    )

    // 3. Top Tactical Status Bar (Match Score, Room Code, Camera Switch, Ping/FPS, Pause)
    TopCombatStatusBar(
      uiState = uiState,
      settings = settings,
      onToggleCamera = onToggleCamera,
      onToggleShoulder = onToggleShoulder,
      onPause = { onPauseToggle(true) },
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
    )

    // 4. Killfeed & Damage Popups (Top-Left / Center-Top)
    if (settings.showKillfeed && uiState.killFeed.isNotEmpty()) {
      Column(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(start = 14.dp, top = 64.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        uiState.killFeed.forEach { msg ->
          Box(
            modifier = Modifier
              .background(Color(0x990B1021), RoundedCornerShape(6.dp))
              .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(6.dp))
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Text(
              text = msg,
              color = Color(0xFFE2E8F0),
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    }

    // Floating Damage Numbers near crosshair
    if (settings.showDamageNumbers && uiState.damagePopups.isNotEmpty()) {
      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(bottom = 84.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        uiState.damagePopups.forEach { pop ->
          Text(
            text = if (pop.isElimination) "💥 -${pop.amount} ЛИКВИДАЦИЯ (${pop.targetName})" else "-${pop.amount}",
            color = if (pop.isElimination) Color(0xFFFFAB00) else Color(0xFF00E5FF),
            fontSize = if (pop.isElimination) 15.sp else 13.sp,
            fontWeight = FontWeight.ExtraBold
          )
        }
      }
    }

    // 5. Bottom-Center Health, Ammo & Weapon Switcher Bar
    BottomHealthAndWeaponBar(
      uiState = uiState,
      onSwitchWeapon = onSwitchWeapon,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 10.dp)
    )

    // 6. Action Buttons Cluster (Fire, Reload, Aim/ADS, Jump, Sprint)
    val clusterAlign = if (settings.leftHandedMode) Alignment.BottomStart else Alignment.BottomEnd
    CombatActionButtonsCluster(
      uiState = uiState,
      btnScale = btnScale,
      btnAlpha = btnAlpha,
      onFireHeldChange = onFireHeldChange,
      onReload = onReload,
      onJump = onJump,
      onToggleAim = onToggleAim,
      onToggleSprint = onToggleSprint,
      modifier = Modifier
        .align(clusterAlign)
        .padding(horizontal = 18.dp, vertical = 18.dp)
    )

    // 7. Respawn Countdown Overlay
    AnimatedVisibility(
      visible = uiState.respawnCountdown > 0f,
      modifier = Modifier.align(Alignment.Center)
    ) {
      Surface(
        color = Color(0xDD0B1021),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .border(1.5.dp, Color(0xFFFF1744), RoundedCornerShape(16.dp))
          .padding(24.dp)
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "ПЕРСОНАЖ УНИЧТОЖЕН",
            color = Color(0xFFFF1744),
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Возрождение на арене через ${"%.1f".format(uiState.respawnCountdown)} сек...",
            color = Color.White,
            fontSize = 14.sp
          )
        }
      }
    }

    // 8. Pause Modal Overlay
    if (uiState.isPaused) {
      PauseMenuDialog(
        uiState = uiState,
        onResume = { onPauseToggle(false) },
        onToggleCamera = onToggleCamera,
        onOpenSettings = onOpenSettingsFromPause,
        onExitMatch = onExitMatch
      )
    }
  }
}

@Composable
private fun VirtualJoystickZone(
  opacity: Float,
  scale: Float,
  onMove: (Float, Float) -> Unit,
  modifier: Modifier = Modifier
) {
  var baseCenter by remember { mutableStateOf<Offset?>(null) }
  var knobPos by remember { mutableStateOf<Offset?>(null) }
  val maxRadiusPx = 115f * scale

  Box(
    modifier = modifier.pointerInput(scale) {
      detectDragGestures(
        onDragStart = { startOffset ->
          baseCenter = startOffset
          knobPos = startOffset
          onMove(0f, 0f)
        },
        onDragEnd = {
          baseCenter = null
          knobPos = null
          onMove(0f, 0f)
        },
        onDragCancel = {
          baseCenter = null
          knobPos = null
          onMove(0f, 0f)
        },
        onDrag = { change, _ ->
          change.consume()
          val c = baseCenter ?: change.position
          val delta = change.position - c
          val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
          val clampedDelta = if (dist > maxRadiusPx) {
            delta * (maxRadiusPx / dist)
          } else {
            delta
          }
          knobPos = c + clampedDelta
          val nx = (clampedDelta.x / maxRadiusPx).coerceIn(-1f, 1f)
          // Screen Y down is negative forward Z
          val nz = (-clampedDelta.y / maxRadiusPx).coerceIn(-1f, 1f)
          onMove(nx, nz)
        }
      )
    }
  ) {
    Canvas(modifier = Modifier.fillMaxSize().alpha(opacity)) {
      val defaultCenter = Offset(size.width * 0.34f, size.height * 0.72f)
      val c = baseCenter ?: defaultCenter
      val k = knobPos ?: c

      drawCircle(
        color = Color(0x550F172A),
        radius = maxRadiusPx,
        center = c
      )
      drawCircle(
        color = Color(0xAA00E5FF),
        radius = maxRadiusPx,
        center = c,
        style = Stroke(width = 3f)
      )
      drawCircle(
        color = Color(0xEE00E5FF),
        radius = maxRadiusPx * 0.38f,
        center = k
      )
    }
  }
}

@Composable
private fun TouchCameraLookZone(
  onLookDelta: (Float, Float) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier.pointerInput(Unit) {
      detectDragGestures { change, dragAmount ->
        change.consume()
        onLookDelta(dragAmount.x * 0.24f, -dragAmount.y * 0.20f)
      }
    }
  )
}

@Composable
private fun CenterCrosshairAndHitMarker(
  uiState: CombatUiState,
  settings: GameSettings,
  modifier: Modifier = Modifier
) {
  val baseColor = if (settings.alternativeCrosshairColor) {
    Color(0xFFFFEA00)
  } else {
    Color(settings.crosshairColor.hex)
  }
  val scale = settings.crosshairSize.coerceIn(0.6f, 1.8f)
  val dynamicSpread = (uiState.recoilOffset * 18f) + (if (uiState.isAiming) 4f else 10f)

  Canvas(modifier = modifier.size(96.dp)) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val gap = dynamicSpread * scale
    val arm = 12f * scale

    when (settings.crosshairStyle) {
      CrosshairStyle.DOT -> {
        drawCircle(color = baseColor, radius = 4f * scale, center = Offset(cx, cy))
      }
      CrosshairStyle.CIRCLE -> {
        drawCircle(
          color = baseColor,
          radius = (14f + uiState.recoilOffset * 10f) * scale,
          center = Offset(cx, cy),
          style = Stroke(width = 2.2f)
        )
        drawCircle(color = baseColor, radius = 2.5f * scale, center = Offset(cx, cy))
      }
      CrosshairStyle.CROSS -> {
        drawLine(baseColor, Offset(cx - gap - arm, cy), Offset(cx - gap, cy), strokeWidth = 2.5f)
        drawLine(baseColor, Offset(cx + gap, cy), Offset(cx + gap + arm, cy), strokeWidth = 2.5f)
        drawLine(baseColor, Offset(cx, cy - gap - arm), Offset(cx, cy - gap), strokeWidth = 2.5f)
        drawLine(baseColor, Offset(cx, cy + gap), Offset(cx, cy + gap + arm), strokeWidth = 2.5f)
      }
      CrosshairStyle.CHESS_CROWN -> {
        drawCircle(
          color = baseColor.copy(alpha = 0.75f),
          radius = gap + 6f,
          center = Offset(cx, cy),
          style = Stroke(width = 1.8f)
        )
        drawLine(baseColor, Offset(cx - gap - arm, cy), Offset(cx - gap, cy), strokeWidth = 2.5f)
        drawLine(baseColor, Offset(cx + gap, cy), Offset(cx + gap + arm, cy), strokeWidth = 2.5f)
        drawLine(baseColor, Offset(cx, cy - gap - arm), Offset(cx, cy - gap), strokeWidth = 2.5f)
        drawLine(baseColor, Offset(cx, cy + gap), Offset(cx, cy + gap + arm), strokeWidth = 2.5f)
        drawCircle(color = baseColor, radius = 2.5f, center = Offset(cx, cy))
      }
    }

    // Hit Marker X
    if (settings.showHitMarker && uiState.hitMarkerAlpha > 0.02f) {
      val hmColor = if (uiState.hitMarkerIsKill) {
        Color(0xFFFF1744).copy(alpha = uiState.hitMarkerAlpha)
      } else {
        Color.White.copy(alpha = uiState.hitMarkerAlpha)
      }
      val d0 = 7f
      val d1 = 18f
      drawLine(hmColor, Offset(cx - d1, cy - d1), Offset(cx - d0, cy - d0), strokeWidth = 3f, cap = StrokeCap.Round)
      drawLine(hmColor, Offset(cx + d1, cy - d1), Offset(cx + d0, cy - d0), strokeWidth = 3f, cap = StrokeCap.Round)
      drawLine(hmColor, Offset(cx - d1, cy + d1), Offset(cx - d0, cy + d0), strokeWidth = 3f, cap = StrokeCap.Round)
      drawLine(hmColor, Offset(cx + d1, cy + d1), Offset(cx + d0, cy + d0), strokeWidth = 3f, cap = StrokeCap.Round)
    }
  }
}

@Composable
private fun TopCombatStatusBar(
  uiState: CombatUiState,
  settings: GameSettings,
  onToggleCamera: () -> Unit,
  onToggleShoulder: () -> Unit,
  onPause: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left: Mode / Scoreboard Pill
    Surface(
      color = Color(0xCC0B1021),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.border(1.dp, Color(0x4400E5FF), RoundedCornerShape(12.dp))
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (uiState.isTrainingMode) {
          Text(
            text = "ТРЕНИРОВКА • СЧЁТ: ${uiState.localKills}",
            color = Color(0xFF00E5FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        } else {
          val wScore = uiState.activeRoom?.whiteScore ?: 0L
          val bScore = uiState.activeRoom?.blackScore ?: 0L
          val limit = uiState.activeRoom?.scoreLimit ?: 15L
          Text(
            text = "БЕЛЫЕ $wScore : $bScore ЧЁРНЫЕ (ДО $limit)",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "• #${uiState.activeRoomCode}",
            color = Color(0xFFFFAB00),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // Center-Right: Camera Mode Button (1st Person / 3rd Person) + Shoulder Swap + FPS/Ping + Pause
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (settings.showFps || settings.showNetworkPing) {
        Surface(
          color = Color(0xAA0B1021),
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            if (settings.showFps) {
              Text(
                text = "${uiState.currentFps} FPS",
                color = Color(0xFF00E676),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
            if (settings.showNetworkPing && !uiState.isTrainingMode) {
              Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "Ping",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(13.dp)
              )
              Text(
                text = "${uiState.currentPingMs} ms",
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp
              )
            }
          }
        }
      }

      // Dedicated Camera Perspective Toggle Button (1P <-> 3P)
      Surface(
        color = Color(0xDD1E293B),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(10.dp))
          .clickable { onToggleCamera() }
          .testTag("camera_toggle_button")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Icon(
            imageVector = Icons.Default.FlipCameraAndroid,
            contentDescription = "Смена камеры",
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = if (uiState.perspective == CameraPerspective.FIRST_PERSON) "1-Е ЛИЦО" else "3-Е ЛИЦО",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Shoulder Swap Button (visible in 3rd Person Mode)
      if (uiState.perspective == CameraPerspective.THIRD_PERSON) {
        Surface(
          color = Color(0xDD1E293B),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .border(1.dp, Color(0x8894A3B8), RoundedCornerShape(10.dp))
            .clickable { onToggleShoulder() }
            .testTag("shoulder_toggle_button")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.SwapHoriz,
              contentDescription = "Смена плеча",
              tint = Color(0xFFFFAB00),
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = if (uiState.shoulderSide.name == "RIGHT") "ПРАВ" else "ЛЕВ",
              color = Color.White,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      // Pause Button
      Surface(
        color = Color(0xDD1E293B),
        shape = CircleShape,
        modifier = Modifier
          .size(40.dp)
          .border(1.dp, Color(0x6694A3B8), CircleShape)
          .clickable { onPause() }
          .testTag("pause_button")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Pause,
            contentDescription = "Пауза",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun BottomHealthAndWeaponBar(
  uiState: CombatUiState,
  onSwitchWeapon: (WeaponSpec) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = Color(0xCC0B1021),
    shape = RoundedCornerShape(14.dp),
    modifier = modifier
      .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(14.dp))
  ) {
    Column(
      modifier = Modifier
        .padding(horizontal = 14.dp, vertical = 8.dp)
        .width(290.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Health & Ammo Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "HP ${uiState.health}",
            color = if (uiState.health > 35) Color(0xFF00E676) else Color(0xFFFF1744),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
          )
          Spacer(modifier = Modifier.width(8.dp))
          LinearProgressIndicator(
            progress = { (uiState.health / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
              .width(90.dp)
              .height(7.dp)
              .clip(RoundedCornerShape(4.dp)),
            color = if (uiState.health > 35) Color(0xFF00E676) else Color(0xFFFF1744),
            trackColor = Color(0xFF1E293B)
          )
        }

        Text(
          text = if (uiState.isReloading) {
            "ПЕРЕЗАРЯДКА..."
          } else {
            "${uiState.ammo} / ${uiState.weapon.magSize}"
          },
          color = if (uiState.ammo <= 5) Color(0xFFFFAB00) else Color(0xFF00E5FF),
          fontSize = 14.sp,
          fontWeight = FontWeight.Black
        )
      }

      // Quick Weapon Archetype Selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        WeaponSpec.entries.forEach { spec ->
          val selected = uiState.weapon == spec
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .background(if (selected) Color(0x3300E5FF) else Color(0xFF1E293B))
              .border(
                width = 1.dp,
                color = if (selected) Color(0xFF00E5FF) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
              )
              .clickable { onSwitchWeapon(spec) }
              .padding(vertical = 5.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = when (spec) {
                WeaponSpec.PULSE_RIFLE -> "AR-8"
                WeaponSpec.RAIL_PISTOL -> "RAIL"
                WeaponSpec.SHOTGUN_ROOK -> "ROOK"
              },
              color = if (selected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CombatActionButtonsCluster(
  uiState: CombatUiState,
  btnScale: Float,
  btnAlpha: Float,
  onFireHeldChange: (Boolean) -> Unit,
  onReload: () -> Unit,
  onJump: () -> Unit,
  onToggleAim: () -> Unit,
  onToggleSprint: () -> Unit,
  modifier: Modifier = Modifier
) {
  val fireSize = (78f * btnScale).dp
  val subSize = (48f * btnScale).dp

  Column(
    modifier = modifier.alpha(btnAlpha),
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Upper Row: Aim (ADS), Reload, Jump
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      // Sprint Toggle
      Surface(
        color = if (uiState.isSprinting) Color(0xDD00E5FF) else Color(0xCC1E293B),
        shape = CircleShape,
        modifier = Modifier
          .size(subSize)
          .border(1.dp, Color(0xFF00E5FF), CircleShape)
          .clickable { onToggleSprint() }
          .testTag("sprint_button")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
            contentDescription = "Бег",
            tint = if (uiState.isSprinting) Color(0xFF0B1021) else Color.White,
            modifier = Modifier.size(22.dp)
          )
        }
      }

      // Aim / ADS Button
      Surface(
        color = if (uiState.isAiming) Color(0xDDFFAB00) else Color(0xCC1E293B),
        shape = CircleShape,
        modifier = Modifier
          .size(subSize)
          .border(1.dp, Color(0xFFFFAB00), CircleShape)
          .clickable { onToggleAim() }
          .testTag("aim_button")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.CenterFocusStrong,
            contentDescription = "Прицел",
            tint = if (uiState.isAiming) Color(0xFF0B1021) else Color(0xFFFFAB00),
            modifier = Modifier.size(22.dp)
          )
        }
      }

      // Jump Button
      Surface(
        color = Color(0xCC1E293B),
        shape = CircleShape,
        modifier = Modifier
          .size(subSize)
          .border(1.dp, Color(0xFF94A3B8), CircleShape)
          .clickable { onJump() }
          .testTag("jump_button")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.KeyboardDoubleArrowUp,
            contentDescription = "Прыжок",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
          )
        }
      }
    }

    // Lower Row: Reload Button + Primary Fire Trigger
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Reload Button
      Surface(
        color = if (uiState.isReloading) Color(0xDDFFAB00) else Color(0xCC1E293B),
        shape = CircleShape,
        modifier = Modifier
          .size(subSize)
          .border(1.dp, Color(0xFF00E5FF), CircleShape)
          .clickable { onReload() }
          .testTag("reload_button")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Cached,
            contentDescription = "Перезарядка",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
          )
        }
      }

      // Primary Fire Trigger (Supports Tap & Continuous Hold)
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(fireSize)
          .clip(CircleShape)
          .background(if (uiState.isFiringHeld) Color(0xFFFF1744) else Color(0xDD00E5FF))
          .border(3.dp, Color.White, CircleShape)
          .pointerInput(Unit) {
            detectTapGestures(
              onPress = {
                onFireHeldChange(true)
                tryAwaitRelease()
                onFireHeldChange(false)
              }
            )
          }
          .testTag("fire_button")
      ) {
        Icon(
          imageVector = Icons.Default.MyLocation,
          contentDescription = "Огонь",
          tint = Color(0xFF0B1021),
          modifier = Modifier.size(34.dp)
        )
      }
    }
  }
}

@Composable
private fun PauseMenuDialog(
  uiState: CombatUiState,
  onResume: () -> Unit,
  onToggleCamera: () -> Unit,
  onOpenSettings: () -> Unit,
  onExitMatch: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xAA050811)),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = Color(0xFF0F172A),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier
        .width(340.dp)
        .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "ПАУЗА • CHESS ARENA",
          style = MaterialTheme.typography.titleLarge,
          color = Color.White,
          fontWeight = FontWeight.Black
        )
        Text(
          text = if (uiState.isTrainingMode) {
            "Режим тренировки • Фраги: ${uiState.localKills}"
          } else {
            "Комната: #${uiState.activeRoomCode} • Игроков: ${uiState.roomPlayers.size}"
          },
          color = Color(0xFF94A3B8),
          fontSize = 13.sp
        )

        Button(
          onClick = onResume,
          modifier = Modifier.fillMaxWidth().testTag("resume_match_button"),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF0B1021))
        ) {
          Text("ПРОДОЛЖИТЬ БОЙ", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
          onClick = onToggleCamera,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = if (uiState.perspective == CameraPerspective.FIRST_PERSON) {
              "Переключить на вид от 3-го лица"
            } else {
              "Переключить на вид от 1-го лица"
            },
            color = Color.White
          )
        }

        OutlinedButton(
          onClick = onOpenSettings,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("НАСТРОЙКИ ИГРЫ", color = Color(0xFF00E5FF))
        }

        OutlinedButton(
          onClick = onExitMatch,
          modifier = Modifier.fillMaxWidth().testTag("leave_match_button")
        ) {
          Text("ВЫЙТИ В ГЛАВНОЕ МЕНЮ", color = Color(0xFFFF5252))
        }
      }
    }
  }
}
