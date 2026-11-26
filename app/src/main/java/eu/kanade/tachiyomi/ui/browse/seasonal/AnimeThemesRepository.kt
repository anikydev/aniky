package eu.kanade.tachiyomi.ui.browse.seasonal

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.ui.browse.seasonal.model.AnimeThemeSong
import eu.kanade.tachiyomi.ui.browse.seasonal.model.ThemeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class AnimeThemesRepository(
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
    private val json: Json = Injekt.get(),
) {
    fun getCached(mediaId: Long): List<AnimeThemeSong>? = cache[mediaId]

    suspend fun getThemeSongs(
        mediaId: Long,
        idMal: Long?,
        title: String?,
        forceRefresh: Boolean = false,
    ): List<AnimeThemeSong> {
        if (!forceRefresh) {
            cache[mediaId]?.let { return it }
        }

        val songs = withIOContext {
            // 1. Primary: AnimeThemes GraphQL API (fast, reliable, includes video/audio streams)
            var result = fetchGraphQlByExternalSite("ANILIST", mediaId)

            if (result.isEmpty() && idMal != null && idMal > 0) {
                result = fetchGraphQlByExternalSite("MAL", idMal)
            }

            if (result.isEmpty() && !title.isNullOrBlank()) {
                result = fetchGraphQlBySearch(title)
            }

            // 2. Fallback: AnimeThemes REST API
            if (result.isEmpty()) {
                result = fetchAnimeThemesByAniListId(mediaId)
            }

            if (result.isEmpty() && idMal != null && idMal > 0) {
                result = fetchAnimeThemesByMalId(idMal)
            }

            if (result.isEmpty() && !title.isNullOrBlank()) {
                result = fetchAnimeThemesByQuery(title)
            }

            // 3. Fallback: Jikan (MyAnimeList) text themes
            if (result.isEmpty() && idMal != null && idMal > 0) {
                result = fetchJikanThemes(idMal)
            }

            result
        }

        if (songs.isNotEmpty()) {
            cache[mediaId] = songs
        }
        return songs
    }

    private suspend fun fetchGraphQlByExternalSite(site: String, externalId: Long): List<AnimeThemeSong> {
        return try {
            val query = """
                query (${'$'}id: [Int!]) {
                    findAnimeByExternalSite(site: $site, id: ${'$'}id) {
                        $GRAPHQL_ANIME_THEMES_SELECTION
                    }
                }
            """.trimIndent()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    putJsonArray("id") {
                        add(externalId.toInt())
                    }
                }
            }
            val response = client.newCall(
                POST(
                    url = ANIME_THEMES_GRAPHQL_URL,
                    body = payload.toString().toRequestBody(JSON_MEDIA_TYPE),
                ),
            ).awaitSuccess()
            val parsed = with(json) { response.parseAs<AnimeThemesGraphQlResponse>() }
            parseGraphQlAnimeList(parsed.data?.findAnimeByExternalSite.orEmpty())
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "AnimeThemes GraphQL: fetch by $site ID $externalId failed" }
            emptyList()
        }
    }

    private suspend fun fetchGraphQlBySearch(searchQuery: String): List<AnimeThemeSong> {
        return try {
            val query = """
                query (${'$'}search: String!) {
                    animePagination(search: ${'$'}search, first: 1) {
                        data {
                            $GRAPHQL_ANIME_THEMES_SELECTION
                        }
                    }
                }
            """.trimIndent()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("search", searchQuery)
                }
            }
            val response = client.newCall(
                POST(
                    url = ANIME_THEMES_GRAPHQL_URL,
                    body = payload.toString().toRequestBody(JSON_MEDIA_TYPE),
                ),
            ).awaitSuccess()
            val parsed = with(json) { response.parseAs<AnimeThemesGraphQlResponse>() }
            parseGraphQlAnimeList(parsed.data?.animePagination?.data.orEmpty())
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "AnimeThemes GraphQL: search '$searchQuery' failed" }
            emptyList()
        }
    }

    private fun parseGraphQlAnimeList(animeList: List<GraphQlAnimeDto>): List<AnimeThemeSong> {
        val themes = animeList.flatMap { it.animethemes }
        return themes.mapNotNull { entry ->
            val songTitle = (entry.song?.title?.romaji ?: entry.song?.title?.native)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            val type = when (entry.type?.uppercase()) {
                "ED" -> ThemeType.ED
                else -> ThemeType.OP
            }
            val seq = entry.sequence
            val label = when {
                seq != null -> "${type.name} $seq"
                !entry.slug.isNullOrBlank() -> entry.slug
                else -> type.name
            }
            val artist = entry.song?.performances.orEmpty()
                .mapNotNull { perf ->
                    perf.alias?.trim()?.takeIf { it.isNotEmpty() }
                        ?: perf.artist?.name?.main?.trim()?.takeIf { it.isNotEmpty() }
                        ?: perf.artist?.name?.native?.trim()?.takeIf { it.isNotEmpty() }
                }
                .distinct()
                .joinToString(", ")
                .takeIf { it.isNotEmpty() }

            val preferredEntry = entry.animethemeentries
                .sortedWith(
                    compareBy<GraphQlThemeVersionDto> { it.nsfw == true }
                        .thenBy { it.spoiler == true },
                )
                .firstOrNull { !it.videos?.nodes.isNullOrEmpty() }
                ?: entry.animethemeentries.firstOrNull()

            val bestVideo = preferredEntry?.videos?.nodes.orEmpty()
                .filter { !it.link.isNullOrBlank() }
                .sortedWith(
                    compareByDescending<AnimeThemeVideoDto> { (it.resolution ?: 0) in 720..1080 }
                        .thenByDescending { it.nc == true }
                        .thenBy { abs((it.resolution ?: 720) - 720) },
                )
                .firstOrNull()

            AnimeThemeSong(
                type = type,
                sequence = seq,
                label = label,
                title = songTitle,
                artist = artist,
                episodes = preferredEntry?.episodes?.trim()?.takeIf { it.isNotEmpty() },
                videoUrl = bestVideo?.link?.takeIf { it.startsWith("http") },
                audioUrl = bestVideo?.audio?.link?.takeIf { it.startsWith("http") },
            )
        }.distinctBy { "${it.type}_${it.title}_${it.artist}" }
            .sortedWith(
                compareBy<AnimeThemeSong> { it.type }
                    .thenBy { it.sequence ?: Int.MAX_VALUE }
                    .thenBy { it.label },
            )
    }

    private suspend fun fetchAnimeThemesByAniListId(mediaId: Long): List<AnimeThemeSong> {
        return try {
            val url = "https://api.animethemes.moe/anime".toHttpUrl().newBuilder()
                .addQueryParameter("filter[has]", "resources")
                .addQueryParameter("filter[resource][site]", "AniList")
                .addQueryParameter("filter[resource][external_id]", mediaId.toString())
                .addQueryParameter("include", ANIME_THEMES_INCLUDE)
                .build()
            val response = client.newCall(GET(url.toString())).awaitSuccess()
            val data = with(json) { response.parseAs<AnimeThemesResponse>() }
            parseAnimeThemesResponse(data)
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "AnimeThemes: fetch by AniList ID $mediaId failed" }
            emptyList()
        }
    }

    private suspend fun fetchAnimeThemesByMalId(idMal: Long): List<AnimeThemeSong> {
        return try {
            val url = "https://api.animethemes.moe/anime".toHttpUrl().newBuilder()
                .addQueryParameter("filter[has]", "resources")
                .addQueryParameter("filter[resource][site]", "MyAnimeList")
                .addQueryParameter("filter[resource][external_id]", idMal.toString())
                .addQueryParameter("include", ANIME_THEMES_INCLUDE)
                .build()
            val response = client.newCall(GET(url.toString())).awaitSuccess()
            val data = with(json) { response.parseAs<AnimeThemesResponse>() }
            parseAnimeThemesResponse(data)
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "AnimeThemes: fetch by MAL ID $idMal failed" }
            emptyList()
        }
    }

    private suspend fun fetchAnimeThemesByQuery(query: String): List<AnimeThemeSong> {
        return try {
            val url = "https://api.animethemes.moe/anime".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("include", ANIME_THEMES_INCLUDE)
                .addQueryParameter("page[size]", "1")
                .build()
            val response = client.newCall(GET(url.toString())).awaitSuccess()
            val data = with(json) { response.parseAs<AnimeThemesResponse>() }
            parseAnimeThemesResponse(data)
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "AnimeThemes: fetch by query $query failed" }
            emptyList()
        }
    }

    private fun parseAnimeThemesResponse(response: AnimeThemesResponse): List<AnimeThemeSong> {
        val themes = response.anime.flatMap { it.animethemes }
        return themes.mapNotNull { entry ->
            val songTitle = entry.song?.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val type = when (entry.type?.uppercase()) {
                "ED" -> ThemeType.ED
                else -> ThemeType.OP
            }
            val seq = entry.sequence
            val label = when {
                seq != null -> "${type.name} $seq"
                !entry.slug.isNullOrBlank() -> entry.slug
                else -> type.name
            }
            val artist = entry.song.artists
                .mapNotNull { it.name?.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
                .takeIf { it.isNotEmpty() }

            val preferredEntry = entry.animethemeentries
                .sortedWith(
                    compareBy<AnimeThemeVersionDto> { it.nsfw == true }
                        .thenBy { it.spoiler == true },
                )
                .firstOrNull { it.videos.isNotEmpty() }
                ?: entry.animethemeentries.firstOrNull()

            val bestVideo = preferredEntry?.videos
                ?.filter { !it.link.isNullOrBlank() }
                ?.sortedWith(
                    compareByDescending<AnimeThemeVideoDto> { (it.resolution ?: 0) in 720..1080 }
                        .thenByDescending { it.nc == true }
                        .thenBy { abs((it.resolution ?: 720) - 720) },
                )
                ?.firstOrNull()

            AnimeThemeSong(
                type = type,
                sequence = seq,
                label = label,
                title = songTitle,
                artist = artist,
                episodes = preferredEntry?.episodes?.trim()?.takeIf { it.isNotEmpty() },
                videoUrl = bestVideo?.link?.takeIf { it.startsWith("http") },
                audioUrl = bestVideo?.audio?.link?.takeIf { it.startsWith("http") },
            )
        }.distinctBy { "${it.type}_${it.title}_${it.artist}" }
            .sortedWith(
                compareBy<AnimeThemeSong> { it.type }
                    .thenBy { it.sequence ?: Int.MAX_VALUE }
                    .thenBy { it.label },
            )
    }

    private suspend fun fetchJikanThemes(idMal: Long): List<AnimeThemeSong> {
        return try {
            val url = "https://api.jikan.moe/v4/anime/$idMal/themes"
            val response = client.newCall(GET(url)).awaitSuccess()
            val data = with(json) { response.parseAs<JikanThemesResponse>() }
            val result = mutableListOf<AnimeThemeSong>()
            data.data?.openings?.forEach { raw ->
                parseJikanThemeString(raw, ThemeType.OP)?.let { result.add(it) }
            }
            data.data?.endings?.forEach { raw ->
                parseJikanThemeString(raw, ThemeType.ED)?.let { result.add(it) }
            }
            result.distinctBy { "${it.type}_${it.title}_${it.artist}" }
                .sortedWith(
                    compareBy<AnimeThemeSong> { it.type }
                        .thenBy { it.sequence ?: Int.MAX_VALUE }
                        .thenBy { it.label },
                )
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Jikan: fetch themes for MAL ID $idMal failed" }
            emptyList()
        }
    }

    private fun parseJikanThemeString(raw: String, type: ThemeType): AnimeThemeSong? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        var sequence: Int? = null
        val colonIdx = text.indexOf(':')
        if (colonIdx in 1..4 && text.substring(0, colonIdx).all { it.isDigit() }) {
            sequence = text.substring(0, colonIdx).trim().toIntOrNull()
            text = text.substring(colonIdx + 1).trim()
        }
        var episodeNote: String? = null
        val lastParenOpen = text.lastIndexOf('(')
        val lastParenClose = text.lastIndexOf(')')
        if (lastParenOpen != -1 && lastParenClose == text.length - 1 && lastParenOpen < lastParenClose) {
            episodeNote = text.substring(lastParenOpen + 1, lastParenClose).trim()
            text = text.substring(0, lastParenOpen).trim()
        }
        val byIdx = text.indexOf(" by ", ignoreCase = true)
        val (rawTitle, rawArtist) = if (byIdx != -1) {
            text.substring(0, byIdx).trim() to text.substring(byIdx + 4).trim()
        } else {
            text to null
        }
        val title = rawTitle.removeSurrounding("\"").removeSurrounding("'").trim()
        if (title.isEmpty()) return null
        val artist = rawArtist?.removeSurrounding("\"")?.removeSurrounding("'")?.trim()?.takeIf { it.isNotEmpty() }
        val label = if (sequence != null) "${type.name} $sequence" else type.name
        return AnimeThemeSong(
            type = type,
            sequence = sequence,
            label = label,
            title = title,
            artist = artist,
            episodes = episodeNote,
        )
    }

    companion object {
        private val cache = ConcurrentHashMap<Long, List<AnimeThemeSong>>()
        private const val ANIME_THEMES_GRAPHQL_URL = "https://graphql.animethemes.moe/"
        private const val ANIME_THEMES_INCLUDE =
            "animethemes.song.artists,animethemes.animethemeentries.videos.audio"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val GRAPHQL_ANIME_THEMES_SELECTION = """
            id
            animethemes {
                id
                type
                sequence
                slug
                song {
                    title {
                        romaji
                        native
                    }
                    performances {
                        alias
                        artist {
                            name {
                                main
                                native
                            }
                        }
                    }
                }
                animethemeentries {
                    episodes
                    nsfw
                    spoiler
                    videos {
                        nodes {
                            link
                            resolution
                            nc
                            audio {
                                link
                            }
                        }
                    }
                }
            }
        """
    }
}

