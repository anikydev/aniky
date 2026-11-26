package eu.kanade.tachiyomi.ui.anilist.browse

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DiscoverScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<DiscoverScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val notLoggedIn: Boolean = false,
        val hasError: Boolean = false,
        val trending: List<ALBrowseItem> = emptyList(),
        val popular: List<ALBrowseItem> = emptyList(),
        val topRated: List<ALBrowseItem> = emptyList(),
        val favourites: List<ALBrowseItem> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = trending.isEmpty() && popular.isEmpty() && topRated.isEmpty() && favourites.isEmpty()
    }

    private var fetchJob: Job? = null

    @Volatile
    private var loadRequested = false

    init {
        // A login while the tab is open clears the "log in" prompt and loads content, so the
        // warning never lingers after the user signs in (matches Search/Seasonal).
        trackerManager.aniList.isLoggedInFlow
            .onEach { loggedIn -> if (loggedIn && state.value.notLoggedIn) refresh() }
            .launchIn(screenModelScope)
    }

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
        mutableState.update {
            it.copy(
                isLoading = it.isEmpty,
                isRefreshing = !it.isEmpty,
                hasError = false,
                notLoggedIn = false,
            )
        }
        fetchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch the four feeds concurrently. Each is wrapped in feedOrNull and all are
                // awaited via awaitAll, so a failing feed can't leave an un-awaited Deferred behind
                // (the crash the old sequential version guarded against) while the rest still render.
                val results = coroutineScope {
                    listOf("TRENDING_DESC", "POPULARITY_DESC", "SCORE_DESC", "FAVOURITES_DESC")
                        .map { sort -> async { feedOrNull { trackerManager.aniList.browseAnime(1, 10, sort = sort) } } }
                        .awaitAll()
                }
                val trending = results[0]
                val popular = results[1]
                val top = results[2]
                val fav = results[3]
                if (trending == null && popular == null && top == null && fav == null) {
                    throw IllegalStateException("All discover feeds failed")
                }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        trending = trending?.items.orEmpty(),
                        popular = popular?.items.orEmpty(),
                        topRated = top?.items.orEmpty(),
                        favourites = fav?.items.orEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Discover: failed" }
                mutableState.update { it.copy(isLoading = false, isRefreshing = false, hasError = it.isEmpty) }
            }
        }
    }

    /**
     * Runs [block], returning null on failure so one dead feed doesn't sink the others - but never
     * swallows coroutine cancellation (runCatching would, turning a cancelled refresh into a bogus
     * "all feeds failed" error).
     */
    private suspend fun <T> feedOrNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
