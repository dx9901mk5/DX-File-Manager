package com.dx9901mk5.filemanager.ui.gesture

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object DxHaptics {
    /**
     * 進入多選狀態的大振動提示 (長壓滿 1.5 秒)
     */
    fun strongVibrate(context: Context, view: View? = null) {
        view?.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        vibrate(context, durationMs = 95L, amplitude = 255)
    }

    /**
     * 後續多選疊加的小震動提示 (第二指點擊加入)
     */
    fun lightVibrate(context: Context, view: View? = null) {
        view?.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        vibrate(context, durationMs = 30L, amplitude = 120)
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
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            }
        }
    }
}
