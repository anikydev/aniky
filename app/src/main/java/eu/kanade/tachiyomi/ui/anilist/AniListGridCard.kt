package eu.kanade.tachiyomi.ui.anilist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.manga.components.AnimeCover
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.pureBlackBorder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

// ANK -->
data class AnilistLibraryLookup(
    val animeIdByRemoteId: Map<Long, Long> = emptyMap(),
    val animeIdByTitle: Map<String, Long> = emptyMap(),
) {
    fun findAnimeId(remoteId: Long?, vararg titles: String?): Long? {
        if (remoteId != null) {
            animeIdByRemoteId[remoteId]?.let { return it }
        }
        for (t in titles) {
            if (!t.isNullOrBlank()) {
                animeIdByTitle[t.lowercase(Locale.ROOT)]?.let { return it }
            }
        }
        return null
    }
}

internal object AnilistLibraryLookupHolder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun notifyTrackChanged() {
        refreshTrigger.value++
    }

    val flow: StateFlow<AnilistLibraryLookup> by lazy {
        val getLibraryManga = Injekt.get<GetLibraryManga>()
        val getTracks = Injekt.get<GetTracks>()
        combine(
            getLibraryManga.subscribe(),
            refreshTrigger,
        ) { libraryList, _ ->
            val libraryIds = libraryList.map { it.manga.id }.toHashSet()
            val byTitle = libraryList.associate {
                it.manga.title.lowercase(Locale.ROOT) to it.manga.id
            }
            val byRemoteId = getTracks.await()
                .asSequence()
                .filter { it.trackerId == TrackerManager.ANILIST && it.mangaId in libraryIds }
                .associate { it.remoteId to it.mangaId }
            AnilistLibraryLookup(
                animeIdByRemoteId = byRemoteId,
                animeIdByTitle = byTitle,
            )
        }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), AnilistLibraryLookup())
    }
}

@Composable
fun rememberAnilistLibraryLookup(): AnilistLibraryLookup {
    val lookup by AnilistLibraryLookupHolder.flow.collectAsState()
    return lookup
}
// ANK <--

/**
 * Shared grid card for AniList browse results: 2:3 cover with floating frosted score badge,
 * live user tracking status badge, 1-tap add to library button, and formatted metadata.
 */
@Composable
fun AniListGridCard(
    item: ALBrowseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inLibrary: Boolean = false,
    onAdd: (() -> Unit)? = null,
) {
    val title = anilistDisplayTitle(item.title.userPreferred, item.title.english, item.title.romaji, item.title.native)
    val lookup = rememberAnilistLibraryLookup()
    val libraryAnimeId = lookup.findAnimeId(
        item.id,
        item.title.userPreferred,
        item.title.english,
        item.title.romaji,
        item.title.native,
    )
    AniListGridCard(
        title = title,
        coverUrl = item.coverImage.extraLarge ?: item.coverImage.large,
        score = item.averageScore,
        format = item.format,
        episodes = item.episodes,
        status = item.mediaListEntry?.status,
        inLibrary = inLibrary || libraryAnimeId != null,
        libraryAnimeId = libraryAnimeId,
        onClick = onClick,
        onAdd = onAdd,
        modifier = modifier,
        resolveFromLibrary = false,
    )
}

/**
 * Shared grid card for AniList anime: 2:3 cover with at most three overlays - the viewer's list
 * status (top-start), score (bottom-start) and a single library action (bottom-end: open when the
 * anime is already in the library, add otherwise).
 */
@Composable
fun AniListGridCard(
    title: String,
    coverUrl: String?,
    score: Int? = null,
    format: String? = null,
    episodes: Int? = null,
    status: String? = null,
    inLibrary: Boolean = false,
    libraryAnimeId: Long? = null,
    mediaId: Long? = null,
    onClick: () -> Unit,
    onAdd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    resolveFromLibrary: Boolean = true,
) {
    val navigator = LocalNavigator.current
    val resolvedLibraryId = if (resolveFromLibrary && libraryAnimeId == null && !inLibrary) {
        rememberAnilistLibraryLookup().findAnimeId(mediaId, title)
    } else {
        libraryAnimeId
    }
    val isInLibrary = inLibrary || resolvedLibraryId != null
    val shape = AniListShapes.Card
    Column(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .pureBlackBorder(shape),
        ) {
            AnimeCover.Book(
                data = coverUrl,
                contentDescription = title,
                shape = shape,
                modifier = Modifier.fillMaxWidth(),
            )

            AniListStatusBadge(
                status = status,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )

            AniListScoreBadge(
                score100 = score,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )

            // One action corner: the in-library marker doubles as the "open in library" shortcut.
            when {
                isInLibrary && resolvedLibraryId != null -> AniListLibraryBadge(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    onClick = { navigator?.push(MangaScreen(resolvedLibraryId)) },
                )
                isInLibrary -> AniListLibraryBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
                onAdd != null -> AniListAddBadge(
                    onClick = onAdd,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        Text(
            text = title,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp),
        )

        val formatText = format?.takeIf { it.isNotBlank() }?.let { anilistFormatLabel(it) }
        val episodesText = episodes?.takeIf { it > 0 }
            ?.let { pluralStringResource(AMR.plurals.anilist_episodes_count, it, it) }
        val metaText = listOfNotNull(formatText, episodesText).joinToString(" • ")
        if (metaText.isNotEmpty()) {
            Text(
                text = metaText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            )
        }
    }
}