@Serializable
internal data class AnimeThemesGraphQlResponse(
    val data: AnimeThemesGraphQlDataDto? = null,
)

@Serializable
internal data class AnimeThemesGraphQlDataDto(
    val findAnimeByExternalSite: List<GraphQlAnimeDto>? = null,
    val animePagination: GraphQlAnimePaginationDto? = null,
)

@Serializable
internal data class GraphQlAnimePaginationDto(
    val data: List<GraphQlAnimeDto> = emptyList(),
)

@Serializable
internal data class GraphQlAnimeDto(
    val id: Long? = null,
    val animethemes: List<GraphQlThemeEntryDto> = emptyList(),
)

@Serializable
internal data class GraphQlThemeEntryDto(
    val id: Long? = null,
    val sequence: Int? = null,
    val slug: String? = null,
    val type: String? = null,
    val song: GraphQlSongDto? = null,
    val animethemeentries: List<GraphQlThemeVersionDto> = emptyList(),
)

@Serializable
internal data class GraphQlSongDto(
    val title: GraphQlSongTitleDto? = null,
    val performances: List<GraphQlPerformanceDto> = emptyList(),
)

@Serializable
internal data class GraphQlSongTitleDto(
    val romaji: String? = null,
    val native: String? = null,
)

@Serializable
internal data class GraphQlPerformanceDto(
    val alias: String? = null,
    val artist: GraphQlArtistDto? = null,
)

