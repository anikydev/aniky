package eu.kanade.tachiyomi.ui.anilist.mylists

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.manga.components.AnimeCover
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.anilist.dto.ALListStatus
import eu.kanade.tachiyomi.ui.anilist.AniListAddBadge
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListGridSkeleton
import eu.kanade.tachiyomi.ui.anilist.AniListInfoBadge
import eu.kanade.tachiyomi.ui.anilist.AniListLibraryBadge
import eu.kanade.tachiyomi.ui.anilist.AniListLoginRequired
import eu.kanade.tachiyomi.ui.anilist.AniListRowSkeleton
import eu.kanade.tachiyomi.ui.anilist.AniListScoreBadge
import eu.kanade.tachiyomi.ui.anilist.AniListShapes
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.ScrollToTopOnReselect
import eu.kanade.tachiyomi.ui.anilist.anilistFormatLabel
import eu.kanade.tachiyomi.ui.anilist.anilistScoreStar
import eu.kanade.tachiyomi.ui.anilist.anilistStatusColor
import eu.kanade.tachiyomi.ui.anilist.anilistStatusLabel
import eu.kanade.tachiyomi.ui.anilist.anilistTitle
import eu.kanade.tachiyomi.ui.anilist.browse.AdvancedSearchScreen
import eu.kanade.tachiyomi.ui.anilist.rememberAnilistLibraryLookup
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.pureBlackBorder
import tachiyomi.core.common.i18n.stringResource as contextStringResource

class MyListsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val screenModel = rememberScreenModel { MyListsScreenModel() }
        val state by screenModel.state.collectAsState()
        // A pushed screen is immediately visible, so kick off the deferred first load right away.
        LaunchedEffect(Unit) { screenModel.loadIfNeeded() }
        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = screenModel::search,
                    titleContent = { AppBarTitle(stringResource(AMR.strings.anilist_tab_lists)) },
                    navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    actions = { AppBarActions(myListsActions(state, screenModel, navigator)) },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Box(modifier = Modifier.padding(contentPadding)) {
                MyListsContent(screenModel = screenModel)
            }
        }
    }
}

@Composable
private fun myListsActions(
    state: MyListsScreenModel.State,
    screenModel: MyListsScreenModel,
    navigator: Navigator,
): ImmutableList<AppBar.AppBarAction> {
    var sortExpanded by remember { mutableStateOf(false) }
    return persistentListOf(
        AppBar.ActionCompose(
            title = stringResource(AMR.strings.anilist_sort),
        ) {
            Box {
                IconButton(onClick = { sortExpanded = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(AMR.strings.anilist_sort),
                    )
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                ) {
                    MyListsScreenModel.Sort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(sort)) },
                            trailingIcon = {
                                if (state.sort == sort) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            onClick = {
                                screenModel.setSort(sort)
                                sortExpanded = false
                            },
                        )
                    }
                }
            }
        },
        AppBar.Action(
            title = if (state.isGridView) {
                stringResource(AMR.strings.seasonal_view_list)
            } else {
                stringResource(AMR.strings.seasonal_view_grid)
            },
            icon = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
            onClick = screenModel::toggleViewMode,
        ),
        AppBar.OverflowAction(
            title = stringResource(MR.strings.label_stats),
            icon = Icons.Outlined.BarChart,
            onClick = { navigator.push(StatsScreen()) },
        ),
    )
}

@Composable
fun myListsTab(screenModel: MyListsScreenModel): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    return TabContent(
        titleRes = AMR.strings.anilist_tab_lists,
        searchEnabled = true,
        searchQuery = state.searchQuery,
        onChangeSearchQuery = screenModel::search,
        actions = myListsActions(state, screenModel, navigator),
        content = { padding, _ ->
            Box(modifier = Modifier.padding(padding)) { MyListsContent(screenModel = screenModel) }
        },
    )
}

