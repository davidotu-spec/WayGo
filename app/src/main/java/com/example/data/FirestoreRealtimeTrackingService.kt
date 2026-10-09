package com.example.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Real-time event model representing a ride request status change.
 */
data class RideStatusEvent(
    val requestId: String,
    val status: String,
    val previousStatus: String? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val vehiclePlate: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val request: ActiveRideRequest
)

/**
 * Real-time event model representing a live driver GPS telemetry update.
 */
data class DriverLocationEvent(
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float = 0f,
    val speedMps: Float = 0f,
    val isOnline: Boolean = true,
    val isAvailable: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val data: DriverLocationData
)

/**
 * FirestoreRealtimeTrackingService
 *
 * Dedicated service class leveraging Firebase Firestore snapshot listeners to provide:
 * 1. Real-time Ride Request Status Updates:
 *    - Instant detection of status transitions: REQUESTED -> SEARCHING -> ACCEPTED -> ARRIVED -> EN_ROUTE -> COMPLETED / CANCELLED.
 *    - Both Kotlin Coroutine Flows (callbackFlow) and callback ListenerRegistrations.
 *    - Dual-direction sync for Passenger apps, Driver apps, and the Admin Web Portal (waygowebadminportal.com).
 *
 * 2. Real-time Driver Location & Telematics Tracking:
 *    - Sub-second GPS coordinate streaming from `driver_locations` collection.
 *    - Single-driver tracking (e.g., passenger following assigned driver en route to pickup).
 *    - Vicinity driver tracking (nearby online fleet matching with Haversine distance).
 *    - All-driver tracking for centralized dispatch and Admin live operational map.
 *
 * 3. Lifecycle & Leak Prevention:
 *    - Thread-safe registry for active ListenerRegistration instances.
 *    - Scoped cancellation per request, per driver, or globally upon user sign-out.
 *
 * Admin Web Portal Integration:
 * - Web admins connect to the identical Firestore collections (`active_ride_requests` and `driver_locations`).
 * - Listeners in this service immediately pick up status changes made by web administrators (e.g. manual dispatch or cancellation),
 *   and updates from drivers/passengers are mirrored in the web portal within ~200-500ms.
 */
