package app.tropereader.data.backup

import app.tropereader.domain.BackupFormatException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupFileTest {
    private val file = BackupFile(
        format = BackupFile.FORMAT,
        exportedAt = 1_000,
        appVersion = "0.3.0",
        works = listOf(WorkRecord("w", "https://tvtropes.org/pmwiki/pmwiki.php/Series/BreakingBad", listOf("https://old"), "WORK", "Breaking Bad", "Series", 1, 2)),
        pages = listOf(PageRecord("p", "w", "Main", listOf("a", "b"), "MAIN", 0, false, 2)),
        readStates = listOf(ReadStateRecord("r", "p", "Main/AbortedArc", true, 3, 3)),
        positions = listOf(PositionRecord("pos", "p", "Main/AbortedArc", 4)),
        history = listOf(HistoryRecord("h", "p", "Main/AbortedArc", "Aborted Arc", 5, 5, deletedAt = 6)),
    )

    @Test
    fun `round trip keeps every record`() {
        assertEquals(file, BackupFile.decode(BackupFile.encode(file)))
    }

    @Test
    fun `fields added by a later version are ignored`() {
        val json = BackupFile.encode(file).replaceFirst("{", "{\"futureField\": [1, 2],")
        assertEquals(file, BackupFile.decode(json))
    }

    @Test
    fun `a newer format is refused with a clear message`() {
        val json = BackupFile.encode(file.copy(format = BackupFile.FORMAT + 1))
        val error = assertFailsWith<BackupFormatException> { BackupFile.decode(json) }
        assertEquals("This backup comes from a newer version of TropeReader. Update the app first.", error.message)
    }

    @Test
    fun `other files are refused`() {
        assertFailsWith<BackupFormatException> { BackupFile.decode("hello") }
        assertFailsWith<BackupFormatException> { BackupFile.decode("{\"name\": \"not a backup\"}") }
    }
}

class NewerThanLocalTest {
    private data class R(val id: String, val updatedAt: Long)

    private fun merge(local: List<R>, incoming: List<R>) = newerThanLocal(local, incoming, { it.id }, { it.updatedAt })

    @Test
    fun `new and more recent records win, older ones and ties do not`() {
        val local = listOf(R("a", 10), R("b", 10), R("c", 10))
        val incoming = listOf(R("a", 20), R("b", 5), R("c", 10), R("d", 1))
        assertEquals(listOf(R("a", 20), R("d", 1)), merge(local, incoming))
    }
}
