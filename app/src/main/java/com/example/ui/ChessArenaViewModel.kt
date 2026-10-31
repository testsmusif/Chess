package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundSynthesizer
import com.example.data.ChessArenaRepository
import com.example.data.MatchRoom
import com.example.data.RoomPlayer
import com.example.data.UserProfile
import com.example.engine.BulletTracer3D
import com.example.engine.ChessArenaWorld
import com.example.engine.HitSurfaceType
import com.example.engine.ImpactDecal3D
import com.example.engine.Math3D
import com.example.engine.Particle3D
import com.example.engine.PlayerRenderState
import com.example.engine.Ray3D
import com.example.engine.TrainingDrone
import com.example.engine.Vec3
import com.example.settings.CameraPerspective
import com.example.settings.GameSettings
import com.example.settings.SettingsRepository
import com.example.settings.ShoulderSide
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen {
  MAIN_MENU,
  FIND_MATCH,
  CREATE_ROOM,
  JOIN_BY_CODE,
  LOBBY_WAITING,
  ARENA_COMBAT,
  MATCH_RESULTS,
  SETTINGS,
  CONTROLS_INFO,
  ABOUT_GAME
}

enum class WeaponSpec(
  val id: String,
  val titleRu: String,
  val titleEn: String,
  val magSize: Int,
  val fireCooldownSec: Float,
  val reloadDurationSec: Float,
  val damage: Int,
  val recoilKick: Float,
  val tracerHex: Long
) {
  PULSE_RIFLE("PULSE_RIFLE", "Импульсный карабин AR-8", "Pulse Carbine AR-8", 30, 0.13f, 1.65f, 24, 0.35f, 0xFF00E5FF),
  RAIL_PISTOL("RAIL_PISTOL", "Рельсовый пистолет Q-12", "Rail Pistol Q-12", 12, 0.32f, 1.40f, 45, 0.62f, 0xFFFFAB00),
  SHOTGUN_ROOK("SHOTGUN_ROOK", "Тактический дробовик Ладья", "Rook Tactical Shotgun", 8, 0.68f, 2.10f, 68, 0.88f, 0xFFFF1744);

  companion object {
    fun fromId(id: String): WeaponSpec = entries.find { it.id == id } ?: PULSE_RIFLE
  }
}

data class DamagePopup(
  val id: Long,
  val amount: Int,
  val targetName: String,
  val isElimination: Boolean,
  var timer: Float = 0.9f
)

data class CombatUiState(
  val screen: AppScreen = AppScreen.MAIN_MENU,
  val isTrainingMode: Boolean = false,
  val activeRoomCode: String = "",
  val activeRoom: MatchRoom? = null,
  val roomPlayers: List<RoomPlayer> = emptyList(),
  val publicRooms: List<MatchRoom> = emptyList(),
  val userProfile: UserProfile = UserProfile(),
  val statusBanner: String? = null,
  val isBusy: Boolean = false,

  // Local Player Combat State
  val pos: Vec3 = Vec3(0f, 0f, -11.5f),
  val velY: Float = 0f,
  val isGrounded: Boolean = true,
  val yaw: Float = 0f,
  val pitch: Float = 0f,
  val smoothCamPos: Vec3 = Vec3(0f, 2.2f, -14.5f),
  val smoothCamYaw: Float = 0f,
  val smoothCamPitch: Float = 0f,
  val perspective: CameraPerspective = CameraPerspective.THIRD_PERSON,
  val shoulderSide: ShoulderSide = ShoulderSide.RIGHT,
  val isAiming: Boolean = false,
  val isSprinting: Boolean = false,
  val isFiringHeld: Boolean = false,
  val isPaused: Boolean = false,

  val weapon: WeaponSpec = WeaponSpec.PULSE_RIFLE,
  val ammo: Int = 30,
  val isReloading: Boolean = false,
  val reloadProgress: Float = 0f,
  val recoilOffset: Float = 0f,
  val muzzleFlash: Float = 0f,
  val hurtFlash: Float = 0f,
  val walkPhase: Float = 0f,

  val health: Int = 100,
  val localKills: Int = 0,
  val localDeaths: Int = 0,
  val respawnCountdown: Float = 0f,
  val hitMarkerAlpha: Float = 0f,
  val hitMarkerIsKill: Boolean = false,
  val damagePopups: List<DamagePopup> = emptyList(),
  val killFeed: List<String> = emptyList(),
  val currentFps: Int = 60,
  val currentPingMs: Int = 24
)

