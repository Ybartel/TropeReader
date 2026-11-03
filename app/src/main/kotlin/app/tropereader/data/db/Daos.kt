package app.tropereader.data.db

import androidx.room.Dao
import androidx.room.Embedded
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

/** A work with its aggregated progress (F1.3). */
data class WorkRow(
    @Embedded val work: WorkEntity,
    val pageCount: Int,
    val openedPageCount: Int,
    val cardCount: Int,
    val readCount: Int,
    val lastReadAt: Long?,
)

/** A page with its state (F2.2); [cardCount] is null until the page has been opened once. */
data class WorkPageRow(
    @Embedded val page: WorkPageEntity,
    val cardCount: Int?,
    val readCount: Int,
    val lastReadAt: Long?,
)

@Dao
interface LibraryDao {
    @Query(
        """
        SELECT w.*,
          (SELECT COUNT(*) FROM work_page p WHERE p.workId = w.id AND p.deletedAt IS NULL) AS pageCount,
          (SELECT COUNT(*) FROM page_summary s JOIN work_page p ON p.id = s.pageId
             WHERE p.workId = w.id AND p.deletedAt IS NULL) AS openedPageCount,
          (SELECT COALESCE(SUM(s.cardCount), 0) FROM page_summary s JOIN work_page p ON p.id = s.pageId
             WHERE p.workId = w.id AND p.deletedAt IS NULL) AS cardCount,
          (SELECT COUNT(*) FROM trope_read_state r JOIN work_page p ON p.id = r.pageId
             WHERE p.workId = w.id AND p.deletedAt IS NULL AND r.isRead = 1 AND r.deletedAt IS NULL) AS readCount,
          (SELECT MAX(rp.updatedAt) FROM reading_position rp JOIN work_page p ON p.id = rp.pageId
             WHERE p.workId = w.id AND rp.deletedAt IS NULL) AS lastReadAt
        FROM work w WHERE w.deletedAt IS NULL
        """,
    )
    fun observeWorks(): Flow<List<WorkRow>>

    @Query("SELECT * FROM work WHERE id = :workId AND deletedAt IS NULL")
    fun observeWork(workId: String): Flow<WorkEntity?>

    @Query(
        """
        SELECT p.*, s.cardCount AS cardCount,
          (SELECT COUNT(*) FROM trope_read_state r
             WHERE r.pageId = p.id AND r.isRead = 1 AND r.deletedAt IS NULL) AS readCount,
          (SELECT rp.updatedAt FROM reading_position rp WHERE rp.pageId = p.id AND rp.deletedAt IS NULL) AS lastReadAt
        FROM work_page p LEFT JOIN page_summary s ON s.pageId = p.id
        WHERE p.workId = :workId AND p.deletedAt IS NULL
        ORDER BY p.displayOrder
        """,
    )
    fun observePages(workId: String): Flow<List<WorkPageRow>>

    @Query("SELECT * FROM work WHERE id = :workId")
    suspend fun work(workId: String): WorkEntity?

    /** Matches the current URL or a former one (F11.3). */
    @Query("SELECT * FROM work WHERE canonicalUrl = :url OR (char(10) || aliasUrls || char(10)) LIKE '%' || char(10) || :url || char(10) || '%' LIMIT 1")
    suspend fun workByUrl(url: String): WorkEntity?

    @Query("SELECT * FROM work_page WHERE id = :pageId")
    suspend fun page(pageId: String): WorkPageEntity?

    @Upsert
    suspend fun upsertWork(work: WorkEntity)

    /** Keeps existing pages untouched (order, hidden flag). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPage(page: WorkPageEntity)

    @Query("UPDATE work_page SET sourceUrls = :sourceUrls, updatedAt = :now WHERE id = :pageId AND sourceUrls != :sourceUrls")
    suspend fun updatePageSources(pageId: String, sourceUrls: String, now: Long)

    @Query("UPDATE work SET deletedAt = :now, updatedAt = :now WHERE id = :workId")
    suspend fun softDeleteWork(workId: String, now: Long)

    @Upsert
    suspend fun upsertSummary(summary: PageSummaryEntity)
}

@Dao
interface CachedPageDao {
    @Query("SELECT * FROM cached_page WHERE url = :url")
    suspend fun get(url: String): CachedPageEntity?

    @Upsert
    suspend fun put(page: CachedPageEntity)
}
