package eu.kanade.tachiyomi.ui.browse.seasonal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.anilist.anilistFormatLabel
import eu.kanade.tachiyomi.ui.anilist.anilistSeasonLabel
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun SeasonalHeader(
    state: SeasonalScreenModel.State,
    onPrevSeason: () -> Unit,
    onNextSeason: () -> Unit,
    onJumpToCurrent: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectYear: (Int) -> Unit,
    onSelectFormat: (Int) -> Unit,
    onSelectStatus: (Int) -> Unit,
    onSelectSort: (Int) -> Unit,
    onToggleOrder: () -> Unit,
    onClearFormat: () -> Unit,
    onClearStatus: () -> Unit,
    onClearGenre: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val now = java.time.LocalDate.now()
    val selection = SeasonSelection.of(state.season, state.year, now)
    val effectiveYear = selection.year
    val isViewingCurrent = (state.season == -1 || state.season == currentSeasonIndex(now)) &&
        (state.year <= 0 || state.year == currentSeasonYear(now))

    var showSeasonDialog by remember { mutableStateOf(false) }
    var showYearDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        // Row 1: Season & Year Navigation + Sort & Order
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevSeason,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showSeasonDialog = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val seasonIcon = when (selection.index) {
                        0 -> "❄️"
                        1 -> "🌸"
                        2 -> "☀️"
                        else -> "🍂"
                    }
                    val displaySeason = if (state.season == SEASON_ALL) {
                        stringResource(AMR.strings.seasonal_all_seasons)
                    } else {
                        "$seasonIcon ${anilistSeasonLabel(selection.label)}"
                    }
                    Text(
                        text = displaySeason,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showYearDialog = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = effectiveYear.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(
                onClick = onNextSeason,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (!isViewingCurrent) {
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = onJumpToCurrent,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = stringResource(AMR.strings.seasonal_current),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showSortDialog = true },
                shape = RoundedCornerShape(8.dp),
                color = if (state.sort != 0 || state.sortAscending) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sortText = when (state.sort) {
                        1 -> stringResource(AMR.strings.seasonal_sort_label_score)
                        2 -> stringResource(AMR.strings.seasonal_sort_label_title)
                        3 -> stringResource(AMR.strings.seasonal_sort_label_airing)
                        else -> stringResource(AMR.strings.seasonal_sort_label_popularity)
                    }
                    val arrow = if (state.sortAscending) "↑" else "↓"
                    Text(
                        text = "$sortText $arrow",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.sort != 0 || state.sortAscending) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        // Row 2: Quick Filter Chips (Formats, Statuses, Active Genres, Clear All)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Quick Formats: ALL, TV, TV Short, Movie, Special, OVA, ONA
            ANILIST_FORMATS.forEachIndexed { index, formatKey ->
                val isSelected = (index == 0 && state.format <= 0) || (index == state.format)
                item(key = "format_$index") {
                    val label = if (index == 0) {
                        stringResource(AMR.strings.anilist_season_all)
                    } else {
                        anilistFormatLabel(formatKey)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (index == 0) {
                                onClearFormat()
                            } else if (state.format == index) {
                                onClearFormat()
                            } else {
                                onSelectFormat(index)
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp),
                    )
                }
            }

            // Divider
            item(key = "status_divider") {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }

            // Status items: Airing, Finished, Upcoming
            val statuses = listOf(
                1 to AMR.strings.seasonal_status_airing,
                2 to AMR.strings.seasonal_status_finished,
                3 to AMR.strings.seasonal_status_upcoming,
            )
            statuses.forEach { (statusCode, strRes) ->
                val isSelected = state.statusFilter == statusCode
                item(key = "status_$statusCode") {
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onClearStatus()
                            } else {
                                onSelectStatus(statusCode)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(strRes),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp),
                    )
                }
            }

            // Active Genres with 'X'
            state.selectedGenres.sorted().forEach { genre ->
                item(key = "genre_$genre") {
                    FilterChip(
                        selected = true,
                        onClick = { onClearGenre(genre) },
                        label = {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(AMR.strings.seasonal_clear_filter),
                                modifier = Modifier.size(12.dp),
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp),
                    )
                }
            }

            // Clear all if any non-default filter is active
            if (hasActiveFilters(state)) {
                item(key = "clear_all_chip") {
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(AMR.strings.seasonal_clear_all),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (showSeasonDialog) {
        SeasonalSeasonDialog(
            currentSeason = state.season,
            onSelectSeason = {
                onSelectSeason(it)
                showSeasonDialog = false
            },
            onDismissRequest = { showSeasonDialog = false },
        )
    }

    if (showYearDialog) {
        SeasonalYearDialog(
            currentYear = state.year,
            onSelectYear = {
                onSelectYear(it)
                showYearDialog = false
            },
            onDismissRequest = { showYearDialog = false },
        )
    }

    if (showSortDialog) {
        SeasonalSortDialog(
            currentSort = state.sort,
            isAscending = state.sortAscending,
            onSelectSort = {
                onSelectSort(it)
                showSortDialog = false
            },
            onToggleOrder = onToggleOrder,
            onDismissRequest = { showSortDialog = false },
        )
    }
}

@Composable
internal fun SeasonalYearDialog(
    currentYear: Int,
    onSelectYear: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val currentCalendarYear = java.time.LocalDate.now().year
    val years = remember {
        val list = mutableListOf<Int>()
        list.add(-1) // Sentinel for "Current"
        for (y in (currentCalendarYear + 1) downTo 1980) {
            list.add(y)
        }
        list
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AMR.strings.seasonal_bar_year),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(MR.strings.action_close),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                items(years) { y ->
                    val isSelected = (y == -1 && currentYear <= 0) || (y == currentYear)
                    val label = if (y == -1) {
                        "${stringResource(AMR.strings.seasonal_current)} ($currentCalendarYear)"
                    } else {
                        y.toString()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectYear(y) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectYear(y) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SeasonalSeasonDialog(
    currentSeason: Int,
    onSelectSeason: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val seasons = listOf(
        -1 to stringResource(AMR.strings.seasonal_current),
        0 to anilistSeasonLabel("WINTER"),
        1 to anilistSeasonLabel("SPRING"),
        2 to anilistSeasonLabel("SUMMER"),
        3 to anilistSeasonLabel("FALL"),
        SEASON_ALL to stringResource(AMR.strings.seasonal_all_seasons),
    )

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AMR.strings.seasonal_bar_season),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(MR.strings.action_close),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            seasons.forEach { (index, label) ->
                val isSelected = currentSeason == index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectSeason(index) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectSeason(index) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SeasonalSortDialog(
    currentSort: Int,
    isAscending: Boolean,
    onSelectSort: (Int) -> Unit,
    onToggleOrder: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sortOptions = listOf(
        0 to stringResource(AMR.strings.seasonal_sort_popularity),
        1 to stringResource(AMR.strings.seasonal_sort_score),
        2 to stringResource(AMR.strings.seasonal_sort_title),
        3 to stringResource(AMR.strings.seasonal_sort_airing),
    )

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AMR.strings.seasonal_bar_sort),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(MR.strings.action_close),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            sortOptions.forEach { (sortIndex, label) ->
                val isSelected = currentSort == sortIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectSort(sortIndex) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectSort(sortIndex) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleOrder)
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AMR.strings.seasonal_bar_order),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (isAscending) {
                        stringResource(AMR.strings.seasonal_order_asc)
                    } else {
                        stringResource(AMR.strings.seasonal_order_desc)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isAscending,
                    onCheckedChange = { onToggleOrder() },
                )
            }
        }
    }
}
