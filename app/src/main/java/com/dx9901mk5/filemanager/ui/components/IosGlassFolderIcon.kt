package com.dx9901mk5.filemanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * iOS 27 Liquid Glass 風格資料夾圖示：
 * - 透過半透明層疊玻璃、高光折射邊框與霧面漸層呈現液態玻璃質感
 * - 區分「空資料夾」(通透中空玻璃) 與「有內容物資料夾」(內含層疊文件頁片與暖金折射光澤)
 */
@Composable
fun IosGlassFolderIcon(
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFFFC83D)
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val left = w * 0.06f
            val right = w * 0.94f
            val backTop = h * 0.14f
            val shoulderTop = h * 0.24f
            val bottom = h * 0.88f
            val tabRight = w * 0.44f
            val corner = w * 0.11f

            // 1. 後層資料夾背板路徑（含左上標籤）
            val backPath = Path().apply {
                moveTo(left + corner * 0.7f, backTop)
                lineTo(tabRight - corner * 0.6f, backTop)
                quadraticTo(tabRight, backTop, tabRight + w * 0.05f, shoulderTop - h * 0.02f)
                quadraticTo(tabRight + w * 0.08f, shoulderTop, tabRight + w * 0.13f, shoulderTop)
                lineTo(right - corner, shoulderTop)
                quadraticTo(right, shoulderTop, right, shoulderTop + corner)
                lineTo(right, bottom - corner)
                quadraticTo(right, bottom, right - corner, bottom)
                lineTo(left + corner, bottom)
                quadraticTo(left, bottom, left, bottom - corner)
                lineTo(left, backTop + corner * 0.7f)
                quadraticTo(left, backTop, left + corner * 0.7f, backTop)
                close()
            }

            // 背板液態玻璃底色
            val backBrush = if (isEmpty) {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF9EB3C8).copy(alpha = 0.30f),
                        Color(0xFF60748A).copy(alpha = 0.20f)
                    ),
                    start = Offset(left, backTop),
                    end = Offset(right, bottom)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.82f),
                        Color(0xFFD98A00).copy(alpha = 0.68f)
                    ),
                    start = Offset(left, backTop),
                    end = Offset(right, bottom)
                )
            }
            drawPath(path = backPath, brush = backBrush)

            // 背板玻璃邊緣高光
            drawPath(
                path = backPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isEmpty) 0.45f else 0.65f),
                        Color.White.copy(alpha = 0.10f)
                    ),
                    start = Offset(left, backTop),
                    end = Offset(right, bottom)
                ),
                style = Stroke(width = (w * 0.025f).coerceAtLeast(1f))
            )

            // 2. 有內容物時：繪製探出玻璃袋口的多層文件卡片
            if (!isEmpty) {
                val sheetLeft = left + w * 0.08f
                val sheetRight = right - w * 0.08f
                val sheetRadius = CornerRadius(w * 0.055f, w * 0.055f)

                // 後層微傾卡片
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.62f),
                    topLeft = Offset(sheetLeft + w * 0.04f, h * 0.215f),
                    size = Size((sheetRight - sheetLeft) - w * 0.06f, h * 0.26f),
                    cornerRadius = sheetRadius
                )
                // 前層亮白文件卡片
                val frontSheetTop = h * 0.265f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.94f),
                            Color(0xFFEAF2FF).copy(alpha = 0.78f)
                        ),
                        startY = frontSheetTop,
                        endY = frontSheetTop + h * 0.24f
                    ),
                    topLeft = Offset(sheetLeft, frontSheetTop),
                    size = Size(sheetRight - sheetLeft, h * 0.24f),
                    cornerRadius = sheetRadius
                )
                // 文件上的極簡文字線條
                drawLine(
                    color = Color(0xFF7D8FA3).copy(alpha = 0.55f),
                    start = Offset(sheetLeft + w * 0.08f, frontSheetTop + h * 0.055f),
                    end = Offset(sheetRight - w * 0.18f, frontSheetTop + h * 0.055f),
                    strokeWidth = (h * 0.022f).coerceAtLeast(1f),
                    cap = StrokeCap.Round
                )
            }

            // 3. 前層液態玻璃口袋 (Liquid Glass Front Pocket)
            val frontTop = h * 0.345f
            val frontRect = Rect(left, frontTop, right, bottom)
            val frontPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = frontRect,
                        cornerRadius = CornerRadius(corner * 1.05f, corner * 1.05f)
                    )
                )
            }

            // 玻璃口袋漸層填充
            val frontFill = if (isEmpty) {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color(0xFFCFDCEB).copy(alpha = 0.09f),
                        Color.White.copy(alpha = 0.16f)
                    ),
                    start = Offset(left, frontTop),
                    end = Offset(right, bottom)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFE58F).copy(alpha = 0.52f),
                        accentColor.copy(alpha = 0.34f),
                        Color(0xFFFFF3C4).copy(alpha = 0.44f)
                    ),
                    start = Offset(left, frontTop),
                    end = Offset(right, bottom)
                )
            }
            drawPath(path = frontPath, brush = frontFill)

            // 玻璃內部頂端折射光帶與空資料夾虛影標記
            clipPath(frontPath) {
                // 頂部液態高光反射弧
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isEmpty) 0.38f else 0.52f),
                            Color.Transparent
                        ),
                        startY = frontTop,
                        endY = frontTop + h * 0.16f
                    ),
                    topLeft = Offset(left, frontTop),
                    size = Size(right - left, h * 0.16f)
                )

                // 斜向玻璃反光條
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        start = Offset(left, frontTop),
                        end = Offset(w * 0.65f, bottom)
                    ),
                    start = Offset(left + w * 0.08f, frontTop),
                    end = Offset(w * 0.42f, bottom),
                    strokeWidth = w * 0.14f
                )

                // 空資料夾：中央極簡中空凹槽高光，強化「空」的視覺辨識
                if (isEmpty) {
                    val slotW = (right - left) * 0.34f
                    val slotH = (h * 0.055f).coerceAtLeast(2.dp.value)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.28f),
                        topLeft = Offset((w - slotW) / 2f, (frontTop + bottom) / 2f - slotH / 2f),
                        size = Size(slotW, slotH),
                        cornerRadius = CornerRadius(slotH / 2f, slotH / 2f)
                    )
                }
            }

            // 4. 外圈玻璃高光邊框（左上亮、右下柔）
            drawPath(
                path = frontPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isEmpty) 0.68f else 0.85f),
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = if (isEmpty) 0.42f else 0.55f)
                    ),
                    start = Offset(left, frontTop),
                    end = Offset(right, bottom)
                ),
                style = Stroke(width = (w * 0.032f).coerceAtLeast(1.2f))
            )
        }
    }
}
