package com.atlyn.subterranea.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * SoundManager handles all game audio and haptic feedback.
 * Uses system sounds and synthesized tones for a tactile feel.
 */
class SoundManager(private val context: Context) {
    
    private val soundPool: SoundPool
    private val loadedSounds = mutableMapOf<GameSound, Int>()
    private var soundsLoaded = false
    
    // Vibrator for haptic feedback
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    
    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(audioAttributes)
            .build()
        
        soundPool.setOnLoadCompleteListener { _, _, _ ->
            soundsLoaded = true
        }
    }
    
    /**
     * Play a game sound effect
     */
    fun play(sound: GameSound) {
        // For now, use haptic feedback as primary feedback
        // Sound files can be added later to res/raw
        vibrateForSound(sound)
    }
    
    /**
     * Provide haptic feedback for different game actions
     */
    private fun vibrateForSound(sound: GameSound) {
        try {
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = when (sound) {
                        GameSound.DICE_ROLL -> VibrationEffect.createWaveform(
                            longArrayOf(0, 30, 50, 30, 50, 30, 50, 60),
                            intArrayOf(0, 100, 0, 80, 0, 60, 0, 120),
                            -1
                        )
                        GameSound.RESOURCE_GAIN -> VibrationEffect.createOneShot(40, 80)
                        GameSound.BUILD_STRUCTURE -> VibrationEffect.createWaveform(
                            longArrayOf(0, 50, 30, 80),
                            intArrayOf(0, 150, 0, 200),
                            -1
                        )
                        GameSound.EXPLORE -> VibrationEffect.createOneShot(60, 100)
                        GameSound.BUTTON_TAP -> VibrationEffect.createOneShot(20, 50)
                        GameSound.VICTORY -> VibrationEffect.createWaveform(
                            longArrayOf(0, 100, 100, 100, 100, 200),
                            intArrayOf(0, 200, 0, 200, 0, 255),
                            -1
                        )
                        GameSound.TRADE -> VibrationEffect.createOneShot(50, 120)
                        GameSound.TURN_END -> VibrationEffect.createOneShot(30, 60)
                        GameSound.ERROR -> VibrationEffect.createWaveform(
                            longArrayOf(0, 50, 50, 50),
                            intArrayOf(0, 100, 0, 100),
                            -1
                        )
                        GameSound.ACHIEVEMENT -> VibrationEffect.createWaveform(
                            longArrayOf(0, 80, 60, 80, 60, 150),
                            intArrayOf(0, 180, 0, 180, 0, 255),
                            -1
                        )
                        GameSound.TILE_SELECT -> VibrationEffect.createOneShot(15, 40)
                    }
                    v.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(50)
                }
            }
        } catch (e: SecurityException) {
            // VIBRATE permission not granted - silently ignore
        }
    }
    
    fun release() {
        soundPool.release()
    }
}

/**
 * Enum of all game sound effects
 */
enum class GameSound {
    DICE_ROLL,       // Rolling the dice
    RESOURCE_GAIN,   // Gaining resources from production
    BUILD_STRUCTURE, // Building a structure
    EXPLORE,         // Exploring a new tile
    BUTTON_TAP,      // Generic button tap
    VICTORY,         // Winning the game
    TRADE,           // Trading resources
    TURN_END,        // Ending turn
    ERROR,           // Invalid action
    ACHIEVEMENT,     // Unlocking achievement
    TILE_SELECT      // Selecting a tile
}
