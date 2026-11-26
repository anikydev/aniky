package eu.kanade.tachiyomi.ui.browse.seasonal

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.manga.components.AnimeCover
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.anilist.dto.ALCharacterDetails
import eu.kanade.tachiyomi.data.track.anilist.dto.ALCharacterMediaEdge
import eu.kanade.tachiyomi.data.track.anilist.dto.ALFuzzyDate
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStaffNodeLite
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListGridCard
import eu.kanade.tachiyomi.ui.anilist.AniListInnerSpacer
import eu.kanade.tachiyomi.ui.anilist.AniListSectionHeader
import eu.kanade.tachiyomi.ui.anilist.AniListSectionSpacer
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.anilistCharacterRoleLabel
import eu.kanade.tachiyomi.ui.anilist.anilistDisplayTitle
import eu.kanade.tachiyomi.ui.anilist.detail.StaffDetailScreen
import eu.kanade.tachiyomi.ui.anilist.rememberAnilistLibraryLookup
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.pureBlackBorder
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

class CharacterDetailScreen(
    val characterId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val screenModel = rememberScreenModel { CharacterDetailScreenModel(characterId) }
        val state by screenModel.state.collectAsState()
        var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }

        if (state.isLoading) {
            Scaffold(
                topBar = {
                    AppBar(
                        title = "",
                        navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    )
                },
            ) { contentPadding ->
                LoadingScreen(modifier = Modifier.padding(contentPadding))
            }
            return
        }

        val character = state.character
        if (character == null) {
            Scaffold(
                topBar = {
                    AppBar(
                        title = "",
                        navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    )
                },
            ) { contentPadding ->
                EmptyScreen(
                    modifier = Modifier.padding(contentPadding),
                    stringRes = AMR.strings.error,
                    actions = persistentListOf(
                        EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::retry),
                    ),
                )
            }
            return
        }

        CharacterDetailContent(
            character = character,
            navigateUp = { handleBack?.invoke() ?: navigator.pop() },
            // Only anime resolve on the AniList detail screen; manga/other appearances are display-only.
            onAnimeClick = { mediaId, libraryId ->
                if (libraryId != null) {
                    navigator.push(MangaScreen(libraryId))
                } else {
                    navigator.push(SeasonalAnimeDetailScreen(mediaId))
                }
            },
            onStaffClick = { navigator.push(StaffDetailScreen(it)) },
            onAddToLibrary = { addTarget = it },
        )

        addTarget?.let { target ->
            AniListSourceSearchSheet(
                target = target,
                navigator = navigator,
                onDismissRequest = { addTarget = null },
            )
        }
    }
}

