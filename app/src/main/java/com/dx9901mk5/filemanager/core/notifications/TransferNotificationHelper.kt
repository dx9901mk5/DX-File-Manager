package com.dx9901mk5.filemanager.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dx9901mk5.filemanager.ui.dialogs.formatFileSize
import java.util.concurrent.atomic.AtomicInteger

/**
 * 檔案傳輸（複製 / 移動 / 上傳 / 下載）通知欄進度條管理員
 */
class TransferNotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "dx_file_transfer_channel"
        private const val CHANNEL_NAME = "檔案傳輸進度"
        private val nextId = AtomicInteger(1001)

        fun allocateNotificationId(): Int = nextId.getAndIncrement()
    }

    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "顯示檔案複製、移動、上傳與下載的即時進度條"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showProgress(
        notificationId: Int,
        title: String,
        currentFileName: String,
        currentIndex: Int,
        totalCount: Int,
        bytesTransferred: Long,
        totalBytes: Long,
        isUpload: Boolean
    ) {
        if (!hasNotificationPermission()) return

        val percent = when {
            totalBytes > 0L -> ((bytesTransferred * 100L) / totalBytes).toInt().coerceIn(0, 100)
            totalCount > 0 -> ((currentIndex * 100) / totalCount).coerceIn(0, 100)
            else -> 0
        }
        val sizeInfo = if (totalBytes > 0L) {
            " • ${formatFileSize(bytesTransferred)} / ${formatFileSize(totalBytes)}"
        } else {
            ""
        }
        val contentText = "$currentFileName ($currentIndex/$totalCount)$sizeInfo • $percent%"

        val iconRes = if (isUpload) {
            android.R.drawable.stat_sys_upload
        } else {
            android.R.drawable.stat_sys_download
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText("$percent%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, totalBytes <= 0L && currentIndex == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showCompleted(
        notificationId: Int,
        title: String,
        summary: String,
        isSuccess: Boolean
    ) {
        if (!hasNotificationPermission()) return

        val iconRes = if (isSuccess) {
            android.R.drawable.stat_sys_download_done
        } else {
            android.R.drawable.stat_notify_error
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(summary)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
