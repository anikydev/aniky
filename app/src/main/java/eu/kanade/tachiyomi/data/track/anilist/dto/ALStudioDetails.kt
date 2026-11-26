package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALStudioDetailsResult(
    val data: ALStudioDetailsData,
)

@Serializable
data class ALStudioDetailsData(
    @SerialName("Studio")
    val studio: ALStudioDetails? = null,
)

@Serializable
data class ALStudioDetails(
    val id: Long = 0,
    val name: String = "",
    val isAnimationStudio: Boolean = false,
    val favourites: Int? = null,
    val siteUrl: String? = null,
    val media: ALStudioMediaConnection? = null,
)

@Serializable
data class ALStudioMediaConnection(
    val pageInfo: ALBrowsePageInfo = ALBrowsePageInfo(),
    val nodes: List<ALBrowseItem> = emptyList(),
)
