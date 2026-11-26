package eu.kanade.tachiyomi.ui.notifications

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AniListNotificationStore
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AniListNotificationsScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val notificationStore: AniListNotificationStore = Injekt.get(),
) : StateScreenModel<AniListNotificationsScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val hasError: Boolean = false,
        val notLoggedIn: Boolean = false,
        val items: List<ALNotification> = emptyList(),
        // Locally read notification ids. AniList exposes no per-item read flag, so an
        // entry stays unread until tapped - viewing the feed or refreshing never clears it.
        val readIds: Set<Long> = emptySet(),
        // AniList media id -> local library anime id, for AniList-tracked entries.
        val animeIdByAnilistId: Map<Long, Long> = emptyMap(),
    )

    private var fetchJob: Job? = null

    init {
        if (!trackerManager.aniList.isLoggedIn) {
            mutableState.update { it.copy(isLoading = false, notLoggedIn = true) }
        } else {
            // Show the previous feed instantly when there is one, then always trigger a
            // background refresh so new notifications are fetched when opening the screen.
            val cached = notificationStore.getCached()
            if (cached.isNotEmpty()) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        items = cached,
                        readIds = notificationStore.getReadIds(),
                    )
                }
                refreshAnimeMap()
            }
            refresh()
        }
        // Re-run when the user logs in while this screen is alive (login happens in Settings, which
        // is pushed on top of this screen). Mirrors the Statistics screen.
        trackerManager.aniList.isLoggedInFlow
            .onEach { loggedIn ->
                if (loggedIn) {
                    if (state.value.notLoggedIn) refresh()
                } else {
                    // Logout clears the store on a separate instance; drop the in-memory feed too so
                    // the screen doesn't keep showing (and re-marking read into) a cleared account.
                    fetchJob?.cancel()
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            notLoggedIn = true,
                            items = emptyList(),
                            readIds = emptySet(),
                            animeIdByAnilistId = emptyMap(),
                        )
                    }
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
            // Keep an already-shown feed visible under the pull-refresh spinner; only show the
            // full-screen loader on the first load.
            it.copy(
                isLoading = it.items.isEmpty(),
                isRefreshing = it.items.isNotEmpty(),
                notLoggedIn = false,
                hasError = false,
            )
        }
        fetchJob?.cancel()
        fetchJob = screenModelScope.launchIO {
            try {
                val notifications = trackerManager.aniList.getNotifications()
                // First sync only: seed already-read markers from AniList's own unread counter
                // BEFORE publishing the feed to the cache. The unread badge reacts to the cache, so
                // saving first would briefly compute it against an unseeded feed and spike it to the
                // full feed size. Afterwards read state changes exclusively via markAsRead().
                // Only hit the unread-count endpoint when a backfill can still run; once seeded,
                // backfillReadIds early-returns, so the request would be wasted on every refresh.
                val serverUnread = if (notificationStore.isSeeded()) {
                    -1
                } else {
                    // getUnreadNotificationCount() already returns -1 on real errors and only rethrows
                    // CancellationException; do NOT wrap it in runCatching, or a first sync cancelled by
                    // logout would swallow the cancellation and still fall through to saveCache below,
                    // re-persisting the just-cleared feed (and leaking it to the next account).
                    trackerManager.aniList.getUnreadNotificationCount()
                }
                notificationStore.backfillReadIds(notifications, serverUnread)
                notificationStore.saveCache(notifications)
                // Map the viewer's AniList-tracked library entries so an aired-episode notification
                // can jump straight to the library anime instead of the AniList detail screen.
                val animeIdByAnilistId = getTracks.await()
                    .filter { it.trackerId == TrackerManager.ANILIST }
                    .associate { it.remoteId to it.animeId }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        items = notifications,
                        readIds = notificationStore.getReadIds(),
                        animeIdByAnilistId = animeIdByAnilistId,
                    )
                }
                // Read markers are deliberately left alone: brand-new arrivals default to
                // unread, previously tapped ones stay read.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch AniList notifications" }
                mutableState.update {
                    // A failed refresh must not nuke an already-shown cached feed into an
                    // error screen; only a load with nothing to show is an error.
                    it.copy(isLoading = false, isRefreshing = false, hasError = it.items.isEmpty())
                }
            }
        }
    }

    /**
     * The only thing that clears an entry's unread state. Called when the user taps the
     * notification row, before any navigation happens.
     */
    fun markAsRead(notification: ALNotification) {
        val id = notification.id ?: return
        if (id in state.value.readIds) return
        notificationStore.markRead(id)
        mutableState.update { it.copy(readIds = it.readIds + id) }
    }

    /** Marks the whole visible feed read. Backs a future mark-all-read action. */
    fun markAllRead() {
        val ids = state.value.items.mapNotNull { it.id }
        if (ids.isEmpty()) return
        notificationStore.markAllRead(ids)
        mutableState.update { it.copy(readIds = it.readIds + ids) }
    }

    /** Library-entry map for the cached feed, loaded without any loading spinner. */
    private fun refreshAnimeMap() {
        screenModelScope.launchIO {
            val animeIdByAnilistId = getTracks.await()
                .filter { it.trackerId == TrackerManager.ANILIST }
                .associate { it.remoteId to it.animeId }
            mutableState.update { it.copy(animeIdByAnilistId = animeIdByAnilistId) }
        }
    }
}
