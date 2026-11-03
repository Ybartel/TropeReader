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

/** Works and their pages. */
interface LibraryRepository {
    /** Makes sure [page] is in the library as a one-page work; returns its page id. */
    suspend fun ensureSinglePageWork(requestedUrl: String, page: ParsedPage): String
}

class PageLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
