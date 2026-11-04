package app.tropereader.domain

import app.tropereader.parser.Card
import app.tropereader.parser.CardKind
import app.tropereader.parser.PageType
import app.tropereader.parser.ParsedPage
import app.tropereader.parser.TextSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SITE = "https://tvtropes.org/pmwiki/pmwiki.php/"

private fun example(key: String, parent: String, text: String) =
    Card(key, parent, 0, CardKind.EXAMPLE, parent, null, null, text, null, null, listOf(TextSegment(text, false)))

private fun entry(key: String, text: String) =
    Card(key, key, 0, CardKind.ENTRY, null, null, null, text, null, null, listOf(TextSegment(text, false)))

class ReconcileTest {
    private val old = listOf(
        entry("Main/AbandonedArea", "The house at the very end."),
        example("Main/AbortedArc#1", "Main/AbortedArc", "The kleptomania subplot is dropped after season one."),
        example("Main/AbortedArc#2", "Main/AbortedArc", "The robot arc ends abruptly with no explanation."),
        entry("Main/Gone", "This trope was cut from the page."),
    )

    @Test
    fun `same keys stay, edited examples move, new and removed cards are reported`() {
        val new = listOf(
            entry("Main/AbandonedArea", "The house at the very end, now with more detail."),
            example("Main/AbortedArc#9", "Main/AbortedArc", "The kleptomania subplot is quietly dropped after season one."),
            example("Main/AbortedArc#2", "Main/AbortedArc", "The robot arc ends abruptly with no explanation."),
            example("Main/AbortedArc#3", "Main/AbortedArc", "A brand new example about a different arc entirely."),
        )
        val changes = Reconcile.of(old, new)
        assertEquals(mapOf("Main/AbortedArc#1" to "Main/AbortedArc#9"), changes.moved)
        assertEquals(listOf("Main/AbortedArc#3"), changes.added.map { it.key })
        assertEquals(listOf("Main/Gone"), changes.removed.map { it.key })
    }

    @Test
    fun `examples never move to another trope`() {
        val new = listOf(example("Main/Other#1", "Main/Other", "The kleptomania subplot is dropped after season one."))
        assertTrue(Reconcile.of(old, new).moved.isEmpty())
    }
}

class RefreshWorkPageTest {
    private class Pages(var current: Map<String, ParsedPage>, private val fetched: Map<String, Long?> = emptyMap()) : PageRepository {
        override suspend fun load(url: String) = current.getValue(url)
        override suspend fun refresh(url: String) = current.getValue(url)
        override suspend fun fetchedAt(url: String) = fetched[url]
    }

    private class Library(private val page: WorkPage) : LibraryRepository {
        val renames = mutableListOf<Pair<String, String>>()
        override suspend fun page(pageId: String) = page
        override suspend fun work(workId: String) = Work("w", "Show", "Series", page.sourceUrls.first())
        override suspend fun recordRename(pageId: String, oldUrl: String, newUrl: String) {
            renames += oldUrl to newUrl
        }
        override suspend fun recordCardCount(pageId: String, cardCount: Int) = Unit
        override fun observeWorks(): Flow<List<WorkSummary>> = emptyFlow()
        override fun observeWork(workId: String): Flow<Work?> = emptyFlow()
        override fun observePages(workId: String): Flow<List<WorkPageState>> = emptyFlow()
        override suspend fun findWorkByUrl(url: String): String? = null
        override suspend fun saveWork(work: NewWork) = "w"
        override suspend fun deleteWork(workId: String) = Unit
        override suspend fun setPageHidden(pageId: String, hidden: Boolean) = Unit
        override suspend fun reorderPages(orderedPageIds: List<String>) = Unit
    }

    private class Progress(read: Set<String>, var position: String?) : ReadingProgressRepository {
        val read = MutableStateFlow(read)
        override fun observeReadKeys(pageId: String): Flow<Set<String>> = read
        override suspend fun setRead(pageId: String, tropeKey: String, isRead: Boolean) = Unit
        override suspend fun markAllRead(pageId: String, tropeKeys: Collection<String>) {
            read.value = read.value + tropeKeys
        }
        override suspend fun markAllUnread(pageId: String) = Unit
        override suspend fun position(pageId: String) = position
        override suspend fun savePosition(pageId: String, tropeKey: String) {
            position = tropeKey
        }
    }

    private fun parsed(path: String, cards: List<Card>) =
        ParsedPage(SITE + path, path, "Show", "Series", PageType.WORK, cards, emptyList(), emptyList(), emptyList())

    private val oldCards = listOf(
        example("Main/AbortedArc#1", "Main/AbortedArc", "The kleptomania subplot is dropped after season one."),
        entry("Main/Gone", "This trope was cut."),
    )
    private val newCards = listOf(
        example("Main/AbortedArc#9", "Main/AbortedArc", "The kleptomania subplot is quietly dropped after season one."),
        entry("Main/New", "A new trope."),
    )
    private val page = WorkPage("p", "w", "Main", listOf(SITE + "Series/Show"))

    @Test
    fun `read state and position follow edited cards`() = runTest {
        val progress = Progress(setOf("Main/AbortedArc#1"), position = "Main/AbortedArc#1")
        val pages = Pages(mapOf(SITE + "Series/Show" to parsed("Series/Show", newCards)))
        val result = RefreshWorkPage(Library(page), pages, progress)("p", oldCards)
        assertTrue("Main/AbortedArc#9" in progress.read.value)
        assertEquals("Main/AbortedArc#9", progress.position)
        assertEquals(listOf("Main/New"), result.changes.added.map { it.key })
        assertFalse(result.renamed)
    }

    @Test
    fun `a redirect to a new name is recorded`() = runTest {
        val library = Library(page)
        val pages = Pages(mapOf(SITE + "Series/Show" to parsed("Series/ShowRenamed", newCards)))
        val result = RefreshWorkPage(library, pages, Progress(emptySet(), null))("p", oldCards)
        assertTrue(result.renamed)
        assertEquals(listOf(SITE + "Series/Show" to SITE + "Series/ShowRenamed"), library.renames)
    }

    @Test
    fun `pages older than a week, or never downloaded, are stale`() = runTest {
        val now = 100L * 24 * 60 * 60 * 1000
        val day = 24L * 60 * 60 * 1000
        val refresh = { fetched: Long? -> RefreshWorkPage(Library(page), Pages(emptyMap(), mapOf(SITE + "Series/Show" to fetched)), Progress(emptySet(), null)) }
        assertFalse(refresh(now - 6 * day).isStale("p", now))
        assertTrue(refresh(now - 8 * day).isStale("p", now))
        assertTrue(refresh(null).isStale("p", now))
    }
}
