package app.tropereader.parser

/** Result of splitting one TvTropes page into reading cards. Pure content: recomputable at any time. */
data class ParsedPage(
    /** Canonical URL, as declared by the page itself (follows renames). */
    val url: String,
    /** Wiki path, e.g. `Series/BreakingBad` or `BreakingBad/TropesAToB`. */
    val path: String,
    val title: String,
    /** First path segment: media type for a work (`Series`, `VideoGame`…), or the subpage kind (`YMMV`…). */
    val namespace: String,
    val pageType: PageType,
    val cards: List<Card>,
    /** Sibling pages from the subpage bar (Characters, YMMV, Trivia, Recap…). */
    val relatedPages: List<PageLink>,
    /** Alphabetical tropes subpages ("Tropes A to B"…) to merge into one logical page (F2.5). */
    val splitParts: List<PageLink>,
    /** Episode pages, in index order, when this page is a Recap index (F3.7). */
    val recapEpisodes: List<PageLink>,
)

enum class PageType {
    WORK,
    TROPES_PART,
    GENERAL_TROPE,
    CHARACTERS,
    YMMV,
    TRIVIA,
    RECAP_INDEX,
    RECAP,
    OTHER_SUBPAGE,
}

data class PageLink(val label: String, val path: String, val url: String)

enum class CardKind {
    /** Text before the trope list (F3.5); for a Recap episode, the episode summary. */
    INTRO,
    /** A top-level entry without sub-bullets: one trope, one card. */
    ENTRY,
    /** One sub-bullet of a top-level entry (F3.2); [Card.title] and [Card.leadHtml] come from the parent. */
    EXAMPLE,
    /** Free text between lists (section notes, closing quote…). */
    TEXT,
}

data class Card(
    /** Stable key used to store read state (F3.6). Unique within the page. */
    val key: String,
    /** Key of the parent trope; equals [key] for anything that is not an [CardKind.EXAMPLE]. */
    val parentKey: String,
    val position: Int,
    val kind: CardKind,
    /** Trope (or work) name of the entry, when one could be detected. */
    val title: String?,
    val titleUrl: String?,
    /** Parent entry's own text (name + intro sentence), shown as header of an [CardKind.EXAMPLE]. */
    val leadHtml: String?,
    /** Sanitized HTML of the card body. Links are absolute. */
    val bodyHtml: String,
    /** Nearest heading above the card. */
    val section: String?,
    /** Folder label path, e.g. `Tropes A-C` or `Season 1 / Episodes` (F3.4). */
    val folder: String?,
    /** Plain text, ready for speech (F10.1, F10.2). */
    val segments: List<TextSegment>,
) {
    val plainText: String get() = segments.joinToString("") { it.text }
}

data class TextSegment(val text: String, val isSpoiler: Boolean)
