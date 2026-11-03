package app.tropereader.domain

import javax.inject.Inject

/**
 * The only way forward (F10.4): marks the current card read, then moves on and saves the position.
 * On the last card, it is marked read and the cursor stays.
 */
class AdvanceToNextCard @Inject constructor(private val progress: ReadingProgressRepository) {
    suspend operator fun invoke(pageId: String, cursor: ReadingCursor): ReadingCursor {
        progress.setRead(pageId, cursor.current.key, true)
        val next = cursor.next() ?: return cursor
        progress.savePosition(pageId, next.current.key)
        return next
    }
}

/** Any other move (back, jump): saves the position without marking anything read (F4.3, F7.2). */
class MoveToCard @Inject constructor(private val progress: ReadingProgressRepository) {
    suspend operator fun invoke(pageId: String, cursor: ReadingCursor, index: Int): ReadingCursor {
        val moved = cursor.moveTo(index)
        if (moved.index != cursor.index) progress.savePosition(pageId, moved.current.key)
        return moved
    }
}

/** Marks a whole page read or unread (F4.6), from the reader or from the work screen. */
class SetPageRead @Inject constructor(
    private val loadWorkPage: LoadWorkPage,
    private val progress: ReadingProgressRepository,
) {
    /** @param cardKeys the page's cards when already loaded; otherwise the page is loaded (cache first). */
    suspend operator fun invoke(pageId: String, isRead: Boolean, cardKeys: List<String>? = null) {
        if (isRead) {
            progress.markAllRead(pageId, cardKeys ?: loadWorkPage(pageId).cards.map { it.key })
        } else {
            progress.markAllUnread(pageId)
        }
    }
}
