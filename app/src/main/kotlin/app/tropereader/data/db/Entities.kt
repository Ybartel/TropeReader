package app.tropereader.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// User state: precious, synced one day. Deterministic ids (see domain.Ids), soft delete, last write wins on updatedAt.

@Entity(tableName = "work")
data class WorkEntity(
    @PrimaryKey val id: String,
    val canonicalUrl: String,
    /** Former URLs after renames, one per line (F11.3). */
    val aliasUrls: String,
    /** `WORK` or `TROPE` (general trope read like a work, F7.5). */
    val kind: String,
    val title: String,
    val mediaType: String,
    val addedAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "work_page", indices = [Index("workId")])
data class WorkPageEntity(
    @PrimaryKey val id: String,
    val workId: String,
    /** Logical role within the work: `Main`, `Tropes`, `YMMV`, `Recap/…`. */
    val role: String,
    /** Site pages making up this logical page, one URL per line (several for merged A-to-Z subpages, F2.5). */
    val sourceUrls: String,
    val pageType: String,
    val displayOrder: Int,
    val isHidden: Boolean,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "trope_read_state", indices = [Index(value = ["pageId", "tropeKey"], unique = true)])
data class TropeReadStateEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val tropeKey: String,
    val isRead: Boolean,
    val readAt: Long?,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "reading_position", indices = [Index(value = ["pageId"], unique = true)])
data class ReadingPositionEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val tropeKey: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

// Content: a disposable cache, rebuilt from the site.

@Entity(tableName = "cached_page")
data class CachedPageEntity(
    @PrimaryKey val url: String,
    val rawHtml: String,
    val fetchedAt: Long,
)
