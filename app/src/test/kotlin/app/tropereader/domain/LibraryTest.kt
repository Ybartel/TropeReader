package app.tropereader.domain

import app.tropereader.parser.Card
import app.tropereader.parser.CardKind
import app.tropereader.parser.PageLink
import app.tropereader.parser.PageType
import app.tropereader.parser.ParsedPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private const val BASE = "https://tvtropes.org/pmwiki/pmwiki.php/"

private fun link(label: String, path: String) = PageLink(label, path, BASE + path)

private fun card(key: String) =
    Card(key, key, 0, if (key == "intro") CardKind.INTRO else CardKind.ENTRY, null, null, null, key, null, null, emptyList())

private fun page(
    path: String,
    type: PageType = PageType.WORK,
    cards: List<String> = emptyList(),
    related: List<PageLink> = emptyList(),
    split: List<PageLink> = emptyList(),
) = ParsedPage(
    url = BASE + path,
    path = path,
    title = path.substringAfter('/'),
    namespace = path.substringBefore('/'),
    pageType = type,
    cards = cards.map(::card),
    relatedPages = related,
    splitParts = split,
    recapEpisodes = emptyList(),
)

private val BREAKING_BAD = page(
    "Series/BreakingBad",
    related = listOf(
        link("Main", "Series/BreakingBad"),
        link("Characters", "Characters/BreakingBad"),
        link("YMMV", "YMMV/BreakingBad"),
        link("Better Call Saul", "Series/BetterCallSaul"),
    ),
    split = listOf(link("Tropes A to B", "BreakingBad/TropesAToB"), link("Tropes C to D", "BreakingBad/TropesCToD")),
)

class TvTropesUrlsTest {
    @Test
    fun `finds and normalizes a page link in shared text`() {
        assertEquals(
            BASE + "Series/BreakingBad",
            TvTropesUrls.extract("Breaking Bad (Series) - TV Tropes http://m.tvtropes.org/pmwiki/pmwiki.php/Series/BreakingBad?from=x"),
        )
        assertEquals("Series/BreakingBad", TvTropesUrls.path("tvtropes.org/pmwiki/pmwiki.php/Series/BreakingBad"))
        assertNull(TvTropesUrls.extract("https://example.com/Series/BreakingBad"))
    }

    @Test
    fun `only wiki page links open in the app`() {
        assertEquals(BASE + "Main/AbortedArc", TvTropesUrls.wikiPage("https://tvtropes.org/pmwiki/pmwiki.php/Main/AbortedArc"))
        assertNull(TvTropesUrls.wikiPage("https://tvtropes.org/pmwiki/posts.php?discussion=1"))
        assertNull(TvTropesUrls.wikiPage("https://en.wikipedia.org/wiki/X?u=tvtropes.org/pmwiki/pmwiki.php/Main/A"))
    }
}

class WorkPlannerTest {
    @Test
    fun `main page and alphabetical parts become one page, then the work's subpages`() {
        val work = WorkPlanner.plan(BASE + "Series/BreakingBad", BREAKING_BAD)
        assertEquals(listOf("Main", "Characters", "YMMV"), work.pages.map { it.role })
        assertEquals(
            listOf(BASE + "Series/BreakingBad", BASE + "BreakingBad/TropesAToB", BASE + "BreakingBad/TropesCToD"),
            work.pages.first().sourceUrls,
        )
        assertNull(work.aliasUrl)
    }

    @Test
    fun `a renamed page keeps the requested url as alias`() {
        assertEquals(BASE + "Series/BrBa", WorkPlanner.plan(BASE + "Series/BrBa", BREAKING_BAD).aliasUrl)
    }
}

class MergeCardsTest {
    @Test
    fun `parts are concatenated, later intros dropped, repeated keys get the source path`() {
        val merged = LoadWorkPage.mergeCards(
            listOf(
                page("Series/BreakingBad", cards = listOf("intro", "x:quote")),
                page("BreakingBad/TropesAToB", cards = listOf("intro", "Main/AbortedArc", "x:quote")),
            ),
        )
        assertEquals(listOf("intro", "x:quote", "Main/AbortedArc", "x:quote@BreakingBad/TropesAToB"), merged.map { it.key })
        assertEquals(listOf(0, 1, 2, 3), merged.map { it.position })
    }
}

