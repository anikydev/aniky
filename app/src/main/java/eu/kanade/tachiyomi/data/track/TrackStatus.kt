package eu.kanade.tachiyomi.data.track

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.sy.SYMR

enum class TrackStatus(val int: Int, val res: StringResource) {
    WATCHING(11, AYMR.strings.watching),
    REWATCHING(17, AYMR.strings.repeating_anime),
    PLAN_TO_WATCH(16, AYMR.strings.plan_to_watch),
    PAUSED(4, MR.strings.on_hold),
    COMPLETED(5, MR.strings.completed),
    DROPPED(6, MR.strings.dropped),
    OTHER(7, SYMR.strings.not_tracked),
    ;

    companion object {
        fun parseTrackerStatus(trackerManager: TrackerManager, tracker: Long, status: Long): TrackStatus? {
            return when (tracker) {
                trackerManager.myAnimeList.id -> {
                    when (status) {
                        MyAnimeList.WATCHING -> WATCHING
                        MyAnimeList.COMPLETED -> COMPLETED
                        MyAnimeList.ON_HOLD -> PAUSED
                        MyAnimeList.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        MyAnimeList.DROPPED -> DROPPED
                        MyAnimeList.REWATCHING -> REWATCHING
                        else -> null
                    }
                }
                trackerManager.aniList.id -> {
                    when (status) {
                        Anilist.WATCHING -> WATCHING
                        Anilist.COMPLETED -> COMPLETED
                        Anilist.ON_HOLD -> PAUSED
                        Anilist.PLAN_TO_WATCH -> PLAN_TO_WATCH
                        Anilist.DROPPED -> DROPPED
                        Anilist.REWATCHING -> REWATCHING
                        else -> null
                    }
                }
                else -> null
            }
        }
    }
}
