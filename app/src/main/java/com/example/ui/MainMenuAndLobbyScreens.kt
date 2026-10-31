package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MatchRoom
import com.example.engine.ChessPieceType
import com.example.engine.Vec3
import com.example.settings.GameSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MainMenuScreen(
  uiState: CombatUiState,
  onQuickPlay: () -> Unit,
  onNavigate: (AppScreen) -> Unit,
  onStartTraining: (String) -> Unit,
  onUpdateNickname: (String) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier
) {
  var editingNickname by remember { mutableStateOf(false) }
  var nicknameDraft by remember(uiState.userProfile.nickname) {
    mutableStateOf(uiState.userProfile.nickname)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.radialGradient(
          colors = listOf(Color(0xFF13203B), Color(0xFF070B14), Color(0xFF04060C))
        )
      )
      .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    // Animated 3D Rotating Chess Piece Showcase in Background Center
    Animated3DChessShowcase(
      modifier = Modifier
        .fillMaxSize()
        .padding(end = 180.dp)
    )

    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left Column: Brand Title, Player Profile Card & Primary "ИГРАТЬ" Action
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Header & Callsign
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Surface(
              color = Color(0x2200E5FF),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
            ) {
              Text(
                text = "3D TACTICAL ARENA",
                color = Color(0xFF00E5FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
            Text(
              text = "ШАХМАТЫ 3: ШУТЕР",
              color = Color(0xFF94A3B8),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Text(
            text = "CHESS ARENA",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
          )

          // Player Profile Chip
          Surface(
            color = Color(0xCC0F172A),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(12.dp))
              .clickable { editingNickname = !editingNickname }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Column {
                Text(
                  text = "БОЕЦ: ${uiState.userProfile.nickname}",
                  color = Color.White,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Фраги: ${uiState.userProfile.kills} • Побед: ${uiState.userProfile.wins} • Матчей: ${uiState.userProfile.matchesPlayed}",
                  color = Color(0xFF94A3B8),
                  fontSize = 11.sp
                )
              }
              IconButton(
                onClick = onSignOut,
                modifier = Modifier.size(32.dp).testTag("sign_out_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Logout,
                  contentDescription = "Выйти из аккаунта",
                  tint = Color(0xFF94A3B8),
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }

          if (editingNickname) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedTextField(
                value = nicknameDraft,
                onValueChange = { nicknameDraft = it.take(24) },
                singleLine = true,
                label = { Text("Никнейм") },
                modifier = Modifier.width(190.dp)
              )
              Button(
                onClick = {
                  onUpdateNickname(nicknameDraft)
                  editingNickname = false
                }
              ) {
                Text("ОК")
              }
            }
          }
        }

        // Bottom-Left: Large Primary "ИГРАТЬ" Button & Status Banner
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (uiState.statusBanner != null) {
            Surface(
              color = Color(0xCC3B1219),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.border(1.dp, Color(0xFFFF5252), RoundedCornerShape(10.dp))
            ) {
              Text(
                text = uiState.statusBanner,
                color = Color(0xFFFF8A80),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }

          Button(
            onClick = onQuickPlay,
            enabled = !uiState.isBusy,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF00E5FF),
              contentColor = Color(0xFF070B14)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .widthIn(min = 230.dp, max = 300.dp)
              .height(58.dp)
              .testTag("menu_play_button")
          ) {
            if (uiState.isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFF070B14),
                strokeWidth = 2.5.dp
              )
            } else {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "1. ИГРАТЬ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
              )
            }
          }
        }
      }

      // Right Column: Minimalist Tactical Menu Items (Items 2..8)
      Column(
        modifier = Modifier
          .width(275.dp)
          .fillMaxHeight()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
      ) {
        MenuNavCard(
          title = "2. Найти матч",
          subtitle = "Список открытых сетевых комнат",
          icon = Icons.Default.Public,
          accent = Color(0xFF00E5FF),
          tag = "menu_find_match",
          onClick = { onNavigate(AppScreen.FIND_MATCH) }
        )
        MenuNavCard(
          title = "3. Создать комнату",
          subtitle = "Командный бой или Каждый сам за себя",
          icon = Icons.Default.AddCircleOutline,
          accent = Color(0xFF00E676),
          tag = "menu_create_room",
          onClick = { onNavigate(AppScreen.CREATE_ROOM) }
        )
        MenuNavCard(
          title = "4. Присоединиться по коду",
          subtitle = "Вход в приватный или командный матч",
          icon = Icons.Default.Numbers,
          accent = Color(0xFFFFAB00),
          tag = "menu_join_code",
          onClick = { onNavigate(AppScreen.JOIN_BY_CODE) }
        )
        MenuNavCard(
          title = "5. Тренировка",
          subtitle = "Одиночная 3D-арена, тест камер и оружия",
          icon = Icons.Default.FitnessCenter,
          accent = Color(0xFF38BDF8),
          tag = "menu_training",
          onClick = { onStartTraining("cyber_obsidian") }
        )
        MenuNavCard(
          title = "6. Настройки",
          subtitle = "Графика, камера, звук, прицел, сеть",
          icon = Icons.Default.Settings,
          accent = Color(0xFFE2E8F0),
          tag = "menu_settings",
          onClick = { onNavigate(AppScreen.SETTINGS) }
        )
        MenuNavCard(
          title = "7. Управление",
          subtitle = "Сенсорная раскладка, размер и чувствительность",
          icon = Icons.Default.Gamepad,
          accent = Color(0xFFC084FC),
          tag = "menu_controls",
          onClick = { onNavigate(AppScreen.CONTROLS_INFO) }
        )
        MenuNavCard(
          title = "8. Об игре",
          subtitle = "Механики, камеры и архитектура сервера",
          icon = Icons.Default.Info,
          accent = Color(0xFF94A3B8),
          tag = "menu_about",
          onClick = { onNavigate(AppScreen.ABOUT_GAME) }
        )
      }
    }
  }
}

