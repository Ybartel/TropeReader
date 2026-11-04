package app.tropereader.domain

data class Work(
    val id: String,
    val title: String,
    val mediaType: String,
    val canonicalUrl: String,
)

/** A library entry with its progress (F1.3). Card totals only cover pages opened at least once. */
data class WorkSummary(
    val work: Work,
    val pageCount: Int,
    val openedPageCount: Int,
    val cardCount: Int,
    val readCount: Int,
    val lastReadAt: Long?,
) {
    val progress: Float get() = if (cardCount == 0) 0f else (readCount.toFloat() / cardCount).coerceAtMost(1f)
}

data class WorkPage(
    val id: String,
    val workId: String,
    /** `Main` (the tropes, merged across A-to-Z subpages), or a subpage label: `YMMV`, `Trivia`… */
    val role: String,
    val sourceUrls: List<String>,
    /** Hidden by the user (F2.4): left out of the page list, Resume and the work's progress. */
    val isHidden: Boolean = false,
)

enum class PageStatus { NOT_STARTED, IN_PROGRESS, DONE }

/** A page with its state (F2.2); [cardCount] is null until the page has been opened once. */
data class WorkPageState(
    val page: WorkPage,
    val cardCount: Int?,
    val readCount: Int,
    val lastReadAt: Long?,
) {
    val status: PageStatus
        get() = when {
            cardCount != null && cardCount > 0 && readCount >= cardCount -> PageStatus.DONE
            readCount > 0 || lastReadAt != null -> PageStatus.IN_PROGRESS
            else -> PageStatus.NOT_STARTED
        }

    companion object {
        /** "Resume" target (F2.3): the visible page read most recently, else the first visible one. */
        fun resumeTarget(pages: List<WorkPageState>): WorkPageState? {
            val visible = pages.filterNot { it.page.isHidden }
            return visible.filter { it.lastReadAt != null }.maxByOrNull { it.lastReadAt!! } ?: visible.firstOrNull()
        }

        /** Page ids in their new order after moving [pageId] one step up ([delta] -1) or down (+1). */
        fun moved(pages: List<WorkPageState>, pageId: String, delta: Int): List<String> {
            val ids = pages.map { it.page.id }.toMutableList()
            val from = ids.indexOf(pageId)
            val to = from + delta
            if (from < 0 || to !in ids.indices) return ids
            ids.add(to, ids.removeAt(from))
            return ids
        }
    }
}

/** A work to add, as planned from its main page. */
data class NewWork(
    val path: String,
    val canonicalUrl: String,
    /** URL the user gave, when it differs from the canonical one (F11.3). */
    val aliasUrl: String?,
    val isGeneralTrope: Boolean,
    val title: String,
    val mediaType: String,
    val pages: List<NewPage>,
)

data class NewPage(val role: String, val sourceUrls: List<String>)
