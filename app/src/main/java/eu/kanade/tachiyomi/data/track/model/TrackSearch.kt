@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.data.track.model

import eu.kanade.tachiyomi.data.database.models.Track

class TrackSearch : Track {

    override var id: Long? = null

    override var anime_id: Long = 0

    override var tracker_id: Long = 0

    override var remote_id: Long = 0

    override var library_id: Long? = null

    override lateinit var title: String

    var englishTitle: String? = null

    var romajiTitle: String? = null

    var nativeTitle: String? = null

    override var last_episode_seen: Double = 0.0

    override var total_episodes: Long = 0

    override var score: Double = -1.0

    override var status: Long = 0

    override var started_watching_date: Long = 0

    override var finished_watching_date: Long = 0

    override var private: Boolean = false

    override lateinit var tracking_url: String

    var authors: List<String> = emptyList()

    var artists: List<String> = emptyList()

    var cover_url: String = ""

    var summary: String = ""

    var publishing_status: String = ""

    var publishing_type: String = ""

    var start_date: String = ""

    var popularity: Long = 0

    var next_airing_episode: Long = 0

    var next_airing_at: Long = 0

    var genres: List<String> = emptyList()

    var season: String = ""

    var seasonYear: Int = 0

    var hasTrailer: Boolean = false

    var studio: String? = null

    var favourites: Long = 0

    var trackerStatus: String? = null

    var mediaListId: Long? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrackSearch

        if (anime_id != other.anime_id) return false
        if (tracker_id != other.tracker_id) return false
        if (remote_id != other.remote_id) return false
        if (status != other.status) return false
        if (last_episode_seen != other.last_episode_seen) return false
        if (score != other.score) return false
        if (trackerStatus != other.trackerStatus) return false
        if (mediaListId != other.mediaListId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = anime_id.hashCode()
        result = 31 * result + tracker_id.hashCode()
        result = 31 * result + remote_id.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + last_episode_seen.hashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + (trackerStatus?.hashCode() ?: 0)
        result = 31 * result + (mediaListId?.hashCode() ?: 0)
        return result
    }

    companion object {
        fun create(serviceId: Long): TrackSearch = TrackSearch().apply {
            tracker_id = serviceId
        }
    }

    var manga_id: Long = 0
        get() = anime_id
        set(value) {
            field = value
            anime_id = value
        }
}