@Composable
private fun MenuNavCard(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accent: Color,
  tag: String,
  onClick: () -> Unit
) {
  Surface(
    color = Color(0xCC0F172A),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .testTag(tag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .background(accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
      }
      Column {
        Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 10.sp)
      }
    }
  }
}

@Composable
private fun Animated3DChessShowcase(modifier: Modifier = Modifier) {
  val infinite = rememberInfiniteTransition(label = "chess_pedestal")
  val angleDeg by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 16000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "yaw"
  )
  val path = remember { Path() }

  Canvas(modifier = modifier) {
    val cx = size.width * 0.42f
    val cy = size.height * 0.56f
    val rad = (angleDeg * PI / 180.0).toFloat()

    // Soft glowing volumetric halo
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color(0x3300E5FF), Color(0x1100E5FF), Color.Transparent),
        center = Offset(cx, cy - 30f),
        radius = size.minDimension * 0.48f
      ),
      radius = size.minDimension * 0.48f,
      center = Offset(cx, cy - 30f)
    )

    // Draw rotating 3D wireframe + shaded Chess King & Pedestal rings
    val rings = listOf(
      110f to 70f,
      95f to 52f,
      62f to 36f,
      46f to -45f,
      72f to -68f,
      54f to -90f,
      76f to -130f,
      24f to -150f
    )
    val segs = 10
    for (rIdx in 0 until rings.size - 1) {
      val (r0, y0) = rings[rIdx]
      val (r1, y1) = rings[rIdx + 1]
      for (s in 0 until segs) {
        val a0 = rad + (2.0 * PI * s / segs).toFloat()
        val a1 = rad + (2.0 * PI * (s + 1) / segs).toFloat()
        val p00 = Offset(cx + r0 * cos(a0), cy + y0 + r0 * sin(a0) * 0.28f)
        val p10 = Offset(cx + r0 * cos(a1), cy + y0 + r0 * sin(a1) * 0.28f)
        val p11 = Offset(cx + r1 * cos(a1), cy + y1 + r1 * sin(a1) * 0.28f)
        val p01 = Offset(cx + r1 * cos(a0), cy + y1 + r1 * sin(a0) * 0.28f)

        path.reset()
        path.moveTo(p00.x, p00.y)
        path.lineTo(p10.x, p10.y)
        path.lineTo(p11.x, p11.y)
        path.lineTo(p01.x, p01.y)
        path.close()

        val shade = (0.25f + 0.25f * sin(a0)).coerceIn(0.1f, 0.55f)
        drawPath(path, color = Color(0xFF1E293B).copy(alpha = shade))
        drawPath(
          path,
          color = if (rIdx == 1 || rIdx == 4) Color(0xAA00E5FF) else Color(0x4494A3B8),
          style = Stroke(width = 1.4f)
        )
      }
    }

    // Crown Cross on top of the 3D King sculpture
    drawLine(
      color = Color(0xFF00E5FF),
      start = Offset(cx, cy - 150f),
      end = Offset(cx, cy - 195f),
      strokeWidth = 5f
    )
    drawLine(
      color = Color(0xFF00E5FF),
      start = Offset(cx - 20f * cos(rad), cy - 175f),
      end = Offset(cx + 20f * cos(rad), cy - 175f),
      strokeWidth = 5f
    )
  }
}

