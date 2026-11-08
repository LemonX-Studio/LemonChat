package com.lemon.chat.ui

import com.lemon.chat.LemonLog
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// 超椭圆（superellipse）圆角
// 角部曲线就是标准超椭圆 |u/a|^n + |v/a|^n = 1（n=4.5）上从 (a,0) 到 (0,a) 的那段，
// 参数化采样：u=a·cos^(2/n)(t), v=a·sin^(2/n)(t)。锚点不外扩（起点在距角 a 处），
// 采样 40 点保证平滑无折线。
class SmoothCornerShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val spread: Float = 1.0f, // 超椭圆不需要外扩
) : Shape {
    constructor(radius: Dp) : this(radius, radius, radius, radius)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        // 尺寸异常防护：0/负数/NaN 一律回退空矩形，绝不产出坏路径
        if (!(w > 0f && h > 0f) || w.isNaN() || h.isNaN() || w.isInfinite() || h.isInfinite()) {
            LemonLog.log("shape", "SmoothCornerShape BAD-SIZE ${w}x${h}")
            return Outline.Generic(Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w.coerceAtLeast(0f), h.coerceAtLeast(0f))) })
        }
        fun anchor(r: Dp) = with(density) { r.toPx() } * spread
        var aTL = anchor(topStart)
        var aTR = anchor(topEnd)
        var aBR = anchor(bottomEnd)
        var aBL = anchor(bottomStart)

        // 防自交关键：同一条边上相邻两角锚点之和不得超过边长，超了按比例收缩。
        // 否则小尺寸组件（方形头像等）路径交叉、非零填充正负抵消，背景直接塌掉变白。
        fun fit(x: Float, y: Float, max: Float): Pair<Float, Float> {
            val s = x + y
            return if (s > max && s > 0f) { val k = max / s; x * k to y * k } else x to y
        }
        repeat(3) {
            var p = fit(aTL, aTR, w); aTL = p.first; aTR = p.second
            p = fit(aTR, aBR, h); aTR = p.first; aBR = p.second
            p = fit(aBR, aBL, w); aBR = p.first; aBL = p.second
            p = fit(aBL, aTL, h); aBL = p.first; aTL = p.second
        }

        // 探针：同一尺寸只报一次（防刷屏），能看清每个组件拿到什么尺寸
        if (LemonLog.enabled) {
            val key = "scs ${w.toInt()}x${h.toInt()}"
            LemonLog.once(key, "shape", "outline size=${w.toInt()}x${h.toInt()} aTL=${aTL.toInt()}")
        }

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
        val steps = 40
        // 标准超椭圆：u=a·cos^(2/n)(t)，n=4.5。外鼓凸曲线，t 只走 0→π/2（负数的分数次幂=NaN）
        val m = 2.0 / 4.5
        for (i in 1..steps) {
            val t = Math.PI * 0.5 * i / steps
            val u = (a * cos(t).pow(m)).toFloat() // 沿进入边，从 a 收到 0
            val v = (a * sin(t).pow(m)).toFloat() // 沿离开边，从 0 涨到 a
            path.lineTo(cx - u * inx + v * outx, cy - u * iny + v * outy)
        }
    }
}

// ── 画布级 squircle 裁剪 ──
// 不用 Modifier.clip()：它走 RenderNode outline 裁剪通道，安卓 9 的硬件渲染对
// 自绘曲线路径的 outline 裁剪不可靠（会静默把内容裁没 = 全白）。
// 这里改为 DrawScope 的 canvas.clipPath，所有安卓版本行为一致。
fun Modifier.squircleClip(shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawWithContent {
        when (outline) {
            is Outline.Generic -> clipPath(outline.path) { this@onDrawWithContent.drawContent() }
            is Outline.Rounded -> clipPath(Path().apply { addRoundRect(outline.roundRect) }) { this@onDrawWithContent.drawContent() }
            else -> this@onDrawWithContent.drawContent()
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
        if (size.isEmpty()) {
            onDrawWithContent { drawContent() }
        } else {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawWithContent {
                drawContent()
                drawOutline(outline, color, style = Stroke(width = strokeWidth.toPx()))
            }
        }
    }
