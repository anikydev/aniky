package eu.kanade.tachiyomi.ui.anilist

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.anilist.AniListNotificationStore
import eu.kanade.tachiyomi.ui.anilist.browse.AdvancedSearchScreenModel
import eu.kanade.tachiyomi.ui.anilist.browse.DiscoverScreenModel
import eu.kanade.tachiyomi.ui.anilist.browse.discoverTab
import eu.kanade.tachiyomi.ui.anilist.browse.searchTab
import eu.kanade.tachiyomi.ui.anilist.detail.FavouritesScreenModel
import eu.kanade.tachiyomi.ui.anilist.detail.favouritesTab
import eu.kanade.tachiyomi.ui.anilist.mylists.MyListsScreenModel
import eu.kanade.tachiyomi.ui.anilist.mylists.myListsTab
import eu.kanade.tachiyomi.ui.browse.seasonal.SeasonalScreenModel
import eu.kanade.tachiyomi.ui.browse.seasonal.seasonalTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.notifications.AniListNotificationsScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object AniListTab : Tab {

    // ANK -->
    private const val DISCOVER_PAGE = 0
    private const val SEASONAL_PAGE = 1
    private const val LISTS_PAGE = 2
    private const val SEARCH_PAGE = 3
    private const val FAVOURITES_PAGE = 4
    // ANK <--

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 4u,
                title = stringResource(AMR.strings.label_anilist),
                icon = painterResource(R.drawable.ic_anilist),
            )
        }

    @Composable
    override fun Badge() {
        val store: AniListNotificationStore = remember { Injekt.get() }
        val unread by store.unreadCount.collectAsState(initial = 0)
        if (unread > 0) {
            Badge { Text(unread.toString()) }
        }
    }

    // ANK --> Reselecting the tab scrolls to top; the bell icon in the app bar already opens notifications.
    override suspend fun onReselect(navigator: Navigator) {
        AniListTabEvents.requestScrollToTop()
    }
    // ANK <--

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val trackPreferences: TrackPreferences = remember { Injekt.get() }
        val notificationStore: AniListNotificationStore = remember { Injekt.get() }
        val unreadCount by notificationStore.unreadCount.collectAsState(initial = 0)

        val seasonalScreenModel = rememberScreenModel { SeasonalScreenModel() }
        val searchScreenModel = rememberScreenModel { AdvancedSearchScreenModel() }
        val myListsScreenModel = rememberScreenModel { MyListsScreenModel() }
        val discoverScreenModel = rememberScreenModel { DiscoverScreenModel() }
        // Loads once on first open (loadIfNeeded); pull-to-refresh forces a re-fetch. Avoids
        // re-paging the whole favourites list from the network on every tab switch.
        val favouritesScreenModel = rememberScreenModel { FavouritesScreenModel() }

        // Every page gets the bottom-nav padding applied; without it the last row of the
        // grids/lists slides under the navigation bar and gets cut off.
        val tabs = persistentListOf(
            discoverTab(discoverScreenModel),
            seasonalTab(seasonalScreenModel),
            myListsTab(myListsScreenModel),
            searchTab(searchScreenModel),
            favouritesTab(favouritesScreenModel),
        )
        val initialPage = remember {
            trackPreferences.anilistLastTab().get().coerceIn(0, tabs.size - 1)
        }
        val state = rememberPagerState(initialPage = initialPage) { tabs.size }

        TabbedScreen(
            titleRes = AMR.strings.label_anilist,
            tabs = tabs,
            state = state,
            scrollable = true,
            extraActions = {
                IconButton(onClick = { navigator.push(AniListNotificationsScreen()) }) {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge { Text(unreadCount.toString()) }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = stringResource(AMR.strings.notifications_title),
                        )
                    }
                }
            },
        )

        LaunchedEffect(state.currentPage) {
            trackPreferences.anilistLastTab().set(state.currentPage)
            if (state.currentPage == SEARCH_PAGE) searchScreenModel.loadIfNeeded()
            if (state.currentPage == LISTS_PAGE) myListsScreenModel.loadIfNeeded()
            if (state.currentPage == SEASONAL_PAGE) seasonalScreenModel.loadIfNeeded()
            if (state.currentPage == DISCOVER_PAGE) discoverScreenModel.loadIfNeeded()
            if (state.currentPage == FAVOURITES_PAGE) favouritesScreenModel.loadIfNeeded()
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
