package eu.kanade.tachiyomi.data.track.anilist

/**
 * AniList answered HTTP 200 but the GraphQL body carried a top-level `errors` array, i.e. the
 * query or mutation was rejected (bad argument, invalid token scope, unknown field, ...). AniList
 * reports these as 200-with-errors rather than a 4xx, so the HTTP status check in the request path
 * passes them through; this exception lets callers treat a rejected write/read as the failure it
 * is instead of silently succeeding. [message] is the first server-provided error message.
 */
class AnilistGraphqlException(
    message: String,
) : IllegalStateException(message)
