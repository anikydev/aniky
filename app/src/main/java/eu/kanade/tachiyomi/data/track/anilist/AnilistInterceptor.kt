package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.anilist.dto.ALOAuth
import eu.kanade.tachiyomi.data.track.anilist.dto.isExpired
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

// [token] is read on OkHttp threads in [intercept] and written from [setAuth] (login/logout), so it
// is @Volatile like [oauth]: without it a caller could briefly see a stale token after an account
// change.
class AnilistInterceptor(val anilist: Anilist, @Volatile private var token: String?) : Interceptor {

    /**
     * OAuth object used for authenticated requests.
     *
     * [AnilistApi.createOAuth] already stores the expiration date in milliseconds, so only the
     * 1 minute safety margin is applied here - converting again would push the expiry thousands of
     * years out and [isExpired] would never fire.
     */
    @Volatile
    private var oauth: ALOAuth? = null
        set(value) {
            field = value?.copy(expires = value.expires - 60 * 1000)
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (token.isNullOrEmpty()) {
            throw IOException("Not authenticated with Anilist")
        }
        if (oauth == null) {
            oauth = anilist.loadOAuth()
        }

        // Throw on null auth. Checked before the expiry so a missing/corrupt stored token surfaces
        // as an IOException instead of an NPE from inside the interceptor.
        val auth = oauth ?: throw IOException("No authentication token")

        // Log out if the access token expired.
        if (auth.isExpired()) {
            anilist.logout()
            throw IOException("Token expired")
        }

        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer ${auth.accessToken}")
            .header("User-Agent", "Aniky v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()

        return chain.proceed(authRequest)
    }

    /**
     * Called when the user authenticates with Anilist for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: ALOAuth?) {
        token = oauth?.accessToken
        this.oauth = oauth
        anilist.saveOAuth(oauth)
    }
}
