package app.tropereader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tropereader.parser.Card
import app.tropereader.parser.CardKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text((state as? ReaderUiState.Reading)?.title ?: "TropeReader", maxLines = 1, overflow = TextOverflow.Ellipsis)
            })
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ReaderUiState.Loading -> LoadingView()
                is ReaderUiState.Error -> ErrorView(s.message, onRetry = viewModel::load)
                is ReaderUiState.Reading -> ReaderContent(s, onCardSettled = viewModel::onCardSettled, onToggleRead = viewModel::toggleRead)
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Loading page…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun ReaderContent(state: ReaderUiState.Reading, onCardSettled: (Int) -> Unit, onToggleRead: () -> Unit) {
    val cards = state.cursor.cards
    val pagerState = rememberPagerState(initialPage = state.cursor.index) { cards.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(onCardSettled)
    }
    val current = state.cursor.current
    val isRead = current.key in state.readKeys

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The folder is the useful context (F4.4); headings can be whole sentences on this site.
            Text(
                (current.folder ?: current.section).orEmpty(),
                Modifier.weight(1f).padding(end = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${state.cursor.index + 1} / ${cards.size}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { contentDescription = "Card ${state.cursor.index + 1} of ${cards.size}" },
            )
        }
        HorizontalPager(pagerState, Modifier.weight(1f), beyondViewportPageCount = 1, key = { cards[it].key }) { page ->
            CardView(cards[page])
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled = state.cursor.hasPrevious,
            ) { Text("Previous") }
            FilledTonalButton(onClick = onToggleRead, modifier = Modifier.weight(1f)) {
                Text(if (isRead) "Read ✓" else "Unread")
            }
            Button(
                onClick = {
                    // On the last card, "Next" still marks it read.
                    if (state.cursor.hasNext) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } else if (!isRead) onToggleRead()
                },
            ) { Text("Next") }
        }
    }
}

@Composable
private fun CardView(card: Card) {
    var revealSpoilers by rememberSaveable(card.key) { mutableStateOf(false) }
    val spoilers = remember(card.key) { hasSpoilers(card.bodyHtml) || card.leadHtml?.let(::hasSpoilers) == true }
    Surface(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
                .clickable(enabled = spoilers) { revealSpoilers = !revealSpoilers },
        ) {
            if (card.kind == CardKind.EXAMPLE) {
                card.title?.let { Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                card.leadHtml?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(cardHtml(it, revealSpoilers), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
            } else if (card.kind == CardKind.INTRO) {
                Text("Introduction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
            }
            Text(cardHtml(card.bodyHtml, revealSpoilers), style = MaterialTheme.typography.bodyLarge)
            if (spoilers) {
                Spacer(Modifier.height(16.dp))
                Text(
                    if (revealSpoilers) "Tap to hide spoilers" else "Tap to reveal spoilers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
