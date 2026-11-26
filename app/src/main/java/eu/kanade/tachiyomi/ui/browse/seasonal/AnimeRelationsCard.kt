package eu.kanade.tachiyomi.ui.browse.seasonal

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.AnimeCover
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeDetailsRelationEdge
import eu.kanade.tachiyomi.ui.anilist.anilistDisplayTitle
import eu.kanade.tachiyomi.ui.anilist.anilistScoreText
import eu.kanade.tachiyomi.ui.anilist.anilistStatusColor
import eu.kanade.tachiyomi.ui.anilist.anilistStatusLabel
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.pureBlackBorder

// AniList MediaFormat values that belong to type: ANIME; anything else (MANGA, NOVEL, ONE_SHOT)
// cannot be opened by SeasonalAnimeDetailScreen.
val ANIME_MEDIA_FORMATS = setOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC")

// Most-useful-first ordering for relation cards; types not listed keep their API order after these.
private val RELATION_TYPE_ORDER = listOf(
    "CURRENT",
    "SEQUEL",
    "PREQUEL",
    "SIDE_STORY",
    "PARENT",
    "SPIN_OFF",
)

/**
 * Drops duplicates and non-anime edge nodes, and orders the survivors sequel-first (stable sort),
 * so "what to watch next" is the first card on both the info screen and the seasonal detail screen.
 * Nodes whose format isn't ANIME (manga/novel/one-shot) are pruned outright: the details query only
 * resolves type: ANIME, so they could never be opened anyway and would only show as dead cards.
 */
fun sortAndDedupeRelations(edges: List<ALAnimeDetailsRelationEdge>): List<ALAnimeDetailsRelationEdge> {
    return edges
        .filter { it.relationType.equals("CURRENT", ignoreCase = true) || it.node.format in ANIME_MEDIA_FORMATS }
        .distinctBy { it.node.id }
        .sortedBy { edge ->
            RELATION_TYPE_ORDER.indexOf(edge.relationType).takeIf { it >= 0 } ?: RELATION_TYPE_ORDER.size
        }
}

@Composable
fun AnimeRelationsRow(
    relations: List<ALAnimeDetailsRelationEdge>,
    inLibraryIds: Set<Long>,
    onRelationClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onAddClick: ((String) -> Unit)? = null,
) {
    if (relations.isEmpty()) return
    val sortedRelations = remember(relations) { sortAndDedupeRelations(relations) }
    if (sortedRelations.isEmpty()) return
    val hasOtherRelations = remember(sortedRelations) {
        sortedRelations.any { !it.relationType.equals("CURRENT", ignoreCase = true) }
    }
    // Collapsed by default; one tap on the header expands it
    val (expanded, onExpanded) = rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "relations_chevron")
    Column(modifier = modifier.fillMaxWidth()) {
        // One-tap show/hide: the header stays visible either way
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { onExpanded(!expanded) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (hasOtherRelations) {
                    stringResource(AMR.strings.relations)
                } else {
                    stringResource(AMR.strings.pref_category_anilist)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (hasOtherRelations) {
                val otherRelationsCount = sortedRelations.count { !it.relationType.equals("CURRENT", ignoreCase = true) }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = otherRelationsCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        Box(modifier = Modifier.animateContentSize()) {
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sortedRelations) { edge ->
                        // sortAndDedupeRelations only keeps type: ANIME, so every card opens a
                        // detail screen the SeasonalAnimeDetailScreen can actually resolve.
                        val title = anilistDisplayTitle(
                            edge.node.title.userPreferred.orEmpty(),
                            edge.node.title.english,
                            edge.node.title.romaji,
                            edge.node.title.native,
                        )
                        RelationCard(
                            edge = edge,
                            onClick = { onRelationClick(edge.node.id) },
                            inLibrary = edge.node.id in inLibraryIds,
                            onAdd = if (onAddClick != null) {
                                { onAddClick(title) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RelationCard(
    edge: ALAnimeDetailsRelationEdge,
    onClick: (() -> Unit)?,
    inLibrary: Boolean = false,
    onAdd: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    val title = anilistDisplayTitle(
        edge.node.title.userPreferred.orEmpty(),
        edge.node.title.english,
        edge.node.title.romaji,
        edge.node.title.native,
    )
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(shape)
            .then(clickableModifier)
            .padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .clip(shape)
                .pureBlackBorder(shape),
        ) {
            AnimeCover.Book(
                data = edge.node.coverImage?.large,
                contentDescription = title,
                shape = shape,
                modifier = Modifier.fillMaxWidth(),
            )

            // Top-End: Score badge
            edge.node.averageScore?.takeIf { it > 0 }?.let { s ->
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(10.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = anilistScoreText(s),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Top-Start: In-Library bookmark AND/OR User Tracking Status pill
            val relStatus = edge.node.mediaListEntry?.status
            if (inLibrary || relStatus != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (inLibrary) {
                        Surface(
                            modifier = Modifier.size(20.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = stringResource(MR.strings.in_library),
                                modifier = Modifier.padding(3.dp),
                            )
                        }
                    }
                    if (relStatus != null) {
                        val statusColor = anilistStatusColor(relStatus)
                        val statusLabel = anilistStatusLabel(relStatus)
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(statusColor, CircleShape),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom-End: 1-Tap Add to Library button
            if (onAdd != null && !inLibrary) {
                Surface(
                    onClick = onAdd,
                    color = Color.Black.copy(alpha = 0.68f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(AMR.strings.seasonal_add),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        edge.relationType?.let { type ->
            Spacer(modifier = Modifier.height(3.dp))
            val isCurrent = type.equals("CURRENT", ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = if (isCurrent) {
                        stringResource(AMR.strings.anilist_relation_current)
                    } else {
                        type.lowercase().replace('_', ' ')
                            .replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
