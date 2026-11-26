@file:OptIn(kotlin.time.ExperimentalTime::class)

package mihon.feature.upcoming.anime

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.injectLazy
import kotlin.time.Clock

@Serializable
data class CachedAiringEntry(
    val remoteId: Long,
    val title: String,
    val coverUrl: String?,
    val nextEpisode: Int,
    val nextAiringAt: Long,
)

class UpcomingCalendarCache(
    preferenceStore: PreferenceStore,
) {
    private val json: Json by injectLazy()

    // "mine" bucket: the user's own AniList list.
    private val entriesPref: Preference<String> =
        preferenceStore.getString(Preference.appStateKey("anime_upcoming_anilist_cache"), "")
    private val fetchedAtPref: Preference<Long> =
        preferenceStore.getLong(Preference.appStateKey("anime_upcoming_anilist_cache_fetched_at"), 0L)

    // "all" bucket: the global airing schedule (all currently-airing anime).
    private val allEntriesPref: Preference<String> =
        preferenceStore.getString(Preference.appStateKey("anime_upcoming_all_cache"), "")
    private val allFetchedAtPref: Preference<Long> =
        preferenceStore.getLong(Preference.appStateKey("anime_upcoming_all_cache_fetched_at"), 0L)

    fun getEntries(): List<CachedAiringEntry> = decode(entriesPref)

    fun setEntries(entries: List<CachedAiringEntry>) = store(entriesPref, fetchedAtPref, entries)

    fun lastFetchedAt(): Long = fetchedAtPref.get()

    fun getAllEntries(): List<CachedAiringEntry> = decode(allEntriesPref)

    fun setAllEntries(entries: List<CachedAiringEntry>) = store(allEntriesPref, allFetchedAtPref, entries)

    fun allLastFetchedAt(): Long = allFetchedAtPref.get()

    private fun decode(pref: Preference<String>): List<CachedAiringEntry> {
        val raw = pref.get()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<CachedAiringEntry>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun store(
        entriesPref: Preference<String>,
        fetchedAtPref: Preference<Long>,
        entries: List<CachedAiringEntry>,
    ) {
        entriesPref.set(json.encodeToString(entries))
        fetchedAtPref.set(Clock.System.now().toEpochMilliseconds())
    }
}
