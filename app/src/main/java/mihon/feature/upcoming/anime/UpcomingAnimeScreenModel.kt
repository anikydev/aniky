@file:OptIn(kotlin.time.ExperimentalTime::class)

package mihon.feature.upcoming.anime

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Clock

enum class UpcomingMode { MINE, ALL }

class UpcomingAnimeScreenModel(
    private val getTracksPerManga: GetTracksPerManga = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val cache: UpcomingCalendarCache = Injekt.get(),
) : StateScreenModel<UpcomingAnimeScreenModel.State>(State()) {

    // "My shows": the user's own AniList list. Seeded from cache for an instant first paint.
    private val airingEntries = MutableStateFlow(cache.getEntries())

    // "All anime": the global airing schedule. Seeded from cache; refreshed lazily on first use.
    private val allAiringEntries = MutableStateFlow(cache.getAllEntries())

    @Volatile
    private var allRefreshStarted = false

    // True only while the Upcoming screen is actually on screen. Gates the ticker so the
    // schedule isn't rebuilt every minute while the tab is backgrounded. Defaults to true so the
    // first combine emission (and thus the first paint) is never blocked waiting on a tick.
    private val screenVisible = MutableStateFlow(true)

    fun setScreenVisible(visible: Boolean) {
        screenVisible.value = visible
    }

    // Emits every minute, but only while the screen is visible, so countdowns and day
    // headers stay fresh (an episode crossing "Airing soon" -> "Aired", midnight, etc.).
    private val ticker: Flow<Unit> = screenVisible.flatMapLatest { visible ->
        if (!visible) {
            emptyFlow()
        } else {
            flow {
                while (true) {
                    emit(Unit)
                    delay(TICK_MS)
                }
            }
        }
    }

    init {
        // Both Upcoming tabs read AniList through the authenticated client - its Cloudflare 403s
        // anonymous API calls - so without a session there is nothing to show. Gate on login and
        // surface the prompt; react to a login while the screen is open the way Statistics does.
        mutableState.update { it.copy(notLoggedIn = !trackerManager.aniList.isLoggedIn) }

        refreshMineInBackground()

        screenModelScope.launchIO {
            val timeZone = ZoneId.systemDefault()

            combine(
                airingEntries,
                allAiringEntries,
                getTracksPerManga.subscribe()
                    .catch {
                        logcat(LogPriority.ERROR, it)
                        // combine() never emits unless every source emits at least once.
                        emit(emptyMap())
                    },
                ticker,
            ) { mine, all, tracks, _ ->
                val animeIdByRemoteId = animeIdByRemoteId(tracks)
                buildWeeklySchedule(mine, animeIdByRemoteId, timeZone) to
                    buildWeeklySchedule(all, animeIdByRemoteId, timeZone)
            }
                .catch { logcat(LogPriority.ERROR, it) { "Upcoming schedule combine failed" } }
                .collectLatest { (mineDays, allDays) ->
                    mutableState.update { state ->
                        state.copy(mineDays = mineDays, allDays = allDays)
                    }
                }
        }

        // If a previous session already cached the global schedule, keep it fresh even before the
        // user switches to the "All" tab, so that tab paints instantly when they do.
        if (allAiringEntries.value.isNotEmpty()) maybeRefreshAll()

        trackerManager.aniList.isLoggedInFlow
            .onEach { loggedIn ->
                val wasLoggedOut = state.value.notLoggedIn
                mutableState.update { it.copy(notLoggedIn = !loggedIn) }
                if (loggedIn && wasLoggedOut) {
                    refreshMineInBackground()
                    if (state.value.mode == UpcomingMode.ALL) maybeRefreshAll()
                }
            }
            .launchIn(screenModelScope)
    }

    fun setMode(mode: UpcomingMode) {
        mutableState.update { it.copy(mode = mode) }
        if (mode == UpcomingMode.ALL) maybeRefreshAll()
    }

    fun setSelectedDate(date: LocalDate) {
        mutableState.update { it.copy(selectedDate = date) }
    }

    /**
     * Re-checks the AniList session and, once logged in, fetches the current tab. Wired to the
     * Retry action on the login-required screen.
     */
    fun refresh() {
        val loggedIn = trackerManager.aniList.isLoggedIn
        mutableState.update { it.copy(notLoggedIn = !loggedIn) }
        if (!loggedIn) return
        refreshMineInBackground()
        if (state.value.mode == UpcomingMode.ALL) maybeRefreshAll()
    }

    // ANK -->
    fun forceRefresh() {
        val loggedIn = trackerManager.aniList.isLoggedIn
        mutableState.update { it.copy(notLoggedIn = !loggedIn) }
        if (!loggedIn) return
        mutableState.update { it.copy(isRefreshing = true) }
        screenModelScope.launchIO {
            try {
                when (state.value.mode) {
                    UpcomingMode.MINE -> {
                        val fresh = trackerManager.aniList.getUserListWithAiring()
                        val mapped = fresh.items.toCachedEntries()
                        airingEntries.value = mapped
                        if (fresh.complete) cache.setEntries(mapped)
                    }
                    UpcomingMode.ALL -> {
                        val result = trackerManager.aniList.getAiringSchedule()
                        val mapped = result.items.toCachedEntries()
                        allAiringEntries.value = mapped
                        if (result.complete) cache.setAllEntries(mapped)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to force-refresh AniList airing schedule" }
            } finally {
                mutableState.update { it.copy(isRefreshing = false) }
            }
        }
    }
    // ANK <--

    private fun refreshMineInBackground() {
        // Nothing to fetch without a session; the cached entries (if any) stay visible.
        if (!trackerManager.aniList.isLoggedIn) return
        if (isFresh(cache.lastFetchedAt())) return

        screenModelScope.launchIO {
            try {
                val fresh = trackerManager.aniList.getUserListWithAiring()
                val mapped = fresh.items.toCachedEntries()
                airingEntries.value = mapped
                // A partial fetch (a page failed mid-way) is fine to show, but must not be cached as
                // fresh: otherwise isFresh() short-circuits the next refresh and the list stays
                // truncated for STALE_MS. Mirrors the ALL path's completeness guard.
                if (fresh.complete) cache.setEntries(mapped)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to refresh AniList airing data" }
            }
        }
    }

    private fun maybeRefreshAll() {
        // The global schedule also goes through the authenticated client now, so there is nothing
        // to fetch without a session.
        if (!trackerManager.aniList.isLoggedIn) return
        // Skip if the cache is fresh and already populated; otherwise (re)fetch.
        if (isFresh(cache.allLastFetchedAt()) && allAiringEntries.value.isNotEmpty()) return
        if (allRefreshStarted) return
        allRefreshStarted = true

        screenModelScope.launchIO {
            try {
                val result = trackerManager.aniList.getAiringSchedule()
                val mapped = result.items.toCachedEntries()
                allAiringEntries.value = mapped
                // A partial fetch (page failed mid-way) is fine to show, but must not be cached
                // as fresh: the next open would re-fetch and fill the gap.
                if (result.complete) cache.setAllEntries(mapped)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to refresh AniList global airing schedule" }
            } finally {
                // Allow a retry on the next switch if this attempt failed to populate the cache.
                allRefreshStarted = false
            }
        }
    }

    private fun isFresh(lastFetchedAt: Long): Boolean {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        return nowMs - lastFetchedAt < STALE_MS
    }

    private fun List<TrackSearch>.toCachedEntries() = map {
        CachedAiringEntry(
            remoteId = it.remote_id,
            title = it.title,
            coverUrl = it.cover_url.ifEmpty { null },
            nextEpisode = it.next_airing_episode.toInt(),
            nextAiringAt = it.next_airing_at,
        )
    }

    private fun animeIdByRemoteId(tracksPerAnime: Map<Long, List<Track>>): Map<Long, Long> {
        return tracksPerAnime
            .values
            .flatten()
            .filter { it.trackerId == TrackerManager.ANILIST }
            .associate { it.remoteId to it.animeId }
    }

    private fun buildWeeklySchedule(
        entries: List<CachedAiringEntry>,
        animeIdByRemoteId: Map<Long, Long>,
        timeZone: ZoneId,
    ): List<UpcomingDay> {
        val now = Clock.System.now()
        val today = now.toLocalDate(timeZone)
        // The query window is [now, startOfDay(today+7)), i.e. strictly before the start of
        // day today+7, so every fetched airing lands on days today..today+6. Display exactly
        // those 7 days: an 8th header would always be empty.
        val days = (0..6).map { today.plusDays(it.toLong()) }

        val itemsByDate = mutableMapOf<LocalDate, MutableList<UpcomingAnimeItem>>()

        entries.forEach { entry ->
            if (entry.nextAiringAt <= 0) return@forEach
            val localDateTime = Instant.ofEpochSecond(entry.nextAiringAt).atZone(timeZone)
            val date = localDateTime.toLocalDate()
            if (date !in days) return@forEach

            // Locale-independent digits: "HH:mm" is used for both display and time-ordered sorting.
            val airingTime = localDateTime.hour.toString().padStart(2, '0') +
                ":" + localDateTime.minute.toString().padStart(2, '0')
            val diffMs = entry.nextAiringAt * 1000L - now.toEpochMilliseconds()
            val diffHours = diffMs / 3_600_000L
            // Clamp negatives: an episode that already aired today must not read "Airing soon".
            val countdown = when {
                diffMs < 0 -> CountdownKind.AIRED to 0L
                diffHours < 1 -> CountdownKind.AIRING_SOON to 0L
                diffHours < 24 -> CountdownKind.HOURS to diffHours
                else -> CountdownKind.DAYS to diffHours / 24
            }

            itemsByDate.getOrPut(date) { mutableListOf() }.add(
                UpcomingAnimeItem(
                    remoteId = entry.remoteId,
                    title = entry.title,
                    coverUrl = entry.coverUrl,
                    animeId = animeIdByRemoteId[entry.remoteId],
                    nextEpisode = entry.nextEpisode,
                    airingTime = airingTime,
                    countdownKind = countdown.first,
                    countdownValue = countdown.second,
                ),
            )
        }

        return days.map { date ->
            // Within a day, order by airing time. Zero-padded "HH:mm" sorts chronologically.
            UpcomingDay(date, itemsByDate[date]?.sortedBy { it.airingTime }.orEmpty())
        }
    }

    data class State(
        val mode: UpcomingMode = UpcomingMode.MINE,
        val notLoggedIn: Boolean = false,
        // ANK -->
        val isRefreshing: Boolean = false,
        // ANK <--
        val selectedDate: LocalDate? = null,
        val mineDays: List<UpcomingDay> = emptyList(),
        val allDays: List<UpcomingDay> = emptyList(),
    ) {
        val days: List<UpcomingDay>
            get() = when (mode) {
                UpcomingMode.ALL -> allDays
                UpcomingMode.MINE -> mineDays
            }

        // Falls back to the first day (today) until the user taps another, and whenever the
        // stored selection is not among the current seven days.
        val selectedDay: UpcomingDay?
            get() = days.firstOrNull { it.date == selectedDate } ?: days.firstOrNull()

        val dayItems: List<UpcomingAnimeItem>
            get() = selectedDay?.items.orEmpty()
    }

    private companion object {
        const val STALE_MS = 6 * 60 * 60 * 1000L
        const val TICK_MS = 60 * 1000L
    }
}

private fun kotlin.time.Instant.toLocalDate(timeZone: ZoneId): LocalDate {
    return Instant.ofEpochMilli(toEpochMilliseconds()).atZone(timeZone).toLocalDate()
}
