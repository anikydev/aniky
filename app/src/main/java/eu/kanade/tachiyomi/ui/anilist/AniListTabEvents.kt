package eu.kanade.tachiyomi.ui.anilist

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// ANK -->
/**
 * Tapping the AniList bottom-nav icon while already on it scrolls the visible page back to the top
 * (the usual bottom-nav behaviour). The pages publish nothing; they just listen via
 * [ScrollToTopOnReselect].
 */
object AniListTabEvents {
    private val _scrollToTop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTop: SharedFlow<Unit> = _scrollToTop.asSharedFlow()

    fun requestScrollToTop() {
        _scrollToTop.tryEmit(Unit)
    }
}

@Composable
fun ScrollToTopOnReselect(state: ScrollState) {
    LaunchedEffect(state) {
        AniListTabEvents.scrollToTop.collect { state.animateScrollTo(0) }
    }
}

@Composable
fun ScrollToTopOnReselect(state: LazyListState) {
    LaunchedEffect(state) {
        AniListTabEvents.scrollToTop.collect { state.animateScrollToItem(0) }
    }
}

@Composable
fun ScrollToTopOnReselect(state: LazyGridState) {
    LaunchedEffect(state) {
        AniListTabEvents.scrollToTop.collect { state.animateScrollToItem(0) }
    }
}
// ANK <--
