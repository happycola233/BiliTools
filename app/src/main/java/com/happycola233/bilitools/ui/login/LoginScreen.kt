package com.happycola233.bilitools.ui.login

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.ui.AppAlertDialog
import com.happycola233.bilitools.ui.LeadingIconText
import com.happycola233.bilitools.ui.SubpageScaffold
import com.happycola233.bilitools.ui.haptics.rememberAppHaptics
import com.happycola233.bilitools.ui.theme.AppAccents
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme

private val LoginCardPadding = 20.dp
private val LoginMethodSelectorHeight = 44.dp

/** 输入框与主按钮同高，三种登录方式的表单节奏保持一致；放大字号时两者都会随内容长高。 */
private val LoginControlHeight = 56.dp
private val LoginFieldSpacing = 12.dp
private val QrTileSize = 200.dp

/** 生成的二维码图片自带 4 个模块宽的白边，外框只需留一点余量。 */
private val QrTilePadding = 4.dp
private val QrStatusBadgeSize = 56.dp

/** 登录页入口：与其他独立页面一样在根部套上 [BiliToolsTheme]，跟随应用的配色与深浅设置。 */
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
    onDismissError: () -> Unit,
    onConfirmRiskVerification: () -> Unit,
    onDismissRiskVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BiliToolsTheme(settings = settings) {
        LoginScreen(
            state = state,
            onBack = onExit,
            onPrepareLogin = onPrepareLogin,
            onRefreshQr = onRefreshQr,
            onTabChange = onTabChange,
            onRequestPasswordLogin = onRequestPasswordLogin,
            onRequestSmsCode = onRequestSmsCode,
            onLoginWithSms = onLoginWithSms,
            onSetCountryId = onSetCountryId,
            onDismissError = onDismissError,
            onConfirmRiskVerification = onConfirmRiskVerification,
            onDismissRiskVerification = onDismissRiskVerification,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoginScreen(
    state: LoginUiState,
    onBack: () -> Unit,
    onPrepareLogin: () -> Unit,
    onRefreshQr: () -> Unit,
    onTabChange: (LoginTab) -> Unit,
    onRequestPasswordLogin: (String, String) -> Unit,
    onRequestSmsCode: (Int, String) -> Unit,
    onLoginWithSms: (Int, String, String) -> Unit,
    onSetCountryId: (Int) -> Unit,
    onDismissError: () -> Unit,
    onConfirmRiskVerification: () -> Unit,
    onDismissRiskVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onPrepareLogin()
    }
    LaunchedEffect(state.isRiskSmsMode, state.riskPrefillPhone) {
        val prefillPhone = state.riskPrefillPhone.orEmpty()
        if (state.isRiskSmsMode && prefillPhone.isNotBlank() && phone != prefillPhone) {
            phone = prefillPhone
        }
    }

    // 修改输入即视为在纠正上一次的错误，提示随之收起
    val clearError = { if (state.errorText != null) onDismissError() }

    SubpageScaffold(
        title = stringResource(R.string.login_title),
        subtitle = stringResource(R.string.login_account_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 键盘弹出时滚动区让出输入法高度，聚焦的输入框会被自动滚入视野；
                // 底部导航栏已计入 innerPadding，先扣除以免重复留白
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 24.dp),
        ) {
            LoginPanelCard(
                state = state,
                account = account,
                password = password,
                phone = phone,
                smsCode = smsCode,
                onAccountChange = {
                    account = it
                    clearError()
                },
                onPasswordChange = {
                    password = it
                    clearError()
                },
                onPhoneChange = {
                    phone = it
                    clearError()
                },
                onSmsCodeChange = {
                    smsCode = it
                    clearError()
                },
                onTabChange = onTabChange,
                onRefreshQr = onRefreshQr,
                onRequestPasswordLogin = { onRequestPasswordLogin(account.trim(), password) },
                onRequestSmsCode = { onRequestSmsCode(state.selectedCountryId, phone.trim()) },
                onLoginWithSms = {
                    onLoginWithSms(state.selectedCountryId, phone.trim(), smsCode.trim())
                },
                onSetCountryId = onSetCountryId,
            )
        }
    }

    state.riskVerificationPrompt?.let { prompt ->
        RiskVerificationDialog(
            message = prompt.message,
            onVerify = onConfirmRiskVerification,
            onDismiss = onDismissRiskVerification,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginPanelCard(
    state: LoginUiState,
    account: String,
    password: String,
    phone: String,
    smsCode: String,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onTabChange: (LoginTab) -> Unit,
    onRefreshQr: () -> Unit,
    onRequestPasswordLogin: () -> Unit,
    onRequestSmsCode: () -> Unit,
    onLoginWithSms: () -> Unit,
    onSetCountryId: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    // 卡片只包住当前登录方式的内容，切换时高度随内容弹性伸缩
    Surface(
        color = AppSurfaces.cardContainerColor,
        shape = MaterialTheme.shapes.largeIncreased,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(LoginCardPadding)) {
            LoginMethodSelector(
                selected = state.activeTab,
                onSelect = onTabChange,
            )
            Spacer(Modifier.height(24.dp))
            // 几种登录方式是同级目的地，淡出淡入比整屏横滑更干净，也不会在交叠时出现重影
            AnimatedContent(
                targetState = state.activeTab,
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
                            clip = false,
                            sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() },
                        ),
                    )
                },
                contentKey = { it },
                label = "loginMethodPanel",
            ) { method ->
                when (method) {
                    LoginTab.Qr -> QrLoginPanel(
                        state = state,
                        onRefreshQr = onRefreshQr,
                    )
                    LoginTab.Password -> PasswordLoginPanel(
                        account = account,
                        password = password,
                        isLoggingIn = state.isLoggingIn,
                        errorText = state.errorText,
                        onAccountChange = onAccountChange,
                        onPasswordChange = onPasswordChange,
                        onLogin = onRequestPasswordLogin,
                    )
                    LoginTab.Sms -> SmsLoginPanel(
                        state = state,
                        phone = phone,
                        smsCode = smsCode,
                        onPhoneChange = onPhoneChange,
                        onSmsCodeChange = onSmsCodeChange,
                        onRequestSmsCode = onRequestSmsCode,
                        onLogin = onLoginWithSms,
                        onSetCountryId = onSetCountryId,
                    )
                }
            }
        }
    }
}

