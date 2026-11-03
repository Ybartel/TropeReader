package app.tropereader.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkSpoilersTest {
    @Test
    fun `marks each outermost spoiler, nested spans included`() {
        val html = "A <span class=\"spoiler\">dies <span class=\"note\">(really)</span></span>, then <em>B</em> <span class=\"spoiler\">wins</span>."
        assertEquals(
            "A <span class=\"spoiler\">dies <span class=\"note\">(really)</span></span>, then <em>B</em> <span class=\"spoiler\">wins</span>.",
            markSpoilers(html),
        )
    }
}
