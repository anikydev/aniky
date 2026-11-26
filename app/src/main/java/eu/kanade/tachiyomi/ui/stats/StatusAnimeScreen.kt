package eu.kanade.tachiyomi.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListGridCard
import eu.kanade.tachiyomi.ui.anilist.AniListLoginRequired
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.rememberAnilistLibraryLookup
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalListItem
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

class StatusAnimeScreen(
    private val status: String,
    private val title: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val screenModel = rememberScreenModel(status) { StatusAnimeScreenModel(status) }
        val state by screenModel.state.collectAsState()
        val libraryLookup = rememberAnilistLibraryLookup()
        var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }
        var showSortMenu by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    titleContent = { AppBarTitle(title) },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = screenModel::setSearchQuery,
                    navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = screenModel::toggleDisplayMode) {
                            Icon(
                                imageVector = if (state.isGrid) {
                                    Icons.AutoMirrored.Outlined.ViewList
                                } else {
                                    Icons.Outlined.GridView
                                },
                                contentDescription = stringResource(MR.strings.action_display_mode),
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                                    contentDescription = stringResource(MR.strings.action_sort),
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                StatusAnimeScreenModel.SortOption.entries.forEach { option ->
                                    val label = when (option) {
                                        StatusAnimeScreenModel.SortOption.TITLE ->
                                            stringResource(AMR.strings.status_sort_title)
                                        StatusAnimeScreenModel.SortOption.PROGRESS ->
                                            stringResource(AMR.strings.status_sort_progress)
                                        StatusAnimeScreenModel.SortOption.SCORE ->
                                            stringResource(AMR.strings.status_sort_score)
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        leadingIcon = if (state.sort == option) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            screenModel.setSort(option)
                                            showSortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
        ) { contentPadding ->
            val filteredEntries = remember(state.entries, state.searchQuery, state.sort) {
                state.filteredEntries
            }

            PullRefresh(
                refreshing = state.isRefreshing,
                onRefresh = { screenModel.refresh(isPullRefresh = true) },
                enabled = !state.isLoading && !state.notLoggedIn,
                indicatorPadding = contentPadding,
            ) {
                when {
                    state.isLoading -> {
                        LoadingScreen(modifier = Modifier.padding(contentPadding))
                    }
                    state.notLoggedIn -> {
                        Box(modifier = Modifier.padding(contentPadding)) {
                            AniListLoginRequired(onRetry = screenModel::refresh)
                        }
                    }
                    state.hasError -> {
                        EmptyScreen(
                            modifier = Modifier.padding(contentPadding),
                            stringRes = AMR.strings.stats_status_list_error,
                            actions = persistentListOf(
                                EmptyScreenAction(
                                    stringRes = MR.strings.action_retry,
                                    icon = Icons.Outlined.Refresh,
                                    onClick = screenModel::refresh,
                                ),
                            ),
                        )
                    }
                    state.entries.isEmpty() -> {
                        EmptyScreen(
                            modifier = Modifier.padding(contentPadding),
                            stringRes = AMR.strings.stats_status_list_empty,
                        )
                    }
                    filteredEntries.isEmpty() -> {
                        EmptyScreen(
                            modifier = Modifier.padding(contentPadding),
                            stringRes = MR.strings.no_results_found,
                        )
                    }
                    state.isGrid -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(112.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding + PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item(
                                key = "status_header_count",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                StatusCountHeader(count = filteredEntries.size)
                            }
                            items(filteredEntries, key = { it.item.id }) { entry ->
                                val item = entry.item
                                val libraryAnimeId = libraryLookup.findAnimeId(item.id, item.title)
                                AniListGridCard(
                                    title = item.title,
                                    coverUrl = item.coverUrl,
                                    score = entry.score100,
                                    format = item.format,
                                    episodes = entry.totalEpisodes,
                                    status = status,
                                    inLibrary = item.inLibrary || libraryAnimeId != null,
                                    libraryAnimeId = libraryAnimeId,
                                    onClick = { navigator.push(SeasonalAnimeDetailScreen(item.id)) },
                                    onAdd = {
                                        addTarget = AniListAddTarget(
                                            mediaId = item.id,
                                            title = item.title,
                                            totalEpisodes = entry.totalEpisodes,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding + PaddingValues(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item(key = "status_header_count") {
                                StatusCountHeader(
                                    count = filteredEntries.size,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            items(filteredEntries, key = { it.item.id }) { entry ->
                                val item = entry.item
                                val libraryAnimeId = libraryLookup.findAnimeId(item.id, item.title)
                                val resolvedItem = if (libraryAnimeId != null && !item.inLibrary) {
                                    item.copy(inLibrary = true)
                                } else {
                                    item
                                }
                                SeasonalListItem(
                                    item = resolvedItem,
                                    onClick = { navigator.push(SeasonalAnimeDetailScreen(item.id)) },
                                    onAdd = {
                                        addTarget = AniListAddTarget(
                                            mediaId = item.id,
                                            title = item.title,
                                            totalEpisodes = entry.totalEpisodes,
                                        )
                                    },
                                    onOpenInLibrary = libraryAnimeId?.let { id ->
                                        { navigator.push(MangaScreen(id)) }
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
}

@Composable
private fun StatusCountHeader(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ) {
            Text(
                text = stringResource(AMR.strings.status_anime_count, count),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
