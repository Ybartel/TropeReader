package app.tropereader.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema version must migrate to the next with user state intact (F11.2). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TropeReaderDatabase::class.java)

    @Test
    fun migrate1To2KeepsProgress() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO work (id, canonicalUrl, aliasUrls, kind, title, mediaType, addedAt, updatedAt, deletedAt) " +
                    "VALUES ('w', 'https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/Portal2', '', 'WORK', 'Portal 2', 'VideoGame', 1, 1, NULL)",
            )
            execSQL(
                "INSERT INTO work_page (id, workId, role, sourceUrls, pageType, displayOrder, isHidden, updatedAt, deletedAt) " +
                    "VALUES ('p', 'w', 'Main', 'https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/Portal2', 'WORK', 0, 0, 1, NULL)",
            )
            execSQL("INSERT INTO trope_read_state VALUES ('r', 'p', 'Main/AbandonedArea', 1, 2, 2, NULL)")
            execSQL("INSERT INTO reading_position VALUES ('pos', 'p', 'Main/AccentAdaptation', 3, NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true)

        db.query("SELECT tropeKey FROM trope_read_state WHERE pageId = 'p' AND isRead = 1").use {
            it.moveToFirst()
            assertEquals("Main/AbandonedArea", it.getString(0))
        }
        db.query("SELECT tropeKey FROM reading_position WHERE pageId = 'p'").use {
            it.moveToFirst()
            assertEquals("Main/AccentAdaptation", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM page_summary").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
