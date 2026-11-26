package eu.kanade.tachiyomi.ui.anilist

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.util.pureBlackBorder

/**
 * One pill/chip for every AniList screen (genre chips on the detail screen, tag chips in the
 * detail extras, the Discover genre row). Centralising the shape, color and padding keeps them
 * identical - they were hand-built as an identical Surface+Text in each place. Use `FilterChip`
 * only for real toggles.
 */
@Composable
fun AniListChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = AniListShapes.Pill
    Surface(
        modifier = modifier.pureBlackBorder(shape),
        shape = shape,
        color = if (MaterialTheme.colorScheme.background == Color.Black) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