class WorkPageStateTest {
    private fun state(role: String, count: Int?, read: Int, last: Long?, hidden: Boolean = false) =
        WorkPageState(WorkPage(role, "w", role, emptyList(), hidden), count, read, last)

    @Test
    fun `status follows read cards`() {
        assertEquals(PageStatus.NOT_STARTED, state("a", null, 0, null).status)
        assertEquals(PageStatus.IN_PROGRESS, state("a", 10, 0, 5L).status)
        assertEquals(PageStatus.IN_PROGRESS, state("a", 10, 3, 5L).status)
        assertEquals(PageStatus.DONE, state("a", 10, 10, 5L).status)
    }

    @Test
    fun `resume goes to the most recently read page, else the first`() {
        val pages = listOf(state("Main", 10, 2, 100L), state("YMMV", 5, 1, 300L), state("Trivia", null, 0, null))
        assertEquals("YMMV", WorkPageState.resumeTarget(pages)!!.page.role)
        assertEquals("Trivia", WorkPageState.resumeTarget(listOf(state("Trivia", null, 0, null)))!!.page.role)
    }

    @Test
    fun `resume skips hidden pages`() {
        val pages = listOf(state("Main", 10, 2, 100L), state("YMMV", 5, 1, 300L, hidden = true))
        assertEquals("Main", WorkPageState.resumeTarget(pages)!!.page.role)
        assertNull(WorkPageState.resumeTarget(listOf(state("YMMV", 5, 1, 300L, hidden = true))))
    }

    @Test
    fun `moving a page swaps it with its neighbour, and stops at the ends`() {
        val pages = listOf(state("A", null, 0, null), state("B", null, 0, null), state("C", null, 0, null))
        assertEquals(listOf("B", "A", "C"), WorkPageState.moved(pages, "B", -1))
        assertEquals(listOf("A", "C", "B"), WorkPageState.moved(pages, "B", 1))
        assertEquals(listOf("A", "B", "C"), WorkPageState.moved(pages, "A", -1))
        assertEquals(listOf("A", "B", "C"), WorkPageState.moved(pages, "C", 1))
    }
}

class AddWorkTest {
    private class FakePages(private val pages: Map<String, ParsedPage>) : PageRepository {
        val loaded = mutableListOf<String>()

        override suspend fun load(url: String): ParsedPage {
            loaded += url
            return pages[url] ?: throw PageLoadException("404")
        }
    }

    private class FakeLibrary(private val known: Map<String, String> = emptyMap()) : LibraryRepository {
        val saved = mutableListOf<NewWork>()

        override suspend fun findWorkByUrl(url: String) = known[url]

        override suspend fun saveWork(work: NewWork): String {
            saved += work
            return Ids.work(work.path)
        }

        override fun observeWorks(): Flow<List<WorkSummary>> = emptyFlow()
        override fun observeWork(workId: String): Flow<Work?> = emptyFlow()
        override fun observePages(workId: String): Flow<List<WorkPageState>> = emptyFlow()
        override suspend fun deleteWork(workId: String) = Unit
        override suspend fun page(pageId: String): WorkPage? = null
        override suspend fun work(workId: String): Work? = null
        override suspend fun recordCardCount(pageId: String, cardCount: Int) = Unit
        override suspend fun setPageHidden(pageId: String, hidden: Boolean) = Unit
        override suspend fun reorderPages(orderedPageIds: List<String>) = Unit
    }

    private val ymmv = page("YMMV/BreakingBad", PageType.YMMV, related = listOf(link("Main", "Series/BreakingBad")))
    private val pages = FakePages(mapOf(BREAKING_BAD.url to BREAKING_BAD, ymmv.url to ymmv))

    @Test
    fun `a subpage link adds its work`() = runTest {
        val library = FakeLibrary()
        AddWork(pages, library)("Look at this: " + ymmv.url)
        assertEquals("Series/BreakingBad", library.saved.single().path)
    }

    @Test
    fun `a work already in the library is opened, not fetched again`() = runTest {
        val library = FakeLibrary(mapOf(BREAKING_BAD.url to "existing"))
        assertEquals("existing", AddWork(pages, library)(BREAKING_BAD.url))
        assertEquals(emptyList(), pages.loaded)
    }

    @Test
    fun `text without a link is rejected`() = runTest {
        assertFailsWith<PageLoadException> { AddWork(pages, FakeLibrary())("hello") }
    }
}
