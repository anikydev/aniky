package eu.kanade.tachiyomi.ui.browse.seasonal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.anilist.AniListGridCard
import eu.kanade.tachiyomi.ui.anilist.ScrollToTopOnReselect
import eu.kanade.tachiyomi.ui.anilist.anilistFormatLabel
import eu.kanade.tachiyomi.ui.anilist.rememberAnilistLibraryLookup
import eu.kanade.tachiyomi.ui.home.HomeItemData
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.items as gridItems

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeasonalList(
    state: SeasonalScreenModel.State,
    contentPadding: PaddingValues,
    navigator: Navigator,
    onStatusClick: (HomeItemData) -> Unit,
    onAddToPlanning: (HomeItemData) -> Unit,
    onAddClick: (HomeItemData) -> Unit,
) {
    val libraryLookup = rememberAnilistLibraryLookup()

    // Sectioned only when no specific format is selected - with a format filter active every item is
    // that format, so a single header adds nothing.
    val grouped = if (state.format <= 0) {
        groupByFormat(
            state.seasonalList,
            otherTitle = stringResource(AMR.strings.seasonal_section_other),
        )
    } else {
        listOf(SeasonSection("", state.seasonalList))
    }

    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        grouped.forEach { section ->
            if (section.items.isEmpty()) return@forEach
            if (section.title.isNotEmpty()) {
                stickyHeader(key = "header_${section.title}") {
                    SeasonalFormatHeader(
                        title = anilistFormatLabel(section.title),
                        count = section.items.size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            items(section.items, key = { it.id }) { item ->
                val libraryAnimeId = libraryLookup.findAnimeId(item.id, item.title)
                val resolvedItem = if (libraryAnimeId != null && !item.inLibrary) item.copy(inLibrary = true) else item
                SeasonalListItem(
                    item = resolvedItem,
                    onClick = { navigator.push(SeasonalAnimeDetailScreen(resolvedItem.id)) },
                    onStatusClick = { onStatusClick(resolvedItem) },
                    onAddToPlanning = { onAddToPlanning(resolvedItem) },
                    onAdd = { onAddClick(resolvedItem) },
                    onOpenInLibrary = libraryAnimeId?.let { animeId ->
                        { navigator.push(MangaScreen(animeId)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun SeasonalFormatHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private data class SeasonSection(
    val title: String,
    val items: List<HomeItemData>,
)

/**
 * Splits the list into one section per AniList format, in the order [ANILIST_FORMATS] declares - the
 * same order the format filter chips use, so the sections read the way the filter does.
 *
 * Index 0 of that list is the "ALL" sentinel and is skipped. Anything the list does not name (MUSIC,
 * or an entry AniList returned with no format at all) lands in the catch-all so no counted title can
 * silently disappear.
 *
 * Note the separator swap: [ANILIST_FORMATS] holds the raw API values because they are also sent as
 * the `format:` query argument, but `AnilistApi` rewrites `_` to `-` when it builds the item, so
 * `TV_SHORT` arrives here as `TV-SHORT`. Matching without this normalisation dumped every TV short
 * into "Other".
 */
private fun groupByFormat(
    items: List<HomeItemData>,
    otherTitle: String,
): List<SeasonSection> {
    val byFormat = items.groupBy { it.format.toItemFormat() }
    val sections = ANILIST_FORMATS.drop(1).map { format ->
        SeasonSection(
            title = format,
            items = byFormat[format.toItemFormat()].orEmpty(),
        )
    }
    val other = items.filterNot { it.format.toItemFormat() in KNOWN_ITEM_FORMATS }
    return sections + SeasonSection(otherTitle, other)
}

private fun String.toItemFormat(): String = replace("_", "-")

private val KNOWN_ITEM_FORMATS = ANILIST_FORMATS.drop(1).mapTo(mutableSetOf()) { it.toItemFormat() }

@Composable
internal fun SeasonalGrid(
    state: SeasonalScreenModel.State,
    contentPadding: PaddingValues,
    navigator: Navigator,
    onAddClick: (HomeItemData) -> Unit,
) {
    val libraryLookup = rememberAnilistLibraryLookup()
    val grouped = if (state.format <= 0) {
        groupByFormat(
            state.seasonalList,
            otherTitle = stringResource(AMR.strings.seasonal_section_other),
        )
    } else {
        listOf(SeasonSection("", state.seasonalList))
    }

    val gridState = rememberLazyGridState()
    ScrollToTopOnReselect(gridState)
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(state.gridColumns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        grouped.forEach { section ->
            if (section.items.isEmpty()) return@forEach
            if (section.title.isNotEmpty()) {
                item(
                    key = "grid_header_${section.title}",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SeasonalFormatHeader(
                        title = anilistFormatLabel(section.title),
                        count = section.items.size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                    )
                }
            }
            gridItems(section.items, key = { it.id }) { item ->
                val libraryAnimeId = libraryLookup.findAnimeId(item.id, item.title)
                val resolvedItem = if (libraryAnimeId != null && !item.inLibrary) item.copy(inLibrary = true) else item
                val score100 = resolvedItem.rating.toDoubleOrNull()?.let {
                    if (it <= 10.0) (it * 10).roundToInt() else it.roundToInt()
                }
                AniListGridCard(
                    title = resolvedItem.title,
                    coverUrl = resolvedItem.coverUrl,
                    score = score100,
                    format = resolvedItem.format,
                    status = resolvedItem.trackerStatus,
                    inLibrary = resolvedItem.inLibrary,
                    libraryAnimeId = libraryAnimeId,
                    mediaId = resolvedItem.id,
                    onClick = { navigator.push(SeasonalAnimeDetailScreen(resolvedItem.id)) },
                    onAdd = { onAddClick(resolvedItem) },
                    modifier = Modifier.fillMaxWidth(),
                    resolveFromLibrary = false,
                )
            }
        }
    }
}

@Composable
internal fun ErrorState(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(AMR.strings.seasonal_load_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(MR.strings.action_retry))
        }
    }
}

@Composable
internal fun NoResultsState(
    query: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(AMR.strings.seasonal_no_results, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(AMR.strings.seasonal_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
