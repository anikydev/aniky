package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotification
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotificationCover
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotificationMedia
import eu.kanade.tachiyomi.data.track.anilist.dto.ALNotificationTitle
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * Pins the local notification state: the cached feed round-trips, corrupt data degrades
 * to empty instead of throwing, and read markers accumulate per tap with a cap.
 */
@Execution(ExecutionMode.CONCURRENT)
class AniListNotificationStoreTest {

    private fun store() = AniListNotificationStore(InMemoryPreferenceStore())

    private fun notification(id: Long) = ALNotification(
        id = id,
        type = "AIRING",
        createdAt = 1_700_000_000L,
        episode = 5,
        contexts = listOf("Episode ", " of ", " aired."),
        media = ALNotificationMedia(
            id = 21,
            title = ALNotificationTitle("Example"),
            coverImage = ALNotificationCover("https://cdn/x.jpg"),
        ),
    )

    @Test
    fun `cache and read ids start empty`() {
        val store = store()

        store.getCached() shouldBe emptyList()
        store.getReadIds() shouldBe emptySet()
        store.isSeeded() shouldBe false
    }

    @Test
    fun `saved feed round-trips`() {
        val store = store()
        val feed = listOf(notification(1), notification(2))

        store.saveCache(feed)

        store.getCached() shouldBe feed
    }

    @Test
    fun `corrupt cache degrades to empty`() {
        val prefs = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    AniListNotificationStore.CACHE_KEY,
                    "!!! not json !!!",
                    "",
                ),
            ),
        )

        AniListNotificationStore(prefs).getCached() shouldBe emptyList()
    }

    @Test
    fun `markRead records ids and is idempotent`() {
        val store = store()

        store.markRead(7)
        store.markRead(7)
        store.markRead(9)

        store.getReadIds() shouldBe setOf(7L, 9L)
    }

    @Test
    fun `read ids are capped, newest markers kept`() {
        val store = store()

        (1L..(AniListNotificationStore.MAX_READ_IDS + 10L)).forEach { store.markRead(it) }

        val readIds = store.getReadIds()
        readIds.size shouldBe AniListNotificationStore.MAX_READ_IDS
        readIds shouldContain (AniListNotificationStore.MAX_READ_IDS + 10L)
        readIds shouldNotContain 1L
        readIds shouldNotContain 10L
    }

    @Test
    fun `corrupt read ids degrade to empty`() {
        val prefs = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    AniListNotificationStore.READ_IDS_KEY,
                    "!!! not json !!!",
                    "[]",
                ),
            ),
        )

        AniListNotificationStore(prefs).getReadIds() shouldBe emptySet()
    }

    @Test
    fun `backfill seeds everything older than the server unread window`() {
        val store = store()
        // Newest-first: server still considers the first one unread.
        val feed = listOf(notification(3), notification(2), notification(1))

        store.backfillReadIds(feed, serverUnreadCount = 1)

        store.getReadIds() shouldBe setOf(2L, 1L)
    }

    @Test
    fun `backfill with zero server unread marks everything read`() {
        val store = store()

        store.backfillReadIds(listOf(notification(2), notification(1)), serverUnreadCount = 0)

        store.getReadIds() shouldBe setOf(2L, 1L)
    }

    @Test
    fun `a tap marker does not suppress the one-time backfill`() {
        val store = store()
        store.markRead(1)

        // The seed is gated on the persisted flag, not the read set, so a tap before the first
        // backfill no longer permanently suppresses it; the backfill unions with the tap marker.
        store.backfillReadIds(listOf(notification(3), notification(2)), serverUnreadCount = 0)
        store.getReadIds() shouldBe setOf(1L, 3L, 2L)

        // ...and it still runs only once.
        store.backfillReadIds(listOf(notification(4)), serverUnreadCount = 0)
        store.getReadIds() shouldBe setOf(1L, 3L, 2L)
    }

    @Test
    fun `backfill is a no-op when the counter query failed`() {
        val store = store()

        store.backfillReadIds(listOf(notification(2), notification(1)), serverUnreadCount = -1)

        store.getReadIds() shouldBe emptySet()
        store.isSeeded() shouldBe false
    }

    @Test
    fun `backfill marks seeded even when everything is unread or the feed is empty`() {
        val allUnread = store()
        allUnread.backfillReadIds(listOf(notification(2), notification(1)), serverUnreadCount = 2)

        // Nothing to mark read, but the seed ran: a second backfill must not re-seed,
        // and opens must not treat this as unseeded and refetch every time.
        allUnread.getReadIds() shouldBe emptySet()
        allUnread.isSeeded() shouldBe true
        allUnread.backfillReadIds(listOf(notification(3)), serverUnreadCount = 0)
        allUnread.getReadIds() shouldBe emptySet()

        val emptyFeed = store()
        emptyFeed.backfillReadIds(emptyList(), serverUnreadCount = 0)
        emptyFeed.isSeeded() shouldBe true
    }

    @Test
    fun `pre-existing tap markers alone do not count as seeded`() {
        // The persisted seed flag is the sole source of truth. A legacy install with tap markers
        // but no flag is re-seeded once (backfill unions with the existing taps) rather than a
        // stray tap during the first refresh permanently suppressing the seed.
        val prefs = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    AniListNotificationStore.READ_IDS_KEY,
                    "[1]",
                    "[]",
                ),
            ),
        )

        AniListNotificationStore(prefs).isSeeded() shouldBe false
    }

    @Test
    fun `unread count excludes tapped ids and null ids`() {
        val store = store()
        store.saveCache(listOf(notification(1), notification(2), notification(3).copy(id = null)))

        store.getUnreadCount() shouldBe 2

        store.markRead(1)

        store.getUnreadCount() shouldBe 1
    }

    @Test
    fun `markAllRead clears the unread count and clear resets everything`() {
        val store = store()
        store.saveCache(listOf(notification(1), notification(2)))

        store.markAllRead(listOf(1L, 2L, null))

        store.getUnreadCount() shouldBe 0

        store.clear()

        store.getCached() shouldBe emptyList()
        store.getReadIds() shouldBe emptySet()
        store.getUnreadCount() shouldBe 0
        store.isSeeded() shouldBe false
    }
}