// --- FIND MATCH SCREEN ---

@Composable
fun FindMatchScreen(
  uiState: CombatUiState,
  onJoinRoom: (String) -> Unit,
  onCreateNewRoom: () -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  ScreenShell(title = "НАЙТИ СЕТЕВОЙ МАТЧ", onBack = onBack) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Доступные публичные комнаты (${uiState.publicRooms.size})",
        color = Color(0xFF94A3B8),
        fontSize = 14.sp
      )
      Button(
        onClick = onCreateNewRoom,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF0B1021))
      ) {
        Text("Создать свою комнату", fontWeight = FontWeight.Bold)
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (uiState.publicRooms.isEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(24.dp).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Активных публичных матчей сейчас нет",
            color = Color.White,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Нажмите «Создать свою комнату», чтобы открыть арену для других игроков.",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
          )
        }
      }
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uiState.publicRooms, key = { it.roomCode }) { room ->
          RoomListItemCard(room = room, onJoin = { onJoinRoom(room.roomCode) })
        }
      }
    }
  }
}

@Composable
private fun RoomListItemCard(room: MatchRoom, onJoin: () -> Unit) {
  Surface(
    color = Color(0xFF0F172A),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(12.dp))
  ) {
    Row(
      modifier = Modifier.padding(14.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "КОМНАТА #${room.roomCode} • Хост: ${room.hostName}",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp
        )
        val modeLabel = if (room.mode == "ffa") "Каждый сам за себя" else "Командный бой (Белые vs Чёрные)"
        Text(
          text = "$modeLabel • Игроков: ${room.playerCount}/12 • До ${room.scoreLimit} очков",
          color = Color(0xFF94A3B8),
          fontSize = 12.sp
        )
      }
      Button(
        onClick = onJoin,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF0B1021))
      ) {
        Text("ВОЙТИ", fontWeight = FontWeight.Bold)
      }
    }
  }
}

// --- CREATE ROOM SCREEN ---

