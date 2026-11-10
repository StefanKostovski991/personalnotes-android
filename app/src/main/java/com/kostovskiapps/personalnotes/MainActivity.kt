package com.kostovskiapps.personalnotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kostovskiapps.personalnotes.presentation.notes.AppViewModelFactory
import com.kostovskiapps.personalnotes.presentation.notes.IncomingNoteViewModel
import com.kostovskiapps.personalnotes.presentation.notes.ReceivedNotesScreen
import com.kostovskiapps.personalnotes.presentation.theme.PersonalNotesTheme

/**
 * The single activity.
 *
 * It owns the two things a Compose tree cannot: the launching intent (deep links and shares)
 * and the hand-off from the system splash screen to the animated one.
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as PersonalNotesApp).container }

    /**
     * Activity scoped on purpose: the launch intent can arrive before composition, and the
     * Compose tree must observe the very same instance.
     */
    private val incomingViewModel: IncomingNoteViewModel by lazy {
        ViewModelProvider(this, AppViewModelFactory(container))
            .get(IncomingNoteViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the platform splash on screen until the first frame is ready, so the user never
        // sees a white flash before the animated splash.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !firstFrameDrawn }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PersonalNotesTheme {
                val incomingState by incomingViewModel.state.collectAsStateWithLifecycle()

                // Compose is running; the platform splash can go away.
                firstFrameDrawn = true

                PersonalNotesRoot(
                    container = container,
                    incomingViewModel = incomingViewModel,
                )

                if (incomingState.received.size > 1) {
                    ReceivedNotesScreen(
                        notes = incomingState.received,
                        onDismiss = incomingViewModel::clearReceived,
                    )
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Deep links and received files are handled by the same ViewModel as everything else, so
     * there is exactly one import path.
     */
    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_MAIN) return
        incomingViewModel.handle(intent)
    }

    private var firstFrameDrawn = false
}
