package eu.kanade.tachiyomi.ui.browse.seasonal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListGridSkeleton
import eu.kanade.tachiyomi.ui.anilist.AniListLoginRequired
import eu.kanade.tachiyomi.ui.anilist.AniListRowSkeleton
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.anilistStatusLabel
import eu.kanade.tachiyomi.ui.home.HomeItemData
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import mihon.feature.upcoming.anime.UpcomingAnimeScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import tachiyomi.core.common.i18n.stringResource as contextStringResource

@Composable
fun seasonalTab(screenModel: SeasonalScreenModel): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = AMR.strings.label_seasonal_chart,
        searchEnabled = true,
        searchQuery = state.searchQuery,
        onChangeSearchQuery = screenModel::search,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.label_upcoming),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { navigator.push(UpcomingAnimeScreen()) },
            ),
            AppBar.Action(
                title = stringResource(AMR.strings.seasonal_filter),
                icon = Icons.Default.FilterList,
                iconTint = if (hasActiveFilters(state)) MaterialTheme.colorScheme.active else null,
                onClick = { screenModel.toggleFilterDialog() },
            ),
        ),
        content = { contentPadding, _ ->
            val context = LocalContext.current
            var selectedAnimeForStatus by remember { mutableStateOf<HomeItemData?>(null) }
            var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }

            if (state.showFilterDialog) {
                SeasonalFilterDialog(
                    state = state,
                    onApplyFilters = screenModel::applyFilters,
                    onSelectSort = screenModel::setSort,
                    onSetSortAscending = screenModel::setSortAscending,
                    onSelectViewMode = screenModel::setViewMode,
                    onSelectGridColumns = screenModel::setGridColumns,
                    onDismissRequest = screenModel::toggleFilterDialog,
                )
            }

            addTarget?.let { target ->
                AniListSourceSearchSheet(
                    target = target,
                    navigator = navigator,
                    onDismissRequest = { addTarget = null },
                )
            }

            selectedAnimeForStatus?.let { item ->
                SeasonalStatusDialog(
                    title = item.title,
                    currentStatus = item.trackerStatus,
                    onSelectStatus = { newStatus ->
                        screenModel.setAnimeStatus(item.id, newStatus) { success ->
                            if (success) {
                                val label = anilistStatusLabel(context, newStatus)
                                context.toast(context.contextStringResource(AMR.strings.seasonal_status_updated, label))
                            } else {
                                context.toast(context.contextStringResource(AMR.strings.seasonal_login_prompt))
                            }
                        }
                    },
                    onRemoveStatus = {
                        screenModel.removeAnimeStatus(item.id, item.mediaListId) { success ->
                            if (success) {
                                context.toast(context.contextStringResource(AMR.strings.seasonal_removed_from_list))
                            }
                        }
                    },
                    onDismissRequest = { selectedAnimeForStatus = null },
                )
            }

            SeasonalScreen(
                state = state,
                contentPadding = contentPadding,
                onRefresh = screenModel::refresh,
                onRetry = screenModel::refresh,
                onSeasonChange = { season, year -> screenModel.setSeason(season, year) },
                onSelectSort = screenModel::setSort,
                onToggleOrder = screenModel::toggleSortOrder,
                onSelectFormat = screenModel::setSeasonFormat,
                onSelectStatus = screenModel::setStatusFilter,
                onClearFormat = { screenModel.setSeasonFormat(-1) },
                onClearStatus = { screenModel.setStatusFilter(0) },
                onClearGenre = { genre -> screenModel.setGenres(state.selectedGenres - genre) },
                onClearAll = { screenModel.clearAllFilters() },
                onStatusClick = { item -> selectedAnimeForStatus = item },
                onAddToPlanning = { item ->
                    screenModel.setAnimeStatus(item.id, "PLANNING") { success ->
                        if (success) {
                            context.toast(context.contextStringResource(AMR.strings.seasonal_added_to_planning))
                        } else {
                            context.toast(context.contextStringResource(AMR.strings.seasonal_login_prompt))
                        }
                    }
                },
                onAddClick = { item ->
                    addTarget = AniListAddTarget(
                        mediaId = item.id,
                        title = item.title,
                    )
                },
            )
        },
    )
}