@Serializable
internal data class GraphQlArtistDto(
    val name: GraphQlArtistNameDto? = null,
)

@Serializable
internal data class GraphQlArtistNameDto(
    val main: String? = null,
    val native: String? = null,
)

@Serializable
internal data class GraphQlThemeVersionDto(
    val episodes: String? = null,
    val nsfw: Boolean? = null,
    val spoiler: Boolean? = null,
    val videos: GraphQlVideoConnectionDto? = null,
)

@Serializable
internal data class GraphQlVideoConnectionDto(
    val nodes: List<AnimeThemeVideoDto> = emptyList(),
)

@Serializable
internal data class AnimeThemesResponse(
    val anime: List<AnimeThemesAnimeDto> = emptyList(),
)

@Serializable
internal data class AnimeThemesAnimeDto(
    val id: Long? = null,
    val name: String? = null,
    val animethemes: List<AnimeThemeEntryDto> = emptyList(),
)

@Serializable
internal data class AnimeThemeEntryDto(
    val id: Long? = null,
    val sequence: Int? = null,
    val slug: String? = null,
    val type: String? = null,
    val song: AnimeThemeSongDto? = null,
    val animethemeentries: List<AnimeThemeVersionDto> = emptyList(),
)

@Serializable
internal data class AnimeThemeVersionDto(
    val id: Long? = null,
    val episodes: String? = null,
    val nsfw: Boolean? = null,
    val spoiler: Boolean? = null,
    val videos: List<AnimeThemeVideoDto> = emptyList(),
)

@Serializable
internal data class AnimeThemeVideoDto(
    val id: Long? = null,
    val link: String? = null,
    val resolution: Int? = null,
    val nc: Boolean? = null,
    val audio: AnimeThemeAudioDto? = null,
)

@Serializable
internal data class AnimeThemeAudioDto(
    val id: Long? = null,
    val link: String? = null,
)

@Serializable
internal data class AnimeThemeSongDto(
    val id: Long? = null,
    val title: String? = null,
    val artists: List<AnimeThemeArtistDto> = emptyList(),
)

@Serializable
internal data class AnimeThemeArtistDto(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
internal data class JikanThemesResponse(
    val data: JikanThemesDataDto? = null,
)

@Serializable
internal data class JikanThemesDataDto(
    val openings: List<String> = emptyList(),
    val endings: List<String> = emptyList(),
)
