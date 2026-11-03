package com.saltgarden.kechengbiao.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃（Liquid Glass）材质系统。
 *
 * 参考 Apple「液态玻璃」的材质语言（apple-design 原则 §12）：
 *  - 半透明填充 + 顶部亮边（光打在材质上的高光）+ 柔和环境阴影；
 *  - 材质是悬浮的功能层，内容在其下滚动；
 *  - 亮色玻璃用白色高透明，暗色玻璃用低透明白，避免两层轻玻璃叠加；
 *  - 所有交互反馈在「按下瞬间」出现，用临界阻尼弹簧回弹（damping 1.0）。
 *
 * 说明：真实背景实时模糊（RenderEffect）仅 API 31+ 可用且需引入第三方库；
 * 这里用「彩色渐变底 + 高透明玻璃层」获得同等的层次感，并在低版本上无缝退化。
 */

/** 玻璃弹簧：临界阻尼、响应约 0.32s（apple-design §4 默认值） */
fun <T> glassSpring(stiffness: Float = 380f) = spring<T>(dampingRatio = 1f, stiffness = stiffness)

/** 按下反馈弹簧：临界阻尼，用于按下/抬起缩放 */
val PressSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

data class GlassPalette(
    val fillTop: Color,
    val fillBottom: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val shadow: Color,
)

private val LightGlass = GlassPalette(
    fillTop = Color(0xC2FFFFFF),
    fillBottom = Color(0x78FFFFFF),
    borderTop = Color(0xE6FFFFFF),
    borderBottom = Color(0x2EFFFFFF),
    shadow = Color(0x2E5A6BD8),
)

private val DarkGlass = GlassPalette(
    fillTop = Color(0x2BFFFFFF),
    fillBottom = Color(0x14FFFFFF),
    borderTop = Color(0x54FFFFFF),
    borderBottom = Color(0x0FFFFFFF),
    shadow = Color(0x52000000),
)

@Composable
fun glassPalette(): GlassPalette = if (isSystemInDarkTheme()) DarkGlass else LightGlass

/**
 * 玻璃表面：渐变半透明填充 + 1px 顶部亮边描边 + 可选柔和投影。
 */
fun Modifier.glassSurface(
    shape: Shape,
    elevation: Dp = 0.dp,
    tint: Color? = null,
    fillAlpha: Float = 1f,
): Modifier = composed {
    val p = glassPalette()
    val fillTop = (tint ?: p.fillTop).let { it.copy(alpha = it.alpha * fillAlpha) }
    // 有色玻璃：底部稍深，产生体积感；无色玻璃用材质自身渐变
    val fillBottom = if (tint != null) {
        tint.copy(alpha = tint.alpha * fillAlpha * 0.72f)
    } else {
        p.fillBottom.copy(alpha = p.fillBottom.alpha * fillAlpha)
    }
    this
        .shadow(elevation = elevation, shape = shape, clip = false, ambientColor = p.shadow, spotColor = p.shadow)
        .background(Brush.verticalGradient(listOf(fillTop, fillBottom)), shape)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(p.borderTop, p.borderBottom)),
            shape = shape,
        )
}

/**
 * 按压缩放反馈：在「按下瞬间」立即响应（apple-design §1），松手以弹簧回弹。
 * 配合 clickable(interactionSource = ...) 使用。
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PressSpring,
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** App 背景：柔和彩色渐变 + 光斑，让玻璃层有「可折射」的内容 */
fun Modifier.kcbBackdrop(): Modifier = composed {
    val dark = isSystemInDarkTheme()
    if (dark) {
        background(
            Brush.verticalGradient(
                listOf(Color(0xFF0A0F1E), Color(0xFF121A33), Color(0xFF0E1526))
            )
        ).drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x332946FF), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.08f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.12f, size.height * 0.08f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2E7C4DFF), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.32f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.92f, size.height * 0.32f),
            )
        }
    } else {
        background(
            Brush.verticalGradient(
                listOf(Color(0xFFE7EDFF), Color(0xFFF4EEFF), Color(0xFFE7F6FF))
            )
        ).drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x66A8BCFF), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.06f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.1f, size.height * 0.06f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x59E0B8FF), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.28f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.95f, size.height * 0.28f),
            )
        }
    }
}

/** 玻璃小胶囊按钮：按下即缩放，无 ripple（缩放就是反馈） */
@Composable
fun GlassChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, pressedScale = 0.93f)
            .glassSurface(
                shape = RoundedCornerShape(50),
                elevation = 3.dp,
                tint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else null,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
        )
    }
}

/** 玻璃圆形图标按钮 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(interaction, pressedScale = 0.9f)
            .glassSurface(shape = CircleShape, elevation = 3.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}
