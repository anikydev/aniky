package eu.kanade.presentation.browse

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.TabContent
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BrowseTabWrapper(tab: TabContent, onBackPressed: (() -> Unit)? = null) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = { scrollBehavior ->
            // ANK -->
            if (tab.searchEnabled) {
                SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(tab.titleRes)) },
                    searchEnabled = true,
                    searchQuery = tab.searchQuery,
                    onChangeSearchQuery = tab.onChangeSearchQuery ?: {},
                    actions = { AppBarActions(tab.actions) },
                    navigateUp = onBackPressed,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                AppBar(
                    title = stringResource(tab.titleRes),
                    actions = {
                        AppBarActions(tab.actions)
                    },
                    navigateUp = onBackPressed,
                    scrollBehavior = scrollBehavior,
                )
            }
            // ANK <--
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        tab.content(paddingValues, snackbarHostState)
    }
}
