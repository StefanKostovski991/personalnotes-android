package com.kostovskiapps.personalnotes.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kostovskiapps.personalnotes.BuildConfig
import com.kostovskiapps.personalnotes.data.repository.AndroidNoteShareRepository

/**
 * Picks a `.pnote` file the user received. Several MIME types are requested because messengers
 * and file managers label an unknown extension differently.
 */
@Composable
fun rememberNoteFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPicked) }

    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    AndroidNoteShareRepository.MIME_TYPE,
                    "application/json",
                    "text/plain",
                    "application/octet-stream",
                    "*/*",
                ),
            )
        }
    }
}

/** True when a document looks like one of our note files. */
fun isNoteFileName(name: String?): Boolean =
    name?.lowercase()?.endsWith(".${BuildConfig.NOTE_FILE_EXTENSION}") == true
