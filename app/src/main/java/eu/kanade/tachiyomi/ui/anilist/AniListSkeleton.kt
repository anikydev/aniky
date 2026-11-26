package eu.kanade.tachiyomi.ui.anilist

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ANK -->
/** Pulsing placeholder color shared by every AniList skeleton so loading states look identical. */
@Composable
fun rememberAniListSkeletonColor(): Color {
    val transition = rememberInfiniteTransition(label = "anilist_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "anilist_skeleton_alpha",
    )
    return MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
}

@Composable
private fun SkeletonBlock(
    color: Color,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color),
    )
}

/** Placeholder with the same silhouette as [AniListGridCard] (2:3 cover + two text lines). */
@Composable
fun AniListGridCardSkeleton(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SkeletonBlock(
            color = color,
            shape = AniListShapes.Card,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBlock(color = color, modifier = Modifier.fillMaxWidth(0.9f).height(12.dp))
        Spacer(modifier = Modifier.height(6.dp))
        SkeletonBlock(color = color, modifier = Modifier.fillMaxWidth(0.55f).height(10.dp))
    }
}

/** Loading placeholder for any AniList poster grid (Lists, Favourites, Search). */
@Composable
fun AniListGridSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 12,
) {
    val color = rememberAniListSkeletonColor()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 105.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(count) {
            AniListGridCardSkeleton(color = color)
        }
    }
}

/** Loading placeholder for AniList row lists (My Lists in list mode). */
@Composable
fun AniListRowSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 7,
) {
    val color = rememberAniListSkeletonColor()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(count) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AniListShapes.Card)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(
                    color = color,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .width(52.dp)
                        .aspectRatio(2f / 3f),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    SkeletonBlock(color = color, modifier = Modifier.fillMaxWidth(0.8f).height(14.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonBlock(color = color, modifier = Modifier.fillMaxWidth(0.5f).height(10.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    SkeletonBlock(color = color, modifier = Modifier.fillMaxWidth().height(5.dp))
                }
            }
        }
    }
}

/** Loading placeholder for the anime detail screen: banner, cover + title block, action row. */
@Composable
fun AniListDetailSkeleton(
    modifier: Modifier = Modifier,
) {
    val color = rememberAniListSkeletonColor()
    Column(modifier = modifier.fillMaxSize()) {
        SkeletonBlock(
            color = color,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkeletonBlock(
                color = color,
                shape = AniListShapes.Card,
                modifier = Modifier
                    .width(105.dp)
                    .aspectRatio(2f / 3f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBlock(color = color, modifier = Modifier.fillMaxWidth().height(22.dp))
                SkeletonBlock(color = color, modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
                SkeletonBlock(color = color, modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        SkeletonBlock(
            color = color,
            shape = AniListShapes.Pill,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(44.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        repeat(4) {
            SkeletonBlock(
                color = color,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(if (it == 3) 0.6f else 1f)
                    .height(12.dp),
            )
        }
    }
}
// ANK <--
