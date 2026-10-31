package com.example.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "chess_arena_settings")

enum class GraphicsQuality { LOW, MEDIUM, HIGH, ULTRA }
enum class ShadowQuality { OFF, SOFT, SHARP }
enum class ReflectionQuality { OFF, SIMPLE, FULL }
enum class CameraPerspective { FIRST_PERSON, THIRD_PERSON }
enum class ShoulderSide { RIGHT, LEFT }
enum class CrosshairStyle { CROSS, DOT, CIRCLE, CHESS_CROWN }
enum class CrosshairColor(val hex: Long) {
  CYAN(0xFF00E5FF),
  GREEN(0xFF00E676),
  AMBER(0xFFFFAB00),
  RED(0xFFFF1744),
  WHITE(0xFFF8FAFC)
}
enum class SettingsCategory {
  GRAPHICS, CAMERA, CONTROLS, AUDIO, GAMEPLAY, INTERFACE, NETWORK, ACCESSIBILITY
}

data class GameSettings(
  // 1. Graphics
  val qualityPreset: GraphicsQuality = GraphicsQuality.HIGH,
  val renderScale: Float = 1.0f,
  val shadowQuality: ShadowQuality = ShadowQuality.SOFT,
  val drawDistance: Float = 45.0f,
  val reflectionQuality: ReflectionQuality = ReflectionQuality.FULL,
  val antiAliasing: Boolean = true,
  val glowEffects: Boolean = true,
  val motionBlur: Boolean = false,
  val fpsLimit: Int = 60,
  val autoOptimize: Boolean = true,
  val showFps: Boolean = true,
  val powerSaving: Boolean = false,

  // 2. Camera
  val defaultPerspective: CameraPerspective = CameraPerspective.THIRD_PERSON,
  val cameraSensitivity: Float = 1.0f,
  val aimSensitivity: Float = 0.7f,
  val invertYAxis: Boolean = false,
  val thirdPersonDistance: Float = 3.4f,
  val shoulderSide: ShoulderSide = ShoulderSide.RIGHT,
  val cameraSmoothing: Float = 0.8f,
  val fov: Float = 78.0f,
  val cameraShake: Float = 0.5f,

  // 3. Controls
  val joystickSensitivity: Float = 1.0f,
  val touchAimSensitivity: Float = 1.0f,
  val buttonScale: Float = 1.0f,
  val buttonOpacity: Float = 0.85f,
  val controlLayoutPreset: String = "STANDARD", // "STANDARD", "COMPACT", "ONE_HAND"
  val leftHandedMode: Boolean = false,
  val holdToAim: Boolean = false,

  // 4. Audio (No music per specification)
  val masterVolume: Float = 0.85f,
  val gunshotVolume: Float = 0.85f,
  val hitVolume: Float = 0.9f,
  val effectsVolume: Float = 0.8f,
  val uiVolume: Float = 0.6f,
  val spatialAudio: Boolean = true,
  val muteAll: Boolean = false,

  // 5. Gameplay
  val crosshairStyle: CrosshairStyle = CrosshairStyle.CHESS_CROWN,
  val crosshairSize: Float = 1.0f,
  val crosshairColor: CrosshairColor = CrosshairColor.CYAN,
  val showHitMarker: Boolean = true,
  val showDamageNumbers: Boolean = true,
  val aimAssistStrength: Float = 0.25f,
  val autoReload: Boolean = true,
  val showNetworkPing: Boolean = true,

  // 6. Interface
  val language: String = "RU", // "RU" or "EN"
  val hudScale: Float = 1.0f,
  val hudLayout: String = "TACTICAL", // "TACTICAL", "MINIMAL"
  val showKillfeed: Boolean = true,
  val safeNotchPadding: Boolean = true,
  val notificationsEnabled: Boolean = true,

  // 7. Network
  val serverAddress: String = "wss://chess-arena-relay.example.org",
  val serverRegion: String = "EU-CENTRAL", // "EU-CENTRAL", "US-EAST", "ASIA-EAST"
  val autoReconnect: Boolean = true,
  val connectionAlerts: Boolean = true,

  // 8. Accessibility
  val reduceCameraShake: Boolean = false,
  val highContrastArena: Boolean = false,
  val alternativeCrosshairColor: Boolean = false,
  val reduceFlashIntensity: Boolean = false,
  val simplifyVisualEffects: Boolean = false
)

