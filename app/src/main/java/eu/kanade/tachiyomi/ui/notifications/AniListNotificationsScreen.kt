package eu.kanade.tachiyomi.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotification
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListLoginRequired
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.rememberAnilistLibraryLookup
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
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
import tachiyomi.presentation.core.util.isPureBlack
import tachiyomi.presentation.core.util.pureBlackBorder

private val NotificationPosterShape = RoundedCornerShape(10.dp)

// ANK -->
private enum class NotificationCategoryFilter {
    ALL,
    AIRING,
    RELATED,
    SOCIAL,
    ;

    fun matches(type: String?): Boolean = when (this) {
        ALL -> true
        AIRING -> type == "AIRING"
        RELATED ->
            type == "RELATED_MEDIA_ADDITION" ||
                type == "MEDIA_DATA_CHANGE" ||
                type == "MEDIA_MERGE" ||
                type == "MEDIA_DELETION"
        SOCIAL ->
            type != "AIRING" &&
                type != "RELATED_MEDIA_ADDITION" &&
                type != "MEDIA_DATA_CHANGE" &&
                type != "MEDIA_MERGE" &&
                type != "MEDIA_DELETION"
    }
}
// ANK <--

class AniListNotificationsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val screenModel = rememberScreenModel { AniListNotificationsScreenModel() }
        val state by screenModel.state.collectAsState()
        val libraryLookup = rememberAnilistLibraryLookup()
        var selectedFilter by rememberSaveable { mutableStateOf(NotificationCategoryFilter.ALL) }
        var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }
        var showMarkAllReadConfirm by remember { mutableStateOf(false) }

        val filteredItems = remember(state.items, selectedFilter) {
            if (selectedFilter == NotificationCategoryFilter.ALL) {
                state.items
            } else {
                state.items.filter { selectedFilter.matches(it.type) }
            }
        }

        val unreadCountByFilter = remember(state.items, state.readIds) {
            NotificationCategoryFilter.entries.associateWith { filter ->
                state.items.count { n ->
                    n.id != null && n.id !in state.readIds && filter.matches(n.type)
                }
            }
        }
        val totalUnreadCount = unreadCountByFilter[NotificationCategoryFilter.ALL] ?: 0
        val hasUnread = totalUnreadCount > 0

        if (showMarkAllReadConfirm) {
            AlertDialog(
                onDismissRequest = { showMarkAllReadConfirm = false },
                title = { Text(text = stringResource(AMR.strings.notifications_mark_all_read_confirm_title)) },
                text = {
                    Text(
                        text = stringResource(
                            AMR.strings.notifications_mark_all_read_confirm_message,
                            totalUnreadCount,
                        ),
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showMarkAllReadConfirm = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = {
                            showMarkAllReadConfirm = false
                            screenModel.markAllRead()
                        },
                    ) {
                        Text(text = stringResource(AMR.strings.notifications_mark_all_read))
                    }
                },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    titleContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppBarTitle(stringResource(AMR.strings.notifications_title))
                            if (totalUnreadCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = stringResource(AMR.strings.notifications_unread_count, totalUnreadCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    },
                    navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (state.items.isNotEmpty()) {
                            AppBarActions(
                                persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(AMR.strings.notifications_mark_all_read),
                                        icon = Icons.Outlined.DoneAll,
                                        enabled = hasUnread,
                                        onClick = { showMarkAllReadConfirm = true },
                                    ),
                                ),
                            )
                        }
                    },
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
                            stringRes = AMR.strings.notifications_login_required,
                        )
                    }
                    state.hasError -> {
                        EmptyScreen(
                            modifier = Modifier.padding(contentPadding),
                            stringRes = AMR.strings.notifications_error,
                            actions = persistentListOf(
                                EmptyScreenAction(
                                    stringRes = MR.strings.action_retry,
                                    icon = Icons.Outlined.Refresh,
                                    onClick = screenModel::refresh,
                                ),
                            ),
                        )
                    }
                    state.items.isEmpty() -> {
                        EmptyScreen(
                            modifier = Modifier.padding(contentPadding),
                            stringRes = AMR.strings.notifications_empty,
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(NotificationCategoryFilter.entries, key = { it.name }) { filter ->
                                    val label = when (filter) {
                                        NotificationCategoryFilter.ALL ->
                                            stringResource(AMR.strings.notifications_filter_all)
                                        NotificationCategoryFilter.AIRING ->
                                            stringResource(AMR.strings.notifications_filter_airing)
                                        NotificationCategoryFilter.RELATED ->
                                            stringResource(AMR.strings.notifications_filter_related)
                                        NotificationCategoryFilter.SOCIAL ->
                                            stringResource(AMR.strings.notifications_filter_social)
                                    }
                                    val filterUnread = unreadCountByFilter[filter] ?: 0
                                    val selected = selectedFilter == filter
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedFilter = filter },
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                )
                                                if (filterUnread > 0) {
                                                    Badge(
                                                        containerColor = if (selected) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.secondaryContainer
                                                        },
                                                        contentColor = if (selected) {
                                                            MaterialTheme.colorScheme.onPrimary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        },
                                                    ) {
                                                        Text("$filterUnread")
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                }
                            }
                            if (filteredItems.isEmpty()) {
                                EmptyScreen(
                                    modifier = Modifier.weight(1f),
                                    stringRes = AMR.strings.notifications_empty_filtered,
                                )
                            } else {
                                ScrollbarLazyColumn(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(bottom = 12.dp),
                                ) {
                                    itemsIndexed(
                                        items = filteredItems,
                                        key = { index, it -> it.id ?: -(index.toLong() + 1) },
                                    ) { index, notification ->
                                        val bucket = dayBucketOf(notification.createdAt)
                                        val previousBucket = filteredItems.getOrNull(index - 1)
                                            ?.let { dayBucketOf(it.createdAt) }
                                        if (bucket != previousBucket) {
                                            DayHeader(bucket = bucket)
                                        }
                                        val mediaId = notification.media?.id
                                        val mediaTitle = notification.media?.title?.userPreferred
                                        val resolvedAnimeId = mediaId?.let { state.animeIdByAnilistId[it] }
                                            ?: libraryLookup.findAnimeId(mediaId, mediaTitle)
                                        val navigate: (() -> Unit)? = when {
                                            resolvedAnimeId != null -> {
                                                { navigator.push(MangaScreen(resolvedAnimeId)) }
                                            }
                                            mediaId != null -> {
                                                { navigator.push(SeasonalAnimeDetailScreen(mediaId)) }
                                            }
                                            else -> null
                                        }
                                        NotificationRow(
                                            notification = notification,
                                            unread = notification.id != null && notification.id !in state.readIds,
                                            isInLibrary = resolvedAnimeId != null,
                                            onAddToLibrary = if (mediaId != null &&
                                                resolvedAnimeId == null &&
                                                !mediaTitle.isNullOrBlank()
                                            ) {
                                                {
                                                    addTarget = AniListAddTarget(
                                                        mediaId = mediaId,
                                                        title = mediaTitle,
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                screenModel.markAsRead(notification)
                                                navigate?.invoke()
                                            },
                                        )
                                    }
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

@Composable
private fun NotificationRow(
    notification: ALNotification,
    unread: Boolean,
    isInLibrary: Boolean = false,
    onAddToLibrary: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val mediaCoverUrl = notification.media?.coverImage?.large
    val userAvatarUrl = notification.user?.avatar?.large
    val cardShape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .pureBlackBorder(cardShape)
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        color = if (unread) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                mediaCoverUrl != null -> {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 62.dp)
                            .clip(NotificationPosterShape)
                            .pureBlackBorder(NotificationPosterShape),
                    ) {
                        AsyncImage(
                            model = mediaCoverUrl,
                            contentDescription = notification.media.title?.userPreferred,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isInLibrary) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(3.dp)
                                    .size(16.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = stringResource(MR.strings.in_library),
                                    modifier = Modifier.padding(2.dp),
                                )
                            }
                        }
                    }
                }
                userAvatarUrl != null -> {
                    AsyncImage(
                        model = userAvatarUrl,
                        contentDescription = notification.user.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .pureBlackBorder(CircleShape),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (MaterialTheme.isPureBlack) {
                                    Color.Black
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            )
                            .pureBlackBorder(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = iconForType(notification.type),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = notificationText(notification),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = relativeTimeSpanString((notification.createdAt ?: 0L) * 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            if (unread || onAddToLibrary != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (unread) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = stringResource(AMR.strings.notifications_unread_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (onAddToLibrary != null) {
                        FilledTonalIconButton(
                            onClick = onAddToLibrary,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(AMR.strings.anilist_add_to_library),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun notificationText(n: ALNotification): String {
    val mediaTitle = n.media?.title?.userPreferred
    val userName = n.user?.name
    val contexts = n.contexts
    val context = n.context
    return when {
        !contexts.isNullOrEmpty() -> buildString {
            append(contexts.getOrElse(0) { "" })
            n.episode?.let { append(it) }
            append(contexts.getOrElse(1) { "" })
            if (mediaTitle != null) append(mediaTitle)
            append(contexts.getOrElse(2) { "" })
        }.trim()
        context != null -> ((userName ?: mediaTitle ?: "") + context).trim()
        else -> mediaTitle ?: userName.orEmpty()
    }
}

private enum class DayBucket { TODAY, YESTERDAY, EARLIER }

private fun dayBucketOf(createdAtSeconds: Long?): DayBucket {
    if (createdAtSeconds == null || createdAtSeconds <= 0L) return DayBucket.EARLIER
    val date = java.time.Instant.ofEpochSecond(createdAtSeconds)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> DayBucket.TODAY
        today.minusDays(1) -> DayBucket.YESTERDAY
        else -> DayBucket.EARLIER
    }
}

@Composable
private fun DayHeader(bucket: DayBucket) {
    val text = when (bucket) {
        DayBucket.TODAY -> stringResource(MR.strings.relative_time_today)
        DayBucket.YESTERDAY -> stringResource(AMR.strings.notifications_yesterday)
        DayBucket.EARLIER -> stringResource(AMR.strings.notifications_earlier)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun iconForType(type: String?): ImageVector = when {
    type == "AIRING" -> Icons.Outlined.PlayCircle
    type == "FOLLOWING" -> Icons.Outlined.PersonAdd
    type == "RELATED_MEDIA_ADDITION" -> Icons.Outlined.NewReleases
    type == "ACTIVITY_LIKE" || type == "ACTIVITY_REPLY_LIKE" -> Icons.Outlined.Favorite
    type != null && (type.startsWith("ACTIVITY_") || type.startsWith("THREAD_")) -> Icons.Outlined.Forum
    else -> Icons.Outlined.Notifications
}
