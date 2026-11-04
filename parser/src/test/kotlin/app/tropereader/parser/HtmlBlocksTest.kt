package app.tropereader.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlBlocksTest {
    private val parser = TvTropesPageParser()

    private fun page(name: String) =
        parser.parse(checkNotNull(javaClass.getResource(name)).readText(), "https://tvtropes.org/pmwiki/pmwiki.php/X/Y")

    @Test
    fun `intro splits into image with caption, quote with attribution, then text`() {
        val intro = page("/synthetic/work_with_folders.html").cards.first()
        val blocks = HtmlBlocks.split(intro.bodyHtml)
        val image = assertIs<HtmlBlock.Image>(blocks[0])
        assertEquals("https://static.tvtropes.org/pmwiki/pub/images/sample.png", image.url)
        assertEquals("<em>Science!</em>", image.captionHtml)
        val quote = assertIs<HtmlBlock.Quote>(blocks[1])
        assertTrue("Some quote." in quote.html)
        assertEquals("— <strong>Someone</strong>", quote.attributionHtml)
        val text = assertIs<HtmlBlock.Text>(blocks[2])
        assertTrue("puzzle game" in text.html)
        assertEquals(3, blocks.size, "caption not repeated as text")
    }

    @Test
    fun `tables keep rows, cells and header cells`() {
        val card = page("/synthetic/work_with_folders.html").cards.first { it.key == "n:damagetable" }
        val blocks = HtmlBlocks.split(card.bodyHtml)
        assertTrue((blocks[0] as HtmlBlock.Text).html.contains("See below"))
        val table = assertIs<HtmlBlock.Table>(blocks[1])
        assertEquals(listOf(listOf("Weapon", "Damage"), listOf("Portal gun", "0")), table.rows.map { r -> r.map { it.html } })
        assertEquals(listOf(true, true), table.rows[0].map { it.isHeader })
    }

    @Test
    fun `plain entries stay one text block`() {
        val card = page("/synthetic/work_with_folders.html").cards.first { it.key == "Main/DeadpanSnarker" }
        assertEquals(1, HtmlBlocks.split(card.bodyHtml).size)
    }

    @Test
    fun `a quote without a dash line has no attribution`() {
        val quote = assertIs<HtmlBlock.Quote>(HtmlBlocks.split("<div class=\"indent\"><em>It's been fun.</em></div>").single())
        assertNull(quote.attributionHtml)
    }

    @Test
    fun `editors' inline icons are dropped and do not split the text`() {
        val card = page("/pages/VideoGame_Portal2.html").cards.first { it.key == "Main/AccentAdaptation" }
        assertTrue("hreficon" !in card.bodyHtml, card.bodyHtml)
        assertIs<HtmlBlock.Text>(HtmlBlocks.split(card.bodyHtml).single())
    }

    @Test
    fun `real page intros split cleanly`() {
        for (name in listOf("Series_BreakingBad", "VideoGame_Portal2", "Main_AbandonedArea")) {
            val blocks = HtmlBlocks.split(page("/pages/$name.html").cards.first().bodyHtml)
            assertIs<HtmlBlock.Image>(blocks.first(), name)
            assertTrue(blocks.any { it is HtmlBlock.Quote }, name)
            assertTrue(blocks.none { it is HtmlBlock.Text && "acaptionright" in it.html }, name)
        }
    }
}
