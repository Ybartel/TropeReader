package app.tropereader.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Safelist

/**
 * TvTropes markup as observed on 2026-11-03. Every site-specific selector lives in this file,
 * so a site redesign only touches this class (and its recorded test pages).
 *
 * Observed structure of `#main-article`:
 * - intro blocks (image, caption, quote, paragraphs), then `<hr>`;
 * - headings (`h2`/`h3`) and `ul`/`ol` lists, one top-level `li` per trope;
 * - folders: `div.folderlabel` (`togglefolder('folderN')`) followed by `div.folder#folderN`,
 *   plus one `toggleAllFolders()` label to ignore;
 * - spoilers: `span.spoiler`; inline notes: `span.notelabel` + `span.inlinefolder`;
 * - ads: `div.tvtropes-ad-unit`.
 */
class TvTropesPageParser : PageParser {

    override fun parse(html: String, url: String): ParsedPage {
        val doc = Jsoup.parse(html, url)
        val article = doc.selectFirst(ARTICLE) ?: throw PageParseException("No $ARTICLE in $url")
        val canonical = doc.selectFirst("link[rel=canonical]")?.absUrl("href")?.takeIf { it.isNotBlank() } ?: url
        val path = wikiPath(canonical) ?: throw PageParseException("Not a wiki URL: $canonical")
        val namespace = path.substringBefore('/')
        val splitParts = splitParts(article, path)
        val recapEpisodes = if (namespace == "Recap") recapEpisodes(article, path) else emptyList()
        return ParsedPage(
            url = canonical,
            path = path,
            title = title(doc),
            namespace = namespace,
            pageType = pageType(path, namespace, recapEpisodes),
            cards = CardBuilder(canonical, splitParts.map { it.path }.toSet()).build(article),
            relatedPages = relatedPages(doc, path),
            splitParts = splitParts,
            recapEpisodes = recapEpisodes,
        )
    }

    private fun title(doc: Document): String {
        val h1 = doc.selectFirst("h1.entry-title") ?: doc.selectFirst("h1")
        if (h1 != null) {
            val copy = h1.clone()
            copy.select(".ns_parts").remove()
            cleanText(copy).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() } ?: doc.title()
    }

    private fun pageType(path: String, namespace: String, recapEpisodes: List<PageLink>): PageType = when {
        SPLIT_PART.matches(path) -> PageType.TROPES_PART
        namespace == "Main" -> PageType.GENERAL_TROPE
        namespace == "Characters" -> PageType.CHARACTERS
        namespace == "YMMV" -> PageType.YMMV
        namespace == "Trivia" -> PageType.TRIVIA
        namespace == "Recap" -> if (recapEpisodes.isNotEmpty()) PageType.RECAP_INDEX else PageType.RECAP
        namespace in SUBPAGE_NAMESPACES -> PageType.OTHER_SUBPAGE
        else -> PageType.WORK
    }

    private fun relatedPages(doc: Document, self: String): List<PageLink> =
        doc.select("nav.subpage-links a[href]").mapNotNull { link(it) }.filter { it.path != self }.distinctBy { it.path }

    private fun splitParts(article: Element, self: String): List<PageLink> =
        article.select("a.twikilink[href]").mapNotNull { link(it) }
            .filter { SPLIT_PART.matches(it.path) && it.path != self }
            .distinctBy { it.path }

    /** Episode list of a Recap index: list items whose first link is another Recap page. */
    private fun recapEpisodes(article: Element, self: String): List<PageLink> {
        val episodes = article.select("li").mapNotNull { li -> li.selectFirst("a.twikilink[href]")?.let { link(it) } }
            .filter { it.path.startsWith("Recap/") && it.path != self }
            .distinctBy { it.path }
        return if (episodes.size >= MIN_RECAP_EPISODES) episodes else emptyList()
    }

    private class CardBuilder(private val baseUrl: String, private val splitParts: Set<String>) {
        private val cards = mutableListOf<Card>()
        private val keyCounts = mutableMapOf<String, Int>()
        private val folders = ArrayDeque<String>()
        private val pendingText = mutableListOf<Node>()
        private var pendingLabel: String? = null
        private var section: String? = null

        fun build(article: Element): List<Card> {
            val nodes = article.childNodes().filterNot(::isNoise)
            val introEnd = introEnd(nodes)
            val intro = nodes.subList(0, introEnd)
            if (hasContent(intro)) {
                val (html, segments) = render(intro)
                add(CardKind.INTRO, "intro", null, null, null, null, html, segments)
            }
            walk(nodes.subList(introEnd, nodes.size))
            flushText()
            return cards
        }

