package com.dx9901mk5.filemanager.core.shortcut

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dx9901mk5.filemanager.MainActivity
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import org.json.JSONArray
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

object ShortcutHelper {
    private const val ICON_SIZE = 192
    private const val PREF_GROUPS = "dx_shortcut_groups"
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getShortcutGroup(context: Context, groupId: String): List<VfsFile> {
        val raw = context.getSharedPreferences(PREF_GROUPS, Context.MODE_PRIVATE)
            .getString(groupId, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val f = File(arr.getString(i))
                    if (f.exists()) {
                        add(
                            VfsFile(
                                name = f.name,
                                path = f.absolutePath,
                                isDirectory = f.isDirectory,
                                size = if (f.isDirectory) 0L else f.length(),
                                lastModified = f.lastModified(),
                                mountId = "local"
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun pinFilesToDesktop(context: Context, files: List<VfsFile>, onUnsupported: () -> Unit = {}) {
        val appCtx = context.applicationContext
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(appCtx)) {
            onUnsupported()
            return
        }
        val localFiles = files.filter { it.mountId == "local" }.map { File(it.path) to it.name }
            .filter { it.first.exists() }
        if (localFiles.isEmpty()) {
            onUnsupported()
            return
        }

        worker.execute {
            if (localFiles.size == 1) {
                val (file, displayName) = localFiles.first()
                val shortcut = buildFileShortcut(appCtx, file, displayName)
                mainHandler.post {
                    val ok = ShortcutManagerCompat.requestPinShortcut(appCtx, shortcut, null)
                    val msg = if (ok) {
                        "已請求建立桌面捷徑：$displayName"
                    } else {
                        "啟動器拒絕建立桌面捷徑，請確認系統權限（桌面捷徑）"
                    }
                    Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
                }
            } else {
                // 多選（含複合性檔案類別／資料夾）：批次依序產生對應圖示／縮圖的桌面捷徑
                val shortcuts = localFiles.map { (file, displayName) ->
                    buildFileShortcut(appCtx, file, displayName)
                }
                mainHandler.post {
                    Toast.makeText(
                        appCtx,
                        "正在批次建立 ${shortcuts.size} 個桌面捷徑...",
                        Toast.LENGTH_SHORT
                    ).show()
                    shortcuts.forEachIndexed { idx, shortcut ->
                        mainHandler.postDelayed({
                            ShortcutManagerCompat.requestPinShortcut(appCtx, shortcut, null)
                        }, idx * 280L)
                    }
                }
            }
        }
    }

    private fun buildFileShortcut(
        appCtx: Context,
        file: File,
        displayName: String
    ): ShortcutInfoCompat {
        val bmp = createShortcutBitmap(appCtx, file)
        val shortcutIntent = Intent(appCtx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("shortcut_path", file.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return ShortcutInfoCompat.Builder(appCtx, "dx_shortcut_${file.absolutePath.hashCode()}")
            .setShortLabel(displayName.take(24))
            .setLongLabel(displayName)
            .setIcon(IconCompat.createWithBitmap(bmp))
            .setIntent(shortcutIntent)
            .build()
    }

    private fun createGroupedFolderBitmap(context: Context, files: List<File>): Bitmap {
        val out = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 資料夾頂部標籤與半透明磨砂金黃外框
        val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D97706") }
        canvas.drawRoundRect(RectF(14f, 12f, 92f, 44f), 12f, 12f, tabPaint)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E2430") }
        canvas.drawRoundRect(RectF(10f, 26f, ICON_SIZE - 10f, ICON_SIZE - 10f), 32f, 32f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFC83D")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRoundRect(RectF(12f, 28f, ICON_SIZE - 12f, ICON_SIZE - 12f), 30f, 30f, borderPaint)

        // 2x2 縮圖 / 類型圖示格
        val slotPositions = listOf(
            RectF(26f, 42f, 90f, 106f),
            RectF(102f, 42f, 166f, 106f),
            RectF(26f, 114f, 90f, 178f),
            RectF(102f, 114f, 166f, 178f)
        )
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        files.take(4).forEachIndexed { idx, f ->
            val subBmp = createShortcutBitmap(context, f)
            canvas.drawBitmap(subBmp, null, slotPositions[idx], imgPaint)
        }

        // 右上角數量徽章
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFC83D") }
        canvas.drawCircle(ICON_SIZE - 26f, 28f, 20f, badgePaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 22f
        }
        val countStr = files.size.toString()
        val tb = Rect()
        textPaint.getTextBounds(countStr, 0, countStr.length, tb)
        canvas.drawText(countStr, ICON_SIZE - 26f, 28f - tb.exactCenterY(), textPaint)
        return out
    }

    fun createShortcutBitmap(context: Context, file: File): Bitmap {
        if (file.isDirectory) {
            return drawBadgeIcon(bgColor = Color.parseColor("#F59E0B"), tag = "DIR", subShape = BadgeShape.FOLDER)
        }
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext in IMAGE_EXTS) {
            decodeImageThumbnail(file)?.let { return cropRoundedBitmap(it, drawPlayBadge = false) }
        }
        if (ext in VIDEO_EXTS) {
            decodeVideoThumbnail(file)?.let { return cropRoundedBitmap(it, drawPlayBadge = true) }
            return drawBadgeIcon(bgColor = Color.parseColor("#7C3AED"), tag = ext.uppercase(Locale.ROOT).ifEmpty { "VID" }, subShape = BadgeShape.VIDEO)
        }
        if (ext in APK_EXTS) {
            decodeApkIcon(context, file)?.let { return it }
            return drawBadgeIcon(bgColor = Color.parseColor("#10B981"), tag = "APK", subShape = BadgeShape.DOCUMENT)
        }

        return when (ext) {
            in ARCHIVE_EXTS -> drawBadgeIcon(
                bgColor = Color.parseColor("#D97706"),
                tag = ext.uppercase(Locale.ROOT),
                subShape = BadgeShape.ARCHIVE
            )
            "pdf" -> drawBadgeIcon(Color.parseColor("#DC2626"), "PDF", BadgeShape.DOCUMENT)
            "doc", "docx", "rtf", "odt" -> drawBadgeIcon(Color.parseColor("#2563EB"), ext.uppercase(Locale.ROOT), BadgeShape.DOCUMENT)
            "xls", "xlsx", "csv", "ods" -> drawBadgeIcon(Color.parseColor("#16A34A"), ext.uppercase(Locale.ROOT), BadgeShape.DOCUMENT)
            "ppt", "pptx", "odp" -> drawBadgeIcon(Color.parseColor("#EA580C"), ext.uppercase(Locale.ROOT), BadgeShape.DOCUMENT)
            "txt", "md", "log", "json", "xml", "yaml", "yml", "ini" -> drawBadgeIcon(
                bgColor = Color.parseColor("#475569"),
                tag = ext.uppercase(Locale.ROOT),
                subShape = BadgeShape.DOCUMENT
            )
            in AUDIO_EXTS -> drawBadgeIcon(Color.parseColor("#9333EA"), ext.uppercase(Locale.ROOT), BadgeShape.AUDIO)
            else -> drawBadgeIcon(
                bgColor = Color.parseColor("#334155"),
                tag = ext.uppercase(Locale.ROOT).take(4).ifEmpty { "FILE" },
                subShape = BadgeShape.DOCUMENT
            )
        }
    }

    private fun decodeImageThumbnail(file: File): Bitmap? {
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            val minSide = minOf(opts.outWidth, opts.outHeight)
            var sample = 1
            while (minSide / sample > ICON_SIZE * 2) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        }.getOrNull()
    }

    private fun decodeVideoThumbnail(file: File): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(ICON_SIZE, ICON_SIZE), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(
                    file.absolutePath,
                    android.provider.MediaStore.Images.Thumbnails.MINI_KIND
                )
            }
        }.getOrNull()
    }

    private fun decodeApkIcon(context: Context, file: File): Bitmap? {
        return runCatching {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            pkgInfo.applicationInfo?.apply {
                sourceDir = file.absolutePath
                publicSourceDir = file.absolutePath
            }?.loadIcon(pm)?.let { drawable ->
                val out = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                drawable.setBounds(12, 12, ICON_SIZE - 12, ICON_SIZE - 12)
                drawable.draw(canvas)
                out
            }
        }.getOrNull()
    }

    private fun cropRoundedBitmap(src: Bitmap, drawPlayBadge: Boolean): Bitmap {
        val out = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val clipPath = Path().apply {
            addRoundRect(RectF(0f, 0f, ICON_SIZE.toFloat(), ICON_SIZE.toFloat()), 36f, 36f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        val scale = maxOf(ICON_SIZE.toFloat() / src.width, ICON_SIZE.toFloat() / src.height)
        val drawW = src.width * scale
        val drawH = src.height * scale
        val left = (ICON_SIZE - drawW) / 2f
        val top = (ICON_SIZE - drawH) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, null, RectF(left, top, left + drawW, top + drawH), paint)
        canvas.restore()

        if (drawPlayBadge) {
            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 0, 0, 0) }
            val cx = ICON_SIZE / 2f
            val cy = ICON_SIZE / 2f
            canvas.drawCircle(cx, cy, 34f, circlePaint)
            val triPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val tri = Path().apply {
                moveTo(cx - 10f, cy - 16f)
                lineTo(cx + 18f, cy)
                lineTo(cx - 10f, cy + 16f)
                close()
            }
            canvas.drawPath(tri, triPaint)
        }
        return out
    }

    private enum class BadgeShape { DOCUMENT, ARCHIVE, FOLDER, AUDIO, VIDEO }

    private fun drawBadgeIcon(bgColor: Int, tag: String, subShape: BadgeShape): Bitmap {
        val out = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRoundRect(RectF(8f, 8f, ICON_SIZE - 8f, ICON_SIZE - 8f), 38f, 38f, bgPaint)

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255) }
        when (subShape) {
            BadgeShape.FOLDER -> {
                canvas.drawRoundRect(RectF(32f, 44f, 96f, 70f), 10f, 10f, fgPaint)
                canvas.drawRoundRect(RectF(32f, 56f, ICON_SIZE - 32f, ICON_SIZE - 36f), 16f, 16f, fgPaint)
            }
            BadgeShape.ARCHIVE -> {
                // 壓縮檔拉鍊條紋
                val zipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 255, 255, 255) }
                for (y in 24..72 step 14) {
                    val x = if ((y / 14) % 2 == 0) 82f else 96f
                    canvas.drawRect(x, y.toFloat(), x + 14f, (y + 9).toFloat(), zipPaint)
                }
            }
            BadgeShape.AUDIO -> {
                val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(75, 255, 255, 255) }
                canvas.drawCircle(76f, 76f, 16f, notePaint)
                canvas.drawRect(86f, 34f, 96f, 76f, notePaint)
                canvas.drawRect(86f, 34f, 120f, 46f, notePaint)
            }
            BadgeShape.VIDEO, BadgeShape.DOCUMENT -> {
                // 右上角摺頁效果
                val corner = Path().apply {
                    moveTo(ICON_SIZE - 54f, 8f)
                    lineTo(ICON_SIZE - 8f, 54f)
                    lineTo(ICON_SIZE - 54f, 54f)
                    close()
                }
                canvas.drawPath(corner, fgPaint)
            }
        }

        // 底部清晰類型標籤
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 0, 0, 0) }
        canvas.drawRoundRect(RectF(26f, 106f, ICON_SIZE - 26f, ICON_SIZE - 26f), 18f, 18f, pillPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when {
                tag.length <= 3 -> 42f
                tag.length == 4 -> 34f
                else -> 28f
            }
        }
        val bounds = Rect()
        textPaint.getTextBounds(tag, 0, tag.length, bounds)
        val centerY = 106f + (ICON_SIZE - 26f - 106f) / 2f + bounds.height() / 2f - 2f
        canvas.drawText(tag.take(5), ICON_SIZE / 2f, centerY, textPaint)
        return out
    }

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts")
    private val ARCHIVE_EXTS = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "dxe")
    private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma")
    private val APK_EXTS = setOf("apk", "apks", "xapk")
}
