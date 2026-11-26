package eu.kanade.tachiyomi.ui.anilist.detail

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import eu.kanade.tachiyomi.ui.anilist.AniListAddTarget
import eu.kanade.tachiyomi.ui.anilist.AniListGridCard
import eu.kanade.tachiyomi.ui.anilist.AniListInnerSpacer
import eu.kanade.tachiyomi.ui.anilist.AniListSectionHeader
import eu.kanade.tachiyomi.ui.anilist.AniListSectionSpacer
import eu.kanade.tachiyomi.ui.anilist.AniListSourceSearchSheet
import eu.kanade.tachiyomi.ui.anilist.anilistDisplayTitle
import eu.kanade.tachiyomi.ui.browse.seasonal.AniListDescriptionText
import eu.kanade.tachiyomi.ui.browse.seasonal.CharacterDetailScreen
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeCoverDialog
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen

class StaffDetailScreen(val staffId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val screenModel = rememberScreenModel { StaffDetailScreenModel(staffId) }
        val state by screenModel.state.collectAsState()

        val context = LocalContext.current
        var showFullscreenCover by remember { mutableStateOf(false) }
        var addTarget by remember { mutableStateOf<AniListAddTarget?>(null) }

        val scrollState = rememberScrollState()
        val collapseRangePx = with(LocalDensity.current) { 100.dp.toPx() }
        val toolbarAlpha by remember(collapseRangePx) {
            derivedStateOf { (scrollState.value / collapseRangePx).coerceIn(0f, 1f) }
        }
        val titleAlpha by remember(collapseRangePx) {
            derivedStateOf {
                ((scrollState.value - collapseRangePx * 0.5f) / (collapseRangePx * 0.5f)).coerceIn(0f, 1f)
            }
        }
        val headerTitle = state.details?.name?.full ?: state.details?.name?.userPreferred ?: ""

        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        AppBarTitle(
                            title = headerTitle,
                            modifier = Modifier.alpha(titleAlpha),
                        )
                    },
                    backgroundColor = MaterialTheme.colorScheme
                        .surfaceColorAtElevation(3.dp)
                        .copy(alpha = toolbarAlpha),
                    navigateUp = { handleBack?.invoke() ?: navigator.pop() },
                    actions = {
                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "https://anilist.co/staff/$staffId")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, headerTitle))
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
            when {
                state.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))
                state.error || state.details == null -> EmptyScreen(
                    modifier = Modifier.padding(contentPadding),
                    stringRes = AMR.strings.error,
                    actions = persistentListOf(
                        EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, screenModel::retry),
                    ),
                )
                else -> {
                    val d = state.details!!
                    val staffName = d.name?.full ?: d.name?.userPreferred ?: ""
                    val imgUrl = d.image?.large ?: d.image?.medium
                    // Explicit AniListSectionSpacer/AniListInnerSpacer (16/8) rather than a blanket
                    // spacedBy, matching the vertical rhythm of the anime/character detail screens.
                    Column(
                        modifier = Modifier.fillMaxSize().padding(contentPadding).verticalScroll(scrollState).padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimeCover.Square(
                                data = imgUrl,
                                contentDescription = staffName,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (!imgUrl.isNullOrBlank()) {
                                            showFullscreenCover = true
                                        }
                                    },
                                shape = CircleShape,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(staffName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                d.name?.native?.let {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                d.favourites?.takeIf { it > 0 }?.let { favCount ->
                                    Spacer(modifier = Modifier.height(4.dp))
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
                                val metaItems = buildList {
                                    if (d.primaryOccupations.isNotEmpty()) {
                                        add(d.primaryOccupations.take(3).joinToString(", "))
                                    }
                                    d.languageV2?.takeIf { it.isNotBlank() }?.let {
                                        add(it)
                                    }
                                }
                                if (metaItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        metaItems.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        d.description?.takeIf { it.isNotBlank() }?.let {
                            AniListSectionSpacer()
                            AniListDescriptionText(raw = it)
                        }
                        if (d.staffMedia?.edges?.isNotEmpty() == true) {
                            AniListSectionSpacer()
                            AniListSectionHeader(stringResource(AMR.strings.anilist_staff))
                            AniListInnerSpacer()
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Index-based keys: the same anime can appear twice with different roles.
                                itemsIndexed(d.staffMedia.edges) { _, edge ->
                                    val node = edge.node ?: return@itemsIndexed
                                    val title = anilistDisplayTitle(node.title.userPreferred, node.title.english, node.title.romaji, node.title.native)
                                    Column(modifier = Modifier.width(115.dp)) {
                                        AniListGridCard(
                                            item = node,
                                            onClick = { navigator.push(SeasonalAnimeDetailScreen(node.id)) },
                                            onAdd = {
                                                addTarget = AniListAddTarget(
                                                    mediaId = node.id,
                                                    title = title,
                                                    totalEpisodes = node.episodes,
                                                )
                                            },
                                        )
                                        edge.staffRole?.let { role ->
                                            Text(
                                                text = role,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (d.characterMedia?.edges?.isNotEmpty() == true) {
                            AniListSectionSpacer()
                            AniListSectionHeader(stringResource(AMR.strings.anilist_voice_roles))
                            AniListInnerSpacer()
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(d.characterMedia.edges) { _, edge ->
                                    val node = edge.node ?: return@itemsIndexed
                                    val title = anilistDisplayTitle(node.title.userPreferred, node.title.english, node.title.romaji, node.title.native)
                                    val char = edge.characters.firstOrNull()
                                    val charName = char?.name?.userPreferred ?: char?.name?.full.orEmpty()
                                    Column(modifier = Modifier.width(115.dp)) {
                                        AniListGridCard(
                                            item = node,
                                            onClick = { navigator.push(SeasonalAnimeDetailScreen(node.id)) },
                                            onAdd = {
                                                addTarget = AniListAddTarget(
                                                    mediaId = node.id,
                                                    title = title,
                                                    totalEpisodes = node.episodes,
                                                )
                                            },
                                        )
                                        if (char != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .clickable { navigator.push(CharacterDetailScreen(char.id)) }
                                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                            ) {
                                                AnimeCover.Square(
                                                    data = char.image?.large ?: char.image?.medium,
                                                    contentDescription = charName,
                                                    modifier = Modifier.size(20.dp),
                                                    shape = CircleShape,
                                                )
                                                Text(
                                                    text = charName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        } else if (edge.characterRole != null) {
                                            Text(
                                                text = edge.characterRole,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (showFullscreenCover && !imgUrl.isNullOrBlank()) {
                        SeasonalAnimeCoverDialog(
                            coverUrl = imgUrl,
                            bannerUrl = null,
                            title = staffName,
                            onDismissRequest = { showFullscreenCover = false },
                        )
                    }

                    addTarget?.let { target ->
                        AniListSourceSearchSheet(
                            target = target,
                            navigator = navigator,
                            onDismissRequest = { addTarget = null },
                        )
                    }
                }
            }
        }
    }
}
