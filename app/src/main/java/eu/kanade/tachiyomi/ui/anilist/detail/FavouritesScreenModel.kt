package eu.kanade.tachiyomi.ui.anilist.detail

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FavouritesScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<FavouritesScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val notLoggedIn: Boolean = false,
        val hasError: Boolean = false,
        val items: List<ALBrowseItem> = emptyList(),
    )

    private var fetchJob: Job? = null

    /** Whether the first fetch has been requested for this session. */
    @Volatile
    private var loadRequested = false

    init {
        // Clear the "log in" prompt and load favourites the moment the user signs in, so the
        // warning never lingers after login (matches Search/Seasonal/Discover).
        trackerManager.aniList.isLoggedInFlow
            .onEach { loggedIn -> if (loggedIn && state.value.notLoggedIn) refresh() }
            .launchIn(screenModelScope)
    }

    /**
     * Fetches favourites once, on first open. Later tab switches are no-ops so the whole list isn't
     * re-paged from the network on every visit; pull-to-refresh and login recovery still force a
     * re-fetch via [refresh]. Idempotent.
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
        mutableState.update {
            it.copy(
                isLoading = it.items.isEmpty(),
                isRefreshing = it.items.isNotEmpty(),
                hasError = false,
                notLoggedIn = false,
            )
        }
        fetchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val items = trackerManager.aniList.getFavouriteAnime()
                mutableState.update { it.copy(isLoading = false, isRefreshing = false, items = items) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Favourites failed" }
                mutableState.update {
                    it.copy(isLoading = false, isRefreshing = false, hasError = it.items.isEmpty())
                }
            }
        }
    }
}
