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
import app.tropereader.domain.RecordCardView
import app.tropereader.domain.RefreshResult
import app.tropereader.domain.RefreshWorkPage
import app.tropereader.domain.SetPageRead
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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
        val isRefreshing: Boolean = false,
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
    private val recordCardView: RecordCardView,
    private val refreshWorkPage: RefreshWorkPage,
) : ViewModel() {
    private val pageId: String = checkNotNull(savedStateHandle[PAGE_ID])

    /** Card to open on instead of the saved position, when coming from history (F8.2). */
    private var requestedCard: String? = savedStateHandle[CARD_KEY]
    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

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
                var cursor = ReadingCursor.resume(page.cards, progress.position(pageId), readKeys)
                requestedCard?.let { key ->
                    page.cards.indexOfFirst { it.key == key }.takeIf { it >= 0 }?.let { cursor = moveToCard(pageId, cursor, it) }
                    requestedCard = null
                }
                _state.value = ReaderUiState.Reading(page.title, pageId, cursor, readKeys)
                recordCardView(pageId, cursor.current)
                observeReadKeys(pageId)
                // Cached pages show at once; older than a week, they are refreshed behind (F9.3).
                if (refreshWorkPage.isStale(pageId)) refresh(silent = true)
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
                recordCardView(reading.pageId, moved.current)
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
                recordCardView(reading.pageId, moved.current)
            }
        }
    }

    /**
     * Downloads the page again and keeps the reader on the same card (F9.3, F5.3).
     * [silent]: automatic refresh, only reports actual changes.
     */
    fun refresh(silent: Boolean = false) {
        val reading = _state.value as? ReaderUiState.Reading ?: return
        if (reading.isRefreshing) return
        _state.value = reading.copy(isRefreshing = true)
        viewModelScope.launch {
            moves.withLock {
                try {
                    val result = refreshWorkPage(pageId, reading.cursor.cards)
                    val before = (_state.value as? ReaderUiState.Reading)?.cursor ?: reading.cursor
                    val key = before.current.key.let { result.changes.moved[it] ?: it }
                    val cards = result.page.cards
                    val readKeys = progress.observeReadKeys(pageId).first()
                    val index = cards.indexOfFirst { it.key == key }
                    val cursor = if (index >= 0) ReadingCursor(cards, index) else ReadingCursor.resume(cards, progress.position(pageId), readKeys)
                    _state.value = ReaderUiState.Reading(result.page.title, pageId, cursor, readKeys)
                    refreshMessage(result, silent)?.let { _messages.send(it) }
                } catch (e: PageLoadException) {
                    _state.update { (it as? ReaderUiState.Reading)?.copy(isRefreshing = false) ?: it }
                    if (!silent) _messages.send("Could not refresh: ${e.message}")
                }
            }
        }
    }

    private fun refreshMessage(result: RefreshResult, silent: Boolean): String? {
        val added = result.changes.added.size
        val removed = result.changes.removed.size
        val edited = result.changes.moved.size
        if (added + removed + edited == 0 && !result.renamed) return if (silent) null else "Page is up to date"
        return buildList {
            if (added > 0) add(if (added == 1) "1 new card" else "$added new cards")
            if (edited > 0) add("$edited edited")
            if (removed > 0) add("$removed removed")
            if (result.renamed) add("page renamed on the site")
        }.joinToString(", ", prefix = "Page updated: ")
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
        const val CARD_KEY = "card"
    }
}
