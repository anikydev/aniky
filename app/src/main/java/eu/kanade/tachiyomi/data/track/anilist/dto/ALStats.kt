package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALStatsResult(
    val data: ALStatsData,
)

@Serializable
data class ALStatsData(
    @SerialName("Viewer")
    val viewer: ALStatsViewer,
)

@Serializable
data class ALUserAvatar(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class ALStatsViewer(
    val id: Int? = null,
    val name: String? = null,
    val avatar: ALUserAvatar? = null,
    val statistics: ALStatistics,
    val mediaListOptions: ALUserListOptions? = null,
)

@Serializable
data class ALStatistics(
    val anime: ALAnimeStats = ALAnimeStats(),
)

@Serializable
data class ALAnimeStats(
    val count: Int? = null,
    val meanScore: Double? = null,
    val standardDeviation: Double? = null,
    val minutesWatched: Int? = null,
    val episodesWatched: Int? = null,
    val statuses: List<ALStatsStatus> = emptyList(),
    val formats: List<ALStatsFormat> = emptyList(),
    val genres: List<ALStatsGenre> = emptyList(),
    val studios: List<ALStatsStudio> = emptyList(),
    val scores: List<ALStatsScore> = emptyList(),
    val lengths: List<ALStatsLength> = emptyList(),
    val releaseYears: List<ALStatsYear> = emptyList(),
    val startYears: List<ALStatsYear> = emptyList(),
    val countries: List<ALStatsCountry> = emptyList(),
    val tags: List<ALStatsTag> = emptyList(),
    val voiceActors: List<ALStatsVoiceActor> = emptyList(),
    val staff: List<ALStatsStaff> = emptyList(),
    // Not part of the statistics payload: copied from the viewer's mediaListOptions because
    // AniList reports [scores] in the viewer's own score format, not on a fixed 0-100 scale.
    val scoreFormat: String? = null,
    val userName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class ALStatsStatus(
    val status: String? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsFormat(
    val format: String? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsGenre(
    val genre: String? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsStudio(
    val studio: ALStatsStudioName? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsStudioName(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
data class ALStatsScore(
    val score: Double? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsLength(
    val length: String? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsYear(
    @SerialName("releaseYear")
    val releaseYear: Int? = null,
    @SerialName("startYear")
    val startYear: Int? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsCountry(
    val country: String? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsTag(
    val tag: ALStatsTagName? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsTagName(
    val name: String? = null,
)

@Serializable
data class ALStatsVoiceActor(
    val voiceActor: ALStatsPerson? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsStaff(
    val staff: ALStatsPerson? = null,
    val count: Int? = null,
)

@Serializable
data class ALStatsPerson(
    val name: ALStatsPersonName? = null,
    val image: ALStatsImage? = null,
)

@Serializable
data class ALStatsPersonName(
    val full: String? = null,
)

@Serializable
data class ALStatsImage(
    val large: String? = null,
)
