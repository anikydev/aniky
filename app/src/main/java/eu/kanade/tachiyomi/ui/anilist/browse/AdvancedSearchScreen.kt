package eu.kanade.tachiyomi.ui.anilist.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListGridCard
import eu.kanade.tachiyomi.ui.anilist.AniListGridSkeleton
import eu.kanade.tachiyomi.ui.anilist.AniListLoginRequired
import eu.kanade.tachiyomi.ui.anilist.AniListSectionHeader
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.ScrollToTopOnReselect
import eu.kanade.tachiyomi.ui.anilist.anilistAiringStatusLabel
import eu.kanade.tachiyomi.ui.anilist.anilistDisplayTitle
import eu.kanade.tachiyomi.ui.anilist.anilistFormatLabel
import eu.kanade.tachiyomi.ui.anilist.anilistSeasonLabel
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.active

class AdvancedSearchScreen(
    private val initialSort: String = "TRENDING_DESC",
    private val initialGenre: String? = null,
    private val initialTag: String? = null,
    private val initialSearch: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val screenModel = rememberScreenModel {
            AdvancedSearchScreenModel(
                initialSort = initialSort,
                initialGenre = initialGenre,
                initialTag = initialTag,
                initialSearch = initialSearch,
            )
        }
        val state by screenModel.state.collectAsState()

        // A pushed screen is immediately visible, so kick off the deferred first load right away.
        LaunchedEffect(Unit) { screenModel.loadIfNeeded() }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.query,
                    onChangeSearchQuery = screenModel::onQueryChange,
                    titleContent = { AppBarTitle(stringResource(AMR.strings.anilist_tab_search)) },
                    navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    actions = { AppBarActions(persistentListOf(advancedSearchFilterAction(state, screenModel))) },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            PullRefresh(
                refreshing = state.isRefreshing,
                onRefresh = screenModel::refresh,
                enabled = !state.isLoading && !state.isLoadingMore,
                modifier = Modifier.padding(contentPadding),
            ) {
                // Not logged in: show only the login prompt, not dead search controls.
                if (state.notLoggedIn) {
                    AniListLoginRequired(onRetry = screenModel::refresh)
                    return@PullRefresh
                }
                ResultChrome(state = state, screenModel = screenModel, navigator = navigator)
            }
        }
    }
}

/** Filter action shared by the pushed screen and the AniList tab page (same icon/tint/behavior). */
@Composable
fun advancedSearchFilterAction(
    state: AdvancedSearchScreenModel.State,
    screenModel: AdvancedSearchScreenModel,
) = AppBar.Action(
    title = stringResource(AMR.strings.anilist_filter),
    icon = Icons.Filled.FilterList,
    iconTint = if (hasActiveSearchFilters(state)) MaterialTheme.colorScheme.active else null,
    onClick = screenModel::toggleFilterDialog,
)

internal fun hasActiveSearchFilters(state: AdvancedSearchScreenModel.State): Boolean {
    return state.genres.isNotEmpty() ||
        state.tags.isNotEmpty() ||
        state.formats.isNotEmpty() ||
        state.statuses.isNotEmpty() ||
        state.season != null ||
        state.seasonYear != null
}

@Composable
fun searchTab(screenModel: AdvancedSearchScreenModel): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    return TabContent(
        titleRes = AMR.strings.anilist_tab_search,
        searchEnabled = true,
        searchQuery = state.query,
        onChangeSearchQuery = screenModel::onQueryChange,
        actions = persistentListOf(advancedSearchFilterAction(state, screenModel)),
        content = { padding, _ ->
            PullRefresh(
                refreshing = state.isRefreshing,
                onRefresh = screenModel::refresh,
                enabled = !state.isLoading && !state.isLoadingMore,
                modifier = Modifier.padding(padding),
            ) {
                // Not logged in: show only the login prompt, not dead search controls.
                if (state.notLoggedIn) {
                    AniListLoginRequired(onRetry = screenModel::refresh)
                    return@PullRefresh
                }
                ResultChrome(state = state, screenModel = screenModel, navigator = navigator)
            }
        },
    )
}

