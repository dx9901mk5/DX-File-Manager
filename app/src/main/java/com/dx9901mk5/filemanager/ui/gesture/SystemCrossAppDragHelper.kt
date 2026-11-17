package com.dx9901mk5.filemanager.ui.gesture

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import java.io.File
import java.net.URLConnection
import java.util.Locale

/**
 * Android 系統級跨 App 拖曳輔助器：
 * 透過 FileProvider URI + View.DRAG_FLAG_GLOBAL | DRAG_FLAG_GLOBAL_URI_READ，
 * 讓雙 App 並排／多視窗模式下可直接將圖片、文件等檔案拖曳投放至通訊軟體、Office、瀏覽器或社群 App。
 */
object SystemCrossAppDragHelper {
    fun startSystemDrag(
        view: View,
        context: Context,
        files: List<VfsFile>
    ): Boolean {
        val localFiles = files
            .filter { !it.isDirectory && it.mountId == "local" }
            .mapNotNull { vf ->
                val f = File(vf.path)
                if (f.exists() && f.isFile) vf to f else null
            }
        if (localFiles.isEmpty()) return false

        val authority = "${context.packageName}.fileprovider"
        val urisAndMimes = localFiles.mapNotNull { (_, file) ->
            runCatching {
                val uri = FileProvider.getUriForFile(context, authority, file)
                val mime = resolveMimeType(file.name)
                uri to mime
            }.getOrNull()
        }
        if (urisAndMimes.isEmpty()) return false

        val mimeTypes = urisAndMimes.map { it.second }.distinct().toTypedArray()
        val label = if (localFiles.size == 1) {
            localFiles.first().first.name
        } else {
            "${localFiles.size} 個檔案"
        }

        val firstUri: Uri = urisAndMimes.first().first
        val clipDescription = ClipDescription(label, mimeTypes)
        val clipData = ClipData(clipDescription, ClipData.Item(firstUri))
        for (i in 1 until urisAndMimes.size) {
            clipData.addItem(ClipData.Item(urisAndMimes[i].first))
        }

        val shadowBuilder = DxFileDragShadowBuilder(
            density = context.resources.displayMetrics.density,
            label = label,
            count = urisAndMimes.size
        )

        val flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
        return runCatching {
            view.startDragAndDrop(clipData, shadowBuilder, null, flags)
        }.getOrDefault(false)
    }

    private fun resolveMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val byExt = if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } else null
        return byExt
            ?: URLConnection.guessContentTypeFromName(fileName)
            ?: "*/*"
    }

    private class DxFileDragShadowBuilder(
        private val density: Float,
        private val label: String,
        private val count: Int
    ) : View.DragShadowBuilder() {
        private val widthPx = (168 * density).toInt()
        private val heightPx = (52 * density).toInt()

        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(widthPx, heightPx)
            outShadowTouchPoint.set(widthPx / 2, heightPx / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(235, 32, 34, 38)
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 193, 7)
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
            }
            val rect = RectF(
                2f * density,
                2f * density,
                widthPx - 2f * density,
                heightPx - 2f * density
            )
            val radius = 14f * density
            canvas.drawRoundRect(rect, radius, radius, bgPaint)
            canvas.drawRoundRect(rect, radius, radius, borderPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 13f * density
                isFakeBoldText = true
            }
            val display = if (count > 1) "拖曳 $count 個檔案" else label.take(14)
            val textY = heightPx / 2f + (textPaint.textSize / 3f)
            canvas.drawText(display, 14f * density, textY, textPaint)
        }
    }
}
