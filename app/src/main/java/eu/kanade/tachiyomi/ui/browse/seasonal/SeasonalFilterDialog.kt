package eu.kanade.tachiyomi.ui.browse.seasonal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.anilist.anilistFormatLabel
import eu.kanade.tachiyomi.ui.anilist.anilistSeasonLabel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeasonalFilterDialog(
    state: SeasonalScreenModel.State,
    onApplyFilters: (season: Int, year: Int, format: Int, status: Int, genres: Set<String>) -> Unit,
    onSelectSort: (Int) -> Unit = {},
    onSetSortAscending: (Boolean) -> Unit = {},
    onSelectViewMode: (Int) -> Unit = {},
    onSelectGridColumns: (Int) -> Unit = {},
    onDismissRequest: () -> Unit,
) {
    val now = LocalDate.now()
    val currentCalendarYear = now.year

    var season by remember { mutableIntStateOf(state.season) }
    var year by remember { mutableIntStateOf(state.year) }
    var format by remember { mutableIntStateOf(state.format) }
    var status by remember { mutableIntStateOf(state.statusFilter) }
    var selectedGenres by remember { mutableStateOf(state.selectedGenres) }
    var genreQuery by remember { mutableStateOf("") }
    var showAllYearsDialog by remember { mutableStateOf(false) }

    val filterTabTitle = buildString {
        append(stringResource(MR.strings.action_filter))
        val activeCount = (if (season != -1) 1 else 0) +
            (if (year > 0 || year == -2) 1 else 0) +
            (if (format > 0) 1 else 0) +
            (if (status > 0) 1 else 0) +
            selectedGenres.size
        if (activeCount > 0) {
            append(" ($activeCount)")
        }
    }

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            filterTabTitle,
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
        ),
    ) { page ->
        when (page) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = TabbedDialogPaddings.Horizontal,
                                vertical = 12.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Season Section
                        FilterGroup(title = stringResource(AMR.strings.seasonal_filter_season)) {
                            val currentSelection = SeasonSelection.of(-1, year, now)
                            val seasonOptions = listOf(
                                -1 to (
                                    "${stringResource(AMR.strings.seasonal_current)} " +
                                        "(${anilistSeasonLabel(currentSelection.label)})"
                                    ),
                                0 to "❄️ ${anilistSeasonLabel("WINTER")}",
                                1 to "🌸 ${anilistSeasonLabel("SPRING")}",
                                2 to "☀️ ${anilistSeasonLabel("SUMMER")}",
                                3 to "🍂 ${anilistSeasonLabel("FALL")}",
                                SEASON_ALL to ("🌐 " + stringResource(AMR.strings.seasonal_all_seasons)),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                seasonOptions.forEach { (code, label) ->
                                    val isSelected = season == code
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { season = code },
                                        label = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                }
                            }
                        }

                        // Year Section
                        FilterGroup(title = stringResource(AMR.strings.seasonal_filter_year)) {
                            val quickYears = listOf(
                                -1 to ("${stringResource(AMR.strings.seasonal_current)} ($currentCalendarYear)"),
                                currentCalendarYear + 1 to (currentCalendarYear + 1).toString(),
                                currentCalendarYear to currentCalendarYear.toString(),
                                currentCalendarYear - 1 to (currentCalendarYear - 1).toString(),
                                currentCalendarYear - 2 to (currentCalendarYear - 2).toString(),
                                currentCalendarYear - 3 to (currentCalendarYear - 3).toString(),
                                currentCalendarYear - 4 to (currentCalendarYear - 4).toString(),
                                currentCalendarYear - 5 to (currentCalendarYear - 5).toString(),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                quickYears.forEach { (yVal, label) ->
                                    val isSelected = (yVal == -1 && year <= 0) || (year == yVal)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { year = yVal },
                                        label = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                }

                                val isCustomYearSelected = year > 0 && year < (currentCalendarYear - 5)
                                FilterChip(
                                    selected = isCustomYearSelected,
                                    onClick = { showAllYearsDialog = true },
                                    label = {
                                        Text(
                                            text = if (isCustomYearSelected) {
                                                "$year"
                                            } else {
                                                "⋯ ${stringResource(AMR.strings.anilist_more_genres)}"
                                            },
                                            fontWeight = if (isCustomYearSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                )
                            }
                        }

                        // Format Section
                        FilterGroup(title = stringResource(AMR.strings.seasonal_filter_format)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ANILIST_FORMATS.forEachIndexed { index, formatKey ->
                                    val label = if (index == 0) {
                                        stringResource(AMR.strings.anilist_season_all)
                                    } else {
                                        anilistFormatLabel(formatKey)
                                    }
                                    val isSelected = (index == 0 && format <= 0) || (format == index)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { format = if (index == 0) 0 else index },
                                        label = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                }
                            }
                        }

                        // Status Section
                        FilterGroup(title = stringResource(AMR.strings.seasonal_filter_status)) {
                            val statusList = listOf(
                                0 to stringResource(AMR.strings.seasonal_status_all),
                                1 to stringResource(AMR.strings.seasonal_status_airing),
                                2 to stringResource(AMR.strings.seasonal_status_finished),
                                3 to stringResource(AMR.strings.seasonal_status_upcoming),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                statusList.forEach { (statusCode, label) ->
                                    val isSelected = status == statusCode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { status = statusCode },
                                        label = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                }
                            }
                        }

                        // Genres Section
                        if (state.availableGenres.isNotEmpty()) {
                            val genreTitle = if (selectedGenres.isNotEmpty()) {
                                "${stringResource(AMR.strings.seasonal_filter_genre)} (${selectedGenres.size})"
                            } else {
                                stringResource(AMR.strings.seasonal_filter_genre)
                            }

                            FilterGroup(title = genreTitle) {
                                OutlinedTextField(
                                    value = genreQuery,
                                    onValueChange = { genreQuery = it },
                                    placeholder = { Text(stringResource(MR.strings.action_search)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    trailingIcon = if (genreQuery.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { genreQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                )

                                val filteredGenres = remember(state.availableGenres, genreQuery) {
                                    if (genreQuery.isBlank()) {
                                        state.availableGenres
                                    } else {
                                        state.availableGenres.filter {
                                            it.contains(genreQuery.trim(), ignoreCase = true)
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    filteredGenres.forEach { genre ->
                                        val isSelected = genre in selectedGenres
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedGenres = if (isSelected) {
                                                    selectedGenres - genre
                                                } else {
                                                    selectedGenres + genre
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = genre,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                )
                                            },
                                            leadingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                season = -1
                                year = -1
                                format = 0
                                status = 0
                                selectedGenres = emptySet()
                                genreQuery = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(MR.strings.action_reset))
                        }

                        Button(
                            onClick = {
                                onApplyFilters(
                                    season,
                                    year,
                                    if (format >= 0) format else 0,
                                    if (status >= 0) status else 0,
                                    selectedGenres,
                                )
                                onDismissRequest()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f),
                        ) {
                            Text(
                                text = stringResource(MR.strings.action_apply),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            1 -> {
                val sortOptions = listOf(
                    0 to stringResource(AMR.strings.seasonal_sort_popularity),
                    1 to stringResource(AMR.strings.seasonal_sort_score),
                    2 to stringResource(AMR.strings.seasonal_sort_title),
                    3 to stringResource(AMR.strings.seasonal_sort_airing),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = TabbedDialogPaddings.Vertical),
                ) {
                    sortOptions.forEach { (mode, label) ->
                        val isSelected = state.sort == mode
                        SortItem(
                            label = label,
                            sortDescending = (!state.sortAscending).takeIf { isSelected },
                            onClick = {
                                if (isSelected) {
                                    onSetSortAscending(!state.sortAscending)
                                } else {
                                    onSelectSort(mode)
                                }
                            },
                        )
                    }
                }
            }

            2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = TabbedDialogPaddings.Vertical),
                ) {
                    SettingsChipRow(MR.strings.action_display_mode) {
                        val modes = listOf(
                            0 to stringResource(AMR.strings.seasonal_view_grid),
                            1 to stringResource(AMR.strings.seasonal_view_list),
                        )
                        modes.forEach { (mode, label) ->
                            FilterChip(
                                selected = state.viewMode == mode,
                                onClick = { onSelectViewMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }

                    if (state.viewMode == 0) {
                        SettingsChipRow(AMR.strings.seasonal_grid_columns_label) {
                            listOf(2, 3, 4).forEach { cols ->
                                FilterChip(
                                    selected = state.gridColumns == cols,
                                    onClick = { onSelectGridColumns(cols) },
                                    label = { Text(stringResource(AMR.strings.seasonal_grid_columns, cols)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAllYearsDialog) {
        SeasonalYearDialog(
            currentYear = year,
            onSelectYear = {
                year = it
                showAllYearsDialog = false
            },
            onDismissRequest = { showAllYearsDialog = false },
        )
    }
}

@Composable
private fun FilterGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}
