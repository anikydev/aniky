package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.util.PkceUtil
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.ank.AMR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTrackingScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsTrackingScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://anikku-app.github.io/docs/guides/tracking") }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val trackPreferences = remember { Injekt.get<TrackPreferences>() }
        val trackerManager = remember { Injekt.get<TrackerManager>() }

        var dialog by remember { mutableStateOf<LogoutDialog?>(null) }
        dialog?.run {
            TrackingLogoutDialog(
                tracker = tracker,
                onDismissRequest = { dialog = null },
            )
        }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.trackOnAddingToLibrary(),
                title = stringResource(AYMR.strings.pref_track_on_add_library),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.showNextChapterAiringTime(),
                title = stringResource(AYMR.strings.pref_show_next_episode_airing_time),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.autoUpdateTrack(),
                title = stringResource(AMR.strings.pref_auto_update_anime_sync),
            ),
            // AM -->
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.smartTrackerSync(),
                title = stringResource(AMMR.strings.pref_smart_sync_tracker),
                subtitle = stringResource(AMMR.strings.pref_smart_sync_tracker_summary),
            ),
            // <-- AM
            Preference.PreferenceItem.ListPreference(
                preference = trackPreferences.autoUpdateTrackOnMarkRead(),
                entries = AutoTrackState.entries
                    .associateWith { stringResource(it.titleRes) }
                    .toPersistentMap(),
                title = stringResource(AMR.strings.pref_auto_update_anime_on_mark_seen),
            ),
            // KMK -->
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.autoSyncProgressFromTrackers(),
                title = stringResource(KMR.strings.pref_auto_sync_progress_from_trackers),
            ),
            // KMK <--
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.services),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.myAnimeList,
                        login = {
                            val state = PkceUtil.generateCodeVerifier()
                            trackPreferences.pendingOAuthState.set(state)
                            context.openInBrowser(
                                MyAnimeListApi.authUrl(state),
                                forceDefaultBrowser = true,
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.myAnimeList) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.aniList,
                        login = {
                            val state = PkceUtil.generateCodeVerifier()
                            trackPreferences.pendingOAuthState.set(state)
                            context.openInBrowser(
                                AnilistApi.authUrl(state),
                                forceDefaultBrowser = true,
                            )
                        },
                        logout = { dialog = LogoutDialog(trackerManager.aniList) },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(AMR.strings.tracking_info_anime)),
                ),
            ),
        )
    }

    @Composable
    private fun TrackingLogoutDialog(
        tracker: Tracker,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(MR.strings.logout_title, tracker.name),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismissRequest,
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            tracker.logout()
                            onDismissRequest()
                            context.toast(MR.strings.logout_success)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.logout))
                    }
                }
            },
        )
    }
}

private data class LogoutDialog(
    val tracker: Tracker,
)
