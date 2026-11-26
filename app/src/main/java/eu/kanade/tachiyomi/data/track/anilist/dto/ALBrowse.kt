package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phase 0 foundation: browse/discover, favourites, staff and extended detail DTOs.
 * All queries are anime-only (the app is anime-only) and login-gated via authClient.
 */

@Serializable
data class ALBrowseResult(
    val data: ALBrowseData,
)

@Serializable
data class ALBrowseData(
    @SerialName("Page")
    val page: ALBrowsePage,
)

@Serializable
data class ALBrowsePage(
    val media: List<ALBrowseItem> = emptyList(),
    val pageInfo: ALBrowsePageInfo = ALBrowsePageInfo(),
)

@Serializable
data class ALBrowsePageInfo(
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
)

@Serializable
data class ALBrowseItem(
    val id: Long,
    val type: String? = null,
    val title: ALBrowseTitle,
    val coverImage: ALBrowseCover,
    val bannerImage: String? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val genres: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val startDate: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val nextAiringEpisode: ALNextAiringEpisode? = null,
    val trailer: ALTrailer? = null,
    val isAdult: Boolean? = null,
    val mediaListEntry: ALMediaListEntryLite? = null,
)

@Serializable
data class ALBrowseTitle(
    // Nullable: AniList can return title.userPreferred as null, which as a non-null field would
    // fail the whole page parse (MissingFieldException) rather than just this one entry - the same
    // reason coverImage.large is nullable.
    val userPreferred: String? = null,
    val english: String? = null,
    val romaji: String? = null,
    val native: String? = null,
)

@Serializable
data class ALBrowseCover(
    val large: String? = null,
    val extraLarge: String? = null,
    val medium: String? = null,
)

enum class ALBrowseSort(val api: String) {
    TRENDING("TRENDING_DESC"),
    POPULARITY("POPULARITY_DESC"),
    SCORE("SCORE_DESC"),
    FAVOURITES("FAVOURITES_DESC"),
    START_DATE("START_DATE_DESC"),
    UPDATED("UPDATED_AT_DESC"),
}

@Serializable
data class ALGenreCollectionResult(
    val data: ALGenreCollectionData,
)

@Serializable
data class ALGenreCollectionData(
    @SerialName("GenreCollection")
    val genres: List<String> = emptyList(),
)

@Serializable
data class ALTagCollectionResult(
    val data: ALTagCollectionData,
)

@Serializable
data class ALTagCollectionData(
    @SerialName("MediaTagCollection")
    val tags: List<ALMediaTag> = emptyList(),
)

@Serializable
data class ALMediaTag(
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val isAdult: Boolean = false,
)

// --- Extended detail: staff / recommendations / tags / favourites ---

@Serializable
data class ALStaffName(
    val full: String? = null,
    val userPreferred: String? = null,
    val native: String? = null,
)

@Serializable
data class ALStaffImage(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class ALStaffDetailsResult(
    val data: ALStaffDetailsData,
)

@Serializable
data class ALStaffDetailsData(
    @SerialName("Staff")
    val staff: ALStaffDetails? = null,
)

@Serializable
data class ALStaffDetails(
    val id: Long,
    val name: ALStaffName? = null,
    val image: ALStaffImage? = null,
    val description: String? = null,
    val primaryOccupations: List<String> = emptyList(),
    val languageV2: String? = null,
    val gender: String? = null,
    val dateOfBirth: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val age: Int? = null,
    val favourites: Int? = null,
    val staffMedia: ALStaffMediaConnection? = null,
    val characterMedia: ALStaffMediaConnection? = null,
)

@Serializable
data class ALStaffMediaConnection(
    val edges: List<ALStaffMediaEdge> = emptyList(),
)

@Serializable
data class ALStaffMediaEdge(
    val staffRole: String? = null,
    val characterRole: String? = null,
    val node: ALBrowseItem? = null,
    val characters: List<ALStaffCharacterNode> = emptyList(),
)

@Serializable
data class ALStaffCharacterNode(
    val id: Long = 0,
    val name: ALCharacterNameLite? = null,
    val image: ALCharacterImageLite? = null,
)

@Serializable
data class ALCharacterNameLite(
    val full: String? = null,
    val userPreferred: String? = null,
)

@Serializable
data class ALCharacterImageLite(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class ALFavouritesResult(
    val data: ALFavouritesData,
)

@Serializable
data class ALFavouritesData(
    // AniList's Viewer.favourites connection returns empty/stale results; favourites are read via
    // User(id: <viewer id>) instead, so the payload is keyed "User".
    @SerialName("User")
    val user: ALFavouritesUser? = null,
)

@Serializable
data class ALFavouritesUser(
    val favourites: ALFavouritesCollection? = null,
)

@Serializable
data class ALFavouritesCollection(
    val anime: ALFavouritesConnection? = null,
)

@Serializable
data class ALFavouritesConnection(
    val nodes: List<ALBrowseItem> = emptyList(),
    val pageInfo: ALBrowsePageInfo = ALBrowsePageInfo(),
)