class SettingsRepository(private val context: Context) {

  private object Keys {
    val QUALITY = stringPreferencesKey("quality_preset")
    val RENDER_SCALE = floatPreferencesKey("render_scale")
    val SHADOWS = stringPreferencesKey("shadow_quality")
    val DRAW_DIST = floatPreferencesKey("draw_distance")
    val REFLECTIONS = stringPreferencesKey("reflection_quality")
    val AA = booleanPreferencesKey("anti_aliasing")
    val GLOW = booleanPreferencesKey("glow_effects")
    val MOTION_BLUR = booleanPreferencesKey("motion_blur")
    val FPS_LIMIT = intPreferencesKey("fps_limit")
    val AUTO_OPT = booleanPreferencesKey("auto_optimize")
    val SHOW_FPS = booleanPreferencesKey("show_fps")
    val POWER_SAVE = booleanPreferencesKey("power_saving")

    val CAM_PERSPECTIVE = stringPreferencesKey("cam_perspective")
    val CAM_SENS = floatPreferencesKey("cam_sens")
    val AIM_SENS = floatPreferencesKey("aim_sens")
    val INVERT_Y = booleanPreferencesKey("invert_y")
    val TP_DIST = floatPreferencesKey("tp_dist")
    val SHOULDER = stringPreferencesKey("shoulder_side")
    val CAM_SMOOTH = floatPreferencesKey("cam_smooth")
    val FOV = floatPreferencesKey("fov")
    val CAM_SHAKE = floatPreferencesKey("cam_shake")

    val JOY_SENS = floatPreferencesKey("joy_sens")
    val TOUCH_AIM_SENS = floatPreferencesKey("touch_aim_sens")
    val BTN_SCALE = floatPreferencesKey("btn_scale")
    val BTN_OPACITY = floatPreferencesKey("btn_opacity")
    val CTRL_PRESET = stringPreferencesKey("ctrl_preset")
    val LEFT_HAND = booleanPreferencesKey("left_hand")
    val HOLD_AIM = booleanPreferencesKey("hold_aim")

    val MASTER_VOL = floatPreferencesKey("master_vol")
    val SHOT_VOL = floatPreferencesKey("shot_vol")
    val HIT_VOL = floatPreferencesKey("hit_vol")
    val FX_VOL = floatPreferencesKey("fx_vol")
    val UI_VOL = floatPreferencesKey("ui_vol")
    val SPATIAL_AUDIO = booleanPreferencesKey("spatial_audio")
    val MUTE_ALL = booleanPreferencesKey("mute_all")

    val CROSS_STYLE = stringPreferencesKey("cross_style")
    val CROSS_SIZE = floatPreferencesKey("cross_size")
    val CROSS_COLOR = stringPreferencesKey("cross_color")
    val HIT_MARKER = booleanPreferencesKey("hit_marker")
    val DMG_NUMS = booleanPreferencesKey("dmg_nums")
    val AIM_ASSIST = floatPreferencesKey("aim_assist")
    val AUTO_RELOAD = booleanPreferencesKey("auto_reload")
    val SHOW_PING = booleanPreferencesKey("show_ping")

    val LANG = stringPreferencesKey("lang")
    val HUD_SCALE = floatPreferencesKey("hud_scale")
    val HUD_LAYOUT = stringPreferencesKey("hud_layout")
    val SHOW_KILLFEED = booleanPreferencesKey("show_killfeed")
    val SAFE_NOTCH = booleanPreferencesKey("safe_notch")
    val NOTIFICATIONS = booleanPreferencesKey("notifications")

