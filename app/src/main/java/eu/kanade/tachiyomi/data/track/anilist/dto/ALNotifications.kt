package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALNotificationsResult(
    val data: ALNotificationsData,
)

@Serializable
data class ALNotificationsData(
    @SerialName("Page")
    val page: ALNotificationsPage,
)

@Serializable
data class ALNotificationsPage(
    val notifications: List<ALNotification> = emptyList(),
)

@Serializable
data class ALUnreadCountResult(
    val data: ALUnreadCountData,
)

@Serializable
data class ALUnreadCountData(
    @SerialName("Viewer")
    val viewer: ALUnreadCountViewer? = null,
)

@Serializable
data class ALUnreadCountViewer(
    val unreadNotificationCount: Int = 0,
)

/**
 * One AniList notification. AniList's `NotificationUnion` has no shared fields, so the query asks for
 * every variant through inline fragments and this single flat model holds the union of them, keyed by
 * [type]. The injected [kotlinx.serialization.json.Json] is lenient, so fields absent for a given
 * variant deserialize to null.
 */
@Serializable
data class ALNotification(
    val id: Long? = null,
    val type: String? = null,
    // Epoch seconds.
    val createdAt: Long? = null,
    // AiringNotification: the episode that aired.
    val episode: Int? = null,
    // AiringNotification: ["Episode ", " of ", " aired."] fragments AniList pre-splits for styling.
    val contexts: List<String>? = null,
    // Single-context variants (following, activity, replies): the human-readable sentence fragment.
    val context: String? = null,
    // The related media (airing / related-media-addition notifications).
    val media: ALNotificationMedia? = null,
    // The other user (following / activity / reply notifications).
    val user: ALNotificationUser? = null,
)

@Serializable
data class ALNotificationMedia(
    val id: Long? = null,
    val title: ALNotificationTitle? = null,
    val coverImage: ALNotificationCover? = null,
)

@Serializable
data class ALNotificationTitle(
    val userPreferred: String? = null,
)

@Serializable
data class ALNotificationCover(
    val large: String? = null,
)

@Serializable
data class ALNotificationUser(
    val name: String? = null,
    val avatar: ALNotificationAvatar? = null,
)

@Serializable
data class ALNotificationAvatar(
    val large: String? = null,
)
