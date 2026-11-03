package app.tropereader.domain

import app.tropereader.parser.Card
import app.tropereader.parser.CardKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun card(key: String, position: Int) =
    Card(key, key, position, CardKind.ENTRY, key, null, null, "<p>$key</p>", null, null, emptyList())

private val CARDS = listOf("a", "b", "c", "d").mapIndexed { i, key -> card(key, i) }
private const val PAGE = "page-1"

private class InMemoryProgress : ReadingProgressRepository {
    val read = MutableStateFlow(emptySet<String>())
    var savedPosition: String? = null

    override fun observeReadKeys(pageId: String): Flow<Set<String>> = read.map { it }

    override suspend fun setRead(pageId: String, tropeKey: String, isRead: Boolean) {
        read.value = if (isRead) read.value + tropeKey else read.value - tropeKey
    }

    override suspend fun markAllRead(pageId: String, tropeKeys: Collection<String>) {
        read.value = read.value + tropeKeys
    }

    override suspend fun markAllUnread(pageId: String) {
        read.value = emptySet()
    }

    override suspend fun position(pageId: String): String? = savedPosition

    override suspend fun savePosition(pageId: String, tropeKey: String) {
        savedPosition = tropeKey
    }
}

class ReadingCursorTest {
    @Test
    fun `resumes on the last viewed card`() {
        assertEquals(2, ReadingCursor.resume(CARDS, savedKey = "c", readKeys = setOf("a")).index)
    }

    @Test
    fun `falls back to the first unread card when the saved card is gone`() {
        assertEquals(2, ReadingCursor.resume(CARDS, savedKey = "removed", readKeys = setOf("a", "b")).index)
    }

    @Test
    fun `starts at the beginning when everything is read`() {
        assertEquals(0, ReadingCursor.resume(CARDS, savedKey = null, readKeys = CARDS.map { it.key }.toSet()).index)
    }

    @Test
    fun `stops at both ends`() {
        val first = ReadingCursor(CARDS, 0)
        assertNull(first.previous())
        assertNull(first.moveTo(3).next())
        assertEquals(3, first.moveTo(99).index)
    }
}

class ReadingActionsTest {
    private val progress = InMemoryProgress()
    private val advance = AdvanceToNextCard(progress)
    private val moveTo = MoveToCard(progress)

    @Test
    fun `advancing marks the current card read and saves the next position`() = runTest {
        val next = advance(PAGE, ReadingCursor(CARDS, 0))
        assertEquals(1, next.index)
        assertEquals(setOf("a"), progress.read.value)
        assertEquals("b", progress.savedPosition)
    }

    @Test
    fun `advancing on the last card marks it read and stays`() = runTest {
        val last = advance(PAGE, ReadingCursor(CARDS, 3))
        assertEquals(3, last.index)
        assertEquals(setOf("d"), progress.read.value)
    }

    @Test
    fun `moving back or jumping saves the position without marking anything`() = runTest {
        val back = moveTo(PAGE, ReadingCursor(CARDS, 2), 1)
        assertEquals("b", progress.savedPosition)
        moveTo(PAGE, back, 3)
        assertEquals("d", progress.savedPosition)
        assertEquals(emptySet(), progress.read.value)
    }

    @Test
    fun `reading then resuming lands on the same card`() = runTest {
        var cursor = ReadingCursor(CARDS, 0)
        cursor = advance(PAGE, cursor)
        cursor = advance(PAGE, cursor)
        val resumed = ReadingCursor.resume(CARDS, progress.position(PAGE), progress.read.value)
        assertEquals(cursor.index, resumed.index)
        assertEquals("c", resumed.current.key)
    }
}

class JumpTest {
    private val progress = InMemoryProgress()

    @Test
    fun `jumping one card ahead from the list does not mark the current card read`() = runTest {
        val moved = MoveToCard(progress)(PAGE, ReadingCursor(CARDS, 0), 1)
        assertEquals(1, moved.index)
        assertEquals(emptySet(), progress.read.value)
    }
}

class IdsTest {
    @Test
    fun `ids are deterministic across devices`() {
        val work = Ids.work("VideoGame/Portal2")
        assertEquals(work, Ids.work("videogame/portal2"))
        assertEquals(Ids.readState(Ids.page(work, "Main"), "Main/AbortedArc"), Ids.readState(Ids.page(work, "Main"), "Main/AbortedArc"))
    }
}
