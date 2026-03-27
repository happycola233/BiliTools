package com.happycola233.bilitools.ui.me

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.decode.SvgDecoder
import coil.load
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.model.UserInfo
import com.happycola233.bilitools.ui.login.LoginTab
import com.happycola233.bilitools.ui.login.LoginUiState
import com.happycola233.bilitools.ui.theme.BiliToolsSettingsTheme
import java.util.Locale
import kotlin.math.abs
import androidx.core.text.HtmlCompat

private data class MeActionItem(
    @DrawableRes val iconRes: Int,
    val titleRes: Int,
    val summaryRes: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private val LoginPanelDefaultHeight = 438.dp
private val QrStatusSlotMinHeight = 44.dp
private val LoginFormControlHeight = 56.dp
private val LoginFormControlShape = RoundedCornerShape(18.dp)
private val ProfileCardContentPadding = 16.dp
private val ProfileCardSectionSpacing = 14.dp
private val ProfileCardHeaderSpacing = 12.dp
private val ProfileAvatarSize = 64.dp
private val ProfileVipBadgeSize = 20.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiliToolsMeContent(
    settings: AppSettings,
    loginState: LoginUiState,
    onOpenLogin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorite: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenSettings: () -> Unit,
    onConfirmLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BiliToolsSettingsTheme(settings = settings) {
        MeOverviewScreen(
            loginState = loginState,
            onNavigateToLogin = onOpenLogin,
            onOpenHistory = onOpenHistory,
            onOpenFavorite = onOpenFavorite,
            onOpenWatchLater = onOpenWatchLater,
            onOpenSettings = onOpenSettings,
            onConfirmLogout = onConfirmLogout,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiliToolsLoginContent(
    settings: AppSettings,
    state: LoginUiState,
    onExit: () -> Unit,
    onPrepareLogin: () -> Unit,
    onRefreshQr: () -> Unit,
    onTabChange: (LoginTab) -> Unit,
    onRequestPasswordLogin: (String, String) -> Unit,
    onRequestSmsCode: (Int, String) -> Unit,
    onLoginWithSms: (Int, String, String) -> Unit,
    onSetCountryId: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BiliToolsSettingsTheme(settings = settings) {
        LoginSubscreen(
            state = state,
            onPrepareLogin = onPrepareLogin,
            onBack = onExit,
            onRefreshQr = onRefreshQr,
            onTabChange = onTabChange,
            onRequestPasswordLogin = onRequestPasswordLogin,
            onRequestSmsCode = onRequestSmsCode,
            onLoginWithSms = onLoginWithSms,
            onSetCountryId = onSetCountryId,
            modifier = modifier,
        )
    }
}

@Composable
private fun MeOverviewScreen(
    loginState: LoginUiState,
    onNavigateToLogin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorite: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenSettings: () -> Unit,
    onConfirmLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoggedIn = loginState.isLoggedIn
    val hasUserInfo = loginState.userInfo != null
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
            enabled = hasUserInfo,
            onClick = onOpenFavorite,
        ),
        MeActionItem(
            iconRes = R.drawable.ic_refresh_24,
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
                    onClick = onConfirmLogout,
                ),
            )
        }
    }

    MePageContainer(modifier = modifier) {
        val nestedScrollInterop = rememberNestedScrollInteropConnection()
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollInterop),
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
                        headlineContent = {
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
                    headlineContent = { MeItemTitle(stringResource(item.titleRes)) },
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
                    headlineContent = { MeItemTitle(stringResource(item.titleRes)) },
                    supportingContent = { Text(stringResource(item.summaryRes)) },
                    trailingContent = { MeItemIcon(R.drawable.ic_chevron_right_24) },
                    onClick = item.onClick,
                )
            }
        }
    }
}

