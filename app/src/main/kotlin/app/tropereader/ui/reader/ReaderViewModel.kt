package app.tropereader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tropereader.domain.AdvanceToNextCard
import app.tropereader.domain.LibraryRepository
import app.tropereader.domain.MoveToCard
import app.tropereader.domain.PageLoadException
import app.tropereader.domain.PageRepository
import app.tropereader.domain.ReadingCursor
import app.tropereader.domain.ReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Error(val message: String) : ReaderUiState

    data class Reading(
        val title: String,
        val pageId: String,
        val cursor: ReadingCursor,
        val readKeys: Set<String>,
    ) : ReaderUiState
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val pages: PageRepository,
    private val library: LibraryRepository,
    private val progress: ReadingProgressRepository,
    private val advanceToNextCard: AdvanceToNextCard,
    private val moveToCard: MoveToCard,
) : ViewModel() {
    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Moves are applied one at a time, in order, so a fast double swipe cannot skip a save. */
    private val moves = Mutex()
    private var readKeysJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ReaderUiState.Loading
            try {
                val page = pages.load(DEMO_URL)
                if (page.cards.isEmpty()) throw PageLoadException("This page has no cards.")
                val pageId = library.ensureSinglePageWork(DEMO_URL, page)
                val readKeys = progress.observeReadKeys(pageId).first()
                val cursor = ReadingCursor.resume(page.cards, progress.position(pageId), readKeys)
                _state.value = ReaderUiState.Reading(page.title, pageId, cursor, readKeys)
                observeReadKeys(pageId)
            } catch (e: PageLoadException) {
                _state.value = ReaderUiState.Error(e.message ?: "Could not load the page.")
            }
        }
    }

    /** The pager settled on [index], by swipe or button: one step forward advances, anything else just moves. */
    fun onCardSettled(index: Int) {
        viewModelScope.launch {
            moves.withLock {
                val reading = _state.value as? ReaderUiState.Reading ?: return@withLock
                val cursor = reading.cursor
                val moved = when (index) {
                    cursor.index -> return@withLock
                    cursor.index + 1 -> advanceToNextCard(reading.pageId, cursor)
                    else -> moveToCard(reading.pageId, cursor, index)
                }
                _state.update { (it as? ReaderUiState.Reading)?.copy(cursor = moved) ?: it }
            }
        }
    }

    fun toggleRead() {
        val reading = _state.value as? ReaderUiState.Reading ?: return
        val key = reading.cursor.current.key
        viewModelScope.launch { progress.setRead(reading.pageId, key, key !in reading.readKeys) }
    }

    private fun observeReadKeys(pageId: String) {
        readKeysJob?.cancel()
        readKeysJob = viewModelScope.launch {
            progress.observeReadKeys(pageId).collect { keys ->
                _state.update { (it as? ReaderUiState.Reading)?.copy(readKeys = keys) ?: it }
            }
        }
    }

    private companion object {
        /** Step 2 of the roadmap reads one hard-coded page; the library comes in step 3. */
        const val DEMO_URL = "https://tvtropes.org/pmwiki/pmwiki.php/VideoGame/Portal2"
    }
}
