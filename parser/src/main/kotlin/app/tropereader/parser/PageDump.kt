package app.tropereader.parser

/** Human-readable dump of a parsed page: console output of the prototype and snapshot format of the tests. */
object PageDump {
    private const val PREVIEW_LENGTH = 90

    fun render(page: ParsedPage): String = buildString {
        appendLine("# ${page.title}")
        appendLine("path: ${page.path}")
        appendLine("type: ${page.pageType} (${page.namespace})")
        appendLine("cards: ${page.cards.size}")
        links("related", page.relatedPages)
        links("split parts", page.splitParts)
        links("recap episodes", page.recapEpisodes)
        appendLine()
        var context: String? = null
        for (card in page.cards) {
            val here = listOfNotNull(card.section, card.folder).joinToString(" > ")
            if (here != context) {
                appendLine("== ${here.ifEmpty { "(top)" }}")
                context = here
            }
            val spoilers = card.segments.count { it.isSpoiler }
            val preview = card.plainText.replace('\n', ' ').let { if (it.length > PREVIEW_LENGTH) it.take(PREVIEW_LENGTH) + "…" else it }
            append("%4d  %-7s %s".format(card.position, card.kind, card.key))
            if (spoilers > 0) append("  [spoilers:$spoilers]")
            appendLine()
            card.title?.let { appendLine("      title: $it") }
            appendLine("      text:  $preview")
        }
    }

    private fun StringBuilder.links(label: String, links: List<PageLink>) {
        if (links.isEmpty()) return
        appendLine("$label: " + links.joinToString(", ") { "${it.label} <${it.path}>" })
    }
}