@Composable
fun CreateRoomScreen(
  isBusy: Boolean,
  onCreateRoom: (mode: String, isPublic: Boolean, scoreLimit: Int, arenaTheme: String) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  var mode by remember { mutableStateOf("tdm") }
  var isPublic by remember { mutableStateOf(true) }
  var scoreLimit by remember { mutableIntStateOf(15) }
  var arenaTheme by remember { mutableStateOf("cyber_obsidian") }

  ScreenShell(title = "СОЗДАТЬ СЕТЕВУЮ КОМНАТУ", onBack = onBack) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text("Режим игры:", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
          selected = mode == "tdm",
          onClick = { mode = "tdm" },
          label = { Text("Командный бой (2 команды)") }
        )
        FilterChip(
          selected = mode == "ffa",
          onClick = { mode = "ffa" },
          label = { Text("Каждый сам за себя") }
        )
      }

      Text("Оформление 3D-шахматной арены:", color = Color.White, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
          selected = arenaTheme == "cyber_obsidian",
          onClick = { arenaTheme = "cyber_obsidian" },
          label = { Text("Кибер-Обсидиан") }
        )
        FilterChip(
          selected = arenaTheme == "royal_marble",
          onClick = { arenaTheme = "royal_marble" },
          label = { Text("Королевский Мрамор") }
        )
        FilterChip(
          selected = arenaTheme == "neon_void",
          onClick = { arenaTheme = "neon_void" },
          label = { Text("Неоновый Вакуум") }
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Лимит очков для победы: $scoreLimit", color = Color.White, fontWeight = FontWeight.Bold)
        Slider(
          value = scoreLimit.toFloat(),
          onValueChange = { scoreLimit = it.toInt() },
          valueRange = 5f..40f,
          steps = 6,
          modifier = Modifier.width(220.dp)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text("Публичная комната", color = Color.White, fontWeight = FontWeight.Bold)
          Text("Видна другим игрокам в разделе «Найти матч»", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        Switch(checked = isPublic, onCheckedChange = { isPublic = it })
      }

      Button(
        onClick = { onCreateRoom(mode, isPublic, scoreLimit, arenaTheme) },
        enabled = !isBusy,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color(0xFF0B1021)),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("confirm_create_room_button")
      ) {
        Text("СОЗДАТЬ КОМНАТУ И ПОЛУЧИТЬ КОД", fontWeight = FontWeight.Black)
      }
    }
  }
}

// --- JOIN BY CODE SCREEN ---

@Composable
fun JoinByCodeScreen(
  uiState: CombatUiState,
  onJoinCode: (String) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  var code by remember { mutableStateOf("") }

  ScreenShell(title = "ПРИСОЕДИНИТЬСЯ ПО КОДУ", onBack = onBack) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        text = "Введите 6-значный код комнаты, полученный от создателя матча:",
        color = Color(0xFF94A3B8),
        fontSize = 14.sp
      )
      OutlinedTextField(
        value = code,
        onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(10) },
        label = { Text("Код комнаты (например, ARENA1)") },
        singleLine = true,
        modifier = Modifier.width(280.dp).testTag("room_code_input")
      )
      if (uiState.statusBanner != null) {
        Text(text = uiState.statusBanner, color = Color(0xFFFF5252), fontSize = 13.sp)
      }
      Button(
        onClick = { onJoinCode(code) },
        enabled = code.length >= 4 && !uiState.isBusy,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFAB00), contentColor = Color(0xFF0B1021)),
        modifier = Modifier.width(280.dp).height(48.dp).testTag("confirm_join_code_button")
      ) {
        Text("ПОДКЛЮЧИТЬСЯ К МАТЧУ", fontWeight = FontWeight.Black)
      }
    }
  }
}

// --- LOBBY WAITING SCREEN ---

