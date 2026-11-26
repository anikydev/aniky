package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibrarySettingsScreenModel
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.ank.AMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// ANK -->
private data class ActiveLibraryFilterChip(
    val key: String,
    val label: String,
    val onRemove: () -> Unit,
)

@Composable
fun LibraryActiveFiltersRow(
    settingsScreenModel: LibrarySettingsScreenModel,
    category: Category?,
    onClickFilterSheet: (() -> Unit)? = null,
) {
    val downloadedOnly by settingsScreenModel.preferences.downloadedOnly().collectAsState()
    val filterDownloaded by settingsScreenModel.libraryPreferences.filterDownloaded().collectAsState()
    val filterUnread by settingsScreenModel.libraryPreferences.filterUnread().collectAsState()
    val filterStarted by settingsScreenModel.libraryPreferences.filterStarted().collectAsState()
    val filterBookmarked by settingsScreenModel.libraryPreferences.filterBookmarked().collectAsState()
    val filterCompleted by settingsScreenModel.libraryPreferences.filterCompleted().collectAsState()
    val filterIntervalCustom by settingsScreenModel.libraryPreferences.filterIntervalCustom().collectAsState()
    val filterLewd by settingsScreenModel.libraryPreferences.filterLewd().collectAsState()
    val filterCategories by settingsScreenModel.libraryPreferences.filterCategories().collectAsState()
    val trackers by settingsScreenModel.trackersFlow.collectAsState()
    val globalSortMode by settingsScreenModel.libraryPreferences.sortingMode().collectAsState()

    val downloadedLabel = stringResource(MR.strings.label_downloaded)
    val unseenLabel = stringResource(AYMR.strings.action_filter_unseen)
    val startedLabel = stringResource(MR.strings.label_started)
    val bookmarkedLabel = stringResource(MR.strings.action_filter_bookmarked)
    val completedLabel = stringResource(MR.strings.completed)
    val intervalLabel = stringResource(MR.strings.action_filter_interval_custom)
    val lewdLabel = stringResource(SYMR.strings.lewd)
    val categoriesLabel = stringResource(MR.strings.categories)
    val trackedLabel = stringResource(MR.strings.action_filter_tracked)
    val trackerStates = trackers.map { service ->
        service to settingsScreenModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState().value
    }

    val chips = buildList {
        fun addTriState(
            key: String,
            state: TriState,
            baseLabel: String,
            onClear: () -> Unit,
        ) {
            when (state) {
                TriState.ENABLED_IS -> add(ActiveLibraryFilterChip(key, baseLabel, onClear))
                TriState.ENABLED_NOT -> add(ActiveLibraryFilterChip(key, "≠ $baseLabel", onClear))
                TriState.DISABLED -> Unit
            }
        }

        if (downloadedOnly) {
            add(
                ActiveLibraryFilterChip("downloaded_only", downloadedLabel) {
                    settingsScreenModel.preferences.downloadedOnly().set(false)
                },
            )
        }
        addTriState("downloaded", filterDownloaded, downloadedLabel) {
            settingsScreenModel.clearFilter(LibraryPreferences::filterDownloaded)
        }
        addTriState("unread", filterUnread, unseenLabel) {
            settingsScreenModel.clearFilter(LibraryPreferences::filterUnread)
        }
        addTriState("started", filterStarted, startedLabel) {
            settingsScreenModel.clearFilter(LibraryPreferences::filterStarted)
        }
        addTriState("bookmarked", filterBookmarked, bookmarkedLabel) {
            settingsScreenModel.clearFilter(LibraryPreferences::filterBookmarked)
        }
        addTriState("completed", filterCompleted, completedLabel) {
            settingsScreenModel.clearFilter(LibraryPreferences::filterCompleted)
        }
        addTriState("interval", filterIntervalCustom, intervalLabel) {
            settingsScreenModel.clearFilter(LibraryPreferences::filterIntervalCustom)
        }
        addTriState("lewd", filterLewd, lewdLabel) {
            settingsScreenModel.clearFilter(LibraryPreferences::filterLewd)
        }
        if (filterCategories) {
            add(
                ActiveLibraryFilterChip("categories", categoriesLabel) {
                    settingsScreenModel.libraryPreferences.filterCategories().set(false)
                },
            )
        }
        trackerStates.forEach { (service, trackerState) ->
            val id = service.id.toInt()
            val label = if (trackers.size == 1) trackedLabel else service.name
            addTriState("tracker_$id", trackerState, label) {
                settingsScreenModel.clearTrackerFilter(id)
            }
        }
    }

    if (chips.isEmpty()) return

    val activeSort = if (settingsScreenModel.grouping == LibraryGroup.BY_DEFAULT) {
        category.sort
    } else {
        globalSortMode
    }
    val sortName = sortModeLabel(activeSort.type)
    val sortArrow = if (activeSort.type == LibrarySort.Type.Random) {
        ""
    } else if (activeSort.isAscending) {
        " ↑"
    } else {
        " ↓"
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "clear_all_library_filters") {
            InputChip(
                selected = false,
                onClick = {
                    settingsScreenModel.clearAllFilters()
                    settingsScreenModel.preferences.downloadedOnly().set(false)
                },
                label = {
                    Text(
                        text = stringResource(AMR.strings.library_clear_filters),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(30.dp),
            )
        }
        items(chips, key = { it.key }) { chip ->
            InputChip(
                selected = true,
                onClick = chip.onRemove,
                label = {
                    Text(
                        text = chip.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(30.dp),
            )
        }
        if (onClickFilterSheet != null) {
            item(key = "library_active_sort") {
                InputChip(
                    selected = false,
                    onClick = onClickFilterSheet,
                    label = {
                        Text(
                            text = "$sortName$sortArrow",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(30.dp),
                )
            }
        }
    }
}

@Composable
private fun sortModeLabel(type: LibrarySort.Type): String {
    return when (type) {
        LibrarySort.Type.Alphabetical -> stringResource(MR.strings.action_sort_alpha)
        LibrarySort.Type.LastRead -> stringResource(AYMR.strings.action_sort_last_seen)
        LibrarySort.Type.LastUpdate -> stringResource(AMR.strings.action_sort_last_update_check)
        LibrarySort.Type.UnreadCount -> stringResource(AYMR.strings.action_sort_unseen_count)
        LibrarySort.Type.TotalChapters -> stringResource(AYMR.strings.action_sort_total_episodes)
        LibrarySort.Type.LatestChapter -> stringResource(AYMR.strings.action_sort_latest_episode)
        LibrarySort.Type.ChapterFetchDate -> stringResource(AYMR.strings.action_sort_episode_fetch_date)
        LibrarySort.Type.DateAdded -> stringResource(MR.strings.action_sort_date_added)
        LibrarySort.Type.TrackerMean -> stringResource(MR.strings.action_sort_tracker_score)
        LibrarySort.Type.AiringTime -> stringResource(AYMR.strings.action_sort_airing_time)
        LibrarySort.Type.TagList -> stringResource(SYMR.strings.tag_sorting)
        LibrarySort.Type.Random -> stringResource(MR.strings.action_sort_random)
    }
}
// ANK <--