@Composable
private fun CharacterDetailContent(
    character: ALCharacterDetails,
    navigateUp: () -> Unit,
    onAnimeClick: (mediaId: Long, libraryId: Long?) -> Unit,
    onStaffClick: (Long) -> Unit,
    onAddToLibrary: (AniListAddTarget) -> Unit,
) {
    val name = character.name.userPreferred ?: character.name.native.orEmpty()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val copiedMessage = stringResource(MR.strings.copied_to_clipboard, name)
    var showFullscreenCover by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val collapseRangePx = with(LocalDensity.current) { 120.dp.toPx() }
    val toolbarAlpha by remember(collapseRangePx) {
        derivedStateOf { (scrollState.value / collapseRangePx).coerceIn(0f, 1f) }
    }
    val titleAlpha by remember(collapseRangePx) {
        derivedStateOf {
            ((scrollState.value - collapseRangePx * 0.5f) / (collapseRangePx * 0.5f)).coerceIn(0f, 1f)
        }
    }

    // Sort voice actors: Japanese seiyuu first, followed by English dub, then others.
    val voiceActors = remember(character) {
        character.media?.edges
            .orEmpty()
            .flatMap { it.voiceActors }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<ALStaffNodeLite> { it.languageV2.equals("Japanese", ignoreCase = true) }
                    .thenByDescending { it.languageV2.equals("English", ignoreCase = true) }
                    .thenBy { it.name?.userPreferred ?: it.name?.full.orEmpty() },
            )
    }

    Scaffold(
        topBar = {
            AppBar(
                titleContent = {
                    AppBarTitle(
                        title = name,
                        modifier = Modifier.alpha(titleAlpha),
                    )
                },
                backgroundColor = MaterialTheme.colorScheme
                    .surfaceColorAtElevation(3.dp)
                    .copy(alpha = toolbarAlpha),
                navigateUp = navigateUp,
                actions = {
                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "https://anilist.co/character/${character.id}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, name))
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(MR.strings.action_share),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            // Header: portrait + names + favorites badge
            Row {
                val coverUrl = character.image?.large
                AnimeCover.Book(
                    data = coverUrl,
                    contentDescription = name,
                    modifier = Modifier
                        .width(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (!coverUrl.isNullOrBlank()) {
                                showFullscreenCover = true
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(
                        modifier = Modifier.clickable {
                            @Suppress("DEPRECATION")
                            clipboardManager.setText(AnnotatedString(name))
                            context.toast(copiedMessage)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(MR.strings.action_copy_to_clipboard),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    character.name.native?.takeIf { it.isNotBlank() && it != name }?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    character.favourites?.takeIf { it > 0 }?.let { favCount ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "%,d".format(favCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                    val alternatives = character.name.alternative.filter { it.isNotBlank() }
                    if (alternatives.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = alternatives.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Attributes (only the ones AniList actually returned)
            val birthday = formatBirthday(character.dateOfBirth)
            val genderLabel = stringResource(AMR.strings.character_gender)
            val ageLabel = stringResource(AMR.strings.character_age)
            val birthdayLabel = stringResource(AMR.strings.character_birthday)
            val bloodTypeLabel = stringResource(AMR.strings.character_blood_type)
            val attributes = buildList {
                character.gender?.takeIf { it.isNotBlank() }?.let { add(genderLabel to it) }
                character.age?.takeIf { it.isNotBlank() }?.let { add(ageLabel to it) }
                birthday?.let { add(birthdayLabel to it) }
                character.bloodType?.takeIf { it.isNotBlank() }?.let { add(bloodTypeLabel to it) }
            }
            if (attributes.isNotEmpty()) {
                AniListSectionSpacer()
                attributes.forEach { (label, value) -> InfoRow(label = label, value = value) }
            }

            // Bio
            val bio = character.description
            if (!bio.isNullOrBlank()) {
                AniListSectionSpacer()
                AniListSectionHeader(title = stringResource(AMR.strings.description))
                AniListInnerSpacer()
                AniListDescriptionText(raw = bio)
            }

            // Voice Actors
            if (voiceActors.isNotEmpty()) {
                AniListSectionSpacer()
                AniListSectionHeader(title = stringResource(AMR.strings.anilist_voice_actors))
                AniListInnerSpacer()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(voiceActors) { va ->
                        VoiceActorCard(va = va, onStaffClick = onStaffClick)
                    }
                }
            }

            // Appears in / Filmography
            val media = character.media?.edges.orEmpty()
            if (media.isNotEmpty()) {
                AniListSectionSpacer()
                AniListSectionHeader(title = stringResource(AMR.strings.anilist_filmography))
                AniListInnerSpacer()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(media) { edge ->
                        MediaAppearanceCard(
                            edge = edge,
                            onAnimeClick = onAnimeClick,
                            onAdd = onAddToLibrary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showFullscreenCover && !character.image?.large.isNullOrBlank()) {
            SeasonalAnimeCoverDialog(
                coverUrl = character.image.large,
                bannerUrl = null,
                title = name,
                onDismissRequest = { showFullscreenCover = false },
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VoiceActorCard(
    va: ALStaffNodeLite,
    onStaffClick: (Long) -> Unit,
) {
    val vaName = va.name?.userPreferred ?: va.name?.full.orEmpty()
    Surface(
        onClick = { onStaffClick(va.id) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(96.dp)
            .pureBlackBorder(RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimeCover.Square(
                data = va.image?.large ?: va.image?.medium,
                contentDescription = vaName,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = vaName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            va.languageV2?.takeIf { it.isNotBlank() }?.let { lang ->
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = lang,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaAppearanceCard(
    edge: ALCharacterMediaEdge,
    onAnimeClick: (mediaId: Long, libraryId: Long?) -> Unit,
    onAdd: (AniListAddTarget) -> Unit,
) {
    val node = edge.node
    val title = anilistDisplayTitle(node.title.userPreferred.orEmpty(), node.title.english, node.title.romaji, node.title.native)
    val isAnime = node.type == null || node.type.equals("ANIME", ignoreCase = true)
    val libraryLookup = rememberAnilistLibraryLookup()
    val libraryAnimeId = libraryLookup.findAnimeId(
        node.id,
        node.title.userPreferred,
        node.title.english,
        node.title.romaji,
        node.title.native,
    )
    val primaryVa = edge.voiceActors.firstOrNull { it.languageV2.equals("Japanese", ignoreCase = true) }
        ?: edge.voiceActors.firstOrNull()

    Column(modifier = Modifier.width(115.dp)) {
        AniListGridCard(
            title = title,
            coverUrl = node.coverImage?.large ?: node.coverImage?.extraLarge,
            score = node.averageScore,
            format = node.format,
            episodes = node.episodes,
            inLibrary = libraryAnimeId != null,
            libraryAnimeId = libraryAnimeId,
            mediaId = node.id,
            resolveFromLibrary = false,
            onClick = {
                if (isAnime) {
                    onAnimeClick(node.id, libraryAnimeId)
                }
            },
            onAdd = if (isAnime) {
                {
                    onAdd(
                        AniListAddTarget(
                            mediaId = node.id,
                            title = title,
                            totalEpisodes = node.episodes,
                        ),
                    )
                }
            } else {
                null
            },
        )
        edge.characterRole?.let { role ->
            Text(
                text = anilistCharacterRoleLabel(role),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        if (primaryVa != null) {
            val vaName = primaryVa.name?.userPreferred ?: primaryVa.name?.full.orEmpty()
            val vaLabel = if (!primaryVa.languageV2.isNullOrBlank()) {
                "${primaryVa.languageV2}: $vaName"
            } else {
                stringResource(AMR.strings.anilist_va_prefix, vaName)
            }
            Text(
                text = vaLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** "September 6" / "September 6, 1990" from an AniList fuzzy date; null when there is no month/day. */
private fun formatBirthday(date: ALFuzzyDate): String? {
    // Guard the numeric ranges: AniList fuzzy dates can carry an out-of-range month/day, and
    // Month.of() throws DateTimeException on anything outside 1..12, crashing the screen.
    val month = date.month?.takeIf { it in 1..12 } ?: return null
    val day = date.day?.takeIf { it in 1..31 } ?: return null
    val monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$monthName $day" + (date.year?.let { ", $it" } ?: "")
}
