package com.happycola233.bilitools.ui.eggs

import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.EasterEggCopy
import com.happycola233.bilitools.data.EasterEggRepository
import com.happycola233.bilitools.data.EggCopyLanguage
import com.happycola233.bilitools.data.EggMilestone
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.MediaUpper
import com.happycola233.bilitools.data.model.UserInfo
import com.happycola233.bilitools.ui.MainCollapsingTopBar
import com.happycola233.bilitools.ui.login.BiliToolsLoginContent
import com.happycola233.bilitools.ui.login.LoginUiState
import com.happycola233.bilitools.ui.login.QrCodeGenerator
import com.happycola233.bilitools.ui.me.BiliToolsMeContent
import com.happycola233.bilitools.ui.parse.ParseConversionSettings
import com.happycola233.bilitools.ui.parse.ParseScreenContent
import com.happycola233.bilitools.ui.parse.ParseUiState
import com.happycola233.bilitools.ui.settings.AboutSettingsScreen
import com.happycola233.bilitools.ui.settings.UpdateCheckUiState
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 全部彩蛋的效果图与动画帧，只用于人工查看，不做断言。
 * 帧序列由 `.tmp/eggs` 下的脚本合成 GIF。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h560dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EggShowcaseScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()
    private val copy: EasterEggCopy by lazy {
        EasterEggRepository(context).copy(EggCopyLanguage.SimplifiedChinese)
    }
    private val normalDay = LocalDateTime.of(2026, 11, 21, 15, 0)

    private fun settings(dark: Boolean) = AppSettings(
        themeMode = if (dark) AppThemeMode.Dark else AppThemeMode.Light,
        themeColor = AppThemeColor.Periwinkle,
    )

    // ---------------- 首页小贴士 ----------------

    private data class HomeScene(
        val name: String,
        val clock: LocalDateTime,
        val milestone: EggMilestone? = null,
        val tips: List<String>? = null,
        val loading: Boolean = false,
        val inputs: List<String> = emptyList(),
        val fools: Boolean = false,
    )

    private val homeScenes = listOf(
        HomeScene("01-tip-meme", normalDay, tips = listOf("「这个视频我下载了，就等于我看过了。」——鲁迅（没说过）")),
        HomeScene("02-tip-useful", normalDay, tips = listOf("在 B 站 App 里点「分享」，选 BiliTools，不用切过来也能下载。")),
        HomeScene("03-rickroll", normalDay, tips = emptyList()),
        HomeScene("04-cheers", normalDay, inputs = listOf("干杯")),
        HomeScene("05-parse-air", normalDay, inputs = List(5) { "" }),
        HomeScene("06-launch-233", normalDay, milestone = EggMilestone.Launch(233)),
        HomeScene("07-download-100", normalDay, milestone = EggMilestone.Downloads(100)),
        HomeScene("08-late-night", LocalDateTime.of(2026, 11, 21, 2, 30)),
        HomeScene("09-bilibili-birthday", LocalDateTime.of(2027, 6, 26, 12, 0)),
        HomeScene("10-bilitools-birthday", LocalDateTime.of(2027, 2, 4, 12, 0)),
        HomeScene("11-programmers-day", LocalDateTime.of(2027, 10, 24, 12, 0)),
        HomeScene("12-april-fools", LocalDateTime.of(2027, 4, 1, 12, 0), fools = true),
        HomeScene("13-loading-signal", normalDay, loading = true),
    )

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h1900dp-xhdpi")
    fun homeTipsLight() = captureHomeTips(dark = false)

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h1900dp-xhdpi")
    fun homeTipsDark() = captureHomeTips(dark = true)

    /** 所有小贴士状态并排画在一张图里，每条前面标上场景名 */
    private fun captureHomeTips(dark: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BiliToolsTheme(settings(dark)) {
                Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppSurfaces.pageContainerColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    homeScenes.forEach { scene ->
                        val sceneCopy = scene.tips?.let { copy.copy(tips = it) } ?: copy
                        val controller = remember(scene) {
                            HomeTipController(sceneCopy, HomeTipPicker(sceneCopy, FirstChoiceRandom), { scene.clock }, scene.milestone)
                                .also { controller -> scene.inputs.forEach(controller::interceptParse) }
                        }
                        androidx.compose.material3.Text(
                            text = scene.name,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                        )
                        HomeTipBanner(controller = controller, loading = scene.loading, active = true, onParseTarget = {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_800)
        compose.onRoot().saveEggShowcase("home/tips-${if (dark) "dark" else "light"}")
    }

    @Test fun homePageLight() = captureHomePage(dark = false)

    @Test fun homePageDark() = captureHomePage(dark = true)

    /** 首页整页：输入卡下方的小电视 */
    private fun captureHomePage(dark: Boolean) {
        compose.mainClock.autoAdvance = false
        val controller = HomeTipController(copy, HomeTipPicker(copy, FirstChoiceRandom), { normalDay }, null)
        compose.setContent {
            BiliToolsTheme(settings(dark)) {
                Box(Modifier.background(AppSurfaces.pageContainerColor)) {
                    HomeScreen(controller, loading = false, inputText = "")
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onRoot().saveEggShowcase("home/page-${if (dark) "dark" else "light"}")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun aprilFoolsTitle() {
        compose.mainClock.autoAdvance = false
        val fools = homeScenes.first { it.fools }
        val controller = HomeTipController(copy, HomeTipPicker(copy, FirstChoiceRandom), { fools.clock }, null)
        compose.setContent {
            BiliToolsTheme(settings(dark = false)) {
                Column(Modifier.background(AppSurfaces.pageContainerColor)) {
                    MainCollapsingTopBar(title = "BiliFools", state = rememberTopAppBarState())
                    HomeScreen(controller, loading = false, inputText = "")
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onRoot().saveEggShowcase("home/april-fools-page")
    }

    @Composable
    private fun HomeScreen(controller: HomeTipController, loading: Boolean, inputText: String) {
        ParseShowcase(
            state = ParseUiState(loading = loading, inputText = inputText),
            idleContent = {
                HomeTipBanner(controller = controller, loading = loading, active = true, onParseTarget = {})
            },
        )
    }

    /** 首页：点小电视换一句（弹幕式飘入 + 跳一下），再展示解析时收信号 */
    @Test
    fun homeAnimation() {
        compose.mainClock.autoAdvance = false
        var loading by mutableStateOf(false)
        val controller = HomeTipController(copy, HomeTipPicker(copy, kotlin.random.Random(3)), { normalDay }, null)
        compose.setContent {
            BiliToolsTheme(settings(dark = false)) {
                Box(Modifier.background(AppSurfaces.pageContainerColor)) {
                    HomeScreen(controller, loading = loading, inputText = "")
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val frames = FrameRecorder("anim/home-tap")
        frames.hold(6)
        compose.runOnIdle { controller.showNext() }
        frames.record(26)
        frames.hold(10)
        compose.runOnIdle { controller.showNext() }
        frames.record(26)
        frames.hold(10)
        compose.runOnIdle { controller.interceptParse("干杯") }
        frames.record(40)
        frames.hold(10)
        compose.runOnIdle { loading = true }
        frames.record(40)
        compose.runOnIdle { loading = false }
        frames.record(24)
        frames.hold(8)
    }

    // ---------------- 解析结果贴纸 ----------------

    private data class ResultScene(val name: String, val item: MediaItem)

    private fun videoItem(title: String, pubTime: Long, mid: Long = 7L, aid: Long = 123L, upperName: String = "某位 UP 主") = MediaItem(
        title = title,
        coverUrl = "",
        description = "",
        stat = MediaStat(play = 2_333_333, like = 66_666),
        url = "https://www.bilibili.com/video/BV1xx",
        duration = 233,
        pubTime = pubTime,
        type = MediaType.Video,
        upper = MediaUpper(name = upperName, mid = mid),
        isTarget = true,
        index = 0,
        aid = aid,
        bvid = "BV1xx411c7mD",
    )

    @Test fun resultStickersLight() = captureResults(dark = false)

    @Test fun resultStickersDark() = captureResults(dark = true)

    private fun captureResults(dark: Boolean) {
        val now = System.currentTimeMillis() / 1000
        val scenes = listOf(
            ResultScene("01-vintage", videoItem("一个 2012 年的老视频", pubTime = 1_340_000_000L)),
            ResultScene("02-fresh", videoItem("刚刚发布的新视频", pubTime = now - 600)),
            ResultScene("03-developer", videoItem("开发者本人的视频", pubTime = now - 86_400 * 30, mid = ResultEgg.DEVELOPER_MID, upperName = "肥宅水水呀")),
            ResultScene("04-founder", videoItem("站长的视频", pubTime = now - 86_400 * 30, mid = ResultEgg.FOUNDER_MID, upperName = "碧诗")),
            ResultScene("05-treasure", videoItem("【MV】保加利亚妖王AZIS视频合辑", pubTime = 1_320_850_533L, aid = ResultEgg.TREASURE_AID, upperName = "冰封.虾子")),
            ResultScene("06-first-upload", videoItem("字幕君交流场所", pubTime = 1_252_458_549L, aid = ResultEgg.FIRST_UPLOAD_AID, mid = ResultEgg.FOUNDER_MID, upperName = "碧诗")),
        )
        var scene by mutableStateOf(scenes.first())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BiliToolsTheme(settings(dark)) {
                val item = scene.item
                val info = MediaInfo(type = MediaType.Video, id = "BV1xx411c7mD", nfo = MediaNfo(showTitle = item.title), list = listOf(item))
                Box(Modifier.background(AppSurfaces.pageContainerColor)) {
                    ParseShowcase(
                        state = ParseUiState(mediaInfo = info, items = info.list, selectedItemIndex = 0, selectedItemIndices = listOf(0)),
                        resultEggText = { i, it -> ResultEgg.detect(i, it, now)?.text(copy.result) },
                    )
                }
            }
        }
        scenes.forEach { next ->
            compose.runOnIdle { scene = next }
            compose.mainClock.advanceTimeBy(2_000)
            compose.onRoot().saveEggShowcase("result/${if (dark) "dark" else "light"}/${next.name}")
        }
    }

    // ---------------- 关于页小电视 ----------------

    @Test fun aboutLight() = captureAbout(dark = false)

    @Test fun aboutDark() = captureAbout(dark = true)

    private fun setAbout(dark: Boolean, developerOptionsEnabled: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val themed = ContextThemeWrapper(LocalContext.current, R.style.Theme_BiliTools)
            CompositionLocalProvider(LocalContext provides themed) {
                BiliToolsTheme(settings(dark)) {
                    AboutSettingsScreen(
                        versionName = "3.2.0",
                        versionCode = 52,
                        developerOptionsEnabled = developerOptionsEnabled,
                        onEnableDeveloperOptions = {},
                        updateCheckState = UpdateCheckUiState.UpToDate,
                        onCheckUpdate = {},
                        onOpenSourceLicenses = {},
                        onFeedback = {},
                        onClearDiagnostics = {},
                        onBack = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
    }

    private val mascotNode get() = compose.onNodeWithContentDescription(context.getString(R.string.app_name))

    private fun poke(times: Int) = repeat(times) {
        mascotNode.performClick()
        compose.mainClock.advanceTimeBy(120)
    }

    private fun captureAbout(dark: Boolean) {
        val variant = if (dark) "dark" else "light"
        setAbout(dark)
        compose.onRoot().saveEggShowcase("about/$variant/00-page")
        poke(5)
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().saveEggShowcase("about/$variant/05-surprised")
        poke(3)
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().saveEggShowcase("about/$variant/08-annoyed")
        poke(4)
        compose.mainClock.advanceTimeBy(260)
        compose.onRoot().saveEggShowcase("about/$variant/12-spin")
        // 空翻约 0.9 秒，结束后接着戳仍算同一轮
        compose.mainClock.advanceTimeBy(700)
        poke(3)
        compose.mainClock.advanceTimeBy(900)
        compose.onRoot().saveEggShowcase("about/$variant/15-faint")
        // 晕倒 + 爬起约 4 秒
        compose.mainClock.advanceTimeBy(4_400)
        mascotNode.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_400)
        compose.onRoot().saveEggShowcase("about/$variant/hold-signal")
        mascotNode.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        // 往右拖：小电视要盖在右侧的应用名和按钮之上
        mascotNode.performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
            moveBy(Offset(200f, 10f))
            moveBy(Offset(200f, 10f))
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().saveEggShowcase("about/$variant/drag-right")
        mascotNode.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        // 往上拖：拉得再远也不会钻到标题栏底下
        mascotNode.performTouchInput {
            down(center)
            moveBy(Offset(20f, -60f))
            moveBy(Offset(30f, -300f))
            moveBy(Offset(30f, -300f))
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().saveEggShowcase("about/$variant/drag-up")
        mascotNode.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
    }

    /** 关于页的完整互动过程：连戳升级到晕倒、按住收信号、拖出去再弹回 */
    @Test
    fun aboutAnimation() {
        setAbout(dark = false)
        val frames = FrameRecorder("anim/about-pokes", crop = AboutHeaderCrop)
        frames.hold(6)
        // 1～4 下：普通弹跳
        repeat(4) {
            mascotNode.performClick()
            frames.record(4)
        }
        // 5：疑惑
        mascotNode.performClick()
        frames.record(10)
        repeat(2) {
            mascotNode.performClick()
            frames.record(4)
        }
        // 8：不耐烦
        mascotNode.performClick()
        frames.record(10)
        repeat(3) {
            mascotNode.performClick()
            frames.record(4)
        }
        // 12：空翻
        mascotNode.performClick()
        frames.record(26)
        repeat(2) {
            mascotNode.performClick()
            frames.record(4)
        }
        // 15：晕倒，躺一会儿再爬起来，这一轮到此结束
        mascotNode.performClick()
        frames.record(110, stepMillis = 40)
        frames.hold(6)

        val hold = FrameRecorder("anim/about-hold-drag", crop = AboutHeaderCrop)
        hold.hold(4)
        mascotNode.performTouchInput { down(center) }
        hold.record(50, stepMillis = 40)
        mascotNode.performTouchInput { up() }
        hold.record(20)
        hold.hold(6)
        mascotNode.performTouchInput { down(center) }
        val path = listOf(Offset(30f, 10f), Offset(60f, 20f), Offset(80f, 40f), Offset(90f, 60f), Offset(70f, 70f), Offset(40f, 60f))
        path.forEach { delta ->
            mascotNode.performTouchInput { moveBy(delta) }
            hold.record(2)
        }
        hold.record(6)
        mascotNode.performTouchInput { up() }
        hold.record(36)
        hold.hold(6)
    }

    // ---------------- 其他页面 ----------------

    @Test fun profileLight() = captureProfile(dark = false)

    @Test fun profileDark() = captureProfile(dark = true)

    private fun captureProfile(dark: Boolean) {
        val variant = if (dark) "dark" else "light"
        compose.mainClock.autoAdvance = false
        var user by mutableStateOf(sampleUser(6))
        compose.setContent {
            BiliToolsTheme(settings(dark)) {
                Box(Modifier.background(AppSurfaces.pageContainerColor).fillMaxSize()) {
                    BiliToolsMeContent(
                        loginState = LoginUiState(isLoggedIn = true, userInfo = user, currentMid = user.mid),
                        contentTopPadding = 16.dp,
                        onOpenLogin = {}, onOpenHistory = {}, onOpenFavorite = {}, onOpenWatchLater = {},
                        onOpenSettings = {}, onLogout = {},
                    )
                }
            }
        }
        listOf(6 to "level-6", 3 to "level-3", 0 to "level-0").forEach { (lv, label) ->
            compose.runOnIdle { user = sampleUser(lv) }
            compose.mainClock.advanceTimeBy(800)
            // 等级徽章是资料卡里第一个可点的元素（SVG 在测试环境不加载，没有内容描述可查）
            compose.onAllNodes(hasClickAction())[0].performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.onRoot().saveEggShowcase("profile/$variant/$label")
            compose.mainClock.advanceTimeBy(3_000)
        }
        compose.onNodeWithText("硬币", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().saveEggShowcase("profile/$variant/coins")
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("粉丝", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().saveEggShowcase("profile/$variant/followers")
    }

    private fun sampleUser(level: Int) = UserInfo(
        name = "肥宅水水呀",
        mid = 324042405,
        avatarUrl = null,
        level = level,
        sign = "做点好玩的小工具",
        vipLabel = null,
        coins = 233.0,
        following = 66,
        follower = 3_812,
        dynamic = 120,
    )

    @Test
    fun loginWaiting() {
        compose.mainClock.autoAdvance = false
        val qr = QrCodeGenerator.generate("https://account.bilibili.com/h5/account-h5/auth/scan-web?qrcode_key=showcase", 520)
        compose.setContent {
            val themed = ContextThemeWrapper(LocalContext.current, R.style.Theme_BiliTools)
            CompositionLocalProvider(LocalContext provides themed) {
                BiliToolsLoginContent(
                    settings = settings(dark = false),
                    state = LoginUiState(qrBitmap = qr, qrStatusRes = R.string.login_status_waiting, isPolling = true),
                    onExit = {}, onPrepareLogin = {}, onRefreshQr = {}, onTabChange = {},
                    onRequestPasswordLogin = { _, _ -> }, onRequestSmsCode = { _, _ -> },
                    onLoginWithSms = { _, _, _ -> }, onSetCountryId = {}, onDismissError = {},
                    onConfirmRiskVerification = {}, onDismissRiskVerification = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().saveEggShowcase("login/01-waiting")
        compose.mainClock.advanceTimeBy(121_000)
        compose.onRoot().saveEggShowcase("login/02-waited-too-long")
    }

    @Test
    fun confetti() {
        compose.mainClock.autoAdvance = false
        var trigger by mutableIntStateOf(0)
        compose.setContent {
            BiliToolsTheme(settings(dark = false)) {
                Box(Modifier.fillMaxSize().background(AppSurfaces.pageContainerColor)) {
                    ConfettiBurst(trigger = trigger, colors = com.happycola233.bilitools.ui.downloads.celebrationColors, modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { trigger = 1 }
        FrameRecorder("anim/confetti").record(64, stepMillis = 40)
    }

    // ---------------- 设置 ----------------

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h760dp-xhdpi")
    fun settingsPages() {
        compose.mainClock.autoAdvance = false
        var page by mutableIntStateOf(0)
        compose.setContent {
            val themed = ContextThemeWrapper(LocalContext.current, R.style.Theme_BiliTools)
            CompositionLocalProvider(LocalContext provides themed) {
                val settings = settings(dark = false).copy(developerOptionsEnabled = true)
                BiliToolsTheme(settings) {
                    when (page) {
                        0 -> com.happycola233.bilitools.ui.settings.GeneralSettingsScreen(
                            settings = settings,
                            liveUpdateSupported = true,
                            onLiveActivityStyleNotificationChange = {},
                            onLiveUpdateIconChange = {},
                            onHapticFeedbackLevelChange = {},
                            onLaunchSplashAnimationChange = {},
                            onHomeTipsChange = {},
                            onResetDismissedConfirmations = {},
                            selectedLanguage = com.happycola233.bilitools.core.AppLanguage.System,
                            onOpenLanguage = {},
                            onBack = {},
                        )
                        else -> com.happycola233.bilitools.ui.settings.DeveloperSettingsScreen(
                            settings = settings,
                            onDeveloperOptionsChange = {},
                            onGlassDebugChange = {},
                            onBack = {},
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().saveEggShowcase("settings/general")
        compose.runOnIdle { page = 1 }
        // 先把状态写入派发给重组，再推进时钟，否则截图会停在上一页
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().saveEggShowcase("settings/developer")
    }

    // ---------------- 工具 ----------------

    /** 每次都抽第一条，让效果图展示指定的那句话 */
    private object FirstChoiceRandom : kotlin.random.Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    @Composable
    private fun ParseShowcase(
        state: ParseUiState,
        idleContent: (@Composable () -> Unit)? = null,
        resultEggText: (MediaInfo, MediaItem?) -> String? = { _, _ -> null },
    ) {
        ParseScreenContent(
            state = state,
            conversionSettings = ParseConversionSettings(),
            inputText = state.inputText,
            contentTopPadding = 0.dp,
            externalMode = false,
            subtitleCopyPreviewEntries = null,
            aiSummaryCopyPreviewEntries = null,
            onInputChange = {}, onPaste = {}, onParse = {}, onMediaTypeChange = {}, onDownload = {},
            onSectionChange = {}, onOpenUpper = {}, onCopyResultContent = { _, _ -> },
            onSelectAllItems = {}, onClearSelectedItems = {}, onLoadPrevPage = {}, onLoadNextPage = {},
            onLoadPage = {}, onItemClick = {}, onItemSelectionChange = { _, _ -> },
            onFormatChange = {}, onOutputTypeChange = {}, onCollectionModeChange = {},
            onResolutionModeChange = {}, onResolutionChange = {}, onCodecChange = {},
            onAudioBitrateModeChange = {}, onAudioBitrateChange = {},
            onSubtitleEnabledChange = {}, onSubtitleLanguageChange = {},
            onEmbedSubtitlesEnabledChange = {}, onEmbedSubtitleSelectionChange = {},
            onEmbedLyricsEnabledChange = {}, onEmbedLyricsLanguageChange = {},
            onCopySubtitles = {}, onAiSummaryEnabledChange = {}, onCopyAiSummaries = {},
            onNfoCollectionEnabledChange = {}, onNfoSingleEnabledChange = {},
            onDanmakuLiveEnabledChange = {}, onDanmakuHistoryEnabledChange = {},
            onDanmakuDateChange = {}, onDanmakuHourChange = {}, onImageSelectionChange = { _, _ -> },
            onOpusContentEnabledChange = {}, onOpusImagesEnabledChange = {},
            onDismissSubtitleCopyPreview = {}, onDismissAiSummaryCopyPreview = {},
            onCopyCurrentSubtitle = {}, onCopyAllSubtitles = {}, onCopyCurrentAiSummary = {}, onCopyAllAiSummaries = {},
            onDismissError = {},
            idleContent = idleContent,
            resultEggText = resultEggText,
        )
    }

    /** 关于页只截卡片附近，GIF 小一些 */
    private val AboutHeaderCrop = FrameCrop(topDp = 64, heightDp = 300)

    internal data class FrameCrop(val topDp: Int, val heightDp: Int)

    /** 按固定步长推进时钟并逐帧截图，帧序号连续，交给脚本合成 GIF */
    private inner class FrameRecorder(private val name: String, private val crop: FrameCrop? = null) {
        private var index = 0

        fun record(count: Int, stepMillis: Long = 33) = repeat(count) {
            compose.mainClock.advanceTimeBy(stepMillis)
            capture()
        }

        /** 静止停留几帧，让 GIF 在关键姿势上稍作停顿 */
        fun hold(count: Int) = repeat(count) {
            compose.mainClock.advanceTimeBy(33)
            capture()
        }

        private fun capture() {
            val suffix = crop?.let { "@crop-${it.topDp}-${it.heightDp}" }.orEmpty()
            compose.onRoot().saveEggShowcase("$name$suffix/${"%04d".format(index++)}")
        }
    }
}
