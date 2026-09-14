package com.happycola233.bilitools.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.AppAccents

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadsGroupActions(
    reparseEnabled: Boolean,
    onReparse: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val textStyle = MaterialTheme.typography.labelLarge
    // 六字按钮在大字体下需要更宽的列；空间不足时整颗按钮换行，保持触控区与文案完整。
    val minimumWidth = with(LocalDensity.current) { (textStyle.fontSize * 6).toDp() } + 60.dp
    val buttonModifier = Modifier.heightIn(min = 48.dp).widthIn(min = minimumWidth)
    val shapes = ButtonDefaults.shapesFor(48.dp)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = {
                haptics.tap()
                onReparse()
            },
            enabled = reparseEnabled,
            shapes = shapes,
            colors = AppAccents.filledButtonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = buttonModifier.weight(1f),
        ) {
            Icon(painterResource(R.drawable.ic_retry_24), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.downloads_reparse), style = textStyle)
        }
        FilledTonalButton(
            onClick = {
                haptics.tap()
                onShowDetails()
            },
            shapes = shapes,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = buttonModifier.weight(1f),
        ) {
            Icon(painterResource(R.drawable.ic_info_24), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.downloads_view_details), style = textStyle)
        }
    }
}
