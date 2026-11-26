package eu.kanade.domain.ui.model

/**
 * Which AniList title to show across the AniList client, mirroring AniList's own (and AL-chan's)
 * title-language setting. [pick] falls back through the other titles so a missing field never
 * leaves a blank entry.
 */
enum class AnilistTitleLanguage {
    ROMAJI,
    ENGLISH,
    NATIVE,
    ;

    fun pick(
        romaji: String?,
        english: String?,
        native: String?,
        userPreferred: String? = null,
    ): String {
        val ordered = when (this) {
            ROMAJI -> listOf(romaji, userPreferred, english, native)
            ENGLISH -> listOf(english, userPreferred, romaji, native)
            NATIVE -> listOf(native, userPreferred, romaji, english)
        }
        return ordered.firstOrNull { !it.isNullOrBlank() }.orEmpty()
    }
}
