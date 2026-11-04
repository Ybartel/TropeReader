package app.tropereader.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/** A piece of a card's body, each with its own rendering (F3.8). */
sealed interface HtmlBlock {
    /** Running text, lists included. */
    data class Text(val html: String) : HtmlBlock

    /** An indented quote; [attributionHtml] is its "— Author" line, shown right-aligned. */
    data class Quote(val html: String, val attributionHtml: String?) : HtmlBlock

    data class Table(val rows: List<List<Cell>>) : HtmlBlock {
        data class Cell(val html: String, val isHeader: Boolean)
    }

    data class Image(val url: String, val alt: String?, val captionHtml: String?) : HtmlBlock
}

/** Splits sanitized card HTML (as produced by [TvTropesPageParser]) into [HtmlBlock]s. */
object HtmlBlocks {
    fun split(html: String): List<HtmlBlock> {
        val body = Jsoup.parseBodyFragment(html).body()
        val out = mutableListOf<HtmlBlock>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotBlank()) out += HtmlBlock.Text(text.toString().trim())
            text.clear()
        }
        walk(body.childNodes(), out, text, ::flush)
        flush()
        return out
    }

    private fun walk(nodes: List<Node>, out: MutableList<HtmlBlock>, text: StringBuilder, flush: () -> Unit) {
        var skipCaption = false
        for (node in nodes) {
            if (node !is Element) {
                text.append(node.outerHtml())
                continue
            }
            when {
                node.normalName() == "table" -> {
                    flush()
                    out += table(node)
                }
                // Article pictures only; small inline images stay in the text (and are not drawn).
                node.hasClass("quoteright") || (node.normalName() == "img" && node.hasClass("embeddedimage")) -> {
                    flush()
                    val img = if (node.normalName() == "img") node else node.selectFirst("img")
                    val caption = node.nextElementSibling()?.takeIf { it.hasClass("acaptionright") }
                    if (img != null && img.absUrl("src").isNotBlank()) {
                        out += HtmlBlock.Image(img.absUrl("src"), img.attr("alt").ifBlank { null }, caption?.html()?.trim())
                        skipCaption = caption != null
                    }
                }
                node.hasClass("acaptionright") && skipCaption -> skipCaption = false
                node.hasClass("indent") -> {
                    flush()
                    out += quote(node)
                }
                // A wrapper holding a table, image or quote: open it so they get their own blocks.
                node.normalName() == "div" && node.select("table, img.embeddedimage, div.quoteright, div.indent").isNotEmpty() ->
                    walk(node.childNodes(), out, text, flush)
                else -> text.append(node.outerHtml())
            }
        }
    }

    private fun quote(div: Element): HtmlBlock.Quote {
        val copy = div.clone()
        // The site puts the author in a nested indent starting with a dash: "— Jesse Pinkman".
        val attribution = copy.children().lastOrNull { it.hasClass("indent") && it.text().trimStart().startsWith("—") }
        attribution?.remove()
        return HtmlBlock.Quote(copy.html().trim(), attribution?.html()?.trim())
    }

    private fun table(table: Element): HtmlBlock.Table =
        HtmlBlock.Table(
            table.select("tr").map { row ->
                row.children().filter { it.normalName() == "td" || it.normalName() == "th" }
                    .map { HtmlBlock.Table.Cell(it.html().trim(), it.normalName() == "th") }
            }.filter { it.isNotEmpty() },
        )
}
