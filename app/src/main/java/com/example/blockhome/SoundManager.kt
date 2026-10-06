package com.example.blockhome

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager(context: Context) {

    private val appContext = context.applicationContext

    private var enabled = true
    private var volume = 70

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun setVolume(value: Int) {
        volume = value.coerceIn(0, 100)
    }

    fun isEnabled(): Boolean {
        return enabled
    }

    fun getVolume(): Int {
        return volume
    }

    fun playClick() {
        if (!enabled || volume <= 0) return

        try {
            val tone = ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                volume
            )

            tone.startTone(
                ToneGenerator.TONE_PROP_BEEP,
                60
            )

            tone.release()
        } catch (_: Exception) {
            // Sound failure must never affect launcher operation.
        }
    }
}
