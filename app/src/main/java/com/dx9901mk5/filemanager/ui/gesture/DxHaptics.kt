package com.dx9901mk5.filemanager.ui.gesture

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object DxHaptics {
    /**
     * 進入選擇狀態的大振動提示 (長壓滿 2 秒)
     */
    fun strongVibrate(context: Context) {
        vibrate(context, durationMs = 90L, amplitude = 255)
    }

    /**
     * 後續多選疊加的小震動提示 (第二指點擊加入)
     */
    fun lightVibrate(context: Context) {
        vibrate(context, durationMs = 28L, amplitude = 110)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context, durationMs: Long, amplitude: Int) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }
}
