package com.lemon.chat.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// 连续曲率圆角（squircle）
// 普通圆角是"直边 + 圆弧"，交界处曲率从 0 突然跳到 1/r，眼睛能看出折点；
// 这里用幂次曲线 u=a·cos^m(t), v=a·sin^m(t)（m=4.5），曲率从 0 平滑爬升再平滑收回，
// 与直边、与对角方向都是连续过渡，全程无方角、无曲率折点。
// 锚点外扩：曲线起点在距角点 a=r×1.6 的直边上，小圆角也能平滑融入直边。
class SmoothCornerShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val spread: Float = 1.6f, // 锚点外扩倍数
) : Shape {
    constructor(radius: Dp) : this(radius, radius, radius, radius)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val half = min(size.width, size.height) / 2f
        fun anchor(r: Dp) = min(with(density) { r.toPx() } * spread, half)
        val aTL = anchor(topStart)
        val aTR = anchor(topEnd)
        val aBR = anchor(bottomEnd)
        val aBL = anchor(bottomStart)

        val path = Path()
        path.moveTo(aTL, 0f) // 左上角出口锚点
        // 上边 → 右上角
        path.lineTo(size.width - aTR, 0f)
        corner(path, size.width, 0f, aTR, inx = 1f, outy = 1f)
        // 右边 → 右下角
        path.lineTo(size.width, size.height - aBR)
        corner(path, size.width, size.height, aBR, iny = 1f, outx = -1f)
        // 下边 → 左下角
        path.lineTo(aBL, size.height)
        corner(path, 0f, size.height, aBL, inx = -1f, outy = -1f)
        // 左边 → 左上角（回到起点）
        path.lineTo(0f, aTL)
        corner(path, 0f, 0f, aTL, iny = -1f, outx = 1f)
        path.close()
        return Outline.Generic(path)
    }

    // 从进入边锚点平滑走到离开边锚点。in=(指向角点的边方向)，out=(离开角的边方向)
    private fun corner(
        path: Path,
        cx: Float, cy: Float,
        a: Float,
        inx: Float = 0f, iny: Float = 0f,
        outx: Float = 0f, outy: Float = 0f,
    ) {
        val steps = 14
        for (i in 1..steps) {
            val t = Math.PI * i / steps
            val u = (a * cos(t).pow(4.5)).toFloat() // 沿进入边，从 a 收到 0
            val v = (a * sin(t).pow(4.5)).toFloat() // 沿离开边，从 0 涨到 a
            path.lineTo(cx - u * inx + v * outx, cy - u * iny + v * outy)
        }
    }
}

/**
 * 连续曲率形状专用描边。
 *
 * 不能用系统 Modifier.border()：它遇到非 CornerBasedShape 的轮廓时，会先创建一张
 * 与组件同尺寸的 ImageBitmap 再描边，组件首帧尺寸为 0 时就抛
 * "width and height must be > 0" 崩溃（安卓 9 上已实锤）。
 * 这里直接用矢量轮廓描边，零位图分配、无尺寸限制。
 */
fun Modifier.smoothBorder(strokeWidth: Dp, color: Color, shape: Shape): Modifier =
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            drawOutline(outline, color, style = Stroke(width = strokeWidth.toPx()))
        }
    }
