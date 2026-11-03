package app.tropereader.ui.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import app.tropereader.domain.PageLoadException
import app.tropereader.domain.SetPageRead
import app.tropereader.ui.reader.ConfirmMarkPageDialog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tropereader.domain.LibraryRepository
import app.tropereader.domain.PageStatus
import app.tropereader.domain.Work
import app.tropereader.domain.WorkPageState
import app.tropereader.domain.WorkPlanner
import app.tropereader.ui.common.BackButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WorkUiState(val work: Work? = null, val pages: List<WorkPageState> = emptyList())

@HiltViewModel
class WorkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    library: LibraryRepository,
    private val setPageRead: SetPageRead,
) : ViewModel() {
    private val workId: String = checkNotNull(savedStateHandle[WORK_ID])
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<WorkUiState> = combine(library.observeWork(workId), library.observePages(workId), ::WorkUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkUiState())

    /** F4.6 from the work screen; a page never opened is loaded first to know its cards. */
    fun markPage(pageId: String, isRead: Boolean) {
        viewModelScope.launch {
            try {
                setPageRead(pageId, isRead)
            } catch (e: PageLoadException) {
                _messages.send(e.message ?: "Could not load this page.")
            }
        }
    }

    companion object {
        const val WORK_ID = "workId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen(onBack: () -> Unit, onOpenPage: (String) -> Unit, viewModel: WorkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.work?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            WorkPageState.resumeTarget(state.pages)?.let { target ->
                Button(onClick = { onOpenPage(target.page.id) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (target.status == PageStatus.NOT_STARTED) "Start reading" else "Resume · ${label(target)}")
                }
            }
            LazyColumn {
                items(state.pages, key = { it.page.id }) { page ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenPage(page.page.id) },
                        headlineContent = { Text(label(page)) },
                        supportingContent = { Text(statusLabel(page)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (page.status == PageStatus.DONE) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                                }
                                PageMenu(onMark = { confirm = page.page.id to it })
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    confirm?.let { (pageId, isRead) ->
        ConfirmMarkPageDialog(
            isRead = isRead,
            onConfirm = {
                viewModel.markPage(pageId, isRead)
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun PageMenu(onMark: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Page options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Mark as read") }, onClick = {
                open = false
                onMark(true)
            })
            DropdownMenuItem(text = { Text("Mark as unread") }, onClick = {
                open = false
                onMark(false)
            })
        }
    }
}

private fun label(page: WorkPageState): String = if (page.page.role == WorkPlanner.MAIN_ROLE) "Tropes" else page.page.role

private fun statusLabel(page: WorkPageState): String {
    val parts = page.page.sourceUrls.size.takeIf { it > 1 }?.let { " · $it parts" }.orEmpty()
    return when (page.status) {
        PageStatus.NOT_STARTED -> "Not started$parts"
        PageStatus.IN_PROGRESS -> page.cardCount?.let { "In progress · ${page.readCount} / $it" } ?: "In progress · ${page.readCount} read"
        PageStatus.DONE -> "Done · ${page.cardCount} cards"
    }
}
