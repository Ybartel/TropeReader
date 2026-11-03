package app.tropereader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tropereader.domain.AdvanceToNextCard
import app.tropereader.domain.LoadWorkPage
import app.tropereader.domain.MoveToCard
import app.tropereader.domain.PageLoadException
import app.tropereader.domain.ReadingCursor
import app.tropereader.domain.ReadingProgressRepository
import app.tropereader.domain.SetPageRead
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
    /** [part] / [parts]: progress while the sources of a merged page load one per second. */
    data class Loading(val part: Int = 0, val parts: Int = 0) : ReaderUiState

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
    savedStateHandle: SavedStateHandle,
    private val loadWorkPage: LoadWorkPage,
    private val progress: ReadingProgressRepository,
    private val advanceToNextCard: AdvanceToNextCard,
    private val moveToCard: MoveToCard,
    private val setPageRead: SetPageRead,
) : ViewModel() {
    private val pageId: String = checkNotNull(savedStateHandle[PAGE_ID])
    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Moves are applied one at a time, in order, so a fast double swipe cannot skip a save. */
    private val moves = Mutex()
    private var readKeysJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ReaderUiState.Loading()
            try {
                val page = loadWorkPage(pageId) { part, parts -> _state.value = ReaderUiState.Loading(part, parts) }
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

    /** Jump from the list view (F4.5): never marks anything read, even one card ahead. */
    fun jumpTo(index: Int) {
        viewModelScope.launch {
            moves.withLock {
                val reading = _state.value as? ReaderUiState.Reading ?: return@withLock
                val moved = moveToCard(reading.pageId, reading.cursor, index)
                _state.update { (it as? ReaderUiState.Reading)?.copy(cursor = moved) ?: it }
            }
        }
    }

    /** Marks the whole page read or unread (F4.6). */
    fun markPage(isRead: Boolean) {
        val reading = _state.value as? ReaderUiState.Reading ?: return
        viewModelScope.launch { setPageRead(reading.pageId, isRead, reading.cursor.cards.map { it.key }) }
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

    companion object {
        const val PAGE_ID = "pageId"
    }
}
