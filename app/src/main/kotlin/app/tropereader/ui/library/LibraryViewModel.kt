package app.tropereader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tropereader.domain.AddWork
import app.tropereader.domain.LibraryRepository
import app.tropereader.domain.PageLoadException
import app.tropereader.domain.WorkSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibrarySort { RECENT, TITLE }

data class LibraryUiState(
    val works: List<WorkSummary> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENT,
    val isAdding: Boolean = false,
    val isLoaded: Boolean = false,
)

sealed interface LibraryEvent {
    data class OpenWork(val workId: String) : LibraryEvent

    data class Error(val message: String) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val addWork: AddWork,
) : ViewModel() {
    private val sort = MutableStateFlow(LibrarySort.RECENT)
    private val adding = MutableStateFlow(false)
    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    val state: StateFlow<LibraryUiState> = combine(library.observeWorks(), sort, adding) { works, sort, adding ->
        LibraryUiState(sorted(works, sort), sort, adding, isLoaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Adds a work from a pasted or shared link, then opens it (F1.1). */
    fun add(text: String) {
        if (adding.value) return
        viewModelScope.launch {
            adding.value = true
            try {
                _events.send(LibraryEvent.OpenWork(addWork(text)))
            } catch (e: PageLoadException) {
                _events.send(LibraryEvent.Error(e.message ?: "Could not add this page."))
            } finally {
                adding.value = false
            }
        }
    }

    fun delete(workId: String) {
        viewModelScope.launch { library.deleteWork(workId) }
    }

    fun setSort(value: LibrarySort) {
        sort.value = value
    }

    private fun sorted(works: List<WorkSummary>, sort: LibrarySort): List<WorkSummary> = when (sort) {
        // Never-read works go last, by title (F1.4).
        LibrarySort.RECENT -> works.sortedWith(compareByDescending<WorkSummary> { it.lastReadAt ?: 0L }.thenBy { it.work.title.lowercase() })
        LibrarySort.TITLE -> works.sortedBy { it.work.title.lowercase() }
    }
}
