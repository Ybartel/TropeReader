package app.tropereader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema changes always come with a written, tested migration (F11.2): the app is sideloaded,
 * so a destructive migration would wipe reading progress with no way back.
 */
@Database(
    entities = [
        WorkEntity::class,
        WorkPageEntity::class,
        TropeReadStateEntity::class,
        ReadingPositionEntity::class,
        CachedPageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TropeReaderDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    abstract fun libraryDao(): LibraryDao

    abstract fun cachedPageDao(): CachedPageDao

    companion object {
        const val NAME = "tropereader.db"
    }
}
