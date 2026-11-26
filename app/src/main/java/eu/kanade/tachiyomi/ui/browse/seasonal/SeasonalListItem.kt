package eu.kanade.tachiyomi.ui.browse.seasonal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.AnimeCover
import eu.kanade.tachiyomi.ui.anilist.AniListShapes
import eu.kanade.tachiyomi.ui.anilist.anilistStatusColor
import eu.kanade.tachiyomi.ui.anilist.anilistStatusLabel
import eu.kanade.tachiyomi.ui.home.HomeItemData
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.pureBlackBorder
import java.util.Locale

private val AddToPlanningColor = Color(0xFF26A69A)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeasonalListItem(
    item: HomeItemData,
    onClick: () -> Unit,
    onStatusClick: () -> Unit = {},
    onAddToPlanning: () -> Unit = {},
    onAdd: () -> Unit = {},
    onOpenInLibrary: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .pureBlackBorder(AniListShapes.Card)
            .clip(AniListShapes.Card)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (item.inLibrary && onOpenInLibrary != null) onOpenInLibrary else onAdd,
            ),
        shape = AniListShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(74.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                AnimeCover.Book(
                    data = item.coverData ?: item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    enforceRatio = false,
                )
                if (item.inLibrary) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(18.dp)
                            .then(
                                if (onOpenInLibrary != null) {
                                    Modifier.clickable(onClick = onOpenInLibrary)
                                } else {
                                    Modifier
                                },
                            ),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = stringResource(AMR.strings.seasonal_in_library),
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
                if (item.inLibrary && item.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val studioText = item.studio?.takeIf { it.isNotBlank() } ?: item.subtitle
                    Text(
                        text = studioText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.rating.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "★ ${item.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB800),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (item.favourites > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatStatNumber(item.favourites),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.trackerStatus != null) {
                        val statusColor = anilistStatusColor(item.trackerStatus)
                        val statusLabel = anilistStatusLabel(item.trackerStatus)
                        Surface(
                            modifier = Modifier
                                .clip(AniListShapes.Badge)
                                .clickable(onClick = onStatusClick),
                            shape = AniListShapes.Badge,
                            color = statusColor.copy(alpha = 0.18f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(statusColor, CircleShape),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .clip(AniListShapes.Badge)
                                .clickable(onClick = onAddToPlanning),
                            shape = AniListShapes.Badge,
                            color = AddToPlanningColor.copy(alpha = 0.18f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = AddToPlanningColor,
                                    modifier = Modifier.size(11.dp),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = stringResource(AMR.strings.seasonal_add_to_planning),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AddToPlanningColor,
                                )
                            }
                        }
                    }
                }
            }

            if (item.inLibrary && onOpenInLibrary != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onOpenInLibrary,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdded,
                        contentDescription = stringResource(AMR.strings.anilist_open_in_library),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            } else if (!item.inLibrary) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(AMR.strings.seasonal_add),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun formatStatNumber(num: Long): String {
    return when {
        num >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", num / 1_000_000.0)
        num >= 10_000 -> String.format(Locale.ROOT, "%.1fk", num / 1_000.0)
        num > 0 -> String.format(Locale.ROOT, "%,d", num)
        else -> "-"
    }
}
