package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.settings.CameraPerspective
import com.example.settings.CrosshairColor
import com.example.settings.CrosshairStyle
import com.example.settings.GameSettings
import com.example.settings.GraphicsQuality
import com.example.settings.ReflectionQuality
import com.example.settings.SettingsCategory
import com.example.settings.ShadowQuality
import com.example.settings.ShoulderSide

@Composable
fun DeepSettingsScreen(
  currentSettings: GameSettings,
  onApplySettings: (GameSettings) -> Unit,
  onResetAll: () -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  var selectedCategory by remember { mutableStateOf(SettingsCategory.GRAPHICS) }
  var draft by remember(currentSettings) { mutableStateOf(currentSettings) }
  var statusSaved by remember { mutableStateOf<String?>(null) }

  fun resetCategory(cat: SettingsCategory) {
    val def = GameSettings()
    draft = when (cat) {
      SettingsCategory.GRAPHICS -> draft.copy(
        qualityPreset = def.qualityPreset,
        renderScale = def.renderScale,
        shadowQuality = def.shadowQuality,
        drawDistance = def.drawDistance,
        reflectionQuality = def.reflectionQuality,
        antiAliasing = def.antiAliasing,
        glowEffects = def.glowEffects,
        motionBlur = def.motionBlur,
        fpsLimit = def.fpsLimit,
        autoOptimize = def.autoOptimize,
        showFps = def.showFps,
        powerSaving = def.powerSaving
      )
      SettingsCategory.CAMERA -> draft.copy(
        defaultPerspective = def.defaultPerspective,
        cameraSensitivity = def.cameraSensitivity,
        aimSensitivity = def.aimSensitivity,
        invertYAxis = def.invertYAxis,
        thirdPersonDistance = def.thirdPersonDistance,
        shoulderSide = def.shoulderSide,
        cameraSmoothing = def.cameraSmoothing,
        fov = def.fov,
        cameraShake = def.cameraShake
      )
      SettingsCategory.CONTROLS -> draft.copy(
        joystickSensitivity = def.joystickSensitivity,
        touchAimSensitivity = def.touchAimSensitivity,
        buttonScale = def.buttonScale,
        buttonOpacity = def.buttonOpacity,
        controlLayoutPreset = def.controlLayoutPreset,
        leftHandedMode = def.leftHandedMode,
        holdToAim = def.holdToAim
      )
      SettingsCategory.AUDIO -> draft.copy(
        masterVolume = def.masterVolume,
        gunshotVolume = def.gunshotVolume,
        hitVolume = def.hitVolume,
        effectsVolume = def.effectsVolume,
        uiVolume = def.uiVolume,
        spatialAudio = def.spatialAudio,
        muteAll = def.muteAll
      )
      SettingsCategory.GAMEPLAY -> draft.copy(
        crosshairStyle = def.crosshairStyle,
        crosshairSize = def.crosshairSize,
        crosshairColor = def.crosshairColor,
        showHitMarker = def.showHitMarker,
        showDamageNumbers = def.showDamageNumbers,
        aimAssistStrength = def.aimAssistStrength,
        autoReload = def.autoReload,
        showNetworkPing = def.showNetworkPing
      )
      SettingsCategory.INTERFACE -> draft.copy(
        language = def.language,
        hudScale = def.hudScale,
        hudLayout = def.hudLayout,
        showKillfeed = def.showKillfeed,
        safeNotchPadding = def.safeNotchPadding,
        notificationsEnabled = def.notificationsEnabled
      )
      SettingsCategory.NETWORK -> draft.copy(
        serverAddress = def.serverAddress,
        serverRegion = def.serverRegion,
        autoReconnect = def.autoReconnect,
        connectionAlerts = def.connectionAlerts
      )
      SettingsCategory.ACCESSIBILITY -> draft.copy(
        reduceCameraShake = def.reduceCameraShake,
        highContrastArena = def.highContrastArena,
        alternativeCrosshairColor = def.alternativeCrosshairColor,
        reduceFlashIntensity = def.reduceFlashIntensity,
        simplifyVisualEffects = def.simplifyVisualEffects
      )
    }
    onApplySettings(draft)
    statusSaved = "Категория сброшена"
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF070B14))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 16.dp, vertical = 10.dp)
  ) {
    // Top Header + Action Buttons ("Применить", "Сбросить категорию", "Сбросить всё")
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
          Text("← Назад", color = Color.White)
        }
        Text(
          text = "НАСТРОЙКИ CHESS ARENA",
          color = Color.White,
          fontSize = 17.sp,
          fontWeight = FontWeight.Black
        )
        if (statusSaved != null) {
          Text(
            text = "• $statusSaved",
            color = Color(0xFF00E676),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { resetCategory(selectedCategory) },
          modifier = Modifier.testTag("reset_category_button")
        ) {
          Text("Сбросить категорию", color = Color(0xFFFFAB00), fontSize = 11.sp)
        }
        OutlinedButton(
          onClick = {
            draft = GameSettings()
            onResetAll()
            statusSaved = "Все настройки сброшены"
          },
          modifier = Modifier.testTag("reset_all_button")
        ) {
          Text("Сбросить всё", color = Color(0xFFFF5252), fontSize = 11.sp)
        }
        Button(
          onClick = {
            onApplySettings(draft)
            statusSaved = "Сохранено"
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF0B1021)),
          modifier = Modifier.testTag("apply_settings_button")
        ) {
          Text("Применить", fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Left Sidebar: 8 Categories
      Column(
        modifier = Modifier
          .width(195.dp)
          .fillMaxHeight()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val categories = listOf(
          SettingsCategory.GRAPHICS to "1. Графика",
          SettingsCategory.CAMERA to "2. Камера (1P/3P)",
          SettingsCategory.CONTROLS to "3. Управление",
          SettingsCategory.AUDIO to "4. Звук",
          SettingsCategory.GAMEPLAY to "5. Игровой процесс",
          SettingsCategory.INTERFACE to "6. Интерфейс",
          SettingsCategory.NETWORK to "7. Сеть",
          SettingsCategory.ACCESSIBILITY to "8. Доступность"
        )
        categories.forEach { (cat, label) ->
          val active = selectedCategory == cat
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                if (active) Color(0x3300E5FF) else Color(0xFF0F172A),
                RoundedCornerShape(10.dp)
              )
              .border(
                1.dp,
                if (active) Color(0xFF00E5FF) else Color(0xFF1E293B),
                RoundedCornerShape(10.dp)
              )
              .clickable { selectedCategory = cat }
              .padding(horizontal = 12.dp, vertical = 10.dp)
          ) {
            Text(
              text = label,
              color = if (active) Color(0xFF00E5FF) else Color.White,
              fontSize = 13.sp,
              fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            )
          }
        }
      }

      // Right Content Panel: Category Controls
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .background(Color(0xFF0F172A), RoundedCornerShape(14.dp))
          .padding(16.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        when (selectedCategory) {
          SettingsCategory.GRAPHICS -> {
            Text("Качество графики и рендеринга", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              GraphicsQuality.entries.forEach { q ->
                FilterChip(
                  selected = draft.qualityPreset == q,
                  onClick = { draft = draft.copy(qualityPreset = q) },
                  label = { Text(q.name) }
                )
              }
            }
            SettingSliderRow("Масштаб рендеринга: ${(draft.renderScale * 100).toInt()}%", draft.renderScale, 0.6f..1.0f) {
              draft = draft.copy(renderScale = it)
            }
            SettingSliderRow("Дальность прорисовки: ${draft.drawDistance.toInt()} м", draft.drawDistance, 25f..65f) {
              draft = draft.copy(drawDistance = it)
            }
            Text("Качество теней и отражений", color = Color.White, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ShadowQuality.entries.forEach { sq ->
                FilterChip(
                  selected = draft.shadowQuality == sq,
                  onClick = { draft = draft.copy(shadowQuality = sq) },
                  label = { Text("Тени: ${sq.name}") }
                )
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ReflectionQuality.entries.forEach { rq ->
                FilterChip(
                  selected = draft.reflectionQuality == rq,
                  onClick = { draft = draft.copy(reflectionQuality = rq) },
                  label = { Text("Отражения: ${rq.name}") }
                )
              }
            }
            Text("Ограничение FPS", color = Color.White, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(30, 45, 60, 90, 120).forEach { fps ->
                FilterChip(
                  selected = draft.fpsLimit == fps,
                  onClick = { draft = draft.copy(fpsLimit = fps) },
                  label = { Text("$fps FPS") }
                )
              }
            }
            SettingSwitchRow("Сглаживание (Anti-Aliasing)", draft.antiAliasing) { draft = draft.copy(antiAliasing = it) }
            SettingSwitchRow("Эффекты свечения (Glow)", draft.glowEffects) { draft = draft.copy(glowEffects = it) }
            SettingSwitchRow("Отображение счётчика FPS", draft.showFps) { draft = draft.copy(showFps = it) }
            SettingSwitchRow("Режим энергосбережения (30 FPS + меньше частиц)", draft.powerSaving) { draft = draft.copy(powerSaving = it) }
          }

          SettingsCategory.CAMERA -> {
            Text("Режим камеры по умолчанию", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              FilterChip(
                selected = draft.defaultPerspective == CameraPerspective.FIRST_PERSON,
                onClick = { draft = draft.copy(defaultPerspective = CameraPerspective.FIRST_PERSON) },
                label = { Text("От 1-го лица (1P)") }
              )
              FilterChip(
                selected = draft.defaultPerspective == CameraPerspective.THIRD_PERSON,
                onClick = { draft = draft.copy(defaultPerspective = CameraPerspective.THIRD_PERSON) },
                label = { Text("От 3-го лица (3P)") }
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              FilterChip(
                selected = draft.shoulderSide == ShoulderSide.RIGHT,
                onClick = { draft = draft.copy(shoulderSide = ShoulderSide.RIGHT) },
                label = { Text("Правое плечо (3P)") }
              )
              FilterChip(
                selected = draft.shoulderSide == ShoulderSide.LEFT,
                onClick = { draft = draft.copy(shoulderSide = ShoulderSide.LEFT) },
                label = { Text("Левое плечо (3P)") }
              )
            }
            SettingSliderRow("Дистанция камеры 3-го лица: ${"%.1f".format(draft.thirdPersonDistance)} м", draft.thirdPersonDistance, 2.2f..5.5f) {
              draft = draft.copy(thirdPersonDistance = it)
            }
            SettingSliderRow("Угол обзора (FOV): ${draft.fov.toInt()}°", draft.fov, 60f..105f) {
              draft = draft.copy(fov = it)
            }
            SettingSliderRow("Чувствительность камеры: ${"%.2f".format(draft.cameraSensitivity)}", draft.cameraSensitivity, 0.3f..2.5f) {
              draft = draft.copy(cameraSensitivity = it)
            }
            SettingSliderRow("Чувствительность в прицеле (ADS): ${"%.2f".format(draft.aimSensitivity)}", draft.aimSensitivity, 0.2f..2.0f) {
              draft = draft.copy(aimSensitivity = it)
            }
            SettingSliderRow("Плавность следования камеры: ${"%.2f".format(draft.cameraSmoothing)}", draft.cameraSmoothing, 0.2f..0.98f) {
              draft = draft.copy(cameraSmoothing = it)
            }
            SettingSliderRow("Сила тряски при отдаче: ${"%.0f".format(draft.cameraShake * 100)}%", draft.cameraShake, 0f..1f) {
              draft = draft.copy(cameraShake = it)
            }
            SettingSwitchRow("Инверсия вертикальной оси Y", draft.invertYAxis) { draft = draft.copy(invertYAxis = it) }
          }

          SettingsCategory.CONTROLS -> {
            Text("Параметры сенсорного управления", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            SettingSliderRow("Чувствительность джойстика: ${"%.2f".format(draft.joystickSensitivity)}", draft.joystickSensitivity, 0.5f..1.8f) {
              draft = draft.copy(joystickSensitivity = it)
            }
            SettingSliderRow("Чувствительность сенсорного прицела: ${"%.2f".format(draft.touchAimSensitivity)}", draft.touchAimSensitivity, 0.4f..2.2f) {
              draft = draft.copy(touchAimSensitivity = it)
            }
            SettingSliderRow("Размер боевых кнопок: ${(draft.buttonScale * 100).toInt()}%", draft.buttonScale, 0.75f..1.35f) {
              draft = draft.copy(buttonScale = it)
            }
            SettingSliderRow("Непрозрачность кнопок: ${(draft.buttonOpacity * 100).toInt()}%", draft.buttonOpacity, 0.35f..1.0f) {
              draft = draft.copy(buttonOpacity = it)
            }
            SettingSwitchRow("Левосторонняя раскладка (для игры левой рукой)", draft.leftHandedMode) {
              draft = draft.copy(leftHandedMode = it)
            }
          }

          SettingsCategory.AUDIO -> {
            Text("Звуковые эффекты (без фоновой музыки)", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            SettingSwitchRow("Полное отключение звука (Mute)", draft.muteAll) { draft = draft.copy(muteAll = it) }
            SettingSliderRow("Общая громкость: ${(draft.masterVolume * 100).toInt()}%", draft.masterVolume, 0f..1f) {
              draft = draft.copy(masterVolume = it)
            }
            SettingSliderRow("Громкость выстрелов: ${(draft.gunshotVolume * 100).toInt()}%", draft.gunshotVolume, 0f..1f) {
              draft = draft.copy(gunshotVolume = it)
            }
            SettingSliderRow("Громкость попаданий: ${(draft.hitVolume * 100).toInt()}%", draft.hitVolume, 0f..1f) {
              draft = draft.copy(hitVolume = it)
            }
            SettingSliderRow("Громкость перезарядки и эффектов: ${(draft.effectsVolume * 100).toInt()}%", draft.effectsVolume, 0f..1f) {
              draft = draft.copy(effectsVolume = it)
            }
            SettingSliderRow("Громкость интерфейса: ${(draft.uiVolume * 100).toInt()}%", draft.uiVolume, 0f..1f) {
              draft = draft.copy(uiVolume = it)
            }
            SettingSwitchRow("Пространственное 3D-стерео звучание", draft.spatialAudio) { draft = draft.copy(spatialAudio = it) }
          }

          SettingsCategory.GAMEPLAY -> {
            Text("Прицел и боевая механика", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              CrosshairStyle.entries.forEach { st ->
                FilterChip(
                  selected = draft.crosshairStyle == st,
                  onClick = { draft = draft.copy(crosshairStyle = st) },
                  label = { Text(st.name) }
                )
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              CrosshairColor.entries.forEach { col ->
                FilterChip(
                  selected = draft.crosshairColor == col,
                  onClick = { draft = draft.copy(crosshairColor = col) },
                  label = { Text(col.name) }
                )
              }
            }
            SettingSliderRow("Размер прицела: ${(draft.crosshairSize * 100).toInt()}%", draft.crosshairSize, 0.6f..1.8f) {
              draft = draft.copy(crosshairSize = it)
            }
            SettingSliderRow("Помощь в прицеливании (Aim Assist): ${(draft.aimAssistStrength * 100).toInt()}%", draft.aimAssistStrength, 0f..0.6f) {
              draft = draft.copy(aimAssistStrength = it)
            }
            SettingSwitchRow("Индикатор попадания (Hit Marker)", draft.showHitMarker) { draft = draft.copy(showHitMarker = it) }
            SettingSwitchRow("Всплывающий урон (Damage Numbers)", draft.showDamageNumbers) { draft = draft.copy(showDamageNumbers = it) }
            SettingSwitchRow("Автоматическая перезарядка", draft.autoReload) { draft = draft.copy(autoReload = it) }
            SettingSwitchRow("Показывать сетевую задержку (Ping)", draft.showNetworkPing) { draft = draft.copy(showNetworkPing = it) }
          }

          SettingsCategory.INTERFACE -> {
            Text("Интерфейс и отображение HUD", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("RU" to "Русский", "EN" to "English").forEach { (code, name) ->
                FilterChip(
                  selected = draft.language == code,
                  onClick = { draft = draft.copy(language = code) },
                  label = { Text(name) }
                )
              }
            }
            SettingSliderRow("Масштаб интерфейса: ${(draft.hudScale * 100).toInt()}%", draft.hudScale, 0.8f..1.25f) {
              draft = draft.copy(hudScale = it)
            }
            SettingSwitchRow("Отображать ленту ликвидаций (Killfeed)", draft.showKillfeed) { draft = draft.copy(showKillfeed = it) }
            SettingSwitchRow("Безопасные отступы под вырез камеры (Notch)", draft.safeNotchPadding) { draft = draft.copy(safeNotchPadding = it) }
            SettingSwitchRow("Звуковые уведомления боя", draft.notificationsEnabled) { draft = draft.copy(notificationsEnabled = it) }
          }

          SettingsCategory.NETWORK -> {
            Text("Сетевое подключение и сервер", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            Text(
              text = "Основной мультиплеер работает через облачный кластер Firebase Firestore в реальном времени. Также можно указать адрес выделенного WebSocket-сервера (Node.js):",
              color = Color(0xFF94A3B8),
              fontSize = 12.sp
            )
            OutlinedTextField(
              value = draft.serverAddress,
              onValueChange = { draft = draft.copy(serverAddress = it) },
              label = { Text("Адрес выделенного WebSocket сервера") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("EU-CENTRAL", "US-EAST", "ASIA-EAST").forEach { reg ->
                FilterChip(
                  selected = draft.serverRegion == reg,
                  onClick = { draft = draft.copy(serverRegion = reg) },
                  label = { Text(reg) }
                )
              }
            }
            SettingSwitchRow("Автоматическое переподключение", draft.autoReconnect) { draft = draft.copy(autoReconnect = it) }
            SettingSwitchRow("Уведомления о сетевых ошибках", draft.connectionAlerts) { draft = draft.copy(connectionAlerts = it) }
          }

          SettingsCategory.ACCESSIBILITY -> {
            Text("Специальные возможности (Доступность)", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            SettingSwitchRow("Отключить тряску камеры при стрельбе", draft.reduceCameraShake) { draft = draft.copy(reduceCameraShake = it) }
            SettingSwitchRow("Повышенная контрастность шахматной доски", draft.highContrastArena) { draft = draft.copy(highContrastArena = it) }
            SettingSwitchRow("Альтернативный высококонтрастный цвет прицела", draft.alternativeCrosshairColor) { draft = draft.copy(alternativeCrosshairColor = it) }
            SettingSwitchRow("Уменьшить яркость вспышек выстрелов", draft.reduceFlashIntensity) { draft = draft.copy(reduceFlashIntensity = it) }
            SettingSwitchRow("Отключить мелкие частицы (для слабых устройств)", draft.simplifyVisualEffects) { draft = draft.copy(simplifyVisualEffects = it) }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingSliderRow(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text = label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = range,
      modifier = Modifier.width(210.dp)
    )
  }
}

@Composable
private fun SettingSwitchRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text = label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
