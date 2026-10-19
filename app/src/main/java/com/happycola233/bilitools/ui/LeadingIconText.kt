package com.happycola233.bilitools.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp

/** 前置小图标的中心相对文字基线的高度，以字号为单位。 */
private const val LeadingIconCenterAboveBaselineEm = 0.37f

/**
 * 带前置小图标的辅助文字（输入报错、补充说明等），登录页与解析页共用。
 *
 * 图标按首行基线对齐，中心落在基线上方约 0.37 个字号处，即中日韩字形的视觉中线，也接近拉丁大写字母的中线。
 * 若按行框顶对齐或居中，行框里的字形偏下，图标会显得比文字高；按基线对齐不受行高影响，多行时也停在首行。
 */
@Composable
internal fun LeadingIconText(
    @DrawableRes iconRes: Int,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    // 均衡断行：两行长度接近，避免第二行只剩一两个字
    val textStyle = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading)
    val iconCenterAboveBaseline = with(LocalDensity.current) {
        (textStyle.fontSize * LeadingIconCenterAboveBaselineEm).roundToPx()
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(16.dp)
                .alignBy { icon -> icon.measuredHeight / 2 + iconCenterAboveBaseline },
        )
        Text(
            text = text,
            style = textStyle,
            color = color,
            modifier = Modifier.alignBy(FirstBaseline),
        )
    }
}
