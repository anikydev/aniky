package eu.kanade.tachiyomi.data.track.anilist

import android.net.Uri
import android.os.SystemClock
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAddAnimeResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAiringScheduleResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeDetailsResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALAnimeStats
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALCharacterDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALCharacterDetailsResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALCurrentUserResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALFavouritesResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALGenreCollectionResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaIdResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaListOptions
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaListOptionsResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMyListEntry
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMyListResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNextAiringEpisode
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNextAiringResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotification
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotificationsResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStaffDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStaffDetailsResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStatsResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStudioDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStudioDetailsResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALTagCollectionResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUnreadCountResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserListAiringResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserListEntryQueryResult
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.minutes
import tachiyomi.domain.track.model.Track as DomainAnimeTrack

class AnilistApi(val client: OkHttpClient, interceptor: AnilistInterceptor) {

    private val json: Json by injectLazy()

    // Every AniList read goes through the authenticated client: AniList's Cloudflare 403s the app's
    // anonymous API requests, so the OAuth token has to ride along. The features that reach these
    // methods (Seasonal, Upcoming, the detail screen, tracked-anime enrichment) all require login.
    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .rateLimit(permits = 85, period = 1.minutes)
        // No disk cache: authenticated responses must never persist to network_cache.
        .cache(null)
        .build()

    /**
     * Session snapshot of the viewer's favourite anime ids. AniList's `Media.isFavourite` is
     * unreliable - it comes back false for media that ARE favourited - so favourite state is
     * derived from the favourites list (the same source of truth the Favourites tab shows).
     * Populated lazily by [getFavouriteAnime]/[isAnimeFavourite] and kept in sync by
     * [toggleFavouriteAnime]; null means "not loaded yet".
     */
    // In-place element mutations (add/remove/wholesale replace) go through [favouritesMutex]:
    // @Volatile alone would publish the reference but not those mutations. @Volatile is added on top
    // so the session-boundary reset in [invalidateFavouritesCache] can publish a null reference
    // without the (suspend) mutex - safe because that reset only swaps the reference, never mutates
    // the set in place.
    @Volatile
    private var favouriteAnimeIds: MutableSet<Long>? = null
    private val favouritesMutex = Mutex()

    // Bumped by every toggle and by [invalidateFavouritesCache]. getFavouriteAnime reads it before
    // its (unlocked) network fetch and only commits the snapshot if it hasn't changed, so a toggle -
    // or an account switch - that lands mid-fetch isn't clobbered by a stale pre-toggle snapshot.
    // Only the difference from a snapshot matters, so a rare lost increment from the non-mutex reset
    // is benign.
    @Volatile
    private var favouritesEpoch = 0L

    /**
     * The earliest [SystemClock.elapsedRealtime] after which AniList accepts traffic again,
     * updated whenever an HTTP 429 is observed. Both clients share it so a 429 from either path
     * (e.g. a details fetch) pauses reads from the other (track search, MAL-id resolution) too.
     */
    private val rateLimitResetAt = AtomicLong(0L)

    /** Takes a 429's [Retry-After] delay (in seconds), defaulting to a sensible back-off when the
     *  header is absent, and postpones further AniList traffic by at least that much. */
    private fun recordRateLimit(retryAfterSeconds: Long?) {
        val cooldownMs = (retryAfterSeconds?.takeIf { it > 0 }?.times(1000L) ?: DEFAULT_COOLDOWN_MS)
            .coerceAtLeast(MIN_COOLDOWN_MS)
        rateLimitResetAt.updateAndGet { current ->
            (SystemClock.elapsedRealtime() + cooldownMs).coerceAtLeast(current)
        }
        logcat(LogPriority.WARN) { "AniList rate limited; deferring requests for ${cooldownMs}ms" }
    }

    /**
     * Runs a GraphQL mutation/query via [client], translating HTTP 429 into
     * [AnilistRateLimitedException] and (crucially) failing fast while the server's rate limit is
     * still cooling down, so a refresh storm can't keep hammering a server that already said "no".
     */
    private suspend fun execute(client: OkHttpClient, payload: String): Response {
        val remainingCooldown = rateLimitResetAt.get() - SystemClock.elapsedRealtime()
        if (remainingCooldown > 0) {
            throw AnilistRateLimitedException(remainingCooldown)
        }
        val response = client.newCall(
            POST(API_URL, body = payload.toRequestBody(jsonMime)),
        ).await()
        if (response.code == HTTP_TOO_MANY_REQUESTS) {
            val retryAfterSeconds = response.header(RETRY_AFTER_HEADER)?.toLongOrNull()
            recordRateLimit(retryAfterSeconds)
            response.close()
            // The exception field is in milliseconds (like the cooldown path above); Retry-After is
            // in seconds, so convert. null stays null (callers fall back to a default cooldown).
            throw AnilistRateLimitedException(retryAfterSeconds?.let { it * 1000 })
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpException(code)
        }
        // AniList reports a rejected query/mutation as HTTP 200 + a top-level `errors` array (with
        // `data: null`), which the status check above lets through. Left unchecked it fails to
        // deserialize into the non-null `data` DTO and gets swallowed as an empty/null result, so
        // check it once here - covering every read and mutation - and hand callers a fresh,
        // re-readable body to parse.
        val contentType = response.body.contentType()
        val bodyBytes = response.body.bytes()
        val errors = runCatching {
            json.parseToJsonElement(bodyBytes.decodeToString()).jsonObject["errors"]?.jsonArray
        }.getOrNull()
        if (!errors.isNullOrEmpty()) {
            val message = errors.firstOrNull()
                ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            response.close()
            throw AnilistGraphqlException(message ?: "AniList rejected the request")
        }
        return response.newBuilder()
            .body(bodyBytes.toResponseBody(contentType))
            .build()
    }

