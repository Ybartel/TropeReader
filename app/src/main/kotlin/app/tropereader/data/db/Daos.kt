package app.tropereader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT tropeKey FROM trope_read_state WHERE pageId = :pageId AND isRead = 1 AND deletedAt IS NULL")
    fun observeReadKeys(pageId: String): Flow<List<String>>

    @Upsert
    suspend fun upsertReadState(state: TropeReadStateEntity)

    @Query("SELECT tropeKey FROM reading_position WHERE pageId = :pageId AND deletedAt IS NULL")
    suspend fun position(pageId: String): String?

    @Upsert
    suspend fun upsertPosition(position: ReadingPositionEntity)
}

@Dao
interface LibraryDao {
    /** Keeps an existing work untouched (its addedAt in particular). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWork(work: WorkEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPage(page: WorkPageEntity)
}

@Dao
interface CachedPageDao {
    @Query("SELECT * FROM cached_page WHERE url = :url")
    suspend fun get(url: String): CachedPageEntity?

    @Upsert
    suspend fun put(page: CachedPageEntity)
}
