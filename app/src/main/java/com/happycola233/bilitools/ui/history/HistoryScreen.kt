package com.happycola233.bilitools.ui.history

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.model.HistoryItem
import com.happycola233.bilitools.data.model.HistoryTab
import com.happycola233.bilitools.ui.theme.BiliToolsSettingsTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class HistoryToggleOption<T>(
    val value: T,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiliToolsHistoryContent(
    settings: AppSettings,
    state: HistoryUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectBusiness: (String) -> Unit,
    onGoToPage: (Int) -> Unit,
    onGoToPrevPage: () -> Unit,
    onGoToNextPage: () -> Unit,
    onApplyFilter: (HistoryFilter) -> Unit,
    onDownload: (HistoryItem) -> Unit,
    onOpenAuthor: (HistoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    BiliToolsSettingsTheme(settings = settings) {
        val context = LocalContext.current
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        var pageInput by rememberSaveable { mutableStateOf(state.page.toString()) }
        var pageInputFocused by remember { mutableStateOf(false) }
        var filterSheetVisible by rememberSaveable { mutableStateOf(false) }
        var draftFilter by remember { mutableStateOf(state.filter) }
        var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
        var lastToastError by rememberSaveable { mutableStateOf<String?>(null) }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(state.page, pageInputFocused) {
            if (!pageInputFocused) {
                pageInput = state.page.toString()
            }
        }

        LaunchedEffect(state.errorText, state.items.size) {
            if (!state.errorText.isNullOrBlank() && state.items.isNotEmpty()) {
                if (state.errorText != lastToastError) {
                    Toast.makeText(context, state.errorText, Toast.LENGTH_SHORT).show()
                    lastToastError = state.errorText
                }
            } else if (state.errorText.isNullOrBlank()) {
                lastToastError = null
            }
        }

        fun submitPageInput(clearFocusAfterSubmit: Boolean) {
            val maxPage = state.totalPages.takeIf { it > 0 } ?: Int.MAX_VALUE
            val targetPage = pageInput.trim().toIntOrNull()?.coerceIn(1, maxPage)
            if (targetPage != null) {
                onGoToPage(targetPage)
            } else {
                pageInput = state.page.toString()
            }
            if (clearFocusAfterSubmit) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HistoryExpressiveDefaults.pageBackgroundBrush)
                .then(modifier),
        ) {
            Scaffold(
                topBar = {
                    Surface(color = HistoryExpressiveDefaults.pageContainerColor) {
                        Column {
                            LargeFlexibleTopAppBar(
                                title = {
                                    Text(
                                        text = stringResource(R.string.history_title),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                                subtitle = {
                                    Text(
                                        text = if (state.total > 0) {
                                            stringResource(
                                                R.string.history_page_status_with_pages_and_total,
                                                state.page,
                                                state.totalPages,
                                                state.total,
                                            )
                                        } else {
                                            stringResource(R.string.history_entry_desc)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                navigationIcon = {
                                    FilledTonalIconButton(
                                        onClick = onBack,
                                        shapes = IconButtonDefaults.shapes(),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = HistoryExpressiveDefaults.toolbarActionColor,
                                        ),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_arrow_back_24),
                                            contentDescription = stringResource(R.string.settings_back),
                                        )
                                    }
                                },
                                actions = {
                                    FilledTonalIconButton(
                                        onClick = onRefresh,
                                        enabled = !state.loading,
                                        shapes = IconButtonDefaults.shapes(),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = HistoryExpressiveDefaults.toolbarActionColor,
                                        ),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_refresh_24),
                                            contentDescription = stringResource(R.string.history_refresh),
                                        )
                                    }
                                    FilledTonalIconButton(
                                        onClick = {
                                            draftFilter = state.filter
                                            filterSheetVisible = true
                                        },
                                        enabled = !state.loading,
                                        shapes = IconButtonDefaults.shapes(),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = HistoryExpressiveDefaults.toolbarActionColor,
                                        ),
                                        modifier = Modifier.padding(end = 4.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_tune_24),
                                            contentDescription = stringResource(R.string.history_more_filters),
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = HistoryExpressiveDefaults.pageContainerColor,
                                    scrolledContainerColor = HistoryExpressiveDefaults.pageContainerColor,
                                ),
                                collapsedHeight = TopAppBarDefaults.TopAppBarExpandedHeight,
                                expandedHeight = 108.dp,
                                scrollBehavior = scrollBehavior,
                            )

                            AnimatedVisibility(visible = state.tabs.isNotEmpty()) {
                                Column {
                                    HistoryBusinessTabs(
                                        tabs = state.tabs,
                                        selectedBusiness = state.selectedBusiness,
                                        onSelectBusiness = onSelectBusiness,
                                    )

                                    HistoryPagerCard(
                                        page = state.page,
                                        totalPages = state.totalPages,
                                        hasMore = state.hasMore,
                                        loading = state.loading,
                                        pageInput = pageInput,
                                        onPageInputChange = { input ->
                                            pageInput = input.filter(Char::isDigit)
                                        },
                                        onPageInputFocusChange = { focused ->
                                            if (pageInputFocused && !focused) {
                                                submitPageInput(clearFocusAfterSubmit = false)
                                            }
                                            pageInputFocused = focused
                                        },
                                        onSubmitPage = {
                                            submitPageInput(clearFocusAfterSubmit = true)
                                        },
                                        onPrev = onGoToPrevPage,
                                        onNext = onGoToNextPage,
                                        modifier = Modifier.padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = 10.dp,
                                        ),
                                    )

                                    HistoryActiveFiltersRow(
                                        filter = state.filter,
                                        modifier = Modifier.padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            bottom = 10.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                },
                containerColor = HistoryExpressiveDefaults.pageContainerColor,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .align(Alignment.Center),
            ) { innerPadding ->
                HistoryBody(
                    state = state,
                    innerPadding = innerPadding,
                    onDownload = onDownload,
                    onOpenAuthor = onOpenAuthor,
                )
            }

            if (filterSheetVisible) {
                HistoryFilterBottomSheet(
                    draftFilter = draftFilter,
                    onDraftFilterChange = { draftFilter = it },
                    onDismiss = { filterSheetVisible = false },
                    onReset = {
                        draftFilter = HistoryFilter()
                        onApplyFilter(HistoryFilter())
                        filterSheetVisible = false
                    },
                    onApply = {
                        onApplyFilter(draftFilter)
                        filterSheetVisible = false
                    },
                    onPickCustomRange = { showDateRangePicker = true },
                )
            }

            if (showDateRangePicker) {
                val pickerState = rememberDateRangePickerState(
                    initialSelectedStartDateMillis = draftFilter.customStartUtcMillis,
                    initialSelectedEndDateMillis = draftFilter.customEndUtcMillis,
                )
                DatePickerDialog(
                    onDismissRequest = { showDateRangePicker = false },
                    dismissButton = {
                        OutlinedButton(onClick = { showDateRangePicker = false }) {
                            Text(text = stringResource(android.R.string.cancel))
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                draftFilter = draftFilter.copy(
                                    customStartUtcMillis = pickerState.selectedStartDateMillis,
                                    customEndUtcMillis = pickerState.selectedEndDateMillis,
                                )
                                showDateRangePicker = false
                            },
                        ) {
                            Text(text = stringResource(android.R.string.ok))
                        }
                    },
                ) {
                    DateRangePicker(
                        state = pickerState,
                        showModeToggle = false,
                        modifier = Modifier.wrapContentHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryBusinessTabs(
    tabs: List<HistoryTab>,
    selectedBusiness: String?,
    onSelectBusiness: (String) -> Unit,
) {
    val selectedIndex = tabs.indexOfFirst { it.type == selectedBusiness }.coerceAtLeast(0)

    if (tabs.size <= 4) {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = HistoryExpressiveDefaults.pageContainerColor,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab.type == selectedBusiness,
                    onClick = { onSelectBusiness(tab.type) },
                    text = {
                        Text(
                            text = tab.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    } else {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 16.dp,
            containerColor = HistoryExpressiveDefaults.pageContainerColor,
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab.type == selectedBusiness,
                    onClick = { onSelectBusiness(tab.type) },
                    text = {
                        Text(
                            text = tab.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryBody(
    state: HistoryUiState,
    innerPadding: PaddingValues,
    onDownload: (HistoryItem) -> Unit,
    onOpenAuthor: (HistoryItem) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.page) {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }

    when {
        state.loading && state.items.isEmpty() -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                HistoryExpressiveLoadingIndicator(
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        state.items.isEmpty() -> {
            HistoryEmptyState(
                text = when {
                    !state.isLoggedIn -> stringResource(R.string.history_login_required)
                    !state.errorText.isNullOrBlank() -> state.errorText
                    else -> stringResource(R.string.history_empty)
                }.orEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }

        else -> {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 10.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 32.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = state.items,
                    key = { _, item -> "${item.bvid ?: item.uri ?: item.title}-${item.viewAt}" },
                ) { index, item ->
                    val previousItem = state.items.getOrNull(index - 1)
                    val showSectionHeader =
                        previousItem == null || !isSameHistorySection(item.viewAt, previousItem.viewAt)

                    if (showSectionHeader) {
                        HistoryDateHeader(
                            title = formatHistorySectionTitle(item.viewAt),
                            modifier = Modifier.padding(
                                top = if (index == 0) 2.dp else 14.dp,
                                bottom = 8.dp,
                            ),
                        )
                    }

                    HistoryItemCard(
                        item = item,
                        onDownload = { onDownload(item) },
                        onOpenAuthor = { onOpenAuthor(item) },
                        showDivider = index != state.items.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryActiveFiltersRow(
    filter: HistoryFilter,
    modifier: Modifier = Modifier,
) {
    val keyword = filter.keyword.trim()
    val labels = mutableListOf<String>()
    if (keyword.isNotEmpty()) {
        labels += "${stringResource(R.string.history_keyword)} / $keyword"
    }
    if (filter.duration != HistoryDurationFilter.All) {
        labels +=
            "${stringResource(R.string.history_filter_duration)} / " +
                historyDurationFilterLabel(filter.duration)
    }
    if (filter.time != HistoryTimeFilter.All) {
        labels +=
            "${stringResource(R.string.history_filter_time)} / " +
                historyTimeFilterLabel(
                    filter = filter.time,
                    startUtcMillis = filter.customStartUtcMillis,
                    endUtcMillis = filter.customEndUtcMillis,
                )
    }
    if (filter.device != HistoryDeviceFilter.All) {
        labels +=
            "${stringResource(R.string.history_filter_device)} / " +
                historyDeviceFilterLabel(filter.device)
    }

    if (labels.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        labels.forEach { label ->
            Surface(
                color = HistoryExpressiveDefaults.filterChipColor,
                shape = CircleShape,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryDateHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    LoadingIndicator(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryPagerCard(
    page: Int,
    totalPages: Int,
    hasMore: Boolean,
    loading: Boolean,
    pageInput: String,
    onPageInputChange: (String) -> Unit,
    onPageInputFocusChange: (Boolean) -> Unit,
    onSubmitPage: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerButtonShapes = ButtonDefaults.shapesFor(38.dp)
    val pageSummary = if (totalPages > 0) {
        stringResource(R.string.history_page_status_with_pages, page, totalPages)
    } else {
        stringResource(R.string.history_page_status, page)
    }

    Surface(
        color = HistoryExpressiveDefaults.groupContainerColor,
        shape = MaterialTheme.shapes.largeIncreased,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            TextButton(
                onClick = onPrev,
                enabled = page > 1 && !loading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp),
            ) {
                Text(text = stringResource(R.string.history_page_prev))
            }

            Surface(
                color = HistoryExpressiveDefaults.inputContainerColor,
                shape = pagerButtonShapes.shape,
                border = HistoryExpressiveDefaults.thinBorder,
                modifier = Modifier
                    .width(64.dp)
                    .height(38.dp)
                    .onFocusChanged { onPageInputFocusChange(it.isFocused) },
            ) {
                BasicTextField(
                    value = pageInput,
                    onValueChange = onPageInputChange,
                    enabled = !loading,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall.copy(
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmitPage() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentHeight(Alignment.CenterVertically),
                    decorationBox = { innerTextField ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                        ) {
                            innerTextField()
                        }
                    },
                )
            }

            Text(
                text = pageSummary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            TextButton(
                onClick = onNext,
                enabled = hasMore && !loading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp),
            ) {
                Text(text = stringResource(R.string.history_page_next))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryItemCard(
    item: HistoryItem,
    onDownload: () -> Unit,
    onOpenAuthor: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayTitle = item.title.ifBlank {
        item.longTitle?.takeIf { it.isNotBlank() } ?: item.bvid.orEmpty()
    }
    val duration = item.duration.coerceAtLeast(0)
    val watched = when {
        duration <= 0 -> 0
        item.progress < 0 -> duration
        else -> item.progress.coerceIn(0, duration)
    }
    val progressPercent = if (duration > 0) {
        (watched * 100f / duration).coerceIn(0f, 100f)
    } else {
        0f
    }
    val showProgressBar = duration > 0 && watched < duration
    val progressText = if (duration > 0) {
        if (item.progress < 0) {
            stringResource(
                R.string.history_progress_completed,
                formatHistoryDuration(duration),
            )
        } else {
            stringResource(
                R.string.history_progress_format,
                formatHistoryDuration(watched),
                formatHistoryDuration(duration),
            )
        }
    } else {
        null
    }
    val canJumpDownload = !item.toParseUrl().isNullOrBlank()
    val authorClickable = item.authorMid != null
    val authorName = item.authorName.takeIf { it.isNotBlank() }
    val viewAtText = formatHistoryTimestamp(item.viewAt)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(114.dp)
                    .aspectRatio(120f / 72f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = item.displayCoverUrl,
                    placeholder = painterResource(R.drawable.empty),
                    error = painterResource(R.drawable.empty),
                    fallback = painterResource(R.drawable.empty),
                    contentDescription = stringResource(R.string.history_cover_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                if (showProgressBar) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        drawStopIndicator = {},
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (authorName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.then(
                            if (authorClickable) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onOpenAuthor,
                                )
                            } else {
                                Modifier
                            }
                        ),
                    ) {
                        AsyncImage(
                            model = item.authorAvatarUrl ?: R.drawable.default_avatar,
                            placeholder = painterResource(R.drawable.default_avatar),
                            error = painterResource(R.drawable.default_avatar),
                            fallback = painterResource(R.drawable.default_avatar),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (progressText != null) {
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = viewAtText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                    )
                }

            }

            if (canJumpDownload) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterVertically),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save_alt_24),
                        contentDescription = stringResource(R.string.history_download),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 126.dp),
            )
        }
    }
}

@Composable
private fun HistoryEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            AsyncImage(
                model = R.drawable.empty,
                contentDescription = null,
                modifier = Modifier.size(220.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun HistoryFilterBottomSheet(
    draftFilter: HistoryFilter,
    onDraftFilterChange: (HistoryFilter) -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onPickCustomRange: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val durationOptions = listOf(
        HistoryToggleOption(
            HistoryDurationFilter.All,
            stringResource(R.string.history_filter_duration_all),
        ),
        HistoryToggleOption(
            HistoryDurationFilter.Under10,
            stringResource(R.string.history_filter_duration_under_10),
        ),
        HistoryToggleOption(
            HistoryDurationFilter.Between10And30,
            stringResource(R.string.history_filter_duration_10_30),
        ),
        HistoryToggleOption(
            HistoryDurationFilter.Between30And60,
            stringResource(R.string.history_filter_duration_30_60),
        ),
        HistoryToggleOption(
            HistoryDurationFilter.Over60,
            stringResource(R.string.history_filter_duration_over_60),
        ),
    )
    val timeOptions = listOf(
        HistoryToggleOption(HistoryTimeFilter.All, stringResource(R.string.history_filter_time_all)),
        HistoryToggleOption(HistoryTimeFilter.Today, stringResource(R.string.history_filter_time_today)),
        HistoryToggleOption(
            HistoryTimeFilter.Yesterday,
            stringResource(R.string.history_filter_time_yesterday),
        ),
        HistoryToggleOption(HistoryTimeFilter.Week, stringResource(R.string.history_filter_time_week)),
        HistoryToggleOption(
            HistoryTimeFilter.Custom,
            stringResource(R.string.history_filter_time_custom),
        ),
    )
    val deviceOptions = listOf(
        HistoryToggleOption(
            HistoryDeviceFilter.All,
            stringResource(R.string.history_filter_device_all),
        ),
        HistoryToggleOption(HistoryDeviceFilter.Pc, stringResource(R.string.history_filter_device_pc)),
        HistoryToggleOption(
            HistoryDeviceFilter.Phone,
            stringResource(R.string.history_filter_device_phone),
        ),
        HistoryToggleOption(
            HistoryDeviceFilter.Pad,
            stringResource(R.string.history_filter_device_pad),
        ),
        HistoryToggleOption(HistoryDeviceFilter.Tv, stringResource(R.string.history_filter_device_tv)),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HistoryExpressiveDefaults.pageContainerColor,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.history_more_filters),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = draftFilter.keyword,
                onValueChange = { onDraftFilterChange(draftFilter.copy(keyword = it)) },
                label = { Text(text = stringResource(R.string.history_keyword)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HistoryFilterToggleSection(
                title = stringResource(R.string.history_filter_duration),
                options = durationOptions,
                selected = draftFilter.duration,
                onSelect = { onDraftFilterChange(draftFilter.copy(duration = it)) },
            )

            HistoryFilterToggleSection(
                title = stringResource(R.string.history_filter_time),
                options = timeOptions,
                selected = draftFilter.time,
                onSelect = { selection ->
                    onDraftFilterChange(draftFilter.copy(time = selection))
                },
            )

            AnimatedVisibility(visible = draftFilter.time == HistoryTimeFilter.Custom) {
                Surface(
                    color = HistoryExpressiveDefaults.groupContainerColor,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.history_filter_custom_range_pick),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = formatHistoryRange(
                                    startUtcMillis = draftFilter.customStartUtcMillis,
                                    endUtcMillis = draftFilter.customEndUtcMillis,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        OutlinedButton(onClick = onPickCustomRange) {
                            Text(text = stringResource(R.string.history_filter_custom_range_pick))
                        }
                    }
                }
            }

            HistoryFilterToggleSection(
                title = stringResource(R.string.history_filter_device),
                options = deviceOptions,
                selected = draftFilter.device,
                onSelect = { onDraftFilterChange(draftFilter.copy(device = it)) },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.history_filter_reset))
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.history_filter_apply))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> HistoryFilterToggleSection(
    title: String,
    options: List<HistoryToggleOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val toggleColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = HistoryExpressiveDefaults.toggleContainerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            options.forEachIndexed { index, option ->
                ToggleButton(
                    checked = option.value == selected,
                    onCheckedChange = { onSelect(option.value) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = toggleColors,
                    modifier = Modifier
                        .semantics { role = Role.RadioButton }
                        .heightIn(min = 44.dp),
                ) {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun formatHistoryRange(
    startUtcMillis: Long?,
    endUtcMillis: Long?,
): String {
    return if (startUtcMillis == null || endUtcMillis == null) {
        stringResource(R.string.history_filter_custom_range_none)
    } else {
        stringResource(
            R.string.history_filter_custom_range_value,
            formatHistoryUtcDate(startUtcMillis),
            formatHistoryUtcDate(endUtcMillis),
        )
    }
}

private fun formatHistoryTimestamp(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "--"
    return runCatching {
        Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .format(HISTORY_TIME_FORMATTER)
    }.getOrDefault("--")
}

private fun formatHistoryUtcDate(epochMillis: Long): String {
    return runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .format(HISTORY_DATE_FORMATTER)
    }.getOrDefault("--")
}

private fun formatHistoryDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }
}

@Composable
private fun formatHistorySectionTitle(epochSeconds: Long): String {
    val sectionDate = historySectionDate(epochSeconds) ?: return "--"
    val today = LocalDate.now(ZoneId.systemDefault())
    return when (sectionDate) {
        today -> stringResource(R.string.history_filter_time_today)
        today.minusDays(1) -> stringResource(R.string.history_filter_time_yesterday)
        else -> sectionDate.format(HISTORY_SECTION_FORMATTER)
    }
}

private fun isSameHistorySection(firstEpochSeconds: Long, secondEpochSeconds: Long): Boolean {
    return historySectionDate(firstEpochSeconds) == historySectionDate(secondEpochSeconds)
}

private fun historySectionDate(epochSeconds: Long): LocalDate? {
    if (epochSeconds <= 0L) return null
    return runCatching {
        Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }.getOrNull()
}

@Composable
private fun historyDurationFilterLabel(filter: HistoryDurationFilter): String {
    return when (filter) {
        HistoryDurationFilter.All -> stringResource(R.string.history_filter_duration_all)
        HistoryDurationFilter.Under10 -> stringResource(R.string.history_filter_duration_under_10)
        HistoryDurationFilter.Between10And30 -> stringResource(R.string.history_filter_duration_10_30)
        HistoryDurationFilter.Between30And60 -> stringResource(R.string.history_filter_duration_30_60)
        HistoryDurationFilter.Over60 -> stringResource(R.string.history_filter_duration_over_60)
    }
}

@Composable
private fun historyTimeFilterLabel(
    filter: HistoryTimeFilter,
    startUtcMillis: Long?,
    endUtcMillis: Long?,
): String {
    return when (filter) {
        HistoryTimeFilter.All -> stringResource(R.string.history_filter_time_all)
        HistoryTimeFilter.Today -> stringResource(R.string.history_filter_time_today)
        HistoryTimeFilter.Yesterday -> stringResource(R.string.history_filter_time_yesterday)
        HistoryTimeFilter.Week -> stringResource(R.string.history_filter_time_week)
        HistoryTimeFilter.Custom -> formatHistoryRange(startUtcMillis, endUtcMillis)
    }
}

@Composable
private fun historyDeviceFilterLabel(filter: HistoryDeviceFilter): String {
    return when (filter) {
        HistoryDeviceFilter.All -> stringResource(R.string.history_filter_device_all)
        HistoryDeviceFilter.Pc -> stringResource(R.string.history_filter_device_pc)
        HistoryDeviceFilter.Phone -> stringResource(R.string.history_filter_device_phone)
        HistoryDeviceFilter.Pad -> stringResource(R.string.history_filter_device_pad)
        HistoryDeviceFilter.Tv -> stringResource(R.string.history_filter_device_tv)
    }
}

private object HistoryExpressiveDefaults {
    val pageContainerColor
        @Composable
        get() = if (!MaterialTheme.colorScheme.usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    val pageBackgroundBrush
        @Composable
        get() = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.26f),
                pageContainerColor,
                pageContainerColor,
            ),
        )

    val toolbarActionColor
        @Composable
        get() = if (!MaterialTheme.colorScheme.usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceBright
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val listItemContainerColor
        @Composable
        get() = if (!MaterialTheme.colorScheme.usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceBright
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val groupContainerColor
        @Composable
        get() = if (!MaterialTheme.colorScheme.usesPureBlackSurfaces()) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }

    val inputContainerColor
        @Composable
        get() = MaterialTheme.colorScheme.surface

    val authorChipColor
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val filterChipColor
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val toggleContainerColor
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val thinBorder
        @Composable
        get() = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        )
}

private fun androidx.compose.material3.ColorScheme.usesPureBlackSurfaces(): Boolean {
    return surface == Color.Black && background == Color.Black
}

private val HISTORY_TIME_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
private val HISTORY_DATE_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
private val HISTORY_SECTION_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
