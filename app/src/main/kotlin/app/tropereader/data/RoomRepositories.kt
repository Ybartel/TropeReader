package app.tropereader.data

import app.tropereader.data.db.CachedPageDao
import app.tropereader.data.db.CachedPageEntity
import app.tropereader.data.db.LibraryDao
import app.tropereader.data.db.ProgressDao
import app.tropereader.data.db.ReadingPositionEntity
import app.tropereader.data.db.TropeReadStateEntity
import app.tropereader.data.db.WorkEntity
import app.tropereader.data.db.WorkPageEntity
import app.tropereader.data.db.PageSummaryEntity
import app.tropereader.data.fetch.PageFetcher
import app.tropereader.domain.Ids
import app.tropereader.domain.LibraryRepository
import app.tropereader.domain.NewWork
import app.tropereader.domain.TvTropesUrls
import app.tropereader.domain.Work
import app.tropereader.domain.WorkPage
import app.tropereader.domain.WorkPageState
import app.tropereader.domain.WorkPlanner
import app.tropereader.domain.WorkSummary
import app.tropereader.domain.PageLoadException
import app.tropereader.domain.PageRepository
import app.tropereader.domain.ReadingProgressRepository
import app.tropereader.parser.PageParseException
import app.tropereader.parser.PageParser
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
        dao.upsertReadState(readState(pageId, tropeKey, isRead, System.currentTimeMillis()))
    }

    override suspend fun markAllRead(pageId: String, tropeKeys: Collection<String>) {
        val now = System.currentTimeMillis()
        dao.upsertReadStates(tropeKeys.map { readState(pageId, it, true, now) })
    }

    override suspend fun markAllUnread(pageId: String) = dao.markAllUnread(pageId, System.currentTimeMillis())

    private fun readState(pageId: String, tropeKey: String, isRead: Boolean, now: Long) = TropeReadStateEntity(
        id = Ids.readState(pageId, tropeKey),
        pageId = pageId,
        tropeKey = tropeKey,
        isRead = isRead,
        readAt = if (isRead) now else null,
        updatedAt = now,
    )

    override suspend fun position(pageId: String): String? = dao.position(pageId)

    override suspend fun savePosition(pageId: String, tropeKey: String) {
        dao.upsertPosition(ReadingPositionEntity(Ids.position(pageId), pageId, tropeKey, System.currentTimeMillis()))
    }
}

class RoomLibraryRepository @Inject constructor(private val dao: LibraryDao) : LibraryRepository {
    override fun observeWorks(): Flow<List<WorkSummary>> = dao.observeWorks().map { rows ->
        rows.map { WorkSummary(it.work.toDomain(), it.pageCount, it.openedPageCount, it.cardCount, it.readCount, it.lastReadAt) }
    }

    override fun observeWork(workId: String): Flow<Work?> = dao.observeWork(workId).map { it?.toDomain() }

    override fun observePages(workId: String): Flow<List<WorkPageState>> = dao.observePages(workId).map { rows ->
        rows.map { WorkPageState(it.page.toDomain(), it.cardCount, it.readCount, it.lastReadAt) }
    }

    override suspend fun findWorkByUrl(url: String): String? {
        val work = dao.workByUrl(url) ?: TvTropesUrls.path(url)?.let { dao.work(Ids.work(it)) }
        return work?.takeIf { it.deletedAt == null }?.id
    }

    override suspend fun saveWork(work: NewWork): String {
        val now = System.currentTimeMillis()
        val id = Ids.work(work.path)
        val existing = dao.work(id)
        val aliases = (existing?.aliasUrls.orEmpty().lines() + listOfNotNull(work.aliasUrl, existing?.canonicalUrl))
            .filter { it.isNotBlank() && it != work.canonicalUrl }
            .distinct()
        dao.upsertWork(
            WorkEntity(
                id = id,
                canonicalUrl = work.canonicalUrl,
                aliasUrls = aliases.joinToString("\n"),
                kind = if (work.isGeneralTrope) "TROPE" else "WORK",
                title = work.title,
                mediaType = work.mediaType,
                addedAt = existing?.addedAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        work.pages.forEachIndexed { order, page ->
            val pageId = Ids.page(id, page.role)
            val sources = page.sourceUrls.joinToString("\n")
            dao.insertPage(
                WorkPageEntity(
                    id = pageId,
                    workId = id,
                    role = page.role,
                    sourceUrls = sources,
                    pageType = if (page.role == WorkPlanner.MAIN_ROLE) "MAIN" else "SUBPAGE",
                    displayOrder = order,
                    isHidden = false,
                    updatedAt = now,
                ),
            )
            dao.updatePageSources(pageId, sources, now)
        }
        return id
    }

    override suspend fun deleteWork(workId: String) = dao.softDeleteWork(workId, System.currentTimeMillis())

    override suspend fun page(pageId: String): WorkPage? = dao.page(pageId)?.takeIf { it.deletedAt == null }?.toDomain()

    override suspend fun work(workId: String): Work? = dao.work(workId)?.toDomain()

    override suspend fun recordCardCount(pageId: String, cardCount: Int) =
        dao.upsertSummary(PageSummaryEntity(pageId, cardCount, System.currentTimeMillis()))

    private fun WorkEntity.toDomain() = Work(id, title, mediaType, canonicalUrl)

    override suspend fun setPageHidden(pageId: String, hidden: Boolean) = dao.setPageHidden(pageId, hidden, System.currentTimeMillis())

    override suspend fun reorderPages(orderedPageIds: List<String>) = dao.reorderPages(orderedPageIds, System.currentTimeMillis())

    private fun WorkPageEntity.toDomain() = WorkPage(id, workId, role, sourceUrls.lines().filter { it.isNotBlank() }, isHidden)
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
