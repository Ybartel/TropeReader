package app.tropereader.domain

import app.tropereader.parser.ParsedPage
import kotlinx.coroutines.flow.Flow

/** Read state and position, keyed by stable card keys. The UI never knows where it is stored. */
interface ReadingProgressRepository {
    fun observeReadKeys(pageId: String): Flow<Set<String>>

    suspend fun setRead(pageId: String, tropeKey: String, isRead: Boolean)

    suspend fun position(pageId: String): String?

    suspend fun savePosition(pageId: String, tropeKey: String)
}

/** Parsed pages, from cache first, else from the site. */
interface PageRepository {
    /** @throws PageLoadException */
    suspend fun load(url: String): ParsedPage
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
}

class PageLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