/** 登录方式切换：与解析页的分段选项同为连接式按钮组，按下时被按项略微变宽、两侧让位。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginMethodSelector(
    selected: LoginTab,
    onSelect: (LoginTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val methods = LoginTab.entries
    val select: (LoginTab) -> Unit = { method ->
        if (method != selected) {
            haptics.select()
            onSelect(method)
        }
    }
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        expandedRatio = 0.16f,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        methods.forEachIndexed { index, method ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    ToggleButton(
                        checked = method == selected,
                        onCheckedChange = { select(method) },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            methods.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = AppAccents.toggleButtonColors(),
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(interactionSource)
                            .heightIn(min = LoginMethodSelectorHeight)
                            .semantics { role = Role.RadioButton },
                    ) {
                        Text(
                            text = stringResource(method.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text(stringResource(method.labelRes)) },
                        leadingIcon = {
                            if (method == selected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check_rounded_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        onClick = {
                            select(method)
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

/** 二维码区域的展示状态，由轮询状态推导，决定二维码之上叠加什么徽标。 */
private enum class QrTileState {
    /** 正在生成：显示加载指示，旧二维码（如有）淡到几乎不可见。 */
    Generating,

    /** 二维码有效，等待扫码。 */
    Ready,

    /** 已扫码或已登录，等待手机端确认：盖上对勾。 */
    Confirming,

    /** 轮询已结束（过期、失败或尚未生成）：盖上刷新徽标，点按即可重新生成。 */
    NeedsRefresh,
}

private fun LoginUiState.qrTileState(): QrTileState = when {
    qrStatusRes == R.string.login_status_generating -> QrTileState.Generating
    qrStatusRes == R.string.login_status_scanned ||
        qrStatusRes == R.string.login_status_signed_in -> QrTileState.Confirming
    !isPolling -> QrTileState.NeedsRefresh
    qrBitmap == null -> QrTileState.Generating
    else -> QrTileState.Ready
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QrLoginPanel(
    state: LoginUiState,
    onRefreshQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val tileState = state.qrTileState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        QrCodeTile(
            bitmap = state.qrBitmap,
            tileState = tileState,
            onRefresh = onRefreshQr,
        )
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = state.qrStatusRes,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec())
                    .togetherWith(fadeOut(motionScheme.fastEffectsSpec()))
            },
            label = "qrLoginStatus",
        ) { statusRes ->
            Text(
                text = stringResource(statusRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (statusRes == R.string.login_status_scanned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LoginErrorMessage(
            message = state.errorText,
            centered = true,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onRefreshQr,
            enabled = tileState != QrTileState.Generating,
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh_24),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.login_refresh))
        }
    }
}

