package app.tropereader.domain

import app.tropereader.parser.Card

/** What changed between two versions of a page (F5.3). */
data class Reconciliation(
    /** Old key → new key for cards whose text was edited (their key, a text hash, changed). */
    val moved: Map<String, String>,
    /** Cards that are really new (not an edited version of an old one). */
    val added: List<Card>,
    /** Cards gone from the page. */
    val removed: List<Card>,
)

/**
 * Compares two versions of a page. Cards with the same key are the same card. A card that disappeared
 * is matched with a new card of the same kind under the same trope when their words overlap enough,
 * so a typo fix or a sentence added to an example keeps its read state.
 */
object Reconcile {
    const val MIN_SIMILARITY = 0.5

    fun of(old: List<Card>, new: List<Card>): Reconciliation {
        val oldKeys = old.mapTo(HashSet()) { it.key }
        val newKeys = new.mapTo(HashSet()) { it.key }
        val gone = old.filter { it.key !in newKeys }
        val fresh = new.filter { it.key !in oldKeys }.toMutableList()
        val moved = LinkedHashMap<String, String>()
        for (card in gone) {
            val words = words(card)
            val best = fresh
                .filter { it.kind == card.kind && sameTrope(card, it) }
                .map { it to similarity(words, words(it)) }
                .filter { it.second >= MIN_SIMILARITY }
                .maxByOrNull { it.second }
                ?.first ?: continue
            moved[card.key] = best.key
            fresh.remove(best)
        }
        return Reconciliation(moved, fresh, gone.filter { it.key !in moved })
    }

    /** Examples must share their parent trope; standalone cards (entries, text) can match each other. */
    private fun sameTrope(a: Card, b: Card): Boolean {
        val aStandalone = a.parentKey == a.key
        val bStandalone = b.parentKey == b.key
        return if (aStandalone || bStandalone) aStandalone && bStandalone else a.parentKey == b.parentKey
    }

    private fun words(card: Card): Set<String> =
        card.plainText.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.toSet()

    /** Jaccard index of the two word sets. */
    private fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }
}
