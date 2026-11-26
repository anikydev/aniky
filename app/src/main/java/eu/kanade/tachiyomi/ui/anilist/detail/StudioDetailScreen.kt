package eu.kanade.tachiyomi.ui.anilist.detail

import android.content.Intent
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListGridCard
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.anilistDisplayTitle
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import tachiyomi.presentation.core.util.pureBlackBorder

class StudioDetailScreen(
    private val studioId: Long? = null,
    private val studioName: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val context = LocalContext.current
        val screenKey = remember(studioId, studioName) { "${studioId ?: 0L}:$studioName" }
        val screenModel = rememberScreenModel(screenKey) {
            StudioDetailScreenModel(studioId, studioName)
        }
        val state by screenModel.state.collectAsState()
        var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }

        val gridState = rememberLazyGridState()
        val toolbarAlpha by remember {
            derivedStateOf {
                when {
                    gridState.firstVisibleItemIndex > 0 -> 1f
                    else -> (gridState.firstVisibleItemScrollOffset / 180f).coerceIn(0f, 1f)
                }
            }
        }
        val titleAlpha by remember {
            derivedStateOf {
                when {
                    gridState.firstVisibleItemIndex > 0 -> 1f
                    else -> ((gridState.firstVisibleItemScrollOffset - 90f) / 90f).coerceIn(0f, 1f)
                }
            }
        }

        val resolvedName = state.details?.name?.takeIf { it.isNotBlank() }
            ?: studioName.ifBlank { stringResource(AMR.strings.studio_detail_title) }
        val shareUrl = state.details?.siteUrl
            ?: state.details?.id?.takeIf { it > 0L }?.let { "https://anilist.co/studio/$it" }

        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        AppBarTitle(
                            title = resolvedName,
                            modifier = Modifier.alpha(titleAlpha),
                        )
                    },
                    backgroundColor = MaterialTheme.colorScheme
                        .surfaceColorAtElevation(3.dp)
                        .copy(alpha = toolbarAlpha),
                    navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    actions = {
                        if (!shareUrl.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, resolvedName))
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = stringResource(MR.strings.action_share),
                                )
                            }
                        }
                    },
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))
                state.error || state.details == null -> EmptyScreen(
                    modifier = Modifier.padding(contentPadding),
                    stringRes = AMR.strings.error,
                    actions = persistentListOf(
                        EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::retry),
                    ),
                )
                else -> {
                    val details = state.details!!
                    val totalWorks = details.media?.pageInfo?.total?.takeIf { it > 0 } ?: state.items.size

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding + PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(
                            key = "studio_hero",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pureBlackBorder(RoundedCornerShape(16.dp)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(64.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.MovieFilter,
                                                contentDescription = null,
                                                modifier = Modifier.size(30.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = resolvedName,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (details.isAnimationStudio) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                ) {
                                                    Text(
                                                        text = stringResource(AMR.strings.studio_main_badge),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    )
                                                }
                                            }
                                            if (totalWorks > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                ) {
                                                    Text(
                                                        text = stringResource(AMR.strings.studio_anime_count, totalWorks),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    )
                                                }
                                            }
                                        }
                                        details.favourites?.takeIf { it > 0 }?.let { favCount ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Favorite,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                    Text(
                                                        text = stringResource(
                                                            AMR.strings.studio_favorites_count,
                                                            "%,d".format(favCount),
                                                        ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(
                            key = "studio_sort_bar",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StudioDetailScreenModel.StudioSort.entries.forEach { option ->
                                    item(key = option.name) {
                                        val label = when (option) {
                                            StudioDetailScreenModel.StudioSort.POPULARITY ->
                                                stringResource(AMR.strings.studio_sort_popularity)
                                            StudioDetailScreenModel.StudioSort.NEWEST ->
                                                stringResource(AMR.strings.studio_sort_newest)
                                            StudioDetailScreenModel.StudioSort.SCORE ->
                                                stringResource(AMR.strings.studio_sort_score)
                                        }
                                        val selected = state.sort == option
                                        FilterChip(
                                            selected = selected,
                                            onClick = { screenModel.setSort(option) },
                                            label = {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(32.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (state.items.isEmpty() && !state.isLoadingMore) {
                            item(
                                key = "studio_empty",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(AMR.strings.studio_no_media),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = state.items,
                                key = { _, item -> item.id },
                            ) { index, item ->
                                if (index >= state.items.size - 6 && state.hasNextPage && !state.isLoadingMore) {
                                    LaunchedEffect(Triple(state.currentPage, state.items.size, state.hasNextPage)) {
                                        screenModel.loadMore()
                                    }
                                }
                                val title = anilistDisplayTitle(
                                    item.title.userPreferred,
                                    item.title.english,
                                    item.title.romaji,
                                    item.title.native,
                                )
                                Column {
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
                                    item.seasonYear?.let { year ->
                                        Text(
                                            text = year.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (state.isLoadingMore) {
                            item(
                                key = "studio_loading_more",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
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
