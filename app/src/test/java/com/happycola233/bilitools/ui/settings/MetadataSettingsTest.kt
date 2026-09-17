package com.happycola233.bilitools.ui.settings

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.happycola233.bilitools.BiliToolsApp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.DownloadPreferenceGroup
import com.happycola233.bilitools.data.DownloadPreferenceMemorySettings
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MetadataSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun metadataOptionsFollowTheParentSwitchInLightTheme() = verifyMetadata(AppThemeMode.Light)
    @Test fun metadataOptionsFollowTheParentSwitchInDarkTheme() = verifyMetadata(AppThemeMode.Dark)

    @Test fun downloadSubpagesReturnToDownloadSettingsInLightTheme() = verifyDownloadEntryPoints(AppThemeMode.Light)
    @Test fun downloadSubpagesReturnToDownloadSettingsInDarkTheme() = verifyDownloadEntryPoints(AppThemeMode.Dark)

    @Test fun preferenceMemoryGroupsFollowTheMasterSwitchInLightTheme() = verifyPreferenceMemory(AppThemeMode.Light)
    @Test fun preferenceMemoryGroupsFollowTheMasterSwitchInDarkTheme() = verifyPreferenceMemory(AppThemeMode.Dark)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h720dp")
    fun preferenceMemoryRemainsUsableWithLargeTextOnNarrowScreen() = verifyPreferenceMemory(AppThemeMode.Light, fontScale = 1.6f)

    @Test
    fun preferenceMemorySummaryDescribesWhatIsKept() {
        val texts = mutableMapOf<String, String>()
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                texts["off"] = downloadPreferenceMemorySummary(DownloadPreferenceMemorySettings(enabled = false))
                texts["none"] = downloadPreferenceMemorySummary(DownloadPreferenceMemorySettings(groups = emptySet()))
                texts["all"] = downloadPreferenceMemorySummary(DownloadPreferenceMemorySettings(groups = DownloadPreferenceGroup.entries.toSet()))
                texts["default"] = downloadPreferenceMemorySummary(DownloadPreferenceMemorySettings())
                Text(texts.values.joinToString())
            }
        }
        compose.runOnIdle {
            assertEquals("每次解析都回到默认选项", texts["off"])
            assertEquals("已开启，但没有选择要保留的选项", texts["none"])
            assertEquals("保留解析页的全部下载选项", texts["all"])
            assertEquals("保留 6 类选项：内嵌字幕与歌词、字幕文件与 AI 总结、NFO 元数据、弹幕、图像、图文", texts["default"])
        }
    }

    @Test
    fun preferenceMemorySettingsSurviveRepositoryReload() {
        val context = RuntimeEnvironment.getApplication()
        val repository = SettingsRepository(context)
        val options = DownloadPreferenceMemorySettings(
            enabled = false,
            groups = setOf(DownloadPreferenceGroup.OutputType, DownloadPreferenceGroup.Images),
        )
        repository.setDownloadPreferenceMemory(options)
        assertEquals(options, SettingsRepository(context).currentSettings().downloadPreferenceMemory)
        assertFalse(options.remembers(DownloadPreferenceGroup.Images))
        assertTrue(options.copy(enabled = true).remembers(DownloadPreferenceGroup.Images))
        assertFalse(options.copy(enabled = true).remembers(DownloadPreferenceGroup.Danmaku))
    }

    private fun verifyMetadata(theme: AppThemeMode) {
        var settings by mutableStateOf(AppSettings(themeMode = theme))
        compose.setContent {
            BiliToolsTheme(settings) {
                MetadataSettingsScreen(settings, { settings = settings.copy(metadata = it) }, {})
            }
        }
        switchFor("嵌入封面").assertIsOn()
        capture("metadata-${theme.name.lowercase()}")
        switchFor("嵌入封面").performClick().assertIsOff()
        compose.runOnIdle { assertFalse(settings.metadata.embedCover) }
        switchFor("将合集作为专辑").performClick().assertIsOff()
        compose.runOnIdle { assertFalse(settings.metadata.useCollectionAsAlbum) }
        compose.runOnIdle { settings = settings.copy(addMetadata = false) }
        compose.onNodeWithText("元数据已关闭，可在“下载”设置中开启。").assertExists()
        switchFor("嵌入封面").assertIsNotEnabled()
        switchFor("用 UP 主补充艺术家").assertIsNotEnabled().assertIsOn()
        capture("metadata-disabled-${theme.name.lowercase()}")
    }

    private fun verifyDownloadEntryPoints(theme: AppThemeMode) {
        val container = (RuntimeEnvironment.getApplication() as BiliToolsApp).container
        val viewModel = SettingsViewModel(container.settingsRepository, container.issueReportRepository)
        viewModel.navigateTo(SettingsDestination.Download)
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = theme)) {
                DownloadSettingsScreen(
                    settings = AppSettings(themeMode = theme),
                    onOpenDownloadLocationPicker = {},
                    onOpenDefaultDownloadQuality = { viewModel.navigateTo(SettingsDestination.DefaultDownloadQuality) },
                    onOpenDownloadPreferenceMemory = { viewModel.navigateTo(SettingsDestination.DownloadPreferenceMemory) },
                    onAddMetadataChange = {},
                    onOpenMetadataOptions = { viewModel.navigateTo(SettingsDestination.Metadata) },
                    onConvertXmlDanmakuToAssChange = {},
                    onConvertAudioToMp3Change = {},
                    onConvertVideoToMp4Change = {},
                    onMaxConcurrentDownloadsChange = {},
                    onConfirmCellularChange = {},
                    onHideInAlbumChange = {},
                    onBack = viewModel::popDestination,
                )
            }
        }
        capture("download-${theme.name.lowercase()}")
        fun open(label: String, destination: SettingsDestination) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(
                    listOf(SettingsDestination.Main, SettingsDestination.Download, destination),
                    viewModel.backStack.toList(),
                )
                viewModel.popDestination()
                assertEquals(SettingsDestination.Download, viewModel.backStack.last())
            }
        }
        open("默认下载质量", SettingsDestination.DefaultDownloadQuality)
        open("记忆下载偏好", SettingsDestination.DownloadPreferenceMemory)
        open("元数据选项", SettingsDestination.Metadata)
        // 入口行直接说明当前保留了哪些选项。
        compose.onNodeWithText("保留 6 类选项", substring = true).assertExists()
    }

    private fun verifyPreferenceMemory(theme: AppThemeMode, fontScale: Float = 1f) {
        var settings by mutableStateOf(DownloadPreferenceMemorySettings())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BiliToolsTheme(AppSettings(themeMode = theme)) {
                    DownloadPreferenceMemoryScreen(settings, { settings = it }, {})
                }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        switchFor("记忆下载偏好").assertIsOn()
        switchFor("输出类型").assertIsEnabled().assertIsOff()
        list.performScrollToNode(hasText("内嵌字幕与歌词"))
        switchFor("内嵌字幕与歌词").assertIsOn()
        capture("memory-${theme.name.lowercase()}-$fontScale")

        list.performScrollToNode(hasText("输出类型"))
        switchFor("输出类型").performClick().assertIsOn()
        compose.runOnIdle { assertTrue(DownloadPreferenceGroup.OutputType in settings.groups) }
        list.performScrollToNode(hasText("图文"))
        switchFor("图文").performClick().assertIsOff()
        compose.runOnIdle { assertFalse(DownloadPreferenceGroup.Opus in settings.groups) }

        // 总开关关闭：分组全部变灰但保留原值，并说明整页不再生效。
        list.performScrollToNode(hasText("记忆下载偏好"))
        switchFor("记忆下载偏好").performClick().assertIsOff()
        compose.runOnIdle {
            assertFalse(settings.enabled)
            assertTrue(DownloadPreferenceGroup.OutputType in settings.groups)
        }
        compose.onNodeWithText("已关闭，解析页的所有下载选项每次都会回到默认值。").assertExists()
        switchFor("输出类型").assertIsNotEnabled().assertIsOn()
        list.performScrollToNode(hasText("弹幕"))
        switchFor("弹幕").assertIsNotEnabled().assertIsOn()
        capture("memory-disabled-${theme.name.lowercase()}-$fontScale")
    }

    /** 设置行本身不合并语义，开关是标题的兄弟节点；顶栏标题没有开关，借此与同名的行标题区分。 */
    private fun switchFor(title: String): SemanticsNodeInteraction {
        compose.onAllNodesWithText(title, useUnmergedTree = true)
            .filterToOne(hasAnySibling(isToggleable()))
            .performScrollTo()
        return compose.onAllNodesWithText(title, useUnmergedTree = true)
            .filterToOne(hasAnySibling(isToggleable()))
            .onParent().onChildren().filterToOne(isToggleable())
    }

    private fun capture(name: String) {
        val output = File("../.tmp/metadata-ui-refine/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
