package eu.kanade.domain.track.store

import android.content.Context
import androidx.core.content.edit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class DelayedTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)

    fun add(trackId: Long, lastChapterRead: Double) {
        // Read via `all`: values written by older builds are Floats, and getString() would
        // throw on those. String doubles round-trip exactly, unlike the old Float cast.
        val previousLastChapterRead =
            preferences.all[trackId.toString()]?.toString()?.toDoubleOrNull() ?: 0.0
        if (lastChapterRead > previousLastChapterRead) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $trackId, last episode seen: $lastChapterRead" }
            preferences.edit {
                putString(trackId.toString(), lastChapterRead.toString())
            }
        }
    }

    fun remove(trackId: Long) {
        preferences.edit {
            remove(trackId.toString())
        }
    }

    fun getItems(): List<DelayedTrackingItem> {
        return preferences.all.mapNotNull {
            // Skip malformed keys/values instead of killing the whole drain with a throw.
            val trackId = it.key.toLongOrNull() ?: return@mapNotNull null
            val lastChapterRead = it.value.toString().toDoubleOrNull() ?: return@mapNotNull null
            DelayedTrackingItem(
                trackId = trackId,
                lastChapterRead = lastChapterRead,
            )
        }
    }

    data class DelayedTrackingItem(
        val trackId: Long,
        val lastChapterRead: Double,
    )
}