    val SERVER_ADDR = stringPreferencesKey("server_addr")
    val SERVER_REGION = stringPreferencesKey("server_region")
    val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
    val CONN_ALERTS = booleanPreferencesKey("conn_alerts")

    val ACC_REDUCE_SHAKE = booleanPreferencesKey("acc_reduce_shake")
    val ACC_HIGH_CONTRAST = booleanPreferencesKey("acc_high_contrast")
    val ACC_ALT_CROSS = booleanPreferencesKey("acc_alt_cross")
    val ACC_REDUCE_FLASH = booleanPreferencesKey("acc_reduce_flash")
    val ACC_SIMPLIFY_FX = booleanPreferencesKey("acc_simplify_fx")
  }

  val settingsFlow: Flow<GameSettings> = context.settingsDataStore.data.map { prefs ->
    mapPreferences(prefs)
  }

  private fun mapPreferences(prefs: Preferences): GameSettings {
    val def = GameSettings()
    return GameSettings(
      qualityPreset = prefs[Keys.QUALITY]?.let { runCatching { GraphicsQuality.valueOf(it) }.getOrNull() } ?: def.qualityPreset,
      renderScale = prefs[Keys.RENDER_SCALE] ?: def.renderScale,
      shadowQuality = prefs[Keys.SHADOWS]?.let { runCatching { ShadowQuality.valueOf(it) }.getOrNull() } ?: def.shadowQuality,
      drawDistance = prefs[Keys.DRAW_DIST] ?: def.drawDistance,
      reflectionQuality = prefs[Keys.REFLECTIONS]?.let { runCatching { ReflectionQuality.valueOf(it) }.getOrNull() } ?: def.reflectionQuality,
      antiAliasing = prefs[Keys.AA] ?: def.antiAliasing,
      glowEffects = prefs[Keys.GLOW] ?: def.glowEffects,
      motionBlur = prefs[Keys.MOTION_BLUR] ?: def.motionBlur,
      fpsLimit = prefs[Keys.FPS_LIMIT] ?: def.fpsLimit,
      autoOptimize = prefs[Keys.AUTO_OPT] ?: def.autoOptimize,
      showFps = prefs[Keys.SHOW_FPS] ?: def.showFps,
      powerSaving = prefs[Keys.POWER_SAVE] ?: def.powerSaving,

      defaultPerspective = prefs[Keys.CAM_PERSPECTIVE]?.let { runCatching { CameraPerspective.valueOf(it) }.getOrNull() } ?: def.defaultPerspective,
      cameraSensitivity = prefs[Keys.CAM_SENS] ?: def.cameraSensitivity,
      aimSensitivity = prefs[Keys.AIM_SENS] ?: def.aimSensitivity,
      invertYAxis = prefs[Keys.INVERT_Y] ?: def.invertYAxis,
      thirdPersonDistance = prefs[Keys.TP_DIST] ?: def.thirdPersonDistance,
      shoulderSide = prefs[Keys.SHOULDER]?.let { runCatching { ShoulderSide.valueOf(it) }.getOrNull() } ?: def.shoulderSide,
      cameraSmoothing = prefs[Keys.CAM_SMOOTH] ?: def.cameraSmoothing,
      fov = prefs[Keys.FOV] ?: def.fov,
      cameraShake = prefs[Keys.CAM_SHAKE] ?: def.cameraShake,

      joystickSensitivity = prefs[Keys.JOY_SENS] ?: def.joystickSensitivity,
      touchAimSensitivity = prefs[Keys.TOUCH_AIM_SENS] ?: def.touchAimSensitivity,
      buttonScale = prefs[Keys.BTN_SCALE] ?: def.buttonScale,
      buttonOpacity = prefs[Keys.BTN_OPACITY] ?: def.buttonOpacity,
      controlLayoutPreset = prefs[Keys.CTRL_PRESET] ?: def.controlLayoutPreset,
      leftHandedMode = prefs[Keys.LEFT_HAND] ?: def.leftHandedMode,
      holdToAim = prefs[Keys.HOLD_AIM] ?: def.holdToAim,

      masterVolume = prefs[Keys.MASTER_VOL] ?: def.masterVolume,
      gunshotVolume = prefs[Keys.SHOT_VOL] ?: def.gunshotVolume,
      hitVolume = prefs[Keys.HIT_VOL] ?: def.hitVolume,
      effectsVolume = prefs[Keys.FX_VOL] ?: def.effectsVolume,
      uiVolume = prefs[Keys.UI_VOL] ?: def.uiVolume,
      spatialAudio = prefs[Keys.SPATIAL_AUDIO] ?: def.spatialAudio,
      muteAll = prefs[Keys.MUTE_ALL] ?: def.muteAll,

      crosshairStyle = prefs[Keys.CROSS_STYLE]?.let { runCatching { CrosshairStyle.valueOf(it) }.getOrNull() } ?: def.crosshairStyle,
      crosshairSize = prefs[Keys.CROSS_SIZE] ?: def.crosshairSize,
      crosshairColor = prefs[Keys.CROSS_COLOR]?.let { runCatching { CrosshairColor.valueOf(it) }.getOrNull() } ?: def.crosshairColor,
      showHitMarker = prefs[Keys.HIT_MARKER] ?: def.showHitMarker,
      showDamageNumbers = prefs[Keys.DMG_NUMS] ?: def.showDamageNumbers,
      aimAssistStrength = prefs[Keys.AIM_ASSIST] ?: def.aimAssistStrength,
      autoReload = prefs[Keys.AUTO_RELOAD] ?: def.autoReload,
      showNetworkPing = prefs[Keys.SHOW_PING] ?: def.showNetworkPing,

      language = prefs[Keys.LANG] ?: def.language,
      hudScale = prefs[Keys.HUD_SCALE] ?: def.hudScale,
      hudLayout = prefs[Keys.HUD_LAYOUT] ?: def.hudLayout,
      showKillfeed = prefs[Keys.SHOW_KILLFEED] ?: def.showKillfeed,
      safeNotchPadding = prefs[Keys.SAFE_NOTCH] ?: def.safeNotchPadding,
      notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: def.notificationsEnabled,

      serverAddress = prefs[Keys.SERVER_ADDR] ?: def.serverAddress,
      serverRegion = prefs[Keys.SERVER_REGION] ?: def.serverRegion,
      autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: def.autoReconnect,
      connectionAlerts = prefs[Keys.CONN_ALERTS] ?: def.connectionAlerts,

      reduceCameraShake = prefs[Keys.ACC_REDUCE_SHAKE] ?: def.reduceCameraShake,
      highContrastArena = prefs[Keys.ACC_HIGH_CONTRAST] ?: def.highContrastArena,
      alternativeCrosshairColor = prefs[Keys.ACC_ALT_CROSS] ?: def.alternativeCrosshairColor,
      reduceFlashIntensity = prefs[Keys.ACC_REDUCE_FLASH] ?: def.reduceFlashIntensity,
      simplifyVisualEffects = prefs[Keys.ACC_SIMPLIFY_FX] ?: def.simplifyVisualEffects
    )
  }

