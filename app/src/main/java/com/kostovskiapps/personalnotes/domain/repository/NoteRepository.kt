package com.kostovskiapps.personalnotes.domain.repository

import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteImportResult
import com.kostovskiapps.personalnotes.domain.model.NoteImportSource
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Storage port for notes. The domain layer only knows this interface; Room, JSON files and
 * Android intents all live behind it in the data layer.
 */
interface NoteRepository {
    fun observeNotes(): Flow<List<Note>>

    fun observeNote(id: Long): Flow<Note?>

    suspend fun getNote(id: Long): Note?

    suspend fun saveNote(note: Note): Long

    suspend fun deleteNote(id: Long)

    /**
     * Reads a note shared by another user (from a `.pnote` file, a share intent or an
     * embedded deep link) and stores it locally.
     */
    suspend fun importNote(
        source: InputStream,
        sourceKind: NoteImportSource,
    ): NoteImportResult

    /** Reads a note without storing it, e.g. to preview an incoming share. */
    suspend fun decodeNote(source: InputStream): Note
}
