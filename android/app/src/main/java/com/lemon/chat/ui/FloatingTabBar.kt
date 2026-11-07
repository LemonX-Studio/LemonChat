package com.lemon.chat.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.lemon.chat.ui.SmoothCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 悬浮毛玻璃 Tab 栏：固定不拖，玻璃高亮块在选中的 Tab 下面滑动 ──

data class TabItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

@Composable
fun FloatingTabBar(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .shadow(
                14.dp, SmoothCornerShape(1000.dp),
                ambientColor = Color(0x3322A45D), spotColor = Color(0x5522A45D)
            )
            .clip(SmoothCornerShape(1000.dp))
            .background(LemonColors.Glass)
            .smoothBorder(1.dp, Color(0x66FFFFFF), SmoothCornerShape(1000.dp))
    ) {
        val slotW = maxWidth / tabs.size

        // 玻璃高亮块：滑到当前选中的 Tab 下面
        val hlX by animateDpAsState(
            targetValue = slotW * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "highlightX"
        )

        Box(
            Modifier
                .offset(x = hlX)
                .width(slotW)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 7.dp)
                .clip(SmoothCornerShape(1000.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xF2FFFFFF), Color(0xD84FD98A))
                    )
                )
                .smoothBorder(1.dp, Color(0x88FFFFFF), SmoothCornerShape(1000.dp))
        )

        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, tab ->
                val selected = i == selectedIndex
                val scale = remember { Animatable(1f) }
                LaunchedEffect(selectedIndex) {
                    if (selected) { // 切换时图标小弹跳
                        scale.snapTo(0.85f)
                        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(i) }
                ) {
                    Icon(
                        tab.icon, tab.label,
                        tint = if (selected) LemonColors.Green else LemonColors.TextSub,
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = if (selected) LemonColors.Green else LemonColors.TextSub
                    )
                }
            }
        }
    }
}