        /** The intro ends at the first `<hr>`, or failing that at the first list or folder. */
        private fun introEnd(nodes: List<Node>): Int {
            val hr = nodes.indexOfFirst { it is Element && it.normalName() == "hr" }
            if (hr >= 0) return hr
            val list = nodes.indexOfFirst { it is Element && (it.normalName() in LISTS || it.hasClass("folderlabel") || it.hasClass("folder")) }
            return if (list >= 0) list else nodes.size
        }

        private fun walk(nodes: List<Node>) {
            for (node in nodes) {
                if (isNoise(node)) continue
                if (node !is Element) {
                    pendingText += node
                    continue
                }
                when {
                    node.normalName() == "hr" -> flushText()
                    node.normalName() in HEADINGS -> {
                        flushText()
                        section = cleanText(node).removeSuffix(":").trim()
                    }
                    node.hasClass("folderlabel") -> {
                        flushText()
                        pendingLabel = cleanText(node)
                    }
                    node.hasClass("folder") -> {
                        flushText()
                        val outerSection = section
                        folders.addLast(pendingLabel ?: node.id())
                        pendingLabel = null
                        walk(node.childNodes())
                        flushText()
                        folders.removeLast()
                        section = outerSection
                    }
                    node.normalName() in LISTS -> {
                        flushText()
                        node.children().filter { it.normalName() == "li" }.forEach(::entry)
                    }
                    else -> pendingText.add(node)
                }
            }
        }

        private fun entry(li: Element) {
            if (isSplitPartLink(li)) return
            val items = li.children().filter { it.normalName() in LISTS }.flatMap { list -> list.children().filter { it.normalName() == "li" } }
            val leadNodes = li.childNodes().filterNot { it is Element && it.normalName() in LISTS }
            val head = head(leadNodes)
            val tropeKey = head?.let { if (it.links.isNotEmpty()) StableKeys.forLinks(it.links.map { l -> l.path }) else StableKeys.forName(it.name) }
            val (leadHtml, leadSegments) = render(leadNodes)

            if (items.isEmpty()) {
                val key = uniqueKey(tropeKey ?: StableKeys.forText(plain(leadSegments)))
                add(CardKind.ENTRY, key, key, head?.name, head?.links?.firstOrNull()?.url, null, leadHtml, leadSegments)
                return
            }
            val parentKey = uniqueKey(tropeKey ?: StableKeys.forText(plain(leadSegments)))
            for (item in items) {
                val ownText = item.childNodes().filterNot { it is Element && it.normalName() in LISTS }
                val key = uniqueKey(StableKeys.forExample(parentKey, plain(render(ownText).second)))
                val (html, segments) = render(item.childNodes())
                add(CardKind.EXAMPLE, key, parentKey, head?.name, head?.links?.firstOrNull()?.url, leadHtml, html, segments)
            }
        }

        /** A list item that only links to an alphabetical subpage ("Tropes A to B") is navigation, not a trope. */
        private fun isSplitPartLink(li: Element): Boolean {
            val links = li.select("a.twikilink[href]")
            if (links.size != 1) return false
            val path = link(links.first()!!)?.path ?: return false
            return path in splitParts && cleanText(li) == cleanText(links.first()!!)
        }

        private fun flushText() {
            if (pendingText.isEmpty()) return
            val nodes = pendingText.toList()
            pendingText.clear()
            if (!hasContent(nodes)) return
            val (html, segments) = render(nodes)
            val images = nodes.filterIsInstance<Element>().flatMap { it.select("img").map { img -> img.attr("src") } }
            val key = uniqueKey(StableKeys.forBlock(plain(segments) + images.joinToString(" ")))
            add(CardKind.TEXT, key, key, null, null, null, html, segments)
        }

        private fun add(
            kind: CardKind,
            key: String,
            parentKey: String?,
            title: String?,
            titleUrl: String?,
            leadHtml: String?,
            bodyHtml: String,
            segments: List<TextSegment>,
        ) {
            cards += Card(
                key = key,
                parentKey = parentKey ?: key,
                position = cards.size,
                kind = kind,
                title = title,
                titleUrl = titleUrl,
                leadHtml = leadHtml,
                bodyHtml = bodyHtml,
                section = section,
                folder = folders.takeIf { it.isNotEmpty() }?.joinToString(" / "),
                segments = segments,
            )
        }

        private fun uniqueKey(base: String): String {
            val count = keyCounts.merge(base, 1, Int::plus)!!
            return if (count == 1) base else "$base~$count"
        }

        private fun render(nodes: List<Node>): Pair<String, List<TextSegment>> {
            val container = Element("div")
            nodes.forEach { container.appendChild(it.clone()) }
            prepare(container)
            val html = Jsoup.clean(container.html(), baseUrl, SAFELIST, Document.OutputSettings().prettyPrint(false)).trim()
            return html to TextSegments.of(Jsoup.parseBodyFragment(html).body())
        }
    }

    private data class Head(val name: String, val links: List<PageLink>)

