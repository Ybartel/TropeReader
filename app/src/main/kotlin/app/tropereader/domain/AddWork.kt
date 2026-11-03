package app.tropereader.domain

import app.tropereader.parser.PageType
import app.tropereader.parser.ParsedPage
import javax.inject.Inject

object TvTropesUrls {
    private val PAGE = Regex(
        "(?:https?://)?(?:www\\.|m\\.)?tvtropes\\.org/pmwiki/pmwiki\\.php/([A-Za-z0-9]+)/([A-Za-z0-9]+)",
        RegexOption.IGNORE_CASE,
    )

    /** First TvTropes page link in [text] (a pasted URL, or shared text with a title around it), normalized. */
    fun extract(text: String): String? =
        PAGE.find(text)?.let { "https://tvtropes.org/pmwiki/pmwiki.php/${it.groupValues[1]}/${it.groupValues[2]}" }

    /** Wiki path (`Series/BreakingBad`) of a page link. */
    fun path(text: String): String? = PAGE.find(text)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
}

/** Decides which logical pages a work gets, from its main page (F2.1, F2.5). */
object WorkPlanner {
    const val MAIN_ROLE = "Main"

    fun plan(requestedUrl: String, main: ParsedPage): NewWork {
        val name = main.path.substringAfter('/')
        // Tropes: the main page (intro) followed by its alphabetical subpages, read as one page.
        val tropes = NewPage(MAIN_ROLE, listOf(main.url) + main.splitParts.map { it.url })
        val subpages = main.relatedPages
            .filter { it.path.substringAfter('/').equals(name, ignoreCase = true) }
            .filterNot { it.label.equals(MAIN_ROLE, ignoreCase = true) || it.path == main.path }
            .distinctBy { it.label }
            .map { NewPage(it.label, listOf(it.url)) }
        return NewWork(
            path = main.path,
            canonicalUrl = main.url,
            aliasUrl = requestedUrl.takeIf { it != main.url },
            isGeneralTrope = main.pageType == PageType.GENERAL_TROPE,
            title = main.title,
            mediaType = main.namespace,
            pages = listOf(tropes) + subpages,
        )
    }
}

/** Adds a work from a pasted or shared link (F1.1). A link to a subpage adds its work. */
class AddWork @Inject constructor(
    private val pages: PageRepository,
    private val library: LibraryRepository,
) {
    /** @return the work id. @throws PageLoadException */
    suspend operator fun invoke(text: String): String {
        val url = TvTropesUrls.extract(text) ?: throw PageLoadException("That doesn't look like a TvTropes page link.")
        library.findWorkByUrl(url)?.let { return it }
        var page = pages.load(url)
        if (page.pageType in SUBPAGE_TYPES) {
            page.relatedPages.firstOrNull { it.label.equals(WorkPlanner.MAIN_ROLE, ignoreCase = true) }?.let { main ->
                library.findWorkByUrl(main.url)?.let { return it }
                page = pages.load(main.url)
            }
        }
        return library.saveWork(WorkPlanner.plan(url, page))
    }

    private companion object {
        val SUBPAGE_TYPES = setOf(
            PageType.CHARACTERS, PageType.YMMV, PageType.TRIVIA, PageType.RECAP, PageType.RECAP_INDEX,
            PageType.OTHER_SUBPAGE, PageType.TROPES_PART,
        )
    }
}