    suspend fun addLibAnime(track: Track): Track {
        return withIOContext {
            val query = """
            |mutation AddAnime(${'$'}animeId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}private: Boolean) {
                |SaveMediaListEntry (mediaId: ${'$'}animeId, progress: ${'$'}progress, status: ${'$'}status, private: ${'$'}private) {
                |   id
                |   status
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("animeId", track.remote_id)
                    put("progress", track.last_episode_seen.toInt())
                    put("status", track.toApiStatus())
                    put("private", track.private)
                }
            }
            with(json) {
                // Via execute() so 429s feed the shared cooldown instead of hammering through it.
                execute(authClient, payload.toString())
                    .parseAs<ALAddAnimeResult>()
                    .let {
                        track.library_id = it.data.entry.id
                        track
                    }
            }
        }
    }

    suspend fun updateLibAnime(track: Track): Track {
        return withIOContext {
            val query = """
            |mutation UpdateAnime(
                |${'$'}listId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}private: Boolean,
                |${'$'}score: Int, ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput
            |) {
                |SaveMediaListEntry(
                    |id: ${'$'}listId, progress: ${'$'}progress, status: ${'$'}status, private: ${'$'}private,
                    |scoreRaw: ${'$'}score, startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt
                |) {
                    |id
                    |status
                    |progress
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("listId", track.library_id)
                    put("progress", track.last_episode_seen.toInt())
                    put("status", track.toApiStatus())
                    put("private", track.private)
                    put("score", track.score.toInt())
                    // Only send a date when we actually have one. createDate(0L) emits an all-null
                    // FuzzyDateInput, which AniList reads as "clear this date" — that would wipe a
                    // start/finish date set elsewhere (e.g. a year-only date that toEpochMilli()
                    // reports as 0L). JsonNull leaves it untouched, matching saveMediaListEntryFull.
                    put(
                        "startedAt",
                        if (track.started_watching_date != 0L) createDate(track.started_watching_date) else JsonNull,
                    )
                    put(
                        "completedAt",
                        if (track.finished_watching_date != 0L) createDate(track.finished_watching_date) else JsonNull,
                    )
                }
            }
            execute(authClient, payload.toString()).close()
            track
        }
    }

    suspend fun deleteEntry(listId: Long) {
        withIOContext {
            val query = """
            |mutation DeleteAnime(${'$'}listId: Int) {
                |DeleteMediaListEntry(id: ${'$'}listId) {
                    |deleted
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("listId", listId)
                }
            }
            execute(authClient, payload.toString()).close()
        }
    }

    suspend fun deleteLibAnime(track: DomainAnimeTrack) {
        val listId = track.libraryId
        if (listId != null && listId > 0) {
            deleteEntry(listId)
        }
    }

    suspend fun searchAnime(search: String): List<TrackSearch> {
        return withIOContext {
            val query = """
            |query Search(${'$'}query: String) {
                |Page (perPage: 50) {
                    |media(search: ${'$'}query, type: ANIME) {
                        |id
                        |title {
                            |userPreferred
                        |}
                        |coverImage {
                            |large
                        |}
                        |format
                        |status
                        |episodes
                        |description
                        |startDate {
                            |year
                            |month
                            |day
                        |}
                        |averageScore
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", search)
                }
            }
            try {
                with(json) {
                    execute(authClient, payload.toString())
                        .parseAs<ALSearchResult>()
                        .data.page.media
                        .map { it.toALAnime().toTrack() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnilistRateLimitedException) {
                logcat(LogPriority.WARN, throwable = e) { "AniList rate limited; search for \"$search\" deferred" }
                throw e
            }
        }
    }

    suspend fun getSeasonalAnime(season: String?, year: Int, format: String? = null): List<TrackSearch> {
        return withIOContext {
            // Bind format as a $format GraphQL variable (declared only when used, like $season)
            // rather than interpolating the value into the query text - an injection/breakage vector
            // if a free-text format ever reached here.
            val formatFilter = format?.takeIf { it.isNotBlank() }?.let { ", format: ${'$'}format" } ?: ""
            val seasonFilter = season?.takeIf { it.isNotBlank() }
                ?.let { ", season: ${'$'}season" }
                ?: ""
            // Only declare $season/$format when the filter actually references it: GraphQL rejects an
            // operation that declares a variable it never uses.
            val seasonVariable = if (seasonFilter.isNotEmpty()) "${'$'}season: MediaSeason, " else ""
            val formatVariable = if (formatFilter.isNotEmpty()) "${'$'}format: MediaFormat, " else ""
            val allItems = mutableListOf<TrackSearch>()
            var page = 1
            while (true) {
                val query = """
                query SeasonalAnime($seasonVariable$formatVariable${'$'}year: Int, ${'$'}page: Int) {
                    Page (perPage: 50, page: ${'$'}page) {
                        pageInfo {
                            hasNextPage
                        }
                        media(seasonYear: ${'$'}year, sort: POPULARITY_DESC, type: ANIME, isAdult: false$seasonFilter$formatFilter) {
                            id
                            title {
                                english
                                romaji
                                native
                                userPreferred
                            }
                            coverImage {
                                large
                            }
                            format
                            status
                            episodes
                            description
                            averageScore
                            popularity
                            favourites
                            studios {
                                edges {
                                    isMain
                                    node {
                                        name
                                    }
                                }
                            }
                            mediaListEntry {
                                id
                                status
                                progress
                            }
                            nextAiringEpisode {
                                episode
                                airingAt
                            }
                            startDate {
                                year
                                month
                                day
                            }
                            genres
                            season
                            seasonYear
                            trailer {
                                id
                                site
                            }
                        }
                    }
                }
                """.trimIndent()
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        season?.takeIf { it.isNotBlank() }?.let { put("season", it) }
                        format?.takeIf { it.isNotBlank() }?.let { put("format", it) }
                        put("year", year)
                        put("page", page)
                    }
                }
                val result = try {
                    with(json) {
                        // authClient, so the request carries the AniList OAuth token. AniList's
                        // Cloudflare answers an anonymous API request from the app with a 403 - its
                        // bot rules flag the browser-spoofed User-Agent the shared client sends -
                        // while an authenticated request is let through; the Seasonal screen only
                        // calls this while logged in. authClient still carries the rateLimit
                        // interceptor and the shared 429 cooldown, which keep this up-to-50-page
                        // loop ("All seasons" is one query per page) from hammering a server that
                        // already said no.
                        execute(authClient, payload.toString())
                            .parseAs<ALSearchResult>()
                            .data.page
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) {
                        "getSeasonalAnime: page $page fetch failed, keeping the ${allItems.size} items already collected"
                    }
                    // A failure before any page was fetched must surface to the caller so the
                    // seasonal tab can show an error (with retry) instead of a misleading "empty".
                    if (allItems.isEmpty()) throw e
                    break
                }

                allItems += result.media.map { item ->
                    TrackSearch.create(TrackerManager.ANILIST).apply {
                        remote_id = item.id
                        title = item.title.userPreferred.orEmpty()
                        englishTitle = item.title.english
                        romajiTitle = item.title.romaji
                        nativeTitle = item.title.native
                        total_episodes = item.episodes ?: 0
                        cover_url = item.coverImage.large ?: ""
                        summary = item.description?.htmlDecode() ?: ""
                        score = (item.averageScore ?: 0).toDouble()
                        tracking_url = AnilistApi.animeUrl(remote_id)
                        publishing_status = item.status ?: ""
                        publishing_type = item.format?.replace("_", "-") ?: ""
                        popularity = item.popularity ?: 0
                        favourites = item.favourites ?: 0
                        studio = item.studios?.edges?.firstOrNull { it.isMain }?.node?.name
                            ?: item.studios?.edges?.firstOrNull()?.node?.name
                        trackerStatus = item.mediaListEntry?.status
                        mediaListId = item.mediaListEntry?.id
                        next_airing_episode = item.nextAiringEpisode?.episode ?: 0
                        next_airing_at = item.nextAiringEpisode?.airingAt ?: 0
                        genres = item.genres
                        this.season = item.season ?: ""
                        this.seasonYear = item.seasonYear ?: 0
                        this.hasTrailer = item.trailer?.let { it.id != null && it.site == "youtube" } == true
                    }
                }

                if (!result.pageInfo.hasNextPage) break
                page++
                if (page > 50) break // safety cap
            }
            allItems.distinctBy { it.remote_id }
        }
    }

    suspend fun getAnimeDetails(mediaId: Long): ALAnimeDetails? {
        return withIOContext {
            try {
                val query = """
                query AnimeDetails(${'$'}id: Int) {
                    Media (id: ${'$'}id, type: ANIME) {
                        id
                        idMal
                        title {
                            english
                            romaji
                            native
                            userPreferred
                        }
                        coverImage {
                            extraLarge
                            large
                        }
                        bannerImage
                        description
                        format
                        status
                        episodes
                        duration
                        averageScore
                        popularity
                        favourites
                        genres
                        season
                        seasonYear
                        isFavourite
                        mediaListEntry { id status score(format: POINT_100) progress repeat notes startedAt { year month day } completedAt { year month day } }
                        studios {
                            edges {
                                isMain
                                node {
                                    id
                                    name
                                }
                            }
                        }
                        startDate {
                            year
                            month
                            day
                        }
                        endDate {
                            year
                            month
                            day
                        }
                        nextAiringEpisode {
                            episode
                            airingAt
                        }
                        trailer {
                            id
                            site
                        }
                        relations {
                            edges {
                                relationType
                                node {
                                    id
                                    title {
                                        english
                                        romaji
                                        native
                                        userPreferred
                                    }
                                    coverImage {
                                        large
                                    }
                                    format
                                }
                            }
                        }
                        characters(sort: [ROLE]) {
                            edges {
                                role
                                node {
                                    id
                                    name { userPreferred }
                                    image { large }
                                }
                            }
                        }
                    }
                }
                """.trimIndent()
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("id", mediaId)
                    }
                }
                with(json) {
                    // authClient so the OAuth token is sent - AniList's Cloudflare 403s the app's
                    // anonymous requests. This runs only from screens reachable while logged in:
                    // the Seasonal/Upcoming detail screen and an AniList-tracked anime's info screen.
                    execute(authClient, payload.toString())
                        .parseAs<ALAnimeDetailsResult>()
                        .data.media
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnilistRateLimitedException) {
                logcat(LogPriority.WARN, throwable = e) { "AniList rate limited; deferring details fetch for $mediaId" }
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, throwable = e) { "Failed to fetch AniList anime details" }
                null
            }
        }
    }

    /**
     * Full detail for a single AniList character - name, image, bio, attributes and the media it
     * appears in. Authenticated (`authClient`); reached only from the login-gated detail screen's
     * character list.
     */
    suspend fun getCharacterDetails(characterId: Long): ALCharacterDetails? {
        return withIOContext {
            try {
                val query = """
                |query CharacterDetails(${'$'}id: Int) {
                    |Character(id: ${'$'}id) {
                        |id
                        |name { userPreferred native alternative }
                        |image { large }
                        |description(asHtml: false)
                        |gender
                        |age
                        |dateOfBirth { year month day }
                        |bloodType
                        |favourites
                        |media(sort: [POPULARITY_DESC], type: ANIME, perPage: 25) {
                            |edges {
                                |characterRole
                                |node {
                                    |id
                                    |type
                                    |isAdult
                                    |title { userPreferred english romaji native }
                                    |coverImage { large }
                                    |averageScore
                                    |format
                                    |episodes
                                |}
                                |voiceActors(sort: [RELEVANCE, ID]) {
                                    |id
                                    |name { userPreferred full }
                                    |languageV2
                                    |image { large medium }
                                |}
                            |}
                        |}
                    |}
                |}
                |
                """.trimMargin()
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("id", characterId)
                    }
                }
                with(json) {
                    execute(authClient, payload.toString())
                        .parseAs<ALCharacterDetailsResult>()
                        .data.character
                        .let { char ->
                            char.copy(
                                media = char.media?.copy(
                                    edges = char.media.edges.filter { edge ->
                                        edge.node.isAdult != true &&
                                            (edge.node.type == null || edge.node.type == "ANIME")
                                    },
                                ),
                            )
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnilistRateLimitedException) {
                logcat(LogPriority.WARN, throwable = e) { "AniList rate limited; deferring character $characterId" }
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, throwable = e) { "Failed to fetch AniList character details" }
                null
            }
        }
    }

    /** Resolves the AniList media id of an anime that is only known by its MyAnimeList id. */
    suspend fun getMediaIdByMalId(idMal: Long): Long? {
        return withIOContext {
            try {
                val query = """
                query (${'$'}idMal: Int) {
                    Media (idMal: ${'$'}idMal, type: ANIME) {
                        id
                    }
                }
                """.trimIndent()
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("idMal", idMal)
                    }
                }
                with(json) {
                    // authClient for the same reason as getAnimeDetails: AniList's Cloudflare 403s
                    // anonymous app requests. Reached only while enriching an anime's info with
                    // AniList data; without a session the interceptor throws and the catch returns null.
                    execute(authClient, payload.toString())
                        .parseAs<ALMediaIdResult>()
                        .data.media.id
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnilistRateLimitedException) {
                logcat(LogPriority.WARN, throwable = e) { "AniList rate limited; deferring MAL id resolution for $idMal" }
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, throwable = e) { "Failed to resolve AniList media id from MAL id $idMal" }
                null
            }
        }
    }

    /**
     * The tracked media's next-airing episode from AniList's published schedule, or null when the
     * show has no upcoming episode (finished / between seasons). Authenticated (`authClient`) for the
     * same Cloudflare reason as the other reads. Failures are NOT swallowed here - they propagate so
     * the caller can keep whatever airing time it already had instead of mistaking a network error
     * for "nothing airing" and clearing a good value.
     */
    suspend fun getNextAiringEpisode(mediaId: Long): ALNextAiringEpisode? {
        return withIOContext {
            val query = """
            |query (${'$'}mediaId: Int) {
                |Media(id: ${'$'}mediaId, type: ANIME) {
                    |nextAiringEpisode {
                        |episode
                        |airingAt
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("mediaId", mediaId)
                }
            }
            with(json) {
                execute(authClient, payload.toString())
                    .parseAs<ALNextAiringResult>()
                    .data.media?.nextAiringEpisode
            }
        }
    }

    /**
     * The viewer's AniList notification feed (airing episodes, follows, activity and forum replies).
     * `resetNotificationCount` is always false here: viewing the feed must not mark anything
     * read on AniList. Read state is tracked per notification, locally, and changes only when
     * the user taps that notification.
     * Authenticated (`authClient`); AniList's Cloudflare refuses anonymous requests.
     */
    suspend fun getNotifications(): List<ALNotification> {
        return withIOContext {
            val query = """
            |query Notifications(${'$'}page: Int, ${'$'}perPage: Int) {
                |Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    |notifications(resetNotificationCount: false) {
                        |... on AiringNotification {
                            |id type episode contexts createdAt
                            |media { id title { userPreferred } coverImage { large } }
                        |}
                        |... on FollowingNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ActivityMessageNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ActivityMentionNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ActivityReplyNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ActivityReplySubscribedNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ActivityLikeNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ActivityReplyLikeNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ThreadCommentReplyNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ThreadCommentMentionNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ThreadCommentSubscribedNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ThreadCommentLikeNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on ThreadLikeNotification {
                            |id type context createdAt
                            |user { name avatar { large } }
                        |}
                        |... on RelatedMediaAdditionNotification {
                            |id type context createdAt
                            |media { id title { userPreferred } coverImage { large } }
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("page", 1)
                    put("perPage", NOTIFICATIONS_PER_PAGE)
                }
            }
            try {
                with(json) {
                    execute(authClient, payload.toString())
                        .parseAs<ALNotificationsResult>()
                        .data.page.notifications
                        // Notification kinds without a fragment above (media data change/merge/delete)
                        // come back as empty objects; drop them rather than show a blank row.
                        .filter { it.id != null }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnilistRateLimitedException) {
                logcat(LogPriority.WARN, throwable = e) { "AniList rate limited; deferring notifications" }
                throw e
            } catch (e: Exception) {
                // Rethrow rather than returning emptyList: an empty list is indistinguishable from
                // "genuinely no notifications", and the caller persists it over the cached feed and
                // seeds read-state from it. Throwing lets the screen model's catch keep the cache.
                logcat(LogPriority.ERROR, throwable = e) { "Failed to fetch AniList notifications" }
                throw e
            }
        }
    }

    /**
     * The viewer's server-side unread count, read without resetting it. Only used to seed
     * local read state on first sync (so already-read notifications don't all show unread);
     * afterwards read state is strictly per-tap and local. A failure degrades to "seed
     * nothing" rather than failing the whole refresh.
     */
    suspend fun getUnreadNotificationCount(): Int {
        return try {
            val query = """
            |query {
                |Viewer { unreadNotificationCount }
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
            }
            with(json) {
                execute(authClient, payload.toString())
                    .parseAs<ALUnreadCountResult>()
                    .data.viewer?.unreadNotificationCount ?: -1
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, throwable = e) { "Failed to fetch AniList unread count" }
            -1
        }
    }

    /**
     * The user's AniList anime entries with the given list status, ordered by when they were
     * added/updated (most recent first).
     */
    suspend fun getUserListWithStatus(userId: Int, status: String): UserListResult {
        return withIOContext {
            val query = """
            |query (${'$'}userId: Int!, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}status: MediaListStatus) {
                |Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                    |pageInfo {
                        |hasNextPage
                    |}
                    |mediaList (userId: ${'$'}userId, type: ANIME, status: ${'$'}status) {
                        |progress
                        |scoreRaw: score(format: POINT_100)
                        |media {
                            |id
                            |title {
                                |english
                                |romaji
                                |native
                                |userPreferred
                            |}
                            |coverImage {
                                |large
                            |}
                            |format
                            |episodes
                            |averageScore
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()

            val allItems = mutableListOf<TrackSearch>()
            var page = 1
            var complete = true
            while (true) {
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("userId", userId)
                        put("page", page)
                        put("perPage", 50)
                        put("status", status)
                    }
                }
                val result = try {
                    with(json) {
                        execute(authClient, payload.toString())
                            .parseAs<ALUserListAiringResult>()
                            .data.page
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep pages already gathered on a mid-pagination failure; only a cold
                    // failure (nothing collected yet) propagates.
                    if (allItems.isEmpty()) throw e
                    logcat(LogPriority.WARN, e) { "AniList: partial list load, keeping ${allItems.size} entries" }
                    complete = false
                    break
                }
                result.mediaList.forEach { item ->
                    val media = item.media ?: return@forEach
                    allItems += TrackSearch.create(TrackerManager.ANILIST).apply {
                        remote_id = media.id
                        title = media.title.userPreferred.orEmpty()
                        cover_url = media.coverImage.large ?: ""
                        publishing_type = media.format ?: ""
                        total_episodes = media.episodes ?: 0
                        last_episode_seen = (item.progress ?: 0).toDouble()
                        score = (item.scoreRaw ?: 0).toDouble()
                        tracking_url = animeUrl(remote_id)
                    }
                }
                if (!result.pageInfo.hasNextPage) break
                page++
                if (page > 50) {
                    complete = false
                    break // safety cap
                }
            }
            UserListResult(allItems.distinctBy { it.remote_id }, complete)
        }
    }

    suspend fun getUserListWithAiring(userId: Int): UserListResult {
        return withIOContext {
            val query = """
            |query (${'$'}userId: Int!, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}statusIn: [MediaListStatus]) {
                |Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                    |pageInfo {
                        |hasNextPage
                    |}
                    |mediaList (userId: ${'$'}userId, type: ANIME, status_in: ${'$'}statusIn) {
                        |media {
                            |id
                            |title {
                                |english
                                |romaji
                                |native
                                |userPreferred
                            |}
                            |coverImage {
                                |large
                            |}
                            |status
                            |nextAiringEpisode {
                                |episode
                                |airingAt
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()

            val allItems = mutableListOf<TrackSearch>()
            var page = 1
            var complete = true
            while (true) {
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("userId", userId)
                        put("page", page)
                        put("perPage", 50)
                        putJsonArray("statusIn") {
                            add(JsonPrimitive("CURRENT"))
                            add(JsonPrimitive("REPEATING"))
                        }
                    }
                }
                val result = try {
                    with(json) {
                        execute(authClient, payload.toString())
                            .parseAs<ALUserListAiringResult>()
                            .data.page
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep pages already gathered on a mid-pagination failure; only a cold
                    // failure (nothing collected yet) propagates.
                    if (allItems.isEmpty()) throw e
                    logcat(LogPriority.WARN, e) { "AniList: partial list load, keeping ${allItems.size} entries" }
                    complete = false
                    break
                }
                result.mediaList.forEach { item ->
                    val media = item.media ?: return@forEach
                    val airingAt = media.nextAiringEpisode?.airingAt ?: 0
                    // Skip entries with no scheduled next episode (completed/planning): they can
                    // never populate the calendar, so caching them just bloats the "mine" bucket.
                    if (airingAt <= 0) return@forEach
                    allItems += TrackSearch.create(TrackerManager.ANILIST).apply {
                        remote_id = media.id
                        title = media.title.userPreferred.orEmpty()
                        cover_url = media.coverImage.large ?: ""
                        publishing_status = media.status ?: ""
                        next_airing_episode = media.nextAiringEpisode?.episode ?: 0
                        next_airing_at = airingAt
                        tracking_url = animeUrl(remote_id)
                    }
                }
                if (!result.pageInfo.hasNextPage) break
                page++
                if (page > 50) {
                    complete = false
                    break // safety cap
                }
            }
            UserListResult(allItems.distinctBy { it.remote_id }, complete)
        }
    }

    /**
     * Fetches the global airing schedule for the next 7 local days: every anime with a scheduled
     * episode in that window, earliest-first, adult titles excluded. Uses the public endpoint,
     * so no login is required.
     */
    suspend fun getAiringSchedule(timeZone: ZoneId): AiringScheduleResult {
        return withIOContext {
            val query = """
            |query AiringSchedule(${'$'}page: Int, ${'$'}airingAtGreater: Int, ${'$'}airingAtLesser: Int) {
                |Page (perPage: 50, page: ${'$'}page) {
                    |pageInfo {
                        |hasNextPage
                    |}
                    |airingSchedules (airingAt_greater: ${'$'}airingAtGreater, airingAt_lesser: ${'$'}airingAtLesser, sort: TIME) {
                        |airingAt
                        |episode
                        |media {
                            |id
                            |title {
                                |english
                                |romaji
                                |native
                                |userPreferred
                            |}
                            |coverImage {
                                |large
                            |}
                            |status
                            |isAdult
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()

            // Query through the end of the displayed day (start of today+7), so every airing that
            // the weekly list can show is fetched; nothing lands in the tail that the UI drops.
            val now = ZonedDateTime.now(timeZone)
            val today = now.toLocalDate()
            val windowEnd = today.plusDays(7).atStartOfDay(timeZone).toEpochSecond()

            val allItems = mutableListOf<TrackSearch>()
            var complete = true
            var page = 1
            while (true) {
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("page", page)
                        put("airingAtGreater", now.toEpochSecond())
                        put("airingAtLesser", windowEnd)
                    }
                }
                val result = try {
                    with(json) {
                        // Same reason as getSeasonalAnime: authClient so the OAuth token rides
                        // along - AniList's Cloudflare 403s the anonymous request - and it keeps
                        // the rate limiter and the 429 cooldown for this up-to-25-page loop. The
                        // Upcoming "All anime" tab only calls this while logged in.
                        execute(authClient, payload.toString())
                            .parseAs<ALAiringScheduleResult>()
                            .data.page
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) {
                        "getAiringSchedule: page $page fetch failed, keeping the ${allItems.size} items already collected"
                    }
                    // Surface the error only if nothing was collected, so the caller can fall back
                    // to cached data instead of clobbering it with an empty list.
                    if (allItems.isEmpty()) throw e
                    complete = false
                    break
                }
                result.airingSchedule.forEach { item ->
                    val media = item.media ?: return@forEach
                    val airingAt = item.airingAt
                    // Skip entries without a scheduled time or from adult titles.
                    if (airingAt <= 0) return@forEach
                    if (media.isAdult == true) return@forEach
                    allItems += TrackSearch.create(TrackerManager.ANILIST).apply {
                        remote_id = media.id
                        title = media.title.userPreferred.orEmpty()
                        cover_url = media.coverImage.large ?: ""
                        publishing_status = media.status ?: ""
                        next_airing_episode = item.episode.toLong()
                        next_airing_at = airingAt
                        tracking_url = animeUrl(remote_id)
                    }
                }
                if (!result.pageInfo.hasNextPage) break
                page++
                // The 7-day window bounds the result set; this cap only guards against runaway
                // pagination (~1250 entries covers even peak-season weeks comfortably).
                if (page > 25) {
                    complete = false
                    break
                }
            }
            // One row per show: keep the earliest airing for titles that air multiple times
            // within the window (daily shows, split cours).
            AiringScheduleResult(allItems.distinctBy { it.remote_id }, complete)
        }
    }

    data class AiringScheduleResult(
        val items: List<TrackSearch>,
        val complete: Boolean,
    )

    /**
     * A paginated user-list fetch. [complete] is false when a mid-pagination failure (or the safety
     * cap) truncated the list, so callers must not cache a partial result as if it were the full one.
     */
    data class UserListResult(
        val items: List<TrackSearch>,
        val complete: Boolean,
    )

    suspend fun findLibAnime(track: Track, userid: Int): Track? {
        return withIOContext {
            val query = """
            |query (${'$'}id: Int!, ${'$'}anime_id: Int!) {
                |Page {
                    |mediaList(userId: ${'$'}id, type: ANIME, mediaId: ${'$'}anime_id) {
                        |id
                        |status
                        |scoreRaw: score(format: POINT_100)
                        |progress
                        |private
                        |startedAt {
                            |year
                            |month
                            |day
                        |}
                        |completedAt {
                            |year
                            |month
                            |day
                        |}
                        |media {
                            |id
                            |title {
                                |userPreferred
                            |}
                            |coverImage {
                                |large
                            |}
                            |format
                            |status
                            |episodes
                            |description
                            |startDate {
                                |year
                                |month
                                |day
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("id", userid)
                    put("anime_id", track.remote_id)
                }
            }
            with(json) {
                execute(authClient, payload.toString())
                    .parseAs<ALUserListEntryQueryResult>()
                    .data.page.mediaList
                    .mapNotNull { it.toALUserAnime() }
                    .firstOrNull()
                    ?.toTrack()
            }
        }
    }

    suspend fun getLibAnime(track: Track, userId: Int): Track {
        return findLibAnime(track, userId) ?: throw Exception("Could not find anime")
    }

    fun createOAuth(token: String): ALOAuth {
        return ALOAuth(token, "Bearer", System.currentTimeMillis() + 31536000000, 31536000000)
    }

    suspend fun getUserStats(): ALAnimeStats {
        return withIOContext {
            val query = """
            |query UserStats {
                |Viewer {
                    |id
                    |name
                    |avatar {
                        |large
                    |}
                    |mediaListOptions {
                        |scoreFormat
                    |}
                    |statistics {
                        |anime {
                            |count
                            |meanScore
                            |standardDeviation
                            |minutesWatched
                            |episodesWatched
                            |statuses {
                                |status
                                |count
                            |}
                            |formats {
                                |format
                                |count
                            |}
                            |genres {
                                |genre
                                |count
                            |}
                            |studios {
                                |studio {
                                    |id
                                    |name
                                |}
                                |count
                            |}
                            |scores {
                                |score
                                |count
                            |}
                            |lengths {
                                |length
                                |count
                            |}
                            |releaseYears {
                                |releaseYear
                                |count
                            |}
                            |startYears {
                                |startYear
                                |count
                            |}
                            |countries {
                                |country
                                |count
                            |}
                            |tags {
                                |tag {
                                    |name
                                |}
                                |count
                            |}
                            |voiceActors {
                                |voiceActor {
                                    |name {
                                        |full
                                    |}
                                    |image {
                                        |large
                                    |}
                                |}
                                |count
                            |}
                            |staff {
                                |staff {
                                    |name {
                                        |full
                                    |}
                                    |image {
                                        |large
                                    |}
                                |}
                                |count
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
            }
            with(json) {
                execute(authClient, payload.toString())
                    .parseAs<ALStatsResult>()
                    .data.viewer
                    .let { viewer ->
                        // The statistics query has no format argument, so the score distribution
                        // comes back in the viewer's own format: carry it along for the UI.
                        viewer.statistics.anime.copy(
                            scoreFormat = viewer.mediaListOptions?.scoreFormat,
                            userName = viewer.name,
                            avatarUrl = viewer.avatar?.large,
                        )
                    }
            }
        }
    }

    suspend fun getCurrentUser(): Pair<Int, String> {
        return withIOContext {
            val query = """
            |query User {
                |Viewer {
                    |id
                    |mediaListOptions {
                        |scoreFormat
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
            }
            with(json) {
                execute(authClient, payload.toString())
                    .parseAs<ALCurrentUserResult>()
                    .let {
                        val viewer = it.data.viewer
                        // scoreFormat and its container are both nullable; fall back to AniList's
                        // default so a null mediaListOptions never crashes login/getCurrentUser.
                        Pair(viewer.id, viewer.mediaListOptions?.scoreFormat ?: "POINT_10")
                    }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 0-1 foundation: My Lists / Browse / extended detail (anime-only,
    // login-gated via authClient like every other read).
    // -------------------------------------------------------------------------

    /**
     * Full MediaList entries for the My Lists screen. Paged server-side; callers pass
     * null [status] for all statuses (grouped client-side) or one MediaListStatus.
     */
    suspend fun getMyAnimeLists(
        userId: Int,
        status: String? = null,
    ): List<ALMyListEntry> {
        return withIOContext {
            // When status == null omit the status arg entirely: passing status:null
            // explicitly can be treated as a filter by some GraphQL servers.
            val query = if (status != null) {
                """
            |query (${'$'}userId: Int!, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}status: MediaListStatus) {
                |Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                    |pageInfo { hasNextPage }
                    |mediaList (userId: ${'$'}userId, type: ANIME, status: ${'$'}status, sort: [UPDATED_TIME_DESC]) {
                        |id mediaId status scoreRaw: score(format: POINT_100)
                        |progress repeat notes updatedAt
                        |startedAt { year month day } completedAt { year month day }
                        |media {
                            |id title { userPreferred english romaji native } coverImage { large }
                            |format status episodes averageScore popularity
                            |genres season seasonYear startDate { year month day }
                            |nextAiringEpisode { episode airingAt } trailer { id site }
                        |}
                    |}
                |}
            |}
            |
                """.trimMargin()
            } else {
                """
            |query (${'$'}userId: Int!, ${'$'}page: Int, ${'$'}perPage: Int) {
                |Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                    |pageInfo { hasNextPage }
                    |mediaList (userId: ${'$'}userId, type: ANIME, sort: [UPDATED_TIME_DESC]) {
                        |id mediaId status scoreRaw: score(format: POINT_100)
                        |progress repeat notes updatedAt
                        |startedAt { year month day } completedAt { year month day }
                        |media {
                            |id title { userPreferred english romaji native } coverImage { large }
                            |format status episodes averageScore popularity
                            |genres season seasonYear startDate { year month day }
                            |nextAiringEpisode { episode airingAt } trailer { id site }
                        |}
                    |}
                |}
            |}
            |
                """.trimMargin()
            }
            val all = mutableListOf<ALMyListEntry>()
            var page = 1
            while (true) {
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("userId", userId)
                        put("page", page)
                        put("perPage", 50)
                        if (status != null) put("status", status)
                    }
                }
                val result = try {
                    with(json) {
                        execute(authClient, payload.toString())
                            .parseAs<ALMyListResult>().data.page
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the pages already gathered rather than failing the whole list on a
                    // mid-pagination 429/network/parse error; only a cold failure propagates.
                    if (all.isEmpty()) throw e
                    logcat(LogPriority.WARN, e) { "AniList: partial list load, keeping ${all.size} entries" }
                    break
                }
                all += result.mediaList
                if (!result.pageInfo.hasNextPage) break
                page++
                if (page > 50) break
            }
            all.distinctBy { it.media?.id ?: it.mediaId }
        }
    }

    suspend fun getMediaListOptions(): ALMediaListOptions? {
        return withIOContext {
            try {
                val query = """
                |query {
                    |Viewer {
                        |mediaListOptions {
                            |scoreFormat rowOrder
                            |animeList { splitCompletedSectionByFormat customLists sectionOrder }
                        |}
                    |}
                |}
                |
                """.trimMargin()
                val payload = buildJsonObject { put("query", query) }
                with(json) {
                    execute(authClient, payload.toString())
                        .parseAs<ALMediaListOptionsResult>().data.viewer.mediaListOptions
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to fetch AniList list options" }
                null
            }
        }
    }

    /**
     * Full entry save used by the list edit sheet: status/progress/score/repeat/notes/dates.
     * [scoreRaw100] is the 0-100 POINT_100 value stored server-side.
     */
    suspend fun saveMediaListEntryFull(
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
        return withIOContext {
            val query = """
            |mutation SaveEntry(
                |${'$'}mediaId: Int, ${'$'}id: Int, ${'$'}status: MediaListStatus,
                |${'$'}progress: Int, ${'$'}scoreRaw: Int, ${'$'}repeat: Int, ${'$'}notes: String,
                |${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput
            |) {
                |SaveMediaListEntry(
                    |mediaId: ${'$'}mediaId, id: ${'$'}id, status: ${'$'}status,
                    |progress: ${'$'}progress, scoreRaw: ${'$'}scoreRaw, repeat: ${'$'}repeat, notes: ${'$'}notes,
                    |startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt
                |) { id status progress }
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    if (mediaId != null && mediaId > 0L) put("mediaId", mediaId)
                    if (listId != null && listId > 0L) put("id", listId)
                    if (status != null) put("status", status)
                    if (progress != null) put("progress", progress)
                    if (scoreRaw100 != null) put("scoreRaw", scoreRaw100)
                    if (repeat != null) put("repeat", repeat)
                    if (notes != null) put("notes", notes)
                    // Only send a date when one is provided; an explicit all-null FuzzyDateInput
                    // (createDate(0L)) is read by AniList as "clear this date".
                    // Omitted when null so the existing server-side date is left untouched.
                    if (startedAtMillis != null) put("startedAt", createDate(startedAtMillis))
                    if (completedAtMillis != null) put("completedAt", createDate(completedAtMillis))
                }
            }
            try {
                with(json) {
                    execute(authClient, payload.toString())
                        .parseAs<ALAddAnimeResult>()
                        .data.entry.id
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to save AniList entry: payload=$payload" }
                throw e
            }
        }
    }

    /**
     * Drops the cached favourite id set so the next read reloads it from the server. Called on logout
     * and account switch: without it, one account's favourite membership would drive [isAnimeFavourite]
     * and the heart toggle for the next account - and could flip the wrong favourite server-side.
     * Bumping the epoch also invalidates any in-flight [getFavouriteAnime] snapshot so it can't
     * repopulate the set with the previous account's data.
     */
    fun invalidateFavouritesCache() {
        favouritesEpoch++
        favouriteAnimeIds = null
    }

    /** Toggles the anime's favourite state and returns the resulting state (true = now favourite). */
    suspend fun toggleFavouriteAnime(animeId: Long): Boolean {
        // Establish the prior state before mutating so the returned/cached new state is authoritative
        // rather than a guess: ToggleFavourite flips whatever the server holds, so without a loaded
        // cache we can't tell add from remove. If the list can't be loaded, fail (the caller aborts
        // the tap) instead of blindly reporting "now favourite" and possibly inverting the state.
        if (favouritesMutex.withLock { favouriteAnimeIds } == null) {
            if (runCatching { getFavouriteAnime() }.isFailure ||
                favouritesMutex.withLock { favouriteAnimeIds } == null
            ) {
                throw IllegalStateException("AniList: can't toggle favourite; favourites not loaded")
            }
        }
        // Capture the target state and claim the epoch BEFORE the network call, both under the lock.
        // ToggleFavourite flips whatever the server holds, so the authoritative new state is the
        // negation of what we currently hold. Bumping the epoch here (not after the call) means a full
        // refresh that snapshotted earlier can't commit its post-mutation view over ours and invert
        // the result - the residual window the plain post-call flip left open.
        val target = favouritesMutex.withLock {
            favouritesEpoch++
            val ids = favouriteAnimeIds ?: mutableSetOf<Long>().also { favouriteAnimeIds = it }
            !ids.contains(animeId)
        }
        return withIOContext {
            val query = """
            |mutation (${'$'}animeId: Int) {
                |ToggleFavourite (animeId: ${'$'}animeId) {
                    |anime { nodes { id } }
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") { put("animeId", animeId) }
            }
            // NonCancellable: this write must outlive the detail screen. If the user taps the heart
            // then immediately navigates back, the caller's composition scope is cancelled, but the
            // favourite still has to reach AniList (and the cache mirror stay consistent).
            withContext(NonCancellable) {
                // execute() already throws AnilistGraphqlException if the toggle was rejected.
                execute(authClient, payload.toString()).close()
                // Apply the pre-captured [target] (not a re-read of current membership) under the
                // lock, so a full refresh that committed the post-mutation server state between the
                // capture above and here can't flip the result. The target is authoritative.
                favouritesMutex.withLock {
                    val ids = favouriteAnimeIds ?: mutableSetOf<Long>().also { favouriteAnimeIds = it }
                    if (target) ids.add(animeId) else ids.remove(animeId)
                }
                target
            }
        }
    }

    data class BrowsePage(
        val items: List<eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem>,
        val hasNextPage: Boolean,
        val currentPage: Int,
    )

    /**
     * Advanced anime browse with server-side filters. All params optional besides paging;
     * null/empty means "no filter". [sort] is an AniList MediaSort value.
     */
    suspend fun browseAnime(
        page: Int,
        perPage: Int = 24,
        search: String? = null,
        genreIn: List<String> = emptyList(),
        tagIn: List<String> = emptyList(),
        formatIn: List<String> = emptyList(),
        statusIn: List<String> = emptyList(),
        season: String? = null,
        seasonYear: Int? = null,
        sort: String = "TRENDING_DESC",
    ): BrowsePage {
        return withIOContext {
            // AniList 500s when format_in/status_in are passed as explicit null, so filter
            // args are only declared + sent when actually used. GraphQL also rejects
            // declared-but-unused variables, hence both halves are built together.
            val hasSearch = !search.isNullOrBlank()
            val hasSeason = !season.isNullOrBlank()
            val hasYear = seasonYear != null && seasonYear > 0
            val searchVar = if (hasSearch) "${'$'}search: String, " else ""
            val searchArg = if (hasSearch) "search: ${'$'}search, " else ""
            val genreVar = if (genreIn.isNotEmpty()) "${'$'}genreIn: [String], " else ""
            val genreArg = if (genreIn.isNotEmpty()) "genre_in: ${'$'}genreIn, " else ""
            val tagVar = if (tagIn.isNotEmpty()) "${'$'}tagIn: [String], " else ""
            val tagArg = if (tagIn.isNotEmpty()) "tag_in: ${'$'}tagIn, " else ""
            val formatVar = if (formatIn.isNotEmpty()) "${'$'}formatIn: [MediaFormat], " else ""
            val formatArg = if (formatIn.isNotEmpty()) "format_in: ${'$'}formatIn, " else ""
            val statusVar = if (statusIn.isNotEmpty()) "${'$'}statusIn: [MediaStatus], " else ""
            val statusArg = if (statusIn.isNotEmpty()) "status_in: ${'$'}statusIn, " else ""
            val seasonVar = if (hasSeason) "${'$'}season: MediaSeason, " else ""
            val seasonArg = if (hasSeason) "season: ${'$'}season, " else ""
            val yearVar = if (hasYear) "${'$'}seasonYear: Int, " else ""
            val yearArg = if (hasYear) "seasonYear: ${'$'}seasonYear, " else ""
            val query = """
            |query Browse(
                |${'$'}page: Int, ${'$'}perPage: Int, $searchVar$genreVar$tagVar$formatVar$statusVar$seasonVar$yearVar${'$'}sort: [MediaSort]
            |) {
                |Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                    |pageInfo { hasNextPage currentPage }
                    |media(
                        |type: ANIME, isAdult: false, $searchArg$genreArg$tagArg$formatArg$statusArg$seasonArg$yearArg sort: ${'$'}sort
                    |) {
                        |id title { userPreferred english romaji native }
                        |coverImage { large extraLarge } bannerImage format status episodes
                        |averageScore popularity favourites genres season seasonYear
                        |startDate { year month day }
                        |nextAiringEpisode { episode airingAt } trailer { id site }
                        |mediaListEntry { id status score(format: POINT_100) progress repeat notes }
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("page", page)
                    put("perPage", perPage)
                    if (hasSearch) put("search", search!!)
                    if (genreIn.isNotEmpty()) putJsonArray("genreIn") { genreIn.forEach { add(it) } }
                    if (tagIn.isNotEmpty()) putJsonArray("tagIn") { tagIn.forEach { add(it) } }
                    if (formatIn.isNotEmpty()) putJsonArray("formatIn") { formatIn.forEach { add(it) } }
                    if (statusIn.isNotEmpty()) putJsonArray("statusIn") { statusIn.forEach { add(it) } }
                    if (hasSeason) put("season", season!!)
                    if (hasYear) put("seasonYear", seasonYear!!)
                    putJsonArray("sort") { add(sort) }
                }
            }
            with(json) {
                execute(authClient, payload.toString())
                    .parseAs<ALBrowseResult>().data.page.let {
                        BrowsePage(it.media, it.pageInfo.hasNextPage, it.pageInfo.currentPage)
                    }
            }
        }
    }

    suspend fun getGenreCollection(): List<String> {
        return withIOContext {
            try {
                val payload = buildJsonObject { put("query", "query { GenreCollection }") }
                with(json) {
                    execute(authClient, payload.toString())
                        .parseAs<ALGenreCollectionResult>().data.genres
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnilistRateLimitedException) {
                // Don't mask a rate-limit/cooldown as an empty collection; let it propagate.
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to fetch AniList genres" }
                emptyList()
            }
        }
    }

    suspend fun getTagCollection(): List<eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaTag> {
        return withIOContext {
            try {
                val query = """
                |query { MediaTagCollection { name description category isAdult } }
                |
                """.trimMargin()
                val payload = buildJsonObject { put("query", query) }
                with(json) {
                    execute(authClient, payload.toString())
                        .parseAs<ALTagCollectionResult>().data.tags.filter { it.isAdult.not() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnilistRateLimitedException) {
                // Don't mask a rate-limit/cooldown as an empty collection; let it propagate.
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to fetch AniList tags" }
                emptyList()
            }
        }
    }

    /**
     * Extended detail query: base detail fields plus staff, recommendations, tags,
     * favourite state and the viewer's own list entry (for the "My entry" card).
     */
    suspend fun getAnimeDetailsFull(mediaId: Long): ALAnimeDetails? {
        return withIOContext {
            try {
                val query = """
                query AnimeDetailsFull(${'$'}id: Int) {
                    Media (id: ${'$'}id, type: ANIME) {
                        id
                        idMal
                        title { english romaji native userPreferred }
                        coverImage { extraLarge large }
                        bannerImage description format status episodes duration
                        averageScore popularity favourites genres season seasonYear
                        isFavourite
                        mediaListEntry { id status score(format: POINT_100) progress repeat notes startedAt { year month day } completedAt { year month day } }
                        tags { name rank isMediaSpoiler }
                        studios { edges { isMain node { id name } } }
                        startDate { year month day } endDate { year month day }
                        nextAiringEpisode { episode airingAt }
                        trailer { id site }
                        relations { edges { relationType node {
                            id title { english romaji native userPreferred }
                            coverImage { large } format averageScore episodes
                            mediaListEntry { id status }
                        } } }
                        characters(sort: [ROLE]) { edges { role node {
                            id name { userPreferred } image { large }
                        } voiceActors(sort: [RELEVANCE, ID]) { id name { full userPreferred } languageV2 image { large medium } } } }
                        staff(sort: [RELEVANCE]) { edges { role node {
                            id name { full userPreferred } image { large medium }
                        } } }
                        recommendations(sort: [RATING_DESC]) { nodes {
                            id
                            rating
                            mediaRecommendation {
                                id title { userPreferred english romaji native }
                                coverImage { large } averageScore format episodes
                                mediaListEntry { id status }
                            }
                        } }
                    }
                }
                """.trimIndent()
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") { put("id", mediaId) }
                }
                with(json) {
                    execute(authClient, payload.toString())
                        .parseAs<ALAnimeDetailsResult>().data.media
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnilistRateLimitedException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, throwable = e) { "Failed to fetch AniList full details" }
                null
            }
        }
    }

    suspend fun getStaffDetails(staffId: Long): ALStaffDetails? {
        return withIOContext {
            try {
                val query = """
                |query StaffDetails(${'$'}id: Int) {
                    |Staff(id: ${'$'}id) {
                        |id name { full userPreferred native } image { large medium }
                        |description primaryOccupations languageV2 gender age favourites
                        |dateOfBirth { year month day }
                        |staffMedia(sort: [START_DATE_DESC], type: ANIME, perPage: 25) {
                            |edges { staffRole node {
                                |id type isAdult title { userPreferred english romaji native } coverImage { large }
                                |averageScore format episodes
                                |mediaListEntry { id status }
                            |} }
                        |}
                        |characterMedia(sort: [POPULARITY_DESC], type: ANIME, perPage: 25) {
                            |edges { characterRole node {
                                |id type isAdult title { userPreferred english romaji native } coverImage { large }
                                |averageScore format episodes
                                |mediaListEntry { id status }
                            |} characters { id name { full userPreferred } image { large medium } } }
                        |}
                    |}
                |}
                |
                """.trimMargin()
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") { put("id", staffId) }
                }
                with(json) {
                    execute(authClient, payload.toString())
                        .parseAs<ALStaffDetailsResult>().data.staff
                        ?.let { staff ->
                            staff.copy(
                                staffMedia = staff.staffMedia?.copy(
                                    edges = staff.staffMedia.edges.filter { edge ->
                                        edge.node?.isAdult != true &&
                                            (edge.node?.type == null || edge.node.type == "ANIME")
                                    },
                                ),
                                characterMedia = staff.characterMedia?.copy(
                                    edges = staff.characterMedia.edges.filter { edge ->
                                        edge.node?.isAdult != true &&
                                            (edge.node?.type == null || edge.node.type == "ANIME")
                                    },
                                ),
                            )
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, throwable = e) { "Failed to fetch AniList staff details" }
                null
            }
        }
    }

    // ANK -->
    suspend fun getStudioDetails(
        studioId: Long? = null,
        studioName: String? = null,
        sort: String = "POPULARITY_DESC",
        page: Int = 1,
        perPage: Int = 36,
    ): ALStudioDetails? {
        return withIOContext {
            val useId = studioId != null && studioId > 0L
            val cleanName = studioName?.trim()
            if (!useId && cleanName.isNullOrEmpty()) return@withIOContext null
            try {
                val studioVar = if (useId) "${'$'}id: Int" else "${'$'}search: String"
                val studioArg = if (useId) "id: ${'$'}id" else "search: ${'$'}search"
                val query = """
                |query StudioDetails($studioVar, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort]) {
                    |Studio($studioArg) {
                        |id name isAnimationStudio favourites siteUrl
                        |media(sort: ${'$'}sort, page: ${'$'}page, perPage: ${'$'}perPage) {
                            |pageInfo { hasNextPage currentPage total }
                            |nodes {
                                |id type isAdult
                                |title { userPreferred english romaji native }
                                |coverImage { large extraLarge }
                                |format status episodes averageScore popularity favourites
                                |season seasonYear startDate { year month day }
                                |mediaListEntry { id status score(format: POINT_100) progress repeat notes }
                            |}
                        |}
                    |}
                |}
                |
                """.trimMargin()
                val payload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        if (useId) put("id", studioId!!) else put("search", cleanName!!)
                        put("page", page)
                        put("perPage", perPage)
                        putJsonArray("sort") { add(sort) }
                    }
                }
                with(json) {
                    val studio = execute(authClient, payload.toString())
                        .parseAs<ALStudioDetailsResult>().data.studio
                    studio?.copy(
                        media = studio.media?.copy(
                            nodes = studio.media.nodes
                                .filter { (it.type == null || it.type == "ANIME") && it.isAdult != true }
                                .distinctBy { it.id },
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, throwable = e) { "Failed to fetch AniList studio details" }
                null
            }
        }
    }
    // ANK <--

    suspend fun getFavouriteAnime(): List<eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem> {
        return withIOContext {
            try {
                // AniList's Viewer.favourites connection is unreliable (it returns empty/stale
                // results), so favourites are read the way working clients do: resolve the viewer
                // id, then page through User(id:).favourites.anime until there's no next page.
                val (viewerId, _) = getCurrentUser()
                // Snapshot the epoch before the (unlocked) network reads; if a toggle bumps it
                // while we page, the snapshot below is stale and must not overwrite the cache.
                val startEpoch = favouritesMutex.withLock { favouritesEpoch }
                val all = mutableListOf<eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem>()
                var page = 1
                var complete = true
                while (true) {
                    val query = """
                    |query (${'$'}id: Int, ${'$'}page: Int) {
                        |User(id: ${'$'}id) { favourites { anime(page: ${'$'}page, perPage: 50) { nodes {
                            |id title { userPreferred english romaji native }
                            |coverImage { large extraLarge } format status episodes
                            |averageScore popularity favourites genres season seasonYear
                            |startDate { year month day }
                            |nextAiringEpisode { episode airingAt } trailer { id site }
                        |} pageInfo { hasNextPage } } } }
                    |}
                    |
                    """.trimMargin()
                    val payload = buildJsonObject {
                        put("query", query)
                        putJsonObject("variables") {
                            put("id", viewerId)
                            put("page", page)
                        }
                    }
                    val connection = with(json) {
                        execute(authClient, payload.toString())
                            .parseAs<ALFavouritesResult>()
                            .data.user?.favourites?.anime
                    }
                    all += connection?.nodes.orEmpty()
                    if (connection?.pageInfo?.hasNextPage != true) break
                    page++
                    if (page > FAVOURITES_MAX_PAGES) {
                        // Hit the page cap with more favourites remaining: the id set below would be
                        // incomplete, so mark it non-authoritative instead of silently truncating.
                        complete = false
                        break
                    }
                }
                // Refresh the id snapshot the heart toggle reads from - but only when the full list
                // was fetched. A truncated set would make isAnimeFavourite report false for real
                // favourites and let a toggle remove them server-side, so leave the cache untouched
                // (state stays "unknown"). Also skip if a toggle landed while we were fetching (its
                // cache reflects a newer user action than this snapshot).
                if (complete) {
                    val ids = all.mapTo(mutableSetOf()) { it.id }
                    favouritesMutex.withLock {
                        if (favouritesEpoch == startEpoch) favouriteAnimeIds = ids
                    }
                }
                // Dedup by id: the favourites connection can shift between page requests, so the same
                // anime can land on two pages and crash the grid's id-keyed items().
                all.distinctBy { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surface the failure so the Favourites tab shows its error/retry state instead of
                // an empty screen that reads as "you have no favourites". A genuinely empty list
                // arrives as an empty `nodes` array, not an exception, so it still renders as empty.
                logcat(LogPriority.WARN, e) { "Failed to fetch AniList favourites" }
                throw e
            }
        }
    }

    /**
     * Whether the viewer has favourited [mediaId], or null when the favourites list can't be
     * loaded (state unknown). Callers must not let a tap act on a null - the toggle direction
     * would be a guess.
     */
    suspend fun isAnimeFavourite(mediaId: Long): Boolean? {
        // Fast path: the set is already loaded. contains() runs under the lock so it can't race a
        // concurrent toggle/refresh mutating the same set.
        favouritesMutex.withLock { favouriteAnimeIds?.contains(mediaId) }?.let { return it }
        // Cold path: load the list once (getFavouriteAnime populates the set under the lock), then
        // re-check. A load failure returns null so the caller keeps the state "unknown".
        if (runCatching { getFavouriteAnime() }.isFailure) return null
        return favouritesMutex.withLock { favouriteAnimeIds?.contains(mediaId) }
    }

    private fun createDate(dateValue: Long): JsonObject {
        if (dateValue == 0L) {
            return buildJsonObject {
                put("year", JsonNull)
                put("month", JsonNull)
                put("day", JsonNull)
            }
        }

        val dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateValue), ZoneId.systemDefault())
        return buildJsonObject {
            put("year", dateTime.year)
            put("month", dateTime.monthValue)
            put("day", dateTime.dayOfMonth)
        }
    }

    companion object {
        private val CLIENT_ID: String get() = eu.kanade.tachiyomi.BuildConfig.ANILIST_CLIENT_ID
        private const val API_URL = "https://graphql.anilist.co/"
        private const val BASE_URL = "https://anilist.co/api/v2/"
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val RETRY_AFTER_HEADER = "Retry-After"

        // Server 429s don't always include a Retry-After header; back off this long in that case.
        private const val DEFAULT_COOLDOWN_MS = 60_000L

        // Never retry faster than this even if the server asks, to amortise a cache stampede.
        private const val MIN_COOLDOWN_MS = 1_000L

        // One page of the notification feed; AniList's own site shows a comparable window.
        private const val NOTIFICATIONS_PER_PAGE = 50

        // Safety cap on favourites paging (50/page) so a bad hasNextPage can't loop forever;
        // 40 pages = 2000 favourites, well past any realistic list.
        private const val FAVOURITES_MAX_PAGES = 40
        private const val BASE_ANIME_URL = "https://anilist.co/anime/"

        fun animeUrl(mediaId: Long): String {
            return BASE_ANIME_URL + mediaId
        }

        fun authUrl(state: String): Uri = "${BASE_URL}oauth/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("state", state)
            .build()
    }
}
