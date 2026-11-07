package com.lemon.chat.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 连续曲率圆角（squircle，苹果 iOS 同款"平滑圆角"）。
 *
 * 原理：普通圆角是四分之一圆弧，弧线与直边相接处曲率突变（0 → 1/r），
 * 肉眼能看到"拐角感"。本 Shape 用幂函数曲线 u=a·cos^m(θ)、v=a·sin^m(θ)
 * （m=4.5 > 3），曲线两端与直边相接时曲率连续过渡为 0，全程无突变、无方角。
 *
 * smoothing：锚点外扩系数，曲线在直边上的起点距角点 a = r × smoothing，
 * 值越大角越"方润"，1.0 时退化为普通圆弧。默认 1.6。
 */
class SmoothCornerShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val smoothing: Float = 1.6f,
) : Shape {
    constructor(radius: Dp, smoothing: Float = 1.6f) : this(radius, radius, radius, radius, smoothing)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        // 锚点最多占短边一半，两个角不能互相吃掉
        val half = min(w, h) / 2f
        fun anchor(r: Dp): Float = min(with(density) { r.toPx() } * smoothing, half)
        val aTL = anchor(topStart)
        val aTR = anchor(topEnd)
        val aBR = anchor(bottomEnd)
        val aBL = anchor(bottomStart)

        val path = Path()
        path.moveTo(aTL, 0f)
        // 上边 → 右上角（进入边朝 +x，离开边朝 +y）
        path.lineTo(w - aTR, 0f)
        corner(path, w, 0f, aTR, inX = 1f, outY = 1f)
        // 右边 → 右下角
        path.lineTo(w, h - aBR)
        corner(path, w, h, aBR, inX = 0f, inY = 1f, outX = -1f)
        // 下边 → 左下角
        path.lineTo(aBL, h)
        corner(path, 0f, h, aBL, inX = -1f, inY = 0f, outY = -1f)
        // 左边 → 左上角
        path.lineTo(0f, aTL)
        corner(path, 0f, 0f, aTL, inY = -1f, outX = 1f)
        path.close()
        return Outline.Generic(path)
    }

    /**
     * 画一个角。cornerX/Y 为角点，(inX,inY) 是进入边指向角点的方向，
     * (outX,outY) 是离开边离开角点的方向。曲线从锚点 (corner - a·in) 平滑
     * 弯到锚点 (corner + a·out)。
     */
    private fun corner(
        path: Path,
        cx: Float, cy: Float, a: Float,
        inX: Float = 0f, inY: Float = 0f,
        outX: Float = 0f, outY: Float = 0f,
    ) {
        val steps = 14
        for (i in 1..steps) {
            val t = PI * i / steps
            val u = a * cos(t).pow(4.5).toFloat()
            val v = a * sin(t).pow(4.5).toFloat()
            path.lineTo(cx - u * inX + v * outX, cy - u * inY + v * outY)
        }
    }
}
