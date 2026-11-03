package app.tropereader.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.tropereader.parser.Card
import app.tropereader.parser.CardKind

/**
 * A card's text: header for examples and intros, then the body. Shared by the reader and linked pages.
 * Link taps go to [onLink] (in-app navigation or browser, F7). Each spoiler stays hidden until tapped (F6.1).
 */
@Composable
fun CardContent(card: Card, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    var revealed by rememberSaveable(card.key) { mutableStateOf(emptySet<String>()) }
    val reveal: (String) -> Unit = { revealed = revealed + it }
    Column(modifier) {
        if (card.kind == CardKind.EXAMPLE) {
            card.title?.let { Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            card.leadHtml?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    cardHtml(it, "lead", revealed, reveal, onLink),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        } else if (card.kind == CardKind.INTRO) {
            Text("Introduction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
        }
        Text(cardHtml(card.bodyHtml, "body", revealed, reveal, onLink), style = MaterialTheme.typography.bodyLarge)
        val hidden = card.spoilerCount() - revealed.size
        if (hidden > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (hidden == 1) "1 spoiler hidden · tap it to reveal" else "$hidden spoilers hidden · tap one to reveal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Card.spoilerCount(): Int =
    SPOILER.findAll(bodyHtml).count() + (leadHtml?.let { SPOILER.findAll(it).count() } ?: 0)

/**
 * Card HTML as styled text. Spoilers are marked with private-use characters before conversion, then
 * drawn as solid blocks (hidden) or highlighted (revealed). Images are dropped for now (F3.8).
 */
@Composable
private fun cardHtml(
    html: String,
    part: String,
    revealed: Set<String>,
    onReveal: (String) -> Unit,
    onLink: (String) -> Unit,
): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val text = AnnotatedString.fromHtml(
        markSpoilers(html.replace(IMAGE, "")),
        linkStyles = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)),
        linkInteractionListener = LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.url?.let(onLink) },
    )
    val hidden = SpanStyle(color = colors.onSurfaceVariant, background = colors.onSurfaceVariant)
    val shown = SpanStyle(background = colors.secondaryContainer)
    return buildAnnotatedString {
        val raw = text.text
        var from = 0
        var index = 0
        while (true) {
            val start = raw.indexOf(SPOILER_START, from)
            if (start < 0) {
                append(text.subSequence(from, raw.length))
                break
            }
            append(text.subSequence(from, start))
            val end = raw.indexOf(SPOILER_END, start + 1).let { if (it < 0) raw.length else it }
            val id = "$part:${index++}"
            val content = text.subSequence(start + 1, end)
            if (id in revealed) {
                withStyle(shown) { append(content) }
            } else {
                withLink(LinkAnnotation.Clickable(id) { onReveal(id) }) { withStyle(hidden) { append(content) } }
            }
            from = minOf(end + 1, raw.length)
        }
    }
}

/** Wraps the content of each outermost `span.spoiler` in [SPOILER_START] / [SPOILER_END]. */
internal fun markSpoilers(html: String): String {
    val out = StringBuilder()
    var last = 0
    var depth = 0
    var spoilerDepth = -1
    for (tag in SPAN_TAG.findAll(html)) {
        out.append(html, last, tag.range.first)
        if (tag.groupValues[1].isEmpty()) {
            depth++
            out.append(tag.value)
            if (spoilerDepth < 0 && tag.value.contains("class=\"spoiler\"")) {
                spoilerDepth = depth
                out.append(SPOILER_START)
            }
        } else {
            if (depth == spoilerDepth) {
                out.append(SPOILER_END)
                spoilerDepth = -1
            }
            out.append(tag.value)
            depth--
        }
        last = tag.range.last + 1
    }
    out.append(html, last, html.length)
    return out.toString()
}

private const val SPOILER_START = ''
private const val SPOILER_END = ''
private val SPAN_TAG = Regex("<(/?)span\\b[^>]*>")
private val IMAGE = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val SPOILER = Regex("<span class=\"spoiler\">")