@Composable
private fun FilterSheet(
    state: AdvancedSearchScreenModel.State,
    screenModel: AdvancedSearchScreenModel,
) {
    var tagSearch by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf(state.genres) }
    var tags by remember { mutableStateOf(state.tags) }
    var formats by remember { mutableStateOf(state.formats) }
    var statuses by remember { mutableStateOf(state.statuses) }
    var season by remember { mutableStateOf(state.season) }
    var seasonYear by remember { mutableStateOf(state.seasonYear) }
    AdaptiveSheet(
        onDismissRequest = screenModel::toggleFilterDialog,
    ) {
        // ANK --> Apply/Clear live in a pinned bar so they are reachable without scrolling the long tag list.
        Column(modifier = Modifier.navigationBarsPadding()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterSheetFields(
                    availableGenres = state.availableGenres,
                    availableTags = state.availableTags,
                    genres = genres,
                    onToggleGenre = { g -> genres = if (g in genres) genres - g else genres + g },
                    tags = tags,
                    onToggleTag = { t -> tags = if (t in tags) tags - t else tags + t },
                    formats = formats,
                    onToggleFormat = { f -> formats = if (f in formats) formats - f else formats + f },
                    statuses = statuses,
                    onToggleStatus = { s -> statuses = if (s in statuses) statuses - s else statuses + s },
                    season = season,
                    onSetSeason = { season = it },
                    seasonYear = seasonYear,
                    onSetYear = { seasonYear = it },
                    tagSearch = tagSearch,
                    onTagSearchChange = { tagSearch = it },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        genres = emptyList()
                        tags = emptyList()
                        formats = emptyList()
                        statuses = emptyList()
                        season = null
                        seasonYear = null
                        screenModel.clearFilters()
                    },
                ) {
                    Text(stringResource(AMR.strings.anilist_clear_filters))
                }
                val activeCount = genres.size + tags.size + formats.size +
                    statuses.size + (if (season != null) 1 else 0) +
                    (if (seasonYear != null) 1 else 0)
                Button(
                    onClick = {
                        screenModel.applyFilters(
                            genres = genres,
                            tags = tags,
                            formats = formats,
                            statuses = statuses,
                            season = season,
                            seasonYear = seasonYear,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (activeCount > 0) {
                            stringResource(AMR.strings.anilist_apply_filters_count, activeCount)
                        } else {
                            stringResource(MR.strings.action_ok)
                        },
                    )
                }
            }
        }
        // ANK <--
    }
}

