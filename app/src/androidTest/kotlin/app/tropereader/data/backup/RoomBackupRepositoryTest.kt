package app.tropereader.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tropereader.data.db.HistoryEntryEntity
import app.tropereader.data.db.ReadingPositionEntity
import app.tropereader.data.db.TropeReadStateEntity
import app.tropereader.data.db.TropeReaderDatabase
import app.tropereader.data.db.WorkEntity
import app.tropereader.data.db.WorkPageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Export from one phone, import on another: same state, merged by most recent change. */
@RunWith(AndroidJUnit4::class)
class RoomBackupRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val phoneA = Room.inMemoryDatabaseBuilder(context, TropeReaderDatabase::class.java).build()
    private val phoneB = Room.inMemoryDatabaseBuilder(context, TropeReaderDatabase::class.java).build()

    @After
    fun close() {
        phoneA.close()
        phoneB.close()
    }

    private fun repository(db: TropeReaderDatabase) = RoomBackupRepository(db, db.backupDao())

    private fun seed(db: TropeReaderDatabase, readAt: Long) = runBlocking {
        val dao = db.backupDao()
        dao.upsertWorks(listOf(WorkEntity("w", "https://tvtropes.org/pmwiki/pmwiki.php/Series/BreakingBad", "https://a\nhttps://b", "WORK", "Breaking Bad", "Series", 1, 1)))
        dao.upsertPages(listOf(WorkPageEntity("p", "w", "Main", "https://x\nhttps://y", "MAIN", 0, false, 1)))
        dao.upsertReadStates(listOf(TropeReadStateEntity("r1", "p", "Main/AbortedArc", true, readAt, readAt)))
        dao.upsertPositions(listOf(ReadingPositionEntity("pos", "p", "Main/AbortedArc", readAt)))
        dao.upsertHistory(listOf(HistoryEntryEntity("h1", "p", "Main/AbortedArc", "Aborted Arc", readAt, readAt)))
    }

    @Test
    fun exportThenImportOnAnEmptyPhoneGivesTheSameState() = runBlocking {
        seed(phoneA, readAt = 10)
        val summary = repository(phoneB).importJson(repository(phoneA).exportJson())
        assertEquals(5, summary.total)
        val a = phoneA.backupDao()
        val b = phoneB.backupDao()
        assertEquals(a.works(), b.works())
        assertEquals(a.pages(), b.pages())
        assertEquals(a.readStates(), b.readStates())
        assertEquals(a.positions(), b.positions())
        assertEquals(a.history(), b.history())
    }

    @Test
    fun importKeepsLocalChangesThatAreMoreRecent() = runBlocking {
        seed(phoneA, readAt = 10)
        seed(phoneB, readAt = 10)
        // Phone B unread the card later; phone A's older "read" must not overwrite it.
        phoneB.backupDao().upsertReadStates(listOf(TropeReadStateEntity("r1", "p", "Main/AbortedArc", false, null, 20)))
        val summary = repository(phoneB).importJson(repository(phoneA).exportJson())
        assertEquals(0, summary.total)
        assertEquals(false, phoneB.backupDao().readStates().single().isRead)
    }

    @Test
    fun importingTheSameBackupTwiceChangesNothing() = runBlocking {
        seed(phoneA, readAt = 10)
        val json = repository(phoneA).exportJson()
        repository(phoneB).importJson(json)
        assertEquals(0, repository(phoneB).importJson(json).total)
    }
}
