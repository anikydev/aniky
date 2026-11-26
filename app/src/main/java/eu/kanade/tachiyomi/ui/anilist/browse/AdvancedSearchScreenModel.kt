package eu.kanade.tachiyomi.ui.anilist.browse

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val SEARCH_FORMATS = listOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC")
val SEARCH_STATUSES = listOf("RELEASING", "FINISHED", "NOT_YET_RELEASED", "CANCELLED", "HIATUS")
val SEARCH_SORTS = listOf("TRENDING_DESC", "POPULARITY_DESC", "SCORE_DESC", "FAVOURITES_DESC", "START_DATE_DESC")
val SEARCH_SEASONS = listOf("WINTER", "SPRING", "SUMMER", "FALL")

class AdvancedSearchScreenModel(
    initialSort: String = "TRENDING_DESC",
    initialGenre: String? = null,
    initialTag: String? = null,
    initialSearch: String? = null,
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<AdvancedSearchScreenModel.State>(
    State(
        sort = initialSort,
        genres = initialGenre?.let { listOf(it) } ?: emptyList(),
        tags = initialTag?.let { listOf(it) } ?: emptyList(),
        query = initialSearch,
    ),
) {

    data class State(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val notLoggedIn: Boolean = false,
        val hasError: Boolean = false,
        val items: List<ALBrowseItem> = emptyList(),
        val page: Int = 0,
        val hasNextPage: Boolean = true,
        // Null = search is closed (toolbar shows the title); "" = search open with no filter.
        val query: String? = null,
        val genres: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val formats: List<String> = emptyList(),
        val statuses: List<String> = emptyList(),
        val season: String? = null,
        val seasonYear: Int? = null,
        val sort: String = "TRENDING_DESC",
        val availableGenres: List<String> = emptyList(),
        val availableTags: List<ALMediaTag> = emptyList(),
        val showFilters: Boolean = false,
    )

    private var searchJob: Job? = null
    private val queryFlow = MutableStateFlow<String?>(null)

    /** Whether [loadIfNeeded] has already started the first fetch for this session. */
    @Volatile
    private var loadRequested = false

    /**
     * Whether the genre/tag filter collections have actually loaded. Empty results (e.g. a
     * logged-out first attempt) don't count, so [loadFilterMetadata] retries them, including on login.
     */
    @Volatile
    private var metadataLoaded = false

    init {
        if (!trackerManager.aniList.isLoggedIn) {
            mutableState.update { it.copy(notLoggedIn = true) }
        }
        // A login while the tab is open clears the prompt and loads results, like Seasonal does.
        trackerManager.aniList.isLoggedInFlow
            .onEach { loggedIn ->
                if (loggedIn && state.value.notLoggedIn) refresh()
                // Genre/tag metadata fetched while logged out comes back empty and is never cached,
                // so (re)load it on login; loadFilterMetadata is a no-op once it has real data.
                if (loggedIn) loadFilterMetadata()
            }
            .launchIn(screenModelScope)
        // drop(1) skips the StateFlow's initial null (replayed to this collector at construction),
        // so only real user query changes trigger a search. The first load is driven by
        // loadIfNeeded(); without this the initial null fired a redundant browse ~500ms after open.
        queryFlow.drop(1).debounce(500).distinctUntilChanged().onEach {
            mutableState.update { s -> s.copy(query = it) }
            search(reset = true)
        }.launchIn(screenModelScope)
    }

    /**
     * Starts the first search (and filter metadata prefetch) the moment the Search tab is opened,
     * and not before — mirroring Seasonal's deferred-load pattern so merely opening the AniList
     * tab does not fire requests. Idempotent.
     */
    fun loadIfNeeded() {
        if (loadRequested) return
        loadRequested = true
        loadFilterMetadata()
        search(reset = true)
    }

    /**
     * Fetches the genre/tag filter collections. No-op once they have actually loaded; retried while
     * still empty (e.g. the first attempt ran logged-out and got nothing back), including on login.
     */
    private fun loadFilterMetadata() {
        if (metadataLoaded) return
        screenModelScope.launch(Dispatchers.IO) {
            val genres = runCatching { trackerManager.aniList.getGenreCollection() }.getOrDefault(emptyList())
            val tags = runCatching { trackerManager.aniList.getTagCollection() }.getOrDefault(emptyList())
            if (genres.isNotEmpty() || tags.isNotEmpty()) {
                metadataLoaded = true
                mutableState.update { it.copy(availableGenres = genres, availableTags = tags) }
            }
        }
    }

    fun onQueryChange(q: String?) {
        // Commit the raw value so the toolbar field mirrors keystrokes; null (sent by the
        // close button) clears the filter and shows unfiltered results again.
        queryFlow.value = q
        mutableState.update { it.copy(query = q) }
    }

    fun toggleFilterDialog() {
        mutableState.update { it.copy(showFilters = !it.showFilters) }
    }

    fun toggleGenre(g: String) {
        mutableState.update { it.copy(genres = if (g in it.genres) it.genres - g else it.genres + g) }
    }

    fun toggleTag(t: String) {
        mutableState.update { it.copy(tags = if (t in it.tags) it.tags - t else it.tags + t) }
    }

    fun toggleFormat(f: String) {
        mutableState.update { it.copy(formats = if (f in it.formats) it.formats - f else it.formats + f) }
    }

    fun toggleStatus(s: String) {
        mutableState.update { it.copy(statuses = if (s in it.statuses) it.statuses - s else it.statuses + s) }
    }

    fun setSeason(s: String?) {
        mutableState.update { it.copy(season = s) }
    }

    fun setYear(y: Int?) {
        mutableState.update { it.copy(seasonYear = y) }
    }

    fun setSort(sort: String) {
        mutableState.update { it.copy(sort = sort) }
    }

    fun clearFilters() {
        mutableState.update {
            it.copy(
                showFilters = false,
                genres = emptyList(),
                tags = emptyList(),
                formats = emptyList(),
                statuses = emptyList(),
                season = null,
                seasonYear = null,
            )
        }
        search(reset = true)
    }

    fun removeGenre(g: String) {
        mutableState.update { it.copy(genres = it.genres - g) }
        search(reset = true)
    }

    fun removeTag(t: String) {
        mutableState.update { it.copy(tags = it.tags - t) }
        search(reset = true)
    }

    fun removeFormat(f: String) {
        mutableState.update { it.copy(formats = it.formats - f) }
        search(reset = true)
    }

    fun removeStatus(s: String) {
        mutableState.update { it.copy(statuses = it.statuses - s) }
        search(reset = true)
    }

    fun removeSeason() {
        mutableState.update { it.copy(season = null) }
        search(reset = true)
    }

    fun removeYear() {
        mutableState.update { it.copy(seasonYear = null) }
        search(reset = true)
    }

    fun applyFilters(
        genres: List<String> = mutableState.value.genres,
        tags: List<String> = mutableState.value.tags,
        formats: List<String> = mutableState.value.formats,
        statuses: List<String> = mutableState.value.statuses,
        season: String? = mutableState.value.season,
        seasonYear: Int? = mutableState.value.seasonYear,
    ) {
        mutableState.update {
            it.copy(
                showFilters = false,
                genres = genres,
                tags = tags,
                formats = formats,
                statuses = statuses,
                season = season,
                seasonYear = seasonYear,
            )
        }
        search(reset = true)
    }

    fun search(reset: Boolean = false) {
        if (!trackerManager.aniList.isLoggedIn) return
        val s = mutableState.value
        // Ignore pagination triggers while any request is in flight (incl. a pull-to-refresh);
        // resets always win. Without isRefreshing a load-more during refresh cancelled the refresh
        // and appended a stale page onto the un-refreshed list.
        if (!reset && (s.isLoading || s.isLoadingMore || s.isRefreshing)) return
        searchJob?.cancel()
        val page = if (reset) 1 else s.page + 1
        if (!reset && !s.hasNextPage) return
        mutableState.update {
            it.copy(
                isLoading = reset && it.items.isEmpty(),
                isRefreshing = reset && it.items.isNotEmpty(),
                isLoadingMore = !reset,
                hasError = false,
            )
        }
        searchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val result = trackerManager.aniList.browseAnime(
                    page = page,
                    search = s.query?.trim()?.takeIf { it.isNotEmpty() },
                    genres = s.genres,
                    tags = s.tags,
                    formats = s.formats,
                    statuses = s.statuses,
                    season = s.season,
                    seasonYear = s.seasonYear,
                    sort = s.sort,
                )
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        // Dedup by id: AniList's dynamic sorts (TRENDING/POPULARITY/FAVOURITES) re-rank
                        // between page requests, so the same media can land on two pages and crash the
                        // grid with a duplicate key.
                        items = if (reset) result.items else (it.items + result.items).distinctBy { item -> item.id },
                        page = page,
                        hasNextPage = result.hasNextPage,
                        // A successful load after logging in must clear the login gate.
                        notLoggedIn = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "AdvancedSearch failed" }
                mutableState.update {
                    // A failed reset must not leave the previous query's page/items/hasNextPage in
                    // place, or the next load-more fetches page+1 of the NEW query and appends it onto
                    // the OLD results (skipping the new query's earlier pages).
                    val items = if (reset) emptyList() else it.items
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        items = items,
                        page = if (reset) 0 else it.page,
                        hasNextPage = if (reset) false else it.hasNextPage,
                        hasError = items.isEmpty(),
                    )
                }
            }
        }
    }

    fun loadMore() = search(reset = false)

    fun refresh() = search(reset = true)
}
