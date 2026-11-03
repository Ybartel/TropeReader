package app.tropereader.ui.work

import androidx.compose.foundation.clickable
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
class WorkViewModel @Inject constructor(savedStateHandle: SavedStateHandle, library: LibraryRepository) : ViewModel() {
    private val workId: String = checkNotNull(savedStateHandle[WORK_ID])

    val state: StateFlow<WorkUiState> = combine(library.observeWork(workId), library.observePages(workId), ::WorkUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkUiState())

    companion object {
        const val WORK_ID = "workId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen(onBack: () -> Unit, onOpenPage: (String) -> Unit, viewModel: WorkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
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
                            if (page.status == PageStatus.DONE) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
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