internal fun hasActiveFilters(state: SeasonalScreenModel.State): Boolean {
    return state.season != -1 ||
        state.year > 0 ||
        state.format > 0 ||
        state.statusFilter != 0 ||
        state.selectedGenres.isNotEmpty()
}

@Composable
private fun SeasonalScreen(
    state: SeasonalScreenModel.State,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSeasonChange: (Int, Int) -> Unit,
    onSelectSort: (Int) -> Unit,
    onToggleOrder: () -> Unit,
    onSelectFormat: (Int) -> Unit,
    onSelectStatus: (Int) -> Unit,
    onClearFormat: () -> Unit,
    onClearStatus: () -> Unit,
    onClearGenre: (String) -> Unit,
    onClearAll: () -> Unit,
    onStatusClick: (HomeItemData) -> Unit,
    onAddToPlanning: (HomeItemData) -> Unit,
    onAddClick: (HomeItemData) -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow

    // Not logged in: show only the login prompt, not dead season/filter controls.
    if (state.notLoggedIn) {
        AniListLoginRequired(
            onRetry = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            stringRes = AMR.strings.seasonal_login_required,
        )
        return
    }

    val selection = SeasonSelection.of(state.season, state.year)
    val effectiveSeason = selection.index
    val effectiveYear = selection.year
    val prevSeason = {
        val newSeason = (effectiveSeason - 1 + 4) % 4
        val newYear = if (effectiveSeason == 0) effectiveYear - 1 else effectiveYear
        onSeasonChange(newSeason, newYear)
    }
    val nextSeason = {
        val newSeason = (effectiveSeason + 1) % 4
        val newYear = if (effectiveSeason == 3) effectiveYear + 1 else effectiveYear
        onSeasonChange(newSeason, newYear)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        SeasonalHeader(
            state = state,
            onPrevSeason = prevSeason,
            onNextSeason = nextSeason,
            onJumpToCurrent = {
                // Reset to the "current" sentinels rather than pinning today's resolved season and
                // year, so the view keeps tracking the current season as it rolls over instead of
                // freezing on this one.
                onSeasonChange(-1, -1)
            },
            onSelectSeason = { onSeasonChange(it, state.year) },
            onSelectYear = { onSeasonChange(state.season, it) },
            onSelectFormat = onSelectFormat,
            onSelectStatus = onSelectStatus,
            onSelectSort = onSelectSort,
            onToggleOrder = onToggleOrder,
            onClearFormat = onClearFormat,
            onClearStatus = onClearStatus,
            onClearGenre = onClearGenre,
            onClearAll = onClearAll,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            PullRefresh(
                refreshing = state.isRefreshing,
                onRefresh = onRefresh,
                enabled = true,
                indicatorPadding = contentPadding,
            ) {
                when {
                    state.isLoading && state.seasonalList.isEmpty() -> {
                        if (state.viewMode == 1) {
                            AniListRowSkeleton()
                        } else {
                            AniListGridSkeleton()
                        }
                    }

                    state.hasError && state.seasonalList.isEmpty() -> {
                        ErrorState(
                            modifier = Modifier.fillMaxSize(),
                            onRetry = onRetry,
                        )
                    }

                    state.searchQuery?.isNotBlank() == true && state.seasonalList.isEmpty() -> {
                        NoResultsState(
                            query = state.searchQuery.orEmpty(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    state.seasonalList.isEmpty() -> {
                        EmptyState(modifier = Modifier.fillMaxSize())
                    }

                    state.viewMode == 1 -> {
                        SeasonalList(
                            state = state,
                            contentPadding = contentPadding,
                            navigator = navigator,
                            onStatusClick = onStatusClick,
                            onAddToPlanning = onAddToPlanning,
                            onAddClick = onAddClick,
                        )
                    }

                    else -> {
                        SeasonalGrid(
                            state = state,
                            contentPadding = contentPadding,
                            navigator = navigator,
                            onAddClick = onAddClick,
                        )
                    }
                }
            }
        }
    }
}
