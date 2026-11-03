package app.tropereader.ui.linked

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tropereader.domain.AddWork
import app.tropereader.domain.LibraryRepository
import app.tropereader.domain.PageLoadException
import app.tropereader.domain.PageRepository
import app.tropereader.parser.Card
import app.tropereader.parser.ParsedPage
import app.tropereader.ui.common.BackButton
import app.tropereader.ui.common.CardContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LinkedPageUiState {
    data object Loading : LinkedPageUiState

    data class Error(val message: String) : LinkedPageUiState

    /** [workId]: set when the page (or its work) is in the library. */
    data class Loaded(val page: ParsedPage, val workId: String?, val isAdding: Boolean = false) : LinkedPageUiState
}

/**
 * A page opened from a link, on top of the reader (F7.1). Read-only: nothing here touches reading
 * progress, so going back returns to the exact card, unread (F7.2).
 */
@HiltViewModel
class LinkedPageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pages: PageRepository,
    private val library: LibraryRepository,
    private val addWork: AddWork,
) : ViewModel() {
    private val url: String = checkNotNull(savedStateHandle[URL])
    private val _state = MutableStateFlow<LinkedPageUiState>(LinkedPageUiState.Loading)
    val state: StateFlow<LinkedPageUiState> = _state.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = LinkedPageUiState.Loading
            _state.value = try {
                val page = pages.load(url)
                LinkedPageUiState.Loaded(page, library.findWorkByUrl(url) ?: library.findWorkByUrl(page.url))
            } catch (e: PageLoadException) {
                LinkedPageUiState.Error(e.message ?: "Could not load the page.")
            }
        }
    }

    /** Adds this page's work, or the general trope itself (F7.3, F7.5). */
    fun addToLibrary() {
        val loaded = _state.value as? LinkedPageUiState.Loaded ?: return
        if (loaded.isAdding || loaded.workId != null) return
        _state.value = loaded.copy(isAdding = true)
        viewModelScope.launch {
            try {
                val workId = addWork(url)
                _state.update { (it as? LinkedPageUiState.Loaded)?.copy(workId = workId, isAdding = false) ?: it }
                _messages.send("Added to the library")
            } catch (e: PageLoadException) {
                _state.update { (it as? LinkedPageUiState.Loaded)?.copy(isAdding = false) ?: it }
                _messages.send(e.message ?: "Could not add this page.")
            }
        }
    }

    companion object {
        const val URL = "url"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedPageScreen(
    onBack: () -> Unit,
    onLink: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    viewModel: LinkedPageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? LinkedPageUiState.Loaded)?.page?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    val loaded = state as? LinkedPageUiState.Loaded
                    when {
                        loaded == null -> Unit
                        loaded.workId != null -> TextButton(onClick = { onOpenWork(loaded.workId) }) { Text("In library") }
                        else -> TextButton(onClick = viewModel::addToLibrary, enabled = !loaded.isAdding) {
                            Text(if (loaded.isAdding) "Adding…" else "Add to library")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                LinkedPageUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is LinkedPageUiState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::load) { Text("Retry") }
                }
                is LinkedPageUiState.Loaded -> CardList(s.page.cards, onLink)
            }
        }
    }
}

@Composable
private fun CardList(cards: List<Card>, onLink: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(cards, key = { _, card -> card.key }) { index, card ->
            val folder = card.folder ?: card.section
            val previous = cards.getOrNull(index - 1)?.let { it.folder ?: it.section }
            Column {
                if (folder != null && folder != previous) {
                    Text(
                        folder,
                        Modifier.padding(top = 8.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinkedCard(card, onLink)
            }
        }
    }
}

@Composable
private fun LinkedCard(card: Card, onLink: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        CardContent(card, onLink, Modifier.padding(16.dp))
    }
}
