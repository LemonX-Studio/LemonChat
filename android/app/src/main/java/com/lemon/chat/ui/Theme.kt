package com.lemon.chat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── Lemon Chat 设计系统：白 + 绿，全圆角 ──
object LemonColors {
    val Green = Color(0xFF22A45D)
    val GreenLight = Color(0xFFE6F5EC)
    val GreenDark = Color(0xFF187A45)
    val Bg = Color(0xFFF7F9F8)
    val Card = Color.White
    val TextMain = Color(0xFF1B2420)
    val TextSub = Color(0xFF8A948E)
    val BubbleMe = Color(0xFF22A45D)
    val BubbleMeText = Color.White
    val BubbleOther = Color.White
    val Glass = Color(0xCCFFFFFF) // 毛玻璃底色（半透明白）
    val Danger = Color(0xFFE5484D)
}

object LemonShapes {
    val card = RoundedCornerShape(20.dp)
    val bubble = RoundedCornerShape(18.dp)
    val input = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(50)
}

@Composable
fun LemonChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LemonColors.Green,
            onPrimary = Color.White,
            background = LemonColors.Bg,
            surface = LemonColors.Card,
            error = LemonColors.Danger
        ),
        shapes = Shapes(
            small = LemonShapes.input,
            medium = LemonShapes.card,
            large = LemonShapes.card
        ),
        content = content
    )
}
