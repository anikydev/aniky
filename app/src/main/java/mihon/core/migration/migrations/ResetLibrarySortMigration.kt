package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * Aniky is a flat, category-free anime library, so the effective library sort is read from the
 * system category's `flags`. That row is seeded with `flags = 0`, which decodes to
 * Alphabetical/Descending (Z→A). Force it — and the global sort preference — to
 * Alphabetical/Ascending (A→Z) once for existing installs. Fresh installs get the correct seed
 * directly from categories.sq.
 */
class ResetLibrarySortMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val key = Preference.appStateKey("reset_library_sort_to_ascending")
        val alreadyRun = preferenceStore.getBoolean(key, false)
        if (alreadyRun.get()) return false

        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return false
        val categoryRepository = migrationContext.get<CategoryRepository>() ?: return false

        val default = LibrarySort.default
        libraryPreferences.sortingMode().set(default)
        categoryRepository.updateAllFlags(default.type + default.direction)
        alreadyRun.set(true)
        return true
    }
}
