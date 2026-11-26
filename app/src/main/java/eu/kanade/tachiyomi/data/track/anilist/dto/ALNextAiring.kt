package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Envelope for the lightweight `Media { nextAiringEpisode }` query used by the airing countdown. */
@Serializable
data class ALNextAiringResult(
    val data: ALNextAiringData,
)

@Serializable
data class ALNextAiringData(
    @SerialName("Media")
    val media: ALNextAiringMedia? = null,
)

@Serializable
data class ALNextAiringMedia(
    val nextAiringEpisode: ALNextAiringEpisode? = null,
)
