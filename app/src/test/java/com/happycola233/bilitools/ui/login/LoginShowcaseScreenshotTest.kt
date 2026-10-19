package com.happycola233.bilitools.ui.login

import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 登录页整页效果图：只用于人工比对界面改版前后的观感，不做断言。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginShowcaseScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun light() = captureAll("light", AppSettings(themeMode = AppThemeMode.Light))
    @Test fun dark() = captureAll("dark", AppSettings(themeMode = AppThemeMode.Dark, darkModePureBlack = false))
    @Test fun pureBlack() = captureAll("black", AppSettings(themeMode = AppThemeMode.Dark, darkModePureBlack = true))

    @Test
    @Config(qualifiers = "en-w360dp-h780dp-xhdpi")
    fun narrowEnglish() = captureAll("en360-light", AppSettings(themeMode = AppThemeMode.Light))

    @Test
    fun largeFont() {
        RuntimeEnvironment.setFontScale(1.3f)
        captureAll("font130-light", AppSettings(themeMode = AppThemeMode.Light))
    }

    private data class Scenario(
        val name: String,
        val state: LoginUiState,
        val typeIntoFields: List<String> = emptyList(),
        val focusFirstField: Boolean = false,
        val captureDialog: Boolean = false,
    )

    private fun scenarios(): List<Scenario> {
        val qrBitmap = QrCodeGenerator.generate(
            "https://account.bilibili.com/h5/account-h5/auth/scan-web?navhide=1&qrcode_key=0123456789abcdef0123456789abcdef",
            520,
        )
        val qr = LoginUiState(
            qrBitmap = qrBitmap,
            qrStatusRes = R.string.login_status_waiting,
            isPolling = true,
            countries = listOf(CountryOption(86, "+86"), CountryOption(852, "+852"), CountryOption(1, "+1")),
        )
        val password = qr.copy(activeTab = LoginTab.Password)
        val sms = qr.copy(activeTab = LoginTab.Sms)
        return listOf(
            Scenario("01-qr", qr),
            Scenario("02-qr-generating", qr.copy(qrBitmap = null, qrStatusRes = R.string.login_status_generating)),
            Scenario("03-qr-scanned", qr.copy(qrStatusRes = R.string.login_status_scanned)),
            Scenario("04-qr-expired", qr.copy(qrStatusRes = R.string.login_status_expired, isPolling = false)),
            Scenario(
                "05-qr-failed",
                qr.copy(
                    qrBitmap = null,
                    qrStatusRes = R.string.login_status_qr_failed,
                    isPolling = false,
                    errorText = "Unable to resolve host \"passport.bilibili.com\"",
                ),
            ),
            Scenario("06-password", password),
            Scenario("07-password-focused", password, focusFirstField = true),
            Scenario("08-password-filled", password, typeIntoFields = listOf("bilitools@example.com", "hunter22")),
            Scenario("09-password-logging-in", password.copy(isLoggingIn = true)),
            Scenario("10-password-error", password.copy(errorText = "用户名或密码错误")),
            Scenario(
                "11-password-risk-dialog",
                password.copy(
                    riskVerificationPrompt = RiskVerificationPrompt("本次登录环境存在风险，需使用手机号进行验证或绑定"),
                ),
                captureDialog = true,
            ),
            Scenario("12-sms", sms),
            Scenario("13-sms-filled", sms, typeIntoFields = listOf("13800138000", "123456")),
            Scenario("14-sms-sending", sms.copy(isSendingSms = true)),
            Scenario("15-sms-error", sms.copy(errorText = "短信发送次数已达上限")),
            Scenario(
                "16-sms-risk-locked",
                sms.copy(isRiskSmsMode = true, riskLockPhoneInput = true, riskPrefillPhone = "138****8000"),
            ),
        )
    }

    private fun captureAll(variant: String, settings: AppSettings) {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_BiliTools)
        val themed = settings.copy(themeColor = AppThemeColor.Periwinkle)
        var state by mutableStateOf(LoginUiState())
        compose.setContent {
            // 让矢量图标里的主题属性（如返回箭头的 colorOnSurface）按应用主题解析
            CompositionLocalProvider(LocalContext provides context) {
                BiliToolsLoginContent(
                    settings = themed,
                    state = state,
                    onExit = {},
                    onPrepareLogin = {},
                    onRefreshQr = {},
                    onTabChange = { state = state.copy(activeTab = it) },
                    onRequestPasswordLogin = { _, _ -> },
                    onRequestSmsCode = { _, _ -> },
                    onLoginWithSms = { _, _, _ -> },
                    onSetCountryId = { state = state.copy(selectedCountryId = it) },
                    onDismissError = {},
                    onConfirmRiskVerification = { state = state.copy(riskVerificationPrompt = null) },
                    onDismissRiskVerification = { state = state.copy(riskVerificationPrompt = null) },
                )
            }
        }
        compose.waitForIdle()
        // 加载指示是无限动画，关掉自动推进时钟，每个场景手动推进到动画稳定
        compose.mainClock.autoAdvance = false
        scenarios().forEach { scenario ->
            compose.runOnIdle { state = scenario.state }
            // 先把状态写入派发给重组，再推进时钟，否则截图可能停在上一个场景
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_500)
            val fields = compose.onAllNodes(hasSetTextAction())
            scenario.typeIntoFields.forEachIndexed { index, text ->
                fields[index].performTextInput(text)
            }
            if (scenario.focusFirstField) fields[0].performClick()
            compose.mainClock.advanceTimeBy(1_500)
            val output = File("../.tmp/login-md3e/redesign/$variant/${scenario.name}.png")
            output.parentFile!!.mkdirs()
            val target = if (scenario.captureDialog) compose.onNode(isDialog()) else compose.onRoot()
            output.outputStream().use {
                target.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
