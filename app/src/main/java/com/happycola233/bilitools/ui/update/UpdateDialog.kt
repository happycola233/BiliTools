package com.happycola233.bilitools.ui.update

import com.happycola233.bilitools.core.AppLog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.ReleaseInfo
import com.happycola233.bilitools.ui.markdown.GithubMarkdownParser
import com.happycola233.bilitools.ui.markdown.MarkdownContent
import com.happycola233.bilitools.ui.markdown.MarkdownDocument
import com.happycola233.bilitools.ui.theme.AppAccents
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import com.happycola233.bilitools.update.UpdateStartResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object UpdateDialog {
    private const val HOST_VIEW_TAG = "biltools_update_dialog_host"

    fun show(
        activity: AppCompatActivity,
        release: ReleaseInfo,
        currentVersion: String,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val settingsRepository = activity.applicationContext.appContainer.settingsRepository
        val container = activity.findViewById<ViewGroup>(android.R.id.content)
        container.findViewWithTag<ComposeView>(HOST_VIEW_TAG)?.let(container::removeView)

        val composeView = ComposeView(activity).apply {
            tag = HOST_VIEW_TAG
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
        container.addView(composeView)

        composeView.setContent {
            val settings by settingsRepository.settings.collectAsState()
            BiliToolsTheme(settings = settings) {
                UpdateDialogContent(
                    activity = activity,
                    release = release,
                    currentVersion = currentVersion,
                    onDismiss = {
                        if (composeView.parent === container) {
                            container.removeView(composeView)
                        }
                    },
                )
            }
        }
    }
}

/** 可直接放进 Activity 现有 Compose 根的更新弹窗内容。 */
@Composable
fun UpdateDialogContent(
    activity: AppCompatActivity,
    release: ReleaseInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    val appUpdateManager = remember(activity) {
        activity.applicationContext.appContainer.appUpdateManager
    }
    val gitHubRouteManager = remember(activity) {
        activity.applicationContext.appContainer.gitHubRouteManager
    }
    val imageUrlCandidates = remember(gitHubRouteManager) {
        gitHubRouteManager::releaseNotesMediaCandidates
    }
    UpdateDialogHost(
        activity = activity,
        release = release,
        currentVersion = currentVersion,
        imageUrlCandidates = imageUrlCandidates,
        onRemoveHost = onDismiss,
        onOpenRelease = {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(release.htmlUrl),
            )
            runCatching {
                activity.startActivity(intent)
            }.onFailure {
                AppLog.w("UpdateDialog", "[open-release] failed", it)
                Toast.makeText(
                    activity,
                    activity.getString(R.string.update_dialog_open_release_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onDownloadUpdateNow = {
            startUpdateDownload(
                activity = activity,
                release = release,
                appUpdateManager = appUpdateManager,
            )
        },
        onIgnoreUpdate = {
            appUpdateManager.ignoreRelease(release.versionName)
            Toast.makeText(
                activity,
                activity.getString(
                    R.string.update_dialog_ignore_saved,
                    buildVersionTag(release.versionName),
                ),
                Toast.LENGTH_SHORT,
            ).show()
        },
    )
}

private fun shouldRequestNotificationPermission(activity: AppCompatActivity): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    ) != PackageManager.PERMISSION_GRANTED
}

private fun startUpdateDownload(
    activity: AppCompatActivity,
    release: ReleaseInfo,
    appUpdateManager: com.happycola233.bilitools.update.AppUpdateManager,
): Boolean {
    return when (val result = appUpdateManager.startDownload(release)) {
        UpdateStartResult.Started -> {
            Toast.makeText(
                activity,
                activity.getString(R.string.update_dialog_download_started),
                Toast.LENGTH_SHORT,
            ).show()
            true
        }

        UpdateStartResult.AlreadyRunning -> {
            Toast.makeText(
                activity,
                activity.getString(R.string.update_dialog_download_running),
                Toast.LENGTH_SHORT,
            ).show()
            true
        }

        UpdateStartResult.MissingAsset -> {
            Toast.makeText(
                activity,
                activity.getString(R.string.update_dialog_apk_missing),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }

        is UpdateStartResult.Failed -> {
            Toast.makeText(
                activity,
                activity.getString(
                    R.string.update_dialog_download_failed,
                    result.errorMessage,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateDialogHost(
    activity: AppCompatActivity,
    release: ReleaseInfo,
    currentVersion: String,
    imageUrlCandidates: (String) -> List<String>,
    onRemoveHost: () -> Unit,
    onOpenRelease: () -> Unit,
    onDownloadUpdateNow: () -> Boolean,
    onIgnoreUpdate: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(true) }
    // 说明较长时面板直接全高展开，底部操作栏始终可见；不提供半展开态，避免操作区被推出屏幕。
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val coroutineScope = rememberCoroutineScope()
    // 首次解析会加载 HTML 实体表等，放到后台线程，完成后再弹出面板，避免展开动画中途改变高度。
    val releaseNotes by produceState<MarkdownDocument?>(initialValue = null, release) {
        value = withContext(Dispatchers.Default) {
            GithubMarkdownParser.parse(release.bodyMarkdown, pageUrl = release.htmlUrl)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                activity,
                activity.getString(R.string.notification_permission_denied_tip),
                Toast.LENGTH_SHORT,
            ).show()
        }
        if (onDownloadUpdateNow()) {
            coroutineScope.launch {
                sheetState.hide()
                isVisible = false
            }
        }
    }

    fun dismissSheet() {
        coroutineScope.launch {
            sheetState.hide()
            isVisible = false
        }
    }

    if (!isVisible && !sheetState.isVisible) {
        LaunchedEffect(Unit) {
            onRemoveHost()
        }
        return
    }
    val notes = releaseNotes ?: return

    UpdateBottomSheet(
        sheetState = sheetState,
        release = release,
        releaseNotes = notes,
        currentVersion = currentVersion,
        imageUrlCandidates = imageUrlCandidates,
        onDismiss = { isVisible = false },
        onOpenRelease = onOpenRelease,
        onDownloadUpdate = {
            if (shouldRequestNotificationPermission(activity)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (onDownloadUpdateNow()) {
                dismissSheet()
            }
        },
        onIgnoreUpdate = {
            onIgnoreUpdate()
            dismissSheet()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateBottomSheet(
    sheetState: SheetState,
    release: ReleaseInfo,
    releaseNotes: MarkdownDocument,
    currentVersion: String,
    imageUrlCandidates: (String) -> List<String>,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onIgnoreUpdate: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurfaces.pageContainerColor,
    ) {
        UpdateSheetHeader(
            targetVersion = release.tagName,
            currentVersion = currentVersion,
            publishedAt = release.publishedAt,
        )
        // 只有说明区域滚动，标题与操作区留在原位：长说明读到哪里都能直接下载。
        ReleaseNotesCard(
            document = releaseNotes,
            imageUrlCandidates = imageUrlCandidates,
            // 面板展开或拖动收起的动画期间暂停内部滚动，避免手势同时驱动面板与说明区域。
            scrollEnabled = !sheetState.isAnimationRunning,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 16.dp),
        )
        UpdateActions(
            canDownload = release.apkAsset != null,
            onDownloadUpdate = onDownloadUpdate,
            onIgnoreUpdate = onIgnoreUpdate,
            onOpenRelease = onOpenRelease,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSheetHeader(
    targetVersion: String,
    currentVersion: String,
    publishedAt: Instant?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_update_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = stringResource(R.string.update_dialog_title),
            // 比说明里的一级标题（headlineSmall）高一档，避免两个大标题并列争抢视觉焦点。
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 16.dp)
                .semantics { heading() },
        )
        VersionTransition(
            currentVersion = buildVersionTag(currentVersion),
            targetVersion = targetVersion,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (publishedAt != null) {
            Text(
                text = stringResource(R.string.update_dialog_published_on, formatReleaseDate(publishedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 按应用当前语言和本机时区显示发布日期（GitHub 返回的是 UTC 时间）。 */
@Composable
private fun formatReleaseDate(publishedAt: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(publishedAt, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(publishedAt.atZone(ZoneId.systemDefault()))
    }
}

@Composable
private fun VersionTransition(
    currentVersion: String,
    targetVersion: String,
    modifier: Modifier = Modifier,
) {
    // 整行合并为一次朗读（“当前版本 新版本”），箭头只是视觉连接，不单独朗读。
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = currentVersion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "←" else "→",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = targetVersion,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ReleaseNotesCard(
    document: MarkdownDocument,
    imageUrlCandidates: (String) -> List<String>,
    scrollEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = AppSurfaces.cardContainerColor),
    ) {
        if (document.blocks.isEmpty()) {
            Text(
                text = stringResource(R.string.update_dialog_notes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        } else {
            MarkdownContent(
                document = document,
                imageUrlCandidates = imageUrlCandidates,
                modifier = Modifier
                    .verticalScroll(rememberScrollState(), enabled = scrollEnabled)
                    .padding(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateActions(
    canDownload: Boolean,
    onDownloadUpdate: () -> Unit,
    onIgnoreUpdate: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val buttonHeight = ButtonDefaults.MediumContainerHeight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onDownloadUpdate,
            enabled = canDownload,
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            colors = AppAccents.filledButtonColors(),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = buttonHeight),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download_for_offline_24),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
            )
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
            Text(
                text = stringResource(R.string.update_dialog_download),
                style = ButtonDefaults.textStyleFor(buttonHeight),
            )
        }
        if (!canDownload) {
            Text(
                text = stringResource(R.string.update_dialog_apk_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        // 两个次要操作在较长的译文下自动换行，不会互相挤压。
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            TextButton(onClick = onIgnoreUpdate) {
                Text(stringResource(R.string.update_dialog_ignore_once))
            }
            TextButton(onClick = onOpenRelease) {
                Text(stringResource(R.string.update_dialog_open_release))
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_open_in_new_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun buildVersionTag(version: String): String {
    val normalizedVersion = version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .ifBlank { "0" }
    return "v$normalizedVersion"
}
