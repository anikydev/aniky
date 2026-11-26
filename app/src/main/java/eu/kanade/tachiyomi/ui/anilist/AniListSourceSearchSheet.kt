package eu.kanade.tachiyomi.ui.anilist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.getChapterList
import eu.kanade.tachiyomi.source.getMangaDetails
import eu.kanade.tachiyomi.source.getSearchManga
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.pureBlackBorder
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.domain.track.model.Track as DomainTrack

@Immutable
data class AniListAddTarget(
    val mediaId: Long?,
    val title: String,
    val totalEpisodes: Int? = null,
)

@Immutable
private data class SourceMatchEntry(
    val source: CatalogueSource,
    val initialManga: Manga,
    val isPinned: Boolean,
)

@Composable
fun AniListSourceSearchSheet(
    target: AniListAddTarget,
    navigator: Navigator,
    onDismissRequest: () -> Unit,
) {
    AniListSourceSearchSheet(
        target = target,
        onDismissRequest = onDismissRequest,
        onOpenAnime = { animeId ->
            onDismissRequest()
            navigator.push(MangaScreen(animeId))
        },
        onOpenFullGlobalSearch = { query ->
            onDismissRequest()
            navigator.push(GlobalSearchScreen(query))
        },
        onBrowseExtensions = {
            onDismissRequest()
            navigator.popUntilRoot()
            launchUI {
                HomeScreen.openTab(HomeScreen.Tab.Browse(toExtensions = true))
            }
        },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun AniListSourceSearchSheet(
    target: AniListAddTarget,
    onDismissRequest: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    onOpenFullGlobalSearch: (String) -> Unit,
    onBrowseExtensions: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val sourceManager = remember { Injekt.get<SourceManager>() }
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val networkToLocalManga = remember { Injekt.get<NetworkToLocalManga>() }
    val getManga = remember { Injekt.get<GetManga>() }
    val updateManga = remember { Injekt.get<UpdateManga>() }
    val getCategories = remember { Injekt.get<GetCategories>() }
    val setMangaCategories = remember { Injekt.get<SetMangaCategories>() }
    val setMangaDefaultChapterFlags = remember { Injekt.get<SetMangaDefaultChapterFlags>() }
    val syncChaptersWithSource = remember { Injekt.get<SyncChaptersWithSource>() }
    val coverCache = remember { Injekt.get<CoverCache>() }
    val trackerManager = remember { Injekt.get<TrackerManager>() }
    val addTracks = remember { Injekt.get<AddTracks>() }
    val insertTrack = remember { Injekt.get<InsertTrack>() }
    val getTracks = remember { Injekt.get<GetTracks>() }
    val trackPreferences = remember { Injekt.get<TrackPreferences>() }

    val pinnedSourceIds = remember { sourcePreferences.pinnedSources().get() }
    val preferredSourceIds = remember { trackPreferences.anilistPreferredSources().get() }
    val allEnabledSources = remember(preferredSourceIds, pinnedSourceIds) {
        val visibleSources = sourceManager.getVisibleCatalogueSources()
            .filter { it.id != LocalSource.ID }
        val preferredSources = if (preferredSourceIds.isNotEmpty()) {
            visibleSources.filter { "${it.id}" in preferredSourceIds }
        } else {
            emptyList()
        }
        val candidateSources = preferredSources.ifEmpty {
            val enabledLanguages = sourcePreferences.enabledLanguages().get()
            val disabledSources = sourcePreferences.disabledSources().get()
            visibleSources.filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
        }
        candidateSources.sortedWith(
            compareBy(
                { "${it.id}" !in pinnedSourceIds },
                { "${it.name.lowercase()} (${it.lang})" },
            ),
        )
    }

    val hasPinnedSources = remember(allEnabledSources, pinnedSourceIds) {
        allEnabledSources.any { "${it.id}" in pinnedSourceIds }
    }
    var pinnedOnly by remember { mutableStateOf(false) }

    val activeSources = remember(allEnabledSources, pinnedOnly, pinnedSourceIds) {
        if (pinnedOnly && hasPinnedSources) {
            allEnabledSources.filter { "${it.id}" in pinnedSourceIds }
        } else {
            allEnabledSources
        }
    }

    val matches = remember { mutableStateListOf<SourceMatchEntry>() }
    var completedSources by remember { mutableIntStateOf(0) }
    var sourcesWithHits by remember { mutableIntStateOf(0) }
    var isSearching by remember { mutableStateOf(activeSources.isNotEmpty()) }
    val addingIds = remember { mutableStateListOf<Long>() }

    LaunchedEffect(target.title, activeSources) {
        matches.clear()
        completedSources = 0
        sourcesWithHits = 0
        if (activeSources.isEmpty() || target.title.isBlank()) {
            isSearching = false
            return@LaunchedEffect
        }

        isSearching = true
        val searchDispatcher = Dispatchers.IO.limitedParallelism(5)
        val mutex = Mutex()
        val sanitizedQuery = target.title.sanitize()

        activeSources.map { source ->
            async {
                val found = try {
                    val page = withContext(searchDispatcher) {
                        source.getSearchManga(1, sanitizedQuery, source.getFilterList())
                    }
                    page.mangas
                        .take(5)
                        .map { it.toDomainManga(source.id) }
                        .distinctBy { it.url }
                        .let { networkToLocalManga(it) }
                } catch (_: Exception) {
                    emptyList()
                }

                if (isActive) {
                    val isPinned = "${source.id}" in pinnedSourceIds
                    mutex.withLock {
                        completedSources += 1
                        if (found.isNotEmpty()) {
                            sourcesWithHits += 1
                            val entries = found.map { manga ->
                                SourceMatchEntry(
                                    source = source,
                                    initialManga = manga,
                                    isPinned = isPinned,
                                )
                            }
                            if (isPinned) {
                                val insertIndex = matches.indexOfFirst { !it.isPinned }
                                    .let { if (it == -1) matches.size else it }
                                matches.addAll(insertIndex, entries)
                            } else {
                                matches.addAll(entries)
                            }
                        }
                    }
                }
            }
        }.awaitAll()

        if (isActive) {
            isSearching = false
        }
    }

    suspend fun bindAniListIfNeeded(manga: Manga) {
        val mediaId = target.mediaId?.takeIf { it > 0L } ?: return
        try {
            val existingTracks = getTracks.await(manga.id)
            if (existingTracks.any { it.trackerId == TrackerManager.ANILIST }) return

            if (trackerManager.aniList.isLoggedIn) {
                val dbTrack = DbTrack.create(TrackerManager.ANILIST).apply {
                    anime_id = manga.id
                    remote_id = mediaId
                    title = target.title
                    total_episodes = (target.totalEpisodes ?: 0).toLong()
                    status = Anilist.PLAN_TO_WATCH
                    tracking_url = "https://anilist.co/anime/$mediaId"
                }
                addTracks.bind(trackerManager.aniList, dbTrack, manga)
            } else {
                insertTrack.await(
                    DomainTrack(
                        id = 0L,
                        mangaId = manga.id,
                        trackerId = TrackerManager.ANILIST,
                        remoteId = mediaId,
                        libraryId = null,
                        title = target.title,
                        lastChapterRead = 0.0,
                        totalChapters = (target.totalEpisodes ?: 0).toLong(),
                        status = Anilist.PLAN_TO_WATCH,
                        score = 0.0,
                        remoteUrl = "https://anilist.co/anime/$mediaId",
                        startDate = 0L,
                        finishDate = 0L,
                        private = false,
                    ),
                )
            }
        } catch (e: Exception) {
            manga.xLogE("Failed to bind AniList track on quick add", e)
        }
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Sheet header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(AMR.strings.source_match_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (activeSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .pureBlackBorder(RoundedCornerShape(20.dp)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    text = if (isSearching) {
                                        stringResource(
                                            AMR.strings.source_match_searching,
                                            completedSources,
                                            activeSources.size,
                                        )
                                    } else {
                                        stringResource(
                                            AMR.strings.source_match_done,
                                            matches.size,
                                            sourcesWithHits,
                                        )
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (hasPinnedSources) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = pinnedOnly,
                                onClick = { pinnedOnly = !pinnedOnly },
                                label = {
                                    Text(
                                        text = stringResource(AMR.strings.source_match_pinned),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            when {
                allEnabledSources.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(AMR.strings.source_match_no_sources),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (onBrowseExtensions != null) {
                            Button(onClick = onBrowseExtensions) {
                                Icon(
                                    imageVector = Icons.Outlined.Extension,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(AMR.strings.sources_browse_extensions))
                            }
                        }
                    }
                }

                matches.isEmpty() && !isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(AMR.strings.source_match_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = matches,
                            key = { "${it.source.id}-${it.initialManga.id}-${it.initialManga.url}" },
                        ) { entry ->
                            val liveManga by produceState(
                                initialValue = entry.initialManga,
                                key1 = entry.initialManga.url,
                                key2 = entry.initialManga.source,
                            ) {
                                getManga.subscribe(entry.initialManga.url, entry.initialManga.source)
                                    .filterNotNull()
                                    .collectLatest { value = it }
                            }
                            val isAdding = liveManga.id in addingIds

                            SourceMatchRow(
                                entry = entry,
                                manga = liveManga,
                                isAdding = isAdding,
                                onClickCard = {
                                    val targetManga = liveManga
                                    if (targetManga.favorite && target.mediaId != null) {
                                        launchIO {
                                            bindAniListIfNeeded(targetManga)
                                        }
                                    }
                                    onOpenAnime(targetManga.id)
                                },
                                onClickAdd = {
                                    val targetManga = liveManga
                                    if (targetManga.favorite || isAdding) return@SourceMatchRow
                                    addingIds.add(targetManga.id)
                                    launchIO {
                                        try {
                                            val categories = getCategories.subscribe()
                                                .firstOrNull()
                                                ?.filterNot { it.isSystemCategory }
                                                .orEmpty()
                                            val defaultCategoryId = libraryPreferences.defaultCategory().get()
                                            val defaultCategory = categories.find {
                                                it.id == defaultCategoryId.toLong()
                                            }
                                            when {
                                                defaultCategory != null -> {
                                                    setMangaCategories.await(
                                                        targetManga.id,
                                                        listOf(defaultCategory.id),
                                                    )
                                                }
                                                defaultCategoryId == 0 || categories.isEmpty() -> {
                                                    setMangaCategories.await(targetManga.id, emptyList())
                                                }
                                            }

                                            setMangaDefaultChapterFlags.await(targetManga)
                                            addTracks.bindEnhancedTrackers(targetManga, entry.source)
                                            bindAniListIfNeeded(targetManga)
                                            updateManga.await(
                                                targetManga.copy(
                                                    favorite = true,
                                                    dateAdded = Instant.now().toEpochMilli(),
                                                ).toMangaUpdate(),
                                            )
                                            AnilistLibraryLookupHolder.notifyTrackChanged()

                                            withUIContext {
                                                context.toast(
                                                    context.contextStringResource(AMR.strings.source_match_added),
                                                )
                                            }

                                            if (libraryPreferences.syncOnAdd().get()) {
                                                try {
                                                    val sManga = targetManga.toSManga()
                                                    val remoteManga = entry.source.getMangaDetails(sManga)
                                                    val chapters = entry.source.getChapterList(sManga)
                                                    updateManga.awaitUpdateFromSource(
                                                        targetManga,
                                                        remoteManga,
                                                        false,
                                                        coverCache,
                                                    )
                                                    syncChaptersWithSource.await(
                                                        chapters,
                                                        targetManga,
                                                        entry.source,
                                                        false,
                                                    )
                                                } catch (e: Exception) {
                                                    targetManga.xLogE("Failed to sync chapters on quick add", e)
                                                }
                                            }
                                        } finally {
                                            withUIContext {
                                                addingIds.remove(targetManga.id)
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (allEnabledSources.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onOpenFullGlobalSearch(target.title) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TravelExplore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(AMR.strings.source_match_full_search))
                }
            }
        }
    }
}

@Composable
private fun SourceMatchRow(
    entry: SourceMatchEntry,
    manga: Manga,
    isAdding: Boolean,
    onClickCard: () -> Unit,
    onClickAdd: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .pureBlackBorder(RoundedCornerShape(12.dp))
            .clickable(onClick = onClickCard),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MangaCover.Book(
                data = manga,
                modifier = Modifier.width(46.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${entry.source.name} · ${entry.source.lang.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (entry.isPinned) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = stringResource(AMR.strings.source_match_pinned),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            when {
                isAdding -> {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                manga.favorite -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdded,
                                contentDescription = stringResource(AMR.strings.anilist_in_library),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(AMR.strings.anilist_in_library),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                else -> {
                    FilledTonalIconButton(
                        onClick = onClickAdd,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(AMR.strings.anilist_add_to_library),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
