package eu.kanade.tachiyomi.data.track.anilist

/**
 * AniList answered HTTP 429 Too Many Requests. [retryAfterMs] is the server-provided delay from
 * the Retry-After header, or null when the header was absent (callers fall back to a default
 * cooldown). Callers treat this distinctly from other failures: the details cache serves whatever
 * it already has instead of letting the screen go blank.
 */
class AnilistRateLimitedException(
    val retryAfterMs: Long?,
) : IllegalStateException("AniList rate limited (HTTP 429)")
