package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class ALFuzzyDate(
    val year: Int?,
    val month: Int?,
    val day: Int?,
) {
    fun toEpochMilli(): Long {
        // AniList fuzzy dates are frequently partial (year-only is common). Fill a missing
        // month/day with the start of the period instead of NPEing on year!!/month!!/day!! and
        // silently collapsing the whole date to 0L ("no date").
        val year = year ?: return 0L
        return try {
            LocalDate.of(year, month ?: 1, day ?: 1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
