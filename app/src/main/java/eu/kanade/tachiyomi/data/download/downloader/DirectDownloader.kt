package eu.kanade.tachiyomi.data.download.downloader

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.get
import eu.kanade.tachiyomi.util.size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import okhttp3.Headers
import okhttp3.OkHttpClient
import okio.Throttler
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Downloads single-file MP4/MKV/subtitle/audio resources using multi-part HTTP Range requests
 * when supported, or single-connection streaming otherwise.
 */
class DirectDownloader(
    private val downloader: HttpDownloader,
) {
    fun parsePlaylist(url: String): PlaylistResult {
        return PlaylistResult.Url(url, emptyList(), emptyList())
    }

    suspend fun download(
        url: String,
        headers: Headers,
        client: OkHttpClient,
        progress: ItemProgress,
        throttler: Throttler?,
        threadCount: Int,
        destDir: UniFile,
        name: String,
        forceSingle: Boolean,
    ): String {
        var size = -1L
        var supportsRanges = false

        if (!forceSingle) {
            val probeHeaders = headers.newBuilder()
                .set("Range", "bytes=0-0")
                .build()
            try {
                client.get(url, probeHeaders).use {
                    supportsRanges = it.code == 206

                    val contentRange = it.header("Content-Range")
                    size = if (contentRange != null) {
                        contentRange.substringAfterLast("/").toLongOrNull() ?: -1L
                    } else {
                        it.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
        }

        return if (!forceSingle && size > 0 && supportsRanges && threadCount > 1) {
            val partSize = size / threadCount
            fun partLength(i: Int): Long {
                return if (i == threadCount - 1) {
                    size - i * partSize
                } else {
                    partSize
                }
            }

            val downloadedParts = AtomicLongArray(threadCount)
            (0 until threadCount).forEach {
                val partLen = partLength(it)
                val finished = destDir.findFile("$name-part$it")?.exists() == true
                val downloadedSize = if (finished) {
                    partLen
                } else {
                    destDir.findFile("$name-part$it.tmp")?.length() ?: 0L
                }

                downloadedParts.set(it, downloadedSize.coerceIn(0L, partLen))
            }

            fun report() {
                val downloaded = (0 until threadCount).sumOf { downloadedParts.get(it) }
                progress.report(downloaded.toFloat() / size)
            }
            report()

            coroutineScope {
                List(threadCount) { partIndex ->
                    async {
                        if (destDir.findFile("$name-part$partIndex")?.exists() == true) {
                            return@async
                        }

                        val job = currentCoroutineContext().job
                        val partLen = partLength(partIndex)

                        val listener = object : ProgressListener {
                            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                                job.ensureActive()
                                downloadedParts.accumulateAndGet(
                                    partIndex,
                                    bytesRead.coerceAtMost(partLen),
                                ) { a, b -> maxOf(a, b) }
                                report()
                            }
                        }

                        downloader.downloadFile(
                            url = url,
                            headersBuilder = { part ->
                                val start = partIndex * partSize + part.size()
                                val end = if (partIndex == threadCount - 1) {
                                    size - 1
                                } else {
                                    (partIndex + 1) * partSize - 1
                                }

                                headers.newBuilder()
                                    .set("Range", "bytes=$start-$end")
                                    .build()
                            },
                            client = client,
                            listener = listener,
                            throttler = throttler,
                            destDir = destDir,
                            fileName = "$name-part$partIndex",
                        )
                    }
                }.joinAll()
            }

            progress.report(1f)
            val parts = (0 until threadCount).joinToString("|") { "${destDir.filePath!!}/$name-part$it" }
            "-i \"concat:$parts\""
        } else {
            val job = currentCoroutineContext().job

            val file = downloader.downloadFile(
                url = url,
                headersBuilder = { headers },
                client = client,
                listener = object : ProgressListener {
                    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                        job.ensureActive()
                        if (contentLength > 0) {
                            val prog = (bytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            progress.report(prog)
                        }
                    }
                },
                throttler = throttler,
                destDir = destDir,
                fileName = name,
            )

            progress.report(1f)
            "-i \"${file.filePath!!}\""
        }
    }
}
