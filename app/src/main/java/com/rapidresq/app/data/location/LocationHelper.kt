package com.rapidresq.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class GpsLocationResult(
    val lat: Double,
    val lng: Double,
    val displayAddress: String,
    val isDemo: Boolean = false
)

class LocationHelper(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    fun hasLocationPermission(): Boolean {
        val finePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return finePermission || coarsePermission
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GpsLocationResult? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null

        try {
            val location: Location? = suspendCancellableCoroutine { continuation ->
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (continuation.isActive) continuation.resume(loc)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { lastLoc -> if (continuation.isActive) continuation.resume(lastLoc) }
                                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
                        }
                    }
            }

            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude
                val addressLabel = reverseGeocode(lat, lng)
                GpsLocationResult(lat, lng, addressLabel, isDemo = false)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var resultAddress = ""
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val addr = addresses[0]
                            val feature = addr.subLocality ?: addr.locality ?: addr.featureName ?: ""
                            val city = addr.locality ?: addr.subAdminArea ?: "Indore"
                            resultAddress = if (feature.isNotBlank() && feature != city) "$feature, $city" else city
                        }
                    }
                    if (resultAddress.isNotBlank()) return@withContext resultAddress
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val feature = addr.subLocality ?: addr.locality ?: addr.featureName ?: ""
                        val city = addr.locality ?: addr.subAdminArea ?: "Indore"
                        return@withContext if (feature.isNotBlank() && feature != city) "$feature, $city" else city
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to coordinates
        }
        return@withContext String.format(Locale.US, "%.4f° N, %.4f° E", lat, lng)
    }

    @SuppressLint("MissingPermission")
    fun startDutyLocationUpdates(onLocation: (Double, Double) -> Unit) {
        if (!hasLocationPermission()) return
        stopDutyLocationUpdates()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setMinUpdateIntervalMillis(5000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                res.lastLocation?.let {
                    onLocation(it.latitude, it.longitude)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                context.mainLooper
            )
        } catch (e: SecurityException) {
            // Permission revoked
        }
    }

    fun stopDutyLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
}
