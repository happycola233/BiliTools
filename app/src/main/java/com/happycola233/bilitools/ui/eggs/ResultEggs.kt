package com.happycola233.bilitools.ui.eggs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.ResultEggCopy
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import kotlinx.coroutines.delay

/** 解析结果里能触发的小彩蛋，按优先级从高到低排列。 */
sealed interface ResultEgg {
    /** BiliTools 开发者本人的稿件或空间 */
    data object Developer : ResultEgg

    /** 站长碧诗（UID 2） */
    data object Founder : ResultEgg

    /** av2，碧诗 2009 年的稿件 */
    data object FirstUpload : ResultEgg

    /** av170001，人称「镇站之宝」 */
    data object Treasure : ResultEgg

    /** 发布满十年的老视频 */
    data class Vintage(val years: Int) : ResultEgg

    /** 发布不到一小时 */
    data object Fresh : ResultEgg

    companion object {
        const val DEVELOPER_MID = 324042405L
        const val FOUNDER_MID = 2L
        const val FIRST_UPLOAD_AID = 2L
        const val TREASURE_AID = 170001L
        private const val VINTAGE_YEARS = 10
        private const val FRESH_SECONDS = 3_600L
        private const val SECONDS_PER_YEAR = 365.2425 * 24 * 3600

        /** [item] 是结果卡当前展示的那一条；列表里换着选时贴纸跟着变 */
        fun detect(info: MediaInfo, item: MediaItem?, nowEpochSeconds: Long): ResultEgg? {
            val mid = item?.resolvedUpper(info)?.mid ?: item?.sourceMid ?: info.nfo.upper?.mid
            val aid = item?.aid
            return when {
                mid == DEVELOPER_MID -> Developer
                aid == TREASURE_AID -> Treasure
                aid == FIRST_UPLOAD_AID -> FirstUpload
                mid == FOUNDER_MID -> Founder
                else -> {
                    val pubTime = item?.pubTime?.takeIf { it > 0 } ?: return null
                    val age = nowEpochSeconds - pubTime
                    val years = (age / SECONDS_PER_YEAR).toInt()
                    when {
                        years >= VINTAGE_YEARS -> Vintage(years)
                        age in 0 until FRESH_SECONDS -> Fresh
                        else -> null
                    }
                }
            }
        }
    }
}

fun ResultEgg.text(copy: ResultEggCopy): String = when (this) {
    ResultEgg.Developer -> copy.developer
    ResultEgg.Founder -> copy.founder
    ResultEgg.FirstUpload -> copy.firstUpload
    ResultEgg.Treasure -> copy.treasure
    is ResultEgg.Vintage -> copy.vintage.formatEggCopy(years)
    ResultEgg.Fresh -> copy.fresh
}

/**
 * 贴在结果封面左下角的小贴纸：一只笑眯眯的小电视加一句话。
 * 封面淡入后稍等片刻再弹出来，换一条结果时先收起再按新内容弹出。
 */
@Composable
fun ResultEggSticker(
    text: String?,
    modifier: Modifier = Modifier,
) {
    var shownText by remember { mutableStateOf<String?>(null) }
    // 收起动画期间沿用上一句，文字不会在淡出时突然变空
    var lastShownText by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        shownText = null
        if (text != null) {
            delay(STICKER_DELAY_MILLIS)
            shownText = text
            lastShownText = text
        }
    }
    val mascot = rememberBiliTvMascotState()
    LaunchedEffect(mascot) { mascot.eyes = MascotEyes.Happy }
    AnimatedVisibility(
        visible = shownText != null,
        enter = fadeIn() + scaleIn(initialScale = 0.8f, transformOrigin = TransformOrigin(0f, 1f)) +
            slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) { mascot.hop(0.35f) }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Box(Modifier.size(28.dp)) { BiliTvMascot(mascot, Modifier.size(28.dp)) }
                Text(
                    text = shownText ?: lastShownText,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val STICKER_DELAY_MILLIS = 450L