@Composable
fun LobbyWaitingScreen(
  uiState: CombatUiState,
  currentUserId: String,
  onStartMatch: () -> Unit,
  onLeaveLobby: () -> Unit
) {
  BackHandler(onBack = onLeaveLobby)
  val isHost = uiState.activeRoom?.hostId == currentUserId

  ScreenShell(title = "ЛОББИ КОМНАТЫ #${uiState.activeRoomCode}", onBack = onLeaveLobby) {
    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Left: Room Info & Start Action
      Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .weight(0.45f)
          .fillMaxHeight()
          .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(14.dp))
      ) {
        Column(
          modifier = Modifier.padding(16.dp).fillMaxSize(),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "КОД ДЛЯ ДРУЗЕЙ: ${uiState.activeRoomCode}",
              color = Color(0xFFFFAB00),
              fontSize = 18.sp,
              fontWeight = FontWeight.Black
            )
            Text(
              text = "Режим: ${if (uiState.activeRoom?.mode == "ffa") "Каждый сам за себя" else "Командный бой"}",
              color = Color.White,
              fontSize = 14.sp
            )
            Text(
              text = "Подключено игроков: ${uiState.roomPlayers.size}",
              color = Color(0xFF00E5FF),
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
          }

          if (isHost) {
            Button(
              onClick = onStartMatch,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color(0xFF0B1021)),
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("start_multiplayer_match_button")
            ) {
              Text("НАЧАТЬ БОЙ НА АРЕНЕ", fontWeight = FontWeight.Black)
            }
          } else {
            Text(
              text = "Ожидание запуска матча создателем комнаты (${uiState.activeRoom?.hostName ?: "Хост"})...",
              color = Color(0xFF94A3B8),
              fontSize = 13.sp
            )
          }
        }
      }

      // Right: Live Connected Players List
      Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.weight(0.55f).fillMaxHeight()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "УЧАСТНИКИ МАТЧА",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
          Spacer(modifier = Modifier.height(8.dp))
          LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(uiState.roomPlayers, key = { it.userId }) { p ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = p.nickname + (if (p.userId == currentUserId) " (Вы)" else ""),
                  color = Color.White,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "${p.team} • ${p.weapon}",
                  color = if (p.team == "BLACK") Color(0xFFFFAB00) else Color(0xFF00E5FF),
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        }
      }
    }
  }
}

// --- MATCH RESULTS SCREEN ---

@Composable
fun MatchResultsScreen(
  uiState: CombatUiState,
  onReturnToMenu: () -> Unit
) {
  BackHandler(onBack = onReturnToMenu)
  val room = uiState.activeRoom
  ScreenShell(title = "ИТОГИ МАТЧА • CHESS ARENA", onBack = onReturnToMenu) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(
        imageVector = Icons.Default.EmojiEvents,
        contentDescription = null,
        tint = Color(0xFFFFAB00),
        modifier = Modifier.size(48.dp)
      )
      Text(
        text = "ПОБЕДИТЕЛЬ: ${room?.winner?.ifBlank { "ЗАВЕРШЕНО" } ?: "ЗАВЕРШЕНО"}",
        color = Color(0xFFFFAB00),
        fontSize = 24.sp,
        fontWeight = FontWeight.Black
      )
      Text(
        text = "Счёт команд — БЕЛЫЕ ${room?.whiteScore ?: 0} : ${room?.blackScore ?: 0} ЧЁРНЫЕ",
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )

      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val sorted = uiState.roomPlayers.sortedByDescending { it.kills }
        items(sorted, key = { it.userId }) { p ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
              .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(text = "${p.nickname} [${p.team}]", color = Color.White, fontWeight = FontWeight.Bold)
            Text(text = "K: ${p.kills} / D: ${p.deaths}", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
          }
        }
      }

      Button(
        onClick = onReturnToMenu,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF0B1021)),
        modifier = Modifier.width(260.dp).height(48.dp)
      ) {
        Text("ВЕРНУТЬСЯ В ГЛАВНОЕ МЕНЮ", fontWeight = FontWeight.Black)
      }
    }
  }
}

// --- CONTROLS CUSTOMIZATION & INFO SCREEN ---

