package app.tropereader.domain

import app.tropereader.parser.ParsedPage
import kotlinx.coroutines.flow.Flow

/** Read state and position, keyed by stable card keys. The UI never knows where it is stored. */
interface ReadingProgressRepository {
    fun observeReadKeys(pageId: String): Flow<Set<String>>

    suspend fun setRead(pageId: String, tropeKey: String, isRead: Boolean)

    /** Marks all [tropeKeys] read at once (F4.6). */
    suspend fun markAllRead(pageId: String, tropeKeys: Collection<String>)

    /** Marks every card of the page unread, including cards that have since left the page. */
    suspend fun markAllUnread(pageId: String)

    suspend fun position(pageId: String): String?

    suspend fun savePosition(pageId: String, tropeKey: String)
}

/** Parsed pages, from cache first, else from the site. */
interface PageRepository {
    /** From cache when present. @throws PageLoadException */
    suspend fun load(url: String): ParsedPage

    /** Downloads the page again and replaces the cached copy (F9.3). @throws PageLoadException */
    suspend fun refresh(url: String): ParsedPage

    /** When the cached copy was downloaded, or null when not cached. */
    suspend fun fetchedAt(url: String): Long?
}

/** Works, their pages and aggregated progress. */
interface LibraryRepository {
    fun observeWorks(): Flow<List<WorkSummary>>

    fun observeWork(workId: String): Flow<Work?>

    fun observePages(workId: String): Flow<List<WorkPageState>>

    /** Id of a work in the library (not deleted) known under [url], current or former. */
    suspend fun findWorkByUrl(url: String): String?

    /** Adds the work, or restores and completes it if it already exists; returns its id. */
    suspend fun saveWork(work: NewWork): String

    /** Soft delete: progress is kept, so adding the work again brings it back. */
    suspend fun deleteWork(workId: String)

    suspend fun page(pageId: String): WorkPage?

    suspend fun work(workId: String): Work?

    suspend fun recordCardCount(pageId: String, cardCount: Int)

    /** The site redirected [oldUrl] to [newUrl] (F11.3): the page and its work follow, the old URL stays as an alias. */
    suspend fun recordRename(pageId: String, oldUrl: String, newUrl: String)

    /** F2.4. */
    suspend fun setPageHidden(pageId: String, hidden: Boolean)

    /** F2.4: [orderedPageIds] lists all of the work's pages in their new order. */
    suspend fun reorderPages(orderedPageIds: List<String>)
}

class PageLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
