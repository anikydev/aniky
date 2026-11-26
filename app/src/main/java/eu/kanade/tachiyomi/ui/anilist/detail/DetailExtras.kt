package eu.kanade.tachiyomi.ui.anilist.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.components.AnimeCover
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeDetails
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListChip
import eu.kanade.tachiyomi.ui.anilist.AniListGridCard
import eu.kanade.tachiyomi.ui.anilist.AniListInnerSpacer
import eu.kanade.tachiyomi.ui.anilist.AniListSectionHeader
import eu.kanade.tachiyomi.ui.anilist.AniListSectionSpacer
import eu.kanade.tachiyomi.ui.anilist.anilistDisplayTitle
import eu.kanade.tachiyomi.ui.anilist.browse.AdvancedSearchScreen
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.pureBlackBorder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 3 extras rendered at the bottom of the AniList detail content:
 * staff, recommendations, tags, favourite toggle and the viewer's own entry.
 * Called from [SeasonalAnimeDetailScreen] via [AniListDetailExtras].
 */
@Composable
fun AniListDetailExtras(
    details: ALAnimeDetails,
    onAddAnime: ((AniListAddTarget) -> Unit)? = null,
) {
    val navigator = LocalNavigator.currentOrThrow
    Column(modifier = Modifier.fillMaxWidth()) {
        // Tags (non-spoiler first, top 12) — informational chips, styled like the genre chips
        // on the detail screen rather than chips with a no-op click that ripples but does nothing.
        val tags = details.tags.sortedBy { it.isMediaSpoiler }.take(12)
        if (tags.isNotEmpty()) {
            AniListSectionSpacer()
            AniListSectionHeader(stringResource(AMR.strings.anilist_tags))
            AniListInnerSpacer()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags, key = { it.name }) { tag ->
                    AniListChip(
                        text = if (tag.isMediaSpoiler) "⚠ ${tag.name}" else tag.name,
                        modifier = Modifier.clickable {
                            navigator.push(AdvancedSearchScreen(initialTag = tag.name))
                        },
                    )
                }
            }
        }

        // Staff
        val staff = details.staff?.edges?.filter { it.node != null }?.take(12).orEmpty()
        if (staff.isNotEmpty()) {
            AniListSectionSpacer()
            AniListSectionHeader(stringResource(AMR.strings.anilist_staff))
            AniListInnerSpacer()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Index-based keys: the same person can appear twice with different roles.
                itemsIndexed(staff) { _, edge ->
                    val node = edge.node ?: return@itemsIndexed
                    val staffName = node.name?.userPreferred ?: node.name?.full.orEmpty()
                    Surface(
                        onClick = { navigator.push(StaffDetailScreen(node.id)) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .width(88.dp)
                            .pureBlackBorder(RoundedCornerShape(12.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AnimeCover.Square(
                                data = node.image?.large ?: node.image?.medium,
                                contentDescription = staffName,
                                modifier = Modifier.size(60.dp),
                                shape = CircleShape,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = staffName,
                                maxLines = 2,
                                minLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            edge.role?.let { role ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = role,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recommendations
        // distinctBy id before keying the LazyRow on it: AniList can return the same media
        // recommended twice, and missing ids default to 0L, either of which would otherwise
        // collide and crash the row with a duplicate key.
        val recNodes = details.recommendations?.nodes
            ?.filter { it.mediaRecommendation != null }
            ?.distinctBy { it.mediaRecommendation!!.id }
            ?.take(10)
            .orEmpty()
        if (recNodes.isNotEmpty()) {
            AniListSectionSpacer()
            AniListSectionHeader(stringResource(AMR.strings.anilist_recommendations))
            AniListInnerSpacer()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recNodes, key = { it.mediaRecommendation!!.id }) { recNode ->
                    val rec = recNode.mediaRecommendation!!
                    val recTitle = anilistDisplayTitle(rec.title?.userPreferred ?: "", rec.title?.english, rec.title?.romaji, rec.title?.native)
                    Column(modifier = Modifier.width(115.dp)) {
                        AniListGridCard(
                            title = recTitle,
                            coverUrl = rec.coverImage?.large,
                            score = rec.averageScore,
                            format = rec.format,
                            episodes = rec.episodes,
                            status = rec.mediaListEntry?.status,
                            mediaId = rec.id,
                            onClick = { navigator.push(SeasonalAnimeDetailScreen(rec.id)) },
                            onAdd = {
                                if (onAddAnime != null) {
                                    onAddAnime(
                                        AniListAddTarget(
                                            mediaId = rec.id,
                                            title = recTitle,
                                            totalEpisodes = rec.episodes,
                                        ),
                                    )
                                } else {
                                    navigator.push(GlobalSearchScreen(recTitle))
                                }
                            },
                        )
                        val rating = recNode.rating
                        if (rating != null && rating > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ThumbUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = stringResource(AMR.strings.anilist_recommendation_votes, rating),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavouriteButton(mediaId: Long, isFavourite: Boolean) {
    val scope = rememberCoroutineScope()
    val trackerManager: TrackerManager = remember { Injekt.get() }
    var fav by remember(mediaId) { mutableStateOf(isFavourite) }
    var loaded by remember(mediaId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Media.isFavourite (the seeded value) is unreliable, so confirm against the favourites list -
    // the source of truth - before the heart becomes tappable. Gating taps on `loaded` means a tap
    // can only act on the confirmed state: the toggle never flips from a wrong seed, and this
    // confirming read can never resolve after (and clobber) a toggle.
    LaunchedEffect(mediaId) {
        // Only enable the heart once the favourite state is actually confirmed (non-null). A null
        // means the favourites list couldn't be loaded, so the state stays unknown and the button
        // stays disabled rather than letting a tap act on the unreliable seed and invert the state.
        val confirmed = runCatching { trackerManager.aniList.isAnimeFavourite(mediaId) }.getOrNull()
        if (confirmed != null) {
            fav = confirmed
            loaded = true
        }
    }
    IconButton(
        onClick = {
            // Set busy synchronously and guard re-entry here: doing it inside the coroutine let a
            // fast second tap fire a second toggle before enabled=!busy took effect.
            if (!busy) {
                busy = true
                // toggleFavouriteAnime runs the write NonCancellable and returns the resulting
                // state, so navigating back can't drop it and the heart reflects the real value
                // rather than a blind negation.
                scope.launch(Dispatchers.IO) {
                    try {
                        fav = trackerManager.aniList.toggleFavouriteAnime(mediaId)
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "AniList: toggle favourite failed for $mediaId" }
                    } finally {
                        busy = false
                    }
                }
            }
        },
        enabled = !busy && loaded,
    ) {
        Icon(
            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(if (fav) AMR.strings.anilist_unfavourite else AMR.strings.anilist_favourite),
            tint = if (fav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
