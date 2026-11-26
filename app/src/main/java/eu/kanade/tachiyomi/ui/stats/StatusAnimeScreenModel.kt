package eu.kanade.tachiyomi.ui.stats

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.home.HomeItemData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

class StatusAnimeScreenModel(
    private val status: String,
    private val trackerManager: TrackerManager = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
) : StateScreenModel<StatusAnimeScreenModel.State>(State()) {

    enum class SortOption {
        TITLE,
        PROGRESS,
        SCORE,
    }

    @Immutable
    data class StatusAnimeEntry(
        val item: HomeItemData,
        val score100: Int?,
        val totalEpisodes: Int?,
        val episodesWatched: Int = 0,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val hasError: Boolean = false,
        val notLoggedIn: Boolean = false,
        val entries: List<StatusAnimeEntry> = emptyList(),
        val searchQuery: String? = null,
        val sort: SortOption = SortOption.TITLE,
        val isGrid: Boolean = false,
    ) {
        val filteredEntries: List<StatusAnimeEntry>
            get() {
                val query = searchQuery?.trim()?.lowercase(Locale.ROOT).orEmpty()
                val base = if (query.isEmpty()) {
                    entries
                } else {
                    entries.filter { it.item.title.lowercase(Locale.ROOT).contains(query) }
                }
                return when (sort) {
                    SortOption.TITLE -> base.sortedBy { it.item.title.lowercase(Locale.ROOT) }
                    SortOption.PROGRESS -> base.sortedWith(
                        compareByDescending<StatusAnimeEntry> { it.item.progress }
                            .thenByDescending { it.episodesWatched }
                            .thenBy { it.item.title.lowercase(Locale.ROOT) },
                    )
                    SortOption.SCORE -> base.sortedWith(
                        compareByDescending<StatusAnimeEntry> { it.score100 ?: 0 }
                            .thenBy { it.item.title.lowercase(Locale.ROOT) },
                    )
                }
            }
    }

    private var fetchJob: Job? = null

    init {
        refresh()
        trackerManager.aniList.isLoggedInFlow
            .onEach { loggedIn ->
                if (loggedIn) {
                    if (state.value.notLoggedIn) refresh()
                } else {
                    fetchJob?.cancel()
                    mutableState.update { State(isLoading = false, notLoggedIn = true) }
                }
            }
            .launchIn(screenModelScope)
    }

    fun setSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSort(sort: SortOption) {
        mutableState.update { it.copy(sort = sort) }
    }

    fun toggleDisplayMode() {
        mutableState.update { it.copy(isGrid = !it.isGrid) }
    }

    fun refresh(isPullRefresh: Boolean = false) {
        if (!trackerManager.aniList.isLoggedIn) {
            mutableState.update {
                it.copy(isLoading = false, isRefreshing = false, notLoggedIn = true, hasError = false)
            }
            return
        }
        val hasExisting = state.value.entries.isNotEmpty()
        mutableState.update {
            it.copy(
                isLoading = !isPullRefresh && !hasExisting,
                isRefreshing = isPullRefresh || hasExisting,
                notLoggedIn = false,
                hasError = false,
            )
        }
        fetchJob?.cancel()
        fetchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val result = trackerManager.aniList.getUserListWithStatus(status)
                if (!result.complete) {
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            hasError = it.entries.isEmpty(),
                        )
                    }
                    return@launch
                }
                val tracks = result.items

                val libraryTrackIds = getTracks.await()
                    .filter { it.trackerId == TrackerManager.ANILIST }
                    .toList()
                val trackProgressByRemoteId = libraryTrackIds
                    .filter { it.totalEpisodes > 0 }
                    .associate { it.remoteId to (it.lastEpisodeSeen / it.totalEpisodes).toFloat().coerceIn(0f, 1f) }
                val trackedRemoteIds = libraryTrackIds.map { it.remoteId }.toHashSet()

                val libraryEntries = try {
                    mangaRepository.getLibraryManga()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Status list: failed to load library titles" }
                    emptyList()
                }
                val libraryTitles = libraryEntries.map { it.manga.title.lowercase(Locale.ROOT) }.toHashSet()
                val libraryProgressByTitle = libraryEntries
                    .filter { it.totalChapters > 0 }
                    .associate { entry ->
                        entry.manga.title.lowercase(Locale.ROOT) to
                            (entry.readCount.toFloat() / entry.totalChapters).coerceIn(0f, 1f)
                    }

                val mappedEntries = tracks.map { track ->
                    val titleKey = track.title.lowercase(Locale.ROOT)
                    val inLibrary = track.remote_id in trackedRemoteIds || titleKey in libraryTitles
                    val scoreInt = track.score.toInt().takeIf { it > 0 }
                    val formattedRating = if (scoreInt != null) {
                        val ten = scoreInt / 10.0
                        if (ten % 1.0 == 0.0) ten.toInt().toString() else String.format(Locale.US, "%.1f", ten)
                    } else {
                        ""
                    }
                    val remoteFraction = if (track.total_episodes > 0 && track.last_episode_seen > 0) {
                        (track.last_episode_seen / track.total_episodes).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val progressFraction = when {
                        track.remote_id in trackProgressByRemoteId -> trackProgressByRemoteId.getValue(track.remote_id)
                        titleKey in libraryProgressByTitle -> libraryProgressByTitle.getValue(titleKey)
                        else -> remoteFraction
                    }
                    StatusAnimeEntry(
                        item = HomeItemData(
                            id = track.remote_id,
                            inLibrary = inLibrary,
                            progress = progressFraction,
                            title = track.title,
                            subtitle = track.publishing_type,
                            coverUrl = track.cover_url,
                            coverData = track.cover_url,
                            rating = formattedRating,
                            format = track.publishing_type,
                            trackerStatus = status,
                        ),
                        score100 = scoreInt,
                        totalEpisodes = track.total_episodes.toInt().takeIf { it > 0 },
                        episodesWatched = track.last_episode_seen.toInt(),
                    )
                }
                mutableState.update {
                    it.copy(isLoading = false, isRefreshing = false, entries = mappedEntries)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch AniList status list ($status)" }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        hasError = it.entries.isEmpty(),
                    )
                }
            }
        }
    }
}
