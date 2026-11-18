package com.dx9901mk5.filemanager.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v")
private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus")
private val ARCHIVE_EXTS = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "dxe")
private val DOC_EXTS = setOf("doc", "docx", "txt", "md", "rtf", "odt")
private val SHEET_EXTS = setOf("xls", "xlsx", "csv", "ods")
private val SLIDE_EXTS = setOf("ppt", "pptx", "odp")

private object ThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(160)

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bmp: ImageBitmap) {
        cache.put(key, bmp)
    }
}

/**
 * 依檔案類型顯示實景縮圖（圖片 / 影片 / APK 圖示）或彩色類型圖示。
 */
@Composable
fun FileThumbnailIcon(
    file: VfsFile,
    iconSize: Dp,
    cornerRadius: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val isImage = ext in IMAGE_EXTS
    val isVideo = ext in VIDEO_EXTS
    val isApk = ext == "apk"
    val canPreview = file.mountId == "local" && (isImage || isVideo || isApk)

    val cacheKey = "${file.path}_${file.lastModified}"
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = if (canPreview) ThumbnailCache.get(cacheKey) else null,
        key1 = cacheKey
    ) {
        if (!canPreview) {
            value = null
            return@produceState
        }
        ThumbnailCache.get(cacheKey)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            loadLocalThumbnail(context, file.path, ext)
        }
        if (decoded != null) {
            ThumbnailCache.put(cacheKey, decoded)
            value = decoded
        }
    }

    Box(
        modifier = modifier
            .size(iconSize)
            .clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = thumbnail
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .size((iconSize * 0.42f).coerceAtLeast(14.dp))
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(1.dp).fillMaxSize()
                    )
                }
            }
        } else {
            val (vec, tint) = when {
                isImage -> Icons.Default.Image to Color(0xFF42A5F5)
                isVideo -> Icons.Default.VideoFile to Color(0xFFEF5350)
                ext in AUDIO_EXTS -> Icons.Default.AudioFile to Color(0xFFAB47BC)
                isApk -> Icons.Default.Android to Color(0xFF66BB6A)
                ext == "pdf" -> Icons.Default.PictureAsPdf to Color(0xFFE53935)
                ext in DOC_EXTS -> Icons.Default.Description to Color(0xFF2196F3)
                ext in SHEET_EXTS -> Icons.Default.TableChart to Color(0xFF43A047)
                ext in SLIDE_EXTS -> Icons.Default.Slideshow to Color(0xFFFB8C00)
                ext in ARCHIVE_EXTS -> Icons.Default.Archive to Color(0xFFFFB300)
                else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurface
            }
            Icon(
                imageVector = vec,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size((iconSize * 0.72f).coerceAtLeast(18.dp))
            )
        }
    }
}

private fun loadLocalThumbnail(context: Context, path: String, ext: String): ImageBitmap? {
    val file = File(path)
    if (!file.exists() || !file.isFile) return null
    val targetPx = 192

    return runCatching {
        when {
            ext in IMAGE_EXTS -> decodeSampledImage(path, targetPx)?.asImageBitmap()
            ext in VIDEO_EXTS -> decodeVideoThumbnail(path, targetPx)?.asImageBitmap()
            ext == "apk" -> decodeApkIcon(context, path)?.asImageBitmap()
            else -> null
        }
    }.getOrNull()
}

private fun decodeSampledImage(path: String, targetPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > targetPx * 2 || bounds.outHeight / sampleSize > targetPx * 2) {
        sampleSize *= 2
    }

    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val raw = BitmapFactory.decodeFile(path, opts) ?: return null

    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return raw

    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (rotated != raw) raw.recycle()
    return rotated
}

private fun decodeVideoThumbnail(path: String, targetPx: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                -1L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetPx,
                targetPx
            )
        } else {
            retriever.frameAtTime
        }
        frame
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun decodeApkIcon(context: Context, path: String): Bitmap? {
    val pm: PackageManager = context.packageManager
    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageArchiveInfo(path, 0)
    } ?: return null
    val appInfo = pkgInfo.applicationInfo ?: return null
    appInfo.sourceDir = path
    appInfo.publicSourceDir = path
    val drawable = appInfo.loadIcon(pm)
    return drawable.toBitmap(96, 96)
}

/**
 * 依套件名稱載入已安裝應用程式的真實圖示（搭配 LruCache 快取）。
 */
@Composable
fun InstalledAppIcon(
    packageName: String,
    iconSize: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cacheKey = "app_pkg_$packageName"
    val iconBmp by produceState<ImageBitmap?>(
        initialValue = ThumbnailCache.get(cacheKey),
        key1 = cacheKey
    ) {
        ThumbnailCache.get(cacheKey)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
        if (decoded != null) {
            ThumbnailCache.put(cacheKey, decoded)
            value = decoded
        }
    }

    Box(
        modifier = modifier
            .size(iconSize)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = iconBmp
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = packageName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((iconSize * 0.72f).coerceAtLeast(20.dp))
            )
        }
    }
}
