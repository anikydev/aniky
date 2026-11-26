package eu.kanade.tachiyomi.ui.anilist.mylists

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALFuzzyDate
import eu.kanade.tachiyomi.data.track.anilist.dto.ALListStatus
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMyListEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

data class MyListUiItem(
    val listId: Long,
    val mediaId: Long,
    val title: String,
    val coverUrl: String?,
    val status: String,
    val progress: Int,
    val totalEpisodes: Int,
    val score100: Int,
    val updatedAt: Long,
    val format: String,
    val inLibrary: Boolean,
    // Carried so the edit sheet can preserve them; without these the sheet would send repeat=0
    // and wipe the rewatch count on save.
    val repeat: Int = 0,
    val notes: String? = null,
    val startedAt: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val completedAt: ALFuzzyDate = ALFuzzyDate(null, null, null),
)

class MyListsScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) : StateScreenModel<MyListsScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val notLoggedIn: Boolean = false,
        val hasError: Boolean = false,
        val selectedStatus: String = ALListStatus.CURRENT,
        val counts: Map<String, Int> = emptyMap(),
        val items: List<MyListUiItem> = emptyList(),
        val searchQuery: String? = null,
        val sort: Sort = Sort.UPDATED,
        val isGridView: Boolean = false,
        val savingIds: Set<Long> = emptySet(),
    )

    enum class Sort { UPDATED, TITLE, SCORE, PROGRESS }

    private var fetchJob: Job? = null

    // Guards the read-modify-write of [allEntries] so concurrent progress bumps on different rows
    // don't lose each other's update (last-writer-wins on the shared list). @Volatile keeps the
    // latest reference visible to the reads in applyFilter.
    @Volatile
    private var allEntries: List<ALMyListEntry> = emptyList()
    private val entriesMutex = Mutex()

    @Volatile
    private var trackedRemoteIds: Set<Long> = emptySet()

    /** Whether [loadIfNeeded] has already started the first fetch for this session. */
    @Volatile
    private var loadRequested = false

    init {
        // A login while the tab is open clears the "log in" prompt and loads the lists, so the
        // warning never lingers after the user signs in (matches Search/Seasonal/Discover).
        trackerManager.aniList.isLoggedInFlow
            .onEach { loggedIn -> if (loggedIn && state.value.notLoggedIn) refresh() }
            .launchIn(screenModelScope)
    }

    /**
     * Starts the first fetch the moment the My Lists page is opened, and not before —
     * mirroring Seasonal/Search deferred loading so opening the AniList tab stays light.
     * Idempotent.
     */
    fun loadIfNeeded() {
        if (loadRequested) return
        loadRequested = true
        refresh()
    }

    fun refresh() {
        if (!trackerManager.aniList.isLoggedIn) {
            mutableState.update { it.copy(notLoggedIn = true, isLoading = false, isRefreshing = false) }
            return
        }
        fetchJob?.cancel()
        mutableState.update { it.copy(isLoading = allEntries.isEmpty(), isRefreshing = allEntries.isNotEmpty(), hasError = false, notLoggedIn = false) }
        fetchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val tracks = getTracks.await().filter { it.trackerId == TrackerManager.ANILIST }
                trackedRemoteIds = tracks.map { it.remoteId }.toHashSet()
                // One call for all statuses; server returns UPDATED_TIME_DESC.
                val fetched = trackerManager.aniList.getMyAnimeLists(null)
                entriesMutex.withLock { allEntries = fetched }
                val counts = fetched.groupingBy { e -> e.status }.eachCount()
                mutableState.update {
                    // If the default CURRENT tab is empty, jump to the biggest list so
                    // logged-in users never land on a blank screen.
                    val current = it.selectedStatus
                    val auto = if ((counts[current] ?: 0) == 0) {
                        counts.maxByOrNull { e -> e.value }?.key ?: current
                    } else {
                        current
                    }
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        counts = counts,
                        selectedStatus = auto,
                    )
                }
                applyFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "MyLists: failed to fetch" }
                mutableState.update { it.copy(isLoading = false, isRefreshing = false, hasError = allEntries.isEmpty()) }
            }
        }
    }

    fun selectStatus(status: String) {
        mutableState.update { it.copy(selectedStatus = status) }
        applyFilter()
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    fun setSort(sort: Sort) {
        mutableState.update { it.copy(sort = sort) }
        applyFilter()
    }

    fun toggleViewMode() {
        mutableState.update { it.copy(isGridView = !it.isGridView) }
    }

    private fun applyFilter() {
        // Derive items inside update{} from the same state that gets written, so the final items
        // always match the final selectedStatus/sort/query. Reading state first and writing after
        // let a concurrent status change (main thread) and a refresh (IO) last-writer-win into a
        // list that disagreed with the highlighted status.
        mutableState.update { s ->
            // Search, TITLE-sort and the row label must all use the SAME title. Otherwise searching
            // and sorting on userPreferred while the row displays english makes the search box return
            // no matches and TITLE order look random. Honour the detail/browse "show English" setting.
            val titleLanguage = uiPreferences.anilistTitleLanguage().get()
            fun ALMyListEntry.displayTitle(): String {
                val t = media?.title ?: return ""
                return titleLanguage.pick(
                    romaji = t.romaji,
                    english = t.english,
                    native = t.native,
                    userPreferred = t.userPreferred,
                )
            }
            var list = allEntries.filter { it.status == s.selectedStatus }
            val activeQuery = s.searchQuery
            if (!activeQuery.isNullOrBlank()) {
                list = list.filter { it.displayTitle().contains(activeQuery, ignoreCase = true) }
            }
            list = when (s.sort) {
                Sort.TITLE -> list.sortedBy { it.displayTitle().lowercase(Locale.ROOT) }
                Sort.SCORE -> list.sortedByDescending { it.scoreRaw }
                Sort.PROGRESS -> list.sortedByDescending { it.progress }
                Sort.UPDATED -> list.sortedByDescending { it.updatedAt }
            }
            val ui = list.map { e ->
                MyListUiItem(
                    listId = e.id,
                    mediaId = e.mediaId.takeIf { it != 0L } ?: (e.media?.id ?: 0L),
                    title = e.displayTitle(),
                    coverUrl = e.media?.coverImage?.large,
                    status = e.status,
                    progress = e.progress,
                    totalEpisodes = e.media?.episodes?.toInt() ?: 0,
                    score100 = e.scoreRaw,
                    updatedAt = e.updatedAt,
                    format = e.media?.format ?: "",
                    inLibrary = trackedRemoteIds.contains(e.media?.id ?: e.mediaId),
                    repeat = e.repeat,
                    notes = e.notes,
                    startedAt = e.startedAt,
                    completedAt = e.completedAt,
                )
            }
            s.copy(items = ui)
        }
    }

    /** Inline +/- progress stepper with optimistic UI + server save. */
    fun bumpProgress(item: MyListUiItem, delta: Int) {
        val newProgress = (item.progress + delta).coerceAtLeast(0)
        if (item.totalEpisodes > 0) {
            if (newProgress > item.totalEpisodes) return
        }
        // Per-item in-flight guard: a second tap while the first save is running would clear the
        // spinner early and race a double increment computed from the same pre-tap progress. Keyed
        // on listId (the stable per-entry id) to match the list key - malformed entries can share
        // mediaId 0L, which would cross-wire the spinner and guard between two rows.
        // Atomically claim this entry: getAndUpdate adds the id and returns the PRIOR state in one
        // step, so two fast taps can't both pass the guard before either sets the flag. The old
        // check-then-set was split across the coroutine dispatch, so a double-tap lost an increment
        // and issued two identical writes.
        val alreadySaving = item.listId in mutableState.getAndUpdate {
            it.copy(savingIds = it.savingIds + item.listId)
        }.savingIds
        if (alreadySaving) return
        screenModelScope.launch(Dispatchers.IO) {
            try {
                trackerManager.aniList.saveEntryFull(
                    mediaId = item.mediaId,
                    status = item.status,
                    progress = newProgress,
                )
                entriesMutex.withLock {
                    // Match by the stable listId (like the in-flight guard and the LazyColumn key),
                    // not mediaId, which malformed entries can share as 0L and would cross-wire.
                    allEntries = allEntries.map {
                        if (it.id == item.listId) it.copy(progress = newProgress) else it
                    }
                }
                applyFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "MyLists: progress save failed" }
            } finally {
                mutableState.update { it.copy(savingIds = it.savingIds - item.listId) }
            }
        }
    }

    fun deleteEntry(item: MyListUiItem, onSuccess: (() -> Unit)? = null) {
        val alreadySaving = item.listId in mutableState.getAndUpdate {
            it.copy(savingIds = it.savingIds + item.listId)
        }.savingIds
        if (alreadySaving) return

        screenModelScope.launch(Dispatchers.IO) {
            try {
                withContext(NonCancellable) {
                    trackerManager.aniList.delete(
                        tachiyomi.domain.track.model.Track(
                            id = 0,
                            mangaId = 0,
                            trackerId = TrackerManager.ANILIST,
                            remoteId = item.mediaId,
                            libraryId = item.listId.takeIf { it != 0L },
                            title = item.title,
                            lastChapterRead = 0.0,
                            totalChapters = 0,
                            status = 0,
                            score = 0.0,
                            remoteUrl = "",
                            startDate = 0,
                            finishDate = 0,
                            private = false,
                        ),
                    )
                }
                val newCounts = entriesMutex.withLock {
                    allEntries = allEntries.filterNot { it.id == item.listId }
                    allEntries.groupingBy { e -> e.status }.eachCount()
                }
                mutableState.update { it.copy(counts = newCounts) }
                applyFilter()
                if (onSuccess != null) {
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "MyLists: delete entry failed" }
            } finally {
                mutableState.update { it.copy(savingIds = it.savingIds - item.listId) }
            }
        }
    }

    fun onEntrySaved() {
        refresh()
    }
}
