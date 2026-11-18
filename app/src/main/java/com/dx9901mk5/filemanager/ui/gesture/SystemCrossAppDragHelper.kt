package com.dx9901mk5.filemanager.ui.gesture

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.view.DragEvent
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import com.dx9901mk5.filemanager.MainActivity
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import java.io.File
import java.net.URLConnection
import java.util.Locale

/**
 * Android 系統級跨 App / 跨視窗拖曳輔助器：
 * 透過 FileProvider URI + View.DRAG_FLAG_GLOBAL | DRAG_FLAG_GLOBAL_URI_READ，
 * 支援三星 DeX / 多開自由小視窗間互相拖曳交互，以及拖曳至通訊軟體、Office、瀏覽器等 App；
 * 若目標位置未消費拖曳事件或一次僅接收單檔（如 LINE），自動由檔案管理器接手批次分享傳遞。
 */
object SystemCrossAppDragHelper {
    data class DxDragSession(
        val sourceViewIdentity: Int,
        val files: List<VfsFile>,
        @Volatile var acceptedByDxWindow: Boolean = false,
        @Volatile var leftSourceWindow: Boolean = false
    )

    val batchQueueFiles = mutableStateListOf<VfsFile>()

    private const val WINDOWING_MODE_FREEFORM = 5

    // taskId -> slot (2..9)
    private val taskSlotMap = mutableMapOf<Int, Int>()

    @Synchronized
    private fun pruneDeadTasks(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val aliveIds = runCatching {
            am.appTasks.mapNotNull { it.taskInfo?.taskId }.toSet()
        }.getOrNull() ?: return
        if (aliveIds.isNotEmpty()) {
            taskSlotMap.keys.retainAll(aliveIds)
        }
    }

    @Synchronized
    fun allocateWindowSlot(
        context: Context,
        taskId: Int,
        preferredSlot: Int? = null,
        isMainLauncher: Boolean = false
    ): Int {
        pruneDeadTasks(context)
        taskSlotMap[taskId]?.let { return it }

        val usedSlots = taskSlotMap.values.toSet()
        if (isMainLauncher && 2 !in usedSlots) {
            taskSlotMap[taskId] = 2
            return 2
        }
        if (preferredSlot != null && preferredSlot in 2..9 && preferredSlot !in usedSlots) {
            taskSlotMap[taskId] = preferredSlot
            return preferredSlot
        }
        val next = computeNextSlot(usedSlots) ?: 9
        taskSlotMap[taskId] = next
        return next
    }

    @Synchronized
    fun releaseWindowSlot(taskId: Int) {
        taskSlotMap.remove(taskId)
    }

    @Synchronized
    fun activeWindowCount(context: Context): Int {
        pruneDeadTasks(context)
        return taskSlotMap.size
    }

    @Synchronized
    fun peekNextWindowSlot(context: Context): Int? {
        pruneDeadTasks(context)
        return computeNextSlot(taskSlotMap.values.toSet())
    }

    private fun computeNextSlot(usedSlots: Set<Int>): Int? {
        if (usedSlots.size >= 8) return null
        val maxUsed = usedSlots.maxOrNull() ?: 1
        for (slot in (maxUsed + 1)..9) {
            if (slot !in usedSlots) return slot
        }
        return (2..9).firstOrNull { it !in usedSlots }
    }

    private const val PREF_SMALL_WIN = "dx_small_window_prefs"
    private const val KEY_WIN_W_DP = "small_win_w_dp"
    private const val KEY_WIN_H_DP = "small_win_h_dp"
    private const val KEY_SUPPRESS_FREEFORM_GUIDE = "suppress_freeform_guide"

    fun isFreeformGuideSuppressed(context: Context): Boolean {
        return context.getSharedPreferences(PREF_SMALL_WIN, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUPPRESS_FREEFORM_GUIDE, false)
    }

    fun setFreeformGuideSuppressed(context: Context, suppressed: Boolean) {
        context.getSharedPreferences(PREF_SMALL_WIN, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUPPRESS_FREEFORM_GUIDE, suppressed)
            .apply()
    }

    fun saveSmallWindowSizeDp(context: Context, widthDp: Int, heightDp: Int) {
        val w = widthDp.coerceIn(220, 640)
        val h = heightDp.coerceIn(280, 760)
        context.getSharedPreferences(PREF_SMALL_WIN, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_WIN_W_DP, w)
            .putInt(KEY_WIN_H_DP, h)
            .apply()
    }