  suspend fun saveSettings(s: GameSettings) {
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.QUALITY] = s.qualityPreset.name
      prefs[Keys.RENDER_SCALE] = s.renderScale
      prefs[Keys.SHADOWS] = s.shadowQuality.name
      prefs[Keys.DRAW_DIST] = s.drawDistance
      prefs[Keys.REFLECTIONS] = s.reflectionQuality.name
      prefs[Keys.AA] = s.antiAliasing
      prefs[Keys.GLOW] = s.glowEffects
      prefs[Keys.MOTION_BLUR] = s.motionBlur
      prefs[Keys.FPS_LIMIT] = s.fpsLimit
      prefs[Keys.AUTO_OPT] = s.autoOptimize
      prefs[Keys.SHOW_FPS] = s.showFps
      prefs[Keys.POWER_SAVE] = s.powerSaving

      prefs[Keys.CAM_PERSPECTIVE] = s.defaultPerspective.name
      prefs[Keys.CAM_SENS] = s.cameraSensitivity
      prefs[Keys.AIM_SENS] = s.aimSensitivity
      prefs[Keys.INVERT_Y] = s.invertYAxis
      prefs[Keys.TP_DIST] = s.thirdPersonDistance
      prefs[Keys.SHOULDER] = s.shoulderSide.name
      prefs[Keys.CAM_SMOOTH] = s.cameraSmoothing
      prefs[Keys.FOV] = s.fov
      prefs[Keys.CAM_SHAKE] = s.cameraShake

