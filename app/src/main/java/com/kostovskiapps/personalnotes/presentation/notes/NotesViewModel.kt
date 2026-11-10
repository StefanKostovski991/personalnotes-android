package com.kostovskiapps.personalnotes.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kostovskiapps.personalnotes.di.AppContainer
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteColor
import com.kostovskiapps.personalnotes.domain.model.NoteFilter
import com.kostovskiapps.personalnotes.domain.model.NoteSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val filter: NoteFilter = NoteFilter(),
) {
    val isEmpty: Boolean get() = !isLoading && notes.isEmpty()

    val hasActiveFilter: Boolean
        get() = filter.query.isNotBlank() || filter.color != null || filter.sort != NoteSort.DEFAULT
}

class NotesViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val filter = MutableStateFlow(NoteFilter())
    private val loading = MutableStateFlow(true)

    /** Unfiltered notes, kept so the header can always show the real total. */
    private val allNotes: StateFlow<List<Note>> = container.observeNotes(NoteFilter())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What the list actually shows. */
    private val visibleNotes: StateFlow<List<Note>> = filter
        .flatMapLatest { current -> container.observeNotes(current) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<NotesUiState> =
        combine(visibleNotes, allNotes, filter, loading) { visible, everything, currentFilter, stillLoading ->
            NotesUiState(
                notes = visible,
                totalCount = everything.size,
                isLoading = stillLoading && everything.isEmpty(),
                filter = currentFilter,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    init {
        viewModelScope.launch {
            container.observeNotes(NoteFilter()).collect { loading.value = false }
        }
    }

    fun onQueryChange(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    fun onColorSelected(color: NoteColor?) {
        filter.value = filter.value.copy(color = color)
    }

    fun onSortSelected(sort: NoteSort) {
        filter.value = filter.value.copy(sort = sort)
    }

    fun clearFilters() {
        filter.value = NoteFilter()
    }

    fun delete(note: Note) {
        viewModelScope.launch { container.deleteNote(note.id) }
    }
}
