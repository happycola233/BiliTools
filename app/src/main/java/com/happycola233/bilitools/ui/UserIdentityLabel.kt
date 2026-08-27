package com.happycola233.bilitools.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics

/** 紧凑展示用户头像和昵称；头像只作身份辅助，昵称承担无障碍语义。 */
@Composable
internal fun UserIdentityLabel(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = rememberAppHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val interactionModifier = when {
        onClick == null -> Modifier
        onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                haptics.tap()
                onClick()
            },
            onLongClickLabel = onLongClickLabel,
            onLongClick = {
                haptics.longPress()
                onLongClick()
            },
            hapticFeedbackEnabled = false,
        )
        else -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                haptics.tap()
                onClick()
            },
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.then(interactionModifier),
    ) {
        AsyncImage(
            model = avatarUrl?.trim()?.takeIf { it.isNotBlank() } ?: R.drawable.default_avatar,
            placeholder = painterResource(R.drawable.default_avatar),
            error = painterResource(R.drawable.default_avatar),
            fallback = painterResource(R.drawable.default_avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
