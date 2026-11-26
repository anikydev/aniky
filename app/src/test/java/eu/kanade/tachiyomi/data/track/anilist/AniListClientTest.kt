package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseCover
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseTitle
import eu.kanade.tachiyomi.data.track.anilist.dto.ALCurrentUserResult
import eu.kanade.tachiyomi.data.track.anilist.dto.ALFuzzyDate
import eu.kanade.tachiyomi.data.track.anilist.dto.ALListStatus
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMyListEntry
import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserListItem
import eu.kanade.tachiyomi.ui.anilist.anilistScoreStar
import eu.kanade.tachiyomi.ui.anilist.anilistScoreText
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Phase 5: pins the AniList client foundation — status constants cover all 6
 * AniList list states, and MyList entries resolve media ids for detail navigation.
 */
@Execution(ExecutionMode.CONCURRENT)
class AniListClientTest {

    // Mirrors the app's decoder enough for these DTO tests (unknown keys ignored; coerceInputValues
    // stays off, so an explicit null or a wrong-typed number still surfaces as it would in the app).
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun `list statuses cover all six anilist states`() {
        ALListStatus.ALL shouldBe listOf(
            "CURRENT",
            "REPEATING",
            "COMPLETED",
            "PAUSED",
            "DROPPED",
            "PLANNING",
        )
    }

    @Test
    fun `my list entry exposes media id for detail`() {
        val entry = ALMyListEntry(
            id = 99,
            mediaId = 21,
            status = ALListStatus.CURRENT,
            progress = 5,
            media = ALSearchItem(
                id = 21,
                title = eu.kanade.tachiyomi.data.track.anilist.dto.ALItemTitle("Example"),
                coverImage = eu.kanade.tachiyomi.data.track.anilist.dto.ItemCover("https://cdn/x.jpg"),
            ),
        )
        (entry.media?.id ?: entry.mediaId) shouldBe 21L
    }

    @Test
    fun `browse item carries score and favourites`() {
        val item = ALBrowseItem(
            id = 1,
            title = ALBrowseTitle("Example"),
            coverImage = ALBrowseCover(large = "https://cdn/x.jpg"),
            averageScore = 85,
            favourites = 1000,
        )
        (item.averageScore!! / 10.0) shouldBe 8.5
        item.favourites shouldBe 1000
    }

    @Test
    fun `fuzzy date defaults to nulls`() {
        ALFuzzyDate(null, null, null).toEpochMilli() shouldBe 0L
    }

    @Test
    fun `score text uses 10-point scale without trailing zero`() {
        anilistScoreText(85) shouldBe "8.5"
        anilistScoreText(80) shouldBe "8"
        anilistScoreText(85.0) shouldBe "8.5"
        anilistScoreText(0) shouldBe ""
        anilistScoreText(null) shouldBe ""
    }

    @Test
    fun `score star prefixes only when scored`() {
        anilistScoreStar(85) shouldBe "★ 8.5"
        anilistScoreStar(0) shouldBe ""
    }

    @Test
    fun `my list entry decodes a fractional AniList score without failing the parse`() {
        // AniList's MediaList.score is schema-typed Float; an Int-typed scoreRaw used to throw on a
        // fractional value and fail the whole My Lists page parse. Decode as Double, expose rounded.
        val entry = lenientJson.decodeFromString<ALMyListEntry>(
            """{"id":1,"mediaId":2,"status":"CURRENT","scoreRaw":85.0,"progress":3}""",
        )
        entry.scoreRaw shouldBe 85
    }

    @Test
    fun `user list item rounds a fractional score`() {
        val item = lenientJson.decodeFromString<ALUserListItem>(
            """{"id":1,"status":"CURRENT","scoreRaw":82.6,"progress":3}""",
        )
        item.scoreRaw shouldBe 83
    }

    @Test
    fun `current user parses when mediaListOptions is null`() {
        // AniList can return a null mediaListOptions; a non-null field here used to crash login.
        val result = lenientJson.decodeFromString<ALCurrentUserResult>(
            """{"data":{"Viewer":{"id":7,"mediaListOptions":null}}}""",
        )
        result.data.viewer.mediaListOptions shouldBe null
    }
}
