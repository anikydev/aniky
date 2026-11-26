package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.LogoHeader
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.GITHUB_REPO
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.updaterEnabled
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class AboutScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val handleBack = LocalBackPress.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var isCheckingUpdates by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_category_about),
                    navigateUp = if (handleBack != null) handleBack::invoke else null,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            ScrollbarLazyColumn(
                contentPadding = contentPadding,
            ) {
                item {
                    LogoHeader()
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.version),
                        subtitle = getVersionName(withBuildDate = true),
                        icon = Icons.Outlined.Info,
                        onPreferenceClick = {
                            val deviceInfo = CrashLogUtil(context).getDebugInfo()
                            context.copyToClipboard("Debug information", deviceInfo)
                        },
                    )
                }

                // ANK -->
                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.whats_new),
                        icon = Icons.Outlined.NewReleases,
                        onPreferenceClick = {
                            uriHandler.openUri(RELEASE_URL)
                        },
                    )
                }

                if (updaterEnabled) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.check_for_updates),
                            icon = Icons.Outlined.Refresh,
                            widget = if (isCheckingUpdates) {
                                {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            } else {
                                null
                            },
                            onPreferenceClick = {
                                if (isCheckingUpdates) return@TextPreferenceWidget
                                isCheckingUpdates = true
                                scope.launch {
                                    try {
                                        when (
                                            val result = AppUpdateChecker().checkForUpdate(
                                                context,
                                                forceCheck = true,
                                                autoUpdate = false,
                                            )
                                        ) {
                                            is GetApplicationRelease.Result.NewUpdate -> {
                                                val release = result.release
                                                withUIContext {
                                                    navigator.push(
                                                        NewUpdateScreen(
                                                            versionName = release.version,
                                                            changelogInfo = release.info,
                                                            releaseLink = release.releaseLink,
                                                            downloadLink = release.downloadLink,
                                                        ),
                                                    )
                                                }
                                            }
                                            is GetApplicationRelease.Result.NoNewUpdate -> {
                                                context.toast(MR.strings.update_check_no_new_updates)
                                            }
                                            is GetApplicationRelease.Result.OsTooOld -> {
                                                context.toast(MR.strings.update_check_eol)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        logcat(LogPriority.ERROR, e)
                                        context.toast(e.message)
                                    } finally {
                                        isCheckingUpdates = false
                                    }
                                }
                            },
                        )
                    }
                }

                item { HorizontalDivider() }

                item {
                    TextPreferenceWidget(
                        title = stringResource(AMR.strings.about_github),
                        subtitle = GITHUB_REPO,
                        icon = Icons.Outlined.Code,
                        onPreferenceClick = {
                            uriHandler.openUri("https://github.com/$GITHUB_REPO")
                        },
                    )
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(AMR.strings.about_report_issue),
                        icon = Icons.Outlined.BugReport,
                        onPreferenceClick = {
                            uriHandler.openUri("https://github.com/$GITHUB_REPO/issues")
                        },
                    )
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(AMR.strings.about_forked_from),
                        subtitle = stringResource(AMR.strings.about_forked_from_summary),
                        icon = Icons.Outlined.FavoriteBorder,
                        onPreferenceClick = {
                            uriHandler.openUri("https://github.com/komikku-app/anikku")
                        },
                    )
                }
                // ANK <--

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.licenses),
                        icon = Icons.Outlined.Description,
                        onPreferenceClick = { navigator.push(OpenSourceLicensesScreen()) },
                    )
                }
            }
        }
    }

    companion object {
        fun getVersionName(withBuildDate: Boolean): String {
            return when {
                isDebugBuildType -> {
                    "Debug ${BuildConfig.COMMIT_SHA}".let {
                        if (withBuildDate) {
                            "$it (${getFormattedBuildTime()})"
                        } else {
                            it
                        }
                    }
                }

                isPreviewBuildType -> {
                    "Beta r${BuildConfig.COMMIT_COUNT}".let {
                        if (withBuildDate) {
                            "$it (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
                        } else {
                            "$it (${BuildConfig.COMMIT_SHA})"
                        }
                    }
                }

                else -> {
                    "Stable ${BuildConfig.VERSION_NAME}".let {
                        if (withBuildDate) {
                            "$it (${getFormattedBuildTime()})"
                        } else {
                            it
                        }
                    }
                }
            }
        }

        internal fun getFormattedBuildTime(): String {
            return try {
                LocalDateTime.ofInstant(
                    Instant.parse(BuildConfig.BUILD_TIME),
                    ZoneId.systemDefault(),
                )
                    .toDateTimestampString(
                        UiPreferences.dateFormat(
                            Injekt.get<UiPreferences>().dateFormat().get(),
                        ),
                    )
            } catch (_: Exception) {
                BuildConfig.BUILD_TIME
            }
        }
    }
}
