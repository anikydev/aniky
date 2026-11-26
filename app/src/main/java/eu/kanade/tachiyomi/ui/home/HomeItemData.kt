package eu.kanade.tachiyomi.ui.home

/**
 * Modelo de item para representar animes, películas y series en la Home.
 */
data class HomeItemData(
    val id: Long,
    val inLibrary: Boolean = false,
    val title: String,
    val subtitle: String,
    val coverUrl: String? = null,
    val coverData: Any? = null,
    val rating: String = "",
    val progress: Float = 0f,
    val popularity: Long = 0,
    val publishingStatus: String = "",
    val format: String = "",
    val nextAiringEpisode: Long = 0,
    val nextAiringAt: Long = 0,
    val genres: List<String> = emptyList(),
    val hasTrailer: Boolean = false,
    val studio: String? = null,
    val favourites: Long = 0,
    val trackerStatus: String? = null,
    val mediaListId: Long? = null,
)
