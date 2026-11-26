package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TabbedScreen(
    titleRes: StringResource?,
    tabs: ImmutableList<TabContent>,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { tabs.size },
    // ANK -->
    scrollable: Boolean = false,
    extraActions: @Composable () -> Unit = {},
    // ANK <--
    searchQuery: String? = null,
    onChangeSearchQuery: (String?) -> Unit = {},
    // KMK -->
    feedScreenModel: FeedScreenModel? = null,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel? = null,
    // KMK <--
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // KMK -->
    val feedState = feedScreenModel?.state?.collectAsState()
    val bulkFavoriteState = bulkFavoriteScreenModel?.state?.collectAsState()
    // KMK <--

    Scaffold(
        topBar = {
            val tab = tabs[state.currentPage]
            val searchEnabled = tab.searchEnabled
            // KMK -->
            if (bulkFavoriteScreenModel != null && bulkFavoriteState?.value?.selectionMode == true) {
                BulkSelectionToolbar(
                    selectedCount = bulkFavoriteState.value.selection.size,
                    isRunning = bulkFavoriteState.value.isRunning,
                    onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                    onChangeCategoryClick = bulkFavoriteScreenModel::addFavorite,
                    onSelectAll = {
                        feedState?.value?.items?.let { result ->
                            result.mapNotNull { it.results }
                                .flatten()
                                .forEach { bulkFavoriteScreenModel.select(it) }
                        }
                    },
                    onReverseSelection = {
                        feedState?.value?.items?.let { result ->
                            result.mapNotNull { it.results }
                                .flatten()
                                .let { bulkFavoriteScreenModel.reverseSelection(it) }
                        }
                    },
                )
            } else if (titleRes != null) {
                // KMK <--
                // ANK -->
                val activeQuery = if (tab.onChangeSearchQuery != null) tab.searchQuery else searchQuery
                val activeOnChange = tab.onChangeSearchQuery ?: onChangeSearchQuery
                // ANK <--
                SearchToolbar(
                    titleContent = {
                        val titleText = stringResource(titleRes)
                        val fullTitle = if (tab.numberTitle > 0) "$titleText (${tab.numberTitle})" else titleText
                        AppBarTitle(
                            title = fullTitle,
                            modifier = modifier,
                        )
                    },
                    searchEnabled = searchEnabled,
                    searchQuery = if (searchEnabled) activeQuery else null,
                    onChangeSearchQuery = activeOnChange,
                    actions = {
                        // ANK -->
                        extraActions()
                        // ANK <--
                        AppBarActions(tab.actions)
                    },
                    navigateUp = tab.navigateUp,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ),
        ) {
            // ANK -->
            FlexibleTabRow(
                scrollable = scrollable,
                selectedTabIndex = state.currentPage,
            ) {
                // ANK <--
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = state.currentPage == index,
                        onClick = { scope.launch { state.animateScrollToPage(index) } },
                        text = { TabText(text = stringResource(tab.titleRes), badgeCount = tab.badgeNumber) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
            ) { page ->
                tabs[page].content(
                    PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    snackbarHostState,
                )
            }
        }
    }
}

data class TabContent(
    val titleRes: StringResource,
    val badgeNumber: Int? = null,
    val searchEnabled: Boolean = false,
    // ANK -->
    val searchQuery: String? = null,
    val onChangeSearchQuery: ((String?) -> Unit)? = null,
    val numberTitle: Int = 0,
    val cancelAction: () -> Unit = {},
    val navigateUp: (() -> Unit)? = null,
    // ANK <--
    val actions: ImmutableList<AppBar.AppBarAction> = persistentListOf(),
    val content: @Composable (contentPadding: PaddingValues, snackbarHostState: SnackbarHostState) -> Unit,
)

// ANK -->
@Composable
private fun FlexibleTabRow(
    scrollable: Boolean,
    selectedTabIndex: Int,
    block: @Composable () -> Unit,
) {
    if (scrollable) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 13.dp,
            modifier = Modifier.zIndex(1f),
        ) {
            block()
        }
    } else {
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.zIndex(1f),
        ) {
            block()
        }
    }
}
// ANK <--