    fun getSavedSmallWindowSizeDp(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREF_SMALL_WIN, Context.MODE_PRIVATE)
        val w = prefs.getInt(KEY_WIN_W_DP, 260).coerceIn(220, 640)
        val h = prefs.getInt(KEY_WIN_H_DP, 360).coerceIn(280, 760)
        return w to h
    }

    fun clearBatchQueue() {
        batchQueueFiles.clear()
    }

    fun openNewWindow(context: Context) {
        val nextSlot = peekNextWindowSlot(context)
        if (nextSlot == null) {
            Toast.makeText(context, "最多同時開啟 8 個小視窗（第 2～9 分區）", Toast.LENGTH_SHORT).show()
            return
        }
        val dm = context.resources.displayMetrics
        val density = dm.density
        val (savedWDp, savedHDp) = getSavedSmallWindowSizeDp(context)
        val winW = (savedWDp * density).toInt().coerceAtMost((dm.widthPixels * 0.62f).toInt())
        val winH = (savedHDp * density).toInt().coerceAtMost((dm.heightPixels * 0.65f).toInt())
        val cascade = ((nextSlot - 2) % 4)
        val stepPx = (24 * density).toInt()
        val left = ((dm.widthPixels - winW) / 2 + (cascade - 1) * stepPx).coerceAtLeast(16)
        val top = ((dm.heightPixels - winH) / 2 + (cascade - 1) * stepPx).coerceAtLeast(48)
        val launchBounds = Rect(left, top, left + winW, top + winH)

        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("dxfilemanager://window/$nextSlot")
            putExtra("extra_window_slot", nextSlot)
            putExtra("extra_small_window_mode", true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        }
        val options = ActivityOptions.makeBasic().apply {
            setLaunchBounds(launchBounds)
            // 明確指定 WINDOWING_MODE_FREEFORM (5)，避免一般平板模式預設全螢幕開啟
            runCatching {
                javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    .invoke(this, WINDOWING_MODE_FREEFORM)
            }
        }
        runCatching { context.startActivity(intent, options.toBundle()) }
            .onFailure {
                runCatching { context.startActivity(intent) }
            }
    }

    fun attachWindowDropReceiver(
        view: View,
        onDxFilesDropped: (List<VfsFile>) -> Unit,
        onExternalUrisDropped: (List<Uri>) -> Unit
    ) {
        val myIdentity = System.identityHashCode(view)
        view.setOnDragListener { v, event ->
            val session = event.localState as? DxDragSession
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_EXITED -> {
                    if (session != null && session.sourceViewIdentity == myIdentity) {
                        session.leftSourceWindow = true
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (session != null && session.sourceViewIdentity == myIdentity) {
                        session.leftSourceWindow = false
                    }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    if (session != null) {
                        if (session.sourceViewIdentity == myIdentity) {
                            return@setOnDragListener false
                        }
                        session.acceptedByDxWindow = true
                        onDxFilesDropped(session.files)
                        return@setOnDragListener true
                    }
                    val clip = event.clipData ?: return@setOnDragListener false
                    val uris = buildList {
                        for (i in 0 until clip.itemCount) {
                            clip.getItemAt(i).uri?.let { add(it) }
                        }
                    }
                    if (uris.isEmpty()) return@setOnDragListener false
                    onExternalUrisDropped(uris)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    if (session != null &&
                        session.sourceViewIdentity == myIdentity &&
                        session.leftSourceWindow &&
                        !session.acceptedByDxWindow
                    ) {
                        if (!event.result) {
                            shareFilesViaSystem(v.context, session.files)
                        } else if (session.files.size > 1) {
                            batchQueueFiles.clear()
                            batchQueueFiles.addAll(session.files.drop(1))
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

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

        val mimeTypes = (urisAndMimes.map { it.second } + ClipDescription.MIMETYPE_TEXT_URILIST)
            .distinct()
            .toTypedArray()
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

        val session = DxDragSession(
            sourceViewIdentity = System.identityHashCode(view),
            files = localFiles.map { it.first }
        )

        val flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
        return runCatching {
            view.startDragAndDrop(clipData, shadowBuilder, session, flags)
        }.getOrDefault(false)
    }

    fun shareFilesViaSystem(context: Context, files: List<VfsFile>): Boolean {
        val localFiles = files
            .filter { !it.isDirectory && it.mountId == "local" }
            .mapNotNull { vf ->
                val f = File(vf.path)
                if (f.exists() && f.isFile) f else null
            }
        if (localFiles.isEmpty()) return false

        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList<Uri>(localFiles.size)
        val mimes = mutableListOf<String>()
        for (file in localFiles) {
            runCatching {
                uris.add(FileProvider.getUriForFile(context, authority, file))
                mimes.add(resolveMimeType(file.name))
            }
        }
        if (uris.isEmpty()) return false

        val commonMime = computeCommonMimeType(mimes)
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = commonMime
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(
            intent,
            if (uris.size == 1) "傳送檔案" else "批次傳送 ${uris.size} 個檔案"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private fun computeCommonMimeType(mimes: List<String>): String {
        if (mimes.isEmpty()) return "*/*"
        val distinct = mimes.distinct()
        if (distinct.size == 1) return distinct.first()
        val prefixes = distinct.map { it.substringBefore('/') }.distinct()
        return if (prefixes.size == 1 && prefixes.first() != "*") "${prefixes.first()}/*" else "*/*"
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