class ChessArenaViewModel(
  private val repository: ChessArenaRepository,
  private val settingsRepository: SettingsRepository,
  val currentUserId: String,
  private val defaultDisplayName: String
) : ViewModel() {

  val world = ChessArenaWorld()

  val settings: StateFlow<GameSettings> = settingsRepository.settingsFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = GameSettings()
    )

  private val _uiState = MutableStateFlow(CombatUiState())
  val uiState: StateFlow<CombatUiState> = _uiState.asStateFlow()

  val trainingDrones = mutableListOf<TrainingDrone>()
  val particles = mutableListOf<Particle3D>()
  val tracers = mutableListOf<BulletTracer3D>()
  val decals = mutableListOf<ImpactDecal3D>()

  private var moveInputX = 0f
  private var moveInputZ = 0f
  private var fireCooldown = 0f
  private var reloadTimer = 0f
  private var shotSeqCounter = 0L
  private var lastNetworkSyncTime = 0L
  private var lastKnownRemoteHealth = 100L

  private var gameLoopJob: Job? = null
  private var roomListenerJob: Job? = null
  private var playersListenerJob: Job? = null
  private var publicRoomsJob: Job? = null

  init {
    viewModelScope.launch {
      repository.ensureUserProfile(defaultDisplayName)
    }
    viewModelScope.launch {
      repository.observeOwnProfile()
        .catch { /* logged in repository */ }
        .collect { profile ->
          if (profile != null) {
            _uiState.value = _uiState.value.copy(userProfile = profile)
          }
        }
    }
    viewModelScope.launch {
      settings.collect { s ->
        _uiState.value = _uiState.value.copy(
          perspective = s.defaultPerspective,
          shoulderSide = s.shoulderSide
        )
      }
    }
  }

  fun navigateTo(screen: AppScreen) {
    SoundSynthesizer.playUiClick(settings.value)
    if (screen == AppScreen.FIND_MATCH) {
      startObservingPublicRooms()
    } else {
      publicRoomsJob?.cancel()
    }
    _uiState.value = _uiState.value.copy(
      screen = screen,
      statusBanner = null,
      isPaused = false
    )
  }

  fun clearBanner() {
    _uiState.value = _uiState.value.copy(statusBanner = null)
  }

  private fun startObservingPublicRooms() {
    publicRoomsJob?.cancel()
    publicRoomsJob = viewModelScope.launch {
      repository.observePublicRooms()
        .catch { e ->
          _uiState.value = _uiState.value.copy(statusBanner = e.localizedMessage ?: "Ошибка загрузки списка комнат")
        }
        .collect { rooms ->
          _uiState.value = _uiState.value.copy(publicRooms = rooms)
        }
    }
  }

  // --- TRAINING MODE (SINGLEPLAYER / OFFLINE) ---

  fun startTrainingMatch(arenaTheme: String = "cyber_obsidian") {
    SoundSynthesizer.playNotification(settings.value)
    roomListenerJob?.cancel()
    playersListenerJob?.cancel()

    trainingDrones.clear()
    trainingDrones.addAll(world.createInitialTrainingDrones())
    particles.clear()
    tracers.clear()
    decals.clear()

    val s = settings.value
    val startPos = Vec3(0f, 0f, -11.5f)
    _uiState.value = _uiState.value.copy(
      screen = AppScreen.ARENA_COMBAT,
      isTrainingMode = true,
      activeRoomCode = "TRAINING",
      activeRoom = MatchRoom(
        roomCode = "TRAINING",
        hostId = currentUserId,
        hostName = _uiState.value.userProfile.nickname,
        mode = "tdm",
        status = "playing",
        arenaTheme = arenaTheme
      ),
      roomPlayers = emptyList(),
      pos = startPos,
      velY = 0f,
      isGrounded = true,
      yaw = 0f,
      pitch = 0f,
      smoothCamPos = startPos + Vec3(0f, 2f, -3f),
      smoothCamYaw = 0f,
      smoothCamPitch = 0f,
      perspective = s.defaultPerspective,
      shoulderSide = s.shoulderSide,
      weapon = WeaponSpec.PULSE_RIFLE,
      ammo = WeaponSpec.PULSE_RIFLE.magSize,
      isReloading = false,
      health = 100,
      localKills = 0,
      localDeaths = 0,
      respawnCountdown = 0f,
      isPaused = false,
      killFeed = listOf("Режим тренировки: уничтожайте тактические шахматные дроны")
    )
    startArenaLoop()
  }

  // --- MULTIPLAYER ROOMS (FIREBASE FIRESTORE REAL-TIME) ---

  fun createMultiplayerRoom(
    mode: String,
    isPublic: Boolean,
    scoreLimit: Int,
    arenaTheme: String
  ) {
    SoundSynthesizer.playUiClick(settings.value)
    val rawCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
    val nickname = _uiState.value.userProfile.nickname.ifBlank { defaultDisplayName }
    _uiState.value = _uiState.value.copy(isBusy = true, statusBanner = null)

    viewModelScope.launch {
      val result = repository.createRoom(
        roomCode = rawCode,
        hostName = nickname,
        mode = mode,
        isPublic = isPublic,
        scoreLimit = scoreLimit,
        arenaTheme = arenaTheme,
        weapon = _uiState.value.weapon.id
      )
      result.onSuccess { code ->
        _uiState.value = _uiState.value.copy(
          isBusy = false,
          isTrainingMode = false,
          activeRoomCode = code,
          screen = AppScreen.LOBBY_WAITING
        )
        subscribeToActiveRoom(code)
      }.onFailure { err ->
        _uiState.value = _uiState.value.copy(
          isBusy = false,
          statusBanner = err.localizedMessage ?: "Не удалось создать комнату"
        )
      }
    }
  }

  fun joinMultiplayerRoom(codeInput: String) {
    SoundSynthesizer.playUiClick(settings.value)
    val code = codeInput.trim().uppercase()
    if (code.length < 4) {
      _uiState.value = _uiState.value.copy(statusBanner = "Введите корректный код комнаты (например, ARENA1)")
      return
    }
    val nickname = _uiState.value.userProfile.nickname.ifBlank { defaultDisplayName }
    _uiState.value = _uiState.value.copy(isBusy = true, statusBanner = null)

    viewModelScope.launch {
      val result = repository.joinRoomByCode(
        rawCode = code,
        nickname = nickname,
        weapon = _uiState.value.weapon.id
      )
      result.onSuccess { room ->
        _uiState.value = _uiState.value.copy(
          isBusy = false,
          isTrainingMode = false,
          activeRoomCode = room.roomCode,
          activeRoom = room,
          screen = if (room.status == "playing") AppScreen.ARENA_COMBAT else AppScreen.LOBBY_WAITING
        )
        subscribeToActiveRoom(room.roomCode)
        if (room.status == "playing") {
          enterMultiplayerCombat(room)
        }
      }.onFailure { err ->
        _uiState.value = _uiState.value.copy(
          isBusy = false,
          statusBanner = err.localizedMessage ?: "Не удалось подключиться к комнате"
        )
      }
    }
  }

  fun quickFindMatch() {
    SoundSynthesizer.playUiClick(settings.value)
    val openRoom = _uiState.value.publicRooms.firstOrNull { it.status != "finished" && it.playerCount < 12 }
    if (openRoom != null) {
      joinMultiplayerRoom(openRoom.roomCode)
    } else {
      createMultiplayerRoom(mode = "tdm", isPublic = true, scoreLimit = 15, arenaTheme = "cyber_obsidian")
    }
  }

  private fun subscribeToActiveRoom(roomCode: String) {
    roomListenerJob?.cancel()
    playersListenerJob?.cancel()
    lastKnownRemoteHealth = 100L

    roomListenerJob = viewModelScope.launch {
      repository.observeRoom(roomCode)
        .catch { e ->
          if (settings.value.connectionAlerts) {
            _uiState.value = _uiState.value.copy(statusBanner = "Сетевое предупреждение: ${e.localizedMessage}")
          }
        }
        .collect { room ->
          if (room == null) return@collect
          val prevStatus = _uiState.value.activeRoom?.status
          _uiState.value = _uiState.value.copy(activeRoom = room)

          if (room.status == "playing" && _uiState.value.screen == AppScreen.LOBBY_WAITING) {
            enterMultiplayerCombat(room)
          } else if (room.status == "finished" && prevStatus != "finished") {
            finishMatchAndShowResults(room)
          }
        }
    }

    playersListenerJob = viewModelScope.launch {
      repository.observeRoomPlayers(roomCode)
        .catch { /* logged in repository */ }
        .collect { players ->
          val me = players.find { it.userId == currentUserId }
          if (me != null) {
            // Check if an opponent dealt damage to us on the server
            if (me.health < lastKnownRemoteHealth && _uiState.value.respawnCountdown <= 0f) {
              val newHp = me.health.toInt().coerceAtLeast(0)
              _uiState.value = _uiState.value.copy(
                health = newHp,
                hurtFlash = 1.0f
              )
              if (newHp == 0) {
                handleLocalPlayerDeath(me.lastHitBy, players)
              }
            }
            lastKnownRemoteHealth = me.health
          }
          _uiState.value = _uiState.value.copy(roomPlayers = players)
        }
    }
  }

  fun hostStartMultiplayerMatch() {
    val code = _uiState.value.activeRoomCode
    if (code.isBlank()) return
    SoundSynthesizer.playNotification(settings.value)
    viewModelScope.launch {
      repository.startMatch(code)
    }
  }

  private fun enterMultiplayerCombat(room: MatchRoom) {
    SoundSynthesizer.playNotification(settings.value)
    trainingDrones.clear()
    particles.clear()
    tracers.clear()
    decals.clear()

    val me = _uiState.value.roomPlayers.find { it.userId == currentUserId }
    val team = me?.team ?: "WHITE"
    val startZ = if (team == "BLACK") 11.5f else -11.5f
    val startYaw = if (team == "BLACK") 180f else 0f
    val startPos = Vec3(me?.x?.toFloat() ?: 0f, 0f, startZ)

    _uiState.value = _uiState.value.copy(
      screen = AppScreen.ARENA_COMBAT,
      isTrainingMode = false,
      pos = startPos,
      velY = 0f,
      isGrounded = true,
      yaw = startYaw,
      pitch = 0f,
      smoothCamPos = startPos + Vec3(0f, 2.0f, if (team == "BLACK") 3f else -3f),
      smoothCamYaw = startYaw,
      smoothCamPitch = 0f,
      health = 100,
      ammo = _uiState.value.weapon.magSize,
      isReloading = false,
      respawnCountdown = 0f,
      isPaused = false,
      killFeed = listOf("Матч начался! Команда: $team • Комната: ${room.roomCode}")
    )
    startArenaLoop()
  }

  private fun handleLocalPlayerDeath(attackerUid: String, players: List<RoomPlayer>) {
    SoundSynthesizer.playDeath(settings.value)
    val attackerName = players.find { it.userId == attackerUid }?.nickname ?: "Противник"
    val newDeaths = _uiState.value.localDeaths + 1
    _uiState.value = _uiState.value.copy(
      health = 0,
      localDeaths = newDeaths,
      respawnCountdown = 3.0f,
      killFeed = (listOf("Вы были ликвидированы игроком $attackerName") + _uiState.value.killFeed).take(4)
    )
  }

  private fun finishMatchAndShowResults(room: MatchRoom) {
    gameLoopJob?.cancel()
    SoundSynthesizer.playNotification(settings.value)
    val s = _uiState.value
    val myTeam = s.roomPlayers.find { it.userId == currentUserId }?.team ?: "WHITE"
    val won = room.winner.equals(myTeam, ignoreCase = true) ||
      room.winner.equals(s.userProfile.nickname, ignoreCase = true)
    viewModelScope.launch {
      repository.recordMatchStats(s.localKills, s.localDeaths, won)
    }
    _uiState.value = s.copy(
      screen = AppScreen.MATCH_RESULTS,
      isPaused = false,
      isFiringHeld = false
    )
  }

  fun leaveCurrentMatch() {
    gameLoopJob?.cancel()
    roomListenerJob?.cancel()
    playersListenerJob?.cancel()
    val code = _uiState.value.activeRoomCode
    val wasTraining = _uiState.value.isTrainingMode
    if (!wasTraining && code.isNotBlank() && code != "TRAINING") {
      viewModelScope.launch {
        repository.leaveRoom(code)
      }
    }
    trainingDrones.clear()
    particles.clear()
    tracers.clear()
    decals.clear()
    _uiState.value = _uiState.value.copy(
      screen = AppScreen.MAIN_MENU,
      activeRoomCode = "",
      activeRoom = null,
      roomPlayers = emptyList(),
      isPaused = false,
      isFiringHeld = false
    )
  }

  // --- PLAYER CONTROLS & ACTIONS ---

  fun setMoveJoystick(x: Float, z: Float) {
    moveInputX = x.coerceIn(-1f, 1f)
    moveInputZ = z.coerceIn(-1f, 1f)
  }

  fun rotateCameraDelta(deltaYawDeg: Float, deltaPitchDeg: Float) {
    val st = _uiState.value
    if (st.respawnCountdown > 0f || st.isPaused) return
    val cfg = settings.value
    val sensMul = if (st.isAiming) cfg.aimSensitivity else cfg.cameraSensitivity
    val ySign = if (cfg.invertYAxis) -1f else 1f

    var newYaw = (st.yaw + deltaYawDeg * sensMul * cfg.touchAimSensitivity) % 360f
    if (newYaw > 180f) newYaw -= 360f
    if (newYaw < -180f) newYaw += 360f
    val newPitch = (st.pitch + deltaPitchDeg * sensMul * cfg.touchAimSensitivity * ySign).coerceIn(-75f, 75f)

    _uiState.value = st.copy(yaw = newYaw, pitch = newPitch)
  }

  fun toggleCameraPerspective() {
    SoundSynthesizer.playUiClick(settings.value)
    val next = if (_uiState.value.perspective == CameraPerspective.FIRST_PERSON) {
      CameraPerspective.THIRD_PERSON
    } else {
      CameraPerspective.FIRST_PERSON
    }
    _uiState.value = _uiState.value.copy(perspective = next)
  }

  fun toggleShoulderSide() {
    SoundSynthesizer.playUiClick(settings.value)
    val next = if (_uiState.value.shoulderSide == ShoulderSide.RIGHT) {
      ShoulderSide.LEFT
    } else {
      ShoulderSide.RIGHT
    }
    _uiState.value = _uiState.value.copy(shoulderSide = next)
  }

  fun setAiming(aiming: Boolean) {
    _uiState.value = _uiState.value.copy(isAiming = aiming)
  }

  fun toggleAiming() {
    SoundSynthesizer.playUiClick(settings.value)
    _uiState.value = _uiState.value.copy(isAiming = !_uiState.value.isAiming)
  }

  fun toggleSprint() {
    _uiState.value = _uiState.value.copy(isSprinting = !_uiState.value.isSprinting)
  }

  fun setFiringHeld(held: Boolean) {
    _uiState.value = _uiState.value.copy(isFiringHeld = held)
    if (held) {
      tryFireWeapon()
    }
  }

  fun jump() {
    val st = _uiState.value
    if (st.isGrounded && st.respawnCountdown <= 0f && !st.isPaused) {
      _uiState.value = st.copy(velY = 6.8f, isGrounded = false)
    }
  }

  fun switchWeapon(weaponSpec: WeaponSpec) {
    SoundSynthesizer.playReload(settings.value)
    _uiState.value = _uiState.value.copy(
      weapon = weaponSpec,
      ammo = weaponSpec.magSize,
      isReloading = false,
      reloadProgress = 0f
    )
  }

  fun triggerReload() {
    val st = _uiState.value
    if (st.isReloading || st.ammo >= st.weapon.magSize || st.respawnCountdown > 0f) return
    SoundSynthesizer.playReload(settings.value)
    reloadTimer = 0f
    _uiState.value = st.copy(isReloading = true, reloadProgress = 0f)
  }

  fun setPaused(paused: Boolean) {
    SoundSynthesizer.playUiClick(settings.value)
    _uiState.value = _uiState.value.copy(isPaused = paused, isFiringHeld = false)
  }

  fun saveSettings(updated: GameSettings) {
    SoundSynthesizer.playUiClick(updated)
    viewModelScope.launch {
      settingsRepository.saveSettings(updated)
    }
  }

  fun resetAllSettings() {
    SoundSynthesizer.playUiClick(settings.value)
    viewModelScope.launch {
      settingsRepository.resetAll()
    }
  }

  fun updatePlayerNickname(newName: String) {
    viewModelScope.launch {
      repository.updateNickname(newName)
    }
  }

  // --- CORE 60 FPS GAME LOOP & BALLISTICS ---

  private fun startArenaLoop() {
    gameLoopJob?.cancel()
    gameLoopJob = viewModelScope.launch {
      var lastNs = System.nanoTime()
      var fpsFrameCount = 0
      var fpsAccumSec = 0f
      var elapsedArenaTime = 0f

      while (isActive && _uiState.value.screen == AppScreen.ARENA_COMBAT) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastNs) / 1_000_000_000.0f).coerceIn(0.004f, 0.05f)
        lastNs = nowNs

        fpsFrameCount++
        fpsAccumSec += dt
        if (fpsAccumSec >= 0.5f) {
          val measuredFps = (fpsFrameCount / fpsAccumSec).toInt().coerceIn(15, 120)
          _uiState.value = _uiState.value.copy(currentFps = measuredFps)
          fpsFrameCount = 0
          fpsAccumSec = 0f
        }

        if (!_uiState.value.isPaused) {
          elapsedArenaTime += dt
          stepPhysicsAndCombat(dt, elapsedArenaTime)
        }

        val targetFps = if (settings.value.powerSaving) 30 else settings.value.fpsLimit.coerceIn(30, 120)
        val frameBudgetMs = (1000L / targetFps).coerceIn(8L, 33L)
        delay(frameBudgetMs)
      }
    }
  }

  private fun stepPhysicsAndCombat(dt: Float, elapsedArenaTime: Float) {
    var st = _uiState.value
    val cfg = settings.value

    // 1. Respawn Timer
    if (st.respawnCountdown > 0f) {
      val rem = st.respawnCountdown - dt
      if (rem <= 0f) {
        val myTeam = st.roomPlayers.find { it.userId == currentUserId }?.team ?: "WHITE"
        val spawnZ = if (myTeam == "BLACK") 11.5f else -11.5f
        val spawnX = (Random.nextFloat() - 0.5f) * 14f
        val spawnPos = Vec3(spawnX, 0f, spawnZ)
        lastKnownRemoteHealth = 100L
        st = st.copy(
          pos = spawnPos,
          velY = 0f,
          isGrounded = true,
          health = 100,
          ammo = st.weapon.magSize,
          isReloading = false,
          respawnCountdown = 0f,
          hurtFlash = 0f
        )
        SoundSynthesizer.playNotification(cfg)
      } else {
        st = st.copy(respawnCountdown = rem)
      }
    }

    // 2. Player Movement & Collision against 3D Chess Pieces
    var newPos = st.pos
    var newVelY = st.velY
    var grounded = st.isGrounded
    var walkPhase = st.walkPhase

    if (st.respawnCountdown <= 0f) {
      val forwardFlat = Math3D.forwardFromYawPitch(st.yaw, 0f)
      val rightFlat = Math3D.rightFromYaw(st.yaw)
      val inputLen = sqrt(moveInputX * moveInputX + moveInputZ * moveInputZ)

      if (inputLen > 0.04f) {
        val baseSpeed = if (st.isSprinting && !st.isAiming) 7.4f else if (st.isAiming) 3.6f else 5.4f
        val moveSpeed = baseSpeed * cfg.joystickSensitivity.coerceIn(0.5f, 1.8f)
        val moveVec = (rightFlat * moveInputX + forwardFlat * moveInputZ) * (moveSpeed * dt)
        newPos = world.resolvePlayerMovement(st.pos, st.pos + moveVec)
        walkPhase += dt * moveSpeed * 1.9f
      }

      // Gravity & Jump
      if (!grounded || newPos.y > 0f) {
        newVelY -= 18.5f * dt
        val nextY = newPos.y + newVelY * dt
        if (nextY <= 0f) {
          newPos = newPos.copy(y = 0f)
          newVelY = 0f
          grounded = true
        } else {
          newPos = newPos.copy(y = nextY)
        }
      }
    }

    // 3. Camera Calculation (First-Person vs Third-Person with Wall/Piece Anti-Clipping)
    val shakeAmp = if (cfg.reduceCameraShake) 0f else (st.recoilOffset * cfg.cameraShake * 2.2f)
    val effectivePitch = (st.pitch + shakeAmp).coerceIn(-80f, 80f)
    val lookForward = Math3D.forwardFromYawPitch(st.yaw, effectivePitch)
    val lookRight = Math3D.rightFromYaw(st.yaw)

    val targetCamPos = if (st.perspective == CameraPerspective.FIRST_PERSON) {
      newPos + Vec3(0f, ChessArenaWorld.EYE_HEIGHT, 0f)
    } else {
      val shoulderSign = if (st.shoulderSide == ShoulderSide.RIGHT) 1f else -1f
      val dist = if (st.isAiming) (cfg.thirdPersonDistance * 0.58f) else cfg.thirdPersonDistance
      val pivot = newPos + Vec3(0f, 1.52f, 0f) + lookRight * (0.58f * shoulderSign)
      val desired = pivot - lookForward * dist + Vec3(0f, 0.25f, 0f)
      world.computeSafeThirdPersonCamera(pivot, desired)
    }

    val smoothAlpha = (1f - (1f - cfg.cameraSmoothing.coerceIn(0.1f, 0.98f)) * 0.45f).coerceIn(0.25f, 1f)
    val finalCamPos = if (st.perspective == CameraPerspective.FIRST_PERSON) {
      targetCamPos
    } else {
      st.smoothCamPos.lerp(targetCamPos, smoothAlpha)
    }

    // 4. Weapon Cooldown, Reload & Auto-Fire
    if (fireCooldown > 0f) fireCooldown = (fireCooldown - dt).coerceAtLeast(0f)

    var isReloading = st.isReloading
    var reloadProg = st.reloadProgress
    var ammo = st.ammo
    if (isReloading) {
      reloadTimer += dt
      reloadProg = (reloadTimer / st.weapon.reloadDurationSec).coerceIn(0f, 1f)
      if (reloadTimer >= st.weapon.reloadDurationSec) {
        ammo = st.weapon.magSize
        isReloading = false
        reloadProg = 0f
      }
    }

    // 5. Update Training Drones (if in Training Mode)
    if (st.isTrainingMode) {
      for (drone in trainingDrones) {
        if (drone.hitFlashTimer > 0f) {
          drone.hitFlashTimer = (drone.hitFlashTimer - dt).coerceAtLeast(0f)
        }
        if (drone.health <= 0) {
          drone.respawnTimer -= dt
          if (drone.respawnTimer <= 0f) {
            drone.health = drone.maxHealth
            drone.pos = drone.startPos
          }
        } else {
          val offset = sin(elapsedArenaTime * drone.speed * 0.65f) * drone.patrolRange
          drone.pos = if (drone.patrolAxisX) {
            drone.startPos.copy(x = (drone.startPos.x + offset).coerceIn(-12.5f, 12.5f))
          } else {
            drone.startPos.copy(z = (drone.startPos.z + offset).coerceIn(-12.5f, 12.5f))
          }
        }
      }
    }

    // 6. Step Particles, Bullet Tracers, Decals & Popups
    val particleIter = particles.iterator()
    while (particleIter.hasNext()) {
      val p = particleIter.next()
      p.life -= dt
      if (p.life <= 0f) {
        particleIter.remove()
      } else {
        p.vel = p.vel.copy(y = p.vel.y - 9.8f * dt)
        p.pos = p.pos + p.vel * dt
      }
    }

    val tracerIter = tracers.iterator()
    while (tracerIter.hasNext()) {
      val tr = tracerIter.next()
      tr.life -= dt
      if (tr.life <= 0f) tracerIter.remove()
    }

    val decalIter = decals.iterator()
    while (decalIter.hasNext()) {
      val d = decalIter.next()
      d.life -= dt
      if (d.life <= 0f) decalIter.remove()
    }

    val updatedPopups = st.damagePopups.mapNotNull { pop ->
      val nextT = pop.timer - dt
      if (nextT > 0f) pop.copy(timer = nextT) else null
    }

    _uiState.value = st.copy(
      pos = newPos,
      velY = newVelY,
      isGrounded = grounded,
      smoothCamPos = finalCamPos,
      smoothCamYaw = st.yaw,
      smoothCamPitch = effectivePitch,
      ammo = ammo,
      isReloading = isReloading,
      reloadProgress = reloadProg,
      recoilOffset = (st.recoilOffset - dt * 2.8f).coerceAtLeast(0f),
      muzzleFlash = (st.muzzleFlash - dt * 11f).coerceAtLeast(0f),
      hurtFlash = (st.hurtFlash - dt * 2.2f).coerceAtLeast(0f),
      hitMarkerAlpha = (st.hitMarkerAlpha - dt * 3.5f).coerceAtLeast(0f),
      walkPhase = walkPhase,
      damagePopups = updatedPopups
    )

    // Auto-fire if holding trigger
    if (_uiState.value.isFiringHeld && !_uiState.value.isReloading && fireCooldown <= 0f) {
      tryFireWeapon()
    }

    // 7. Throttled Multiplayer Network Sync (10 Hz to stay well within Firestore rate limits)
    val nowMs = System.currentTimeMillis()
    if (!st.isTrainingMode && st.activeRoomCode.isNotBlank() && nowMs - lastNetworkSyncTime >= 110L) {
      lastNetworkSyncTime = nowMs
      val cur = _uiState.value
      viewModelScope.launch {
        val t0 = System.currentTimeMillis()
        repository.syncPlayerTelemetry(
          roomCode = cur.activeRoomCode,
          x = cur.pos.x.toDouble(),
          y = cur.pos.y.toDouble(),
          z = cur.pos.z.toDouble(),
          yaw = cur.yaw.toDouble(),
          pitch = cur.pitch.toDouble(),
          health = cur.health,
          ammo = cur.ammo,
          kills = cur.localKills,
          deaths = cur.localDeaths,
          weapon = cur.weapon.id,
          lastShotSeq = shotSeqCounter
        )
        val rtt = (System.currentTimeMillis() - t0).toInt().coerceIn(12, 450)
        _uiState.value = _uiState.value.copy(currentPingMs = rtt)
      }
    }
  }

  /**
   * Executes a weapon shot with soft aim-assist, 1st/3rd-person parallax raycast ballistics,
   * particle impacts, and authoritative multiplayer damage sync.
   */
  fun tryFireWeapon() {
    val st = _uiState.value
    if (st.respawnCountdown > 0f || st.isReloading || st.isPaused || fireCooldown > 0f) return
    val cfg = settings.value

    if (st.ammo <= 0) {
      if (cfg.autoReload) {
        triggerReload()
      } else {
        SoundSynthesizer.playUiClick(cfg)
      }
      return
    }

    fireCooldown = st.weapon.fireCooldownSec
    shotSeqCounter++
    SoundSynthesizer.playGunshot(cfg, st.weapon.id)

    // Build list of active target actors (Training Drones or Remote Multiplayer Opponents)
    val targets = if (st.isTrainingMode) {
      trainingDrones.filter { it.health > 0 }.map { d ->
        ChessArenaWorld.TargetActor(id = d.id, pos = d.pos, radius = 0.88f, height = 3.1f)
      }
    } else {
      val myTeam = st.roomPlayers.find { it.userId == currentUserId }?.team ?: "WHITE"
      val isFfa = st.activeRoom?.mode == "ffa"
      st.roomPlayers.filter { rp ->
        rp.userId != currentUserId && rp.health > 0L && (isFfa || rp.team != myTeam)
      }.map { rp ->
        ChessArenaWorld.TargetActor(
          id = rp.userId,
          pos = Vec3(rp.x.toFloat(), rp.y.toFloat(), rp.z.toFloat()),
          radius = 0.65f,
          height = 1.80f
        )
      }
    }

    // Subtle Touch Aim Assist (if enabled in settings)
    var effectiveYaw = st.yaw
    val assist = cfg.aimAssistStrength.coerceIn(0f, 0.6f)
    if (assist > 0.02f && targets.isNotEmpty()) {
      for (t in targets) {
        val dx = t.pos.x - st.pos.x
        val dz = t.pos.z - st.pos.z
        val angleToTarget = (atan2(dx, dz) * 180.0 / Math.PI).toFloat()
        var diff = (angleToTarget - effectiveYaw) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        if (kotlin.math.abs(diff) < 8.5f * assist) {
          effectiveYaw += diff * (0.35f * assist)
          break
        }
      }
    }

    // Step 1: Raycast from Camera Center through Crosshair
    val camDir = Math3D.forwardFromYawPitch(effectiveYaw, st.smoothCamPitch)
    val cameraRay = Ray3D(st.smoothCamPos, camDir)
    val crosshairHit = world.raycastArena(cameraRay, targets)

    // Step 2: In Third-Person Mode, compute bullet trajectory from Character Weapon Muzzle to Crosshair Hit Point!
    val playerRight = Math3D.rightFromYaw(effectiveYaw)
    val playerForward = Math3D.forwardFromYawPitch(effectiveYaw, st.pitch)
    val muzzleWorldPos = if (st.perspective == CameraPerspective.FIRST_PERSON) {
      val handSign = if (cfg.leftHandedMode) -1f else 1f
      st.smoothCamPos + playerRight * (0.22f * handSign) - Vec3(0f, 0.16f, 0f) + camDir * 0.45f
    } else {
      st.pos + playerRight * 0.34f + Vec3(0f, 1.22f, 0f) + playerForward * 0.65f
    }

    val finalHit = if (st.perspective == CameraPerspective.THIRD_PERSON) {
      val muzzleToTargetDir = (crosshairHit.point - muzzleWorldPos).normalized()
      world.raycastArena(Ray3D(muzzleWorldPos, muzzleToTargetDir), targets)
    } else {
      crosshairHit
    }

    // Add 3D Bullet Tracer from Muzzle to Actual Hit Point
    tracers.add(
      BulletTracer3D(
        start = muzzleWorldPos,
        end = finalHit.point,
        colorHex = st.weapon.tracerHex,
        life = 0.16f
      )
    )

    // Spawn Impact Particles & Decals (capped for Android performance)
    spawnImpactEffects(finalHit.point, finalHit.normal, finalHit.hitType)

    var newKills = st.localKills
    var hitAlpha = st.hitMarkerAlpha
    var hitIsKill = false
    val newPopups = st.damagePopups.toMutableList()
    var newFeed = st.killFeed

    if (finalHit.hitType == HitSurfaceType.CHESS_PIECE || finalHit.hitType == HitSurfaceType.WALL) {
      SoundSynthesizer.playHitObject(cfg)
    } else if (finalHit.hitType == HitSurfaceType.PLAYER_OR_DRONE && finalHit.targetId.isNotBlank()) {
      val dmg = st.weapon.damage
      hitAlpha = 1.0f

      if (st.isTrainingMode) {
        val drone = trainingDrones.find { it.id == finalHit.targetId }
        if (drone != null && drone.health > 0) {
          drone.health = (drone.health - dmg).coerceAtLeast(0)
          drone.hitFlashTimer = 0.15f
          val eliminated = drone.health == 0
          if (eliminated) {
            drone.respawnTimer = 3.2f
            newKills++
            hitIsKill = true
            newFeed = (listOf("Ликвидирован ${drone.callsign} [+100]") + newFeed).take(4)
            spawnExplosionParticles(drone.pos + Vec3(0f, 1.5f, 0f), 0xFFFFAB00)
          }
          SoundSynthesizer.playHitPlayer(cfg, isElimination = eliminated)
          newPopups.add(
            DamagePopup(
              id = System.nanoTime(),
              amount = dmg,
              targetName = drone.callsign,
              isElimination = eliminated
            )
          )
        }
      } else {
        // Real Multiplayer Opponent Hit
        val targetPlayer = st.roomPlayers.find { it.userId == finalHit.targetId }
        if (targetPlayer != null && targetPlayer.health > 0L) {
          val updatedHp = (targetPlayer.health.toInt() - dmg).coerceAtLeast(0)
          val eliminated = updatedHp == 0
          val updatedDeaths = if (eliminated) targetPlayer.deaths.toInt() + 1 else targetPlayer.deaths.toInt()
          if (eliminated) {
            newKills++
            hitIsKill = true
            newFeed = (listOf("Вы ликвидировали ${targetPlayer.nickname}!") + newFeed).take(4)
            spawnExplosionParticles(finalHit.point, 0xFF00E5FF)
          }
          SoundSynthesizer.playHitPlayer(cfg, isElimination = eliminated)
          newPopups.add(
            DamagePopup(
              id = System.nanoTime(),
              amount = dmg,
              targetName = targetPlayer.nickname,
              isElimination = eliminated
            )
          )

          val roomCode = st.activeRoomCode
          val myTeam = st.roomPlayers.find { it.userId == currentUserId }?.team ?: "WHITE"
          val room = st.activeRoom
          viewModelScope.launch {
            repository.applyDamageToOpponent(
              roomCode = roomCode,
              targetUserId = targetPlayer.userId,
              newHealth = updatedHp,
              newDeaths = updatedDeaths
            )
            if (eliminated && room != null) {
              val newTeamScore = if (myTeam == "BLACK") room.blackScore.toInt() + 1 else room.whiteScore.toInt() + 1
              val winnerLabel = if (room.mode == "ffa") st.userProfile.nickname else myTeam
              repository.incrementTeamScoreAndCheckWin(
                roomCode = roomCode,
                scoringTeam = myTeam,
                newTeamScore = newTeamScore,
                scoreLimit = room.scoreLimit.toInt(),
                winnerLabel = winnerLabel
              )
            }
          }
        }
      }
    }

    val nextAmmo = (st.ammo - 1).coerceAtLeast(0)
    _uiState.value = st.copy(
      yaw = effectiveYaw,
      ammo = nextAmmo,
      recoilOffset = (st.recoilOffset + st.weapon.recoilKick).coerceAtMost(1.2f),
      muzzleFlash = 1.0f,
      localKills = newKills,
      hitMarkerAlpha = hitAlpha,
      hitMarkerIsKill = hitIsKill,
      damagePopups = newPopups.takeLast(4),
      killFeed = newFeed
    )

    if (nextAmmo == 0 && cfg.autoReload) {
      triggerReload()
    }
  }

  private fun spawnImpactEffects(point: Vec3, normal: Vec3, hitType: HitSurfaceType) {
    if (hitType == HitSurfaceType.NONE) return
    val cfg = settings.value

    if (decals.size >= 24) decals.removeAt(0)
    val decalHex = when (hitType) {
      HitSurfaceType.PLAYER_OR_DRONE -> 0xFFFF1744
      HitSurfaceType.CHESS_PIECE -> 0xFF00E5FF
      else -> 0xFF94A3B8
    }
    decals.add(ImpactDecal3D(pos = point + normal * 0.04f, normal = normal, colorHex = decalHex))

    if (cfg.simplifyVisualEffects) return
    val maxParticles = if (cfg.powerSaving) 18 else 48
    while (particles.size > maxParticles) {
      particles.removeAt(0)
    }

    val count = if (hitType == HitSurfaceType.CHESS_PIECE) 8 else 5
    for (i in 0 until count) {
      val spread = Vec3(
        (Random.nextFloat() - 0.5f) * 4.5f,
        Random.nextFloat() * 4.0f,
        (Random.nextFloat() - 0.5f) * 4.5f
      )
      particles.add(
        Particle3D(
          pos = point + normal * 0.06f,
          vel = normal * 3.2f + spread,
          colorHex = if (i % 2 == 0) decalHex else 0xFFFFAB00,
          life = 0.35f,
          maxLife = 0.35f,
          size = 0.07f
        )
      )
    }
  }

  private fun spawnExplosionParticles(center: Vec3, colorHex: Long) {
    if (settings.value.simplifyVisualEffects) return
    for (i in 0 until 14) {
      val vel = Vec3(
        (Random.nextFloat() - 0.5f) * 8f,
        Random.nextFloat() * 6.5f,
        (Random.nextFloat() - 0.5f) * 8f
      )
      particles.add(
        Particle3D(
          pos = center,
          vel = vel,
          colorHex = if (i % 2 == 0) colorHex else 0xFFFFFFFF,
          life = 0.55f,
          maxLife = 0.55f,
          size = 0.12f
        )
      )
    }
  }

  fun buildRenderState(): PlayerRenderState {
    val st = _uiState.value
    val myTeam = st.roomPlayers.find { it.userId == currentUserId }?.team ?: "WHITE"
    return PlayerRenderState(
      pos = st.pos,
      yaw = st.yaw,
      pitch = st.pitch,
      camPos = st.smoothCamPos,
      camYaw = st.smoothCamYaw,
      camPitch = st.smoothCamPitch,
      perspective = st.perspective,
      isAiming = st.isAiming,
      recoilOffset = st.recoilOffset,
      reloadProgress = st.reloadProgress,
      isReloading = st.isReloading,
      walkPhase = st.walkPhase,
      muzzleFlashIntensity = st.muzzleFlash,
      hurtFlashIntensity = st.hurtFlash,
      equippedWeapon = st.weapon.id,
      team = myTeam,
      arenaTheme = st.activeRoom?.arenaTheme ?: "cyber_obsidian"
    )
  }

  override fun onCleared() {
    super.onCleared()
    gameLoopJob?.cancel()
    roomListenerJob?.cancel()
    playersListenerJob?.cancel()
    publicRoomsJob?.cancel()
  }
}
