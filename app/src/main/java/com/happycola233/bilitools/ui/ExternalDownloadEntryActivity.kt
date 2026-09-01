package com.happycola233.bilitools.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.ui.parse.ParseRoute
import com.happycola233.bilitools.ui.parse.ParseViewModel
import com.happycola233.bilitools.ui.theme.BiliToolsTheme

/**
 * 供其他应用分享或打开 URL 的公开入口。
 *
 * Activity 使用透明对话框主题，让用户在不完整跳入主界面的情况下完成解析与下载；
 * 任务成功加入下载队列后立即关闭，回到来源应用继续操作。
 */
class ExternalDownloadEntryActivity : AppCompatActivity() {
    private val viewModel: ParseViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySettingsThemeOverlays()
        super.onCreate(savedInstanceState)

        val initialUrl = intent.extractExternalDownloadUrl()
        if (initialUrl == null) {
            finish()
            return
        }
        // 普通配置变更沿用 Activity ViewModel；进程恢复时 ViewModel 为空，重新消费原始 intent。
        if (viewModel.state.value.inputText.isBlank()) {
            viewModel.submitExternalUrl(initialUrl)
        }

        setContent {
            val settings by applicationContext.appContainer.settingsRepository.settings.collectAsState()
            BiliToolsTheme(settings = settings) {
                ExternalDownloadEntryContent(
                    viewModel = viewModel,
                    onDismiss = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchExternalUrl(intent)
    }

    private fun dispatchExternalUrl(sourceIntent: Intent) {
        val url = sourceIntent.extractExternalDownloadUrl()
        if (url == null) {
            finish()
            return
        }
        viewModel.submitExternalUrl(url)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalDownloadEntryContent(
    viewModel: ParseViewModel,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x1F000000))
                .pointerInput(onDismiss) {
                    detectTapGestures { onDismiss() }
                },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .padding(
                    start = 12.dp,
                    top = 44.dp,
                    end = 12.dp,
                    bottom = 16.dp,
                )
                // 消费面板空白处的点击，避免穿透到底层遮罩并关闭入口。
                .pointerInput(Unit) { detectTapGestures {} },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.parse_section_options),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back_24),
                                contentDescription = stringResource(R.string.download_cancel),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
                Box(modifier = Modifier.weight(1f)) {
                    ParseRoute(
                        viewModel = viewModel,
                        externalMode = true,
                        // 下载已经交给后台任务，关闭临时入口即可回到来源应用。
                        onExternalDownloadQueued = onDismiss,
                    )
                }
            }
        }
    }
}
