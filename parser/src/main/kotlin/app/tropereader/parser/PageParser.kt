package app.tropereader.parser

/** Turns a page's raw HTML into cards. The only place that knows the site's markup. */
interface PageParser {
    /**
     * @param html raw HTML as served by the site.
     * @param url URL the HTML was fetched from; used to resolve relative links when the page declares no canonical URL.
     * @throws PageParseException when the page does not look like a wiki article (the app falls back to raw display).
     */
    fun parse(html: String, url: String): ParsedPage
}

class PageParseException(message: String) : Exception(message)