@Composable
private fun FilterSheetFields(
    availableGenres: List<String>,
    availableTags: List<eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaTag>,
    genres: List<String>,
    onToggleGenre: (String) -> Unit,
    tags: List<String>,
    onToggleTag: (String) -> Unit,
    formats: List<String>,
    onToggleFormat: (String) -> Unit,
    statuses: List<String>,
    onToggleStatus: (String) -> Unit,
    season: String?,
    onSetSeason: (String?) -> Unit,
    seasonYear: Int?,
    onSetYear: (Int?) -> Unit,
    tagSearch: String,
    onTagSearchChange: (String) -> Unit,
) {
    // Emitted straight into the scrolling column owned by [FilterSheet].
    AniListSectionHeader(stringResource(AMR.strings.anilist_filter_genres))
    FlowChips(availableGenres, genres, onToggleGenre)
    if (availableTags.isNotEmpty()) {
        AniListSectionHeader(stringResource(AMR.strings.anilist_filter_tags))
        OutlinedTextField(
            value = tagSearch,
            onValueChange = onTagSearchChange,
            placeholder = { Text(stringResource(MR.strings.action_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val filteredTags = remember(availableTags, tagSearch) {
            if (tagSearch.isBlank()) {
                availableTags
            } else {
                availableTags.filter { it.name.contains(tagSearch, ignoreCase = true) }
            }
        }
        FlowChips(filteredTags.map { it.name }, tags, onToggleTag)
    }
    AniListSectionHeader(stringResource(AMR.strings.anilist_filter_format))
    FlowChips(SEARCH_FORMATS, formats, onToggleFormat, labelFor = { anilistFormatLabel(it) })
    AniListSectionHeader(stringResource(AMR.strings.anilist_filter_status))
    FlowChips(SEARCH_STATUSES, statuses, onToggleStatus, labelFor = { anilistAiringStatusLabel(it) })
    AniListSectionHeader(stringResource(AMR.strings.anilist_filter_season))
    FlowChips(
        all = SEARCH_SEASONS,
        selected = season?.let { listOf(it) } ?: emptyList(),
        onToggle = { onSetSeason(if (season == it) null else it) },
        labelFor = { anilistSeasonLabel(it) },
    )
    // ANK: Quick-pick recent year chips alongside custom year field
    AniListSectionHeader(stringResource(AMR.strings.anilist_filter_year))
    val recentYears = remember {
        val currentYear = java.time.LocalDate.now().year
        (currentYear downTo currentYear - 6).toList()
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(recentYears, key = { it }) { year ->
            FilterChip(
                selected = seasonYear == year,
                onClick = { onSetYear(if (seasonYear == year) null else year) },
                label = { Text(year.toString()) },
            )
        }
    }
    OutlinedTextField(
        value = seasonYear?.toString() ?: "",
        onValueChange = { text ->
            onSetYear(text.filter { it.isDigit() }.take(4).toIntOrNull())
        },
        label = { Text(stringResource(AMR.strings.anilist_filter_year)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSortChips(
    state: AdvancedSearchScreenModel.State,
    screenModel: AdvancedSearchScreenModel,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SEARCH_SORTS, key = { it }) { sort ->
            FilterChip(
                selected = state.sort == sort,
                onClick = {
                    screenModel.setSort(sort)
                    screenModel.refresh()
                },
                label = { Text(sortLabel(sort)) },
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFilterChipsRow(
    state: AdvancedSearchScreenModel.State,
    screenModel: AdvancedSearchScreenModel,
) {
    if (!hasActiveSearchFilters(state)) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "clear_all_filters") {
            InputChip(
                selected = false,
                onClick = screenModel::clearFilters,
                label = { Text(stringResource(AMR.strings.anilist_clear_filters)) },
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }
        items(state.genres, key = { "genre_$it" }) { genre ->
            InputChip(
                selected = true,
                onClick = { screenModel.removeGenre(genre) },
                label = { Text(genre) },
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        items(state.tags, key = { "tag_$it" }) { tag ->
            InputChip(
                selected = true,
                onClick = { screenModel.removeTag(tag) },
                label = { Text(tag) },
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        items(state.formats, key = { "format_$it" }) { format ->
            InputChip(
                selected = true,
                onClick = { screenModel.removeFormat(format) },
                label = { Text(anilistFormatLabel(format)) },
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        items(state.statuses, key = { "status_$it" }) { status ->
            InputChip(
                selected = true,
                onClick = { screenModel.removeStatus(status) },
                label = { Text(anilistAiringStatusLabel(status)) },
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        state.season?.let { season ->
            item(key = "season_$season") {
                InputChip(
                    selected = true,
                    onClick = screenModel::removeSeason,
                    label = { Text(anilistSeasonLabel(season)) },
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
        state.seasonYear?.let { year ->
            item(key = "year_$year") {
                InputChip(
                    selected = true,
                    onClick = screenModel::removeYear,
                    label = { Text(year.toString()) },
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultsGrid(
    state: AdvancedSearchScreenModel.State,
    screenModel: AdvancedSearchScreenModel,
    navigator: Navigator,
    onAddClick: (AniListAddTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    ScrollToTopOnReselect(gridState)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 105.dp),
        state = gridState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(state.items, key = { it.id }) { item ->
            val title = anilistDisplayTitle(item.title.userPreferred, item.title.english, item.title.romaji, item.title.native)
            AniListGridCard(
                item = item,
                onClick = { navigator.push(SeasonalAnimeDetailScreen(item.id)) },
                onAdd = {
                    onAddClick(
                        AniListAddTarget(
                            mediaId = item.id,
                            title = title,
                            totalEpisodes = item.episodes,
                        ),
                    )
                },
            )
        }
        if (state.hasNextPage) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isLoadingMore) {
                        CircularProgressIndicator()
                    } else {
                        TextButton(onClick = screenModel::loadMore) {
                            Text(stringResource(AMR.strings.anilist_load_more))
                        }
                    }
                }
            }
        }
    }

    // Auto-load the next page when the end of the grid approaches.
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= latestState.items.size - 6) {
                    screenModel.loadMore()
                }
            }
    }
}

@Composable
private fun ResultChrome(
    state: AdvancedSearchScreenModel.State,
    screenModel: AdvancedSearchScreenModel,
    navigator: Navigator,
) {
    var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchSortChips(state = state, screenModel = screenModel)
        ActiveFilterChipsRow(state = state, screenModel = screenModel)
        // The branches below fill the leftover height; weight(1f) keeps the grid
        // from overflowing past the bottom of the screen.
        when {
            state.isLoading -> AniListGridSkeleton(modifier = Modifier.weight(1f))
            state.hasError -> EmptyScreen(
                stringRes = AMR.strings.anilist_browse_error,
                modifier = Modifier.weight(1f),
                actions = persistentListOf(
                    EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::refresh),
                ),
            )
            state.items.isEmpty() -> EmptyScreen(
                stringRes = AMR.strings.anilist_browse_empty,
                modifier = Modifier.weight(1f),
                actions = persistentListOf(
                    EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::refresh),
                ),
            )
            else -> ResultsGrid(
                state = state,
                screenModel = screenModel,
                navigator = navigator,
                onAddClick = { addTarget = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (state.showFilters) {
        FilterSheet(state = state, screenModel = screenModel)
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
private fun sortLabel(sort: String): String {
    return when (sort) {
        "TRENDING_DESC" -> stringResource(AMR.strings.anilist_sort_trending)
        "POPULARITY_DESC" -> stringResource(AMR.strings.anilist_sort_popular)
        "SCORE_DESC" -> stringResource(AMR.strings.anilist_sort_top_rated)
        "FAVOURITES_DESC" -> stringResource(AMR.strings.anilist_sort_favourites)
        "START_DATE_DESC" -> stringResource(AMR.strings.anilist_sort_start_date)
        else -> sort
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(
    all: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    labelFor: @Composable (String) -> String = { it },
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        all.forEach { g ->
            FilterChip(selected = g in selected, onClick = { onToggle(g) }, label = { Text(labelFor(g)) })
        }
    }
}
