package app.tropereader.domain

import app.tropereader.parser.Card
import app.tropereader.parser.CardKind
import app.tropereader.parser.TextSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

private fun card(key: String, title: String? = null, segments: List<TextSegment> = emptyList()) =
    Card(key, key, 0, CardKind.ENTRY, title, null, null, "", null, null, segments)

private class FakeHistory : HistoryRepository {
    val entries = mutableListOf<NewHistoryEntry>()

    override fun observe(): Flow<List<HistoryItem>> = emptyFlow()

    override suspend fun latest(): NewHistoryEntry? = entries.lastOrNull()

    override suspend fun add(entry: NewHistoryEntry) {
        entries += entry
    }

    override suspend fun clear() = entries.clear()
}

class RecordCardViewTest {
    private val history = FakeHistory()
    private val record = RecordCardView(history)

    @Test
    fun `the same card viewed twice in a row is logged once`() = runTest {
        record("p", card("a"))
        record("p", card("a"))
        record("p", card("b"))
        record("p", card("a"))
        assertEquals(listOf("a", "b", "a"), history.entries.map { it.tropeKey })
    }

    @Test
    fun `labels use the trope name, else a preview with spoilers blanked`() {
        assertEquals("Aborted Arc", RecordCardView.label(card("k", title = "Aborted Arc")))
        assertEquals(
            "Walt ▮▮▮ in the end",
            RecordCardView.label(card("k", segments = listOf(TextSegment("Walt ", false), TextSegment("dies", true), TextSegment(" in the end", false)))),
        )
    }
}

class GroupByDayTest {
    @Test
    fun `entries are grouped by day, most recent first`() {
        val day1 = LocalDate.of(2026, 11, 3).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val day2 = day1 + 86_400_000L
        val items = listOf(day2 + 5, day2 + 1, day1 + 9).mapIndexed { i, at -> HistoryItem("$i", "p", "k$i", "W", "Main", "L", at) }
        val groups = items.groupByDay(ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 3)), groups.map { it.first })
        assertEquals(listOf("0", "1"), groups.first().second.map { it.id })
    }
}