@Composable
private fun MyListsContent(screenModel: MyListsScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    val libraryLookup = rememberAnilistLibraryLookup()
    var editItem by remember { mutableStateOf<MyListUiItem?>(null) }
    var deleteItem by remember { mutableStateOf<MyListUiItem?>(null) }
    var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }

    // Not logged in: show only the login prompt, not dead controls.
    if (state.notLoggedIn) {
        AniListLoginRequired(onRetry = screenModel::refresh)
        return
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(gridState)
    ScrollToTopOnReselect(listState)

    // Pull-to-refresh around the whole list (same pattern as the Seasonal tab).
    PullRefresh(
        refreshing = state.isRefreshing,
        onRefresh = screenModel::refresh,
        enabled = !state.isLoading,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Only statuses that actually have entries (plus the current selection, so the
                // user never ends up with no chip selected).
                val chips = ALListStatus.ALL.filter {
                    (state.counts[it] ?: 0) > 0 || it == state.selectedStatus
                }
                items(chips, key = { it }) { status ->
                    val count = state.counts[status] ?: 0
                    FilterChip(
                        selected = state.selectedStatus == status,
                        onClick = { screenModel.selectStatus(status) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // ANK: Colored status dot for instant visual scanning
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(anilistStatusColor(status), CircleShape),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(anilistStatusLabel(status))
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (state.selectedStatus == status) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            // The branches below fill the leftover height; weight(1f) keeps the list
            // from overflowing past the bottom of the screen.
            when {
                state.isLoading -> if (state.isGridView) {
                    AniListGridSkeleton(modifier = Modifier.weight(1f))
                } else {
                    AniListRowSkeleton(modifier = Modifier.weight(1f))
                }
                state.hasError -> EmptyScreen(
                    stringRes = AMR.strings.anilist_lists_error,
                    modifier = Modifier.weight(1f),
                    actions = persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.action_retry,
                            icon = Icons.Outlined.Refresh,
                            onClick = screenModel::refresh,
                        ),
                    ),
                )
                state.items.isEmpty() -> EmptyScreen(
                    stringRes = AMR.strings.anilist_lists_empty,
                    modifier = Modifier.weight(1f),
                    actions = persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.action_search,
                            icon = Icons.Filled.Search,
                            onClick = { navigator.push(AdvancedSearchScreen()) },
                        ),
                        EmptyScreenAction(
                            stringRes = MR.strings.action_retry,
                            icon = Icons.Outlined.Refresh,
                            onClick = screenModel::refresh,
                        ),
                    ),
                )
                state.isGridView -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.listId }) { item ->
                        val itemTitle = item.title.ifBlank { stringResource(AMR.strings.anilist_unknown_title) }
                        val libraryAnimeId = libraryLookup.findAnimeId(item.mediaId, item.title)
                        MyListGridCard(
                            item = item,
                            inLibrary = item.inLibrary || libraryAnimeId != null,
                            saving = item.listId in state.savingIds,
                            onClick = { navigator.push(SeasonalAnimeDetailScreen(item.mediaId)) },
                            onEdit = { editItem = item },
                            onDelete = { deleteItem = item },
                            onBump = { screenModel.bumpProgress(item, it) },
                            onAdd = {
                                addTarget = AniListAddTarget(
                                    mediaId = item.mediaId,
                                    title = itemTitle,
                                    totalEpisodes = item.totalEpisodes.takeIf { it > 0 },
                                )
                            },
                            onOpenInLibrary = libraryAnimeId?.let { id -> { navigator.push(MangaScreen(id)) } },
                        )
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Key on the unique MediaList entry id, not mediaId: a malformed entry can
                    // leave mediaId 0L and two of them would collide into a duplicate-key crash.
                    items(state.items, key = { it.listId }) { item ->
                        val itemTitle = item.title.ifBlank { stringResource(AMR.strings.anilist_unknown_title) }
                        val libraryAnimeId = libraryLookup.findAnimeId(item.mediaId, item.title)
                        MyListRow(
                            item = item,
                            inLibrary = item.inLibrary || libraryAnimeId != null,
                            saving = item.listId in state.savingIds,
                            onClick = { navigator.push(SeasonalAnimeDetailScreen(item.mediaId)) },
                            onEdit = { editItem = item },
                            onDelete = { deleteItem = item },
                            onBump = { screenModel.bumpProgress(item, it) },
                            onAdd = {
                                addTarget = AniListAddTarget(
                                    mediaId = item.mediaId,
                                    title = itemTitle,
                                    totalEpisodes = item.totalEpisodes.takeIf { it > 0 },
                                )
                            },
                            onOpenInLibrary = libraryAnimeId?.let { id -> { navigator.push(MangaScreen(id)) } },
                        )
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

    editItem?.let { item ->
        ListEditSheet(
            item = item,
            onDismiss = { editItem = null },
            onSaved = {
                editItem = null
                screenModel.onEntrySaved()
            },
        )
    }

    deleteItem?.let { item ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text(stringResource(AMR.strings.anilist_entry_delete)) },
            text = { Text(stringResource(AMR.strings.anilist_entry_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteItem = null
                        screenModel.deleteEntry(item) {
                            context.toast(context.contextStringResource(AMR.strings.anilist_entry_deleted))
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun sortLabel(sort: MyListsScreenModel.Sort): String {
    return when (sort) {
        MyListsScreenModel.Sort.UPDATED -> stringResource(AMR.strings.anilist_sort_updated)
        MyListsScreenModel.Sort.TITLE -> stringResource(AMR.strings.anilist_sort_title)
        MyListsScreenModel.Sort.SCORE -> stringResource(AMR.strings.anilist_sort_score)
        MyListsScreenModel.Sort.PROGRESS -> stringResource(AMR.strings.anilist_sort_progress)
    }
}

// ANK -->
/** "3 / 12 eps" (or "Ep 3" when the total is unknown); [short] gives the compact "3/12" form. */
@Composable
private fun myListProgressText(item: MyListUiItem, short: Boolean = false): String {
    return when {
        item.totalEpisodes <= 0 -> stringResource(AMR.strings.anilist_progress_episode, item.progress)
        short -> stringResource(AMR.strings.anilist_progress_short, item.progress, item.totalEpisodes)
        else -> stringResource(AMR.strings.anilist_progress_of_total, item.progress, item.totalEpisodes)
    }
}

/** −/+ progress stepper shared by the row and grid cards. */
@Composable
private fun ProgressStepper(
    item: MyListUiItem,
    saving: Boolean,
    onBump: (Int) -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AniListShapes.Pill,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onBump(-1) },
                enabled = !saving && item.progress > 0,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(AMR.strings.anilist_progress_decrease),
                    modifier = Modifier.size(iconSize),
                )
            }
            IconButton(
                onClick = { onBump(1) },
                enabled = !saving && (item.totalEpisodes <= 0 || item.progress < item.totalEpisodes),
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(AMR.strings.anilist_progress_increase),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/** Edit / delete tucked behind a single ⋮ so the destructive action is never one stray tap away. */
@Composable
private fun MyListOverflowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    buttonSize: Dp,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(buttonSize),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(AMR.strings.anilist_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(AMR.strings.anilist_edit_entry)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(AMR.strings.anilist_entry_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
// ANK <--

@Composable
private fun MyListRow(
    item: MyListUiItem,
    inLibrary: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBump: (Int) -> Unit,
    onAdd: (() -> Unit)? = null,
    onOpenInLibrary: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pureBlackBorder(AniListShapes.Card)
            .clip(AniListShapes.Card)
            .clickable(onClick = onClick),
        shape = AniListShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                AnimeCover.Book(
                    data = item.coverUrl,
                    contentDescription = anilistTitle(item.title),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (inLibrary) {
                        // ANK: 1-tap jump to local library entry when available
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(20.dp)
                                .then(
                                    if (onOpenInLibrary != null) {
                                        Modifier.clickable(onClick = onOpenInLibrary)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.BookmarkAdded,
                                    contentDescription = stringResource(MR.strings.in_library),
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (onAdd != null) {
                        Surface(
                            onClick = onAdd,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(20.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(AMR.strings.anilist_add_to_library),
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = anilistTitle(item.title),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildString {
                        append(myListProgressText(item))
                        if (item.format.isNotBlank()) append(" · ${anilistFormatLabel(item.format)}")
                        anilistScoreStar(item.score100).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.totalEpisodes > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val animProgress by animateFloatAsState(
                        targetValue = (item.progress.toFloat() / item.totalEpisodes).coerceIn(0f, 1f),
                        animationSpec = tween(300),
                        label = "my_list_row_progress_anim",
                    )
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ProgressStepper(
                    item = item,
                    saving = saving,
                    onBump = onBump,
                    buttonSize = 36.dp,
                    iconSize = 16.dp,
                )
                MyListOverflowMenu(
                    onEdit = onEdit,
                    onDelete = onDelete,
                    buttonSize = 36.dp,
                )
            }
        }
    }
}

@Composable
private fun MyListGridCard(
    item: MyListUiItem,
    inLibrary: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBump: (Int) -> Unit,
    onAdd: (() -> Unit)? = null,
    onOpenInLibrary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .pureBlackBorder(AniListShapes.Card)
            .clip(AniListShapes.Card)
            .clickable(onClick = onClick),
        shape = AniListShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                AnimeCover.Book(
                    data = item.coverUrl,
                    contentDescription = anilistTitle(item.title),
                    shape = AniListShapes.Card,
                    modifier = Modifier.fillMaxWidth(),
                )
                // ANK -->
                AniListScoreBadge(
                    score100 = item.score100,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
                when {
                    inLibrary && onOpenInLibrary != null -> AniListLibraryBadge(
                        modifier = Modifier.align(Alignment.TopStart),
                        onClick = onOpenInLibrary,
                    )
                    inLibrary -> AniListLibraryBadge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    )
                    onAdd != null -> AniListAddBadge(
                        onClick = onAdd,
                        contentDescription = stringResource(AMR.strings.anilist_add_to_library),
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
                AniListInfoBadge(
                    text = myListProgressText(item, short = true),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, end = 6.dp, bottom = if (item.totalEpisodes > 0) 9.dp else 6.dp),
                )
                if (item.totalEpisodes > 0) {
                    val animProgress by animateFloatAsState(
                        targetValue = (item.progress.toFloat() / item.totalEpisodes).coerceIn(0f, 1f),
                        animationSpec = tween(300),
                        label = "my_list_grid_progress_anim",
                    )
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.45f),
                        drawStopIndicator = {},
                    )
                }
                // ANK <--
            }
            Column(modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 2.dp)) {
                Text(
                    text = anilistTitle(item.title),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ProgressStepper(
                        item = item,
                        saving = saving,
                        onBump = onBump,
                        buttonSize = 30.dp,
                        iconSize = 14.dp,
                    )
                    MyListOverflowMenu(
                        onEdit = onEdit,
                        onDelete = onDelete,
                        buttonSize = 30.dp,
                    )
                }
            }
        }
    }
}
