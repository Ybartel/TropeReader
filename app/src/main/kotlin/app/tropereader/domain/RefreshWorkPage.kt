package app.tropereader.domain

import app.tropereader.parser.Card
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class RefreshResult(val page: LoadedPage, val changes: Reconciliation, val renamed: Boolean)

/**
 * Downloads a page again (F9.3) and keeps the reader's progress across site edits (F5.3):
 * cards with the same key keep their state, edited cards inherit it, new cards come in unread,
 * and the saved position follows its card. Follows renames (F11.3).
 */
class RefreshWorkPage @Inject constructor(
    private val library: LibraryRepository,
    private val pages: PageRepository,
    private val progress: ReadingProgressRepository,
) {
    /** True when a source of the page was never downloaded or is older than [MAX_AGE_MS]. */
    suspend fun isStale(pageId: String, now: Long = System.currentTimeMillis()): Boolean {
        val page = library.page(pageId) ?: return false
        return page.sourceUrls.any { url -> pages.fetchedAt(url)?.let { now - it > MAX_AGE_MS } ?: true }
    }

    /** @param oldCards the cards the reader had before. @throws PageLoadException */
    suspend operator fun invoke(pageId: String, oldCards: List<Card>, onPart: (part: Int, total: Int) -> Unit = { _, _ -> }): RefreshResult {
        val page = library.page(pageId) ?: throw PageLoadException("This page is no longer in the library.")
        var renamed = false
        val parsed = page.sourceUrls.mapIndexed { i, url ->
            onPart(i + 1, page.sourceUrls.size)
            pages.refresh(url).also { fresh ->
                if (TvTropesUrls.path(fresh.url) != TvTropesUrls.path(url)) {
                    library.recordRename(pageId, url, fresh.url)
                    renamed = true
                }
            }
        }
        val cards = LoadWorkPage.mergeCards(parsed)
        if (cards.isEmpty()) throw PageLoadException("This page has no cards.")
        library.recordCardCount(pageId, cards.size)

        val changes = Reconcile.of(oldCards, cards)
        val readKeys = progress.observeReadKeys(pageId).first()
        val carried = changes.moved.filterKeys { it in readKeys }.values
        if (carried.isNotEmpty()) progress.markAllRead(pageId, carried)
        progress.position(pageId)?.let { key -> changes.moved[key]?.let { progress.savePosition(pageId, it) } }

        val workTitle = library.work(page.workId)?.title ?: parsed.first().title
        val current = library.page(pageId) ?: page
        return RefreshResult(LoadedPage(current, LoadWorkPage.title(current, workTitle), cards), changes, renamed)
    }

    companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
