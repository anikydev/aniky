package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALAnimeDetailsResult(
    val data: ALAnimeDetailsData,
)

@Serializable
data class ALAnimeDetailsData(
    @SerialName("Media")
    val media: ALAnimeDetails,
)

@Serializable
data class ALMediaIdResult(
    val data: ALMediaIdData,
)

@Serializable
data class ALMediaIdData(
    @SerialName("Media")
    val media: ALMediaId,
)

@Serializable
data class ALMediaId(
    val id: Long,
)

@Serializable
data class ALAnimeDetails(
    val id: Long,
    val idMal: Long? = null,
    val title: ALItemTitle,
    val coverImage: ALAnimeDetailsCover,
    val bannerImage: String? = null,
    val description: String? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Long? = null,
    val duration: Long? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val genres: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val studios: ALStudios? = null,
    val startDate: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val endDate: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val nextAiringEpisode: ALNextAiringEpisode? = null,
    val trailer: ALTrailer? = null,
    val relations: ALAnimeDetailsRelations? = null,
    val characters: ALAnimeDetailsCharacters? = null,
    val staff: ALStaffConnection? = null,
    val recommendations: ALRecommendationsConnection? = null,
    val tags: List<ALDetailTagLite> = emptyList(),
    val isFavourite: Boolean = false,
    val mediaListEntry: ALMediaListEntryLite? = null,
)

@Serializable
data class ALAnimeDetailsRelations(
    val edges: List<ALAnimeDetailsRelationEdge> = emptyList(),
)

@Serializable
data class ALAnimeDetailsRelationEdge(
    val relationType: String? = null,
    val node: ALAnimeDetailsRelationNode,
)

@Serializable
data class ALAnimeDetailsRelationNode(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ALAnimeDetailsCover? = null,
    val format: String? = null,
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val mediaListEntry: ALMediaListEntryLite? = null,
)

@Serializable
data class ALAnimeDetailsCover(
    val extraLarge: String? = null,
    val large: String? = null,
)

@Serializable
data class ALAnimeDetailsCharacters(
    val edges: List<ALAnimeDetailsCharacterEdge>,
)

@Serializable
data class ALAnimeDetailsCharacterEdge(
    val role: String? = null,
    val node: ALAnimeDetailsCharacterNode,
    val voiceActors: List<ALStaffNodeLite> = emptyList(),
)

@Serializable
data class ALAnimeDetailsCharacterNode(
    val id: Long,
    val name: ALItemTitle,
    val image: ALAnimeDetailsCharacterImage? = null,
)

@Serializable
data class ALAnimeDetailsCharacterImage(
    val large: String? = null,
)

@Serializable
data class ALStaffConnection(
    val edges: List<ALStaffEdgeLite> = emptyList(),
)

@Serializable
data class ALStaffEdgeLite(
    val role: String? = null,
    val node: ALStaffNodeLite? = null,
)

@Serializable
data class ALStaffNodeLite(
    val id: Long = 0,
    val name: ALStaffNameLite? = null,
    val image: ALStaffImageLite? = null,
    val languageV2: String? = null,
)

@Serializable
data class ALStaffNameLite(
    val full: String? = null,
    val userPreferred: String? = null,
)

@Serializable
data class ALStaffImageLite(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class ALRecommendationsConnection(
    val nodes: List<ALRecommendationNodeLite> = emptyList(),
)

@Serializable
data class ALRecommendationNodeLite(
    val id: Long = 0,
    val rating: Int? = null,
    val mediaRecommendation: ALRecommendationMedia? = null,
)

@Serializable
data class ALRecommendationMedia(
    val id: Long = 0,
    val title: ALItemTitle? = null,
    val coverImage: ALAnimeDetailsCover? = null,
    val averageScore: Int? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val mediaListEntry: ALMediaListEntryLite? = null,
)

@Serializable
data class ALDetailTagLite(
    val name: String = "",
    val rank: Int? = null,
    val isMediaSpoiler: Boolean = false,
)

@Serializable
data class ALMediaListEntryLite(
    val id: Long = 0,
    val status: String? = null,
    val score: Double? = null,
    val progress: Int? = null,
    val repeat: Int? = null,
    val notes: String? = null,
    val startedAt: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val completedAt: ALFuzzyDate = ALFuzzyDate(null, null, null),
)
