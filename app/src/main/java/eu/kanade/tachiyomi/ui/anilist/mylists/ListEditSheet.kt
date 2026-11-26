package eu.kanade.tachiyomi.ui.anilist.mylists

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.AnimeCover
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALListStatus
import eu.kanade.tachiyomi.ui.anilist.anilistScoreText
import eu.kanade.tachiyomi.ui.anilist.anilistStatusColor
import eu.kanade.tachiyomi.ui.anilist.anilistStatusLabel
import eu.kanade.tachiyomi.ui.anilist.anilistTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListEditSheet(
    item: MyListUiItem,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val trackerManager: TrackerManager = remember { Injekt.get() }
    var status by remember { mutableStateOf(item.status) }
    var progress by remember { mutableIntStateOf(item.progress) }
    var score100 by remember { mutableFloatStateOf(item.score100.toFloat()) }
    var repeat by remember { mutableIntStateOf(item.repeat) }
    var notes by remember { mutableStateOf(item.notes ?: "") }
    var startedAtMillis by remember {
        mutableStateOf<Long?>(
            item.startedAt.takeIf { it.year != null }?.let {
                LocalDate.of(it.year!!, it.month ?: 1, it.day ?: 1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            },
        )
    }
    var completedAtMillis by remember {
        mutableStateOf<Long?>(
            item.completedAt.takeIf { it.year != null }?.let {
                LocalDate.of(it.year!!, it.month ?: 1, it.day ?: 1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            },
        )
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showCompletedDatePicker by remember { mutableStateOf(false) }

    val executeDelete: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            saving = true
            error = false
            try {
                withContext(NonCancellable) {
                    trackerManager.aniList.delete(
                        tachiyomi.domain.track.model.Track(
                            id = 0,
                            mangaId = 0,
                            trackerId = TrackerManager.ANILIST,
                            remoteId = item.mediaId,
                            libraryId = item.listId.takeIf { it != 0L },
                            title = item.title,
                            lastChapterRead = 0.0,
                            totalChapters = 0,
                            status = 0,
                            score = 0.0,
                            remoteUrl = "",
                            startDate = 0,
                            finishDate = 0,
                            private = false,
                        ),
                    )
                }
                trackerManager.aniList.invalidateDetailsCache(item.mediaId)
                saving = false
                withContext(Dispatchers.Main) {
                    onSaved()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saving = false
                error = true
            }
        }
    }

    // ANK -->
    AdaptiveSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ANK <--
            // Header Row: Anime Thumbnail + Title + Format
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!item.coverUrl.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .width(44.dp)
                            .height(62.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        AnimeCover.Book(
                            data = item.coverUrl,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = anilistTitle(item.title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        item.format.takeIf { it.isNotBlank() }?.replace("_", " "),
                        if (item.totalEpisodes > 0) "${item.totalEpisodes} eps" else null,
                    ).joinToString(" • ")
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 1-Tap Status Selector Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(AMR.strings.anilist_entry_status),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ALListStatus.ALL.forEach { s ->
                        val isSelected = status == s
                        val statusColor = anilistStatusColor(s)
                        FilterChip(
                            selected = isSelected,
                            onClick = { status = s },
                            label = {
                                Text(
                                    text = anilistStatusLabel(s),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(statusColor, CircleShape),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = statusColor.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) statusColor else MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = statusColor,
                            ),
                        )
                    }
                }
            }

            // Episode Progress with Tactile Steppers & Max Button
            val progressCap = if (item.totalEpisodes > 0) item.totalEpisodes else Int.MAX_VALUE
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AMR.strings.anilist_entry_progress),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (item.totalEpisodes > 0) {
                        Text(
                            text = "$progress / ${item.totalEpisodes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { if (progress > 0) progress-- },
                        enabled = progress > 0,
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "-1")
                    }
                    OutlinedTextField(
                        value = progress.toString(),
                        onValueChange = { progress = it.toIntOrNull()?.coerceIn(0, progressCap) ?: progress },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = if (item.totalEpisodes > 0) {
                            { Text("/ ${item.totalEpisodes}") }
                        } else {
                            null
                        },
                    )
                    FilledTonalIconButton(
                        onClick = { if (progress < progressCap) progress++ },
                        enabled = progress < progressCap,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "+1")
                    }
                    if (item.totalEpisodes > 0) {
                        FilledTonalButton(
                            onClick = { progress = item.totalEpisodes },
                            enabled = progress < item.totalEpisodes,
                        ) {
                            Text(stringResource(AMR.strings.anilist_max_episodes))
                        }
                    }
                }
            }

            // Auto-complete suggestion banner
            if (item.totalEpisodes > 0 && progress >= item.totalEpisodes && status != ALListStatus.COMPLETED) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(AMR.strings.anilist_all_episodes_reached),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(
                            onClick = {
                                status = ALListStatus.COMPLETED
                                if (completedAtMillis == null || completedAtMillis == 0L) {
                                    completedAtMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                                }
                            },
                        ) {
                            Text(stringResource(MR.strings.completed))
                        }
                    }
                }
            }

            // Score with steppers, quick 1-10 selector and slider
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(18.dp),
                        )
                        val scoreDisplay = anilistScoreText(score100.roundToInt())
                            .ifEmpty { stringResource(AMR.strings.anilist_score_unset) }
                        Text(
                            text = "${stringResource(AMR.strings.anilist_entry_score)}: $scoreDisplay",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { score100 = (score100 - 5f).coerceIn(0f, 100f) },
                            enabled = score100 > 0f,
                        ) {
                            Text("-0.5")
                        }
                        TextButton(
                            onClick = { score100 = (score100 + 5f).coerceIn(0f, 100f) },
                            enabled = score100 < 100f,
                        ) {
                            Text("+0.5")
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (0..10).forEach { scoreVal ->
                        val isSelected = if (scoreVal == 0) {
                            score100 == 0f
                        } else {
                            (score100 / 10).roundToInt() == scoreVal && score100 > 0f
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { score100 = (scoreVal * 10).toFloat() },
                            label = {
                                Text(
                                    text = if (scoreVal == 0) {
                                        "—"
                                    } else if (scoreVal == 10) {
                                        "10 ★"
                                    } else {
                                        "$scoreVal"
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }

                Slider(
                    value = score100,
                    onValueChange = { score100 = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Dates section
            val notSetText = stringResource(AMR.strings.anilist_date_not_set)
            val dateSetText = stringResource(AMR.strings.anilist_date_set)
            Text(
                text = stringResource(AMR.strings.anilist_tracking_dates),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            // Start Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(MR.strings.track_started_reading_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatDate(startedAtMillis, notSetText, dateSetText),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { startedAtMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(stringResource(MR.strings.relative_time_today), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { showStartDatePicker = true }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(AMR.strings.anilist_pick_start_date))
                    }
                    if (startedAtMillis != null && startedAtMillis != 0L) {
                        IconButton(onClick = { startedAtMillis = 0L }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(AMR.strings.anilist_clear_start_date))
                        }
                    }
                }
            }

            // Finish Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(MR.strings.track_finished_reading_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatDate(completedAtMillis, notSetText, dateSetText),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { completedAtMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(stringResource(MR.strings.relative_time_today), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { showCompletedDatePicker = true }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(AMR.strings.anilist_pick_finish_date))
                    }
                    if (completedAtMillis != null && completedAtMillis != 0L) {
                        IconButton(onClick = { completedAtMillis = 0L }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(AMR.strings.anilist_clear_finish_date))
                        }
                    }
                }
            }

            // Rewatches
            OutlinedTextField(
                value = repeat.toString(),
                onValueChange = { repeat = it.toIntOrNull()?.coerceAtLeast(0) ?: repeat },
                label = { Text(stringResource(AMR.strings.anilist_entry_repeat)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(AMR.strings.anilist_entry_notes)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            if (error) {
                Text(
                    stringResource(AMR.strings.anilist_lists_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.listId > 0L) {
                    TextButton(
                        enabled = !saving,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        onClick = { showDeleteConfirm = true },
                    ) {
                        Text(stringResource(AMR.strings.anilist_entry_delete))
                    }
                }
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            saving = true
                            error = false
                            try {
                                withContext(NonCancellable) {
                                    trackerManager.aniList.saveEntryFull(
                                        mediaId = item.mediaId,
                                        listId = item.listId.takeIf { it != 0L },
                                        status = status,
                                        progress = progress,
                                        scoreRaw100 = score100.roundToInt(),
                                        repeat = repeat,
                                        notes = when {
                                            notes.isNotBlank() -> notes
                                            !item.notes.isNullOrBlank() -> ""
                                            else -> null
                                        },
                                        startedAtMillis = startedAtMillis?.takeIf { it != 0L }?.let {
                                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                                                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        } ?: startedAtMillis,
                                        completedAtMillis = completedAtMillis?.takeIf { it != 0L }?.let {
                                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                                                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        } ?: completedAtMillis,
                                    )
                                }
                                trackerManager.aniList.invalidateDetailsCache(item.mediaId)
                                saving = false
                                withContext(Dispatchers.Main) {
                                    onSaved()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                saving = false
                                error = true
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(AMR.strings.anilist_entry_save))
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(AMR.strings.anilist_entry_delete)) },
            text = { Text(stringResource(AMR.strings.anilist_entry_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        executeDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startedAtMillis?.takeIf { it != 0L }
                ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startedAtMillis = datePickerState.selectedDateMillis
                        showStartDatePicker = false
                    },
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Finish Date Picker Dialog
    if (showCompletedDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = completedAtMillis?.takeIf { it != 0L }
                ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showCompletedDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        completedAtMillis = datePickerState.selectedDateMillis
                        showCompletedDatePicker = false
                    },
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompletedDatePicker = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatDate(epochMillis: Long?, notSetText: String, dateSetText: String): String {
    if (epochMillis == null || epochMillis == 0L) return notSetText
    return try {
        val instant = Instant.ofEpochMilli(epochMillis)
        val date = instant.atZone(ZoneOffset.UTC).toLocalDate()
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (_: Exception) {
        dateSetText
    }
}
