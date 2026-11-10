package com.kostovskiapps.personalnotes

import android.app.Application
import com.kostovskiapps.personalnotes.di.AppContainer

class PersonalNotesApp : Application() {

    /** Created once for the process; everything else is lazily built from it. */
    val container: AppContainer by lazy { AppContainer(this) }
}
