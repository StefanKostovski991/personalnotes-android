package com.kostovskiapps.personalnotes.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.kostovskiapps.personalnotes.di.AppContainer
import com.kostovskiapps.personalnotes.presentation.share.NoteShareViewModel

/**
 * Builds ViewModels with [AppContainer] injected. A hand written factory keeps the app free of
 * an annotation processor while still giving every screen a constructor-injected ViewModel.
 */
class AppViewModelFactory private constructor(
    private val container: AppContainer,
    private val editorNoteId: Long = 0L,
    private val startInSpeakingMode: Boolean = false,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        when {
            modelClass.isAssignableFrom(NotesViewModel::class.java) ->
                NotesViewModel(container) as T

            modelClass.isAssignableFrom(EditorViewModel::class.java) ->
                EditorViewModel(container, editorNoteId, startInSpeakingMode) as T

            modelClass.isAssignableFrom(IncomingNoteViewModel::class.java) ->
                IncomingNoteViewModel(container) as T

            modelClass.isAssignableFrom(NoteShareViewModel::class.java) ->
                NoteShareViewModel(container) as T

            else -> error("Unknown ViewModel: ${modelClass.name}")
        }

    companion object {
        operator fun invoke(container: AppContainer): AppViewModelFactory =
            AppViewModelFactory(container)

        fun editor(
            container: AppContainer,
            noteId: Long,
            startInSpeakingMode: Boolean,
        ): AppViewModelFactory = AppViewModelFactory(container, noteId, startInSpeakingMode)
    }
}