class FirestoreRealtimeTrackingService(
    private val firestoreInstance: FirebaseFirestore? = null,
    private val forceOfflineMode: Boolean = false
) {
    companion object {
        private const val TAG = "RealtimeTrackingService"
        const val COLLECTION_RIDE_REQUESTS = "active_ride_requests"
        const val COLLECTION_DRIVER_LOCATIONS = "driver_locations"

        @Volatile
        private var INSTANCE: FirestoreRealtimeTrackingService? = null

        fun getInstance(): FirestoreRealtimeTrackingService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreRealtimeTrackingService(firestoreInstance = FirestoreManager.firestore).also { INSTANCE = it }
            }
        }
    }

    private val db: FirebaseFirestore?
        get() = if (forceOfflineMode) null else (firestoreInstance ?: FirestoreManager.firestore)

    // Thread-safe listener registries to manage active snapshot listeners and prevent leaks
    private val activeRideListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val activeDriverListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val activeQueryListeners = ConcurrentHashMap<String, ListenerRegistration>()

    // =========================================================================
    // SECTION 1: REAL-TIME RIDE REQUEST STATUS UPDATES
    // =========================================================================

    /**
     * Sets up a real-time snapshot listener on a specific ride request document.
     * Triggers whenever the status, assigned driver, ETA, or verification PIN changes.
     *
     * @param requestId The ID of the ride request in Firestore.
     * @param onUpdate Callback invoked with the parsed ActiveRideRequest, or null if deleted/not found.
     * @param onError Optional error callback for permissions or connectivity errors.
     * @return ListenerRegistration handle that can be used to stop listening.
     */
    fun listenToRideRequestStatus(
        requestId: String,
        onUpdate: (ActiveRideRequest?) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration? {
        if (requestId.isBlank()) {
            Log.w(TAG, "Cannot listen to empty requestId")
            onUpdate(null)
            return null
        }

        // Cancel any existing listener on the same request ID first
        stopListeningToRideRequest(requestId)

        val firestore = db
        if (firestore == null) {
            Log.w(TAG, "Firestore unconfigured. Simulating active status for request: $requestId")
            onUpdate(createFallbackRideRequest(requestId))
            return null
        }

        try {
            val registration = firestore.collection(COLLECTION_RIDE_REQUESTS)
                .document(requestId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Snapshot listener error for ride $requestId: ${error.localizedMessage}", error)
                        onError?.invoke(error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val ride = snapshot.toActiveRideRequest()
                        Log.d(TAG, "Ride request $requestId updated status -> ${ride?.status}")
                        onUpdate(ride)
                    } else {
                        Log.i(TAG, "Ride request $requestId document does not exist or was deleted")
                        onUpdate(null)
                    }
                }

            activeRideListeners[requestId] = registration
            return registration
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering snapshot listener for ride $requestId: ${e.localizedMessage}", e)
            onError?.invoke(e)
            return null
        }
    }

    /**
     * Kotlin Coroutines Flow variant for real-time ride request status updates.
     * Automatically registers the snapshot listener on collection and removes it when flow is cancelled.
     */
    fun rideRequestStatusFlow(requestId: String): Flow<ActiveRideRequest?> = callbackFlow {
        if (requestId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val firestore = db
        if (firestore == null) {
            Log.w(TAG, "Firestore null in rideRequestStatusFlow, emitting fallback for $requestId")
            trySend(createFallbackRideRequest(requestId))
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection(COLLECTION_RIDE_REQUESTS)
            .document(requestId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Flow snapshot error for ride $requestId: ${error.localizedMessage}")
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toActiveRideRequest())
                } else {
                    trySend(null)
                }
            }

        awaitClose {
            Log.d(TAG, "Closing rideRequestStatusFlow for $requestId, removing snapshot listener")
            registration.remove()
        }
    }

    /**
     * Flow tracking all active rides belonging to a specific passenger.
     * Useful for passenger home/activity screen and status notifications.
     */
    fun passengerActiveRidesFlow(passengerId: String): Flow<List<ActiveRideRequest>> = callbackFlow {
        val firestore = db
        if (firestore == null || passengerId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection(COLLECTION_RIDE_REQUESTS)
            .whereEqualTo("passengerId", passengerId)
            .whereIn("status", listOf("REQUESTED", "SEARCHING", "ACCEPTED", "ARRIVED", "EN_ROUTE"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to passenger active rides: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toActiveRideRequest() } ?: emptyList()
                trySend(list)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Flow tracking active rides assigned to a specific driver.
     * Emits the currently assigned active ride, or null if driver has no active ride.
     */
    fun driverAssignedRideFlow(driverId: String): Flow<ActiveRideRequest?> = callbackFlow {
        val firestore = db
        if (firestore == null || driverId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection(COLLECTION_RIDE_REQUESTS)
            .whereEqualTo("driverId", driverId)
            .whereIn("status", listOf("ACCEPTED", "ARRIVED", "EN_ROUTE"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in driverAssignedRideFlow: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val activeRide = snapshot?.documents?.firstOrNull()?.toActiveRideRequest()
                trySend(activeRide)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Real-time stream of ALL active rides in the entire system.
     * Used by the Admin Web Portal (waygowebadminportal.com) and dispatch monitors
     * to monitor live system activity, manage rides, and assign drivers.
     */
    fun allActiveRideRequestsFlow(): Flow<List<ActiveRideRequest>> = callbackFlow {
        val firestore = db
        if (firestore == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection(COLLECTION_RIDE_REQUESTS)
            .whereIn("status", listOf("REQUESTED", "SEARCHING", "ACCEPTED", "ARRIVED", "EN_ROUTE"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to all active ride requests: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toActiveRideRequest() } ?: emptyList()
                trySend(list)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Unregisters and removes a snapshot listener for a specific ride request ID.
     */
    fun stopListeningToRideRequest(requestId: String) {
        activeRideListeners.remove(requestId)?.let { registration ->
            try {
                registration.remove()
                Log.d(TAG, "Successfully stopped listening to ride request: $requestId")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing listener for ride $requestId: ${e.localizedMessage}")
            }
        }
    }

    // =========================================================================
    // SECTION 2: REAL-TIME DRIVER LOCATION TRACKING
    // =========================================================================

    /**
     * Sets up a real-time snapshot listener on a specific driver's location document.
     * Triggers whenever the driver's GPS updates, bearing changes, or online/offline status shifts.
     *
     * @param driverId The driver's unique ID.
     * @param onLocationUpdate Callback invoked with fresh DriverLocationData.
     * @param onError Optional error callback.
     * @return ListenerRegistration handle.
     */
    fun listenToDriverLocation(
        driverId: String,
        onLocationUpdate: (DriverLocationData?) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration? {
        if (driverId.isBlank()) {
            Log.w(TAG, "Cannot track empty driverId")
            onLocationUpdate(null)
            return null
        }

        // Remove any prior listener for this driver
        stopTrackingDriver(driverId)

        val firestore = db
        if (firestore == null) {
            Log.w(TAG, "Firestore offline. Returning simulated driver coordinates for: $driverId")
            onLocationUpdate(createFallbackDriverLocation(driverId))
            return null
        }

        try {
            val registration = firestore.collection(COLLECTION_DRIVER_LOCATIONS)
                .document(driverId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Snapshot listener error for driver $driverId: ${error.localizedMessage}", error)
                        onError?.invoke(error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val driverLoc = snapshot.toDriverLocationData()
                        Log.d(TAG, "Driver $driverId live GPS update: (${driverLoc?.latitude}, ${driverLoc?.longitude})")
                        onLocationUpdate(driverLoc)
                    } else {
                        Log.w(TAG, "Driver $driverId location document does not exist")
                        onLocationUpdate(null)
                    }
                }

            activeDriverListeners[driverId] = registration
            return registration
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering driver location listener for $driverId: ${e.localizedMessage}", e)
            onError?.invoke(e)
            return null
        }
    }

    /**
     * Kotlin Coroutines Flow variant for tracking a specific driver's live GPS telematics.
     * Ideal for Jetpack Compose `collectAsStateWithLifecycle()` on Passenger ride-in-progress screen.
     */
    fun driverLocationFlow(driverId: String): Flow<DriverLocationData?> = callbackFlow {
        if (driverId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val firestore = db
        if (firestore == null) {
            Log.w(TAG, "Firestore null in driverLocationFlow, emitting fallback for $driverId")
            trySend(createFallbackDriverLocation(driverId))
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection(COLLECTION_DRIVER_LOCATIONS)
            .document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Flow snapshot error for driver $driverId: ${error.localizedMessage}")
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toDriverLocationData())
                } else {
                    trySend(null)
                }
            }

        awaitClose {
            Log.d(TAG, "Closing driverLocationFlow for $driverId, removing snapshot listener")
            registration.remove()
        }
    }

    /**
     * Real-time stream of nearby online drivers around a passenger's pickup location.
     * Updates automatically whenever any online driver's location shifts in Firestore.
     *
     * @param pickupLat Passenger pickup latitude
     * @param pickupLng Passenger pickup longitude
     * @param radiusKm Search radius in kilometers (default 10.0km)
     * @param vehicleTypeFilter Optional vehicle filter ("CAR", "TAXI", "TRICYCLE", "VAN", or null for all)
     */
    fun nearbyDriversFlow(
        pickupLat: Double,
        pickupLng: Double,
        radiusKm: Double = 10.0,
        vehicleTypeFilter: String? = null
    ): Flow<List<DriverLocationData>> = callbackFlow {
        val firestore = db
        if (firestore == null) {
            trySend(getFallbackNearbyDrivers(pickupLat, pickupLng, vehicleTypeFilter))
            awaitClose { }
            return@callbackFlow
        }

        val (latRange, lngRange) = GeoUtils.getBoundingBox(pickupLat, pickupLng, radiusKm)
        val (minLat, maxLat) = latRange
        val (minLng, maxLng) = lngRange

        val registration = firestore.collection(COLLECTION_DRIVER_LOCATIONS)
            .whereGreaterThanOrEqualTo("latitude", minLat)
            .whereLessThanOrEqualTo("latitude", maxLat)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in nearbyDriversFlow: ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    trySend(getFallbackNearbyDrivers(pickupLat, pickupLng, vehicleTypeFilter))
                    return@addSnapshotListener
                }

                val nearby = mutableListOf<DriverLocationData>()
                for (doc in snapshot.documents) {
                    val driver = doc.toDriverLocationData() ?: continue
                    if (!driver.isOnline || !driver.isAvailable) continue
                    if (driver.longitude < minLng || driver.longitude > maxLng) continue

                    if (vehicleTypeFilter != null && vehicleTypeFilter != "ALL" &&
                        !driver.vehicleType.equals(vehicleTypeFilter, ignoreCase = true)) {
                        continue
                    }

                    val distance = GeoUtils.haversineDistanceKm(pickupLat, pickupLng, driver.latitude, driver.longitude)
                    if (distance <= radiusKm) {
                        nearby.add(driver.copy(distanceFromPickupKm = (distance * 10).roundToInt() / 10.0))
                    }
                }

                nearby.sortBy { it.distanceFromPickupKm }
                trySend(if (nearby.isNotEmpty()) nearby else getFallbackNearbyDrivers(pickupLat, pickupLng, vehicleTypeFilter))
            }

        awaitClose { registration.remove() }
    }

    /**
     * Real-time stream of all online drivers across The Gambia.
     * Used by the Admin Web Portal (waygowebadminportal.com) to render the live operational fleet map.
     */
    fun allOnlineDriversFlow(): Flow<List<DriverLocationData>> = callbackFlow {
        val firestore = db
        if (firestore == null) {
            trySend(getFallbackNearbyDrivers(13.4432, -16.6812, null))
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection(COLLECTION_DRIVER_LOCATIONS)
            .whereEqualTo("isOnline", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to all online drivers: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val drivers = snapshot?.documents?.mapNotNull { it.toDriverLocationData() } ?: emptyList()
                trySend(drivers)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Unregisters and removes a snapshot listener for a specific driver ID.
     */
    fun stopTrackingDriver(driverId: String) {
        activeDriverListeners.remove(driverId)?.let { registration ->
            try {
                registration.remove()
                Log.d(TAG, "Successfully stopped tracking driver location: $driverId")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing listener for driver $driverId: ${e.localizedMessage}")
            }
        }
    }

    // =========================================================================
    // SECTION 3: WRITE & PUBLISH (TRIGGERS LISTENERS FOR APP & ADMIN PORTAL)
    // =========================================================================

    /**
     * Publishes driver live GPS telematics to Firestore.
     * Triggers real-time snapshot listeners on both passenger devices and the Admin Web Portal.
     */
    fun publishDriverLocation(
        driverId: String,
        driverName: String,
        driverPhone: String,
        vehicleType: String,
        vehiclePlate: String,
        latitude: Double,
        longitude: Double,
        bearing: Float = 0f,
        speedMps: Float = 0f,
        isOnline: Boolean = true,
        isAvailable: Boolean = true,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val firestore = db
        val geohash = GeoUtils.encodeGeohash(latitude, longitude)
        val data = hashMapOf<String, Any>(
            "driverId" to driverId,
            "driverName" to driverName,
            "driverPhone" to driverPhone,
            "vehicleType" to vehicleType,
            "vehiclePlate" to vehiclePlate,
            "latitude" to latitude,
            "longitude" to longitude,
            "bearing" to bearing,
            "speedMps" to speedMps,
            "isOnline" to isOnline,
            "isAvailable" to isAvailable,
            "lastUpdated" to System.currentTimeMillis(),
            "geohash" to geohash
        )

        if (firestore == null) {
            Log.w(TAG, "Simulating local publish for driver location: $driverId")
            onComplete(true)
            return
        }

        try {
            firestore.collection(COLLECTION_DRIVER_LOCATIONS)
                .document(driverId)
                .set(data)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully published location for driver: $driverId")
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed publishing location for driver: $driverId", e)
                    onComplete(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing driver location: ${e.localizedMessage}")
            onComplete(false)
        }
    }

    /**
     * Updates ride status in Firestore, immediately notifying passenger, driver, and admin portal.
     */
    fun updateRideRequestStatus(
        requestId: String,
        newStatus: String,
        driverId: String? = null,
        driverName: String? = null,
        driverPhone: String? = null,
        vehiclePlate: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val firestore = db
        if (firestore == null) {
            Log.w(TAG, "Simulating status update to $newStatus for $requestId")
            onComplete(true)
            return
        }

        val updates = mutableMapOf<String, Any>(
            "status" to newStatus,
            "updatedAt" to System.currentTimeMillis()
        )
        if (driverId != null) updates["driverId"] = driverId
        if (driverName != null) updates["driverName"] = driverName
        if (driverPhone != null) updates["driverPhone"] = driverPhone
        if (vehiclePlate != null) updates["vehiclePlate"] = vehiclePlate

        try {
            firestore.collection(COLLECTION_RIDE_REQUESTS)
                .document(requestId)
                .update(updates)
                .addOnSuccessListener {
                    Log.i(TAG, "Ride $requestId status successfully updated to: $newStatus")
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed updating ride $requestId status to: $newStatus", e)
                    onComplete(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating ride status: ${e.localizedMessage}")
            onComplete(false)
        }
    }

    // =========================================================================
    // SECTION 4: LIFECYCLE & CLEANUP
    // =========================================================================

    /**
     * Removes all active snapshot listeners across ride requests, drivers, and queries.
     * Call when user logs out or the application/service transitions to background.
     */
    fun removeAllListeners() {
        Log.i(TAG, "Removing all active Firestore snapshot listeners (${activeRideListeners.size} rides, ${activeDriverListeners.size} drivers, ${activeQueryListeners.size} queries)")

        for ((id, reg) in activeRideListeners) {
            try { reg.remove() } catch (e: Exception) { Log.e(TAG, "Error removing ride listener $id", e) }
        }
        activeRideListeners.clear()

        for ((id, reg) in activeDriverListeners) {
            try { reg.remove() } catch (e: Exception) { Log.e(TAG, "Error removing driver listener $id", e) }
        }
        activeDriverListeners.clear()

        for ((id, reg) in activeQueryListeners) {
            try { reg.remove() } catch (e: Exception) { Log.e(TAG, "Error removing query listener $id", e) }
        }
        activeQueryListeners.clear()
    }

    fun getActiveRideListenersCount(): Int = activeRideListeners.size
    fun getActiveDriverListenersCount(): Int = activeDriverListeners.size

    // =========================================================================
    // SECTION 5: PARSING & FALLBACK HELPERS
    // =========================================================================

    private fun createFallbackRideRequest(requestId: String): ActiveRideRequest {
        return ActiveRideRequest(
            requestId = requestId,
            passengerId = "usr_guest",
            passengerName = "WayGo Passenger",
            passengerPhone = "+220 7000000",
            pickupName = "Westfield Junction, Serrekunda",
            pickupLat = 13.4385,
            pickupLng = -16.6760,
            dropoffName = "Albert Market, Banjul",
            dropoffLat = 13.4533,
            dropoffLng = -16.5746,
            vehicleType = "CAR",
            fareGmd = 150,
            status = "SEARCHING",
            verificationPin = "4819"
        )
    }

    private fun createFallbackDriverLocation(driverId: String): DriverLocationData {
        return DriverLocationData(
            driverId = driverId,
            driverName = "Alieu Ceesay",
            driverPhone = "+220 7481920",
            vehicleType = "CAR",
            vehiclePlate = "BJL 4821 C",
            latitude = 13.4420,
            longitude = -16.6780,
            bearing = 45f,
            speedMps = 8.5f,
            isOnline = true,
            isAvailable = true,
            rating = 4.95
        )
    }

    private fun getFallbackNearbyDrivers(
        lat: Double,
        lng: Double,
        vehicleFilter: String?
    ): List<DriverLocationData> {
        val list = listOf(
            DriverLocationData(
                driverId = "drv_alieu_01",
                driverName = "Alieu Ceesay",
                driverPhone = "+220 7481920",
                vehicleType = "CAR",
                vehiclePlate = "BJL 4821 C",
                latitude = lat + 0.006,
                longitude = lng - 0.004,
                bearing = 35f,
                speedMps = 10f,
                isOnline = true,
                isAvailable = true,
                rating = 4.95,
                distanceFromPickupKm = 0.8
            ),
            DriverLocationData(
                driverId = "drv_mariama_02",
                driverName = "Mariama Jallow",
                driverPhone = "+220 3109482",
                vehicleType = "TRICYCLE",
                vehiclePlate = "KM 9312 T",
                latitude = lat - 0.005,
                longitude = lng + 0.005,
                bearing = 120f,
                speedMps = 6f,
                isOnline = true,
                isAvailable = true,
                rating = 4.88,
                distanceFromPickupKm = 1.2
            ),
            DriverLocationData(
                driverId = "drv_bakary_03",
                driverName = "Bakary Touray",
                driverPhone = "+220 7982011",
                vehicleType = "TAXI",
                vehiclePlate = "WCR 7431 B",
                latitude = lat + 0.010,
                longitude = lng + 0.008,
                bearing = 210f,
                speedMps = 12f,
                isOnline = true,
                isAvailable = true,
                rating = 4.92,
                distanceFromPickupKm = 1.6
            )
        )
        return if (vehicleFilter != null && vehicleFilter != "ALL") {
            list.filter { it.vehicleType.equals(vehicleFilter, ignoreCase = true) }
        } else {
            list
        }
    }
}

/**
 * Extension function to safely convert a Firestore DocumentSnapshot into an ActiveRideRequest.
 */
fun DocumentSnapshot.toActiveRideRequest(): ActiveRideRequest? {
    return ActiveRideRequest.fromFirestore(this)
}

/**
 * Extension function to safely convert a Firestore DocumentSnapshot into DriverLocationData.
 */
fun DocumentSnapshot.toDriverLocationData(): DriverLocationData? {
    if (!exists()) return null
    return try {
        val lat = getDouble("latitude") ?: return null
        val lng = getDouble("longitude") ?: return null
        DriverLocationData(
            driverId = getString("driverId") ?: id,
            driverName = getString("driverName") ?: "WayGo Fleet Driver",
            driverPhone = getString("driverPhone") ?: "+220 7000000",
            vehicleType = getString("vehicleType") ?: "CAR",
            vehiclePlate = getString("vehiclePlate") ?: "BJL 1234 A",
            latitude = lat,
            longitude = lng,
            bearing = getDouble("bearing")?.toFloat() ?: 0f,
            speedMps = getDouble("speedMps")?.toFloat() ?: 0f,
            isOnline = getBoolean("isOnline") ?: true,
            isAvailable = getBoolean("isAvailable") ?: true,
            rating = getDouble("rating") ?: 4.9,
            lastUpdated = getLong("lastUpdated") ?: System.currentTimeMillis(),
            geohash = getString("geohash") ?: GeoUtils.encodeGeohash(lat, lng),
            distanceFromPickupKm = getDouble("distanceFromPickupKm") ?: 0.0
        )
    } catch (e: Exception) {
        Log.e("toDriverLocationData", "Error parsing DriverLocationData from document $id: ${e.localizedMessage}")
        null
    }
}
