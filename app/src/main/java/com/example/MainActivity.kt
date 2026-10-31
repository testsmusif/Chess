package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ChessArenaRepository
import com.example.engine.Arena3DViewport
import com.example.settings.SettingsRepository
import com.example.ui.AboutGameScreen
import com.example.ui.AppScreen
import com.example.ui.AuthScreen
import com.example.ui.ChessArenaViewModel
import com.example.ui.CombatHudOverlay
import com.example.ui.ControlsCustomizationScreen
import com.example.ui.CreateRoomScreen
import com.example.ui.DeepSettingsScreen
import com.example.ui.FindMatchScreen
import com.example.ui.JoinByCodeScreen
import com.example.ui.LobbyWaitingScreen
import com.example.ui.MainMenuScreen
import com.example.ui.MatchResultsScreen
import com.example.ui.signOut
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    ChessArenaAuthenticatedRoot(
      currentUserId = user.uid,
      defaultDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Commander"
    )
  }
}

@Composable
fun ChessArenaAuthenticatedRoot(
  currentUserId: String,
  defaultDisplayName: String
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val viewModel: ChessArenaViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        ChessArenaViewModel(
          repository = ChessArenaRepository(db),
          settingsRepository = SettingsRepository(app),
          currentUserId = currentUserId,
          defaultDisplayName = defaultDisplayName
        )
      }
    }
  )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  var returnToCombatFromSettings by remember { mutableStateOf(false) }

  when (uiState.screen) {
    AppScreen.MAIN_MENU -> {
      MainMenuScreen(
        uiState = uiState,
        onQuickPlay = { viewModel.quickFindMatch() },
        onNavigate = { viewModel.navigateTo(it) },
        onStartTraining = { theme -> viewModel.startTrainingMatch(theme) },
        onUpdateNickname = { viewModel.updatePlayerNickname(it) },
        onSignOut = {
          signOut(
            context = context,
            credentialManager = credentialManager,
            onSignOutComplete = {},
            scope = coroutineScope
          )
        }
      )
    }

    AppScreen.FIND_MATCH -> {
      FindMatchScreen(
        uiState = uiState,
        onJoinRoom = { code -> viewModel.joinMultiplayerRoom(code) },
        onCreateNewRoom = { viewModel.navigateTo(AppScreen.CREATE_ROOM) },
        onBack = { viewModel.navigateTo(AppScreen.MAIN_MENU) }
      )
    }

    AppScreen.CREATE_ROOM -> {
      CreateRoomScreen(
        isBusy = uiState.isBusy,
        onCreateRoom = { mode, isPublic, limit, theme ->
          viewModel.createMultiplayerRoom(mode, isPublic, limit, theme)
        },
        onBack = { viewModel.navigateTo(AppScreen.MAIN_MENU) }
      )
    }

    AppScreen.JOIN_BY_CODE -> {
      JoinByCodeScreen(
        uiState = uiState,
        onJoinCode = { code -> viewModel.joinMultiplayerRoom(code) },
        onBack = { viewModel.navigateTo(AppScreen.MAIN_MENU) }
      )
    }

    AppScreen.LOBBY_WAITING -> {
      LobbyWaitingScreen(
        uiState = uiState,
        currentUserId = currentUserId,
        onStartMatch = { viewModel.hostStartMultiplayerMatch() },
        onLeaveLobby = { viewModel.leaveCurrentMatch() }
      )
    }

    AppScreen.ARENA_COMBAT -> {
      BackHandler {
        viewModel.setPaused(!uiState.isPaused)
      }
      Box(modifier = Modifier.fillMaxSize()) {
        Arena3DViewport(
          world = viewModel.world,
          renderState = viewModel.buildRenderState(),
          remotePlayers = uiState.roomPlayers,
          trainingDrones = viewModel.trainingDrones,
          particles = viewModel.particles,
          tracers = viewModel.tracers,
          decals = viewModel.decals,
          settings = settings,
          localUserId = currentUserId,
          modifier = Modifier.fillMaxSize()
        )
        CombatHudOverlay(
          uiState = uiState,
          settings = settings,
          onJoystickMove = { x, z -> viewModel.setMoveJoystick(x, z) },
          onLookDelta = { dyaw, dpitch -> viewModel.rotateCameraDelta(dyaw, dpitch) },
          onFireHeldChange = { held -> viewModel.setFiringHeld(held) },
          onReload = { viewModel.triggerReload() },
          onJump = { viewModel.jump() },
          onToggleCamera = { viewModel.toggleCameraPerspective() },
          onToggleShoulder = { viewModel.toggleShoulderSide() },
          onToggleAim = { viewModel.toggleAiming() },
          onToggleSprint = { viewModel.toggleSprint() },
          onSwitchWeapon = { spec -> viewModel.switchWeapon(spec) },
          onPauseToggle = { paused -> viewModel.setPaused(paused) },
          onOpenSettingsFromPause = {
            returnToCombatFromSettings = true
            viewModel.navigateTo(AppScreen.SETTINGS)
          },
          onExitMatch = { viewModel.leaveCurrentMatch() }
        )
      }
    }

    AppScreen.MATCH_RESULTS -> {
      MatchResultsScreen(
        uiState = uiState,
        onReturnToMenu = { viewModel.leaveCurrentMatch() }
      )
    }

    AppScreen.SETTINGS -> {
      DeepSettingsScreen(
        currentSettings = settings,
        onApplySettings = { viewModel.saveSettings(it) },
        onResetAll = { viewModel.resetAllSettings() },
        onBack = {
          if (returnToCombatFromSettings) {
            returnToCombatFromSettings = false
            viewModel.navigateTo(AppScreen.ARENA_COMBAT)
          } else {
            viewModel.navigateTo(AppScreen.MAIN_MENU)
          }
        }
      )
    }

    AppScreen.CONTROLS_INFO -> {
      ControlsCustomizationScreen(
        settings = settings,
        onSaveSettings = { viewModel.saveSettings(it) },
        onBack = { viewModel.navigateTo(AppScreen.MAIN_MENU) }
      )
    }

    AppScreen.ABOUT_GAME -> {
      AboutGameScreen(
        onBack = { viewModel.navigateTo(AppScreen.MAIN_MENU) }
      )
    }
  }
}
