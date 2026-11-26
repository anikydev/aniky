package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastMap
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.presentation.player.components.OutlinedNumericChooser
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.ank.AMR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDownloadScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsDownloadScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_downloads

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(initial = emptyList())

        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val parallelSourceLimit by downloadPreferences.parallelSourceLimit().collectAsState()
        val basePreferences = remember { Injekt.get<BasePreferences>() }

        // ANK -->
        val internalDownloaderPref = downloadPreferences.useInternalDownloader()
        val internalDownloader by internalDownloaderPref.collectAsState()

        val speedLimitPref = downloadPreferences.downloadSpeedLimit()
        val speedLimit by speedLimitPref.collectAsState()

        var showDownloadLimitDialog by rememberSaveable { mutableStateOf(false) }
        var currentSpeedLimit by remember(speedLimit) { mutableIntStateOf(speedLimit) }

        if (showDownloadLimitDialog) {
            DownloadLimitDialog(
                initialValue = currentSpeedLimit,
                onDismissRequest = { showDownloadLimitDialog = false },
                onValueChanged = { currentSpeedLimit = it },
                onConfirm = {
                    val clamped = currentSpeedLimit.coerceIn(0, 1_000_000)
                    speedLimitPref.set(clamped)
                    showDownloadLimitDialog = false
                },
            )
        }
        // <-- ANK

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.downloadOnlyOverWifi(),
                title = stringResource(MR.strings.connected_to_wifi),
            ),
            // AM -->
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.ignoreBrokenTracks,
                title = stringResource(AMMR.strings.pref_download_ignore_broken_tracks),
            ),
            // <-- AM
            Preference.PreferenceItem.SliderPreference(
                value = parallelSourceLimit,
                valueRange = 1..10,
                title = stringResource(MR.strings.pref_download_concurrent_sources),
                onValueChanged = { downloadPreferences.parallelSourceLimit().set(it) },
            ),
            // ANK -->
            Preference.PreferenceItem.SwitchPreference(
                preference = internalDownloaderPref,
                title = stringResource(AMR.strings.pref_download_use_internal),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = downloadPreferences.downloadThreads(),
                entries = (1..64).associateWith { it.toString() }.toImmutableMap(),
                title = stringResource(AMR.strings.pref_download_threads),
                enabled = internalDownloader,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(AMR.strings.pref_download_speed_limit),
                subtitle = if (speedLimit == 0) {
                    stringResource(MR.strings.off)
                } else {
                    stringResource(AMR.strings.pref_download_speed_limit_value, speedLimit)
                },
                enabled = internalDownloader,
                onClick = {
                    currentSpeedLimit = speedLimit
                    showDownloadLimitDialog = true
                },
            ),
            // <-- ANK
            getDeleteChaptersGroup(
                downloadPreferences = downloadPreferences,
                categories = allCategories,
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allCategories = allCategories,
            ),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
            // KMK -->
            getDownloadCacheRenewInterval(downloadPreferences = downloadPreferences),
            // KMK <--
            getExternalDownloaderGroup(
                downloadPreferences = downloadPreferences,
                basePreferences = basePreferences,
            ),
        )
    }

    @Composable
    private fun getDeleteChaptersGroup(
        downloadPreferences: DownloadPreferences,
        categories: List<Category>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AMR.strings.pref_category_delete_episodes),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeAfterMarkedAsRead(),
                    title = stringResource(AMR.strings.pref_remove_after_marked_as_seen),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.removeAfterReadSlots(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        0 to stringResource(AMR.strings.last_seen_episode),
                        1 to stringResource(AMR.strings.second_to_last_episode),
                        2 to stringResource(AMR.strings.third_to_last_episode),
                        3 to stringResource(AMR.strings.fourth_to_last_episode),
                        4 to stringResource(AMR.strings.fifth_to_last_episode),
                    ),
                    title = stringResource(AMR.strings.pref_remove_after_watching),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeBookmarkedChapters(),
                    title = stringResource(AMR.strings.pref_remove_bookmarked_episodes),
                ),
                // AY -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.downloadFillermarkedEpisodes(),
                    title = stringResource(AYMR.strings.pref_download_fillermarked_items),
                ),
                // <-- AY
                getExcludedCategoriesPreference(
                    downloadPreferences = downloadPreferences,
                    categories = { categories },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedCategoriesPreference(
        downloadPreferences: DownloadPreferences,
        categories: () -> List<Category>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            preference = downloadPreferences.removeExcludeCategories(),
            entries = categories()
                .associate { it.id.toString() to it.visualName }
                .toImmutableMap(),
            title = stringResource(MR.strings.pref_remove_exclude_categories),
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allCategories: List<Category>,
    ): Preference.PreferenceGroup {
        val downloadNewChaptersPref = downloadPreferences.downloadNewChapters()
        val downloadNewUnreadChaptersOnlyPref = downloadPreferences.downloadNewUnreadChaptersOnly()
        val downloadNewChapterCategoriesPref = downloadPreferences.downloadNewChapterCategories()
        val downloadNewChapterCategoriesExcludePref = downloadPreferences.downloadNewChapterCategoriesExclude()

        val downloadNewChapters by downloadNewChaptersPref.collectAsState()

        val included by downloadNewChapterCategoriesPref.collectAsState()
        val excluded by downloadNewChapterCategoriesExcludePref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            TriStateListDialog(
                title = stringResource(MR.strings.categories),
                message = stringResource(MR.strings.pref_download_new_categories_details),
                items = allCategories,
                initialChecked = included.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                initialInversed = excluded.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewChapterCategoriesPref.set(newIncluded.fastMap { it.id.toString() }.toSet())
                    downloadNewChapterCategoriesExcludePref.set(newExcluded.fastMap { it.id.toString() }.toSet())
                    showDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewChaptersPref,
                    title = stringResource(AYMR.strings.pref_download_new_episodes),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewUnreadChaptersOnlyPref,
                    title = stringResource(AYMR.strings.pref_download_new_unseen_episodes_only),
                    enabled = downloadNewChapters,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    onClick = { showDialog = true },
                    enabled = downloadNewChapters,
                ),
            ),
        )
    }

    @Composable
    private fun getDownloadAheadGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.download_ahead),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.autoDownloadWhileReading(),
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith {
                            if (it == 0) {
                                stringResource(MR.strings.disabled)
                            } else {
                                pluralStringResource(AYMR.plurals.next_unseen_episodes, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.auto_download_while_watching),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(AYMR.strings.download_ahead_info_anime)),
            ),
        )
    }

    @Composable
    private fun getExternalDownloaderGroup(
        downloadPreferences: DownloadPreferences,
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val useExternalDownloader = downloadPreferences.useExternalDownloader()
        val externalDownloaderPreference = downloadPreferences.externalDownloaderSelection()

        val pm = basePreferences.context.packageManager
        val installedPackages = pm.getInstalledPackages(0)
        val supportedDownloaders = installedPackages.filter {
            when (it.packageName) {
                "idm.internet.download.manager" -> true
                "idm.internet.download.manager.plus" -> true
                "idm.internet.download.manager.adm.lite" -> true
                "com.dv.adm" -> true
                else -> false
            }
        }
        val packageNames = supportedDownloaders.map { it.packageName }
        val packageNamesReadable = supportedDownloaders
            .map { pm.getApplicationLabel(it.applicationInfo!!).toString() }

        val packageNamesMap: Map<String, String> =
            mapOf("" to "None") + packageNames.zip(packageNamesReadable).toMap()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_external_downloader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = useExternalDownloader,
                    title = stringResource(AYMR.strings.pref_use_external_downloader),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = externalDownloaderPreference,
                    entries = packageNamesMap.toPersistentMap(),
                    title = stringResource(AYMR.strings.pref_external_downloader_selection),
                ),
            ),
        )
    }

    // KMK -->
    @Composable
    private fun getDownloadCacheRenewInterval(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.download_cache_renew_interval),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.downloadCacheRenewInterval(),
                    entries = persistentMapOf(
                        -1 to stringResource(KMR.strings.download_cache_renew_interval_manual),
                        1 to stringResource(KMR.strings.download_cache_renew_interval_1hour),
                        2 to stringResource(KMR.strings.download_cache_renew_interval_2hour),
                        6 to stringResource(KMR.strings.download_cache_renew_interval_6hour),
                        12 to stringResource(KMR.strings.download_cache_renew_interval_12hour),
                        24 to stringResource(KMR.strings.download_cache_renew_interval_24hour),
                    ),
                    title = stringResource(KMR.strings.download_cache_renew_interval),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(AMR.strings.download_cache_renew_interval_info_anime)),
            ),
        )
    }
    // KMK <--

    // ANK -->
    @Composable
    private fun DownloadLimitDialog(
        initialValue: Int,
        onDismissRequest: () -> Unit,
        onValueChanged: (newValue: Int) -> Unit,
        onConfirm: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(AMR.strings.pref_download_speed_limit)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .padding(bottom = MaterialTheme.padding.medium)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        OutlinedNumericChooser(
                            value = initialValue,
                            onChange = onValueChanged,
                            max = 1_000_000,
                            step = 100,
                            min = 0,
                            label = {
                                Text(stringResource(AMR.strings.pref_download_speed_limit))
                            },
                            suffix = {
                                Text(stringResource(AMR.strings.pref_download_speed_limit_unit))
                            },
                        )
                    }
                    Text(text = stringResource(AYMR.strings.download_speed_limit_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
    // <-- ANK
}
