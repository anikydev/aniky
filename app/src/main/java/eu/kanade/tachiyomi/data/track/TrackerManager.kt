package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import kotlinx.coroutines.flow.combine

class TrackerManager {

    companion object {
        const val MYANIMELIST = 1L
        const val ANILIST = 2L
    }

    val myAnimeList = MyAnimeList(MYANIMELIST)
    val aniList = Anilist(ANILIST)

    val trackers: List<BaseTracker> = listOf(
        myAnimeList,
        aniList,
    )

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }
}
