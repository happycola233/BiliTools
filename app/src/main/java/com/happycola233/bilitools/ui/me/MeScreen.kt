package com.happycola233.bilitools.ui.me

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.decode.DataSource
import coil3.load
import coil3.request.error
import coil3.request.fallback
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.UserInfo
import com.happycola233.bilitools.ui.AppAlertDialog
import com.happycola233.bilitools.ui.mainBottomBarBottomInset
import com.happycola233.bilitools.ui.login.LoginUiState
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.SegmentedListShapes
import com.happycola233.bilitools.ui.theme.animateSegmentShape
import com.happycola233.bilitools.ui.theme.BiliToolsFonts
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import kotlin.math.abs
import androidx.core.text.HtmlCompat

private data class MeActionItem(
    @DrawableRes val iconRes: Int,
    val titleRes: Int,
    val summaryRes: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private val ProfileCardContentPadding = 16.dp
private val ProfileCardSectionSpacing = 14.dp
private val ProfileCardHeaderSpacing = 12.dp
private val ProfileAvatarSize = 64.dp
private val ProfileVipBadgeSize = 20.dp

private data class RemoteImageRequest(
    val model: String?,
    @DrawableRes val fallbackRes: Int?,
    val fadeInOnLoad: Boolean,
    val fadeInDurationMillis: Int,
)

/** 主壳统一提供 [BiliToolsTheme]，页面内容不再创建嵌套主题。 */
@Composable
fun BiliToolsMeContent(
    loginState: LoginUiState,
    contentTopPadding: Dp,
    onOpenLogin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorite: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MeOverviewScreen(
        loginState = loginState,
        contentTopPadding = contentTopPadding,
        onNavigateToLogin = onOpenLogin,
        onOpenHistory = onOpenHistory,
        onOpenFavorite = onOpenFavorite,
        onOpenWatchLater = onOpenWatchLater,
        onOpenSettings = onOpenSettings,
        onLogout = onLogout,
        modifier = modifier,
    )
}

@Composable
private fun MeOverviewScreen(
    loginState: LoginUiState,
    contentTopPadding: Dp,
    onNavigateToLogin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorite: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoggedIn = loginState.isLoggedIn
    var showLogoutConfirmDialog by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberAppHaptics()

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            showLogoutConfirmDialog = false
        }
    }

    val canOpenFavorite = isLoggedIn && loginState.currentMid != null
    val contentItems = listOf(
        MeActionItem(
            iconRes = R.drawable.ic_history_24,
            titleRes = R.string.history_title,
            summaryRes = if (isLoggedIn) {
                R.string.history_entry_desc
            } else {
                R.string.me_history_entry_disabled_desc
            },
            enabled = isLoggedIn,
            onClick = onOpenHistory,
        ),
        MeActionItem(
            iconRes = R.drawable.ic_star_24,
            titleRes = R.string.login_entry_favorite_title,
            summaryRes = if (isLoggedIn) {
                R.string.login_entry_favorite_desc
            } else {
                R.string.me_favorite_entry_disabled_desc
            },
            enabled = canOpenFavorite,
            onClick = onOpenFavorite,
        ),
        MeActionItem(
            iconRes = R.drawable.ic_watch_later_24,
            titleRes = R.string.login_entry_watch_later_title,
            summaryRes = if (isLoggedIn) {
                R.string.login_entry_watch_later_desc
            } else {
                R.string.me_watch_later_entry_disabled_desc
            },
            enabled = isLoggedIn,
            onClick = onOpenWatchLater,
        ),
    )
    val manageItems = buildList {
        add(
            MeActionItem(
                iconRes = R.drawable.ic_settings_24,
                titleRes = R.string.login_entry_settings_title,
                summaryRes = R.string.login_entry_settings_desc,
                enabled = true,
                onClick = onOpenSettings,
            ),
        )
        if (isLoggedIn) {
            add(
                MeActionItem(
                    iconRes = R.drawable.ic_logout_24,
                    titleRes = R.string.login_logout,
                    summaryRes = R.string.me_logout_summary,
                    enabled = true,
                    onClick = { showLogoutConfirmDialog = true },
                ),
            )
        }
    }

    if (showLogoutConfirmDialog) {
        AppAlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.login_logout_confirm_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(stringResource(R.string.login_logout_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.confirm()
                        showLogoutConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.login_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    MePageContainer(modifier = modifier) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            // 页面全出血绘制，内容从主界面底栏后方滚过，底部预留底栏净空
            contentPadding = PaddingValues(
                start = 16.dp,
                top = contentTopPadding + 14.dp,
                end = 16.dp,
                bottom = 14.dp + mainBottomBarBottomInset(),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                if (isLoggedIn) {
                    val userInfo = loginState.userInfo
                    if (userInfo != null) {
                        ProfileCard(userInfo = userInfo)
                    } else {
                        ProfileLoadingCard()
                    }
                } else {
                    MeClickableListItem(
                        items = 1,
                        index = 0,
                        leadingContent = { MeItemIcon(R.drawable.ic_info_24) },
                        content = {
                            MeItemTitle(stringResource(R.string.me_login_entry_title))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.me_login_entry_summary))
                        },
                        trailingContent = {
                            MeItemIcon(R.drawable.ic_chevron_right_24)
                        },
                        onClick = onNavigateToLogin,
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
            item { MeSectionTitle(stringResource(R.string.me_section_content)) }

            itemsIndexed(contentItems) { index, item ->
                MeClickableListItem(
                    items = contentItems.size,
                    index = index,
                    enabled = item.enabled,
                    leadingContent = { MeItemIcon(item.iconRes) },
                    content = { MeItemTitle(stringResource(item.titleRes)) },
                    supportingContent = { Text(stringResource(item.summaryRes)) },
                    trailingContent = { MeItemIcon(R.drawable.ic_chevron_right_24) },
                    onClick = item.onClick,
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
            item { MeSectionTitle(stringResource(R.string.me_section_manage)) }

            itemsIndexed(manageItems) { index, item ->
                MeClickableListItem(
                    items = manageItems.size,
                    index = index,
                    leadingContent = { MeItemIcon(item.iconRes) },
                    content = { MeItemTitle(stringResource(item.titleRes)) },
                    supportingContent = { Text(stringResource(item.summaryRes)) },
                    trailingContent = { MeItemIcon(R.drawable.ic_chevron_right_24) },
                    onClick = item.onClick,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    userInfo: UserInfo,
    modifier: Modifier = Modifier,
) {
    val sign = remember(userInfo.sign) { sanitizeSign(userInfo.sign) }
    val showVipBadge = remember(userInfo) { shouldShowVipBadge(userInfo) }

    Surface(
        color = AppSurfaces.cardContainerColor,
        shape = MeExpressiveShapes.cardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            if (!userInfo.topPhotoUrl.isNullOrBlank()) {
                RemoteImage(
                    model = userInfo.topPhotoUrl,
                    contentDescription = stringResource(R.string.login_cover_desc),
                    modifier = Modifier
                        .matchParentSize()
                        .blur(18.dp)
                        .alpha(0.22f),
                    contentScale = ContentScale.Crop,
                    fadeInOnLoad = true,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(ProfileCardSectionSpacing),
                modifier = Modifier.padding(ProfileCardContentPadding),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ProfileCardHeaderSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.size(ProfileAvatarSize)) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.matchParentSize(),
                        ) {
                            RemoteImage(
                                model = userInfo.avatarUrl,
                                contentDescription = stringResource(R.string.login_avatar_desc),
                                modifier = Modifier.fillMaxSize(),
                                fallbackRes = R.drawable.default_avatar,
                                contentScale = ContentScale.Crop,
                            )
                        }
                        if (showVipBadge) {
                            RemoteImage(
                                model = "file:///android_asset/big-vip.svg",
                                contentDescription = stringResource(R.string.login_vip_badge_desc),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(ProfileVipBadgeSize),
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = userInfo.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            LevelBadge(
                                level = userInfo.level,
                                isSeniorMember = userInfo.isSeniorMember,
                            )
                            VipLabel(userInfo = userInfo)
                        }

                        Text(
                            text = stringResource(R.string.login_user_mid, userInfo.mid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (sign.isNotBlank()) {
                    Text(
                        text = sign,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ProfileStat(
                        label = stringResource(R.string.login_stat_following),
                        value = formatUserCount(userInfo.following ?: 0),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStat(
                        label = stringResource(R.string.login_stat_follower),
                        value = formatUserCount(userInfo.follower ?: 0),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStat(
                        label = stringResource(R.string.login_stat_dynamic),
                        value = formatUserCount(userInfo.dynamic ?: 0),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStat(
                        label = stringResource(R.string.login_stat_coins),
                        value = formatCoins(userInfo.coins),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileLoadingCard(
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AppSurfaces.cardContainerColor,
        shape = MeExpressiveShapes.cardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            // Compose 指示器直接观察 ColorScheme，配色切换时不会滞留 AndroidView 创建时的旧色。
            LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
            )
        }
    }
}

@Composable
private fun LevelBadge(
    level: Int?,
    isSeniorMember: Boolean,
    modifier: Modifier = Modifier,
) {
    val assetPath = remember(level, isSeniorMember) {
        val badgeLevel = if (isSeniorMember) 6 else level?.coerceIn(0, 6) ?: return@remember null
        val assetFile = if (isSeniorMember) "LV6_flash.svg" else "LV$badgeLevel.svg"
        "file:///android_asset/user_level_icon/$assetFile"
    }
    if (assetPath == null) return

    RemoteImage(
        model = assetPath,
        contentDescription = stringResource(
            R.string.login_user_level,
            if (isSeniorMember) 6 else level?.coerceIn(0, 6) ?: 0,
        ),
        modifier = modifier.height(16.dp),
    )
}

@Composable
private fun VipLabel(
    userInfo: UserInfo,
    modifier: Modifier = Modifier,
) {
    if (!userInfo.vipLabelImageUrl.isNullOrBlank()) {
        RemoteImage(
            model = userInfo.vipLabelImageUrl,
            contentDescription = stringResource(R.string.login_vip_label_desc),
            modifier = modifier.height(24.dp),
        )
        return
    }

    val vipText = userInfo.vipLabel.orEmpty().trim()
    if (vipText.isBlank()) return

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    ) {
        Text(
            text = vipText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ProfileStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = BiliToolsFonts.googleSansFlexRond100,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RemoteImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    @DrawableRes fallbackRes: Int? = null,
    contentScale: ContentScale = ContentScale.Fit,
    fadeInOnLoad: Boolean = false,
    fadeInDurationMillis: Int = 320,
) {
    AndroidView(
        factory = { context ->
            android.widget.ImageView(context).apply {
                adjustViewBounds = true
                scaleType = contentScale.toScaleType()
            }
        },
        update = { imageView ->
            val request = RemoteImageRequest(
                model = model,
                fallbackRes = fallbackRes,
                fadeInOnLoad = fadeInOnLoad,
                fadeInDurationMillis = fadeInDurationMillis,
            )
            imageView.scaleType = contentScale.toScaleType()
            if (imageView.tag != request || imageView.drawable == null) {
                imageView.tag = request
                imageView.animate().cancel()
                when {
                    model.isNullOrBlank() && fallbackRes != null -> {
                        imageView.alpha = 1f
                        imageView.setImageResource(fallbackRes)
                    }

                    model.isNullOrBlank() -> {
                        imageView.alpha = 1f
                        imageView.setImageDrawable(null)
                    }

                    else -> imageView.load(model) {
                        if (fallbackRes != null) {
                            fallback(fallbackRes)
                            error(fallbackRes)
                        }
                        listener(
                            onStart = {
                                imageView.alpha = if (fadeInOnLoad) 0f else 1f
                            },
                            onCancel = {
                                imageView.alpha = 1f
                            },
                            onError = { _, _ ->
                                imageView.alpha = 1f
                            },
                            onSuccess = { _, result ->
                                // 内存缓存命中说明图已经在手上，淡入只会让 Activity 重建后
                                // 本该原地复原的画面凭空闪一下
                                val fromMemory = result.dataSource == DataSource.MEMORY_CACHE
                                if (fadeInOnLoad && !fromMemory) {
                                    imageView.animate()
                                        .alpha(1f)
                                        .setDuration(fadeInDurationMillis.toLong())
                                        .start()
                                } else {
                                    imageView.alpha = 1f
                                }
                            },
                        )
                    }
                }
            }
            imageView.contentDescription = contentDescription
        },
        modifier = modifier,
    )
}

@Composable
private fun MePageContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(AppSurfaces.pageContainerColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = MeExpressiveShapes.paneMaxWidth),
        ) {
            content()
        }
    }
}

@Composable
private fun MeSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun MeItemIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .wrapContentHeight(Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
        )
    }
}

@Composable
private fun MeItemTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MeClickableListItem(
    items: Int,
    index: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = animateSegmentShape(
        topCorner = SegmentedListShapes.edgeCorner(items == 1 || index == 0),
        bottomCorner = SegmentedListShapes.edgeCorner(items == 1 || index == items - 1),
        pressed = enabled && isPressed,
    )

    ListItem(
        content = content,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = AppSurfaces.cardContainerColor,
        ),
        modifier = modifier
            .clip(shape)
            .alpha(if (enabled) 1f else 0.52f)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.tap()
                    onClick()
                },
            ),
    )
}

private object MeExpressiveShapes {
    val cardShape: CornerBasedShape
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable get() = MaterialTheme.shapes.largeIncreased

    val paneMaxWidth = 600.dp
}

private fun sanitizeSign(value: String?): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return ""
    return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT)
        .toString()
        .trim()
}

private fun shouldShowVipBadge(info: UserInfo): Boolean {
    info.vipAvatarSubscript?.let { return it == 1 }
    val status = info.vipStatus
    val type = info.vipType
    if (status != null || type != null) {
        return (status ?: 0) > 0 && (type ?: 0) > 0
    }
    return !info.vipLabelImageUrl.isNullOrBlank() || !info.vipLabel.isNullOrBlank()
}

@Composable
private fun formatUserCount(value: Number): String = java.text.NumberFormat
    .getIntegerInstance(LocalConfiguration.current.locales[0]).format(value)

@Composable
private fun formatCoins(value: Double?): String {
    val locale = LocalConfiguration.current.locales[0]
    if (value == null) return "-"
    val rounded = value.toLong().toDouble()
    return if (abs(value - rounded) < 0.01) {
        java.text.NumberFormat.getIntegerInstance(locale).format(rounded.toLong())
    } else {
        String.format(locale, "%.1f", value)
    }
}

private fun ContentScale.toScaleType(): android.widget.ImageView.ScaleType {
    return when (this) {
        ContentScale.Crop -> android.widget.ImageView.ScaleType.CENTER_CROP
        else -> android.widget.ImageView.ScaleType.FIT_CENTER
    }
}
