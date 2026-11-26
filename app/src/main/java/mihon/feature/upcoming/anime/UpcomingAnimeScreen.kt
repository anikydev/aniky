package mihon.feature.upcoming.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalAnimeDetailScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen

class UpcomingAnimeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { UpcomingAnimeScreenModel() }
        val state by screenModel.state.collectAsState()

        // Keep the 60 s ticker running only while this screen is composed; the ScreenModel's
        // combine rebuilds countdowns/headers on each tick.
        DisposableEffect(Unit) {
            screenModel.setScreenVisible(true)
            onDispose { screenModel.setScreenVisible(false) }
        }

        UpcomingAnimeScreenContent(
            state = state,
            onClickUpcoming = { item, resolvedAnimeId ->
                if (resolvedAnimeId != null) {
                    navigator.push(MangaScreen(resolvedAnimeId))
                } else {
                    navigator.push(SeasonalAnimeDetailScreen(item.remoteId))
                }
            },
            onSetMode = screenModel::setMode,
            onSelectDate = screenModel::setSelectedDate,
            onRetry = screenModel::refresh,
            onForceRefresh = screenModel::forceRefresh,
        )
    }
}
