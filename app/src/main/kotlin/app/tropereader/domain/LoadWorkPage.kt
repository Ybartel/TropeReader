package app.tropereader.domain

import app.tropereader.parser.Card
import app.tropereader.parser.ParsedPage
import javax.inject.Inject

data class LoadedPage(val page: WorkPage, val title: String, val cards: List<Card>)

/** Loads a logical page: each source in order, merged into one card sequence (F2.5). */
class LoadWorkPage @Inject constructor(
    private val library: LibraryRepository,
    private val pages: PageRepository,
) {
    /** @param onPart called before each source is loaded, with its 1-based index and the total. */
    suspend operator fun invoke(pageId: String, onPart: (part: Int, total: Int) -> Unit = { _, _ -> }): LoadedPage {
        val page = library.page(pageId) ?: throw PageLoadException("This page is no longer in the library.")
        val work = library.work(page.workId)
        val parsed = page.sourceUrls.mapIndexed { i, url ->
            onPart(i + 1, page.sourceUrls.size)
            pages.load(url)
        }
        val cards = mergeCards(parsed)
        if (cards.isEmpty()) throw PageLoadException("This page has no cards.")
        library.recordCardCount(pageId, cards.size)
        val workTitle = work?.title ?: parsed.first().title
        val title = if (page.role == WorkPlanner.MAIN_ROLE) workTitle else "$workTitle · ${page.role}"
        return LoadedPage(page, title, cards)
    }

    companion object {
        /** Concatenates the sources' cards; a key already used by an earlier source gets `@<path>`. */
        fun mergeCards(parsed: List<ParsedPage>): List<Card> {
            val seen = mutableSetOf<String>()
            return parsed.flatMap { source ->
                source.cards.map { card ->
                    if (seen.add(card.key)) {
                        card
                    } else {
                        val key = "${card.key}@${source.path}"
                        seen += key
                        card.copy(key = key, parentKey = if (card.parentKey == card.key) key else card.parentKey)
                    }
                }
            }.mapIndexed { i, card -> card.copy(position = i) }
        }
    }
}
