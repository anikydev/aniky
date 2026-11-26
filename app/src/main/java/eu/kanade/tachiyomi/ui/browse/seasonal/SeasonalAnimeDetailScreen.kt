package eu.kanade.tachiyomi.ui.browse.seasonal

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.model.AnilistTitleLanguage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.manga.components.AnimeCover
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeDetailsCharacterEdge
import eu.kanade.tachiyomi.data.track.anilist.dto.ALFuzzyDate
import eu.kanade.tachiyomi.data.track.anilist.dto.ALListStatus
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListChip
import eu.kanade.tachiyomi.ui.anilist.AniListDetailSkeleton
import eu.kanade.tachiyomi.ui.anilist.AniListInnerSpacer
import eu.kanade.tachiyomi.ui.anilist.AniListScoreBadge
import eu.kanade.tachiyomi.ui.anilist.AniListSectionHeader
import eu.kanade.tachiyomi.ui.anilist.AniListSectionSpacer
import eu.kanade.tachiyomi.ui.anilist.AniListShapes
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.anilistAiringStatusLabel
import eu.kanade.tachiyomi.ui.anilist.anilistCharacterRoleLabel
import eu.kanade.tachiyomi.ui.anilist.anilistDisplayTitle
import eu.kanade.tachiyomi.ui.anilist.anilistFormatLabel
import eu.kanade.tachiyomi.ui.anilist.anilistScoreStar
import eu.kanade.tachiyomi.ui.anilist.anilistScoreText
import eu.kanade.tachiyomi.ui.anilist.anilistSeasonLabel
import eu.kanade.tachiyomi.ui.anilist.anilistStatusColor
import eu.kanade.tachiyomi.ui.anilist.anilistStatusLabel
import eu.kanade.tachiyomi.ui.anilist.browse.AdvancedSearchScreen
import eu.kanade.tachiyomi.ui.anilist.detail.AniListDetailExtras
import eu.kanade.tachiyomi.ui.anilist.detail.FavouriteButton
import eu.kanade.tachiyomi.ui.anilist.detail.StaffDetailScreen
import eu.kanade.tachiyomi.ui.anilist.detail.StudioDetailScreen
import eu.kanade.tachiyomi.ui.anilist.mylists.ListEditSheet
import eu.kanade.tachiyomi.ui.anilist.mylists.MyListUiItem
import eu.kanade.tachiyomi.ui.anilist.rememberAnilistLibraryLookup
import eu.kanade.tachiyomi.ui.browse.seasonal.model.AnimeThemeSong
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.pureBlackBorder
import kotlin.math.roundToInt
import eu.kanade.tachiyomi.ui.manga.MangaScreen as AnimeScreen
import tachiyomi.core.common.i18n.stringResource as contextStringResource

class SeasonalAnimeDetailScreen(
    val mediaId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val context = LocalContext.current
        val screenModel = rememberScreenModel { SeasonalAnimeDetailScreenModel(mediaId) }
        val state by screenModel.state.collectAsState()
        var showEntryEditor by remember { mutableStateOf(false) }
        var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }

        if (state.isLoading) {
            Scaffold(
                topBar = {
                    AppBar(
                        title = "",
                        navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    )
                },
            ) { contentPadding ->
                AniListDetailSkeleton(modifier = Modifier.padding(contentPadding))
            }
            return
        }

        val details = state.details
        if (details == null) {
            Scaffold(
                topBar = {
                    AppBar(
                        title = "",
                        navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    )
                },
            ) { contentPadding ->
                EmptyScreen(
                    modifier = Modifier.padding(contentPadding),
                    stringRes = AMR.strings.error,
                    actions = persistentListOf(
                        EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::retry),
                    ),
                )
            }
            return
        }

        AniListDetailContent(
            details = details,
            inLibraryAnimeId = state.inLibraryAnimeId,
            isBumping = state.isBumping,
            isRefreshing = state.isRefreshing,
            isLoggedIn = screenModel.isLoggedIn,
            titleLanguage = screenModel.titleLanguage,
            themeSongs = state.themeSongs,
            navigateUp = { handleBack?.invoke() ?: navigator.pop() },
            onRefresh = screenModel::refresh,
            onAddToLibrary = {
                addTarget = AniListAddTarget(
                    mediaId = details.id,
                    title = screenModel.titleLanguage.pick(
                        romaji = details.title.romaji,
                        english = details.title.english,
                        native = details.title.native,
                        userPreferred = details.title.userPreferred,
                    ),
                    totalEpisodes = details.episodes?.toInt(),
                )
            },
            onAddAnime = { addTarget = it },
            onOpenLibraryAnime = { animeId ->
                val prev = navigator.items.getOrNull(navigator.size - 2)
                if (prev is AnimeScreen && prev.mangaId == animeId) {
                    navigator.pop()
                } else {
                    navigator.push(AnimeScreen(animeId))
                }
            },
            onRelationClick = { navigator.push(SeasonalAnimeDetailScreen(it)) },
            onCharacterClick = { navigator.push(CharacterDetailScreen(it)) },
            onEditEntry = { showEntryEditor = true },
            onDeleteEntry = {
                screenModel.deleteEntry {
                    context.toast(context.contextStringResource(AMR.strings.anilist_entry_deleted))
                }
            },
            onBumpProgress = screenModel::bumpProgress,
            onSetStatus = screenModel::setEntryStatus,
        )

        addTarget?.let { target ->
            AniListSourceSearchSheet(
                target = target,
                navigator = navigator,
                onDismissRequest = { addTarget = null },
            )
        }

        // The "My entry" card opens the same editor sheet used by the My Lists tab.
        if (showEntryEditor) {
            val item = details.toEditableEntry(screenModel.titleLanguage)
            ListEditSheet(
                item = item,
                onDismiss = { showEntryEditor = false },
                onSaved = {
                    showEntryEditor = false
                    screenModel.refresh()
                },
            )
        }
    }
}

