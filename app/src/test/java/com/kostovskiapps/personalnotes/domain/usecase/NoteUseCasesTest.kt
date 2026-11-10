package com.kostovskiapps.personalnotes.domain.usecase

import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteColor
import com.kostovskiapps.personalnotes.domain.model.NoteDraft
import com.kostovskiapps.personalnotes.domain.model.NoteFilter
import com.kostovskiapps.personalnotes.domain.model.NoteImportResult
import com.kostovskiapps.personalnotes.domain.model.NoteImportSource
import com.kostovskiapps.personalnotes.domain.model.NoteLocation
import com.kostovskiapps.personalnotes.domain.model.NoteSort
import com.kostovskiapps.personalnotes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

/** In-memory stand-in so the use cases are tested without Room or Android. */
private class FakeNoteRepository(initial: List<Note> = emptyList()) : NoteRepository {

    private val notes = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeNotes(): Flow<List<Note>> = notes

    override fun observeNote(id: Long): Flow<Note?> =
        MutableStateFlow(notes.value.firstOrNull { it.id == id })

    override suspend fun getNote(id: Long): Note? = notes.value.firstOrNull { it.id == id }

    override suspend fun saveNote(note: Note): Long {
        val id = if (note.isPersisted) note.id else nextId++
        notes.value = notes.value.filterNot { it.id == id } + note.copy(id = id)
        return id
    }

    override suspend fun deleteNote(id: Long) {
        notes.value = notes.value.filterNot { it.id == id }
    }

    override suspend fun importNote(source: InputStream, sourceKind: NoteImportSource): NoteImportResult =
        NoteImportResult.Failed("not used")

    override suspend fun decodeNote(source: InputStream): Note = error("not used")
}

private fun note(
    id: Long,
    title: String,
    description: String = "",
    color: NoteColor = NoteColor.DEFAULT,
    updatedAt: Long = id,
    location: NoteLocation = NoteLocation.EMPTY,
) = Note(
    id = id,
    title = title,
    description = description,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    location = location,
    color = color,
)

class NoteUseCasesTest {

    private val alpha = note(1, "Alpha", "first note", updatedAt = 10)
    private val bravo = note(2, "Bravo", "second note about coffee", color = NoteColor.AMBER, updatedAt = 30)
    private val charlie = note(
        id = 3,
        title = "Charlie",
        description = "third",
        color = NoteColor.AMBER,
        updatedAt = 20,
        location = NoteLocation("Vienna"),
    )

    private val repository = FakeNoteRepository(listOf(alpha, bravo, charlie))
    private val observeNotes = ObserveNotesUseCase(repository)

    @Test
    fun `default sort is newest first`() = runTest {
        val result = observeNotes(NoteFilter()).first()

        assertEquals(listOf(2L, 3L, 1L), result.map { it.id })
    }

    @Test
    fun `oldest sort reverses the order`() = runTest {
        val result = observeNotes(NoteFilter(sort = NoteSort.OLDEST)).first()

        assertEquals(listOf(1L, 3L, 2L), result.map { it.id })
    }

    @Test
    fun `title sort is alphabetical and case insensitive`() = runTest {
        val result = observeNotes(NoteFilter(sort = NoteSort.TITLE)).first()

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), result.map { it.title })
    }

    @Test
    fun `query matches title, description and place`() = runTest {
        assertEquals(listOf(2L), observeNotes(NoteFilter(query = "coffee")).first().map { it.id })
        assertEquals(listOf(3L), observeNotes(NoteFilter(query = "vienna")).first().map { it.id })
        assertEquals(listOf(1L), observeNotes(NoteFilter(query = "ALPHA")).first().map { it.id })
    }

    @Test
    fun `colour filter keeps only the chosen colour`() = runTest {
        val result = observeNotes(NoteFilter(color = NoteColor.AMBER)).first()

        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test
    fun `colour and query filters combine`() = runTest {
        val result = observeNotes(NoteFilter(query = "third", color = NoteColor.AMBER)).first()

        assertEquals(listOf(3L), result.map { it.id })
    }

    @Test
    fun `unsaved draft is rejected when it is completely empty`() = runTest {
        val useCase = UpsertNoteUseCase(repository)

        val outcome = useCase(NoteDraft())

        assertEquals(UpsertNoteUseCase.Outcome.NothingToSave, outcome)
    }

    @Test
    fun `saving trims input and sets timestamps`() = runTest {
        val useCase = UpsertNoteUseCase(repository) { 5_000L }

        val outcome = useCase(NoteDraft(title = "  Padded  ", description = "  body  "))

        assertTrue(outcome is UpsertNoteUseCase.Outcome.Saved)
        val saved = repository.getNote((outcome as UpsertNoteUseCase.Outcome.Saved).id)!!
        assertEquals("Padded", saved.title)
        assertEquals("body", saved.description)
        assertEquals(5_000L, saved.updatedAt)
    }

    @Test
    fun `a recording without text is still saved`() = runTest {
        val useCase = UpsertNoteUseCase(repository) { 1L }

        val outcome = useCase(NoteDraft(fileRefs = listOf("voice-1.m4a")))

        assertTrue(outcome is UpsertNoteUseCase.Outcome.Saved)
    }

    @Test
    fun `existing note keeps its original creation time`() = runTest {
        val useCase = UpsertNoteUseCase(repository) { 9_000L }

        val outcome = useCase(NoteDraft(id = 1L, title = "Alpha", createdAt = 111L))

        val saved = repository.getNote((outcome as UpsertNoteUseCase.Outcome.Saved).id)!!
        assertEquals(111L, saved.createdAt)
        assertEquals(9_000L, saved.updatedAt)
    }

    @Test
    fun `delete removes the note`() = runTest {
        val useCase = DeleteNoteUseCase(repository)

        useCase(1L)

        assertEquals(null, repository.getNote(1L))
        assertEquals(2, repository.observeNotes().first().size)
    }
}
