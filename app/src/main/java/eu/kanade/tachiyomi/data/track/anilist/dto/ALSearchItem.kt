package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALSearchItem(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
    val description: String? = null,
    val format: String? = null,
    val status: String? = null,
    val isAdult: Boolean? = null,
    val startDate: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val episodes: Long? = null,
    val averageScore: Int? = null,
    val popularity: Long? = null,
    val nextAiringEpisode: ALNextAiringEpisode? = null,
    val genres: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val trailer: ALTrailer? = null,
    val favourites: Long? = null,
    val studios: ALStudios? = null,
    val mediaListEntry: ALMediaListEntryLite? = null,
) {
    fun toALAnime(): ALAnime = ALAnime(
        remoteId = id,
        title = title.userPreferred.orEmpty(),
        imageUrl = coverImage.large ?: "",
        description = description,
        format = format?.replace("_", "-") ?: "",
        publishingStatus = status ?: "",
        startDateFuzzy = startDate.toEpochMilli(),
        totalEpisodes = episodes ?: 0,
        averageScore = averageScore ?: 0,
    )
}

@Serializable
data class ALItemTitle(
    // Nullable: AniList can return title.userPreferred as null, which as a non-null field would
    // fail the whole page parse (MissingFieldException) rather than just this one entry - the same
    // reason coverImage.large is nullable.
    val userPreferred: String? = null,
    val english: String? = null,
    val romaji: String? = null,
    val native: String? = null,
)

@Serializable
data class ItemCover(
    // Nullable: AniList can return coverImage.large as null, which would otherwise fail the whole
    // parse (MissingFieldException) and take down an entire list load, not just one entry.
    val large: String? = null,
)

@Serializable
data class ALNextAiringEpisode(
    val episode: Long? = null,
    val airingAt: Long? = null,
)

@Serializable
data class ALTrailer(
    val id: String? = null,
    val site: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class ALStudios(
    val edges: List<ALStudiosEdge> = emptyList(),
)

@Serializable
data class ALStudiosEdge(
    val isMain: Boolean = false,
    val node: ALStudiosNode = ALStudiosNode(),
)

@Serializable
data class ALStudiosNode(
    val id: Long = 0,
    val name: String = "",
)
