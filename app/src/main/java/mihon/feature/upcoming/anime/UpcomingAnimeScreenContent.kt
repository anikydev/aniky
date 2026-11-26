@file:OptIn(kotlin.time.ExperimentalTime::class)

package mihon.feature.upcoming.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListLoginRequired
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.rememberAnilistLibraryLookup
import kotlinx.collections.immutable.persistentListOf
import mihon.feature.upcoming.anime.components.UpcomingItem
import tachiyomi.core.common.Constants
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock

@Composable
fun UpcomingAnimeScreenContent(
    state: UpcomingAnimeScreenModel.State,
    onClickUpcoming: (UpcomingAnimeItem, Long?) -> Unit,
    onSetMode: (UpcomingMode) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onForceRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    val libraryLookup = rememberAnilistLibraryLookup()
    var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }
    val today = Clock.System.now().toLocalDate(ZoneId.systemDefault())
    val selectedDate = state.selectedDay?.date

    Scaffold(
        topBar = {
            UpcomingToolbar(
                showTodayAction = !state.notLoggedIn && selectedDate != null && selectedDate != today,
                onJumpToToday = { onSelectDate(today) },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        PullRefresh(
            refreshing = state.isRefreshing,
            onRefresh = onForceRefresh,
            enabled = !state.notLoggedIn,
            indicatorPadding = paddingValues,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                if (state.notLoggedIn) {
                    AniListLoginRequired(
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                        stringRes = AMR.strings.upcoming_login_required,
                    )
                    return@Column
                }
                UpcomingModeToggle(
                    mode = state.mode,
                    onSetMode = onSetMode,
                )
                UpcomingDateSelector(
                    days = state.days,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                )
                val selectedDay = state.selectedDay
                val dayItems = selectedDay?.items.orEmpty()
                if (selectedDay != null) {
                    DateHeading(date = selectedDay.date, animeCount = dayItems.size)
                }
                if (dayItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(AMR.strings.upcoming_no_episodes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    FastScrollLazyColumn(
                        modifier = Modifier.weight(1f),
                    ) {
                        items(
                            items = dayItems,
                            key = { "item-${it.remoteId}-${it.nextEpisode}-${it.airingTime}" },
                        ) { item ->
                            val resolvedAnimeId = item.animeId
                                ?: libraryLookup.findAnimeId(item.remoteId, item.title)
                            UpcomingItem(
                                upcoming = item,
                                isInLibrary = resolvedAnimeId != null,
                                onClick = { onClickUpcoming(item, resolvedAnimeId) },
                                onAdd = {
                                    addTarget = AniListAddTarget(
                                        mediaId = item.remoteId,
                                        title = item.title,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    addTarget?.let { target ->
        AniListSourceSearchSheet(
            target = target,
            navigator = navigator,
            onDismissRequest = { addTarget = null },
        )
    }
}

@Composable
private fun UpcomingModeToggle(
    mode: UpcomingMode,
    onSetMode: (UpcomingMode) -> Unit,
) {
    val options = listOf(UpcomingMode.MINE, UpcomingMode.ALL)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onSetMode(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(
                    text = when (option) {
                        UpcomingMode.ALL -> stringResource(AMR.strings.upcoming_mode_all)
                        UpcomingMode.MINE -> stringResource(AMR.strings.upcoming_mode_mine)
                    },
                )
            }
        }
    }
}

@Composable
private fun UpcomingDateSelector(
    days: List<UpcomingDay>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
) {
    val weekdayFormatter = remember(Locale.getDefault()) { DateTimeFormatter.ofPattern("EEE") }
    val dayFormatter = remember(Locale.getDefault()) { DateTimeFormatter.ofPattern("MMM d") }
    val today = Clock.System.now().toLocalDate(ZoneId.systemDefault())
    val listState = rememberLazyListState()

    // Keep the selected day in view (defaulting to today, or when a tap lands near the edge).
    LaunchedEffect(selectedDate, days) {
        val index = days.indexOfFirst { it.date == selectedDate }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = days,
            key = { it.date.toString() },
        ) { day ->
            val weekday = when (day.date) {
                today -> stringResource(MR.strings.relative_time_today)
                today.plusDays(1) -> stringResource(AMR.strings.relative_time_tomorrow)
                else -> day.date.format(weekdayFormatter)
            }
            FilterChip(
                selected = day.date == selectedDate,
                onClick = { onSelectDate(day.date) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (day.items.isNotEmpty()) "$weekday · ${day.items.size}" else weekday,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = day.date.format(dayFormatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun UpcomingToolbar(
    showTodayAction: Boolean,
    onJumpToToday: () -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val uriHandler = LocalUriHandler.current

    AppBar(
        title = stringResource(MR.strings.label_upcoming),
        navigateUp = navigator::pop,
        actions = {
            AppBarActions(
                buildList {
                    if (showTodayAction) {
                        add(
                            AppBar.Action(
                                title = stringResource(AMR.strings.upcoming_today),
                                icon = Icons.Outlined.Today,
                                onClick = onJumpToToday,
                            ),
                        )
                    }
                    add(
                        AppBar.Action(
                            title = stringResource(MR.strings.upcoming_guide),
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            onClick = { uriHandler.openUri(Constants.URL_HELP_UPCOMING) },
                        ),
                    )
                }.let { persistentListOf(*it.toTypedArray()) },
            )
        },
    )
}

@Composable
private fun DateHeading(
    date: LocalDate,
    animeCount: Int,
) {
    val weekdayFormatter = remember(Locale.getDefault()) { DateTimeFormatter.ofPattern("EEEE") }
    val today = Clock.System.now().toLocalDate(ZoneId.systemDefault())
    val label = when (date) {
        today -> stringResource(MR.strings.relative_time_today)
        today.plusDays(1) -> stringResource(AMR.strings.relative_time_tomorrow)
        else -> date.format(weekdayFormatter)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .padding(MaterialTheme.padding.small)
                .padding(start = MaterialTheme.padding.small),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Badge(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text("$animeCount")
        }
    }
}

private fun kotlin.time.Instant.toLocalDate(timeZone: ZoneId): LocalDate {
    return java.time.Instant.ofEpochMilli(toEpochMilliseconds()).atZone(timeZone).toLocalDate()
}