@Composable
private fun LoginSubscreen(
    state: LoginUiState,
    onPrepareLogin: () -> Unit,
    onBack: () -> Unit,
    onRefreshQr: () -> Unit,
    onTabChange: (LoginTab) -> Unit,
    onRequestPasswordLogin: (String, String) -> Unit,
    onRequestSmsCode: (Int, String) -> Unit,
    onLoginWithSms: (Int, String, String) -> Unit,
    onSetCountryId: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var countryMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onPrepareLogin()
    }
    LaunchedEffect(state.isRiskSmsMode, state.riskPrefillPhone) {
        val prefillPhone = state.riskPrefillPhone.orEmpty()
        if (state.isRiskSmsMode && prefillPhone.isNotBlank() && phone != prefillPhone) {
            phone = prefillPhone
        }
    }

    MePageContainer(modifier = modifier) {
        val nestedScrollInterop = rememberNestedScrollInteropConnection()
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .nestedScroll(nestedScrollInterop),
        ) {
            item { LoginHeader(onBack = onBack) }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                LoginTabSelector(
                    selectedTab = state.activeTab,
                    onTabChange = onTabChange,
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
            item {
                LoginPanelCard(
                    state = state,
                    phone = phone,
                    smsCode = smsCode,
                    account = account,
                    password = password,
                    countryMenuExpanded = countryMenuExpanded,
                    onPhoneChange = { phone = it },
                    onSmsCodeChange = { smsCode = it },
                    onAccountChange = { account = it },
                    onPasswordChange = { password = it },
                    onCountryMenuExpandedChange = { countryMenuExpanded = it },
                    onRefreshQr = onRefreshQr,
                    onRequestPasswordLogin = {
                        onRequestPasswordLogin(account.trim(), password)
                    },
                    onRequestSmsCode = {
                        onRequestSmsCode(state.selectedCountryId, phone.trim())
                    },
                    onLoginWithSms = {
                        onLoginWithSms(state.selectedCountryId, phone.trim(), smsCode.trim())
                    },
                    onSetCountryId = onSetCountryId,
                )
            }
            if (!state.errorText.isNullOrBlank()) {
                item { Spacer(Modifier.height(12.dp)) }
                item { ErrorCard(state.errorText.orEmpty()) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MeExpressiveDefaults.listItemContainerColor,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24),
                contentDescription = stringResource(R.string.settings_back),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.me_login_screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginTabSelector(
    selectedTab: LoginTab,
    onTabChange: (LoginTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        LoginTab.Qr to R.string.login_tab_qr,
        LoginTab.Password to R.string.login_tab_password,
        LoginTab.Sms to R.string.login_tab_sms,
    )
    val tabColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        tabs.forEachIndexed { index, (tab, labelRes) ->
            ToggleButton(
                checked = tab == selectedTab,
                onCheckedChange = { onTabChange(tab) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = tabColors,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(
                    text = stringResource(labelRes),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginPanelCard(
    state: LoginUiState,
    phone: String,
    smsCode: String,
    account: String,
    password: String,
    countryMenuExpanded: Boolean,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCountryMenuExpandedChange: (Boolean) -> Unit,
    onRefreshQr: () -> Unit,
    onRequestPasswordLogin: () -> Unit,
    onRequestSmsCode: () -> Unit,
    onLoginWithSms: () -> Unit,
    onSetCountryId: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    Surface(
        color = MeExpressiveDefaults.listItemContainerColor,
        shape = MeExpressiveShapes.cardShape,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LoginPanelDefaultHeight),
    ) {
        // Alternate login methods are sibling destinations, so a fade-through swap reads
        // cleaner than a full cross-slide and avoids visible ghosting during overlap.
        AnimatedContent(
            targetState = state.activeTab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (
                    fadeIn(
                        animationSpec = tween(durationMillis = 180, delayMillis = 70),
                        initialAlpha = 0.05f,
                    ) + scaleIn(
                        initialScale = 0.98f,
                        animationSpec = motionScheme.defaultSpatialSpec(),
                    )
                ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 70)),
                ).using(
                    SizeTransform(
                        clip = true,
                        sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() },
                    ),
                ).apply {
                    targetContentZIndex = 1f
                }
            },
            label = "loginPanel",
            contentKey = { it },
        ) { tab ->
            when (tab) {
                LoginTab.Qr -> QrLoginPanel(
                    state = state,
                    onRefreshQr = onRefreshQr,
                )
                LoginTab.Password -> PasswordLoginPanel(
                    account = account,
                    password = password,
                    enabled = !state.isLoggingIn,
                    onAccountChange = onAccountChange,
                    onPasswordChange = onPasswordChange,
                    onLogin = onRequestPasswordLogin,
                )
                LoginTab.Sms -> SmsLoginPanel(
                    state = state,
                    phone = phone,
                    smsCode = smsCode,
                    countryMenuExpanded = countryMenuExpanded,
                    onPhoneChange = onPhoneChange,
                    onSmsCodeChange = onSmsCodeChange,
                    onCountryMenuExpandedChange = onCountryMenuExpandedChange,
                    onRequestSmsCode = onRequestSmsCode,
                    onLogin = onLoginWithSms,
                    onSetCountryId = onSetCountryId,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QrLoginPanel(
    state: LoginUiState,
    onRefreshQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val qrPrompt = stringResource(R.string.login_status_scan)
    val qrScannedStatus = stringResource(R.string.login_status_scanned)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = qrPrompt,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.size(220.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                val bitmap = state.qrBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.login_qr_desc),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    )
                } else if (state.isPolling) {
                    CircularProgressIndicator()
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_account_circle_24),
                        contentDescription = null,
                        tint = Color(0xFF5F6368),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = QrStatusSlotMinHeight),
        ) {
            if (state.qrStatusText != qrPrompt) {
                Text(
                    text = state.qrStatusText,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                    color = if (state.qrStatusText == qrScannedStatus) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRefreshQr,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.widthIn(min = 160.dp),
        ) {
            Text(stringResource(R.string.login_refresh))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasswordLoginPanel(
    account: String,
    password: String,
    enabled: Boolean,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        OutlinedTextField(
            value = account,
            onValueChange = onAccountChange,
            placeholder = { Text(stringResource(R.string.login_account_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = LoginFormControlShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(LoginFormControlHeight),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = { Text(stringResource(R.string.login_password_hint)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = LoginFormControlShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(LoginFormControlHeight),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(14.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.login_password_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onLogin,
            shapes = ButtonDefaults.shapes(),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.login_action))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SmsLoginPanel(
    state: LoginUiState,
    phone: String,
    smsCode: String,
    countryMenuExpanded: Boolean,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onCountryMenuExpandedChange: (Boolean) -> Unit,
    onRequestSmsCode: () -> Unit,
    onLogin: () -> Unit,
    onSetCountryId: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lockContactInput = state.isRiskSmsMode && state.riskLockPhoneInput
    val selectedCountryLabel = state.countries.firstOrNull { it.id == state.selectedCountryId }?.label
        ?: "+${state.selectedCountryId}"
    val countrySelectorEnabled = !lockContactInput && state.countries.isNotEmpty()
    val requestSmsEnabled = !state.isSendingSms
    val formButtonShapes = ButtonDefaults.shapesFor(LoginFormControlHeight).copy(
        shape = LoginFormControlShape,
    )
    val countrySelectorWidth = 104.dp
    val requestSmsButtonWidth = 124.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.width(countrySelectorWidth)) {
                OutlinedButton(
                    onClick = { onCountryMenuExpandedChange(true) },
                    shapes = formButtonShapes,
                    enabled = countrySelectorEnabled,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LoginFormControlHeight),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = selectedCountryLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_more_24),
                            contentDescription = null,
                        )
                    }
                }
                DropdownMenu(
                    expanded = countryMenuExpanded,
                    onDismissRequest = { onCountryMenuExpandedChange(false) },
                ) {
                    state.countries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSetCountryId(option.id)
                                onCountryMenuExpandedChange(false)
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = phone,
                onValueChange = onPhoneChange,
                placeholder = { Text(stringResource(R.string.login_phone_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                enabled = !lockContactInput,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = LoginFormControlShape,
                modifier = Modifier
                    .weight(1f)
                    .height(LoginFormControlHeight),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = smsCode,
                onValueChange = onSmsCodeChange,
                placeholder = { Text(stringResource(R.string.login_sms_code_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = LoginFormControlShape,
                modifier = Modifier
                    .weight(1f)
                    .height(LoginFormControlHeight),
            )
            OutlinedButton(
                onClick = onRequestSmsCode,
                shapes = formButtonShapes,
                enabled = requestSmsEnabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .height(LoginFormControlHeight)
                    .width(requestSmsButtonWidth),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                ) {
                    Text(
                        text = stringResource(R.string.login_send_sms),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }

        Button(
            onClick = onLogin,
            shapes = ButtonDefaults.shapes(),
            enabled = !state.isLoggingIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.login_action))
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
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
        color = MeExpressiveDefaults.listItemContainerColor,
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
                            VipLabel(
                                userInfo = userInfo,
                                showVipBadge = showVipBadge,
                            )
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
                        value = (userInfo.following ?: 0).toString(),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStat(
                        label = stringResource(R.string.login_stat_follower),
                        value = (userInfo.follower ?: 0).toString(),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStat(
                        label = stringResource(R.string.login_stat_dynamic),
                        value = (userInfo.dynamic ?: 0).toString(),
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

@Composable
private fun ProfileLoadingCard(
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MeExpressiveDefaults.listItemContainerColor,
        shape = MeExpressiveShapes.cardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            AndroidView(
                factory = { context ->
                    android.view.LayoutInflater.from(context)
                        .inflate(R.layout.view_expressive_loading_indicator, null, false)
                },
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
    showVipBadge: Boolean,
    modifier: Modifier = Modifier,
) {
    if (showVipBadge && !userInfo.vipLabelImageUrl.isNullOrBlank()) {
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
) {
    AndroidView(
        factory = { context ->
            android.widget.ImageView(context).apply {
                adjustViewBounds = true
                scaleType = contentScale.toScaleType()
            }
        },
        update = { imageView ->
            imageView.scaleType = contentScale.toScaleType()
            when {
                model.isNullOrBlank() && fallbackRes != null -> imageView.setImageResource(fallbackRes)
                model.isNullOrBlank() -> imageView.setImageDrawable(null)
                else -> imageView.load(model) {
                    decoderFactory(SvgDecoder.Factory())
                    fallback(fallbackRes ?: 0)
                    error(fallbackRes ?: 0)
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
            .background(MeExpressiveDefaults.pageContainerColor),
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
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val top by animateDpAsState(
        targetValue = if (enabled && isPressed) {
            40.dp
        } else if (items == 1 || index == 0) {
            20.dp
        } else {
            4.dp
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "meListItemTop",
    )
    val bottom by animateDpAsState(
        targetValue = if (enabled && isPressed) {
            40.dp
        } else if (items == 1 || index == items - 1) {
            20.dp
        } else {
            4.dp
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "meListItemBottom",
    )

    ListItem(
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MeExpressiveDefaults.listItemContainerColor,
        ),
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = top,
                    topEnd = top,
                    bottomStart = bottom,
                    bottomEnd = bottom,
                ),
            )
            .alpha(if (enabled) 1f else 0.52f)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    )
}

private object MeExpressiveDefaults {
    val pageContainerColor: Color
        @Composable
        get() = if (!MaterialTheme.colorScheme.usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    val listItemContainerColor: Color
        @Composable
        get() = if (!MaterialTheme.colorScheme.usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceBright
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
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

private fun formatCoins(value: Double?): String {
    if (value == null) return "-"
    val rounded = value.toLong().toDouble()
    return if (abs(value - rounded) < 0.01) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

private fun androidx.compose.material3.ColorScheme.usesPureBlackSurfaces(): Boolean {
    return surface == Color.Black && background == Color.Black
}

private fun ContentScale.toScaleType(): android.widget.ImageView.ScaleType {
    return when (this) {
        ContentScale.Crop -> android.widget.ImageView.ScaleType.CENTER_CROP
        else -> android.widget.ImageView.ScaleType.FIT_CENTER
    }
}
