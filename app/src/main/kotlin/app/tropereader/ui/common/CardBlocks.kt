package app.tropereader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tropereader.parser.HtmlBlock
import app.tropereader.parser.HtmlBlocks
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/** A card body, block by block (F3.8): text, quotes, tables that scroll sideways, image thumbnails. */
@Composable
internal fun CardBody(cardKey: String, html: String, spoilers: Spoilers, onLink: (String) -> Unit) {
    val blocks = remember(cardKey, html) { HtmlBlocks.split(html) }
    var fullScreen by remember { mutableStateOf<HtmlBlock.Image?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEachIndexed { i, block ->
            val part = "body$i"
            when (block) {
                is HtmlBlock.Text -> Text(cardHtml(block.html, part, spoilers, onLink), style = MaterialTheme.typography.bodyLarge)
                is HtmlBlock.Quote -> QuoteBlock(block, part, spoilers, onLink)
                is HtmlBlock.Table -> TableBlock(block, part, spoilers, onLink)
                is HtmlBlock.Image -> ImageBlock(block, part, spoilers, onLink, onOpen = { fullScreen = block })
            }
        }
    }
    fullScreen?.let { FullScreenImage(it, onDismiss = { fullScreen = null }) }
}

@Composable
private fun QuoteBlock(quote: HtmlBlock.Quote, part: String, spoilers: Spoilers, onLink: (String) -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                cardHtml(quote.html, part, spoilers, onLink),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            )
            quote.attributionHtml?.let {
                Text(
                    cardHtml(it, "${part}a", spoilers, onLink),
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Columns are sized from their longest cell; the table scrolls sideways instead of squeezing the card. */
@Composable
private fun TableBlock(table: HtmlBlock.Table, part: String, spoilers: Spoilers, onLink: (String) -> Unit) {
    val columns = table.rows.maxOf { it.size }
    val widths = remember(table) {
        (0 until columns).map { c ->
            val longest = table.rows.maxOf { row -> row.getOrNull(c)?.html?.replace(TAG, "")?.length ?: 0 }
            (longest * 8).coerceIn(64, 240).dp
        }
    }
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.border(1.dp, outline)) {
            table.rows.forEachIndexed { r, row ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    row.forEachIndexed { c, cell ->
                        Box(
                            Modifier.width(widths[c]).fillMaxHeight()
                                .border(0.5.dp, outline)
                                .background(if (cell.isHeader) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .padding(8.dp),
                        ) {
                            Text(
                                cardHtml(cell.html, "${part}r${r}c$c", spoilers, onLink),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (cell.isHeader) FontWeight.SemiBold else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBlock(image: HtmlBlock.Image, part: String, spoilers: Spoilers, onLink: (String) -> Unit, onOpen: () -> Unit) {
    var failed by remember(image.url) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (failed) {
            Text(
                "Image unavailable",
                Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = image.url,
                contentDescription = image.alt ?: "Page image. Tap to enlarge.",
                contentScale = ContentScale.Fit,
                onState = { failed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen),
            )
        }
        image.captionHtml?.let {
            Text(
                cardHtml(it, "${part}c", spoilers, onLink),
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Tap a thumbnail to see the image full screen; pinch to zoom, tap to close. */
@Composable
private fun FullScreenImage(image: HtmlBlock.Image, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offsetX += pan.x
            offsetY += pan.y
        }
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = image.url,
                contentDescription = image.alt,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().transformable(transform).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            )
        }
    }
}

private val TAG = Regex("<[^>]+>")