    private companion object {
        const val ARTICLE = "#main-article"
        const val MAX_HEAD_LENGTH = 100
        const val MIN_RECAP_EPISODES = 3
        const val WIKI_PREFIX = "/pmwiki/pmwiki.php/"
        val LISTS = setOf("ul", "ol")
        val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        val SPLIT_PART = Regex("^[^/]+/Tropes[A-Z0-9]+To[A-Z0-9]+$", RegexOption.IGNORE_CASE)
        val SUBPAGE_NAMESPACES = setOf(
            "Awesome", "AwesomeMusic", "Funny", "Heartwarming", "TearJerker", "NightmareFuel", "WMG", "Quotes",
            "Laconic", "Fridge", "FridgeBrilliance", "FridgeHorror", "Headscratchers", "ShoutOut", "Synopsis",
            "Analysis", "Haiku", "ImageLinks", "DerivativeWorks", "FanficRecs", "Ascended", "Characters", "YMMV",
            "Trivia", "Recap",
        )
        /** Classes the reader UI styles: spoilers (F6), quotes (F3.8), notes. Everything else is dropped. */
        val KEPT_CLASSES = setOf("spoiler", "indent", "note", "quoteright", "acaptionright")
        val SAFELIST: Safelist = Safelist.relaxed().addAttributes(":all", "class").preserveRelativeLinks(false)

        fun wikiPath(url: String): String? {
            val start = url.indexOf(WIKI_PREFIX).takeIf { it >= 0 } ?: return null
            return url.substring(start + WIKI_PREFIX.length).substringBefore('?').substringBefore('#').trimEnd('/')
                .takeIf { it.contains('/') }
        }

        fun link(a: Element): PageLink? {
            val url = a.absUrl("href").ifBlank { return null }
            val path = wikiPath(url) ?: return null
            return PageLink(cleanText(a), path, url)
        }

        fun cleanText(e: Element): String = e.text().replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

        fun plain(segments: List<TextSegment>): String = segments.joinToString("") { it.text }

        fun isNoise(node: Node): Boolean = when (node) {
            is Comment, is DataNode -> true
            is TextNode -> node.isBlank
            is Element -> node.normalName() in setOf("script", "style", "noscript") ||
                node.hasClass("tvtropes-ad-unit") ||
                (node.hasClass("folderlabel") && node.attr("onclick").contains("toggleAllFolders")) ||
                (node.normalName() in setOf("p", "div", "span") && !node.hasText() && node.select("img").isEmpty())
            else -> false
        }

        fun hasContent(nodes: List<Node>): Boolean = nodes.any {
            !isNoise(it) && when (it) {
                is TextNode -> !it.isBlank
                is Element -> it.hasText() || it.select("img").isNotEmpty()
                else -> false
            }
        }

        /** Strips notes' toggles, ads and site-only classes before sanitizing. */
        fun prepare(container: Element) {
            container.select(".notelabel, .tvtropes-ad-unit").remove()
            // Notes are shown inline, in parentheses, in the HTML itself so screen and speech agree.
            container.select(".inlinefolder").forEach { it.clearAttributes().addClass("note").prependText(" (").appendText(")") }
            // Text the site hides (e.g. editors' "invoked" labels); sanitizing drops styles, so it would show.
            container.select("[style~=display:\\s*none]").remove()
            container.select("*").forEach { el ->
                val kept = el.classNames().filter { it in KEPT_CLASSES }
                if (kept.isEmpty()) el.removeAttr("class") else el.classNames(kept.toMutableSet())
            }
        }

        /**
         * Detects an entry's name: the text before the first colon at the start of the item,
         * e.g. `<a>Aborted Arc</a>:` or `<em><a>Film</a></em>:`.
         */
        fun head(nodes: List<Node>): Head? {
            val name = StringBuilder()
            val links = mutableListOf<PageLink>()
            fun finish(): Head? {
                val text = name.toString().replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
                return if (text.isEmpty() || text.length > MAX_HEAD_LENGTH) null else Head(text, links)
            }
            for (node in nodes) {
                when (node) {
                    is TextNode -> {
                        val text = node.text()
                        val colon = text.indexOf(':')
                        if (colon >= 0) {
                            name.append(text, 0, colon)
                            return finish()
                        }
                        name.append(text)
                    }
                    is Element -> {
                        if (node.hasClass("notelabel") || node.hasClass("inlinefolder")) return null
                        val anchors = if (node.normalName() == "a") listOf(node) else node.select("a")
                        links += anchors.filter { it.hasClass("twikilink") }.mapNotNull { link(it) }
                        val text = node.text().trimEnd()
                        if (text.endsWith(":")) {
                            name.append(text.dropLast(1))
                            return finish()
                        }
                        if (text.contains(':')) return null
                        name.append(text)
                    }
                }
                if (name.length > MAX_HEAD_LENGTH) return null
            }
            return null
        }
    }
}
