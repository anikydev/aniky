package eu.kanade.tachiyomi.data.track.anilist

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeStats
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ALCharacterDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaListOptions
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaTag
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMyListEntry
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNextAiringEpisode
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotification
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStaffDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStudioDetails
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalScreenModel
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import tachiyomi.domain.track.model.Track as DomainAnimeTrack

class Anilist(id: Long) :
    BaseTracker(
        id,
        "AniList",
    ),
    DeletableTracker {

    companion object {
        const val WATCHING = 11L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 15L
        const val REWATCHING = 16L

        const val POINT_100 = "POINT_100"
        const val POINT_10 = "POINT_10"
        const val POINT_10_DECIMAL = "POINT_10_DECIMAL"
        const val POINT_5 = "POINT_5"
        const val POINT_3 = "POINT_3"

        private const val DETAILS_CACHE_SIZE = 60

        // Details carry time-sensitive airing data (status, episodes, nextAiringEpisode), so a hit
        // older than this is treated as stale and refetched instead of served indefinitely.
        private const val DETAILS_CACHE_TTL_MS = 30 * 60 * 1000L
    }

    private data class CachedEntry(
        val details: ALAnimeDetails,
        val fetchedAt: Long,
        val isFull: Boolean = false,
    )

    @Volatile
    private var cachedGenres: List<String>? = null

    @Volatile
    private var cachedTags: List<ALMediaTag>? = null

    /**
     * LRU memoization with a freshness TTL for [getAnimeDetails]: both the anime info screen and
     * the seasonal detail screen fetch on every open, so keeping recent responses around makes
     * revisits instant, while the TTL stops time-sensitive metadata going stale for the whole
     * session. Failures (null results) are deliberately not cached so retries still hit the network.
     */
    private val detailsCache = object : LinkedHashMap<Long, CachedEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CachedEntry>): Boolean =
            size > DETAILS_CACHE_SIZE
    }

    /**
     * Single-flight registry: when two screens request the same media id concurrently (e.g. a
     * relation tap while the previous screen is still fetching that id) they share one GraphQL call
     * instead of both missing a cold cache and issuing a duplicate request.
     */
    private val inFlightDetails = ConcurrentHashMap<Long, CompletableDeferred<ALAnimeDetails?>>()

    private val json: Json by injectLazy()

    private val interceptor by lazy { AnilistInterceptor(this, getPassword()) }

    private val api by lazy { AnilistApi(client, interceptor) }

    override val supportsReadingDates: Boolean = true

    private val scorePreference = trackPreferences.anilistScoreType()

    init {
        // If the preference is an int from APIv1, logout user to force using APIv2
        try {
            scorePreference.get()
        } catch (e: ClassCastException) {
            logout()
            scorePreference.delete()
        }
    }

    override val supportsPrivateTracking: Boolean = true

    override fun getLogo() = R.drawable.brand_anilist

    fun getLogoColor() = Color.rgb(18, 25, 35)

    override fun getStatusList(): List<Long> {
        return listOf(WATCHING, PLAN_TO_WATCH, COMPLETED, REWATCHING, ON_HOLD, DROPPED)
    }

    fun getStatusListAnime(): List<Long> = getStatusList()

    override fun getStatus(status: Long): StringResource? = when (status) {
        WATCHING -> AMR.strings.watching
        PLAN_TO_WATCH -> AMR.strings.plan_to_watch
        COMPLETED -> MR.strings.completed
        REWATCHING -> AMR.strings.repeating_anime
        ON_HOLD -> MR.strings.paused
        DROPPED -> MR.strings.dropped
        else -> null
    }

    fun getStatusForAnime(status: Long): StringResource? = getStatus(status)

    override fun getReadingStatus(): Long = WATCHING
    fun getWatchingStatus(): Long = WATCHING

    override fun getRereadingStatus(): Long = REWATCHING
    fun getRewatchingStatus(): Long = REWATCHING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun hasNotStartedReading(status: Long): Boolean = status == PLAN_TO_WATCH

    override fun getScoreList(): ImmutableList<String> {
        return when (scorePreference.get()) {
            // 10 point
            POINT_10 -> IntRange(0, 10).map(Int::toString).toImmutableList()
            // 100 point
            POINT_100 -> IntRange(0, 100).map(Int::toString).toImmutableList()
            // 5 stars
            POINT_5 -> IntRange(0, 5).map { "$it ★" }.toImmutableList()
            // Smiley
            POINT_3 -> persistentListOf("-", "😦", "😐", "😊")
            // 10 point decimal
            POINT_10_DECIMAL -> IntRange(0, 100).map { (it / 10f).toString() }.toImmutableList()
            else -> throw Exception("Unknown score type")
        }
    }

    override fun get10PointScore(track: DomainAnimeTrack): Double {
        // Score is stored in 100 point format
        return track.score / 10.0
    }

    override fun indexToScore(index: Int): Double {
        return when (scorePreference.get()) {
            // 10 point
            POINT_10 -> index * 10.0
            // 100 point
            POINT_100 -> index.toDouble()
            // 5 stars
            POINT_5 -> when (index) {
                0 -> 0.0
                else -> index * 20.0 - 10.0
            }
            // Smiley
            POINT_3 -> when (index) {
                0 -> 0.0
                else -> index * 25.0 + 10.0
            }
            // 10 point decimal
            POINT_10_DECIMAL -> index.toDouble()
            else -> throw Exception("Unknown score type")
        }
    }

    override fun displayScore(track: DomainAnimeTrack): String {
        val score = track.score

        return when (scorePreference.get()) {
            POINT_5 -> when (score) {
                0.0 -> "0 ★"
                else -> "${((score + 10) / 20).toInt()} ★"
            }
            POINT_3 -> when {
                score == 0.0 -> "0"
                score <= 35 -> "😦"
                score <= 60 -> "😐"
                else -> "😊"
            }
            else -> track.toApiScore()
        }
    }

    private suspend fun add(track: Track): Track {
        return api.addLibAnime(track).also {
            if (track.remote_id > 0) {
                invalidateDetailsCache(track.remote_id)
            }
        }
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        // If user was using API v1 fetch library_id
        if (track.library_id == null || track.library_id!! == 0L) {
            val libManga = api.findLibAnime(track, resolveUserId())
                ?: throw Exception("$track not found on user library")
            track.library_id = libManga.library_id
        }

        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                    track.status = COMPLETED
                    track.finished_watching_date = System.currentTimeMillis()
                } else if (track.status != REWATCHING) {
                    track.status = WATCHING
                    if (track.last_episode_seen == 1.0) {
                        track.started_watching_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateLibAnime(track).also {
            if (track.remote_id > 0) {
                invalidateDetailsCache(track.remote_id)
            }
        }
    }

    override suspend fun delete(track: DomainAnimeTrack) {
        if (track.remoteId > 0) {
            invalidateDetailsCache(track.remoteId)
        }
        if (track.libraryId == null || track.libraryId!! == 0L) {
            val libAnime = api.findLibAnime(track.toDbTrack(), resolveUserId()) ?: return
            return api.deleteLibAnime(track.copy(libraryId = libAnime.library_id!!))
        }

        api.deleteLibAnime(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val remoteTrack = api.findLibAnime(track, resolveUserId())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REWATCHING
                track.status = if (!isRereading && hasReadChapters) WATCHING else track.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            add(track)
        }.also {
            if (track.remote_id > 0) {
                invalidateDetailsCache(track.remote_id)
            }
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.searchAnime(query)
    }

    suspend fun searchAnime(query: String): List<TrackSearch> {
        return search(query)
    }

    suspend fun getSeasonalAnime(season: String?, year: Int, format: String? = null): List<TrackSearch> {
        return api.getSeasonalAnime(season, year, format)
    }

    /**
     * The user's active AniList watchlists (CURRENT/REPEATING entries), with next airing info
     * (epoch seconds) per anime.
     */
    suspend fun getUserListWithAiring(): AnilistApi.UserListResult {
        return api.getUserListWithAiring(resolveUserId())
    }

    /**
     * All the user's AniList anime entries with the given list status (e.g. COMPLETED, PLANNING).
     */
    suspend fun getUserListWithStatus(status: String): AnilistApi.UserListResult {
        return api.getUserListWithStatus(resolveUserId(), status)
    }

    /**
     * The global airing schedule for the next 7 local days, with next airing info per anime.
     * Public data, so it works without being logged in.
     */
    suspend fun getAiringSchedule(): AnilistApi.AiringScheduleResult {
        return api.getAiringSchedule(ZoneId.systemDefault())
    }

    suspend fun getAnimeDetails(mediaId: Long, forceRefresh: Boolean = false): ALAnimeDetails? {
        if (!forceRefresh) {
            synchronized(detailsCache) {
                detailsCache[mediaId]?.let { entry ->
                    val age = System.currentTimeMillis() - entry.fetchedAt
                    if (age < DETAILS_CACHE_TTL_MS) return entry.details
                }
            }
        }

        // Coalesce concurrent fetches for the same id into a single GraphQL request. A null from the
        // shared request means the leader failed or was cancelled without caching anything, so fall
        // through and fetch rather than reporting "no details" on its behalf.
        inFlightDetails[mediaId]?.await()?.let { return it }
        val deferred = CompletableDeferred<ALAnimeDetails?>()
        val current = inFlightDetails.putIfAbsent(mediaId, deferred)
        if (current != null) current.await()?.let { return it }

        try {
            val details = api.getAnimeDetails(mediaId)
            if (details != null) {
                synchronized(detailsCache) {
                    val existing = detailsCache[mediaId]
                    if (existing != null && existing.isFull) {
                        detailsCache[mediaId] = CachedEntry(
                            details = details.copy(
                                staff = existing.details.staff,
                                recommendations = existing.details.recommendations,
                                tags = existing.details.tags,
                            ),
                            fetchedAt = System.currentTimeMillis(),
                            isFull = true,
                        )
                    } else {
                        detailsCache[mediaId] = CachedEntry(details, System.currentTimeMillis(), isFull = false)
                    }
                }
            }
            deferred.complete(details)
            return details
        } catch (e: CancellationException) {
            // Release any waiter; the caller was cancelled, so nothing useful to cache.
            deferred.complete(null)
            throw e
        } catch (e: AnilistRateLimitedException) {
            // The server said slow down: any cached copy — even past its TTL — beats leaving the
            // screen without a relations row, and nothing is written back, so the TTL still forces
            // a fresh fetch once the cooldown (and the cooldown itself) has passed.
            val stale = synchronized(detailsCache) { detailsCache[mediaId]?.details }
            deferred.complete(stale)
            logcat(LogPriority.WARN, e) { "AniList rate limited; serving cached details for $mediaId" }
            return stale
        } finally {
            inFlightDetails.remove(mediaId, deferred)
            // A no-op once completed. Without it, a failure neither catch above handles would leave
            // the deferred forever incomplete after it has been unregistered, hanging every waiter.
            deferred.complete(null)
        }
    }

    suspend fun getCharacterDetails(characterId: Long): ALCharacterDetails? {
        return api.getCharacterDetails(characterId)
    }

    suspend fun getMediaIdByMalId(idMal: Long): Long? {
        return api.getMediaIdByMalId(idMal)
    }

    suspend fun getNextAiringEpisode(mediaId: Long): ALNextAiringEpisode? {
        return api.getNextAiringEpisode(mediaId)
    }

    suspend fun getUserStats(): ALAnimeStats {
        return api.getUserStats()
    }

    suspend fun getMyAnimeLists(status: String? = null): List<ALMyListEntry> {
        return api.getMyAnimeLists(resolveUserId(), status)
    }

    /** Stored username is the numeric viewer id, but older installs may hold a name — resolve via API then. */
    private suspend fun resolveUserId(): Int {
        getUsername().toIntOrNull()?.let { return it }
        return api.getCurrentUser().first
    }

    suspend fun getMediaListOptions(): ALMediaListOptions? {
        return api.getMediaListOptions()
    }

    fun invalidateDetailsCache(mediaId: Long? = null) {
        synchronized(detailsCache) {
            if (mediaId != null) {
                detailsCache.remove(mediaId)
            } else {
                detailsCache.clear()
            }
        }
    }

    suspend fun saveEntryFull(
        mediaId: Long? = null,
        listId: Long? = null,
        status: String? = null,
        progress: Int? = null,
        scoreRaw100: Int? = null,
        repeat: Int? = null,
        notes: String? = null,
        startedAtMillis: Long? = null,
        completedAtMillis: Long? = null,
    ): Long? {
        val result = api.saveMediaListEntryFull(
            mediaId, listId, status, progress, scoreRaw100, repeat, notes,
            startedAtMillis, completedAtMillis,
        )
        if (mediaId != null) {
            invalidateDetailsCache(mediaId)
        }
        return result
    }

    suspend fun deleteEntry(listId: Long) {
        api.deleteEntry(listId)
        invalidateDetailsCache()
    }

    suspend fun toggleFavouriteAnime(animeId: Long): Boolean {
        return api.toggleFavouriteAnime(animeId)
    }

    suspend fun browseAnime(
        page: Int,
        perPage: Int = 24,
        search: String? = null,
        genres: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        formats: List<String> = emptyList(),
        statuses: List<String> = emptyList(),
        season: String? = null,
        seasonYear: Int? = null,
        sort: String = "TRENDING_DESC",
    ): AnilistApi.BrowsePage {
        return api.browseAnime(page, perPage, search, genres, tags, formats, statuses, season, seasonYear, sort)
    }

    suspend fun getGenreCollection(): List<String> {
        cachedGenres?.let { return it }
        return api.getGenreCollection().also { if (it.isNotEmpty()) cachedGenres = it }
    }

    suspend fun getTagCollection(): List<ALMediaTag> {
        cachedTags?.let { return it }
        return api.getTagCollection().also { if (it.isNotEmpty()) cachedTags = it }
    }

    suspend fun getAnimeDetailsFull(mediaId: Long, forceRefresh: Boolean = false): ALAnimeDetails? {
        // Reuse the base LRU+TTL so revisits are instant and rate-limit fallbacks still work.
        if (!forceRefresh) {
            synchronized(detailsCache) {
                detailsCache[mediaId]?.let { entry ->
                    val age = System.currentTimeMillis() - entry.fetchedAt
                    // Full payload goes stale at the same rate as base detail (airing data).
                    if (age < DETAILS_CACHE_TTL_MS && entry.isFull) return entry.details
                }
            }
        }
        return try {
            val full = api.getAnimeDetailsFull(mediaId)
            if (full != null) {
                synchronized(detailsCache) {
                    detailsCache[mediaId] = CachedEntry(full, System.currentTimeMillis(), isFull = true)
                }
            }
            full
        } catch (e: AnilistRateLimitedException) {
            val stale = synchronized(detailsCache) { detailsCache[mediaId]?.takeIf { it.isFull }?.details }
            logcat(LogPriority.WARN, e) { "AniList rate limited; serving cached full details for $mediaId" }
            stale
        }
    }

    suspend fun getStaffDetails(staffId: Long): ALStaffDetails? {
        return api.getStaffDetails(staffId)
    }

    // ANK -->
    suspend fun getStudioDetails(
        studioId: Long? = null,
        studioName: String? = null,
        sort: String = "POPULARITY_DESC",
        page: Int = 1,
        perPage: Int = 36,
    ): ALStudioDetails? {
        return api.getStudioDetails(studioId, studioName, sort, page, perPage)
    }
    // ANK <--

    suspend fun getFavouriteAnime(): List<ALBrowseItem> {
        return api.getFavouriteAnime()
    }

    suspend fun isAnimeFavourite(mediaId: Long): Boolean? {
        return api.isAnimeFavourite(mediaId)
    }

    suspend fun getNotifications(): List<ALNotification> {
        return api.getNotifications()
    }

    suspend fun getUnreadNotificationCount(): Int {
        return api.getUnreadNotificationCount()
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibAnime(track, resolveUserId())
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    override suspend fun getMangaMetadata(track: DomainAnimeTrack): TrackMangaMetadata {
        val details = getAnimeDetails(track.remoteId)
            ?: throw Exception("Failed to fetch AniList metadata")
        val mainStudios = details.studios?.edges
            ?.filter { it.isMain }
            ?.mapNotNull { it.node.name.takeIf(String::isNotBlank) }
            ?.takeIf { it.isNotEmpty() }
            ?: details.studios?.edges
                ?.mapNotNull { it.node.name.takeIf(String::isNotBlank) }
        return TrackMangaMetadata(
            remoteId = details.id,
            title = details.title.userPreferred ?: details.title.romaji ?: details.title.english,
            thumbnailUrl = details.coverImage.extraLarge ?: details.coverImage.large,
            description = details.description?.htmlDecode(),
            authors = mainStudios?.joinToString(),
        )
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(token: String) {
        // Cache/read markers belong to the previous account, if any. Capture the
        // old username so a switch to a different account can drop them below.
        val previousUsername = runCatching { getUsername() }.getOrDefault("")
        try {
            val oauth = api.createOAuth(token)
            interceptor.setAuth(oauth)
            val (username, scoreType) = api.getCurrentUser()
            scorePreference.set(scoreType)
            saveCredentials(username.toString(), oauth.accessToken)
            if (previousUsername.isNotEmpty() && previousUsername != username.toString()) {
                clearAccountCaches()
            }
        } catch (e: Throwable) {
            logout()
            // Rethrown so the login screen can tell failure apart from success instead of
            // landing back in settings either way.
            throw e
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
        clearAccountCaches()
    }

    private fun clearAccountCaches() {
        // Best-effort: Injekt may be unavailable in unit tests / early init.
        // Never let cache cleanup break login/logout itself.
        runCatching { Injekt.get<AniListNotificationStore>().clear() }
        // Per-account data that lives on the singleton tracker/api must also be dropped on logout and
        // account switch, or the previous account's favourite membership (isAnimeFavourite / the heart
        // toggle) and personal list entries/scores (detailsCache) would be served to - or mutated for -
        // the next account until the 30-min TTL expires. Genres/tags in detailsCache are global, but
        // evicting the whole entry is simpler and only costs a re-fetch.
        runCatching { api.invalidateFavouritesCache() }
        synchronized(detailsCache) { detailsCache.clear() }
        inFlightDetails.clear()
        runCatching { SeasonalScreenModel.clearCache() }
        runCatching {
            val prefStore = Injekt.get<PreferenceStore>()
            prefStore.getString(Preference.appStateKey("anime_upcoming_anilist_cache"), "").delete()
            prefStore.getLong(Preference.appStateKey("anime_upcoming_anilist_cache_fetched_at"), 0L).delete()
        }
    }

    fun saveOAuth(alOAuth: ALOAuth?) {
        if (alOAuth == null) {
            // Don't serialize "null" back into the preference logout() just deleted.
            trackPreferences.trackToken(this).delete()
            return
        }
        trackPreferences.trackToken(this).set(json.encodeToString(alOAuth))
    }

    fun loadOAuth(): ALOAuth? {
        return try {
            json.decodeFromString<ALOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
    }
}
