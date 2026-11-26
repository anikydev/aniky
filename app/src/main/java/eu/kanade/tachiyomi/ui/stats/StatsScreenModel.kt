package eu.kanade.tachiyomi.ui.stats

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class StatsScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<StatsScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val hasError: Boolean = false,
        val notLoggedIn: Boolean = false,
        val stats: ALAnimeStats? = null,
        val username: String = "",
        val avatarUrl: String? = null,
    )

    private var fetchJob: Job? = null

    init {
        refresh()
        // Re-run when the user logs in while this screen is alive (login happens in Settings,
        // which is pushed on top of this screen), and clear state on logout.
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

    fun refresh() {
        if (!trackerManager.aniList.isLoggedIn) {
            mutableState.update {
                it.copy(isLoading = false, isRefreshing = false, notLoggedIn = true, hasError = false)
            }
            return
        }
        mutableState.update {
            it.copy(
                isLoading = it.stats == null,
                isRefreshing = it.stats != null,
                notLoggedIn = false,
                hasError = false,
            )
        }
        fetchJob?.cancel()
        fetchJob = screenModelScope.launchIO {
            try {
                val stats = trackerManager.aniList.getUserStats()
                val username = stats.userName?.takeIf { it.isNotBlank() }
                    ?: runCatching { trackerManager.aniList.getUsername() }.getOrDefault("")
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        stats = stats,
                        username = username,
                        avatarUrl = stats.avatarUrl,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch AniList stats" }
                mutableState.update { it.copy(isLoading = false, isRefreshing = false, hasError = true) }
            }
        }
    }
}
