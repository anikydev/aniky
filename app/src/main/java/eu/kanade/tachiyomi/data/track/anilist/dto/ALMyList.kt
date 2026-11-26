package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Phase 0 foundation: full MediaList entry for the My Lists screen.
 * Unlike [ALUserListItem] (library sync only) this carries progress/score/status
 * plus list metadata needed for inline editing and sorting.
 */
@Serializable
data class ALMyListResult(
    val data: ALMyListData,
)

@Serializable
data class ALMyListData(
    @SerialName("Page")
    val page: ALMyListPage,
)

@Serializable
data class ALMyListPage(
    val mediaList: List<ALMyListEntry> = emptyList(),
    val pageInfo: ALPageInfo = ALPageInfo(),
)

@Serializable
data class ALMyListEntry(
    val id: Long = 0,
    val mediaId: Long = 0,
    val status: String = "",
    // AniList's MediaList.score is schema-typed Float even for POINT_100, so decode as Double: an Int
    // field would throw on a fractional value (coerceInputValues is off) and fail the whole page parse.
    @SerialName("scoreRaw") private val scoreRawValue: Double = 0.0,
    val progress: Int = 0,
    val repeat: Int = 0,
    val notes: String? = null,
    val updatedAt: Long = 0,
    val startedAt: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val completedAt: ALFuzzyDate = ALFuzzyDate(null, null, null),
    val media: ALSearchItem? = null,
) {
    /** Rounded 0-100 score used everywhere it's shown or sorted (wire type is Double; see above). */
    val scoreRaw: Int get() = scoreRawValue.roundToInt()
}

@Serializable
data class ALMediaListOptionsResult(
    val data: ALMediaListOptionsData,
)

@Serializable
data class ALMediaListOptionsData(
    @SerialName("Viewer")
    val viewer: ALMediaListOptionsViewer,
)

@Serializable
data class ALMediaListOptionsViewer(
    val mediaListOptions: ALMediaListOptions? = null,
)

@Serializable
data class ALMediaListOptions(
    val scoreFormat: String? = null,
    val rowOrder: String? = null,
    val animeList: ALAnimeListOptions? = null,
)

@Serializable
data class ALAnimeListOptions(
    val splitCompletedSectionByFormat: Boolean = false,
    val customLists: List<String> = emptyList(),
    val sectionOrder: List<String> = emptyList(),
)

/** AniList MediaListStatus values used across My Lists. */
object ALListStatus {
    const val CURRENT = "CURRENT"
    const val PLANNING = "PLANNING"
    const val COMPLETED = "COMPLETED"
    const val REPEATING = "REPEATING"
    const val PAUSED = "PAUSED"
    const val DROPPED = "DROPPED"

    val ALL = listOf(CURRENT, REPEATING, COMPLETED, PAUSED, DROPPED, PLANNING)
}
