package com.kostovskiapps.personalnotes.di

import android.content.Context
import com.kostovskiapps.personalnotes.data.local.NoteDao
import com.kostovskiapps.personalnotes.data.local.NoteDatabase
import com.kostovskiapps.personalnotes.data.location.AndroidLocationProvider
import com.kostovskiapps.personalnotes.data.repository.AndroidNoteShareRepository
import com.kostovskiapps.personalnotes.data.repository.OfflineNoteRepository
import com.kostovskiapps.personalnotes.data.share.AttachmentStore
import com.kostovskiapps.personalnotes.data.voice.SpeechRecognizerGateway
import com.kostovskiapps.personalnotes.data.voice.VoicePlayer
import com.kostovskiapps.personalnotes.data.voice.VoiceRecorder
import com.kostovskiapps.personalnotes.domain.model.NoteDraft
import com.kostovskiapps.personalnotes.domain.repository.LocationProvider
import com.kostovskiapps.personalnotes.domain.repository.NoteRepository
import com.kostovskiapps.personalnotes.domain.repository.NoteShareRepository
import com.kostovskiapps.personalnotes.domain.usecase.BuildNoteLinkUseCase
import com.kostovskiapps.personalnotes.domain.usecase.DeleteNoteUseCase
import com.kostovskiapps.personalnotes.domain.usecase.ExportNoteUseCase
import com.kostovskiapps.personalnotes.domain.usecase.GetNoteUseCase
import com.kostovskiapps.personalnotes.domain.usecase.ImportNoteUseCase
import com.kostovskiapps.personalnotes.domain.usecase.ObserveNotesUseCase
import com.kostovskiapps.personalnotes.domain.usecase.UpsertNoteUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * The app's object graph.
 *
 * A hand written container is used instead of an annotation-processor DI framework: the graph
 * is small, everything is constructor-injected, and the domain/usecase layer stays pure
 * Kotlin. Swapping in Hilt later only means replacing this one class with modules.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Exposed for the few data-layer helpers that must read content:// uris. */
    val context: Context get() = appContext

    /** Outlives any screen: a recording must survive a configuration change. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: NoteDatabase by lazy { NoteDatabase.build(appContext) }

    val noteDao: NoteDao by lazy { database.noteDao() }

    val attachmentStore: AttachmentStore by lazy { AttachmentStore(appContext) }

    val locationProvider: LocationProvider by lazy { AndroidLocationProvider(appContext) }

    val voiceRecorder: VoiceRecorder by lazy {
        VoiceRecorder(attachmentStore, applicationScope)
    }

    val voicePlayer: VoicePlayer by lazy {
        VoicePlayer(attachmentStore, applicationScope)
    }

    val speechRecognizerFactory: () -> SpeechRecognizerGateway = {
        SpeechRecognizerGateway(appContext)
    }

    val noteRepository: NoteRepository by lazy {
        OfflineNoteRepository(dao = noteDao, attachments = attachmentStore)
    }

    val noteShareRepository: NoteShareRepository by lazy {
        AndroidNoteShareRepository(context = appContext, attachments = attachmentStore)
    }

    /**
     * Validates and writes a note on an application-lifetime scope.
     *
     * Used by the editor, where the screen may be destroyed the moment the user taps save or
     * back. The write is additionally [NonCancellable] so it always runs to completion rather
     * than being cut off half way through moving a recording into place. The returned deferred
     * lets a caller that is still on screen wait for the real id.
     */
    fun persistNote(draft: NoteDraft): Deferred<UpsertNoteUseCase.Outcome> =
        applicationScope.async {
            withContext(NonCancellable) { upsertNote(draft) }
        }

    val observeNotes: ObserveNotesUseCase by lazy { ObserveNotesUseCase(noteRepository) }
    val getNote: GetNoteUseCase by lazy { GetNoteUseCase(noteRepository) }
    val upsertNote: UpsertNoteUseCase by lazy { UpsertNoteUseCase(noteRepository) }
    val deleteNote: DeleteNoteUseCase by lazy { DeleteNoteUseCase(noteRepository) }
    val importNote: ImportNoteUseCase by lazy { ImportNoteUseCase(noteRepository) }
    val exportNote: ExportNoteUseCase by lazy { ExportNoteUseCase(noteShareRepository) }
    val buildNoteLink: BuildNoteLinkUseCase by lazy { BuildNoteLinkUseCase(noteShareRepository) }
}
