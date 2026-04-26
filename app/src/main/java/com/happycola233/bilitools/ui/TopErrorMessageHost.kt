package com.happycola233.bilitools.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.happycola233.bilitools.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ErrorMessageAutoDismissMillis = 3000L
private const val ErrorMessageExitDurationMillis = 260
private const val ErrorMessageExitFadeDurationMillis = 220
private val ErrorMessageShape = RoundedCornerShape(18.dp)
private val ErrorMessageDragDismissThreshold = 44.dp
private val ErrorMessageShadowPadding = 8.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopErrorMessageHost(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = !message.isNullOrBlank()
    var displayMessage by remember { mutableStateOf(message?.takeIf { it.isNotBlank() }) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dragOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dragDismissThresholdPx = remember(density) {
        with(density) { ErrorMessageDragDismissThreshold.toPx() }
    }
    val motionScheme = MaterialTheme.motionScheme

    LaunchedEffect(message) {
        val nextMessage = message?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        dragOffsetY.snapTo(0f)
        displayMessage = nextMessage
        delay(ErrorMessageAutoDismissMillis)
        currentOnDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .zIndex(20f)
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .pointerInput(message) {
                coroutineScope {
                    detectVerticalDragGestures(
                        onDragStart = {
                            launch { dragOffsetY.stop() }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val nextOffset = (dragOffsetY.value + dragAmount).coerceAtMost(0f)
                            launch { dragOffsetY.snapTo(nextOffset) }
                        },
                        onDragEnd = {
                            if (dragOffsetY.value <= -dragDismissThresholdPx) {
                                currentOnDismiss()
                            } else {
                                launch { dragOffsetY.animateTo(0f) }
                            }
                        },
                        onDragCancel = {
                            launch { dragOffsetY.animateTo(0f) }
                        },
                    )
                }
            },
        enter =
            fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                ) +
                scaleIn(
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = motionScheme.defaultSpatialSpec(),
                ),
        exit =
            fadeOut(
                animationSpec = tween(
                    durationMillis = ErrorMessageExitFadeDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) +
                slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(
                        durationMillis = ErrorMessageExitDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                ) +
                scaleOut(
                    targetScale = 0.94f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = tween(
                        durationMillis = ErrorMessageExitDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                ),
    ) {
        displayMessage?.let { alertText ->
            val shadowElevation by transition.animateDp(
                transitionSpec = {
                    if (targetState == EnterExitState.Visible) {
                        motionScheme.defaultSpatialSpec()
                    } else {
                        tween(
                            durationMillis = ErrorMessageExitDurationMillis,
                            easing = FastOutSlowInEasing,
                        )
                    }
                },
                label = "TopErrorMessageElevation",
            ) { targetState ->
                if (targetState == EnterExitState.Visible) 4.dp else 0.dp
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ErrorMessageShadowPadding),
            ) {
                TopErrorMessage(
                    message = alertText,
                    onDismiss = currentOnDismiss,
                    shadowElevation = shadowElevation,
                )
            }
        }
    }
}

@Composable
private fun TopErrorMessage(
    message: String,
    onDismiss: () -> Unit,
    shadowElevation: Dp,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ErrorMessageShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.width(76.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactMessageIconButton(
                    onClick = {
                        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
                        clipboardManager?.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.common_copy_message),
                                message,
                            ),
                        )
                    },
                    contentDescription = stringResource(R.string.common_copy_message),
                    iconRes = R.drawable.ic_content_copy_24,
                )
                CompactMessageIconButton(
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.common_dismiss_message),
                    iconRes = R.drawable.ic_close_rounded_24,
                )
            }
        }
    }
}

@Composable
private fun CompactMessageIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}