/**
 * 二维码要白底黑码才好扫，底色固定为白色、不跟随深浅模式；叠在上面的状态徽标也只取 fixed 色组，
 * 两种模式下观感一致。轮询结束后整块可点按重新生成。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QrCodeTile(
    bitmap: Bitmap?,
    tileState: QrTileState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val qrAlpha by animateFloatAsState(
        targetValue = if (tileState == QrTileState.Ready) 1f else 0.12f,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "qrCodeAlpha",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(QrTileSize)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Color.White)
            .clickable(
                enabled = tileState == QrTileState.NeedsRefresh,
                onClickLabel = stringResource(R.string.login_refresh),
                role = Role.Button,
                onClick = onRefresh,
            ),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = stringResource(R.string.login_qr_desc),
                // 二维码模块是轴对齐的色块，最近邻缩放让边缘保持锐利
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(QrTilePadding)
                    .graphicsLayer { alpha = qrAlpha },
            )
        }
        AnimatedContent(
            targetState = tileState,
            transitionSpec = {
                (fadeIn(motionScheme.fastEffectsSpec()) + scaleIn(
                    initialScale = 0.6f,
                    animationSpec = motionScheme.fastSpatialSpec(),
                )).togetherWith(
                    fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(
                        targetScale = 0.6f,
                        animationSpec = motionScheme.fastSpatialSpec(),
                    ),
                )
            },
            contentAlignment = Alignment.Center,
            label = "qrTileBadge",
        ) { state ->
            when (state) {
                QrTileState.Generating -> ContainedLoadingIndicator(
                    containerColor = AppAccents.fill,
                    indicatorColor = AppAccents.onFill,
                    modifier = Modifier.size(QrStatusBadgeSize),
                )
                QrTileState.Ready -> Unit
                QrTileState.Confirming -> QrStatusBadge(iconRes = R.drawable.ic_check_rounded_24)
                QrTileState.NeedsRefresh -> QrStatusBadge(iconRes = R.drawable.ic_refresh_24)
            }
        }
    }
}

@Composable
private fun QrStatusBadge(@DrawableRes iconRes: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(QrStatusBadgeSize)
            .background(AppAccents.fill, CircleShape),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = AppAccents.onFill,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun PasswordLoginPanel(
    account: String,
    password: String,
    isLoggingIn: Boolean,
    errorText: String?,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val enabled = !isLoggingIn
    val canSubmit = enabled && account.isNotBlank() && password.isNotBlank()
    val submit = {
        focusManager.clearFocus()
        onLogin()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(LoginFieldSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        LoginTextField(
            value = account,
            onValueChange = onAccountChange,
            placeholder = stringResource(R.string.login_account_hint),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
            ),
        )
        Column {
            LoginTextField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = stringResource(R.string.login_password_hint),
                enabled = enabled,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() }),
                trailing = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (passwordVisible) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24,
                            ),
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.login_password_hide else R.string.login_password_show,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            LoginErrorMessage(message = errorText)
        }
        LoginSubmitButton(
            loading = isLoggingIn,
            enabled = canSubmit,
            onClick = submit,
            modifier = Modifier.padding(top = 8.dp),
        )
        LoginHint(text = stringResource(R.string.login_password_note))
    }
}

@Composable
private fun SmsLoginPanel(
    state: LoginUiState,
    phone: String,
    smsCode: String,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onRequestSmsCode: () -> Unit,
    onLogin: () -> Unit,
    onSetCountryId: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    // 风控验证拿到了打码手机号时锁定号码，只允许填写验证码
    val lockContactInput = state.isRiskSmsMode && state.riskLockPhoneInput
    val selectedCountryLabel = state.countries.firstOrNull { it.id == state.selectedCountryId }?.label
        ?: "+${state.selectedCountryId}"
    val canSubmit = !state.isLoggingIn && phone.isNotBlank() && smsCode.isNotBlank()
    val submit = {
        focusManager.clearFocus()
        onLogin()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(LoginFieldSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        LoginTextField(
            value = phone,
            onValueChange = onPhoneChange,
            placeholder = stringResource(R.string.login_phone_hint),
            enabled = !lockContactInput,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
            ),
            leading = {
                CountryCodePicker(
                    selectedLabel = selectedCountryLabel,
                    countries = state.countries,
                    enabled = !lockContactInput && state.countries.isNotEmpty(),
                    onSelect = onSetCountryId,
                )
                LoginFieldDivider()
            },
        )
        Column {
            LoginTextField(
                value = smsCode,
                onValueChange = onSmsCodeChange,
                placeholder = stringResource(R.string.login_sms_code_hint),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() }),
                trailing = {
                    LoginFieldDivider()
                    SendSmsCodeButton(
                        loading = state.isSendingSms,
                        onClick = onRequestSmsCode,
                    )
                },
            )
            LoginErrorMessage(message = state.errorText)
        }
        LoginSubmitButton(
            loading = state.isLoggingIn,
            enabled = canSubmit,
            onClick = submit,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * 登录表单输入框：卡片内嵌层底色加 `outlineVariant` 细描边，与解析页链接输入框是同一套样式；
 * 聚焦时描边换成主色。区号选择、获取验证码这类附属操作放进框内两端，用细分隔线与输入区隔开。
 */
