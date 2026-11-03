package app.tropereader.domain

import app.tropereader.parser.Card
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One card view (F8.1), with enough context to be listed without reloading the page. */
data class HistoryItem(
    val id: String,
    val pageId: String,
    val tropeKey: String,
    val workTitle: String,
    val pageRole: String,
    /** Trope name, or a short preview for cards without one. Spoilers are blanked. */
    val label: String,
    val viewedAt: Long,
)

data class NewHistoryEntry(val pageId: String, val tropeKey: String, val label: String)

interface HistoryRepository {
    /** Most recent first. */
    fun observe(): Flow<List<HistoryItem>>

    suspend fun latest(): NewHistoryEntry?

    suspend fun add(entry: NewHistoryEntry)

    /** F8.3. */
    suspend fun clear()
}

/** Logs the card on screen; viewing the same card again right after (swiping back and forth) is logged once. */
class RecordCardView @Inject constructor(private val history: HistoryRepository) {
    suspend operator fun invoke(pageId: String, card: Card) {
        val latest = history.latest()
        if (latest != null && latest.pageId == pageId && latest.tropeKey == card.key) return
        history.add(NewHistoryEntry(pageId, card.key, label(card)))
    }

    companion object {
        private const val LABEL_LENGTH = 80

        fun label(card: Card): String = card.title ?: card.segments
            .joinToString("") { if (it.isSpoiler) "▮▮▮" else it.text }
            .replace('\n', ' ')
            .let { if (it.length > LABEL_LENGTH) it.take(LABEL_LENGTH).trimEnd() + "…" else it }
            .ifBlank { "Untitled card" }
    }
}

/** F8.2: entries grouped by local day, most recent day first. */
fun List<HistoryItem>.groupByDay(zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, List<HistoryItem>>> =
    groupBy { Instant.ofEpochMilli(it.viewedAt).atZone(zone).toLocalDate() }
        .toList()
        .sortedByDescending { it.first }
