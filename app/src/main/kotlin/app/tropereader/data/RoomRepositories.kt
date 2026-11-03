package app.tropereader.data

import app.tropereader.data.db.CachedPageDao
import app.tropereader.data.db.CachedPageEntity
import app.tropereader.data.db.LibraryDao
import app.tropereader.data.db.ProgressDao
import app.tropereader.data.db.ReadingPositionEntity
import app.tropereader.data.db.TropeReadStateEntity
import app.tropereader.data.db.WorkEntity
import app.tropereader.data.db.WorkPageEntity
import app.tropereader.data.fetch.PageFetcher
import app.tropereader.domain.Ids
import app.tropereader.domain.LibraryRepository
import app.tropereader.domain.PageLoadException
import app.tropereader.domain.PageRepository
import app.tropereader.domain.ReadingProgressRepository
import app.tropereader.parser.PageParseException
import app.tropereader.parser.PageParser
import app.tropereader.parser.PageType
import app.tropereader.parser.ParsedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RoomReadingProgressRepository @Inject constructor(private val dao: ProgressDao) : ReadingProgressRepository {
    override fun observeReadKeys(pageId: String): Flow<Set<String>> =
        dao.observeReadKeys(pageId).map { it.toSet() }.distinctUntilChanged()

    override suspend fun setRead(pageId: String, tropeKey: String, isRead: Boolean) {
        val now = System.currentTimeMillis()
        dao.upsertReadState(
            TropeReadStateEntity(
                id = Ids.readState(pageId, tropeKey),
                pageId = pageId,
                tropeKey = tropeKey,
                isRead = isRead,
                readAt = if (isRead) now else null,
                updatedAt = now,
            ),
        )
    }

    override suspend fun position(pageId: String): String? = dao.position(pageId)

    override suspend fun savePosition(pageId: String, tropeKey: String) {
        dao.upsertPosition(ReadingPositionEntity(Ids.position(pageId), pageId, tropeKey, System.currentTimeMillis()))
    }
}

class RoomLibraryRepository @Inject constructor(private val dao: LibraryDao) : LibraryRepository {
    override suspend fun ensureSinglePageWork(requestedUrl: String, page: ParsedPage): String {
        val now = System.currentTimeMillis()
        val workId = Ids.work(page.path)
        val pageId = Ids.page(workId, MAIN_ROLE)
        dao.insertWork(
            WorkEntity(
                id = workId,
                canonicalUrl = page.url,
                aliasUrls = if (requestedUrl != page.url) requestedUrl else "",
                kind = if (page.pageType == PageType.GENERAL_TROPE) "TROPE" else "WORK",
                title = page.title,
                mediaType = page.namespace,
                addedAt = now,
                updatedAt = now,
            ),
        )
        dao.insertPage(
            WorkPageEntity(
                id = pageId,
                workId = workId,
                role = MAIN_ROLE,
                sourceUrls = page.url,
                pageType = page.pageType.name,
                displayOrder = 0,
                isHidden = false,
                updatedAt = now,
            ),
        )
        return pageId
    }

    private companion object {
        const val MAIN_ROLE = "Main"
    }
}

/** Cache first (F9.2); the raw HTML is kept so the parser can be rerun or the page exported (F11.4). */
class CachedPageRepository @Inject constructor(
    private val cache: CachedPageDao,
    private val fetcher: PageFetcher,
    private val parser: PageParser,
) : PageRepository {
    override suspend fun load(url: String): ParsedPage {
        val html = cache.get(url)?.rawHtml
            ?: fetcher.fetch(url).also { cache.put(CachedPageEntity(url, it, System.currentTimeMillis())) }
        return withContext(Dispatchers.Default) {
            try {
                parser.parse(html, url)
            } catch (e: PageParseException) {
                throw PageLoadException("This page could not be split into cards.", e)
            }
        }
    }
}
