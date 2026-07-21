package com.rakshak.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Match alerting via vibration. A volunteer scanning a moving crowd is watching
 * the crowd, not the screen — the buzz is what tells them a candidate appeared.
 */
object Haptics {

    /** Double pulse, distinct from an ordinary notification buzz. */
    private val MATCH_PATTERN = longArrayOf(0, 200, 120, 200)

    fun vibrateMatch(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        // VibrationEffect requires API 26; minSdk is 24, so keep the legacy path.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(MATCH_PATTERN, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(MATCH_PATTERN, -1)
        }
    }

    private fun vibrator(context: Context): Vibrator? {
        val app = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