@Composable
fun ControlsCustomizationScreen(
  settings: GameSettings,
  onSaveSettings: (GameSettings) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  var draft by remember(settings) { mutableStateOf(settings) }

  ScreenShell(title = "МОБИЛЬНОЕ УПРАВЛЕНИЕ И РАСКЛАДКА", onBack = onBack) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = "Левая половина экрана — виртуальный джойстик движения. Правая половина — свободный поворот камеры, прицеливание, стрельба, перезарядка, прыжок и мгновенное переключение камеры (1-е / 3-е лицо).",
        color = Color(0xFF94A3B8),
        fontSize = 13.sp
      )

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
          selected = !draft.leftHandedMode,
          onClick = {
            draft = draft.copy(leftHandedMode = false)
            onSaveSettings(draft)
          },
          label = { Text("Правосторонняя раскладка (Стандарт)") }
        )
        FilterChip(
          selected = draft.leftHandedMode,
          onClick = {
            draft = draft.copy(leftHandedMode = true)
            onSaveSettings(draft)
          },
          label = { Text("Левосторонняя (Для левшей)") }
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Размер боевых кнопок: ${"%.0f".format(draft.buttonScale * 100)}%", color = Color.White)
        Slider(
          value = draft.buttonScale,
          onValueChange = {
            draft = draft.copy(buttonScale = it)
            onSaveSettings(draft)
          },
          valueRange = 0.75f..1.35f,
          modifier = Modifier.width(220.dp)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Прозрачность кнопок HUD: ${"%.0f".format(draft.buttonOpacity * 100)}%", color = Color.White)
        Slider(
          value = draft.buttonOpacity,
          onValueChange = {
            draft = draft.copy(buttonOpacity = it)
            onSaveSettings(draft)
          },
          valueRange = 0.35f..1.0f,
          modifier = Modifier.width(220.dp)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Чувствительность джойстика: ${"%.2f".format(draft.joystickSensitivity)}", color = Color.White)
        Slider(
          value = draft.joystickSensitivity,
          onValueChange = {
            draft = draft.copy(joystickSensitivity = it)
            onSaveSettings(draft)
          },
          valueRange = 0.5f..1.8f,
          modifier = Modifier.width(220.dp)
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Чувствительность поворота камеры: ${"%.2f".format(draft.touchAimSensitivity)}", color = Color.White)
        Slider(
          value = draft.touchAimSensitivity,
          onValueChange = {
            draft = draft.copy(touchAimSensitivity = it)
            onSaveSettings(draft)
          },
          valueRange = 0.4f..2.2f,
          modifier = Modifier.width(220.dp)
        )
      }
    }
  }
}

// --- ABOUT GAME SCREEN ---

@Composable
fun AboutGameScreen(onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  ScreenShell(title = "ОБ ИГРЕ • CHESS ARENA", onBack = onBack) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text(
        text = "CHESS ARENA (Шахматы 3: Шутер) — 3D мультиплеерный тактический шутер на гигантской шахматной арене 8×8.",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
      )
      Text(
        text = "• Два полноценных режима камеры: вид от 1-го лица (с 3D-моделью оружия, отдачей и прицелом) и вид от 3-го лица (с плавным следованием, защитой от проваливания сквозь фигуры/стены, сменой правого/левого плеча и точным параллакс-рейкастом из центра прицела к дулу оружия).",
        color = Color(0xFFE2E8F0),
        fontSize = 13.sp
      )
      Text(
        text = "• Объёмные 3D-шахматные фигуры: Король, Ферзь, Ладья, Слон, Конь и Пешка созданы процедурной 3D-геометрией с физическими коллайдерами, отражениями на глянцевых клетках и металлическими искрами при попаданиях.",
        color = Color(0xFFE2E8F0),
        fontSize = 13.sp
      )
      Text(
        text = "• Настоящий сетевой мультиплеер: синхронизация комнат, кодов доступа, позиций, здоровья и таблицы лидеров работает через облачный сервер Firebase Firestore в реальном времени, а также в проект включён выделенный Node.js WebSocket сервер (server/chess-arena-server.js).",
        color = Color(0xFF00E5FF),
        fontSize = 13.sp
      )
    }
  }
}

@Composable
fun ScreenShell(
  title: String,
  onBack: () -> Unit,
  content: @Composable () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF070B14))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 18.dp, vertical = 12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Surface(
        color = Color(0xFF1E293B),
        shape = CircleShape,
        modifier = Modifier
          .size(40.dp)
          .clickable { onBack() }
          .testTag("back_button")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад",
            tint = Color.White
          )
        }
      }
      Text(
        text = title,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black
      )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Box(modifier = Modifier.fillMaxSize()) {
      content()
    }
  }
}
