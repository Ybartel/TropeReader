package app.tropereader.data

import app.tropereader.data.db.HistoryDao
import app.tropereader.data.db.HistoryEntryEntity
import app.tropereader.domain.HistoryItem
import app.tropereader.domain.HistoryRepository
import app.tropereader.domain.NewHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class RoomHistoryRepository @Inject constructor(private val dao: HistoryDao) : HistoryRepository {
    override fun observe(): Flow<List<HistoryItem>> = dao.observe(LIMIT).map { rows ->
        rows.map {
            HistoryItem(it.entry.id, it.entry.pageId, it.entry.tropeKey, it.workTitle, it.pageRole, it.entry.label, it.entry.viewedAt)
        }
    }

    override suspend fun latest(): NewHistoryEntry? = dao.latest()?.let { NewHistoryEntry(it.pageId, it.tropeKey, it.label) }

    override suspend fun add(entry: NewHistoryEntry) {
        val now = System.currentTimeMillis()
        dao.insert(HistoryEntryEntity(UUID.randomUUID().toString(), entry.pageId, entry.tropeKey, entry.label, now, now))
    }

    override suspend fun clear() = dao.clear(System.currentTimeMillis())

    private companion object {
        /** The screen shows recent history; older entries stay stored. */
        const val LIMIT = 1_000
    }
}
