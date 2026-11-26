package eu.kanade.tachiyomi.data.download.downloader

import java.net.URI

/** Resolves [relative] against [base] using RFC 3986 URI resolution. */
internal fun resolveUrl(base: String, relative: String): String =
    URI(base).resolve(relative).toString()
