package mihon.core.migration.migrations

import mihon.core.migration.Migration

val migrations: List<Migration>
    get() = listOf(
        SetupBackupCreateMigration(),
        SetupLibraryUpdateMigration(),
        SetupSyncDataMigration(),
        // KMK -->
        SetupAppUpdateMigration(),
        MergedMangaDedupeModeMigration(),
        // KMK <--
        PrefLangMigration(),
        // ANK -->
        EpisodeSortingFlagsMigration(),
        SyncPrefKeyMigration(),
        ResetLibrarySortMigration(),
        // ANK <--
        // AM -->
        SubtitleAssEnumMigration(),
        // <-- AM
    )
