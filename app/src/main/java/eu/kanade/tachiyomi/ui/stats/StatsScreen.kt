package eu.kanade.tachiyomi.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeStats
import eu.kanade.tachiyomi.ui.anilist.AniListLoginRequired
import eu.kanade.tachiyomi.ui.anilist.anilistStatusColor
import eu.kanade.tachiyomi.ui.anilist.anilistStatusStringRes
import eu.kanade.tachiyomi.ui.anilist.browse.AdvancedSearchScreen
import eu.kanade.tachiyomi.ui.anilist.detail.StudioDetailScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.pureBlackBorder

class StatsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { StatsScreenModel() }
        val state by screenModel.state.collectAsState()
        val handleBack = LocalBackPress.current

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_stats),
                    navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            PullRefresh(
                refreshing = state.isRefreshing,
                onRefresh = screenModel::refresh,
                enabled = !state.isLoading,
                indicatorPadding = contentPadding,
            ) {
                when {
                    state.isLoading -> {
                        LoadingScreen(modifier = Modifier.padding(contentPadding))
                    }
                    state.notLoggedIn -> {
                        AniListLoginRequired(
                            onRetry = screenModel::refresh,
                            modifier = Modifier.padding(contentPadding),
                            stringRes = AMR.strings.stats_login_required,
                        )
                    }
                    state.hasError -> {
                        EmptyScreen(
                            modifier = Modifier.padding(contentPadding),
                            stringRes = AMR.strings.stats_error,
                            actions = persistentListOf(
                                EmptyScreenAction(
                                    stringRes = MR.strings.action_retry,
                                    icon = Icons.Outlined.Refresh,
                                    onClick = screenModel::refresh,
                                ),
                            ),
                        )
                    }
                    else -> {
                        StatsContent(
                            modifier = Modifier.padding(contentPadding),
                            stats = state.stats ?: ALAnimeStats(),
                            username = state.username,
                            avatarUrl = state.avatarUrl,
                            onOpenStatus = { status, title ->
                                navigator.push(StatusAnimeScreen(status, title))
                            },
                            onOpenGenre = { genre ->
                                navigator.push(AdvancedSearchScreen(initialGenre = genre))
                            },
                            onOpenStudio = { studioId, studioName ->
                                navigator.push(StudioDetailScreen(studioId = studioId, studioName = studioName))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsContent(
    modifier: Modifier = Modifier,
    stats: ALAnimeStats,
    username: String = "",
    avatarUrl: String? = null,
    onOpenStatus: (String, String) -> Unit,
    onOpenGenre: (String) -> Unit = {},
    onOpenStudio: (Long?, String) -> Unit = { _, _ -> },
) {
    val total = (stats.count ?: 0).coerceAtLeast(1)
    val completed = stats.statuses
        .firstOrNull { it.status == "COMPLETED" }
        ?.count ?: 0
    val statuses = remember(stats) {
        stats.statuses
            .filter { it.status != null && it.count != null }
            .sortedByDescending { it.count ?: 0 }
    }
    val topGenres = remember(stats) {
        stats.genres
            .filter { it.genre != null && it.count != null }
            .sortedByDescending { it.count ?: 0 }
            .take(6)
    }
    val topStudios = remember(stats) {
        stats.studios
            .filter { it.studio?.name != null && it.count != null }
            .sortedByDescending { it.count ?: 0 }
            .take(5)
    }

    ScrollbarLazyColumn(modifier = modifier) {
        if (username.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = username,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                )
                            } else {
                                Text(
                                    text = username.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    Column {
                        Text(
                            text = username,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(AMR.strings.stats_profile_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    value = stats.count?.toString() ?: "0",
                    label = stringResource(AMR.strings.stats_total_anime),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = stats.episodesWatched?.toString() ?: "0",
                    label = stringResource(AMR.strings.stats_episodes_watched),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    value = formatMinutes(stats.minutesWatched ?: 0),
                    label = stringResource(AMR.strings.stats_time_watched),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = formatScore(stats.meanScore),
                    label = stringResource(AMR.strings.stats_mean_score),
                    sub = stats.standardDeviation?.let {
                        "± ${formatScore(it)}"
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    value = formatDays(stats.minutesWatched ?: 0),
                    label = stringResource(AMR.strings.stats_days_watched),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = completed.toString(),
                    label = stringResource(AMR.strings.stats_completed),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Status Segmented Bar & Breakdown
        if (statuses.isNotEmpty()) {
            item {
                SectionHeader(stringResource(AMR.strings.stats_status))
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                    ) {
                        statuses.forEach { status ->
                            val weight = (status.count ?: 0).toFloat()
                            if (weight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(anilistStatusColor(status.status)),
                                )
                            }
                        }
                    }
                }
            }
            statuses.forEach { status ->
                item(key = "status_${status.status.orEmpty()}") {
                    val label = stringResource(anilistStatusStringRes(status.status))
                    val count = status.count ?: 0
                    StatRow(
                        label = label,
                        value = count.toString(),
                        fraction = count / total.toFloat(),
                        barColor = anilistStatusColor(status.status),
                        onClick = {
                            onOpenStatus(status.status.orEmpty(), label)
                        },
                    )
                }
            }
        }

        // Top Genres
        if (topGenres.isNotEmpty()) {
            item(key = "header_genres") {
                SectionHeader(stringResource(AMR.strings.stats_top_genres))
            }
            itemsIndexed(topGenres, key = { idx, g -> "genre_${idx}_${g.genre.orEmpty()}" }) { _, g ->
                val genreName = g.genre.orEmpty()
                StatRow(
                    label = genreName,
                    value = (g.count ?: 0).toString(),
                    fraction = (g.count ?: 0) / total.toFloat(),
                    onClick = if (genreName.isNotBlank()) {
                        { onOpenGenre(genreName) }
                    } else {
                        null
                    },
                )
            }
        }

        // Top Studios
        if (topStudios.isNotEmpty()) {
            item(key = "header_studios") {
                SectionHeader(stringResource(AMR.strings.stats_top_studios))
            }
            itemsIndexed(
                topStudios,
                key = { idx, s -> "studio_${s.studio?.id ?: idx}_${s.studio?.name.orEmpty()}" },
            ) { _, s ->
                val studioName = s.studio?.name.orEmpty()
                val studioId = s.studio?.id?.takeIf { it > 0L }
                StatRow(
                    label = studioName,
                    value = (s.count ?: 0).toString(),
                    fraction = (s.count ?: 0) / total.toFloat(),
                    onClick = if (studioName.isNotBlank()) {
                        { onOpenStudio(studioId, studioName) }
                    } else {
                        null
                    },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    Surface(
        modifier = modifier.pureBlackBorder(MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    fraction: Float = 0f,
    barColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(18.dp),
                )
            }
        }
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor),
                )
            }
        }
    }
}

@Composable
private fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return stringResource(AMR.strings.anilist_countdown_minutes, 0)
    val hours = minutes / 60
    return if (hours > 0) {
        stringResource(AMR.strings.anilist_countdown_hours_minutes, hours, minutes % 60)
    } else {
        stringResource(AMR.strings.anilist_countdown_minutes, minutes)
    }
}

@Composable
private fun formatDays(minutes: Int): String {
    if (minutes <= 0) return stringResource(AMR.strings.anilist_countdown_days, 0)
    val days = minutes / 1440
    val remainingHours = (minutes % 1440) / 60
    return if (remainingHours > 0) {
        stringResource(AMR.strings.anilist_countdown_days_hours, days, remainingHours)
    } else {
        stringResource(AMR.strings.anilist_countdown_days, days)
    }
}

@Composable
private fun formatScore(score: Double?): String {
    if (score == null) return stringResource(AMR.strings.anilist_score_unset)
    val rounded = Math.round(score * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
