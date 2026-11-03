package app.tropereader.parser.cli

import app.tropereader.parser.PageDump
import app.tropereader.parser.PageParseException
import app.tropereader.parser.TvTropesPageParser
import java.io.File
import kotlin.system.exitProcess

/**
 * Prototype console runner: parses saved TvTropes pages and prints their cards.
 *
 *     ./gradlew :parser:run --args="path/to/page.html [more.html…]"
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: parser <page.html> [<page.html>…]")
        exitProcess(2)
    }
    val parser = TvTropesPageParser()
    var failed = false
    for (arg in args) {
        val file = File(arg)
        try {
            // The canonical URL inside the page wins; this is only a fallback for relative links.
            val page = parser.parse(file.readText(), "https://tvtropes.org/pmwiki/pmwiki.php/Unknown/${file.nameWithoutExtension}")
            println(PageDump.render(page))
        } catch (e: PageParseException) {
            System.err.println("${file.name}: ${e.message}")
            failed = true
        }
    }
    if (failed) exitProcess(1)
}
