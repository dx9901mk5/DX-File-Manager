package com.dx9901mk5.filemanager.ui.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * iOS 風格垂直清單/網格回彈效果：
 * 滑動至頂部或最底部時產生阻尼拉伸，放手或慣性撞底後以彈簧動畫回彈歸位。
 */
fun Modifier.dxVerticalOverscrollBounce(): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxOverscrollPx = with(density) { 120.dp.toPx() }
    val overscrollOffset = remember { Animatable(0f) }

    val connection = remember(maxOverscrollPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val current = overscrollOffset.value
                if (current == 0f || available.y == 0f) return Offset.Zero

                // 若使用者往反方向滑動拉回，優先消耗超出的位移
                val pullingBack = (current > 0f && available.y < 0f) || (current < 0f && available.y > 0f)
                if (!pullingBack) return Offset.Zero

                val next = current + available.y * 0.65f
                val clamped = if ((current > 0f && next < 0f) || (current < 0f && next > 0f)) 0f else next
                val consumedY = (clamped - current) / 0.65f
                scope.launch { overscrollOffset.snapTo(clamped) }
                return Offset(0f, consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    val current = overscrollOffset.value
                    val resistance = 0.42f * (1f - (abs(current) / maxOverscrollPx).coerceIn(0f, 0.85f))
                    val target = (current + available.y * resistance).coerceIn(-maxOverscrollPx, maxOverscrollPx)
                    scope.launch { overscrollOffset.snapTo(target) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (overscrollOffset.value != 0f) {
                    overscrollOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (overscrollOffset.value == 0f && abs(available.y) > 400f) {
                    // 慣性高速滑到底/頂時給予微幅撞擊回彈衝量，單次平順歸位
                    val impulse = (available.y * 0.018f).coerceIn(-maxOverscrollPx * 0.45f, maxOverscrollPx * 0.45f)
                    overscrollOffset.snapTo(impulse)
                }
                if (overscrollOffset.value != 0f) {
                    overscrollOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                return Velocity.Zero
            }
        }
    }

    this
        .clipToBounds()
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = overscrollOffset.value
        }
}
