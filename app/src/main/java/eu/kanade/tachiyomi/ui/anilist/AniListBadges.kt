package eu.kanade.tachiyomi.ui.anilist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource

// ANK -->
/**
 * One corner-radius scale for every AniList screen. Hand-picked radii (4/6/8/10/12/14/16/18/20) had
 * crept in; new AniList UI should pick from these instead.
 */
object AniListShapes {
    /** Small overlay badges (score, status, format). */
    val Badge = RoundedCornerShape(6.dp)

    /** Covers, grid/list cards, banners. */
    val Card = RoundedCornerShape(12.dp)

    /** Chips, pills and buttons. */
    val Pill = RoundedCornerShape(16.dp)

    /** Large hero surfaces. */
    val Hero = RoundedCornerShape(18.dp)
}

/** Gold used for score stars everywhere. */
val AniListStarColor = Color(0xFFFFB800)

/**
 * Score pill shared by the grid card, spotlight hero, list cards and detail header. Renders nothing
 * when there is no score. [tonal] swaps the translucent-black overlay look for a theme container
 * (used on plain surfaces such as the detail header, where there is no cover to float over).
 */
@Composable
fun AniListScoreBadge(
    score100: Number?,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
) {
    val text = anilistScoreText(score100)
    if (text.isEmpty()) return
    Surface(
        modifier = modifier,
        color = if (tonal) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.72f),
        contentColor = if (tonal) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
        shape = AniListShapes.Badge,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = AniListStarColor,
                modifier = Modifier.size(if (tonal) 13.dp else 11.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = text,
                style = if (tonal) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Colored-dot pill for the viewer's list status on a cover ("● Watching"). */
@Composable
fun AniListStatusBadge(
    status: String?,
    modifier: Modifier = Modifier,
) {
    if (status == null) return
    val color = anilistStatusColor(status)
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.75f),
        shape = AniListShapes.Badge,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = anilistStatusLabel(status),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

/** Plain translucent label pill (media format, rank, progress) for use over covers. */
@Composable
fun AniListInfoBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        shape = AniListShapes.Badge,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Round-cornered "in library" marker / shortcut. Pass [onClick] to make it a tappable shortcut
 * (the touch target is padded out to 40dp even though the visible badge stays compact).
 */
@Composable
fun AniListLibraryBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val badge: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = stringResource(MR.strings.in_library),
            modifier = Modifier.padding(4.dp),
        )
    }
    if (onClick != null) {
        Box(
            modifier = modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onClick,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = AniListShapes.Badge,
                modifier = Modifier.size(26.dp),
                content = badge,
            )
        }
    } else {
        Surface(
            modifier = modifier.size(22.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = AniListShapes.Badge,
            content = badge,
        )
    }
}

/** "Add to library" quick action over a cover; 26dp visible badge inside a 40dp touch target. */
@Composable
fun AniListAddBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(AMR.strings.seasonal_add),
) {
    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            color = Color.Black.copy(alpha = 0.68f),
            contentColor = Color.White,
            shape = AniListShapes.Badge,
            modifier = Modifier.size(26.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = contentDescription,
                modifier = Modifier.padding(5.dp),
            )
        }
    }
}
// ANK <--
