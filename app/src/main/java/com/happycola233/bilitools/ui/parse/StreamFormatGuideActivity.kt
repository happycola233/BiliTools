package com.happycola233.bilitools.ui.parse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.ui.applySettingsThemeOverlays
import com.happycola233.bilitools.ui.enableBiliEdgeToEdge
import com.happycola233.bilitools.ui.theme.BiliToolsSettingsTheme

class StreamFormatGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableBiliEdgeToEdge()
        applySettingsThemeOverlays()
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        setContentView(composeView)

        composeView.setContent {
            val settingsRepository = remember {
                applicationContext.appContainer.settingsRepository
            }
            val settings by settingsRepository.settings.collectAsState()

            BiliToolsSettingsTheme(settings = settings) {
                StreamFormatGuideScreen(onBack = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreamFormatGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = GuideDefaults.pageContainerColor,
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stream_format_guide_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                subtitle = { Text(stringResource(R.string.stream_format_guide_subtitle)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = GuideDefaults.sectionContainerColor,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24),
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                colors = GuideDefaults.topBarColors,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.stream_format_guide_intro),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                StreamFormatSection(
                    title = stringResource(R.string.format_dash),
                    status = stringResource(R.string.stream_format_guide_dash_status),
                    accent = MaterialTheme.colorScheme.primary,
                    accentContainer = MaterialTheme.colorScheme.primaryContainer,
                    onAccentContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                    summary = stringResource(R.string.stream_format_guide_dash_notice),
                    details = listOf(
                        stringResource(R.string.stream_format_guide_dash_default),
                        stringResource(R.string.stream_format_guide_dash_body),
                        stringResource(R.string.stream_format_guide_dash_merge),
                    ),
                )
            }
            item {
                StreamFormatSection(
                    title = stringResource(R.string.format_mp4),
                    status = stringResource(R.string.stream_format_guide_mp4_status),
                    accent = MaterialTheme.colorScheme.tertiary,
                    accentContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    onAccentContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                    summary = stringResource(R.string.stream_format_guide_mp4_notice),
                    details = listOf(
                        stringResource(R.string.stream_format_guide_mp4_body),
                        stringResource(R.string.stream_format_guide_mp4_exception),
                    ),
                )
            }
            item {
                StreamFormatSection(
                    title = stringResource(R.string.format_flv),
                    status = stringResource(R.string.stream_format_guide_flv_status),
                    accent = MaterialTheme.colorScheme.error,
                    accentContainer = MaterialTheme.colorScheme.errorContainer,
                    onAccentContainer = MaterialTheme.colorScheme.onErrorContainer,
                    summary = stringResource(R.string.stream_format_guide_flv_notice),
                    details = listOf(
                        stringResource(R.string.stream_format_guide_flv_body),
                    ),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.stream_format_guide_footer),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 单个格式分区：标题前一条彩色标记条区分格式，右侧是状态徽章；
 * 正文分「一句话结论」与「补充说明」两级，避免整块彩色底色抢视线。
 */
@Composable
private fun StreamFormatSection(
    title: String,
    status: String,
    accent: Color,
    accentContainer: Color,
    onAccentContainer: Color,
    summary: String,
    details: List<String>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GuideDefaults.sectionContainerColor,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 18.dp)
                        .background(accent, RoundedCornerShape(percent = 50)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                Surface(
                    color = accentContainer,
                    contentColor = onAccentContainer,
                    shape = RoundedCornerShape(percent = 50),
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                text = summary,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                details.forEach { detail ->
                    Text(
                        text = detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private object GuideDefaults {
    val pageContainerColor: Color
        @Composable
        get() = if (!usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    val sectionContainerColor: Color
        @Composable
        get() = if (!usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceBright
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val topBarColors: TopAppBarColors
        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        get() = TopAppBarDefaults.topAppBarColors(
            containerColor = pageContainerColor,
            scrolledContainerColor = pageContainerColor,
        )

    @Composable
    private fun usesPureBlackSurfaces(): Boolean {
        val colorScheme = MaterialTheme.colorScheme
        return colorScheme.surface == Color.Black && colorScheme.background == Color.Black
    }
}
