package com.kostovskiapps.personalnotes.domain.usecase

import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteImportResult
import com.kostovskiapps.personalnotes.domain.model.NoteImportSource
import com.kostovskiapps.personalnotes.domain.repository.ExportedNote
import com.kostovskiapps.personalnotes.domain.repository.NoteRepository
import com.kostovskiapps.personalnotes.domain.repository.NoteShareRepository
import java.io.InputStream

/** Ingests a note another user shared with us (file, intent or deep link). */
class ImportNoteUseCase(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(
        source: InputStream,
        sourceKind: NoteImportSource = NoteImportSource.UNKNOWN,
    ): NoteImportResult = repository.importNote(source, sourceKind)
}

/** Turns a note into a shareable `.pnote` file plus a self-contained deep link. */
class ExportNoteUseCase(
    private val shareRepository: NoteShareRepository,
) {
    suspend operator fun invoke(note: Note): ExportedNote = shareRepository.exportNote(note)
}

/** Builds only the link, for "copy link" style actions. */
class BuildNoteLinkUseCase(
    private val shareRepository: NoteShareRepository,
) {
    operator fun invoke(note: Note): String = shareRepository.buildShareLink(note)
}
