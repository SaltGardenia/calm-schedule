package com.saltgarden.kechengbiao.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 液态玻璃弹窗：透明 Dialog 窗口 + 玻璃卡片（半透明填充 / 顶部亮边 / 柔和投影）。
 * 内容超高时可滚动，窗口内沿安全边距居中。
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 400.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        val cardInteraction = remember { MutableInteractionSource() }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // 全屏透明窗口内没有"窗口外部"，单击空白处主动关闭
                .clickable(interactionSource = scrimInteraction, indication = null) { onDismissRequest() }
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f)
                    // 表单可读性：玻璃之下垫一层半透明底色（弹窗窗口无法实时模糊背景）
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        RoundedCornerShape(28.dp),
                    )
                    .glassSurface(RoundedCornerShape(28.dp), elevation = 20.dp)
                    // 卡片自身吞掉点击，避免点在卡片上也触发关闭
                    .clickable(interactionSource = cardInteraction, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** 弹窗底部玻璃胶囊按钮（primary = 主操作；destructive = 危险操作，红色） */
@Composable
fun RowScope.GlassDialogButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val accent = if (destructive) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .weight(1f)
            .pressScale(interaction, pressedScale = 0.95f)
            .glassSurface(
                shape = RoundedCornerShape(50),
                elevation = if (primary || destructive) 4.dp else 2.dp,
                tint = if (primary || destructive) accent.copy(alpha = 0.88f) else null,
            )
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                primary || destructive -> Color.White
                else -> MaterialTheme.colorScheme.primary
            },
        )
    }
}
