package eu.kanade.tachiyomi.ui.browse.seasonal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaListEntryLite
import eu.kanade.tachiyomi.ui.browse.seasonal.model.AnimeThemeSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

class SeasonalAnimeDetailScreenModel(
    private val mediaId: Long,
    private val trackerManager: TrackerManager = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val animeThemesRepository: AnimeThemesRepository = AnimeThemesRepository(),
) : StateScreenModel<SeasonalAnimeDetailScreenModel.State>(State()) {

    val titleLanguage get() = uiPreferences.anilistTitleLanguage().get()
    val isLoggedIn get() = trackerManager.aniList.isLoggedIn

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val details: ALAnimeDetails? = null,
        val error: Boolean = false,
        val inLibraryAnimeId: Long? = null,
        val isBumping: Boolean = false,
        val themeSongs: List<AnimeThemeSong> = emptyList(),
        val isLoadingThemes: Boolean = false,
    )

    private var fetchJob: Job? = null
    private var themesJob: Job? = null

    init {
        fetchDetails()
    }

    fun refresh() {
        trackerManager.aniList.invalidateDetailsCache(mediaId)
        fetchDetails(isPullRefresh = true, forceRefresh = true)
    }

    fun retry(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            trackerManager.aniList.invalidateDetailsCache(mediaId)
        }
        fetchDetails(forceRefresh = forceRefresh)
    }

    fun bumpProgress(delta: Int) {
        val details = state.value.details ?: return
        val entry = details.mediaListEntry ?: return
        val current = entry.progress ?: 0
        val total = details.episodes?.toInt() ?: 0
        val next = (current + delta).coerceAtLeast(0)
        if (total > 0 && next > total) return
        if (state.value.isBumping) return

        val updatedEntry = entry.copy(progress = next)
        mutableState.update { it.copy(details = details.copy(mediaListEntry = updatedEntry), isBumping = true) }

        screenModelScope.launch(Dispatchers.IO) {
            try {
                withContext(NonCancellable) {
                    trackerManager.aniList.saveEntryFull(
                        mediaId = mediaId,
                        listId = entry.id.takeIf { it != 0L },
                        status = entry.status,
                        progress = next,
                        scoreRaw100 = entry.score?.roundToInt(),
                        repeat = entry.repeat,
                        notes = entry.notes,
                    )
                    trackerManager.aniList.invalidateDetailsCache(mediaId)
                    runCatching {
                        val localTracks = getTracks.await().filter { it.trackerId == TrackerManager.ANILIST && it.remoteId == mediaId }
                        localTracks.forEach { lt ->
                            insertTrack.await(lt.copy(lastChapterRead = next.toDouble()))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "AniList detail: failed to bump progress" }
                mutableState.update { it.copy(details = details.copy(mediaListEntry = entry)) }
            } finally {
                mutableState.update { it.copy(isBumping = false) }
            }
        }
    }

    fun setEntryStatus(newStatus: String) {
        val details = state.value.details ?: return
        val entry = details.mediaListEntry
        // Optimistic UI update so the user sees the new status immediately without lag
        val optimisticEntry = (entry ?: ALMediaListEntryLite(id = 0L, status = newStatus, score = 0.0, progress = 0, repeat = 0, notes = null))
            .copy(status = newStatus)
        mutableState.update { it.copy(details = details.copy(mediaListEntry = optimisticEntry), isBumping = true) }

        screenModelScope.launch(Dispatchers.IO) {
            try {
                var createdId: Long? = null
                withContext(NonCancellable) {
                    val newId = trackerManager.aniList.saveEntryFull(
                        mediaId = mediaId,
                        listId = entry?.id?.takeIf { it != 0L },
                        status = newStatus,
                        progress = entry?.progress ?: 0,
                        scoreRaw100 = entry?.score?.roundToInt(),
                        repeat = entry?.repeat ?: 0,
                        notes = entry?.notes,
                    )
                    createdId = newId
                    trackerManager.aniList.invalidateDetailsCache(mediaId)
                    if (newId != null && entry == null) {
                        mutableState.update { current ->
                            val curDetails = current.details ?: return@update current
                            curDetails.mediaListEntry?.let { currentEntry ->
                                current.copy(details = curDetails.copy(mediaListEntry = currentEntry.copy(id = newId)))
                            } ?: current
                        }
                    }
                    runCatching {
                        val localTracks = getTracks.await().filter { it.trackerId == TrackerManager.ANILIST && it.remoteId == mediaId }
                        localTracks.forEach { lt ->
                            val trackStatus = when (newStatus) {
                                "CURRENT" -> Anilist.WATCHING
                                "COMPLETED" -> Anilist.COMPLETED
                                "PAUSED" -> Anilist.ON_HOLD
                                "DROPPED" -> Anilist.DROPPED
                                "PLANNING" -> Anilist.PLAN_TO_WATCH
                                "REPEATING" -> Anilist.REWATCHING
                                else -> Anilist.PLAN_TO_WATCH
                            }
                            insertTrack.await(lt.copy(status = trackStatus, libraryId = newId ?: lt.libraryId))
                        }
                    }
                }
                // Confirm server state with fresh fetch
                val fresh = trackerManager.aniList.getAnimeDetailsFull(mediaId, forceRefresh = true)
                if (fresh != null) {
                    val resolvedEntry = fresh.mediaListEntry?.let {
                        if (it.status != newStatus) it.copy(status = newStatus) else it
                    } ?: optimisticEntry.copy(id = createdId ?: optimisticEntry.id)
                    mutableState.update { it.copy(details = fresh.copy(mediaListEntry = resolvedEntry)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "AniList detail: failed to set status" }
                // Revert optimistic update on failure
                mutableState.update { it.copy(details = details.copy(mediaListEntry = entry)) }
            } finally {
                mutableState.update { it.copy(isBumping = false) }
            }
        }
    }

    private fun fetchDetails(isPullRefresh: Boolean = false, forceRefresh: Boolean = false) {
        // Cancel any in-flight fetch so a stale response can never overwrite a fresh one.
        fetchJob?.cancel()
        if (isPullRefresh) {
            mutableState.update { it.copy(isRefreshing = true, error = false) }
        } else {
            mutableState.update { it.copy(isLoading = it.details == null, error = false) }
        }
        fetchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                logcat(LogPriority.DEBUG) { "AniList detail: fetching media $mediaId..." }
                // Full detail (staff, recommendations, tags, favourite + own entry) for the
                // AniList client; falls back to cached base detail when rate-limited inside.
                val details = try {
                    trackerManager.aniList.getAnimeDetailsFull(mediaId, forceRefresh = forceRefresh)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "AniList detail: full fetch failed, trying base" }
                    null
                } ?: trackerManager.aniList.getAnimeDetails(mediaId, forceRefresh = forceRefresh)
                logcat(LogPriority.DEBUG) { "AniList detail: fetched ${details?.title?.userPreferred}" }

                val inLibraryAnimeId = try {
                    val tracks = getTracks.await().filter { it.trackerId == TrackerManager.ANILIST && it.remoteId == mediaId }
                    tracks.firstNotNullOfOrNull { track ->
                        getAnime.await(track.animeId)?.takeIf { it.favorite }?.id
                    }
                } catch (e: Exception) {
                    null
                }

                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        details = details,
                        error = details == null,
                        inLibraryAnimeId = inLibraryAnimeId,
                    )
                }

                if (details != null && (state.value.themeSongs.isEmpty() || isPullRefresh)) {
                    fetchThemes(details, forceRefresh = isPullRefresh)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "AniList detail: failed to fetch details for $mediaId" }
                mutableState.update { it.copy(isLoading = false, isRefreshing = false, error = true) }
            }
        }
    }

    private fun fetchThemes(details: ALAnimeDetails, forceRefresh: Boolean = false) {
        themesJob?.cancel()
        themesJob = screenModelScope.launch(Dispatchers.IO) {
            if (!forceRefresh) {
                val cached = animeThemesRepository.getCached(mediaId)
                if (cached != null) {
                    mutableState.update { it.copy(themeSongs = cached, isLoadingThemes = false) }
                    return@launch
                }
            }
            mutableState.update { it.copy(isLoadingThemes = true) }
            try {
                val title = details.title.userPreferred
                    ?: details.title.romaji
                    ?: details.title.english
                val songs = animeThemesRepository.getThemeSongs(
                    mediaId = mediaId,
                    idMal = details.idMal,
                    title = title,
                    forceRefresh = forceRefresh,
                )
                mutableState.update { it.copy(themeSongs = songs, isLoadingThemes = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to fetch theme songs for $mediaId" }
                mutableState.update { it.copy(isLoadingThemes = false) }
            }
        }
    }

    fun deleteEntry(onSuccess: (() -> Unit)? = null) {
        val details = state.value.details ?: return
        val entry = details.mediaListEntry ?: return
        screenModelScope.launch(Dispatchers.IO) {
            try {
                withContext(NonCancellable) {
                    trackerManager.aniList.delete(
                        tachiyomi.domain.track.model.Track(
                            id = 0,
                            mangaId = 0,
                            trackerId = TrackerManager.ANILIST,
                            remoteId = mediaId,
                            libraryId = entry.id.takeIf { it != 0L },
                            title = details.title.userPreferred ?: "",
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
                    trackerManager.aniList.invalidateDetailsCache(mediaId)
                    val idMal = details.idMal
                    if (idMal != null && idMal > 0L && trackerManager.myAnimeList.isLoggedIn) {
                        runCatching {
                            trackerManager.myAnimeList.delete(
                                tachiyomi.domain.track.model.Track(
                                    id = 0,
                                    mangaId = 0,
                                    trackerId = trackerManager.myAnimeList.id,
                                    remoteId = idMal,
                                    libraryId = null,
                                    title = details.title.userPreferred ?: "",
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
                    }
                }
                mutableState.update { it.copy(details = details.copy(mediaListEntry = null)) }
                if (onSuccess != null) {
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "SeasonalAnimeDetailScreen: failed to delete entry" }
            }
        }
    }
}
