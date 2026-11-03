package app.tropereader.domain

import app.tropereader.parser.Card

/**
 * The single reading position (F10.3). Swipes today and the speech player tomorrow both move it,
 * so progress and resume behave the same whatever drives the reading.
 */
data class ReadingCursor(val cards: List<Card>, val index: Int) {
    init {
        require(cards.isNotEmpty()) { "A page needs at least one card" }
        require(index in cards.indices) { "Index $index out of ${cards.size} cards" }
    }

    val current: Card get() = cards[index]
    val hasNext: Boolean get() = index < cards.lastIndex
    val hasPrevious: Boolean get() = index > 0

    fun next(): ReadingCursor? = if (hasNext) copy(index = index + 1) else null

    fun previous(): ReadingCursor? = if (hasPrevious) copy(index = index - 1) else null

    fun moveTo(newIndex: Int): ReadingCursor = copy(index = newIndex.coerceIn(cards.indices))

    companion object {
        /** Resume on the last card viewed (F5.2); if it is gone, on the first unread card; else at the start. */
        fun resume(cards: List<Card>, savedKey: String?, readKeys: Set<String>): ReadingCursor {
            val saved = cards.indexOfFirst { it.key == savedKey }
            val index = when {
                saved >= 0 -> saved
                else -> cards.indexOfFirst { it.key !in readKeys }.takeIf { it >= 0 } ?: 0
            }
            return ReadingCursor(cards, index)
        }
    }
}
