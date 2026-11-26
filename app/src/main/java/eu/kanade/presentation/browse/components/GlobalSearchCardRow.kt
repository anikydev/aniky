package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.pureBlackBorder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun GlobalSearchCardRow(
    titles: List<Manga>,
    getManga: @Composable (Manga) -> State<Manga>,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
    // KMK -->
    selection: List<Manga>,
    // KMK <--
    // ANK -->
    onToggleFavorite: ((Manga) -> Unit)? = null,
    // ANK <--
) {
    if (titles.isEmpty()) {
        EmptyResultItem()
        return
    }

    val usePanoramaCover by remember { Injekt.get<UiPreferences>().usePanoramaCoverFlow() }.collectAsState()

    LazyRow(
        // ANK -->
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.padding.medium,
            vertical = MaterialTheme.padding.small,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        // ANK <--
    ) {
        items(titles) {
            val title by getManga(it)
            val isSelected = selection.fastAny { selected -> selected.id == title.id }
            MangaItem(
                title = title.title,
                cover = title.asMangaCover(),
                isFavorite = title.favorite,
                onClick = { onClick(title) },
                onLongClick = { onLongClick(title) },
                // KMK -->
                isSelected = isSelected,
                usePanoramaCover = usePanoramaCover,
                // KMK <--
                // ANK -->
                onQuickAdd = if (onToggleFavorite != null && !title.favorite && selection.isEmpty()) {
                    { onToggleFavorite(title) }
                } else {
                    null
                },
                // ANK <--
            )
        }
    }
}

@Composable
internal fun MangaItem(
    title: String,
    cover: MangaCover,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    // KMK -->
    isSelected: Boolean = false,
    usePanoramaCover: Boolean? = null,
    // KMK <--
    // ANK -->
    onQuickAdd: (() -> Unit)? = null,
    // ANK <--
) {
    // KMK -->
    val panoramaCover = usePanoramaCover ?: Injekt.get<UiPreferences>().usePanoramaCoverFlow().collectAsState().value
    val coverRatio = remember { mutableFloatStateOf(1f) }
    // KMK <--
    Box(
        modifier = Modifier.width(
            // KMK -->
            // ANK: Widened standard card from 96.dp to 112.dp for cleaner poster proportions
            if (panoramaCover && coverRatio.floatValue <= RatioSwitchToPanorama) 220.dp else 112.dp,
            // KMK <--
        ),
    ) {
        MangaComfortableGridItem(
            title = title,
            titleMaxLines = 2,
            coverData = cover,
            coverBadgeStart = {
                InLibraryBadge(enabled = isFavorite)
            },
            // KMK -->
            isSelected = isSelected,
            coverRatio = coverRatio,
            usePanoramaCover = panoramaCover,
            fitToPanoramaCover = true,
            // KMK <--
            coverAlpha = if (isFavorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        // ANK -->
        if (onQuickAdd != null) {
            Surface(
                onClick = onQuickAdd,
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .pureBlackBorder(RoundedCornerShape(7.dp)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(MR.strings.add_to_library),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        // ANK <--
    }
}

@Composable
internal fun EmptyResultItem() {
    Text(
        text = stringResource(MR.strings.no_results_found),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
    )
}
