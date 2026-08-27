package com.happycola233.bilitools.ui.history

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.model.HistoryItem
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.ExternalDownloadContract
import com.happycola233.bilitools.ui.MainActivity
import com.happycola233.bilitools.ui.applySettingsThemeOverlays
import com.happycola233.bilitools.ui.copyTextWithFeedback
import com.happycola233.bilitools.ui.enableBiliEdgeToEdge

class HistoryActivity : AppCompatActivity() {
    private val viewModel: HistoryViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableBiliEdgeToEdge()
        applySettingsThemeOverlays()
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        setContentView(composeView)

        composeView.setContent {
            val settingsRepository = remember { applicationContext.appContainer.settingsRepository }
            val settings by settingsRepository.settings.collectAsState()
            val state by viewModel.state.collectAsState()

            BiliToolsHistoryContent(
                settings = settings,
                state = state,
                onBack = ::finish,
                onRefresh = viewModel::refresh,
                onSelectBusiness = viewModel::selectBusiness,
                onGoToPage = viewModel::goToPage,
                onGoToPrevPage = viewModel::goToPrevPage,
                onGoToNextPage = viewModel::goToNextPage,
                onApplyFilter = viewModel::applyFilter,
                onDownload = ::jumpToParse,
                onOpenAuthor = ::openAuthorSpace,
                onCopyTitle = ::copyTitle,
                onCopyAuthorName = ::copyAuthorName,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshLoginState()
    }

    private fun jumpToParse(item: HistoryItem) {
        val url = item.toParseUrl() ?: return
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(ExternalDownloadContract.EXTRA_URL, url)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun openAuthorSpace(item: HistoryItem) {
        val mid = item.authorMid ?: return
        openUrl("https://space.bilibili.com/$mid")
    }

    private fun copyTitle(title: String) {
        copyTextWithFeedback(
            content = title,
            clipLabelRes = R.string.common_title_clip_label,
            feedbackRes = R.string.common_title_copied,
        )
    }

    private fun copyAuthorName(authorName: String) {
        copyTextWithFeedback(
            content = authorName,
            clipLabelRes = R.string.common_upper_name_clip_label,
            feedbackRes = R.string.common_upper_name_copied,
        )
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, getString(R.string.common_open_link_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
