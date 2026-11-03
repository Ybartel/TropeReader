package app.tropereader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tropereader.domain.WorkSummary
import app.tropereader.ui.common.relativeDay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
    onOpenWork: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<WorkSummary?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.OpenWork -> onOpenWork(event.workId)
                is LibraryEvent.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }
    // A link shared from the browser (F1.1).
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            viewModel.add(sharedText)
            onSharedTextConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    TextButton(onClick = {
                        viewModel.setSort(if (state.sort == LibrarySort.RECENT) LibrarySort.TITLE else LibrarySort.RECENT)
                    }) { Text(if (state.sort == LibrarySort.RECENT) "Sort: recent" else "Sort: title") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.isAdding) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Adding…",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.isLoaded && state.works.isEmpty() && !state.isAdding) {
                EmptyLibrary()
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.works, key = { it.work.id }) { summary ->
                        WorkItem(summary, onOpen = { onOpenWork(summary.work.id) }, onDelete = { toDelete = summary })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddWorkDialog(
            onAdd = {
                showAdd = false
                viewModel.add(it)
            },
            onDismiss = { showAdd = false },
        )
    }
    toDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remove ${summary.work.title}?") },
            text = { Text("It leaves the library. Adding it again restores your progress.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(summary.work.id)
                    toDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyLibrary() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Add a work with its TvTropes link, or share a page to TropeReader from your browser.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WorkItem(summary: WorkSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(summary.work.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // General tropes live in the `Main` namespace.
        overlineContent = { Text(if (summary.work.mediaType == "Main") "Trope" else summary.work.mediaType) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(progressLabel(summary))
                LinearProgressIndicator(progress = { summary.progress }, modifier = Modifier.fillMaxWidth())
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove") }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        },
    )
}

private fun progressLabel(summary: WorkSummary): String {
    if (summary.lastReadAt == null && summary.readCount == 0) return "Not started · ${summary.pageCount} pages"
    val percent = (summary.progress * 100).roundToInt().let { if (it == 0 && summary.readCount > 0) "<1" else "$it" }
    // Totals are only known for opened pages, so say so until all of them have been opened.
    val scope = if (summary.openedPageCount < summary.pageCount) " of opened pages" else ""
    val last = summary.lastReadAt?.let { " · read ${relativeDay(it)}" }.orEmpty()
    return "$percent%$scope$last"
}

@Composable
private fun AddWorkDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a work") },
        text = {
            Column {
                Text("Paste the link of a work or trope page.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("https://tvtropes.org/pmwiki/pmwiki.php/…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