      prefs[Keys.JOY_SENS] = s.joystickSensitivity
      prefs[Keys.TOUCH_AIM_SENS] = s.touchAimSensitivity
      prefs[Keys.BTN_SCALE] = s.buttonScale
      prefs[Keys.BTN_OPACITY] = s.buttonOpacity
      prefs[Keys.CTRL_PRESET] = s.controlLayoutPreset
      prefs[Keys.LEFT_HAND] = s.leftHandedMode
      prefs[Keys.HOLD_AIM] = s.holdToAim

      prefs[Keys.MASTER_VOL] = s.masterVolume
      prefs[Keys.SHOT_VOL] = s.gunshotVolume
      prefs[Keys.HIT_VOL] = s.hitVolume
      prefs[Keys.FX_VOL] = s.effectsVolume
      prefs[Keys.UI_VOL] = s.uiVolume
      prefs[Keys.SPATIAL_AUDIO] = s.spatialAudio
      prefs[Keys.MUTE_ALL] = s.muteAll

      prefs[Keys.CROSS_STYLE] = s.crosshairStyle.name
      prefs[Keys.CROSS_SIZE] = s.crosshairSize
      prefs[Keys.CROSS_COLOR] = s.crosshairColor.name
      prefs[Keys.HIT_MARKER] = s.showHitMarker
      prefs[Keys.DMG_NUMS] = s.showDamageNumbers
      prefs[Keys.AIM_ASSIST] = s.aimAssistStrength
      prefs[Keys.AUTO_RELOAD] = s.autoReload
      prefs[Keys.SHOW_PING] = s.showNetworkPing

      prefs[Keys.LANG] = s.language
      prefs[Keys.HUD_SCALE] = s.hudScale
      prefs[Keys.HUD_LAYOUT] = s.hudLayout
      prefs[Keys.SHOW_KILLFEED] = s.showKillfeed
      prefs[Keys.SAFE_NOTCH] = s.safeNotchPadding
      prefs[Keys.NOTIFICATIONS] = s.notificationsEnabled

      prefs[Keys.SERVER_ADDR] = s.serverAddress
      prefs[Keys.SERVER_REGION] = s.serverRegion
      prefs[Keys.AUTO_RECONNECT] = s.autoReconnect
      prefs[Keys.CONN_ALERTS] = s.connectionAlerts

      prefs[Keys.ACC_REDUCE_SHAKE] = s.reduceCameraShake
      prefs[Keys.ACC_HIGH_CONTRAST] = s.highContrastArena
      prefs[Keys.ACC_ALT_CROSS] = s.alternativeCrosshairColor
      prefs[Keys.ACC_REDUCE_FLASH] = s.reduceFlashIntensity
      prefs[Keys.ACC_SIMPLIFY_FX] = s.simplifyVisualEffects
    }
  }

  suspend fun resetAll() {
    context.settingsDataStore.edit { it.clear() }
  }
}
