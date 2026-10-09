package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real-time state representing a driver's live GPS coordinates, telematics,
 * and Firestore broadcast status.
 */
data class DriverLiveLocationState(
    val driverId: String = "",
    val latitude: Double = 13.4470,
    val longitude: Double = -16.6790,
    val bearing: Float = 0f,
    val speedMps: Float = 0f,
    val accuracyMeters: Float = 0f,
    val isTracking: Boolean = false,
    val isFirestoreSyncActive: Boolean = false,
    val lastUpdateTimestamp: Long = 0L,
    val errorMessage: String? = null,
    val hasPermission: Boolean = false
) {
    val speedKmh: Float
        get() = speedMps * 3.6f

    val coordinatesDisplay: String
        get() = String.format(java.util.Locale.US, "%.5f° N, %.5f° W", latitude, -longitude)
}

/**
 * Service managing FusedLocationProviderClient to track and continuously update
 * driver real-time coordinates in Cloud Firestore (driver_locations collection).
 */
object DriverFusedLocationTracker {
    private const val TAG = "DriverLocationTracker"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _locationState = MutableStateFlow(DriverLiveLocationState())
    val locationState: StateFlow<DriverLiveLocationState> = _locationState.asStateFlow()

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    // Driver metadata for Firestore telematics payload
    private var currentDriverId: String = ""
    private var currentDriverName: String = ""
    private var currentDriverPhone: String = ""
    private var currentVehicleType: String = "CAR"
    private var currentVehiclePlate: String = ""
    private var currentRating: Double = 4.9
    private var repositoryRef: WayGoRepository? = null

    /**
     * Checks if runtime location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Starts continuous high-frequency location tracking using FusedLocationProviderClient
     * and streams live coordinates directly to Cloud Firestore.
     */
    @SuppressLint("MissingPermission")
    fun startTracking(
        context: Context,
        driverId: String,
        driverName: String,
        driverPhone: String,
        vehicleType: String,
        vehiclePlate: String,
        rating: Double = 4.9,
        repository: WayGoRepository? = null
    ) {
        currentDriverId = driverId
        currentDriverName = driverName
        currentDriverPhone = driverPhone
        currentVehicleType = vehicleType
        currentVehiclePlate = vehiclePlate
        currentRating = rating
        repositoryRef = repository

        val hasPerm = hasLocationPermission(context)
        _locationState.value = _locationState.value.copy(
            driverId = driverId,
            hasPermission = hasPerm
        )

        if (!hasPerm) {
            Log.w(TAG, "Cannot start driver tracking: ACCESS_FINE_LOCATION not granted.")
            _locationState.value = _locationState.value.copy(
                errorMessage = "Location permission required to broadcast driver position.",
                isTracking = false
            )
            return
        }

        try {
            if (fusedLocationClient == null) {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
            }

            // Remove any preexisting callback
            stopTracking()

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                /* intervalMillis = */ 3000L
            ).apply {
                setMinUpdateIntervalMillis(1500L)
                setMinUpdateDistanceMeters(2f) // Update every 2 meters
                setWaitForAccurateLocation(false)
                setMaxUpdateDelayMillis(5000L)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location: Location = result.lastLocation ?: return
                    handleNewDriverLocation(location)
                }

                override fun onLocationAvailability(avail: LocationAvailability) {
                    if (!avail.isLocationAvailable) {
                        Log.w(TAG, "Driver GPS fix temporarily unavailable.")
                    }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            _locationState.value = _locationState.value.copy(
                isTracking = true,
                errorMessage = null
            )
            Log.i(TAG, "FusedLocationProviderClient driver tracking active for $driverId")

            // Immediately query one-shot current location for rapid initial update
            requestOneShotLocation(context) { initialLoc ->
                initialLoc?.let { handleNewDriverLocation(it) }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initiating driver FusedLocation updates: ${e.localizedMessage}", e)
            _locationState.value = _locationState.value.copy(
                isTracking = false,
                errorMessage = "Failed to start GPS: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Processes a fresh location reading, updates reactive state, and synchronizes
     * with Cloud Firestore and local Room cache.
     */
    private fun handleNewDriverLocation(location: Location) {
        val lat = location.latitude
        val lng = location.longitude
        val bearing = location.bearing
        val speed = location.speed
        val accuracy = location.accuracy

        _locationState.value = _locationState.value.copy(
            latitude = lat,
            longitude = lng,
            bearing = bearing,
            speedMps = speed,
            accuracyMeters = accuracy,
            lastUpdateTimestamp = System.currentTimeMillis(),
            isFirestoreSyncActive = true,
            errorMessage = null
        )

        // 1. Update Cloud Firestore telematics collection ("driver_locations")
        if (currentDriverId.isNotBlank()) {
            FirestoreRideService.updateDriverLocation(
                driverId = currentDriverId,
                driverName = currentDriverName,
                driverPhone = currentDriverPhone,
                vehicleType = currentVehicleType,
                vehiclePlate = currentVehiclePlate,
                latitude = lat,
                longitude = lng,
                isOnline = true,
                isAvailable = true,
                rating = currentRating
            ) { success ->
                _locationState.value = _locationState.value.copy(
                    isFirestoreSyncActive = success
                )
            }

            // 2. Update local database cache
            repositoryRef?.let { repo ->
                scope.launch {
                    try {
                        repo.updateDriverLocation(currentDriverId, lat, lng)
                    } catch (e: Exception) {
                        Log.w(TAG, "Local Room driver location update note: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    /**
     * Performs a one-shot current GPS location request using FusedLocationProviderClient.
     */
    @SuppressLint("MissingPermission")
    fun requestOneShotLocation(
        context: Context,
        onLocationResult: (Location?) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onLocationResult(null)
            return
        }

        try {
            val client = fusedLocationClient ?: LocationServices.getFusedLocationProviderClient(context.applicationContext)
            val cts = CancellationTokenSource()

            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    onLocationResult(location)
                } else {
                    // Fallback to last known location
                    client.lastLocation.addOnSuccessListener { lastKnown ->
                        onLocationResult(lastKnown)
                    }.addOnFailureListener {
                        onLocationResult(null)
                    }
                }
            }.addOnFailureListener {
                onLocationResult(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching one-shot location: ${e.localizedMessage}")
            onLocationResult(null)
        }
    }

    /**
     * Stops continuous location updates and marks the driver offline in Firestore.
     */
    fun stopTracking(markOfflineInFirestore: Boolean = false) {
        locationCallback?.let { cb ->
            try {
                fusedLocationClient?.removeLocationUpdates(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing location callback: ${e.localizedMessage}")
            }
        }
        locationCallback = null

        if (markOfflineInFirestore && currentDriverId.isNotBlank()) {
            val lastState = _locationState.value
            FirestoreRideService.updateDriverLocation(
                driverId = currentDriverId,
                driverName = currentDriverName,
                driverPhone = currentDriverPhone,
                vehicleType = currentVehicleType,
                vehiclePlate = currentVehiclePlate,
                latitude = lastState.latitude,
                longitude = lastState.longitude,
                isOnline = false,
                isAvailable = false,
                rating = currentRating
            )
        }

        _locationState.value = _locationState.value.copy(
            isTracking = false,
            isFirestoreSyncActive = false
        )
        Log.i(TAG, "Driver location tracking stopped.")
    }
}
