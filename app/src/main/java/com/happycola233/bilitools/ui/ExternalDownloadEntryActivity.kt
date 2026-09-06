package com.happycola233.bilitools.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
 * 任务成功加入下载队列后播放退场动画并关闭，回到来源应用继续操作。
 */
class ExternalDownloadEntryActivity : AppCompatActivity() {
    private val viewModel: ParseViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySettingsThemeOverlays()
        super.onCreate(savedInstanceState)
        // 关闭本入口的窗口级动画，避免与 Compose 面板动画叠加；跨任务转场仍由系统决定。
        window.setWindowAnimations(R.style.Animation_BiliTools_ExternalDownload)

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
                ExternalDownloadEntryPanel(onDismissed = ::finish) { onDismissRequest ->
                    ParseRoute(
                        viewModel = viewModel,
                        externalMode = true,
                        // 下载已交给后台任务，沿用面板的退场动画后回到来源应用。
                        onExternalDownloadQueued = onDismissRequest,
                    )
                }
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
internal fun ExternalDownloadEntryPanel(
    onDismissed: () -> Unit,
    content: @Composable (onDismissRequest: () -> Unit) -> Unit,
) {
    var dismissRequested by rememberSaveable { mutableStateOf(false) }
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val keyboardController = LocalSoftwareKeyboardController.current
    val onDismissRequest: () -> Unit = {
        keyboardController?.hide()
        dismissRequested = true
    }
    val motionScheme = MaterialTheme.motionScheme
    val enterSpec = motionScheme.defaultSpatialSpec<Float>()
    val exitSpec = motionScheme.fastEffectsSpec<Float>()
    val visibilityProgress = remember { Animatable(0f) }
    val enterOffsetPx = with(LocalDensity.current) { 24.dp.toPx() }

    // 退场期间继续接管返回事件，连续点击返回也不会提前结束 Activity、截断动画。
    BackHandler(onBack = onDismissRequest)
    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            visibilityProgress.animateTo(0f, exitSpec)
            currentOnDismissed()
        } else {
            visibilityProgress.animateTo(1f, enterSpec)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color(0x1F000000), alpha = visibilityProgress.value.coerceIn(0f, 1f))
                }
                .pointerInput(onDismissRequest) {
                    detectTapGestures { onDismissRequest() }
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
                // 动画只更新绘制层，不逐帧重组、测量解析页；遮罩与面板共用一个进度。
                .graphicsLayer {
                    alpha = visibilityProgress.value.coerceIn(0f, 1f)
                    translationY = enterOffsetPx * (1f - visibilityProgress.value)
                }
                // 消费面板空白处的点击，避免穿透到底层遮罩并关闭入口。
                .pointerInput(Unit) { detectTapGestures {} },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 给圆角和标题留一点呼吸空间，使用固定间距而非整份状态栏高度。
                    .padding(top = 12.dp),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.parse_section_options),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back_24),
                                contentDescription = stringResource(R.string.download_cancel),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    // 浮窗外层已预留顶部间距，面板内的标题栏无需再次添加系统栏留白。
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
                Box(modifier = Modifier.weight(1f)) {
                    content(onDismissRequest)
                }
            }
        }
    }
}
