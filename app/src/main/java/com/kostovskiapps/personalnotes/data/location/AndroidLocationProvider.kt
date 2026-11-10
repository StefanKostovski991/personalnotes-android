package com.kostovskiapps.personalnotes.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kostovskiapps.personalnotes.domain.model.NoteLocation
import com.kostovskiapps.personalnotes.domain.repository.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Resolves "where am I" for a note.
 *
 * Order of preference: Play Services fused provider (accurate and battery friendly), then the
 * platform [LocationManager] for devices without Play Services. The coordinates are reverse
 * geocoded into a short human label, and a failure to geocode is not an error: the note keeps
 * its coordinates.
 */
class AndroidLocationProvider(
    private val context: Context,
) : LocationProvider {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override suspend fun currentLocation(): Result<NoteLocation> {
        if (!hasPermission()) {
            return Result.failure(SecurityException("Location permission was not granted"))
        }
        val location = try {
            fusedLocation() ?: platformLocation()
        } catch (error: SecurityException) {
            return Result.failure(error)
        } catch (error: Exception) {
            return Result.failure(error)
        }

        if (location == null) {
            return Result.failure(IllegalStateException("No location fix available"))
        }

        val label = reverseGeocode(location)
        return Result.success(
            NoteLocation(
                label = label ?: formatCoordinates(location.latitude, location.longitude),
                latitude = location.latitude,
                longitude = location.longitude,
            ),
        )
    }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun fusedLocation(): Location? {
        if (!isProviderEnabled()) return null
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { location -> continuation.resume(location) }
                    .addOnFailureListener { continuation.resume(null) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun platformLocation(): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        return providers.firstNotNullOfOrNull { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            }.getOrNull()
        }
    }

    private fun isProviderEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return runCatching {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
    }

    private suspend fun reverseGeocode(location: Location): String? = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        runCatching {
            @Suppress("DEPRECATION")
            val addresses: List<Address>? =
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
            addresses?.firstOrNull()?.let(::formatAddress)
        }.getOrNull()
    }

    private fun formatAddress(address: Address): String? {
        val parts = listOfNotNull(
            address.locality ?: address.subAdminArea,
            address.adminArea,
            address.countryName,
        ).distinct()
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun formatCoordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.4f, %.4f", latitude, longitude)

    companion object {
        private const val LOCATION_TIMEOUT_MS = 12_000L
    }
}
