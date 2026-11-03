package app.tropereader.parser

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Plain text for speech (F10.1–F10.2, F3.9): links reduced to their label, formatting dropped,
 * images skipped, tables read row by row, spoilers flagged per segment.
 */
internal object TextSegments {
    private val BLOCKS = setOf("p", "div", "li", "ul", "ol", "table", "tr", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6")

    /** A raw piece of text, or a line break when [text] is null. */
    private data class Piece(val text: String?, val spoiler: Boolean)

    fun of(root: Element): List<TextSegment> {
        val pieces = mutableListOf<Piece>()
        root.childNodes().forEach { walk(it, false, pieces) }
        return merge(pieces)
    }

    private fun walk(node: Node, spoiler: Boolean, out: MutableList<Piece>) {
        if (node is TextNode) {
            out += Piece(node.wholeText, spoiler)
            return
        }
        if (node !is Element) return
        val inSpoiler = spoiler || node.hasClass("spoiler")
        when (node.normalName()) {
            "img" -> Unit
            "br" -> out += Piece(null, inSpoiler)
            "tr" -> {
                out += Piece(null, inSpoiler)
                node.children().forEachIndexed { i, cell ->
                    if (i > 0) out += Piece(", ", inSpoiler)
                    cell.childNodes().forEach { walk(it, inSpoiler, out) }
                }
                out += Piece(null, inSpoiler)
            }
            else -> {
                val block = node.normalName() in BLOCKS
                val note = node.hasClass("note")
                if (block) out += Piece(null, inSpoiler)
                if (note) out += Piece(" (", inSpoiler)
                node.childNodes().forEach { walk(it, inSpoiler, out) }
                if (note) out += Piece(")", inSpoiler)
                if (block) out += Piece(null, inSpoiler)
            }
        }
    }

    /** Collapses whitespace, turns runs of breaks into one newline and merges neighbours with the same spoiler flag. */
    private fun merge(pieces: List<Piece>): List<TextSegment> {
        val segments = mutableListOf<Pair<StringBuilder, Boolean>>()
        var pendingBreak = false
        fun lastChar(): Char? = segments.lastOrNull { it.first.isNotEmpty() }?.first?.last()

        for (piece in pieces) {
            if (piece.text == null) {
                pendingBreak = true
                continue
            }
            var text = piece.text.replace(' ', ' ').replace(Regex("\\s+"), " ")
            if (text.isEmpty() || (text == " " && (lastChar() == null || pendingBreak))) continue
            if (pendingBreak && lastChar() != null) {
                segments.lastOrNull { it.first.isNotEmpty() }?.first?.let { while (it.endsWith(" ")) it.setLength(it.length - 1) }
                text = "\n" + text.trimStart()
            } else if (lastChar() == null || lastChar() == ' ' || lastChar() == '\n') {
                text = text.trimStart()
            }
            pendingBreak = false
            if (text.isEmpty()) continue
            val last = segments.lastOrNull()
            if (last != null && last.second == piece.spoiler) last.first.append(text) else segments += StringBuilder(text) to piece.spoiler
        }

        val result = segments.map { TextSegment(it.first.toString(), it.second) }.toMutableList()
        if (result.isNotEmpty()) result[result.lastIndex] = result.last().copy(text = result.last().text.trimEnd())
        return result.filter { it.text.isNotEmpty() }
    }
}
