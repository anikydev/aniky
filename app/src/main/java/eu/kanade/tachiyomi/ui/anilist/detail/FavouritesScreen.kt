package eu.kanade.tachiyomi.ui.anilist.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListGridCard
import eu.kanade.tachiyomi.ui.anilist.AniListGridSkeleton
import eu.kanade.tachiyomi.ui.anilist.AniListLoginRequired
import eu.kanade.tachiyomi.ui.anilist.AniListShapes
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.ScrollToTopOnReselect
import eu.kanade.tachiyomi.ui.anilist.anilistDisplayTitle
import eu.kanade.tachiyomi.ui.anilist.browse.AdvancedSearchScreen
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction

@Composable
fun favouritesTab(screenModel: FavouritesScreenModel): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    return TabContent(
        titleRes = AMR.strings.anilist_tab_favourites,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.label_stats),
                icon = Icons.Outlined.BarChart,
                onClick = { navigator.push(StatsScreen()) },
            ),
        ),
        content = { padding, _ ->
            Box(modifier = Modifier.padding(padding)) { FavouritesContent(screenModel = screenModel) }
        },
    )
}

@Composable
private fun FavouritesContent(screenModel: FavouritesScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }

    addTarget?.let { target ->
        AniListSourceSearchSheet(
            target = target,
            navigator = navigator,
            onDismissRequest = { addTarget = null },
        )
    }

    when {
        state.isLoading -> AniListGridSkeleton()
        state.notLoggedIn -> AniListLoginRequired(onRetry = screenModel::refresh)
        state.hasError -> EmptyScreen(
            stringRes = AMR.strings.anilist_browse_error,
            actions = persistentListOf(
                EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::refresh),
            ),
        )
        state.items.isEmpty() -> EmptyScreen(
            stringRes = AMR.strings.anilist_favourites_empty,
            actions = persistentListOf(
                EmptyScreenAction(
                    stringRes = MR.strings.action_search,
                    icon = Icons.Filled.Search,
                    onClick = { navigator.push(AdvancedSearchScreen()) },
                ),
                EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::refresh),
            ),
        )
        else -> PullRefresh(
            refreshing = state.isRefreshing,
            enabled = true,
            onRefresh = screenModel::refresh,
        ) {
            val gridState = rememberLazyGridState()
            ScrollToTopOnReselect(gridState)
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 105.dp),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = AniListShapes.Pill,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = pluralStringResource(
                                        AMR.plurals.anilist_favourites_count,
                                        state.items.size,
                                        state.items.size,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    val title = anilistDisplayTitle(item.title.userPreferred, item.title.english, item.title.romaji, item.title.native)
                    AniListGridCard(
                        item = item,
                        onClick = { navigator.push(SeasonalAnimeDetailScreen(item.id)) },
                        onAdd = {
                            addTarget = AniListAddTarget(
                                mediaId = item.id,
                                title = title,
                                totalEpisodes = item.episodes,
                            )
                        },
                    )
                }
            }
        }
    }
}
