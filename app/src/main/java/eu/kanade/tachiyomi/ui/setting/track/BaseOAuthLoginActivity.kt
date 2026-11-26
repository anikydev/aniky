package eu.kanade.tachiyomi.ui.setting.track

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.injectLazy

abstract class BaseOAuthLoginActivity : BaseActivity() {

    internal val trackerManager: TrackerManager by injectLazy()

    abstract fun handleResult(uri: Uri)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setComposeContent {
            LoadingScreen()
        }

        // Only on a first create. The OAuth state is single-use - TrackLoginActivity.verifyState
        // clears it before comparing, so a replayed callback cannot reuse it - so handling the same
        // intent a second time fails verification and logs the user out of the account they just
        // approved. Recreation is otherwise mostly ruled out by the configChanges on this activity,
        // but process death can still bring it back with the original intent attached.
        //
        // Nothing is left to wait for in that case: the login this activity was showing a spinner for
        // died with the process. Leave rather than sit on LoadingScreen forever - whether it got far
        // enough to save a token is visible in the tracker's own login state.
        if (savedInstanceState == null) {
            val data = intent.data
            if (data == null) {
                returnToSettings()
            } else {
                handleResult(data)
            }
        } else {
            returnToSettings()
        }
    }

    internal fun returnToSettings() {
        finish()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}
