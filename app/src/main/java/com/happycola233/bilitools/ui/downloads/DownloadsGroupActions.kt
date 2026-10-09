package com.happycola233.bilitools.ui.downloads

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics

/** 组级操作使用 Expressive 的超小号按钮，作为任务列表的页脚而不是第二个视觉焦点。 */
private val GroupActionButtonHeight = ButtonDefaults.ExtraSmallContainerHeight

/**
 * 展开后排在任务段末尾的组级操作。两者都是低频操作，按卡片操作区的惯例靠末端排列；
 * 宽度不足时整颗按钮换行，保持触控区与文案完整。
 */
@Composable
internal fun DownloadsGroupActions(
    reparseEnabled: Boolean,
    onReparse: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        GroupActionButton(
            iconRes = R.drawable.ic_restart_alt_24,
            text = stringResource(R.string.downloads_reparse),
            enabled = reparseEnabled,
            onClick = onReparse,
        )
        GroupActionButton(
            iconRes = R.drawable.ic_info_24,
            text = stringResource(R.string.downloads_view_details),
            onClick = onShowDetails,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupActionButton(
    @DrawableRes iconRes: Int,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val haptics = rememberAppHaptics()
    FilledTonalButton(
        onClick = {
            haptics.tap()
            onClick()
        },
        enabled = enabled,
        shapes = ButtonDefaults.shapesFor(GroupActionButtonHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(GroupActionButtonHeight, hasStartIcon = true),
        modifier = Modifier.heightIn(min = GroupActionButtonHeight),
    ) {
        Icon(painterResource(iconRes), null, Modifier.size(ButtonDefaults.iconSizeFor(GroupActionButtonHeight)))
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(GroupActionButtonHeight)))
        Text(text, style = ButtonDefaults.textStyleFor(GroupActionButtonHeight))
    }
}
