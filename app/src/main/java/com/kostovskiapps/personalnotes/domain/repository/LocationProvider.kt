package com.kostovskiapps.personalnotes.domain.repository

import com.kostovskiapps.personalnotes.domain.model.NoteLocation

/**
 * Port for "where am I right now". Implemented with Play Services (or the platform
 * provider as a fallback) so the domain stays free of Android location APIs.
 */
interface LocationProvider {

    /** Whether the caller may ask for a fix; the UI uses this to trigger the permission flow. */
    fun hasPermission(): Boolean

    suspend fun currentLocation(): Result<NoteLocation>
}
