package com.happycola233.bilitools.ui.history

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.assertMovesInOneMotion
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 视口足够高，保证筛选面板完整位于屏幕内。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h1200dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryFilterSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customRangePanelMovesFiltersInOneMotionInLightTheme() = verifyCustomRangePanel(AppThemeMode.Light)

    @Test fun customRangePanelMovesFiltersInOneMotionInDarkTheme() = verifyCustomRangePanel(AppThemeMode.Dark)

    /** 自定义日期范围面板展开、收起时，与上方「时间」分区的间距一起动画，下方「设备」分区一次平滑到位。 */
    private fun verifyCustomRangePanel(theme: AppThemeMode) {
        compose.setContent {
            BiliToolsHistoryContent(
                settings = AppSettings(themeMode = theme),
                state = HistoryUiState(),
                onBack = {}, onRefresh = {}, onSelectBusiness = {}, onGoToPage = {}, onGoToPrevPage = {},
                onGoToNextPage = {}, onVisiblePageChange = {}, onScrollHandled = {}, onLoadNextPage = {},
                onLoadPrevPage = {}, onApplyFilter = {}, onDownload = {}, onOpenAuthor = {}, onCopyTitle = {},
                onCopyAuthorName = {},
            )
        }
        compose.onNodeWithContentDescription(MORE_FILTERS).performClick()
        compose.onNodeWithText(TIME_CUSTOM).performScrollTo()
        capture("history-filter-time-all-${theme.name.lowercase()}")

        // 底部弹窗贴底摆放：内容变高时整个弹窗上移、下方内容在屏幕上不动，因此量两个分区标题在弹窗内的距离。
        val sectionDistance = {
            val timeTitleTop = compose.onNodeWithText(TIME_TITLE).fetchSemanticsNode().boundsInRoot.top
            compose.onNodeWithText(DEVICE_TITLE).fetchSemanticsNode().boundsInRoot.top - timeTitleTop
        }
        compose.assertMovesInOneMotion(
            "选择自定义时间",
            { compose.onNodeWithText(TIME_CUSTOM).performClick() },
            sectionDistance,
        )
        capture("history-filter-time-custom-${theme.name.lowercase()}")

        compose.assertMovesInOneMotion(
            "改选今天",
            { compose.onNodeWithText(TIME_TODAY).performClick() },
            sectionDistance,
        )
    }

    private fun capture(name: String) {
        val output = File("../.tmp/layout-motion/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val MORE_FILTERS = "更多筛选"
        const val TIME_TITLE = "时间"
        const val TIME_TODAY = "今天"
        const val TIME_CUSTOM = "自定义"
        const val DEVICE_TITLE = "设备"
    }
}