@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (focused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "loginFieldBorder",
    )
    val shape = MaterialTheme.shapes.large
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // 高度取输入区的固有高度：放大字号时文字不会被裁掉，两端的附属操作也随之撑满
            .height(IntrinsicSize.Min)
            .heightIn(min = LoginControlHeight)
            .clip(shape)
            .background(AppSurfaces.insetContainerColor)
            .border(width = 1.dp, color = borderColor, shape = shape),
    ) {
        leading?.invoke(this)
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            enabled = enabled,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            // 底色与描边由外层统一绘制，这里只保留文字、占位提示与光标
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

@Composable
private fun LoginFieldDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun CountryCodePicker(
    selectedLabel: String,
    countries: List<CountryOption>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val haptics = rememberAppHaptics()
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxHeight()
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                .padding(start = 16.dp, end = 10.dp)
                .alpha(if (enabled) 1f else DisabledContentAlpha),
        ) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand_more_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            countries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        haptics.select()
                        onSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 获取验证码：嵌在验证码输入框末端的文字按钮，发送中用加载指示占位，按钮宽度不随状态跳动。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SendSmsCodeButton(
    loading: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = !loading,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.login_send_sms),
                maxLines = 1,
                modifier = Modifier.alpha(if (loading) 0f else 1f),
            )
            if (loading) {
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** 登录主按钮：与输入框同高；提交中换成加载指示，既阻止重复提交也给出明确反馈。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginSubmitButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    Button(
        onClick = {
            haptics.confirm()
            onClick()
        },
        enabled = enabled && !loading,
        shapes = ButtonDefaults.shapesFor(LoginControlHeight),
        // 提交中按钮不可点，但要保住强调色容器，否则加载指示会落在禁用灰上
        colors = if (loading) {
            ButtonDefaults.buttonColors(
                disabledContainerColor = AppAccents.fill,
                disabledContentColor = AppAccents.onFill,
            )
        } else {
            AppAccents.filledButtonColors()
        },
        contentPadding = ButtonDefaults.contentPaddingFor(LoginControlHeight),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LoginControlHeight),
    ) {
        if (loading) {
            LoadingIndicator(
                color = AppAccents.onFill,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.login_action),
                style = ButtonDefaults.textStyleFor(LoginControlHeight),
            )
        }
    }
}

/**
 * 表单内的错误提示，紧贴在出错的输入框或状态文字下方，样式与解析页输入框的报错一致。
 * 提示会一直保留，直到用户修改输入、重新提交或切换登录方式。
 */
@Composable
private fun LoginErrorMessage(
    message: String?,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val haptics = rememberAppHaptics()
    val motionScheme = MaterialTheme.motionScheme
    // 收起动画期间沿用最后一条提示，避免文字在退场时突然消失
    var lastMessage by remember { mutableStateOf(message.orEmpty()) }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            lastMessage = message
            haptics.reject()
        }
    }
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(motionScheme.fastEffectsSpec()) +
            expandVertically(motionScheme.defaultSpatialSpec()),
        exit = fadeOut(motionScheme.fastEffectsSpec()) +
            shrinkVertically(motionScheme.defaultSpatialSpec()),
        modifier = modifier,
    ) {
        LeadingIconText(
            iconRes = R.drawable.ic_error_24,
            text = lastMessage,
            color = MaterialTheme.colorScheme.error,
            centered = centered,
            modifier = Modifier
                .padding(start = 4.dp, top = 8.dp, end = 4.dp)
                // 出现时由读屏软件主动播报
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** 表单下方的轻量提示：不加底色块，只用次要文字色，避免喧宾夺主。 */
@Composable
private fun LoginHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    LeadingIconText(
        iconRes = R.drawable.ic_info_24,
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** 密码登录触发风控时的确认对话框：说明原因，并引导到短信验证。 */
@Composable
private fun RiskVerificationDialog(
    message: String?,
    onVerify: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_verified_user_rounded_24),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.login_risk_title)) },
        text = {
            Text(message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.login_risk_message))
        },
        confirmButton = {
            TextButton(onClick = onVerify) {
                Text(stringResource(R.string.login_risk_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/** 与 Material 3 禁用内容的透明度一致。 */
private const val DisabledContentAlpha = 0.38f

@get:StringRes
private val LoginTab.labelRes: Int
    get() = when (this) {
        LoginTab.Qr -> R.string.login_tab_qr
        LoginTab.Password -> R.string.login_tab_password
        LoginTab.Sms -> R.string.login_tab_sms
    }
