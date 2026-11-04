package app.tropereader.data.backup

import androidx.room.withTransaction
import app.tropereader.BuildConfig
import app.tropereader.data.db.BackupDao
import app.tropereader.data.db.HistoryEntryEntity
import app.tropereader.data.db.ReadingPositionEntity
import app.tropereader.data.db.TropeReadStateEntity
import app.tropereader.data.db.TropeReaderDatabase
import app.tropereader.data.db.WorkEntity
import app.tropereader.data.db.WorkPageEntity
import app.tropereader.domain.BackupRepository
import app.tropereader.domain.ImportSummary
import javax.inject.Inject

class RoomBackupRepository @Inject constructor(
    private val db: TropeReaderDatabase,
    private val dao: BackupDao,
) : BackupRepository {
    override suspend fun exportJson(): String = db.withTransaction {
        BackupFile.encode(
            BackupFile(
                format = BackupFile.FORMAT,
                exportedAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                works = dao.works().map { it.toRecord() },
                pages = dao.pages().map { it.toRecord() },
                readStates = dao.readStates().map { it.toRecord() },
                positions = dao.positions().map { it.toRecord() },
                history = dao.history().map { it.toRecord() },
            ),
        )
    }

    /** All or nothing: a failure part-way leaves the local state untouched. */
    override suspend fun importJson(json: String): ImportSummary {
        val file = BackupFile.decode(json)
        return db.withTransaction {
            val works = newerThanLocal(dao.works().map { it.toRecord() }, file.works, { it.id }, { it.updatedAt })
            val pages = newerThanLocal(dao.pages().map { it.toRecord() }, file.pages, { it.id }, { it.updatedAt })
            val readStates = newerThanLocal(dao.readStates().map { it.toRecord() }, file.readStates, { it.id }, { it.updatedAt })
            val positions = newerThanLocal(dao.positions().map { it.toRecord() }, file.positions, { it.id }, { it.updatedAt })
            val history = newerThanLocal(dao.history().map { it.toRecord() }, file.history, { it.id }, { it.updatedAt })
            dao.upsertWorks(works.map { it.toEntity() })
            dao.upsertPages(pages.map { it.toEntity() })
            dao.upsertReadStates(readStates.map { it.toEntity() })
            dao.upsertPositions(positions.map { it.toEntity() })
            dao.upsertHistory(history.map { it.toEntity() })
            ImportSummary(works.size, pages.size, readStates.size, positions.size, history.size)
        }
    }
}

// Lists are stored one per line in the database, as JSON arrays in the file.
private fun String.splitLines(): List<String> = split('\n').filter { it.isNotBlank() }

private fun WorkEntity.toRecord() =
    WorkRecord(id, canonicalUrl, aliasUrls.splitLines(), kind, title, mediaType, addedAt, updatedAt, deletedAt)

private fun WorkRecord.toEntity() =
    WorkEntity(id, canonicalUrl, aliasUrls.joinToString("\n"), kind, title, mediaType, addedAt, updatedAt, deletedAt)

private fun WorkPageEntity.toRecord() =
    PageRecord(id, workId, role, sourceUrls.splitLines(), pageType, displayOrder, isHidden, updatedAt, deletedAt)

private fun PageRecord.toEntity() =
    WorkPageEntity(id, workId, role, sourceUrls.joinToString("\n"), pageType, displayOrder, isHidden, updatedAt, deletedAt)

private fun TropeReadStateEntity.toRecord() = ReadStateRecord(id, pageId, tropeKey, isRead, readAt, updatedAt, deletedAt)

private fun ReadStateRecord.toEntity() = TropeReadStateEntity(id, pageId, tropeKey, isRead, readAt, updatedAt, deletedAt)

private fun ReadingPositionEntity.toRecord() = PositionRecord(id, pageId, tropeKey, updatedAt, deletedAt)

private fun PositionRecord.toEntity() = ReadingPositionEntity(id, pageId, tropeKey, updatedAt, deletedAt)

private fun HistoryEntryEntity.toRecord() = HistoryRecord(id, pageId, tropeKey, label, viewedAt, updatedAt, deletedAt)

private fun HistoryRecord.toEntity() = HistoryEntryEntity(id, pageId, tropeKey, label, viewedAt, updatedAt, deletedAt)
