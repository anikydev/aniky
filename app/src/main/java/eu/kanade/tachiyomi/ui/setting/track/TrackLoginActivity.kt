package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import uy.kohesive.injekt.injectLazy

class TrackLoginActivity : BaseOAuthLoginActivity() {

    private val trackPreferences: TrackPreferences by injectLazy()

    override fun handleResult(uri: Uri) {
        when (uri.host) {
            "anilist-auth" -> handleAnilist(uri)
            "myanimelist-auth" -> handleMyAnimeList(uri)
            else -> returnToSettings()
        }
    }

    private fun verifyState(state: String?): Boolean {
        val expected = trackPreferences.pendingOAuthState.get()
        trackPreferences.pendingOAuthState.set("")
        // An empty stored state means no login was started from the app, so an unsolicited callback
        // must be rejected instead of matching "" against "".
        if (expected.isEmpty()) return false
        return !state.isNullOrEmpty() && state == expected
    }

    private fun handleAnilist(data: Uri) {
        // The token may be the last fragment parameter (no trailing &), so match up to & or end.
        val fragment = data.fragment.orEmpty()
        val regex = "(?:access_token=)([^&]+)".toRegex()
        val matchResult = regex.find(fragment)
        val state = fragment.substringAfter("state=", "").substringBefore("&")
        if (!verifyState(state)) {
            failLogin()
            return
        }
        val token = matchResult?.groups?.get(1)?.value
        if (token != null) {
            lifecycleScope.launchIO {
                runCatching { trackerManager.aniList.login(token) }
                    .onFailure { failLogin(it.message) }
                    .onSuccess { returnToSettings() }
            }
        } else {
            failLogin()
        }
    }

    private fun handleMyAnimeList(data: Uri) {
        val code = data.getQueryParameter("code")
        if (!verifyState(data.getQueryParameter("state"))) {
            failLogin()
            return
        }
        if (code != null) {
            lifecycleScope.launchIO {
                runCatching { trackerManager.myAnimeList.login(code) }
                    .onFailure { failLogin(it.message) }
                    .onSuccess { returnToSettings() }
            }
        } else {
            failLogin()
        }
    }

    /**
     * A failed login now says so: every failure path used to land back in settings with no
     * message, indistinguishable from success.
     */
    private fun failLogin(reason: String? = null) {
        lifecycleScope.launchIO {
            withUIContext {
                if (reason != null) toast(reason) else toast(AMR.strings.login_failed)
            }
            returnToSettings()
        }
    }
}
