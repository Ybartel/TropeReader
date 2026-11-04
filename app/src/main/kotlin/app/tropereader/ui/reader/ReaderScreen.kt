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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tropereader.parser.Card
import app.tropereader.parser.CardKind
import app.tropereader.ui.common.BackButton
import app.tropereader.ui.common.CardContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(onBack: () -> Unit, onLink: (String) -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var listMode by rememberSaveable { mutableStateOf(false) }
    var confirmMark by remember { mutableStateOf<Boolean?>(null) }
    // The list closes only once the cursor is on the chosen card: a pager created earlier would
    // start on the old card and report it as settled, undoing the jump.
    var pendingJump by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(state, pendingJump) {
        val target = pendingJump ?: return@LaunchedEffect
        if ((state as? ReaderUiState.Reading)?.cursor?.index == target) {
            listMode = false
            pendingJump = null
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? ReaderUiState.Reading)?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (state is ReaderUiState.Reading) {
                        IconButton(onClick = { listMode = !listMode }) {
                            if (listMode) {
                                Icon(Icons.Filled.ViewCarousel, contentDescription = "Show one card at a time")
                            } else {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show all cards")
                            }
                        }
                        PageMenu(onMark = { confirmMark = it }, onRefresh = { viewModel.refresh() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is ReaderUiState.Loading -> LoadingView(s.part, s.parts)
                is ReaderUiState.Error -> ErrorView(s.message, onRetry = viewModel::load)
                is ReaderUiState.Reading -> if (listMode) {
                    CardListView(s, onJump = { index ->
                        viewModel.jumpTo(index)
                        pendingJump = index
                    })
                } else {
                    PullToRefreshBox(isRefreshing = s.isRefreshing, onRefresh = { viewModel.refresh() }) {
                        // A refresh replaces the cards: start a fresh pager on the cursor's card.
                        key(s.cursor.cards) {
                            ReaderContent(s, onCardSettled = viewModel::onCardSettled, onToggleRead = viewModel::toggleRead, onLink = onLink)
                        }
                    }
                }
            }
        }
    }
    confirmMark?.let { isRead ->
        ConfirmMarkPageDialog(
            isRead = isRead,
            onConfirm = {
                viewModel.markPage(isRead)
                confirmMark = null
            },
            onDismiss = { confirmMark = null },
        )
    }
}

@Composable
private fun PageMenu(onMark: (Boolean) -> Unit, onRefresh: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Refresh page") }, onClick = {
                open = false
                onRefresh()
            })
            DropdownMenuItem(text = { Text("Mark page as read") }, onClick = {
                open = false
                onMark(true)
            })
            DropdownMenuItem(text = { Text("Mark page as unread") }, onClick = {
                open = false
                onMark(false)
            })
        }
    }
}

/** F4.6: whole-page changes always ask first. Shared with the work screen. */
@Composable
fun ConfirmMarkPageDialog(isRead: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isRead) "Mark the whole page as read?" else "Mark the whole page as unread?") },
        text = { Text(if (isRead) "Every card of this page will count as read." else "All progress on this page will be cleared. Your position is kept.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (isRead) "Mark read" else "Mark unread") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** All cards of the page with their state; tapping one jumps to it without marking anything read (F4.5). */
@Composable
private fun CardListView(state: ReaderUiState.Reading, onJump: (Int) -> Unit) {
    val cards = state.cursor.cards
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.cursor.index)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(cards, key = { _, card -> card.key }) { index, card ->
            val context = card.folder ?: card.section
            val previous = cards.getOrNull(index - 1)?.let { it.folder ?: it.section }
            val isRead = card.key in state.readKeys
            Column {
                if (context != null && context != previous) {
                    Text(
                        context,
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ListItem(
                    modifier = Modifier.clickable { onJump(index) },
                    colors = if (index == state.cursor.index) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    leadingContent = { Text("${index + 1}", Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium) },
                    headlineContent = {
                        Text(
                            card.title ?: if (card.kind == CardKind.INTRO) "Introduction" else preview(card).take(60),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = { Text(preview(card), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { if (isRead) Icon(Icons.Filled.Check, contentDescription = "Read") },
                )
            }
        }
    }
}

/** One-line preview with spoilers blanked out, without the entry's name repeated. */
private fun preview(card: Card): String {
    val text = card.segments.joinToString("") { if (it.isSpoiler) "▮▮▮" else it.text }.replace('\n', ' ')
    val title = card.title ?: return text
    return if (text.startsWith(title)) text.removePrefix(title).trimStart(':', ' ') else text
}

@Composable
private fun LoadingView(part: Int, parts: Int) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(if (parts > 1) "Loading part $part of $parts…" else "Loading page…", style = MaterialTheme.typography.bodyMedium)
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
private fun ReaderContent(
    state: ReaderUiState.Reading,
    onCardSettled: (Int) -> Unit,
    onToggleRead: () -> Unit,
    onLink: (String) -> Unit,
) {
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
            CardView(cards[page], onLink)
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
private fun CardView(card: Card, onLink: (String) -> Unit) {
    Surface(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        CardContent(card, onLink, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp))
    }
}
