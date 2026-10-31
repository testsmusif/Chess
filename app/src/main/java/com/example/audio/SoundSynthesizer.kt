package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.settings.GameSettings
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural real-time digital sound effect synthesizer using Android's AudioTrack.
 * Generates gunshots, reloads, metallic ricochets, player hit markers, death sounds,
 * and subtle UI clicks with 3D stereo panning and zero external asset dependencies.
 */
object SoundSynthesizer {
  private const val SAMPLE_RATE = 22050

  fun playGunshot(settings: GameSettings, weaponType: String = "PULSE_RIFLE", pan: Float = 0f, distanceFactor: Float = 1f) {
    if (settings.muteAll) return
    val gain = (settings.masterVolume * settings.gunshotVolume * distanceFactor).coerceIn(0f, 1f)
    if (gain <= 0.01f) return

    val durationMs = when (weaponType) {
      "SHOTGUN_ROOK" -> 210
      "RAIL_PISTOL" -> 160
      else -> 130
    }
    playPcmAsync(durationMs, gain, if (settings.spatialAudio) pan else 0f) { i, total ->
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / total
      val env = exp(-progress * 10.0)
      val freq = when (weaponType) {
        "SHOTGUN_ROOK" -> 110.0 + 320.0 * exp(-progress * 18.0)
        "RAIL_PISTOL" -> 680.0 * exp(-progress * 8.0) + 180.0
        else -> 420.0 * exp(-progress * 12.0) + 130.0
      }
      val tone = sin(2.0 * PI * freq * t)
      val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.65
      ((tone * 0.55 + noise * 0.45) * env).toFloat()
    }
  }

  fun playReload(settings: GameSettings) {
    if (settings.muteAll) return
    val gain = (settings.masterVolume * settings.effectsVolume).coerceIn(0f, 1f)
    if (gain <= 0.01f) return

    playPcmAsync(260, gain, 0f) { i, total ->
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / total
      // Two metallic clicks at 0.05 and 0.60 progress
      val click1 = if (progress < 0.25) exp(-progress * 35.0) * sin(2.0 * PI * 920.0 * t) else 0.0
      val click2 = if (progress > 0.55) exp(-(progress - 0.55) * 30.0) * sin(2.0 * PI * 1340.0 * t) else 0.0
      (click1 + click2).toFloat()
    }
  }

  fun playHitObject(settings: GameSettings, pan: Float = 0f) {
    if (settings.muteAll) return
    val gain = (settings.masterVolume * settings.hitVolume * 0.7f).coerceIn(0f, 1f)
    if (gain <= 0.01f) return

    playPcmAsync(90, gain, if (settings.spatialAudio) pan else 0f) { i, total ->
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / total
      val env = exp(-progress * 16.0)
      val metallic = sin(2.0 * PI * 1950.0 * t) * 0.6 + sin(2.0 * PI * 2870.0 * t) * 0.4
      (metallic * env).toFloat()
    }
  }

  fun playHitPlayer(settings: GameSettings, isElimination: Boolean = false) {
    if (settings.muteAll) return
    val gain = (settings.masterVolume * settings.hitVolume).coerceIn(0f, 1f)
    if (gain <= 0.01f) return

    val duration = if (isElimination) 180 else 85
    playPcmAsync(duration, gain, 0f) { i, total ->
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / total
      val env = exp(-progress * 12.0)
      val baseFreq = if (isElimination) 880.0 + progress * 440.0 else 1120.0
      (sin(2.0 * PI * baseFreq * t) * env).toFloat()
    }
  }

  fun playDeath(settings: GameSettings) {
    if (settings.muteAll) return
    val gain = (settings.masterVolume * settings.effectsVolume).coerceIn(0f, 1f)
    if (gain <= 0.01f) return

    playPcmAsync(420, gain, 0f) { i, total ->
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / total
      val env = (1.0 - progress)
      val freq = 320.0 * (1.0 - progress * 0.65)
      (sin(2.0 * PI * freq * t) * env * 0.8).toFloat()
    }
  }

  fun playNotification(settings: GameSettings) {
    if (settings.muteAll || !settings.notificationsEnabled) return
    val gain = (settings.masterVolume * settings.effectsVolume * 0.65f).coerceIn(0f, 1f)
    if (gain <= 0.01f) return

    playPcmAsync(180, gain, 0f) { i, total ->
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / total
      val freq = if (progress < 0.5) 587.33 else 880.0
      val env = exp(-(progress % 0.5) * 8.0)
      (sin(2.0 * PI * freq * t) * env * 0.6).toFloat()
    }
  }

  fun playUiClick(settings: GameSettings) {
    if (settings.muteAll) return
    val gain = (settings.masterVolume * settings.uiVolume * 0.45f).coerceIn(0f, 1f)
    if (gain <= 0.01f) return

    playPcmAsync(35, gain, 0f) { i, total ->
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / total
      val env = exp(-progress * 22.0)
      (sin(2.0 * PI * 1480.0 * t) * env).toFloat()
    }
  }

  private fun playPcmAsync(
    durationMs: Int,
    gain: Float,
    pan: Float,
    generator: (sampleIndex: Int, totalSamples: Int) -> Float
  ) {
    thread(isDaemon = true) {
      try {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        // Stereo 16-bit PCM (2 channels per frame)
        val pcm = ShortArray(numSamples * 2)
        val clampedPan = pan.coerceIn(-1f, 1f)
        val leftGain = gain * (if (clampedPan <= 0f) 1f else (1f - clampedPan))
        val rightGain = gain * (if (clampedPan >= 0f) 1f else (1f + clampedPan))

        for (i in 0 until numSamples) {
          val raw = generator(i, numSamples).coerceIn(-1f, 1f)
          pcm[i * 2] = (raw * leftGain * Short.MAX_VALUE).toInt().toShort()
          pcm[i * 2 + 1] = (raw * rightGain * Short.MAX_VALUE).toInt().toShort()
        }

        val bufferSizeBytes = pcm.size * 2
        val track = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(SAMPLE_RATE)
              .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
              .build()
          )
          .setBufferSizeInBytes(bufferSizeBytes.coerceAtLeast(AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
          )))
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()

        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((durationMs + 40).toLong())
        track.stop()
        track.release()
      } catch (_: Exception) {
        // Ignore audio hardware unavailability in headless tests
      }
    }
  }
}
