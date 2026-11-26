package eu.kanade.tachiyomi.ui.anilist

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.anilist.dto.ALListStatus
import eu.kanade.tachiyomi.util.PkceUtil
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

// ANK -->
/** String resource for an AniList list status ("CURRENT" → AMR.strings.watching). */
fun anilistStatusStringRes(status: String?): StringResource {
    return when (status) {
        ALListStatus.CURRENT -> AMR.strings.watching
        ALListStatus.REPEATING -> AMR.strings.repeating_anime
        ALListStatus.COMPLETED -> MR.strings.completed
        ALListStatus.PAUSED -> MR.strings.paused
        ALListStatus.DROPPED -> MR.strings.dropped
        ALListStatus.PLANNING -> AMR.strings.plan_to_watch
        else -> MR.strings.status
    }
}
// ANK <--

/** Localized label for an AniList list status ("CURRENT" → "Watching"); falls back to the raw value. */
@Composable
fun anilistStatusLabel(status: String?): String {
    return when (status) {
        ALListStatus.CURRENT -> stringResource(AMR.strings.watching)
        ALListStatus.REPEATING -> stringResource(AMR.strings.repeating_anime)
        ALListStatus.COMPLETED -> stringResource(MR.strings.completed)
        ALListStatus.PAUSED -> stringResource(MR.strings.paused)
        ALListStatus.DROPPED -> stringResource(MR.strings.dropped)
        ALListStatus.PLANNING -> stringResource(AMR.strings.plan_to_watch)
        else -> status.orEmpty()
    }
}

// ANK -->
/** Non-composable localized label for an AniList list status (e.g. for toast callbacks). */
fun anilistStatusLabel(context: Context, status: String?): String {
    return when (status) {
        ALListStatus.CURRENT -> context.contextStringResource(AMR.strings.watching)
        ALListStatus.REPEATING -> context.contextStringResource(AMR.strings.repeating_anime)
        ALListStatus.COMPLETED -> context.contextStringResource(MR.strings.completed)
        ALListStatus.PAUSED -> context.contextStringResource(MR.strings.paused)
        ALListStatus.DROPPED -> context.contextStringResource(MR.strings.dropped)
        ALListStatus.PLANNING -> context.contextStringResource(AMR.strings.plan_to_watch)
        else -> status.orEmpty()
    }
}
// ANK <--

/** Visual accent color for AniList list status. */
fun anilistStatusColor(status: String?): Color = when (status) {
    ALListStatus.CURRENT -> Color(0xFF4CAF50)
    ALListStatus.PLANNING -> Color(0xFFAB47BC)
    ALListStatus.COMPLETED -> Color(0xFF2196F3)
    ALListStatus.PAUSED -> Color(0xFFFF9800)
    ALListStatus.DROPPED -> Color(0xFFF44336)
    ALListStatus.REPEATING -> Color(0xFF00BCD4)
    else -> Color(0xFF9E9E9E)
}

/**
 * Formats an AniList 0–100 score for display on the 10-point scale AniList itself uses
 * ("85" → "8.5", "80" → "8"). Zero, negative or null → "" (no score to show).
 */
fun anilistScoreText(score100: Number?): String {
    val ten = (score100?.toDouble() ?: 0.0).takeIf { it > 0 }?.div(10.0) ?: return ""
    return if (ten % 1.0 == 0.0) ten.toInt().toString() else ten.toString()
}

/** Starred score ("★ 8.5"), or "" when there is no score. */
fun anilistScoreStar(score100: Number?): String {
    val text = anilistScoreText(score100)
    return if (text.isEmpty()) "" else "★ $text"
}

// ANK -->
/** String resource for an AniList media format ("TV_SHORT" or "TV-SHORT" → AMR.strings.anilist_format_tv_short). */
fun anilistFormatStringRes(format: String?): StringResource? {
    return when (format?.uppercase()?.replace('-', '_')) {
        "TV" -> AMR.strings.anilist_format_tv
        "TV_SHORT" -> AMR.strings.anilist_format_tv_short
        "MOVIE" -> AMR.strings.anilist_format_movie
        "SPECIAL" -> AMR.strings.anilist_format_special
        "OVA" -> AMR.strings.anilist_format_ova
        "ONA" -> AMR.strings.anilist_format_ona
        "MUSIC" -> AMR.strings.anilist_format_music
        else -> null
    }
}

/** Localized label for an AniList media format ("TV_SHORT" → "TV Short"); falls back to a cleaned raw value. */
@Composable
fun anilistFormatLabel(format: String?): String {
    return anilistFormatStringRes(format)?.let { stringResource(it) }
        ?: format.orEmpty().replace("_", " ").replace("-", " ")
}

