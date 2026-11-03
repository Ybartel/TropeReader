package app.tropereader.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration

/**
 * Card HTML as styled text. Spoilers are drawn as solid blocks until revealed (F6.1).
 * Images are dropped for now; thumbnails and tables get dedicated rendering later (F3.8).
 */
@Composable
fun cardHtml(html: String, revealSpoilers: Boolean): AnnotatedString {
    val spoilerColor = String.format("#%06X", MaterialTheme.colorScheme.onSurfaceVariant.toArgb() and 0xFFFFFF)
    val prepared = html
        .replace(IMAGE, "")
        .let { if (revealSpoilers) it else it.replace(SPOILER, "<span style=\"color:$spoilerColor;background-color:$spoilerColor\">") }
    return AnnotatedString.fromHtml(
        prepared,
        linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)),
    )
}

fun hasSpoilers(html: String): Boolean = SPOILER.containsMatchIn(html)

private val IMAGE = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val SPOILER = Regex("<span class=\"spoiler\">")
