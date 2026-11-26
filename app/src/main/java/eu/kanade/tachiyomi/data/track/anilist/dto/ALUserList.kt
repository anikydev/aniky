package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class ALUserListEntryQueryResult(
    val data: ALUserListEntryPage,
)

@Serializable
data class ALUserListEntryPage(
    @SerialName("Page")
    val page: ALUserListMediaList,
)

@Serializable
data class ALUserListMediaList(
    val mediaList: List<ALUserListItem>,
)

@Serializable
data class ALUserListItem(
    val id: Long,
    val status: String,
    // AniList's MediaList.score is schema-typed Float even for POINT_100, so decode as Double: under
    // the app's Json (coerceInputValues off) an Int field would throw on a fractional value and fail
    // the whole findLibAnime page parse, breaking bind/refresh/update for every tracked anime.
    @SerialName("scoreRaw") private val scoreRawValue: Double = 0.0,
    val progress: Int,
    // Nullable with defaults: under the app's Json (coerceInputValues off) a single deleted/private
    // media — or a bare-null date — would otherwise fail the entire findLibAnime page parse, breaking
    // bind/refresh/update for every tracked anime instead of just dropping this one entry.
    val startedAt: ALFuzzyDate? = null,
    val completedAt: ALFuzzyDate? = null,
    val media: ALSearchItem? = null,
    val private: Boolean = false,
) {
    /** Rounded 0-100 score the callers expect (see [scoreRawValue] for why the wire type is Double). */
    val scoreRaw: Int get() = scoreRawValue.roundToInt()

    fun toALUserAnime(): ALUserAnime? {
        val media = media ?: return null
        return ALUserAnime(
            libraryId = this@ALUserListItem.id,
            listStatus = status,
            scoreRaw = scoreRaw,
            episodesSeen = progress,
            startDateFuzzy = startedAt?.toEpochMilli() ?: 0L,
            completedDateFuzzy = completedAt?.toEpochMilli() ?: 0L,
            anime = media.toALAnime(),
            private = private,
        )
    }
}