/** Localized label for an AniList character role ("MAIN" → "Main"). */
@Composable
fun anilistCharacterRoleLabel(role: String?): String {
    return when (role?.uppercase()) {
        "MAIN" -> stringResource(AMR.strings.anilist_role_main)
        "SUPPORTING" -> stringResource(AMR.strings.anilist_role_supporting)
        "BACKGROUND" -> stringResource(AMR.strings.anilist_role_background)
        else -> role.orEmpty()
    }
}

/** Localized label for an AniList airing status ("RELEASING" → "Airing"); falls back to a cleaned raw value. */
@Composable
fun anilistAiringStatusLabel(status: String?): String {
    return when (status) {
        "RELEASING" -> stringResource(AMR.strings.seasonal_status_airing)
        "FINISHED" -> stringResource(AMR.strings.seasonal_status_finished)
        "NOT_YET_RELEASED" -> stringResource(AMR.strings.seasonal_status_upcoming)
        "CANCELLED" -> stringResource(AMR.strings.anilist_airing_status_cancelled)
        "HIATUS" -> stringResource(AMR.strings.anilist_airing_status_hiatus)
        else -> status.orEmpty().replace("_", " ")
    }
}

/** Localized label for an AniList season ("FALL" → "Fall"). */
@Composable
fun anilistSeasonLabel(season: String?): String {
    return when (season?.uppercase()) {
        "WINTER" -> stringResource(AMR.strings.anilist_season_winter)
        "SPRING" -> stringResource(AMR.strings.anilist_season_spring)
        "SUMMER" -> stringResource(AMR.strings.anilist_season_summer)
        "FALL" -> stringResource(AMR.strings.anilist_season_fall)
        else -> season.orEmpty()
    }
}
// ANK <--

/** Display title with a localized fallback for entries whose media is missing. */
@Composable
fun anilistTitle(title: String): String {
    return title.ifBlank { stringResource(AMR.strings.anilist_unknown_title) }
}

/**
 * Login prompt shared by every AniList screen, mirroring the Seasonal tab exactly: the message
 * plus a shortcut to the tracking settings (where AniList login lives) and a retry. The screen
 * models observe the login state and load themselves once the token comes back.
 */
@Composable
fun AniListLoginRequired(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    stringRes: StringResource = AMR.strings.anilist_login_required,
) {
    // ANK -->
    val context = LocalContext.current
    val trackPreferences = remember { Injekt.get<TrackPreferences>() }
    EmptyScreen(
        stringRes = stringRes,
        modifier = modifier,
        help = {
            // Starts the same OAuth flow as Settings > Tracking, so users never have to hunt for it.
            Button(
                onClick = {
                    val state = PkceUtil.generateCodeVerifier()
                    trackPreferences.pendingOAuthState.set(state)
                    context.openInBrowser(AnilistApi.authUrl(state), forceDefaultBrowser = true)
                },
                shape = AniListShapes.Pill,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_anilist),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(AMR.strings.anilist_login_action))
            }
        },
        actions = persistentListOf(
            EmptyScreenAction(
                stringRes = MR.strings.action_retry,
                icon = Icons.Outlined.Refresh,
                onClick = onRetry,
            ),
        ),
    )
    // ANK <--
}

/**
 * Resolves the preferred display title honoring user's title language setting without requiring
 * a Composable context (safe to call in click listeners and event lambdas).
 */
fun anilistPickTitle(
    userPreferred: String?,
    english: String? = null,
    romaji: String? = null,
    native: String? = null,
): String {
    val language = Injekt.get<UiPreferences>().anilistTitleLanguage().get()
    val title = language.pick(romaji = romaji, english = english, native = native, userPreferred = userPreferred)
    return title.ifBlank { userPreferred ?: english ?: romaji ?: "" }
}

/**
 * Preferred display title honouring the AniList "Title language" setting (Romaji / English /
 * Native) used across the AniList UI, with a fallback chain so a missing field never blanks the
 * title, and the same blank→"unknown" fallback as [anilistTitle]. Use everywhere an anime title is
 * shown so the same anime reads identically on every screen.
 */
@Composable
fun anilistDisplayTitle(
    userPreferred: String?,
    english: String? = null,
    romaji: String? = null,
    native: String? = null,
): String {
    // Read fresh each composition (no remember key) so changing the setting takes effect on screens
    // Voyager kept on the back stack, matching how MyLists reads it in applyFilter.
    val title = anilistPickTitle(userPreferred, english, romaji, native)
    return title.ifBlank { stringResource(AMR.strings.anilist_unknown_title) }
}
