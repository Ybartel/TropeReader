package app.tropereader.parser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Real pages saved from the site, in `src/test/resources/pages/` (one per tricky case).
 * Each page must parse with unique keys, and its card dump must match `snapshots/<page>.txt`.
 *
 * New page, or intended parser change: `./gradlew :parser:test -DupdateSnapshots=true`, then review the diff.
 */
class RecordedPagesTest {
    private val parser = TvTropesPageParser()
    private val snapshotDir = File(System.getProperty("snapshotDir"))
    private val update = System.getProperty("updateSnapshots") == "true"

    private fun pages(): List<File> =
        javaClass.getResource("/pages")?.let { File(it.toURI()).listFiles { f -> f.extension == "html" }?.sortedBy { it.name } }.orEmpty()

    @Test
    fun `recorded pages match their snapshots`() {
        val mismatches = mutableListOf<String>()
        for (file in pages()) {
            val page = parser.parse(file.readText(), "https://tvtropes.org/pmwiki/pmwiki.php/Unknown/${file.nameWithoutExtension}")
            assertTrue(page.cards.isNotEmpty(), "${file.name}: no cards")
            assertEquals(page.cards.size, page.cards.map { it.key }.toSet().size, "${file.name}: duplicate keys")

            val dump = PageDump.render(page)
            val snapshot = File(snapshotDir, file.nameWithoutExtension + ".txt")
            when {
                update || !snapshot.exists() -> snapshot.writeText(dump)
                snapshot.readText() != dump -> mismatches += file.name
            }
        }
        if (mismatches.isNotEmpty()) fail("Snapshots differ for $mismatches; rerun with -DupdateSnapshots=true if intended")
    }
}
