package eu.kanade.tachiyomi.ui.browse.seasonal

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.anilist.AniListInnerSpacer
import eu.kanade.tachiyomi.ui.anilist.AniListSectionHeader
import eu.kanade.tachiyomi.ui.browse.seasonal.model.AnimeThemeSong
import eu.kanade.tachiyomi.ui.browse.seasonal.model.ThemeType
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.pureBlackBorder

private enum class ThemeFilter {
    ALL,
    OPENING,
    ENDING,
}

@Composable
fun ThemeSongsSection(
    songs: List<AnimeThemeSong>,
    onSongClick: (AnimeThemeSong) -> Unit,
    onSongCopy: (AnimeThemeSong) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return

    val openings = remember(songs) { songs.filter { it.type == ThemeType.OP } }
    val endings = remember(songs) { songs.filter { it.type == ThemeType.ED } }
    val hasMultipleTypes = openings.isNotEmpty() && endings.isNotEmpty()

    var filter by remember { mutableStateOf(ThemeFilter.ALL) }
    var isExpanded by remember { mutableStateOf(false) }

    val displayedSongs = remember(songs, openings, endings, filter) {
        when (filter) {
            ThemeFilter.ALL -> songs
            ThemeFilter.OPENING -> openings
            ThemeFilter.ENDING -> endings
        }
    }

    val maxCollapsed = 5
    val visibleSongs = if (isExpanded || displayedSongs.size <= maxCollapsed) {
        displayedSongs
    } else {
        displayedSongs.take(maxCollapsed)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AniListSectionHeader(
            title = stringResource(AMR.strings.theme_songs),
        )

        if (hasMultipleTypes) {
            AniListInnerSpacer()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == ThemeFilter.ALL,
                    onClick = { filter = ThemeFilter.ALL },
                    label = { Text("${stringResource(AMR.strings.theme_all)} (${songs.size})") },
                    shape = RoundedCornerShape(20.dp),
                )
                FilterChip(
                    selected = filter == ThemeFilter.OPENING,
                    onClick = { filter = ThemeFilter.OPENING },
                    label = { Text("${stringResource(AMR.strings.theme_openings)} (${openings.size})") },
                    shape = RoundedCornerShape(20.dp),
                )
                FilterChip(
                    selected = filter == ThemeFilter.ENDING,
                    onClick = { filter = ThemeFilter.ENDING },
                    label = { Text("${stringResource(AMR.strings.theme_endings)} (${endings.size})") },
                    shape = RoundedCornerShape(20.dp),
                )
            }
        }

        AniListInnerSpacer()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visibleSongs.forEach { song ->
                ThemeSongItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onCopy = { onSongCopy(song) },
                )
            }

            if (displayedSongs.size > maxCollapsed) {
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    val icon = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                    val text = if (isExpanded) {
                        stringResource(MR.strings.manga_info_collapse)
                    } else {
                        stringResource(MR.strings.manga_info_expand) + " (${displayedSongs.size})"
                    }
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeSongItem(
    song: AnimeThemeSong,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodesLabel = song.episodes?.takeIf { it.isNotBlank() }?.let { ep ->
        ep.toIntOrNull()?.let { stringResource(AMR.strings.anilist_progress_episode, it) }
            ?: stringResource(AYMR.strings.display_mode_episode, ep)
    }
    val subtitle = remember(song.artist, episodesLabel) {
        listOfNotNull(
            song.artist?.takeIf { it.isNotBlank() },
            episodesLabel,
        ).joinToString(" • ")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .pureBlackBorder(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onCopy,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (song.type == ThemeType.OP) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                contentColor = if (song.type == ThemeType.OP) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
            ) {
                Text(
                    text = song.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (song.hasPreview) {
                FilledTonalIconButton(
                    onClick = onClick,
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(AMR.strings.theme_song_preview),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = stringResource(AMR.strings.action_play_on_youtube),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(MR.strings.action_copy_to_clipboard),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
