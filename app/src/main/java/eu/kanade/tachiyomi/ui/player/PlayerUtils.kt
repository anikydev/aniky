/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal fun Uri.openContentFd(context: Context): String? {
    return context.contentResolver.openFileDescriptor(this, "r")?.detachFd()?.let {
        Utils.findRealPath(it)?.also { _ ->
            ParcelFileDescriptor.adoptFd(it).close()
        } ?: "fd://$it"
    }
}

internal fun Uri.resolveUri(context: Context): String? {
    val filepath = when (scheme) {
        "file" -> path
        "content" -> openContentFd(context)
        "data" -> "data://$schemeSpecificPart"
        in Utils.PROTOCOLS -> toString()
        else -> null
    }

    if (filepath == null) logcat(LogPriority.ERROR) { "unknown scheme: $scheme" }
    return filepath
}

internal fun Uri.getFileName(context: Context): String? {
    return context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        cursor.getString(nameIndex)
    }
}

inline fun <reified T> MPVNode.toObject(json: Json): T = json.decodeFromString<T>(toJson())

fun <T> Flow<T>.collectAsState(scope: CoroutineScope, initialValue: T? = null) =
    object : ReadOnlyProperty<Any?, T?> {
        private var value: T? = initialValue
        init {
            scope.launch { collect { value = it } }
        }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = value
    }

// ANK -->
/**
 * mpv property carrying the custom skip button's jump length, in seconds.
 *
 * The button's Lua scripts name this property as a string too -- see the seed in `custom_buttons.sq`
 * and the migrations that rewrite it -- so it cannot be renamed from Kotlin alone.
 */
internal const val SKIP_SECONDS_PROPERTY = "user-data/aniky/skip-seconds"

/**
 * Smallest jump length the button's script will act on, mirroring the `if n < 1 then n = 85 end`
 * guard in the seeded Lua. The picker's range and step live in the `aniyomi.int_picker(...)`
 * arguments inside `custom_buttons.sq`; only this floor is the app's.
 */
internal const val MIN_SKIP_SECONDS = 1

/**
 * The stored jump length, floored at [MIN_SKIP_SECONDS].
 */
internal fun eu.kanade.tachiyomi.ui.player.settings.GesturePreferences.skipSeconds(): Int =
    skipButtonSeconds().get().coerceAtLeast(MIN_SKIP_SECONDS)

/** What an observed [SKIP_SECONDS_PROPERTY] value means for the stored preference. */
internal sealed interface SkipSecondsWrite {
    /** The value already matches the preference; nothing to do. */
    data object Ignore : SkipSecondsWrite

    /** A value the script can use; store it. */
    data class Persist(val seconds: Int) : SkipSecondsWrite

    /** Nonsense the script cannot use; put [seconds] back into mpv and leave the preference alone. */
    data class Restore(val seconds: Int) : SkipSecondsWrite
}

/**
 * Pure decision behind [eu.kanade.tachiyomi.ui.player.PlayerViewModel.saveSkipButtonSeconds].
 *
 * An unusable [observed] value restores [stored] rather than a corrected version of itself, so a
 * script writing garbage into the property can never become the user's saved setting.
 */
internal fun resolveSkipSecondsWrite(observed: Int, stored: Int): SkipSecondsWrite = when {
    observed < MIN_SKIP_SECONDS -> SkipSecondsWrite.Restore(stored)
    observed == stored -> SkipSecondsWrite.Ignore
    else -> SkipSecondsWrite.Persist(observed)
}
// ANK <--
