package com.kostovskiapps.personalnotes.domain.usecase

import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteDraft
import com.kostovskiapps.personalnotes.domain.model.NoteFilter
import com.kostovskiapps.personalnotes.domain.model.NoteKind
import com.kostovskiapps.personalnotes.domain.model.NoteSort
import com.kostovskiapps.personalnotes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the notes list needs, already filtered and sorted. */
class ObserveNotesUseCase(
    private val repository: NoteRepository,
) {
    operator fun invoke(filter: NoteFilter): Flow<List<Note>> =
        repository.observeNotes().map { notes -> applyFilter(notes, filter) }

    private fun applyFilter(notes: List<Note>, filter: NoteFilter): List<Note> {
        val query = filter.query.trim()
        val filtered = notes.filter { note ->
            (filter.color == null || note.color == filter.color) &&
                (
                    query.isEmpty() ||
                        note.title.contains(query, ignoreCase = true) ||
                        note.description.contains(query, ignoreCase = true) ||
                        note.location.label.contains(query, ignoreCase = true)
                    )
        }

        return when (filter.sort) {
            NoteSort.NEWEST -> filtered.sortedByDescending { it.updatedAt }
            NoteSort.OLDEST -> filtered.sortedBy { it.updatedAt }
            NoteSort.TITLE -> filtered.sortedBy { it.displayTitle.lowercase() }
            NoteSort.COLOR -> filtered.sortedWith(
                compareBy({ it.color.ordinal }, { -it.updatedAt }),
            )
        }
    }
}

/** Loads a single note for the editor, or null when it is a brand new note. */
class GetNoteUseCase(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(id: Long): Note? =
        if (id == Note.NO_ID) null else repository.getNote(id)
}

/**
 * Validates and persists a note. Blank notes are rejected so the list never fills up with
 * empty cards after an accidental back press.
 */
class UpsertNoteUseCase(
    private val repository: NoteRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Outcome {
        data class Saved(val id: Long) : Outcome

        data object NothingToSave : Outcome
    }

    suspend operator fun invoke(draft: NoteDraft): Outcome {
        if (draft.title.isBlank() && draft.description.isBlank() && draft.fileRefs.isEmpty()) {
            return Outcome.NothingToSave
        }
        val now = clock()
        val note = Note(
            id = draft.id,
            title = draft.title.trim(),
            description = draft.description.trim(),
            createdAt = if (draft.createdAt == 0L) now else draft.createdAt,
            updatedAt = now,
            location = draft.location,
            color = draft.color,
            kind = resolveKind(draft),
            fileRefs = draft.fileRefs,
        )
        return Outcome.Saved(repository.saveNote(note))
    }

    private fun resolveKind(draft: NoteDraft): NoteKind = when {
        draft.fileRefs.isNotEmpty() && draft.description.isNotBlank() -> NoteKind.MIXED
        draft.fileRefs.isNotEmpty() -> NoteKind.SPOKEN
        else -> NoteKind.TYPED
    }
}

class DeleteNoteUseCase(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(id: Long) {
        if (id != Note.NO_ID) repository.deleteNote(id)
    }
}
