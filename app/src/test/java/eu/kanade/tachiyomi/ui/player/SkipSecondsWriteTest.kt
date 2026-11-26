package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkipSecondsWriteTest {

    @Test
    fun `a value matching the stored preference is ignored`() {
        assertEquals(SkipSecondsWrite.Ignore, resolveSkipSecondsWrite(observed = 85, stored = 85))
    }

    @Test
    fun `a usable new value is persisted`() {
        assertEquals(
            SkipSecondsWrite.Persist(120),
            resolveSkipSecondsWrite(observed = 120, stored = 85),
        )
    }

    @Test
    fun `an unusable value restores the stored preference instead of a corrected one`() {
        // The regression this guards: clamping and saving let a script writing 0 replace the amount
        // the user picked. The stored value has to come back out, not a repaired version of the 0.
        assertEquals(
            SkipSecondsWrite.Restore(85),
            resolveSkipSecondsWrite(observed = 0, stored = 85),
        )
        assertEquals(
            SkipSecondsWrite.Restore(85),
            resolveSkipSecondsWrite(observed = -30, stored = 85),
        )
    }

    @Test
    fun `values above the picker range are left to the picker, not rewritten here`() {
        // The range and step belong to the int_picker arguments in custom_buttons.sq. Duplicating
        // them here is what let the two drift, so anything usable is stored as-is.
        assertEquals(
            SkipSecondsWrite.Persist(600),
            resolveSkipSecondsWrite(observed = 600, stored = 85),
        )
    }

    @Test
    fun `the minimum usable value is the one the Lua guard accepts`() {
        assertEquals(MIN_SKIP_SECONDS, 1)
        assertEquals(
            SkipSecondsWrite.Persist(MIN_SKIP_SECONDS),
            resolveSkipSecondsWrite(observed = MIN_SKIP_SECONDS, stored = 85),
        )
    }
}
