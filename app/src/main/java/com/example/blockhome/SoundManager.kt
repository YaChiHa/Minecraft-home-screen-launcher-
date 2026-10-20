package com.example.blockhome

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val soundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var enabled = true

    @Volatile
    private var volume = 70

    // Keep all ToneGenerator operations on one background thread.
    private var toneGenerator: ToneGenerator? = null

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun setVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        try {
            soundExecutor.execute {
                try {
                    // ToneGenerator volume is set at construction, so recreate it
                    // on the audio thread when the slider changes.
                    toneGenerator?.release()
                    toneGenerator = null
                } catch (_: Exception) {
                    toneGenerator = null
                }
            }
        } catch (_: Exception) {
            // Ignore calls after shutdown.
        }
    }

    fun isEnabled(): Boolean = enabled

    fun getVolume(): Int = volume

    fun playClick() {
        if (!enabled || volume <= 0) return

        try {
            soundExecutor.execute {
                if (!enabled || volume <= 0) return@execute

                try {
                    val tone = toneGenerator ?: ToneGenerator(
                        AudioManager.STREAM_NOTIFICATION,
                        volume
                    ).also { toneGenerator = it }

                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                } catch (_: Exception) {
                    // Sound failure must never affect launcher operation.
                }
            }
        } catch (_: Exception) {
            // Ignore calls after shutdown; launcher interaction must remain responsive.
        }
    }

    fun release() {
        try {
            soundExecutor.execute {
                try {
                    toneGenerator?.release()
                } catch (_: Exception) {
                    // Ignore audio cleanup failures.
                } finally {
                    toneGenerator = null
                }
            }
            soundExecutor.shutdown()
        } catch (_: Exception) {
            // Cleanup must never affect launcher operation.
        }
    }
}
