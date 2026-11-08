package com.lemon.chat.ui

import androidx.compose.foundation.isSystemInDarkTheme
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

// ── 全部使用系统标准圆角（RoundedCornerShape），稳定第一 ──
object LemonShapes {
    val card = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val bubble = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val input = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    // 胶囊/圆头像：50% 百分比圆角 = 圆形/胶囊
    val pill = androidx.compose.foundation.shape.RoundedCornerShape(50)

    // Material 组件同款
    val matCard = card
    val matInput = input
    val matPill = pill
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
        // Material 默认 shapes 要求 CornerBasedShape（不连续曲率），与我们全连续曲率原则冲突；
        // 项目内所有圆角一律走 LemonShapes，故不注入 Material shapes
        content = content
    )
}
