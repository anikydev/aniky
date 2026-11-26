package eu.kanade.tachiyomi.ui.browse.seasonal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Renders an AniList description/bio. AniList mixes a little HTML (`<br>`, `<i>`, `<b>`) with its own
 * spoiler markup (`~!…!~`). We turn `<br>` into real line breaks (otherwise paragraphs collapse into
 * a wall of text), decode the common entities, strip the remaining tags, and hide any spoiler spans
 * behind a toggle so they are not revealed until asked for.
 */
@Composable
fun AniListDescriptionText(
    raw: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var revealSpoilers by rememberSaveable(raw) { mutableStateOf(false) }
    val hasSpoiler = remember(raw) { raw.contains("~!") }
    val text = remember(raw, revealSpoilers) { processAniListDescription(raw, revealSpoilers) }

    Column(modifier = modifier) {
        Text(text = text, style = style, color = color)
        if (hasSpoiler) {
            Text(
                text = stringResource(if (revealSpoilers) AMR.strings.hide_spoiler else AMR.strings.show_spoiler),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { revealSpoilers = !revealSpoilers },
            )
        }
    }
}

private val BR_REGEX = Regex("(?i)<br\\s*/?>")
private val SPOILER_REGEX = Regex("~!(.*?)!~", setOf(RegexOption.DOT_MATCHES_ALL))
private val TAG_REGEX = Regex("<[^>]*>")
private val MANY_NEWLINES_REGEX = Regex("\\n{3,}")

private fun processAniListDescription(raw: String, revealSpoilers: Boolean): String {
    return raw
        .replace(BR_REGEX, "\n")
        .let { s -> SPOILER_REGEX.replace(s) { if (revealSpoilers) it.groupValues[1] else "" } }
        .replace(TAG_REGEX, "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&mdash;", "—")
        .replace("&hellip;", "…")
        .replace(MANY_NEWLINES_REGEX, "\n\n")
        .trim()
}
