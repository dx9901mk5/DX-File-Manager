package com.dx9901mk5.filemanager.ui.gesture

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 輕量內建音效產生器（不需外部音訊資源檔）。
 */
object DxSoundEffects {
    /**
     * 播放「倒垃圾 / 清空垃圾桶」小聲音效（約 360ms：沙沙傾倒聲 + 輕快收尾音）。
     */
    fun playTrashEmptySound() {
        Thread {
            runCatching {
                val sampleRate = 44100
                val durationSec = 0.36
                val totalSamples = (sampleRate * durationSec).toInt()
                val pcm = ShortArray(totalSamples)

                var lowPass = 0.0
                val rng = Random(System.nanoTime())

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate

                    // 1) 前半段 (0..220ms)：紙屑／物品滑落傾倒的「刷」聲（帶低通濾波白噪音）
                    val rustleEnv = if (t < 0.24) {
                        val attack = (t / 0.03).coerceAtMost(1.0)
                        val decay = exp(-10.0 * (t - 0.03).coerceAtLeast(0.0))
                        attack * decay
                    } else 0.0
                    val white = rng.nextDouble(-1.0, 1.0)
                    // 濾波截止隨時間下降，呈現「倒出落下」的空間感
                    val alpha = (0.32 - 0.9 * t).coerceIn(0.06, 0.32)
                    lowPass += alpha * (white - lowPass)
                    val rustle = lowPass * rustleEnv * 0.65

                    // 2) 後半段 (120..360ms)：輕快落底「咚刷」小提示音
                    val chimeSample = if (t >= 0.12) {
                        val ct = t - 0.12
                        val freq = 520.0 - 220.0 * (ct / 0.24).coerceAtMost(1.0)
                        val env = exp(-14.0 * ct) * (ct / 0.012).coerceAtMost(1.0)
                        sin(2.0 * PI * freq * ct) * env * 0.35
                    } else 0.0

                    val mixed = (rustle + chimeSample).coerceIn(-0.95, 0.95)
                    pcm[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
                }

                val bufferSizeBytes = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(420L)
                runCatching { track.stop() }
                track.release()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}
