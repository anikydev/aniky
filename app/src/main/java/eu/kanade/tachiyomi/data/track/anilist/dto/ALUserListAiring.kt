package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALUserListAiringResult(
    val data: ALUserListAiringPage,
)

@Serializable
data class ALUserListAiringPage(
    @SerialName("Page")
    val page: ALUserListAiringMediaList,
)

@Serializable
data class ALUserListAiringMediaList(
    val mediaList: List<ALUserListAiringItem>,
    val pageInfo: ALPageInfo = ALPageInfo(),
)

@Serializable
data class ALUserListAiringItem(
    val progress: Int? = null,
    val scoreRaw: Double? = null,
    // Nullable: a deleted/private list entry can come back as "media": null, which would otherwise
    // fail the whole page parse under the app's Json (coerceInputValues off). Callers skip nulls.
    val media: ALSearchItem? = null,
)

@Serializable
data class ALAiringScheduleResult(
    val data: ALAiringSchedulePage,
)

@Serializable
data class ALAiringSchedulePage(
    @SerialName("Page")
    val page: ALAiringScheduleList,
)

@Serializable
data class ALAiringScheduleList(
    @SerialName("airingSchedules")
    val airingSchedule: List<ALAiringScheduleItem>,
    val pageInfo: ALPageInfo = ALPageInfo(),
)

@Serializable
data class ALAiringScheduleItem(
    val airingAt: Long = 0,
    val episode: Int = 0,
    // Nullable for the same reason as ALUserListAiringItem.media: one null entry must not fail the
    // whole page parse. Callers skip nulls.
    val media: ALSearchItem? = null,
)
