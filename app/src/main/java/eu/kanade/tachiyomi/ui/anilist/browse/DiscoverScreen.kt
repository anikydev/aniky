package eu.kanade.tachiyomi.ui.anilist.browse

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.manga.components.AnimeCover
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListChip
import eu.kanade.tachiyomi.ui.anilist.AniListGridCard
import eu.kanade.tachiyomi.ui.anilist.AniListInfoBadge
import eu.kanade.tachiyomi.ui.anilist.AniListLoginRequired
import eu.kanade.tachiyomi.ui.anilist.AniListScoreBadge
import eu.kanade.tachiyomi.ui.anilist.AniListSectionHeader
import eu.kanade.tachiyomi.ui.anilist.AniListShapes
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.AniListStatusBadge
import eu.kanade.tachiyomi.ui.anilist.ScrollToTopOnReselect
import eu.kanade.tachiyomi.ui.anilist.anilistDisplayTitle
import eu.kanade.tachiyomi.ui.anilist.anilistFormatLabel
import eu.kanade.tachiyomi.ui.anilist.anilistPickTitle
import eu.kanade.tachiyomi.ui.anilist.rememberAniListSkeletonColor
import eu.kanade.tachiyomi.ui.anilist.rememberAnilistLibraryLookup
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.util.pureBlackBorder

@Composable
fun discoverTab(screenModel: DiscoverScreenModel): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    return TabContent(
        titleRes = AMR.strings.anilist_tab_discover,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.label_stats),
                icon = Icons.Outlined.BarChart,
                onClick = { navigator.push(StatsScreen()) },
            ),
        ),
        content = { padding, _ ->
            Box(modifier = Modifier.padding(padding)) {
                DiscoverContent(screenModel = screenModel)
            }
        },
    )
}

