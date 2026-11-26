package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALCurrentUserResult(
    val data: ALUserViewer,
)

@Serializable
data class ALUserViewer(
    @SerialName("Viewer")
    val viewer: ALUserViewerData,
)

@Serializable
data class ALUserViewerData(
    val id: Int,
    // Nullable with a default: AniList's Viewer.mediaListOptions can come back null/absent, and a
    // non-null field here would fail the whole Viewer parse - and thus login / resolveUserId /
    // getFavouriteAnime - on a null. Matches ALMediaListOptionsViewer.mediaListOptions.
    val mediaListOptions: ALUserListOptions? = null,
)

@Serializable
data class ALUserListOptions(
    // Nullable: AniList's mediaListOptions.scoreFormat is schema-nullable, and a non-null field
    // here would fail the whole Viewer parse (and thus login) on a null. Matches ALStats/ALMyList.
    val scoreFormat: String? = null,
)
