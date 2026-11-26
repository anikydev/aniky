package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.presentation.core.components.WheelTextPicker

@Composable
fun IntegerPickerDialog(
    defaultValue: Int,
    minValue: Int,
    maxValue: Int,
    step: Int,
    nameFormat: String,
    title: String,
    onChange: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // ANK -->
    val values = remember(minValue, maxValue, step) { (minValue..maxValue step step).toList() }
    val items = remember(minValue, maxValue, step, nameFormat) {
        values.map { String.format(nameFormat, it) }.toImmutableList()
    }
    val rawIndex = values.indexOfFirst { it == defaultValue }
    val startIndex = if (rawIndex >= 0) rawIndex else 0
    val initialValue = values.getOrElse(startIndex) { defaultValue }
    // The selection is only committed on dismiss, so it has to outlive recomposition: a plain local
    // would silently reset to defaultValue and throw away what was picked.
    var newValue by remember(defaultValue) { mutableIntStateOf(initialValue) }
    // ANK <--

    PlayerDialog(
        title = title,
        modifier = Modifier.fillMaxWidth(fraction = 0.5f),
        onConfirmRequest = null,
        onDismissRequest = {
            // ANK -->
            // Only commit a value the user actually moved to, compared against what the wheel started
            // on rather than the raw default. There is no confirm button, so committing
            // unconditionally would let a dialog that was merely opened overwrite the property.
            if (newValue != initialValue) onChange(newValue)
            // ANK <--
            onDismissRequest()
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            WheelTextPicker(
                modifier = Modifier.align(Alignment.Center),
                items = items,
                onSelectionChanged = { newValue = values[it] },
                startIndex = startIndex,
            )
        }
    }
}
