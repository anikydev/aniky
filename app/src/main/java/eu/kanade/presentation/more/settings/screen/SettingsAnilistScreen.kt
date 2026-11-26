package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AnilistTitleLanguage
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.source.CatalogueSource
import exh.source.BlacklistedSources
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsAnilistScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AMR.strings.pref_category_anilist

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val trackPreferences = remember { Injekt.get<TrackPreferences>() }
        // ANK -->
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val catalogueSources by sourceManager.catalogueSources.collectAsState(
            initial = remember { sourceManager.getVisibleCatalogueSources() },
        )
        val sourceEntries = remember(catalogueSources) {
            catalogueSources
                .filter { it.id !in BlacklistedSources.HIDDEN_SOURCES && it.id != LocalSource.ID }
                .sortedWith(compareBy<CatalogueSource>({ it.name.lowercase() }, { it.lang.lowercase() }))
                .associate { "${it.id}" to "${it.name} (${it.lang.uppercase()})" }
                .toImmutableMap()
        }
        // ANK <--

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AMR.strings.pref_anilist_titles),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = uiPreferences.anilistTitleLanguage(),
                        title = stringResource(AMR.strings.pref_anilist_title_language),
                        entries = persistentMapOf(
                            AnilistTitleLanguage.ROMAJI to stringResource(AMR.strings.anilist_title_romaji),
                            AnilistTitleLanguage.ENGLISH to stringResource(AMR.strings.anilist_title_english),
                            AnilistTitleLanguage.NATIVE to stringResource(AMR.strings.anilist_title_native),
                        ),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.browse),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.showAniListRelations(),
                        title = stringResource(AMR.strings.pref_show_anilist_relations),
                    ),
                    // ANK -->
                    Preference.PreferenceItem.MultiSelectListPreference(
                        preference = trackPreferences.anilistPreferredSources(),
                        entries = sourceEntries,
                        title = stringResource(AMR.strings.pref_anilist_preferred_sources),
                        enabled = sourceEntries.isNotEmpty(),
                        subtitleProvider = { value, entries ->
                            if (entries.isEmpty()) {
                                stringResource(AMR.strings.source_match_no_sources)
                            } else {
                                val combined = remember(value, entries) {
                                    value.mapNotNull { entries[it] }
                                        .joinToString()
                                        .takeUnless { it.isBlank() }
                                }
                                combined ?: stringResource(AMR.strings.pref_anilist_preferred_sources_all_enabled)
                            }
                        },
                    ),
                    // ANK <--
                ),
            ),
        )
    }
}
