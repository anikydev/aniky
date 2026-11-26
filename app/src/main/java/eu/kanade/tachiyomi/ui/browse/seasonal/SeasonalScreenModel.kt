package eu.kanade.tachiyomi.ui.browse.seasonal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.ui.home.HomeItemData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

val ANILIST_SEASONS = listOf("WINTER", "SPRING", "SUMMER", "FALL")

/** Index into [ANILIST_SEASONS] for the season the given date falls in, on AniList's boundaries:
 *  WINTER = Dec-Feb, SPRING = Mar-May, SUMMER = Jun-Aug, FALL = Sep-Nov. */
fun currentSeasonIndex(date: java.time.LocalDate = java.time.LocalDate.now()): Int = when (date.monthValue) {
    12, in 1..2 -> 0
    in 3..5 -> 1
    in 6..8 -> 2
    else -> 3
}

/**
 * Year a "current year" pick resolves to for [seasonIndex] (an index into [ANILIST_SEASONS]). On
 * AniList's boundaries December belongs to *next* year's WINTER, so only WINTER rolls forward in
 * December; SPRING/SUMMER/FALL stay in the calendar year. Resolve against the season being shown,
 * not just the current one, or a specific-season pick in December lands a year off (SUMMER picked in
 * Dec 2026 is Summer 2026, not 2027).
 */
fun seasonYear(seasonIndex: Int, date: java.time.LocalDate = java.time.LocalDate.now()): Int =
    if (date.monthValue == 12 && seasonIndex == 0) date.year + 1 else date.year

/** Year the current season belongs to: on AniList's boundaries December is next year's WINTER. */
fun currentSeasonYear(date: java.time.LocalDate = java.time.LocalDate.now()): Int =
    seasonYear(currentSeasonIndex(date), date)

val ANILIST_FORMATS = listOf(
    "ALL",
    "TV",
    "TV_SHORT",
    "MOVIE",
    "SPECIAL",
    "OVA",
    "ONA",
)

val ANILIST_STATUS = listOf("", "RELEASING", "FINISHED", "NOT_YET_RELEASED")

/** Preference sentinel: -2 = all seasons, -1 = current season, 0..3 = specific season */
const val SEASON_ALL = -2

/**
 * What a stored (season, year) pair means, decoded once. [apiSeason] is null for "all seasons", which
 * is the shape AniList wants; [label] and [index] are what the UI shows and highlights. Decoding the
 * sentinel per call site is how the header, the request and the cache key drifted apart.
 */
data class SeasonSelection(
    val apiSeason: String?,
    val label: String,
    val index: Int,
    val year: Int,
) {
    val isAllSeasons: Boolean get() = apiSeason == null

    val cacheKey: String get() = "$label-$year"

    companion object {
        fun of(prefSeason: Int, prefYear: Int, date: java.time.LocalDate = java.time.LocalDate.now()): SeasonSelection {
            if (prefSeason == SEASON_ALL) {
                // The arrows still need somewhere to step from, so the index stays the real season.
                val year = if (prefYear > 0) prefYear else currentSeasonYear(date)
                return SeasonSelection(apiSeason = null, label = "ALL", index = currentSeasonIndex(date), year = year)
            }
            val index = prefSeason.takeIf { it in ANILIST_SEASONS.indices } ?: currentSeasonIndex(date)
            // A "current year" pick (prefYear <= 0) is resolved for the *selected* season, not the
            // current one: in December only WINTER belongs to next year, so resolving every season to
            // currentSeasonYear() put a specific-season pick a year ahead (see [seasonYear]).
            val year = if (prefYear > 0) prefYear else seasonYear(index, date)
            val name = ANILIST_SEASONS[index]
            return SeasonSelection(apiSeason = name, label = name, index = index, year = year)
        }
    }
}

class SeasonalScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
) : StateScreenModel<SeasonalScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val hasError: Boolean = false,
        val notLoggedIn: Boolean = false,
        val seasonalList: List<HomeItemData> = emptyList(),
        val totalItems: Int = 0,
        val seasonalTitle: String? = null,
        val season: Int = -1,
        val year: Int = -1,
        val format: Int = -1,
        val sort: Int = 0,
        val sortAscending: Boolean = false,
        val statusFilter: Int = 0,
        val selectedGenres: Set<String> = emptySet(),
        val availableGenres: List<String> = emptyList(),
        val viewMode: Int = 0,
        val gridColumns: Int = 3,
        val searchQuery: String? = null,
        val showFilterDialog: Boolean = false,
    )

    private val seasonalState = MutableStateFlow<List<HomeItemData>>(emptyList())
    private var searchJob: Job? = null
    private var fetchJob: Job? = null

    /** Identifies the newest in-flight fetch so superseded responses can be discarded. */
    @Volatile
    private var fetchRequestId = 0

    /** [fetchKey] of the data currently held in [seasonalState], or null if it isn't loaded. */
    @Volatile
    private var loadedKey: String? = null

    /** Whether [loadIfNeeded] has already started the first fetch for this session. */
    @Volatile
    private var loadRequested = false

    /** Serializes state read-modify-writes so concurrent updates can't clobber UI flags. */
    private val stateLock = Any()

    /** Identifies the newest in-flight list compute so superseded results are discarded. */
    private val computeRequestId = AtomicInteger()

    @Volatile
    private var computeJob: Job? = null

    init {
        // No fetch here: it is deferred to [loadIfNeeded], fired when the Seasonal tab is actually
        // viewed, so merely opening Browse (which builds this model) does not hit AniList - and, on
        // "All seasons", does not fire a burst of paginated requests. A login while the screen is
        // open still refreshes, but only after the tab has been opened (notLoggedIn is set by a fetch).
        trackerManager.aniList.isLoggedInFlow
            .onEach { loggedIn -> if (loggedIn && state.value.notLoggedIn) fetchSeasonal() }
            .launchIn(screenModelScope)
    }

    /**
     * Starts the first fetch the moment the Seasonal tab is opened, and not before. Idempotent, so
     * returning to the tab does not re-fetch; use [refresh] or a filter change for that.
     */
    fun loadIfNeeded() {
        if (loadRequested) return
        loadRequested = true
        fetchSeasonal()
    }

    private fun updateState(transform: (State) -> State) {
        synchronized(stateLock) {
            mutableState.value = transform(mutableState.value)
        }
    }

    fun setSeason(season: Int, year: Int) {
        uiPreferences.homeSeasonalSeason.set(season)
        uiPreferences.homeSeasonalYear.set(year)
        fetchSeasonal()
    }

    fun setSeasonFormat(format: Int) {
        uiPreferences.homeSeasonalFormat.set(format)
        fetchSeasonal()
    }

    fun setSort(sort: Int) {
        uiPreferences.homeSeasonalSort.set(sort)
        updateStateWithData()
    }

    fun toggleSortOrder() {
        val current = uiPreferences.homeSeasonalSortAscending.get()
        uiPreferences.homeSeasonalSortAscending.set(!current)
        updateStateWithData()
    }

    fun setSortAscending(ascending: Boolean) {
        uiPreferences.homeSeasonalSortAscending.set(ascending)
        updateStateWithData()
    }

    fun setAnimeStatus(mediaId: Long, status: String, onDone: ((Boolean) -> Unit)? = null) {
        if (!trackerManager.aniList.isLoggedIn) {
            onDone?.invoke(false)
            return
        }
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val entryId = trackerManager.aniList.saveEntryFull(mediaId = mediaId, status = status)
                synchronized(stateLock) {
                    val currentList = seasonalState.value
                    val updatedList = currentList.map { item ->
                        if (item.id == mediaId) {
                            item.copy(trackerStatus = status, mediaListId = entryId ?: item.mediaListId)
                        } else {
                            item
                        }
                    }
                    seasonalState.value = updatedList
                    loadedKey?.let { cache[it] = updatedList }
                }
                updateStateWithData()
                withContext(Dispatchers.Main) {
                    onDone?.invoke(true)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to update anime status for $mediaId to $status" }
                withContext(Dispatchers.Main) {
                    onDone?.invoke(false)
                }
            }
        }
    }

    fun removeAnimeStatus(mediaId: Long, listId: Long?, onDone: ((Boolean) -> Unit)? = null) {
        if (!trackerManager.aniList.isLoggedIn) {
            onDone?.invoke(false)
            return
        }
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val entryId = listId?.takeIf { it > 0 } ?: run {
                    val details = trackerManager.aniList.getAnimeDetails(mediaId)
                    details?.mediaListEntry?.id?.takeIf { it > 0 }
                }
                if (entryId != null && entryId > 0) {
                    trackerManager.aniList.deleteEntry(entryId)
                }
                synchronized(stateLock) {
                    val currentList = seasonalState.value
                    val updatedList = currentList.map { item ->
                        if (item.id == mediaId) {
                            item.copy(trackerStatus = null, mediaListId = null)
                        } else {
                            item
                        }
                    }
                    seasonalState.value = updatedList
                    loadedKey?.let { cache[it] = updatedList }
                }
                updateStateWithData()
                withContext(Dispatchers.Main) {
                    onDone?.invoke(true)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to remove anime entry for $mediaId" }
                withContext(Dispatchers.Main) {
                    onDone?.invoke(false)
                }
            }
        }
    }

    fun setStatusFilter(status: Int) {
        uiPreferences.homeSeasonalStatus.set(status)
        updateStateWithData()
    }

    fun setGenres(genres: Set<String>) {
        uiPreferences.homeSeasonalGenres.set(genres)
        updateStateWithData()
    }

    fun applyFilters(season: Int, year: Int, format: Int, status: Int, genres: Set<String>) {
        val prevKey = fetchKey(uiPreferences)
        uiPreferences.homeSeasonalSeason.set(season)
        uiPreferences.homeSeasonalYear.set(year)
        uiPreferences.homeSeasonalFormat.set(format)
        uiPreferences.homeSeasonalStatus.set(status)
        uiPreferences.homeSeasonalGenres.set(genres)
        if (fetchKey(uiPreferences) != prevKey || loadedKey == null) {
            fetchSeasonal()
        } else {
            updateStateWithData()
        }
    }

    /** Clears every filter and fetches once, instead of setSeason + setSeasonFormat each firing a fetch. */
    fun clearAllFilters() {
        uiPreferences.homeSeasonalSeason.set(-1)
        uiPreferences.homeSeasonalYear.set(-1)
        uiPreferences.homeSeasonalFormat.set(-1)
        uiPreferences.homeSeasonalStatus.set(0)
        uiPreferences.homeSeasonalGenres.set(emptySet())
        fetchSeasonal()
    }

    fun setViewMode(viewMode: Int) {
        uiPreferences.homeSeasonalViewMode.set(viewMode)
        updateStateWithData()
    }

    fun setGridColumns(columns: Int) {
        uiPreferences.homeSeasonalGridColumns.set(columns)
        updateStateWithData()
    }

    fun search(query: String?) {
        // Commit the raw value immediately so the toolbar field mirrors keystrokes. An empty
        // string means "search mode is open with no filter" (the search icon sends ""); only the
        // close button sends null. Normalizing "" to null here would snap the field shut again.
        updateState { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = screenModelScope.launch {
            delay(300)
            updateStateWithData(searchQuery = query)
        }
    }

    fun toggleFilterDialog() {
        updateState { it.copy(showFilterDialog = !it.showFilterDialog) }
    }

    fun refresh() {
        updateState { it.copy(isRefreshing = true) }
        fetchSeasonal()
    }

    private fun fetchSeasonal() {
        // Seasonal reads go through the authenticated AniList client (see
        // AnilistApi.getSeasonalAnime): an anonymous request is refused by AniList's Cloudflare, so
        // without a session there is nothing to fetch. Show the login prompt instead of a doomed load.
        if (!trackerManager.aniList.isLoggedIn) {
            fetchJob?.cancel()
            updateState {
                it.copy(notLoggedIn = true, isLoading = false, isRefreshing = false, hasError = false)
            }
            return
        }

        val prefSeason = uiPreferences.homeSeasonalSeason.get()
        val prefYear = uiPreferences.homeSeasonalYear.get()
        val prefFormat = uiPreferences.homeSeasonalFormat.get()
        val selection = SeasonSelection.of(prefSeason, prefYear)
        val season = selection.apiSeason
        val year = selection.year
        val format = if (prefFormat > 0) ANILIST_FORMATS.getOrElse(prefFormat) { "" } else null
        // Resolved once here: reading it again when the response lands would key the result on
        // whatever season the user has since navigated to.
        val cacheKey = fetchKey(uiPreferences)
        val requestId = ++fetchRequestId

        fetchJob?.cancel()

        // Show the newly selected season straight away — seeded from cache when we have it — so the
        // header and list never lag a network round-trip behind the user's tap.
        if (cacheKey != loadedKey) {
            val cached = cache[cacheKey]
            seasonalState.value = cached ?: emptyList()
            loadedKey = cacheKey.takeIf { cached != null }
        }
        updateState {
            it.copy(
                isLoading = seasonalState.value.isEmpty(),
                hasError = false,
                notLoggedIn = false,
            )
        }
        updateStateWithData()

        fetchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                logcat(LogPriority.DEBUG) {
                    "Seasonal tab: fetching AniList seasonal anime (${season ?: "ALL"} $year, format=$format)..."
                }
                val seasonalAnime = trackerManager.aniList.getSeasonalAnime(season, year, format)
                logcat(LogPriority.DEBUG) {
                    "Seasonal tab: AniList seasonal anime returned ${seasonalAnime.size} items"
                }

                val libraryTrackIds = getTracks.await()
                    .filter { it.trackerId == TrackerManager.ANILIST }
                    .toList()
                val localTrackByRemoteId = libraryTrackIds.associateBy { it.remoteId }
                val trackProgressByRemoteId = libraryTrackIds
                    .filter { it.totalEpisodes > 0 }
                    .associate { it.remoteId to (it.lastEpisodeSeen / it.totalEpisodes).toFloat().coerceIn(0f, 1f) }
                val trackedRemoteIds = libraryTrackIds.map { it.remoteId }.toHashSet()

                val libraryEntries = try {
                    getLibraryManga.await()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Seasonal tab: failed to load library titles" }
                    emptyList()
                }
                val libraryTitles = libraryEntries.map { it.manga.title.lowercase(Locale.ROOT) }.toHashSet()
                val libraryProgressByTitle = libraryEntries
                    .filter { it.totalChapters > 0 }
                    .associate { entry ->
                        entry.manga.title.lowercase(Locale.ROOT) to
                            (entry.readCount.toFloat() / entry.totalChapters).coerceIn(0f, 1f)
                    }

                val seasonLabel = season?.replaceFirstChar { it.titlecase() } ?: "ALL"
                val titleLanguage = uiPreferences.anilistTitleLanguage().get()
                val items = seasonalAnime.map { track ->
                    val titleKey = track.title.lowercase(Locale.ROOT)
                    val inLibrary = track.remote_id in trackedRemoteIds || titleKey in libraryTitles
                    val localTrack = localTrackByRemoteId[track.remote_id]
                    val resolvedStatus = track.trackerStatus
                        ?: localTrack?.status?.let { localStatusToAnilist(it) }
                    val resolvedListId = track.mediaListId ?: localTrack?.libraryId
                    HomeItemData(
                        id = track.remote_id,
                        inLibrary = inLibrary,
                        progress = if (track.remote_id in trackProgressByRemoteId) {
                            trackProgressByRemoteId.getValue(track.remote_id)
                        } else {
                            libraryProgressByTitle[titleKey] ?: 0f
                        },
                        title = titleLanguage.pick(
                            romaji = track.romajiTitle,
                            english = track.englishTitle,
                            native = track.nativeTitle,
                            userPreferred = track.title,
                        ),
                        subtitle = if (season == null) {
                            val itemSeason = track.season
                                .takeIf { it.isNotBlank() }
                                ?.replaceFirstChar { it.titlecase() }
                            listOfNotNull(itemSeason, track.seasonYear.takeIf { it > 0 }).joinToString(" ")
                        } else {
                            seasonLabel
                        },
                        coverUrl = track.cover_url,
                        coverData = track.cover_url,
                        rating = if (track.score > 0) {
                            track.score.toInt().toString()
                        } else {
                            ""
                        },
                        popularity = track.popularity,
                        publishingStatus = track.publishing_status,
                        format = track.publishing_type,
                        nextAiringEpisode = track.next_airing_episode,
                        nextAiringAt = track.next_airing_at,
                        genres = track.genres,
                        hasTrailer = track.hasTrailer,
                        studio = track.studio,
                        favourites = track.favourites,
                        trackerStatus = resolvedStatus,
                        mediaListId = resolvedListId,
                    )
                }
                // Re-check and write under the lock so a stale fetch can never clobber a
                // freshly seeded season (check-then-act must be atomic).
                synchronized(stateLock) {
                    if (requestId != fetchRequestId) return@launch
                    cache[cacheKey] = items
                    loadedKey = cacheKey
                    seasonalState.value = items
                }
                updateState {
                    it.copy(
                        hasError = false,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
                updateStateWithData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, throwable = e) { "Seasonal tab: failed to fetch AniList seasonal anime" }
                // Never leave another season's titles on screen under this season's header. A failed
                // refresh of what's already shown keeps its data; a failed switch clears it.
                synchronized(stateLock) {
                    if (requestId != fetchRequestId) return@launch
                    if (cacheKey != loadedKey) {
                        seasonalState.value = emptyList()
                    }
                }
                updateState {
                    it.copy(
                        hasError = true,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
                updateStateWithData()
            }
        }
    }

    private fun updateStateWithData(searchQuery: String? = state.value.searchQuery) {
        // Atomic, not `++` on a @Volatile Int: this runs on the main thread and on the IO fetch
        // coroutine, and two callers reading the same value would both pass the guard below, letting
        // a superseded compute publish over the newest one.
        val requestId = computeRequestId.incrementAndGet()
        computeJob?.cancel()
        computeJob = screenModelScope.launch {
            val seasonalItems = seasonalState.value
            val computed = withContext(Dispatchers.Default) {
                computeUiState(seasonalItems, searchQuery)
            }
            if (requestId != computeRequestId.get()) return@launch
            updateState { current ->
                current.copy(
                    seasonalList = computed.seasonalList,
                    totalItems = computed.totalItems,
                    seasonalTitle = computed.seasonalTitle,
                    season = computed.season,
                    year = computed.year,
                    format = computed.format,
                    sort = computed.sort,
                    sortAscending = computed.sortAscending,
                    statusFilter = computed.statusFilter,
                    selectedGenres = computed.selectedGenres,
                    availableGenres = computed.availableGenres,
                    viewMode = computed.viewMode,
                    gridColumns = computed.gridColumns,
                    searchQuery = searchQuery,
                )
            }
        }
    }

    private fun computeUiState(seasonalItems: List<HomeItemData>, searchQuery: String?): ComputedSeasonalUi {
        val prefSeason = uiPreferences.homeSeasonalSeason.get()
        val prefYear = uiPreferences.homeSeasonalYear.get()
        val selection = SeasonSelection.of(prefSeason, prefYear)
        val effectiveYear = selection.year
        val seasonalTitle = "${selection.label} $effectiveYear"

        val prefSort = uiPreferences.homeSeasonalSort.get()
        val prefSortAscending = uiPreferences.homeSeasonalSortAscending.get()
        val prefStatus = uiPreferences.homeSeasonalStatus.get()
        val prefViewMode = uiPreferences.homeSeasonalViewMode.get()
        val prefGenres = uiPreferences.homeSeasonalGenres.get()
        val prefGridColumns = uiPreferences.homeSeasonalGridColumns.get()

        val availableGenres = seasonalItems
            .flatMap { it.genres }
            .distinct()
            .sorted()

        var list = seasonalItems
        val status = ANILIST_STATUS.getOrElse(prefStatus) { "" }
        if (status.isNotEmpty()) {
            list = list.filter { it.publishingStatus == status }
        }
        if (prefGenres.isNotEmpty()) {
            list = list.filter { item -> prefGenres.all { selected -> selected in item.genres } }
        }
        val query = searchQuery?.trim().orEmpty()
        if (query.isNotEmpty()) {
            list = list.filter { it.title.contains(query, ignoreCase = true) }
        }
        list = when (prefSort) {
            1 -> if (prefSortAscending) {
                list.sortedBy { it.rating.toIntOrNull() ?: 0 }
            } else {
                list.sortedByDescending { it.rating.toIntOrNull() ?: 0 }
            }
            2 -> if (prefSortAscending) {
                list.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            } else {
                list.sortedBy { it.title.lowercase(Locale.ROOT) }
            }
            3 -> if (prefSortAscending) {
                list.sortedByDescending { if (it.nextAiringAt > 0) it.nextAiringAt else Long.MAX_VALUE }
            } else {
                list.sortedBy { if (it.nextAiringAt > 0) it.nextAiringAt else Long.MAX_VALUE }
            }
            else -> if (prefSortAscending) {
                list.sortedBy { it.popularity }
            } else {
                list.sortedByDescending { it.popularity }
            }
        }

        return ComputedSeasonalUi(
            availableGenres = availableGenres,
            seasonalList = list,
            totalItems = seasonalItems.size,
            seasonalTitle = seasonalTitle,
            season = prefSeason,
            year = prefYear,
            format = uiPreferences.homeSeasonalFormat.get(),
            sort = prefSort,
            sortAscending = prefSortAscending,
            statusFilter = prefStatus,
            selectedGenres = prefGenres,
            viewMode = prefViewMode,
            gridColumns = prefGridColumns,
        )
    }

    private fun localStatusToAnilist(status: Long): String? = when (status) {
        Anilist.WATCHING -> "CURRENT"
        Anilist.PLAN_TO_WATCH -> "PLANNING"
        Anilist.COMPLETED -> "COMPLETED"
        Anilist.ON_HOLD -> "PAUSED"
        Anilist.DROPPED -> "DROPPED"
        Anilist.REWATCHING -> "REPEATING"
        else -> null
    }

    private data class ComputedSeasonalUi(
        val availableGenres: List<String>,
        val seasonalList: List<HomeItemData>,
        val totalItems: Int,
        val seasonalTitle: String?,
        val season: Int,
        val year: Int,
        val format: Int,
        val sort: Int,
        val sortAscending: Boolean,
        val statusFilter: Int,
        val selectedGenres: Set<String>,
        val viewMode: Int,
        val gridColumns: Int,
    )

    companion object {
        // Read from the main thread (fetchSeasonal) and written from Dispatchers.IO (the fetch
        // coroutine), so it has to be synchronized. Access-ordered so that a full cache evicts the
        // least recently used season/year/format combination first.
        private val cache = Collections.synchronizedMap(
            object : LinkedHashMap<String, List<HomeItemData>>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<HomeItemData>>?): Boolean {
                    return size > MAX_CACHE_ENTRIES
                }
            },
        )
        private const val MAX_CACHE_ENTRIES = 8

        fun clearCache() {
            cache.clear()
        }

        private fun fetchKey(uiPreferences: UiPreferences): String {
            val selection = SeasonSelection.of(
                uiPreferences.homeSeasonalSeason.get(),
                uiPreferences.homeSeasonalYear.get(),
            )
            val prefFormat = uiPreferences.homeSeasonalFormat.get()
            val format = if (prefFormat > 0) ANILIST_FORMATS.getOrElse(prefFormat) { "" } else null
            return "${selection.cacheKey}-$format"
        }
    }
}
