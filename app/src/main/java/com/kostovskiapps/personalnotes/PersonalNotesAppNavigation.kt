package com.kostovskiapps.personalnotes

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kostovskiapps.personalnotes.di.AppContainer
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.presentation.components.rememberNoteFilePicker
import com.kostovskiapps.personalnotes.presentation.editor.NoteEditorScreen
import com.kostovskiapps.personalnotes.presentation.notes.AppViewModelFactory
import com.kostovskiapps.personalnotes.presentation.notes.EditorViewModel
import com.kostovskiapps.personalnotes.presentation.notes.IncomingNoteViewModel
import com.kostovskiapps.personalnotes.presentation.notes.NotesListScreen
import com.kostovskiapps.personalnotes.presentation.notes.NotesViewModel
import com.kostovskiapps.personalnotes.presentation.share.NoteShareSheet
import com.kostovskiapps.personalnotes.presentation.share.NoteShareViewModel
import com.kostovskiapps.personalnotes.presentation.splash.SplashScreen

/** Navigation routes. The editor takes an id; 0 means "new note". */
object Routes {
    const val SPLASH = "splash"
    const val NOTES = "notes"
    const val EDITOR = "editor/{noteId}"
    const val SHARE = "share/{noteId}"

    fun editor(noteId: Long) = "editor/$noteId"
    fun share(noteId: Long) = "share/$noteId"
}

@Composable
fun PersonalNotesRoot(
    container: AppContainer,
    incomingViewModel: IncomingNoteViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val factory = remember(container) { AppViewModelFactory(container) }

    // Set when the user opens the editor through the "Speak" action.
    var startInSpeakingMode by remember { mutableStateOf(false) }

    val incomingState by incomingViewModel.state.collectAsStateWithLifecycle()

    // A link or a received file decides where the app lands; the list is the fallback.
    LaunchedEffect(incomingState.openedNoteId) {
        val id = incomingState.openedNoteId
        if (id != null) {
            navController.navigate(Routes.editor(id)) { launchSingleTop = true }
            incomingViewModel.consumeOpenedNote()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(260)) + slideInHorizontally { it / 12 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally { it / 12 } },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.NOTES) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.NOTES) {
            val notesViewModel: NotesViewModel = viewModel(factory = factory)
            val pickNoteFile = rememberNoteFilePicker { uri ->
                incomingViewModel.importDocuments(listOf(uri))
            }

            NotesListScreen(
                viewModel = notesViewModel,
                onCreateNote = {
                    startInSpeakingMode = false
                    navController.navigate(Routes.editor(Note.NO_ID))
                },
                onCreateSpokenNote = {
                    startInSpeakingMode = true
                    navController.navigate(Routes.editor(Note.NO_ID))
                },
                onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSharedFile = { pickNoteFile() },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: Note.NO_ID
            val openInSpeakingMode = startInSpeakingMode
            val editorViewModel: EditorViewModel = viewModel(
                key = "editor-$noteId-${entry.id}",
                factory = AppViewModelFactory.editor(container, noteId, openInSpeakingMode),
            )

            NoteEditorScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
                onShare = { id -> navController.navigate(Routes.share(id)) },
            )
        }

        composable(
            route = Routes.SHARE,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: Note.NO_ID
            val shareViewModel: NoteShareViewModel = viewModel(factory = factory)
            LaunchedEffect(noteId) { shareViewModel.prepare(noteId) }
            NoteShareSheet(
                viewModel = shareViewModel,
                onDismiss = { navController.popBackStack() },
            )
        }
    }
}