/** Maps the viewer's AniList entry onto the editor-sheet model; defaults to PLANNING when not on any list yet. */
private fun ALAnimeDetails.toEditableEntry(language: AnilistTitleLanguage): MyListUiItem {
    val entry = mediaListEntry
    val title = language.pick(
        romaji = title.romaji,
        english = title.english,
        native = title.native,
        userPreferred = title.userPreferred,
    )
    return MyListUiItem(
        listId = entry?.id ?: 0L,
        mediaId = id,
        title = title,
        coverUrl = coverImage.large ?: coverImage.extraLarge,
        status = entry?.status ?: ALListStatus.PLANNING,
        progress = entry?.progress ?: 0,
        totalEpisodes = episodes?.toInt() ?: 0,
        score100 = (entry?.score ?: 0.0).roundToInt(),
        updatedAt = 0L,
        format = format ?: "",
        inLibrary = false,
        repeat = entry?.repeat ?: 0,
        notes = entry?.notes,
        startedAt = entry?.startedAt ?: ALFuzzyDate(null, null, null),
        completedAt = entry?.completedAt ?: ALFuzzyDate(null, null, null),
    )
}

@Composable
private fun AniListDetailContent(
    details: ALAnimeDetails,
    inLibraryAnimeId: Long?,
    isBumping: Boolean,
    isRefreshing: Boolean,
    isLoggedIn: Boolean,
    titleLanguage: AnilistTitleLanguage,
    themeSongs: List<AnimeThemeSong>,
    navigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddAnime: (AniListAddTarget) -> Unit,
    onOpenLibraryAnime: (Long) -> Unit,
    onRelationClick: (Long) -> Unit,
    onCharacterClick: (Long) -> Unit,
    onEditEntry: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBumpProgress: (Int) -> Unit,
    onSetStatus: (String) -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val libraryLookup = rememberAnilistLibraryLookup()
    val displayedTitle = titleLanguage.pick(
        romaji = details.title.romaji,
        english = details.title.english,
        native = details.title.native,
        userPreferred = details.title.userPreferred,
    )
    val clipboardManager = LocalClipboardManager.current
    val copiedMessage = stringResource(MR.strings.copied_to_clipboard, displayedTitle)
    val contextAniListUrl = "https://anilist.co/anime/${details.id}"
    var showFullscreenCover by remember { mutableStateOf(false) }
    var showTrailerSheet by remember { mutableStateOf(false) }
    var activeThemeSong by remember { mutableStateOf<AnimeThemeSong?>(null) }
    val youtubeErrorMessage = stringResource(AMR.strings.anilist_unable_open_youtube)
    val openSongOnYouTube: (AnimeThemeSong) -> Unit = { song ->
        val query = Uri.encode(listOfNotNull(song.title, song.artist).joinToString(" "))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            context.toast(youtubeErrorMessage)
        }
    }

    val scrollState = rememberScrollState()
    val collapseRangePx = with(LocalDensity.current) { 160.dp.toPx() }
    val toolbarAlpha by remember(collapseRangePx) {
        derivedStateOf { (scrollState.value / collapseRangePx).coerceIn(0f, 1f) }
    }
    val titleAlpha by remember(collapseRangePx) {
        derivedStateOf {
            ((scrollState.value - collapseRangePx * 0.55f) / (collapseRangePx * 0.45f)).coerceIn(0f, 1f)
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                titleContent = {
                    AppBarTitle(
                        title = displayedTitle,
                        modifier = Modifier.alpha(titleAlpha),
                    )
                },
                backgroundColor = MaterialTheme.colorScheme
                    .surfaceColorAtElevation(3.dp)
                    .copy(alpha = toolbarAlpha),
                navigateUp = navigateUp,
                actions = {
                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, contextAniListUrl)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, displayedTitle))
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(MR.strings.action_share),
                        )
                    }
                    FavouriteButton(mediaId = details.id, isFavourite = details.isFavourite)
                },
            )
        },
    ) { contentPadding ->
        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = onRefresh,
            enabled = true,
            indicatorPadding = contentPadding,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = contentPadding.calculateBottomPadding()),
            ) {
                // Ambient Banner Backdrop extending cleanly behind the scroll-reactive AppBar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp + contentPadding.calculateTopPadding())
                        .clickable { showFullscreenCover = true },
                ) {
                    AnimeCover.Panorama(
                        data = details.bannerImage ?: details.coverImage.extraLarge ?: details.coverImage.large,
                        contentDescription = displayedTitle,
                        modifier = Modifier.fillMaxSize(),
                        enforceRatio = false,
                    )
                    // Top scrim so toolbar icons remain legible against bright banners at scroll == 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(contentPadding.calculateTopPadding() + 48.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        MaterialTheme.colorScheme.surface,
                                    ),
                                    startY = 80f,
                                ),
                            ),
                    )
                }

                // Cover + titles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = AniListShapes.Card,
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .width(105.dp)
                            .align(Alignment.Top)
                            .clip(AniListShapes.Card)
                            .clickable { showFullscreenCover = true },
                    ) {
                        AnimeCover.Book(
                            data = details.coverImage.extraLarge ?: details.coverImage.large,
                            contentDescription = displayedTitle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Título — tap to copy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    @Suppress("DEPRECATION")
                                    clipboardManager.setText(AnnotatedString(displayedTitle))
                                    context.toast(copiedMessage)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = displayedTitle,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(MR.strings.action_copy_to_clipboard),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        val altTitle = listOfNotNull(
                            details.title.english,
                            details.title.romaji,
                            details.title.native,
                        ).firstOrNull { it.isNotBlank() && !it.equals(displayedTitle, ignoreCase = true) }
                        if (altTitle != null) {
                            val altCopiedMsg = stringResource(MR.strings.copied_to_clipboard, altTitle)
                            Text(
                                text = altTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    @Suppress("DEPRECATION")
                                    clipboardManager.setText(AnnotatedString(altTitle))
                                    context.toast(altCopiedMsg)
                                },
                            )
                        }

                        // Score + format + episodes
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AniListScoreBadge(score100 = details.averageScore, tonal = true)
                            Text(
                                text = listOfNotNull(
                                    details.format?.let { anilistFormatLabel(it) },
                                    details.episodes?.let { pluralStringResource(AMR.plurals.anilist_episodes_count, it.toInt(), it.toInt()) },
                                    details.duration?.let { stringResource(AMR.strings.anilist_duration_minutes, it.toInt()) },
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        // Estado + temporada
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            details.status?.let { status ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = AniListShapes.Badge,
                                ) {
                                    Text(
                                        text = anilistAiringStatusLabel(status),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (details.season != null && details.seasonYear != null) {
                                Text(
                                    text = "${anilistSeasonLabel(details.season)} ${details.seasonYear}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    // Géneros
                    if (details.genres.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(details.genres) { genre ->
                                AniListChip(
                                    text = genre,
                                    modifier = Modifier.clickable {
                                        navigator.push(AdvancedSearchScreen(initialGenre = genre))
                                    },
                                )
                            }
                        }
                    }

                    // Prominent Next Episode Airing Banner
                    details.nextAiringEpisode?.let { next ->
                        val epNum = next.episode ?: 0
                        val airingAtSec = next.airingAt ?: 0L
                        val diffSec = airingAtSec - (System.currentTimeMillis() / 1000)
                        val countdownText = if (diffSec > 0) {
                            val days = (diffSec / 86400).toInt()
                            val hours = ((diffSec % 86400) / 3600).toInt()
                            val mins = ((diffSec % 3600) / 60).toInt()
                            when {
                                days > 0 -> stringResource(AMR.strings.anilist_countdown_days_hours, days, hours)
                                hours > 0 -> stringResource(AMR.strings.anilist_countdown_hours_minutes, hours, mins)
                                else -> stringResource(AMR.strings.anilist_countdown_minutes, mins.coerceAtLeast(1))
                            }
                        } else {
                            null
                        }
                        val airDateLabel = remember(airingAtSec) {
                            if (airingAtSec > 0L) {
                                runCatching {
                                    val zoned = java.time.Instant.ofEpochSecond(airingAtSec)
                                        .atZone(java.time.ZoneId.systemDefault())
                                    java.time.format.DateTimeFormatter
                                        .ofPattern("EEE, MMM d • HH:mm")
                                        .format(zoned)
                                }.getOrNull()
                            } else {
                                null
                            }
                        }

                        AniListSectionSpacer()
                        Surface(
                            shape = AniListShapes.Card,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pureBlackBorder(AniListShapes.Card),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (countdownText != null) {
                                        stringResource(AMR.strings.seasonal_airing_banner, epNum, countdownText)
                                    } else {
                                        stringResource(AMR.strings.seasonal_airing_soon, epNum)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (airDateLabel != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = airDateLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }

                    // My Entry Card (Promoted to Top for instant progress tracking)
                    AniListSectionSpacer()
                    MyEntryHeaderCard(
                        details = details,
                        isBumping = isBumping,
                        isLoggedIn = isLoggedIn,
                        onBumpProgress = onBumpProgress,
                        onEditEntry = onEditEntry,
                        onDeleteEntry = onDeleteEntry,
                        onSetStatus = onSetStatus,
                    )

                    // Library Action: Directly open in library or search sources, plus trailer button
                    val effectiveLibraryId = inLibraryAnimeId ?: libraryLookup.findAnimeId(
                        details.id,
                        details.title.userPreferred,
                        details.title.english,
                        details.title.romaji,
                        details.title.native,
                    )
                    val hasTrailer = details.trailer?.site.equals("youtube", ignoreCase = true) && !details.trailer?.id.isNullOrBlank()
                    AniListSectionSpacer()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (effectiveLibraryId != null) {
                            Button(
                                onClick = { onOpenLibraryAnime(effectiveLibraryId) },
                                shape = AniListShapes.Pill,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.BookmarkAdded, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(MR.strings.in_library))
                            }
                        } else {
                            Button(
                                onClick = onAddToLibrary,
                                shape = AniListShapes.Pill,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(MR.strings.add_to_library))
                            }
                        }
                        val trailerId = details.trailer?.id
                        if (hasTrailer && !trailerId.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { showTrailerSheet = true },
                                shape = AniListShapes.Pill,
                            ) {
                                Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(AMR.strings.anilist_trailer))
                            }
                        }
                    }

                    // Sinopsis
                    if (!details.description.isNullOrBlank()) {
                        AniListSectionSpacer()
                        AniListSectionHeader(title = stringResource(AMR.strings.description))
                        AniListInnerSpacer()
                        AniListDescriptionText(raw = details.description)
                    }

                    // Estudios
                    val studios = details.studios?.edges?.filter { it.isMain }?.map { it.node }
                        ?.takeIf { it.isNotEmpty() }
                        ?: details.studios?.edges?.map { it.node }
                    studios?.filter { it.name.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { studioNodes ->
                        AniListSectionSpacer()
                        AniListSectionHeader(title = stringResource(AMR.strings.studios))
                        AniListInnerSpacer()
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(studioNodes, key = { "${it.id}_${it.name}" }) { studio ->
                                AniListChip(
                                    text = studio.name,
                                    modifier = Modifier.clickable {
                                        navigator.push(
                                            StudioDetailScreen(
                                                studioId = studio.id.takeIf { it > 0L },
                                                studioName = studio.name,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Personajes
                    val characters = details.characters?.edges
                        ?.sortedBy { characterRoleRank(it.role) }
                        ?.take(20)
                    if (!characters.isNullOrEmpty()) {
                        AniListSectionSpacer()
                        AniListSectionHeader(title = stringResource(AMR.strings.characters))
                        AniListInnerSpacer()
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(characters) { edge ->
                                CharacterCard(
                                    edge = edge,
                                    onClick = { onCharacterClick(edge.node.id) },
                                    onStaffClick = { navigator.push(StaffDetailScreen(it)) },
                                )
                            }
                        }
                    }

                    // Relaciones (precuelas, secuelas, etc.)
                    val relations = sortAndDedupeRelations(details.relations?.edges.orEmpty())
                    if (relations.isNotEmpty()) {
                        AniListSectionSpacer()
                        AniListSectionHeader(title = stringResource(AMR.strings.relations))
                        AniListInnerSpacer()
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(relations) { edge ->
                                val relTitle = anilistDisplayTitle(
                                    edge.node.title.userPreferred.orEmpty(),
                                    edge.node.title.english,
                                    edge.node.title.romaji,
                                    edge.node.title.native,
                                )
                                val relLibraryId = libraryLookup.findAnimeId(
                                    edge.node.id,
                                    edge.node.title.userPreferred,
                                    edge.node.title.english,
                                    edge.node.title.romaji,
                                    edge.node.title.native,
                                )
                                val isAnimeRel = edge.node.format == null || edge.node.format in ANIME_MEDIA_FORMATS
                                RelationCard(
                                    edge = edge,
                                    onClick = { onRelationClick(edge.node.id) },
                                    inLibrary = relLibraryId != null,
                                    onAdd = if (isAnimeRel) {
                                        {
                                            onAddAnime(
                                                AniListAddTarget(
                                                    mediaId = edge.node.id,
                                                    title = relTitle,
                                                ),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }

                    // Theme Songs (Openings & Endings)
                    if (themeSongs.isNotEmpty()) {
                        AniListSectionSpacer()
                        ThemeSongsSection(
                            songs = themeSongs,
                            onSongClick = { song ->
                                if (song.hasPreview) {
                                    activeThemeSong = song
                                } else {
                                    openSongOnYouTube(song)
                                }
                            },
                            onSongCopy = { song ->
                                val copyText = if (!song.artist.isNullOrBlank()) "${song.title} - ${song.artist}" else song.title
                                @Suppress("DEPRECATION")
                                clipboardManager.setText(AnnotatedString(copyText))
                                context.toast(context.contextStringResource(MR.strings.copied_to_clipboard, copyText))
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Phase 3 extras: staff, recommendations, tags
                    AniListDetailExtras(
                        details = details,
                        onAddAnime = onAddAnime,
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (showFullscreenCover) {
            val coverUrl = details.coverImage.extraLarge ?: details.coverImage.large ?: ""
            SeasonalAnimeCoverDialog(
                coverUrl = coverUrl,
                bannerUrl = details.bannerImage,
                title = displayedTitle,
                onDismissRequest = { showFullscreenCover = false },
            )
        }

        if (showTrailerSheet && !details.trailer?.id.isNullOrBlank()) {
            TrailerPlayerSheet(
                trailerId = details.trailer.id,
                title = displayedTitle,
                onDismissRequest = { showTrailerSheet = false },
            )
        }

        activeThemeSong?.let { song ->
            ThemeSongPlayerSheet(
                song = song,
                onOpenYouTube = { openSongOnYouTube(song) },
                onDismissRequest = { activeThemeSong = null },
            )
        }
    }
}

@Composable
private fun MyEntryHeaderCard(
    details: ALAnimeDetails,
    isBumping: Boolean,
    isLoggedIn: Boolean,
    onBumpProgress: (Int) -> Unit,
    onEditEntry: () -> Unit,
    onDeleteEntry: () -> Unit,
    onSetStatus: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val entry = details.mediaListEntry
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (entry != null) {
        val total = details.episodes?.toInt() ?: 0
        val prog = entry.progress ?: 0
        val statusColor = anilistStatusColor(entry.status)
        Surface(
            shape = AniListShapes.Card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier
                .fillMaxWidth()
                .pureBlackBorder(AniListShapes.Card)
                .clip(AniListShapes.Card),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
            ) {
                // Top Row: My Entry info, score, steppers, and actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onEditEntry),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(AMR.strings.anilist_my_entry),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Surface(
                                shape = AniListShapes.Pill,
                                color = statusColor.copy(alpha = 0.16f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(statusColor, CircleShape),
                                    )
                                    Text(
                                        text = anilistStatusLabel(entry.status),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val progressLabel = if (total > 0) {
                            stringResource(AMR.strings.anilist_progress_of_total, prog, total)
                        } else {
                            stringResource(AMR.strings.anilist_progress_episode, prog)
                        }
                        Text(
                            text = buildString {
                                append(progressLabel)
                                anilistScoreStar(entry.score).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = AniListShapes.Pill,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onBumpProgress(-1) },
                                enabled = !isBumping && prog > 0,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = stringResource(AMR.strings.anilist_progress_decrease), modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onBumpProgress(1) },
                                enabled = !isBumping && (total <= 0 || prog < total),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(AMR.strings.anilist_progress_increase), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onEditEntry, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(AMR.strings.anilist_edit_entry),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(AMR.strings.anilist_entry_delete),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                if (total > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val animProgress by animateFloatAsState(
                        targetValue = (prog.toFloat() / total).coerceIn(0f, 1f),
                        animationSpec = tween(300),
                        label = "entry_progress_anim",
                    )
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.2f),
                    )
                }

                // 1-Tap Status Switcher Chips
                if (onSetStatus != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ALListStatus.ALL.forEach { s ->
                            val isSelected = entry.status == s
                            val chipColor = anilistStatusColor(s)
                            Surface(
                                onClick = {
                                    if (!isSelected) {
                                        onSetStatus(s)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) chipColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (isSelected) BorderStroke(1.dp, chipColor) else null,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(if (isSelected) chipColor else chipColor.copy(alpha = 0.5f), CircleShape),
                                    )
                                    Text(
                                        text = anilistStatusLabel(s),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) chipColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Untracked state
        Surface(
            shape = AniListShapes.Card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier
                .fillMaxWidth()
                .pureBlackBorder(AniListShapes.Card)
                .clip(AniListShapes.Card),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isLoggedIn) {
                                context.toast(context.contextStringResource(AMR.strings.seasonal_login_prompt))
                            } else {
                                onEditEntry()
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(AMR.strings.anilist_track_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // 1-Tap Quick Add Status Chips
                if (onSetStatus != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ALListStatus.ALL.forEach { s ->
                            val chipColor = anilistStatusColor(s)
                            Surface(
                                onClick = {
                                    if (!isLoggedIn) {
                                        context.toast(context.contextStringResource(AMR.strings.seasonal_login_prompt))
                                    } else {
                                        onSetStatus(s)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = chipColor.copy(alpha = 0.15f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(chipColor, CircleShape),
                                    )
                                    Text(
                                        text = "+ " + anilistStatusLabel(s),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = chipColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(AMR.strings.anilist_entry_delete)) },
            text = { Text(stringResource(AMR.strings.anilist_entry_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteEntry()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CharacterCard(
    edge: ALAnimeDetailsCharacterEdge,
    onClick: () -> Unit,
    onStaffClick: ((Long) -> Unit)? = null,
) {
    val primaryVa = edge.voiceActors.firstOrNull { it.languageV2.equals("Japanese", ignoreCase = true) }
        ?: edge.voiceActors.firstOrNull()
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(50)
        Surface(
            shape = shape,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(96.dp)
                .pureBlackBorder(shape),
        ) {
            AnimeCover.Square(
                data = edge.node.image?.large,
                contentDescription = edge.node.name.userPreferred.orEmpty(),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = edge.node.name.userPreferred.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        edge.role?.let { role ->
            Text(
                text = anilistCharacterRoleLabel(role),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (primaryVa != null) {
            val vaName = primaryVa.name?.userPreferred ?: primaryVa.name?.full.orEmpty()
            val vaDisplay = if (!primaryVa.languageV2.isNullOrBlank() && !primaryVa.languageV2.equals("Japanese", ignoreCase = true)) {
                "$vaName (${primaryVa.languageV2})"
            } else {
                vaName
            }
            Text(
                text = vaDisplay,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onStaffClick != null) {
                    Modifier.clickable { onStaffClick(primaryVa.id) }
                } else {
                    Modifier
                },
            )
        }
    }
}

/** Orders character roles MAIN, then SUPPORTING, then BACKGROUND, then anything else. */
private fun characterRoleRank(role: String?): Int = when (role?.uppercase()) {
    "MAIN" -> 0
    "SUPPORTING" -> 1
    "BACKGROUND" -> 2
    else -> 3
}
