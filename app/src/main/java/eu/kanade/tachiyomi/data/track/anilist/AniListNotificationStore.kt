package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Local state for the AniList notification feed.
 *
 * AniList exposes no per-notification read flag, so read state lives here: an entry is
 * unread until the user taps it, and viewing the feed or pulling to refresh never clears
 * anything. The last fetched feed is cached so opening the screen shows the previous
 * notifications instantly; the network is only hit on pull-to-refresh.
 */
class AniListNotificationStore(
    preferenceStore: PreferenceStore = Injekt.get(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val cache = preferenceStore.getString(CACHE_KEY, "")
    private val readIdsJson = preferenceStore.getString(READ_IDS_KEY, "[]")
    private val seeded = preferenceStore.getBoolean(SEED_KEY, false)

    /** Flow of the unread count, derived from the cached feed vs the local read-id set. */
    val unreadCount: Flow<Int> = combine(
        cache.changes(),
        readIdsJson.changes(),
    ) { feedJson, readIdsJson ->
        countUnread(decodeFeed(feedJson), decodeReadIds(readIdsJson))
    }

    /** Synchronous version of [unreadCount] for callers/tests that already hold the prefs. */
    fun getUnreadCount(): Int = countUnread(getCached(), getReadIds())

    /** The previously fetched feed, or empty if never fetched / unreadable. */
    fun getCached(): List<ALNotification> = decodeFeed(cache.get())

    fun saveCache(notifications: List<ALNotification>) {
        cache.set(json.encodeToString(notifications))
    }

    /** Ids the user has tapped. Anything not in here is still unread. */
    fun getReadIds(): Set<Long> = decodeReadIds(readIdsJson.get())

    fun markRead(id: Long) = synchronized(readIdsLock) {
        // Plus on a set preserves insertion order, so takeLast drops the stalest markers first.
        val updated = (getReadIds() + id).toList().takeLast(MAX_READ_IDS)
        readIdsJson.set(json.encodeToString(updated))
    }

    /**
     * Whether the one-time server-count seed has run. The persisted [seeded] flag is the sole
     * source of truth: also counting a non-empty read set let a single tap during the first
     * refresh flip this to true before the seed ran, permanently suppressing it. backfillReadIds
     * unions with any existing markers, so a legacy install with pre-existing taps seeds once and
     * keeps those taps.
     */
    fun isSeeded(): Boolean = seeded.get()

    /**
     * One-time backfill from AniList's own unread counter.
     *
     * Notifications come newest-first, so the first [serverUnreadCount] are the ones AniList
     * itself still considers unread; everything older was already read elsewhere and is
     * seeded as read locally. Runs only once — afterwards read state changes exclusively
     * via [markRead]/[markAllRead]. A negative count means the counter query failed, in
     * which case nothing is seeded and the next open retries.
     */
    fun backfillReadIds(notifications: List<ALNotification>, serverUnreadCount: Int) = synchronized(readIdsLock) {
        if (serverUnreadCount < 0 || isSeeded()) return@synchronized
        val updated = (
            getReadIds() +
                notifications.drop(serverUnreadCount).mapNotNull { it.id }
            ).toList().takeLast(MAX_READ_IDS)
        readIdsJson.set(json.encodeToString(updated))
        seeded.set(true)
    }

    /** Marks every id in [ids] read (null ids ignored). Used for a future mark-all-read action. */
    fun markAllRead(ids: List<Long?>) = synchronized(readIdsLock) {
        val updated = (getReadIds() + ids.filterNotNull()).toList().takeLast(MAX_READ_IDS)
        readIdsJson.set(json.encodeToString(updated))
    }

    /** Drops the cached feed and all read markers, e.g. on tracker logout / account switch. */
    fun clear() = synchronized(readIdsLock) {
        cache.delete()
        readIdsJson.delete()
        seeded.delete()
    }

    private fun decodeFeed(feedJson: String): List<ALNotification> {
        if (feedJson.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<ALNotification>>(feedJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeReadIds(readIdsJson: String): Set<Long> {
        return try {
            json.decodeFromString<List<Long>>(readIdsJson).toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun countUnread(feed: List<ALNotification>, readIds: Set<Long>): Int {
        return feed.count { it.id != null && it.id !in readIds }
    }

    companion object {
        // Serializes the get -> modify -> set of the read-id set across ALL store instances: the
        // store is instantiated ad hoc at several call sites (logout, the tab, the notifications
        // screen) over the same PreferenceStore keys, so a per-instance monitor gave no mutual
        // exclusion. A tap (markRead), a refresh (backfillReadIds/markAllRead) and a logout (clear)
        // on different threads now can't clobber each other.
        private val readIdsLock = Any()

        internal const val CACHE_KEY = "anilist_notifications_cache"
        internal const val READ_IDS_KEY = "anilist_notifications_read_ids"
        internal const val SEED_KEY = "anilist_notifications_seeded"

        // Read markers only matter while the notification can still reappear in the
        // 50-item feed window; cap the set so it can't grow without bound.
        const val MAX_READ_IDS = 500
    }
}
