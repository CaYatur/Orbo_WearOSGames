package com.cayadev.orbo.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Thin, fail-safe wrapper over the system vibrator for tactile game feedback. */
class Haptics(context: Context) {
    var enabled = true
    private val vib: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) { null }

    private fun oneShot(ms: Long, amp: Int) {
        if (!enabled) return
        val v = vib ?: return
        try {
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255)))
        } catch (_: Throwable) {}
    }

    private fun waveform(timings: LongArray, amps: IntArray) {
        if (!enabled) return
        val v = vib ?: return
        try { v.vibrate(VibrationEffect.createWaveform(timings, amps, -1)) } catch (_: Throwable) {}
    }

    fun tick() = oneShot(8, 60)
    fun light() = oneShot(14, 110)
    fun medium() = oneShot(22, 170)
    fun heavy() = oneShot(38, 235)
    fun success() = waveform(longArrayOf(0, 18, 40, 30), intArrayOf(0, 150, 0, 220))
    fun error() = waveform(longArrayOf(0, 40, 50, 40), intArrayOf(0, 220, 0, 220))
}
