package eu.kanade.tachiyomi.ui.browse.seasonal.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeType {
    OP,
    ED,
}

@Serializable
data class AnimeThemeSong(
    val type: ThemeType,
    val sequence: Int? = null,
    val label: String,
    val title: String,
    val artist: String? = null,
    val episodes: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
) {
    val hasPreview: Boolean
        get() = !videoUrl.isNullOrBlank() || !audioUrl.isNullOrBlank()
}