@Composable
private fun DiscoverContent(screenModel: DiscoverScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }
    val openDetail: (Long) -> Unit = { navigator.push(SeasonalAnimeDetailScreen(it)) }
    val onAddAnime: (ALBrowseItem) -> Unit = { item ->
        val title = anilistPickTitle(
            item.title.userPreferred,
            item.title.english,
            item.title.romaji,
            item.title.native,
        )
        addTarget = AniListAddTarget(
            mediaId = item.id,
            title = title,
            totalEpisodes = item.episodes,
        )
    }

    addTarget?.let { target ->
        AniListSourceSearchSheet(
            target = target,
            navigator = navigator,
            onDismissRequest = { addTarget = null },
        )
    }

    when {
        state.isLoading -> DiscoverSkeleton()
        state.notLoggedIn -> AniListLoginRequired(onRetry = screenModel::refresh)
        state.hasError -> EmptyScreen(
            stringRes = AMR.strings.anilist_browse_error,
            actions = persistentListOf(
                EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::refresh),
            ),
        )
        state.isEmpty -> EmptyScreen(
            stringRes = AMR.strings.anilist_browse_empty,
            actions = persistentListOf(
                EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::refresh),
            ),
        )
        else -> PullRefresh(
            refreshing = state.isRefreshing,
            enabled = true,
            onRefresh = screenModel::refresh,
        ) {
            val scrollState = rememberScrollState()
            ScrollToTopOnReselect(scrollState)
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                val spotlightItems = state.trending.take(5)
                if (spotlightItems.isNotEmpty()) {
                    SpotlightHeroPager(
                        items = spotlightItems,
                        onClick = openDetail,
                        onAdd = onAddAnime,
                        onOpenInLibrary = { navigator.push(MangaScreen(it)) },
                    )
                }

                GenrePillsRow(
                    onSelectGenre = { genre ->
                        navigator.push(AdvancedSearchScreen(initialSort = "TRENDING_DESC", initialGenre = genre))
                    },
                )

                Carousel(
                    title = stringResource(AMR.strings.anilist_sort_trending),
                    items = state.trending,
                    onClick = openDetail,
                    onAdd = onAddAnime,
                    onSeeAll = { navigator.push(AdvancedSearchScreen("TRENDING_DESC")) },
                )
                Carousel(
                    title = stringResource(AMR.strings.anilist_sort_popular),
                    items = state.popular,
                    onClick = openDetail,
                    onAdd = onAddAnime,
                    onSeeAll = { navigator.push(AdvancedSearchScreen("POPULARITY_DESC")) },
                )
                Carousel(
                    title = stringResource(AMR.strings.anilist_sort_top_rated),
                    items = state.topRated,
                    onClick = openDetail,
                    onAdd = onAddAnime,
                    onSeeAll = { navigator.push(AdvancedSearchScreen("SCORE_DESC")) },
                )
                Carousel(
                    title = stringResource(AMR.strings.anilist_sort_favourites),
                    items = state.favourites,
                    onClick = openDetail,
                    onAdd = onAddAnime,
                    onSeeAll = { navigator.push(AdvancedSearchScreen("FAVOURITES_DESC")) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SpotlightHeroPager(
    items: List<ALBrowseItem>,
    onClick: (Long) -> Unit,
    onAdd: (ALBrowseItem) -> Unit,
    onOpenInLibrary: (Long) -> Unit,
) {
    val pagerState = rememberPagerState { items.size }
    val lookup = rememberAnilistLibraryLookup()

    // ANK --> Don't fight the user: hold the auto-advance while a finger is dragging the pager.
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    if (items.size > 1) {
        LaunchedEffect(pagerState.settledPage, items.size, isDragged) {
            if (isDragged) return@LaunchedEffect
            delay(6000L)
            val nextPage = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(nextPage)
        }
    }
    // ANK <--

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val item = items[page]
            val libraryAnimeId = lookup.findAnimeId(
                item.id,
                item.title.userPreferred,
                item.title.english,
                item.title.romaji,
                item.title.native,
            )
            SpotlightHero(
                item = item,
                rank = page + 1,
                libraryAnimeId = libraryAnimeId,
                onClick = { onClick(item.id) },
                onAdd = { onAdd(item) },
                onOpenInLibrary = onOpenInLibrary,
            )
        }

        if (items.size > 1) {
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(items.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 18.dp else 6.dp,
                        label = "spotlight_dot_width",
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightHero(
    item: ALBrowseItem,
    rank: Int = 1,
    libraryAnimeId: Long? = null,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    onOpenInLibrary: (Long) -> Unit = {},
) {
    val title = anilistDisplayTitle(item.title.userPreferred, item.title.english, item.title.romaji, item.title.native)
    val shape = AniListShapes.Hero
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(230.dp)
            .clip(shape)
            .pureBlackBorder(shape)
            .clickable(onClick = onClick),
        shape = shape,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (MaterialTheme.colorScheme.background == Color.Black) 0.dp else 3.dp,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimeCover.Panorama(
                data = item.bannerImage ?: item.coverImage.extraLarge ?: item.coverImage.large,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                enforceRatio = false,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.92f),
                            ),
                            startY = 40f,
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = AniListShapes.Badge,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "#$rank ${stringResource(AMR.strings.anilist_sort_trending).uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    AniListStatusBadge(status = item.mediaListEntry?.status)
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AniListScoreBadge(score100 = item.averageScore)
                        item.format?.let { AniListInfoBadge(text = anilistFormatLabel(it)) }
                    }

                    if (libraryAnimeId != null) {
                        Surface(
                            onClick = { onOpenInLibrary(libraryAnimeId) },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = AniListShapes.Pill,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = stringResource(AMR.strings.anilist_open_in_library),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(MR.strings.in_library),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else {
                        Surface(
                            onClick = onAdd,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = AniListShapes.Pill,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(AMR.strings.anilist_add_to_library),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(AMR.strings.anilist_add_to_library),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** [apiName] is the exact AniList genre used for the query; the label is localized separately. */
private data class GenrePill(val apiName: String, val label: StringResource)

private val POPULAR_GENRES = listOf(
    GenrePill("Action", AMR.strings.anilist_genre_action),
    GenrePill("Adventure", AMR.strings.anilist_genre_adventure),
    GenrePill("Comedy", AMR.strings.anilist_genre_comedy),
    GenrePill("Drama", AMR.strings.anilist_genre_drama),
    GenrePill("Fantasy", AMR.strings.anilist_genre_fantasy),
    GenrePill("Romance", AMR.strings.anilist_genre_romance),
    GenrePill("Sci-Fi", AMR.strings.anilist_genre_sci_fi),
    GenrePill("Slice of Life", AMR.strings.anilist_genre_slice_of_life),
    GenrePill("Supernatural", AMR.strings.anilist_genre_supernatural),
    GenrePill("Thriller", AMR.strings.anilist_genre_thriller),
    GenrePill("Mystery", AMR.strings.anilist_genre_mystery),
    GenrePill("Sports", AMR.strings.anilist_genre_sports),
)

@Composable
private fun GenrePillsRow(
    onSelectGenre: (String) -> Unit,
) {
    Column {
        Text(
            text = stringResource(AMR.strings.anilist_explore_genres),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(POPULAR_GENRES, key = { it.apiName }) { genre ->
                // Same chip as the detail screen's genre chips (navigating, not toggling).
                AniListChip(
                    text = stringResource(genre.label),
                    modifier = Modifier.clickable { onSelectGenre(genre.apiName) },
                )
            }
        }
    }
}

@Composable
private fun Carousel(
    title: String,
    items: List<ALBrowseItem>,
    onClick: (Long) -> Unit,
    onAdd: (ALBrowseItem) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (items.isEmpty()) return
    AniListSectionHeader(
        title = title,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 8.dp),
        onSeeAll = onSeeAll,
    )
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            AniListGridCard(
                item = item,
                onClick = { onClick(item.id) },
                onAdd = { onAdd(item) },
                modifier = Modifier.width(115.dp),
            )
        }
    }
}

@Composable
private fun DiscoverSkeleton() {
    val placeholderColor = rememberAniListSkeletonColor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        // Hero skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(230.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(placeholderColor),
        )

        // Genre pills skeleton
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(6) {
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(placeholderColor),
                )
            }
        }

        // Carousel skeletons
        repeat(2) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .size(width = 120.dp, height = 20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholderColor),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(4) {
                    Column(modifier = Modifier.width(115.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(placeholderColor),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(placeholderColor),
                        )
                    }
                }
            }
        }
    }
}
