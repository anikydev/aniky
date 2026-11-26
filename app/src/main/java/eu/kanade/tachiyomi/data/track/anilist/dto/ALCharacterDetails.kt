package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALCharacterDetailsResult(
    val data: ALCharacterDetailsData,
)

@Serializable
data class ALCharacterDetailsData(
    @SerialName("Character")
    val character: ALCharacterDetails,
)

@Serializable
data class ALCharacterDetails(
    val id: Long,
    val name: ALCharacterName = ALCharacterName(),
    // Reuses the anime-details character image shape (just `large`).
    val image: ALAnimeDetailsCharacterImage? = null,
    val description: String? = null,
    val gender: String? = null,
    // AniList returns age as a string ("17", "17-19", …), not a number.
    val age: String? = null,
    val dateOfBirth: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val bloodType: String? = null,
    val favourites: Int? = null,
    val media: ALCharacterMedia? = null,
)

@Serializable
data class ALCharacterName(
    val userPreferred: String? = null,
    val native: String? = null,
    val alternative: List<String> = emptyList(),
)

@Serializable
data class ALCharacterMedia(
    val edges: List<ALCharacterMediaEdge> = emptyList(),
)

@Serializable
data class ALCharacterMediaEdge(
    val characterRole: String? = null,
    val node: ALCharacterMediaNode,
    val voiceActors: List<ALStaffNodeLite> = emptyList(),
)

@Serializable
data class ALCharacterMediaNode(
    val id: Long,
    val type: String? = null,
    val title: ALItemTitle,
    val coverImage: ALAnimeDetailsCover? = null,
    val averageScore: Int? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val isAdult: Boolean? = null,
)
