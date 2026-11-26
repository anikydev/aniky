package mihon.feature.upcoming.anime

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/** One day of the upcoming week and the episodes airing on it, in airing-time order. */
data class UpcomingDay(
    val date: LocalDate,
    val items: List<UpcomingAnimeItem>,
)

enum class CountdownKind { AIRED, AIRING_SOON, HOURS, DAYS }

@Immutable
data class UpcomingAnimeItem(
    val remoteId: Long,
    val title: String,
    val coverUrl: String?,
    val animeId: Long?,
    val nextEpisode: Int,
    val airingTime: String,
    val countdownKind: CountdownKind,
    val countdownValue: Long,
)
