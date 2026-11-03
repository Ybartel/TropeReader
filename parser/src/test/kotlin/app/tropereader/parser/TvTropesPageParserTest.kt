package app.tropereader.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvTropesPageParserTest {
    private val parser = TvTropesPageParser()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/synthetic/$name")) { "missing fixture $name" }.readText()

    private fun parse(name: String, html: String = fixture(name)) =
        parser.parse(html, "https://tvtropes.org/pmwiki/pmwiki.php/Fallback/Page")

    private val work by lazy { parse("work_with_folders.html") }

    @Test
    fun `reads page identity from canonical url and title`() {
        assertEquals("VideoGame/SampleGame", work.path)
        assertEquals("VideoGame", work.namespace)
        assertEquals("Sample Game", work.title)
        assertEquals(PageType.WORK, work.pageType)
        assertEquals(listOf("Characters/SampleGame", "YMMV/SampleGame"), work.relatedPages.map { it.path })
    }

    @Test
    fun `intro card holds everything before the first rule`() {
        val intro = work.cards.first()
        assertEquals(CardKind.INTRO, intro.kind)
        assertEquals("intro", intro.key)
        assertTrue("img" in intro.bodyHtml, "image kept in HTML")
        assertTrue("puzzle game" in intro.plainText)
        assertTrue("(An aside)" in intro.plainText, "inline note read in parentheses")
        assertFalse("note" in intro.plainText.substringBefore("(An aside)"), "note toggle label dropped")
        assertFalse("Advertisement" in intro.plainText)
    }

    @Test
    fun `folders are flattened with their label as context`() {
        val abandoned = work.cards.first { it.key == "Main/AbandonedArea" }
        assertEquals("Tropes A-C", abandoned.folder)
        assertEquals("These are tropes", abandoned.section)
        assertEquals("Tropes D-F", work.cards.first { it.key == "Main/DeadpanSnarker" }.folder)
        assertTrue(work.cards.none { "open/close" in it.plainText })
    }

    @Test
    fun `sub-bullets become one card each with the parent as header`() {
        val examples = work.cards.filter { it.parentKey == "Main/AbortedArc" }
        assertEquals(2, examples.size)
        assertTrue(examples.all { it.kind == CardKind.EXAMPLE && it.title == "Aborted Arc" })
        assertTrue(examples.all { it.leadHtml!!.contains("Two threads go nowhere") })
        assertTrue(examples.all { it.key.startsWith("Main/AbortedArc#") })
        assertTrue("sequel revisits" in examples[1].plainText, "deeper bullets stay inside their card")
    }

    @Test
    fun `entry keys come from links, then names, then text`() {
        val keys = work.cards.map { it.key }
        assertTrue("Main/The50s+Main/The70s" in keys)
        assertTrue("n:damagetable" in keys)
        assertTrue(keys.any { it.startsWith("e:") }, "unnamed entry keyed by text hash")
        assertTrue(keys.any { it.startsWith("x:") }, "closing quote kept as text card")
    }

    @Test
    fun `repeated keys get a suffix`() {
        val keys = work.cards.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue("Main/AbandonedArea~2" in keys)
    }

    @Test
    fun `spoilers are flagged in text segments`() {
        val card = work.cards.first { it.key == "Main/AbandonedArea" }
        assertEquals(listOf(false, true, false), card.segments.map { it.isSpoiler })
        assertEquals("The house at the very end", card.segments[1].text)
        assertTrue("class=\"spoiler\"" in card.bodyHtml)
    }

    @Test
    fun `html is sanitized with absolute links`() {
        val card = work.cards.first { it.key == "Main/DeadpanSnarker" }
        assertTrue("href=\"https://tvtropes.org/pmwiki/pmwiki.php/Main/DeadpanSnarker\"" in card.bodyHtml)
        assertTrue("href=\"https://example.com/wiki\"" in card.bodyHtml)
        assertFalse("twikilink" in card.bodyHtml)
        assertFalse("data-format" in card.bodyHtml)
        assertEquals("https://tvtropes.org/pmwiki/pmwiki.php/Main/DeadpanSnarker", card.titleUrl)
    }

    @Test
    fun `text the site hides stays hidden`() {
        val card = work.cards.first { it.key == "Main/DeadpanSnarker" }
        assertTrue("with their snark." in card.plainText, card.plainText)
        assertFalse("invoked" in card.bodyHtml)
    }

    @Test
    fun `tables are read row by row`() {
        val card = work.cards.first { it.key == "n:damagetable" }
        assertTrue("<table>" in card.bodyHtml)
        assertTrue("Weapon, Damage\nPortal gun, 0" in card.plainText, card.plainText)
    }

    @Test
    fun `keys survive cosmetic edits and new entries`() {
        val before = work.cards.map { it.key }.toSet()
        val edited = fixture("work_with_folders.html")
            .replace("The kleptomania subplot is dropped.", "the  Kleptomania subplot is DROPPED")
            .replace("Though a sequel revisits it.", "Though a sequel revisits it. A reply was added.")
            .replace(
                "<li> <a class=\"twikilink\" href=\"/pmwiki/pmwiki.php/Main/AbortedArc\">",
                "<li> <a class=\"twikilink\" href=\"/pmwiki/pmwiki.php/Main/AbsentAliens\">Absent Aliens</a>: New.</li><li> <a class=\"twikilink\" href=\"/pmwiki/pmwiki.php/Main/AbortedArc\">",
            )
        val after = parse("work_with_folders.html", edited).cards.map { it.key }.toSet()
        assertEquals(setOf("Main/AbsentAliens"), after - before)
        assertEquals(emptySet(), before - after)
    }

    @Test
    fun `alphabetical subpages are listed, not turned into cards`() {
        val page = parse("split_work.html")
        assertEquals(listOf("SampleShow/TropesAToB", "SampleShow/TropesCToZ"), page.splitParts.map { it.path })
        assertTrue(page.cards.none { "Tropes A to B" in it.plainText })
        assertEquals(listOf(CardKind.INTRO, CardKind.TEXT), page.cards.map { it.kind })
    }

    @Test
    fun `recap index lists episodes in order`() {
        val page = parse("recap_index.html")
        assertEquals(PageType.RECAP_INDEX, page.pageType)
        assertEquals(
            listOf("Recap/SampleShowS1E1Pilot", "Recap/SampleShowS1E2Bag", "Recap/SampleShowS2E1Seven"),
            page.recapEpisodes.map { it.path },
        )
        assertEquals("Season 1 (2008)", page.cards.first { it.title == null && it.kind == CardKind.TEXT }.folder)
    }

    @Test
    fun `general trope pages group examples by media folder`() {
        val page = parse("general_trope.html")
        assertEquals(PageType.GENERAL_TROPE, page.pageType)
        val film = page.cards.first { it.key == "Film/SampleFilm" }
        assertEquals("Film", film.folder)
        assertEquals("Sample Film", film.title)
        val game = page.cards.filter { it.parentKey == "VideoGame/SampleGame" }
        assertEquals(listOf("The crowbar.", "The cake."), game.map { it.plainText })
        assertTrue(game.all { it.folder == "Video Games" })
        assertNull(page.cards.first { it.key.startsWith("e:") }.title)
    }

    @Test
    fun `pages without an article are rejected`() {
        assertFailsWith<PageParseException> { parse("none", "<html><body><p>Just a moment...</p></body></html>") }
    }
}
