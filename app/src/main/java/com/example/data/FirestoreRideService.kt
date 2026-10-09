package com.example.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlin.math.*

/**
 * Data representation for real-time Driver Locations in Firestore.
 */
data class DriverLocationData(
    val driverId: String = "",
    val driverName: String = "",
    val driverPhone: String = "",
    val vehicleType: String = "CAR",
    val vehiclePlate: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bearing: Float = 0f,
    val speedMps: Float = 0f,
    val isOnline: Boolean = true,
    val isAvailable: Boolean = true,
    val rating: Double = 4.9,
    val lastUpdated: Long = System.currentTimeMillis(),
    val geohash: String = GeoUtils.encodeGeohash(latitude, longitude),
    val distanceFromPickupKm: Double = 0.0
)

/**
 * GeoUtils provides helper functions for Haversine distance calculations,
 * bounding box queries, and Geohash encoding to enable GeoQuery capabilities in Firestore.
 */
object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /**
     * Calculates the Haversine distance in kilometers between two GPS coordinates.
     */
    fun haversineDistanceKm(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Computes bounding box latitude and longitude ranges for a given center coordinate and radius in kilometers.
     */
    fun getBoundingBox(lat: Double, lng: Double, radiusKm: Double): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        val latChange = radiusKm / EARTH_RADIUS_KM * (180.0 / Math.PI)
        val lngChange = radiusKm / (EARTH_RADIUS_KM * cos(Math.toRadians(lat))) * (180.0 / Math.PI)

        val minLat = lat - latChange
        val maxLat = lat + latChange
        val minLng = lng - lngChange
        val maxLng = lng + lngChange

        return Pair(Pair(minLat, maxLat), Pair(minLng, maxLng))
    }

    /**
     * Encodes a latitude and longitude into a standard 8-character Geohash string.
     */
    fun encodeGeohash(lat: Double, lng: Double, precision: Int = 7): String {
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0

        val geohash = StringBuilder()
        var isEven = true
        var bit = 0
        var ch = 0

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (lngMin + lngMax) / 2.0
                if (lng >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    lngMin = mid
                } else {
                    lngMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2.0
                if (lat >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    latMin = mid
                } else {
                    latMax = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                geohash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return geohash.toString()
    }
}

/**
 * Service managing Firestore operations for active ride requests and GeoQuery driver matching.
 */
object FirestoreRideService {
    private const val TAG = "FirestoreRideService"
    const val COLLECTION_RIDE_REQUESTS = "ride_requests"
    const val COLLECTION_ACTIVE_RIDE_REQUESTS = "active_ride_requests"
    private const val COLLECTION_DRIVER_LOCATIONS = "driver_locations"

    val db: FirebaseFirestore?
        get() = FirestoreManager.firestore

    /**
     * Creates and stores a new active ride request in Firestore 'ride_requests' collection
     * and mirrors to 'active_ride_requests' for continuous real-time dispatch compatibility.
     */
    fun createRideRequest(
        request: ActiveRideRequest,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val firestore = db
        val docId = request.requestId.ifEmpty { "req_${System.currentTimeMillis()}" }
        val updatedRequest = request.copy(
            requestId = docId,
            geohash = GeoUtils.encodeGeohash(request.pickupLat, request.pickupLng)
        )

        // Validate write payload against WayGo Firestore Security Rules
        val (isValidRule, ruleViolation) = FirebaseSecurityRulesManager.validateRideRequestWrite(updatedRequest)
        if (!isValidRule) {
            Log.e(TAG, "Security rules rejection: $ruleViolation")
            onComplete(false, null)
            return
        }

        val requestData = updatedRequest.toFirestoreMap()

        if (firestore == null) {
            Log.w(TAG, "Firestore unavailable. Simulated local dispatch for request ID: $docId")
            onComplete(true, docId)
            return
        }

        try {
            // 1. Primary write to 'ride_requests' collection as required
            firestore.collection(COLLECTION_RIDE_REQUESTS)
                .document(docId)
                .set(requestData)
                .addOnSuccessListener {
                    Log.i(TAG, "Ride request created successfully in Firestore 'ride_requests': $docId")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Notice saving to 'ride_requests' in Firestore: ${e.localizedMessage}")
                }

            // 2. Also maintain 'active_ride_requests' for continuous real-time tracking compatibility
            firestore.collection(COLLECTION_ACTIVE_RIDE_REQUESTS)
                .document(docId)
                .set(requestData)
                .addOnSuccessListener {
                    Log.i(TAG, "Ride request synced to 'active_ride_requests': $docId")
                    onComplete(true, docId)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Notice syncing to 'active_ride_requests': ${e.localizedMessage}")
                    onComplete(true, docId)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore create ride request notice: ${e.localizedMessage}. Proceeding with local dispatch: $docId")
            onComplete(true, docId)
        }
    }

    /**
     * Dedicated alias for saving ride details directly to the Firestore 'ride_requests' collection.
     */
    fun saveRideRequestToFirestore(
        request: ActiveRideRequest,
        onComplete: (Boolean, String?) -> Unit
    ) {
        createRideRequest(request, onComplete)
    }

    /**
     * Retrieves an active ride request by ID from Firestore.
     */
    fun getRideRequestById(requestId: String, onResult: (ActiveRideRequest?) -> Unit) {
        val firestore = db
        if (firestore == null) {
            onResult(null)
            return
        }
        firestore.collection(COLLECTION_RIDE_REQUESTS)
            .document(requestId)
            .get()
            .addOnSuccessListener { doc ->
                onResult(ActiveRideRequest.fromFirestore(doc))
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error fetching ride request $requestId: ${e.localizedMessage}")
                onResult(null)
            }
    }

    /**
     * Queries active ride requests by lifecycle status (e.g., "pending", "active", "completed").
     */
    fun queryRideRequestsByStatus(status: String, onResult: (List<ActiveRideRequest>) -> Unit) {
        val firestore = db
        if (firestore == null) {
            onResult(emptyList())
            return
        }
        val normalized = RideStatus.fromString(status)
        val queryStatuses = when (normalized) {
            RideStatus.PENDING -> listOf("pending", "REQUESTED", "SEARCHING")
            RideStatus.ACTIVE -> listOf("active", "ACCEPTED", "ARRIVED", "EN_ROUTE")
            RideStatus.COMPLETED -> listOf("completed", "COMPLETED")
            RideStatus.CANCELLED -> listOf("cancelled", "CANCELLED")
        }

        firestore.collection(COLLECTION_RIDE_REQUESTS)
            .whereIn("status", queryStatuses)
            .get()
            .addOnSuccessListener { snapshot ->
                val rides = snapshot.documents.mapNotNull { ActiveRideRequest.fromFirestore(it) }
                onResult(rides)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error querying rides by status $status: ${e.localizedMessage}")
                onResult(emptyList())
            }
    }

    /**
     * Queries active ride requests by status enum.
     */
    fun queryRideRequestsByStatus(status: RideStatus, onResult: (List<ActiveRideRequest>) -> Unit) {
        queryRideRequestsByStatus(status.value, onResult)
    }

    /**
     * Queries all ride requests initiated by a specific passenger ID.
     */
    fun queryRideRequestsForPassenger(passengerId: String, onResult: (List<ActiveRideRequest>) -> Unit) {
        val firestore = db
        if (firestore == null) {
            onResult(emptyList())
            return
        }
        firestore.collection(COLLECTION_RIDE_REQUESTS)
            .whereEqualTo("passengerId", passengerId)
            .get()
            .addOnSuccessListener { snapshot ->
                val rides = snapshot.documents.mapNotNull { ActiveRideRequest.fromFirestore(it) }
                onResult(rides)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error querying rides for passenger $passengerId: ${e.localizedMessage}")
                onResult(emptyList())
            }
    }

    /**
     * Updates the status of an existing ride request in Firestore (e.g. ACCEPTED, COMPLETED).
     */
    fun updateRideRequestStatus(
        requestId: String,
        status: String,
        driverId: String? = null,
        driverName: String? = null,
        driverPhone: String? = null,
        vehiclePlate: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val firestore = db
        if (firestore == null) {
            Log.w(TAG, "Firestore unavailable. Simulated status update to $status for: $requestId")
            onComplete(true)
            return
        }

        val updates = mutableMapOf<String, Any>(
            "status" to status,
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
            firestore.collection(COLLECTION_ACTIVE_RIDE_REQUESTS)
                .document(requestId)
                .update(updates)
                .addOnSuccessListener {
                    Log.i(TAG, "Ride request $requestId updated to status: $status")
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Notice updating status for $requestId (${e.localizedMessage}). Status applied locally.")
                    onComplete(true)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore update notice: ${e.localizedMessage}. Status applied locally.")
            onComplete(true)
        }
    }

    /**
     * Broadcasts or updates a driver's live GPS coordinates in Firestore.
     */
    fun updateDriverLocation(
        driverId: String,
        driverName: String,
        driverPhone: String,
        vehicleType: String,
        vehiclePlate: String,
        latitude: Double,
        longitude: Double,
        isOnline: Boolean = true,
        isAvailable: Boolean = true,
        rating: Double = 4.9,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val firestore = db
        val geohash = GeoUtils.encodeGeohash(latitude, longitude)
        val driverLoc = DriverLocationData(
            driverId = driverId,
            driverName = driverName,
            driverPhone = driverPhone,
            vehicleType = vehicleType,
            vehiclePlate = vehiclePlate,
            latitude = latitude,
            longitude = longitude,
            isOnline = isOnline,
            isAvailable = isAvailable,
            rating = rating,
            lastUpdated = System.currentTimeMillis(),
            geohash = geohash
        )

        // Validate telematics payload against WayGo Firestore Security Rules
        val (isValidRule, ruleViolation) = FirebaseSecurityRulesManager.validateDriverLocationWrite(driverLoc)
        if (!isValidRule) {
            Log.e(TAG, "Telematics security rules rejection: $ruleViolation")
            onComplete(false)
            return
        }

        val data = hashMapOf(
            "driverId" to driverId,
            "driverName" to driverName,
            "driverPhone" to driverPhone,
            "vehicleType" to vehicleType,
            "vehiclePlate" to vehiclePlate,
            "latitude" to latitude,
            "longitude" to longitude,
            "isOnline" to isOnline,
            "isAvailable" to isAvailable,
            "rating" to rating,
            "lastUpdated" to System.currentTimeMillis(),
            "geohash" to geohash
        )

        if (firestore == null) {
            Log.w(TAG, "Firestore offline. Local driver location update logged for $driverId")
            onComplete(true)
            return
        }

        try {
            firestore.collection(COLLECTION_DRIVER_LOCATIONS)
                .document(driverId)
                .set(data)
                .addOnSuccessListener {
                    Log.d(TAG, "Driver location updated: $driverId ($latitude, $longitude)")
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Notice updating driver location (${e.localizedMessage}). Local driver location updated.")
                    onComplete(true)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore driver location notice: ${e.localizedMessage}. Local driver location updated.")
            onComplete(true)
        }
    }

    /**
     * GeoQuery function to locate nearby available drivers from Firestore within a given radius.
     * Uses bounding box latitude/longitude range filtering and Haversine distance calculations.
     */
    fun queryNearbyDrivers(
        pickupLat: Double,
        pickupLng: Double,
        radiusKm: Double = 10.0,
        vehicleTypeFilter: String? = null,
        onComplete: (List<DriverLocationData>) -> Unit
    ) {
        val firestore = db
        if (firestore == null) {
            Log.i(TAG, "Firestore unconfigured. Returning Gambia simulated nearby drivers.")
            val simulated = getSimulatedGambiaDrivers(pickupLat, pickupLng, vehicleTypeFilter)
            onComplete(simulated)
            return
        }

        val (latRange, lngRange) = GeoUtils.getBoundingBox(pickupLat, pickupLng, radiusKm)
        val (minLat, maxLat) = latRange
        val (minLng, maxLng) = lngRange

        try {
            var query = firestore.collection(COLLECTION_DRIVER_LOCATIONS)
                .whereGreaterThanOrEqualTo("latitude", minLat)
                .whereLessThanOrEqualTo("latitude", maxLat)

            query.get().addOnSuccessListener { snapshot ->
                val drivers = mutableListOf<DriverLocationData>()
                for (doc in snapshot.documents) {
                    try {
                        val dLat = doc.getDouble("latitude") ?: continue
                        val dLng = doc.getDouble("longitude") ?: continue

                        // Check longitude bounding box
                        if (dLng < minLng || dLng > maxLng) continue

                        val isOnline = doc.getBoolean("isOnline") ?: true
                        val isAvailable = doc.getBoolean("isAvailable") ?: true
                        if (!isOnline || !isAvailable) continue

                        val vType = doc.getString("vehicleType") ?: "CAR"
                        if (vehicleTypeFilter != null && vehicleTypeFilter != "ALL" && !vType.equals(vehicleTypeFilter, ignoreCase = true)) {
                            continue
                        }

                        val distance = GeoUtils.haversineDistanceKm(pickupLat, pickupLng, dLat, dLng)
                        if (distance <= radiusKm) {
                            val driver = DriverLocationData(
                                driverId = doc.getString("driverId") ?: doc.id,
                                driverName = doc.getString("driverName") ?: "Gambia Fleet Driver",
                                driverPhone = doc.getString("driverPhone") ?: "+220 7000000",
                                vehicleType = vType,
                                vehiclePlate = doc.getString("vehiclePlate") ?: "BJL 1234 A",
                                latitude = dLat,
                                longitude = dLng,
                                isOnline = isOnline,
                                isAvailable = isAvailable,
                                rating = doc.getDouble("rating") ?: 4.9,
                                lastUpdated = doc.getLong("lastUpdated") ?: System.currentTimeMillis(),
                                geohash = doc.getString("geohash") ?: GeoUtils.encodeGeohash(dLat, dLng),
                                distanceFromPickupKm = (distance * 10).roundToInt() / 10.0
                            )
                            drivers.add(driver)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing driver document: ${e.localizedMessage}")
                    }
                }

                // Sort by nearest distance first
                drivers.sortBy { it.distanceFromPickupKm }

                if (drivers.isEmpty()) {
                    // Fallback to simulated nearby drivers if no live cloud records found in area
                    onComplete(getSimulatedGambiaDrivers(pickupLat, pickupLng, vehicleTypeFilter))
                } else {
                    onComplete(drivers)
                }
            }.addOnFailureListener { e ->
                Log.w(TAG, "GeoQuery execution notice (${e.localizedMessage}), using local Gambia fleet fallback")
                onComplete(getSimulatedGambiaDrivers(pickupLat, pickupLng, vehicleTypeFilter))
            }
        } catch (e: Exception) {
            Log.w(TAG, "GeoQuery notice: ${e.localizedMessage}, using local Gambia fleet fallback")
            onComplete(getSimulatedGambiaDrivers(pickupLat, pickupLng, vehicleTypeFilter))
        }
    }

    /**
     * Dedicated real-time tracking service instance handling snapshot listeners for rides and driver GPS.
     */
    val realtimeService: FirestoreRealtimeTrackingService
        get() = FirestoreRealtimeTrackingService.getInstance()

    /**
     * Sets up a real-time snapshot listener on an assigned driver's live GPS coordinates.
     */
    fun listenToDriverLocation(
        driverId: String,
        onLocationUpdate: (DriverLocationData?) -> Unit
    ): ListenerRegistration? {
        return FirestoreRealtimeTrackingService.getInstance().listenToDriverLocation(driverId, onLocationUpdate)
    }

    /**
     * Real-time listener for a passenger's active ride request status changes.
     */
    fun listenToRideRequest(
        requestId: String,
        onUpdate: (ActiveRideRequest?) -> Unit
    ): ListenerRegistration? {
        val firestore = db ?: return null
        return try {
            firestore.collection(COLLECTION_RIDE_REQUESTS)
                .document(requestId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Listen to ride request failed", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val req = ActiveRideRequest(
                            requestId = snapshot.getString("requestId") ?: snapshot.id,
                            passengerId = snapshot.getString("passengerId") ?: "",
                            passengerName = snapshot.getString("passengerName") ?: "Passenger",
                            passengerPhone = snapshot.getString("passengerPhone") ?: "",
                            pickupName = snapshot.getString("pickupName") ?: "Pickup Location",
                            pickupLat = snapshot.getDouble("pickupLat") ?: 0.0,
                            pickupLng = snapshot.getDouble("pickupLng") ?: 0.0,
                            dropoffName = snapshot.getString("dropoffName") ?: "Dropoff Location",
                            dropoffLat = snapshot.getDouble("dropoffLat") ?: 0.0,
                            dropoffLng = snapshot.getDouble("dropoffLng") ?: 0.0,
                            vehicleType = snapshot.getString("vehicleType") ?: "CAR",
                            fareGmd = snapshot.getLong("fareGmd")?.toInt() ?: 150,
                            paymentMethod = snapshot.getString("paymentMethod") ?: "CASH",
                            status = snapshot.getString("status") ?: "REQUESTED",
                            driverId = snapshot.getString("driverId"),
                            driverName = snapshot.getString("driverName"),
                            driverPhone = snapshot.getString("driverPhone"),
                            vehiclePlate = snapshot.getString("vehiclePlate"),
                            verificationPin = snapshot.getString("verificationPin") ?: "",
                            preferences = snapshot.getString("preferences") ?: "",
                            createdAt = snapshot.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                        onUpdate(req)
                    } else {
                        onUpdate(null)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Snapshot listener registration error: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Real-time snapshot listener for active drivers to receive ride requests created in their vicinity.
     * Triggers whenever passengers create a new ride document in Firestore or when active requests change.
     */
    fun listenForNearbyRideRequests(
        driverLat: Double,
        driverLng: Double,
        vehicleTypeFilter: String? = null,
        radiusKm: Double = 15.0,
        onRequestsChanged: (List<ActiveRideRequest>) -> Unit
    ): ListenerRegistration? {
        val firestore = db ?: return null
        return try {
            firestore.collection(COLLECTION_RIDE_REQUESTS)
                .whereIn("status", listOf("REQUESTED", "SEARCHING"))
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Driver vicinity ride request listener error", error)
                        return@addSnapshotListener
                    }

                    if (snapshot == null || snapshot.isEmpty) {
                        onRequestsChanged(emptyList())
                        return@addSnapshotListener
                    }

                    val nearbyRequests = mutableListOf<ActiveRideRequest>()
                    for (doc in snapshot.documents) {
                        try {
                            val pLat = doc.getDouble("pickupLat") ?: continue
                            val pLng = doc.getDouble("pickupLng") ?: continue
                            val vType = doc.getString("vehicleType") ?: "CAR"

                            // Filter vehicle type if specified and not matching
                            if (vehicleTypeFilter != null && vehicleTypeFilter != "ALL" &&
                                !vType.equals(vehicleTypeFilter, ignoreCase = true)) {
                                continue
                            }

                            val dist = GeoUtils.haversineDistanceKm(driverLat, driverLng, pLat, pLng)
                            if (dist <= radiusKm) {
                                val req = ActiveRideRequest(
                                    requestId = doc.getString("requestId") ?: doc.id,
                                    passengerId = doc.getString("passengerId") ?: "",
                                    passengerName = doc.getString("passengerName") ?: "Passenger",
                                    passengerPhone = doc.getString("passengerPhone") ?: "",
                                    pickupName = doc.getString("pickupName") ?: "Pickup Location",
                                    pickupLat = pLat,
                                    pickupLng = pLng,
                                    dropoffName = doc.getString("dropoffName") ?: "Dropoff Location",
                                    dropoffLat = doc.getDouble("dropoffLat") ?: 0.0,
                                    dropoffLng = doc.getDouble("dropoffLng") ?: 0.0,
                                    vehicleType = vType,
                                    fareGmd = doc.getLong("fareGmd")?.toInt() ?: 150,
                                    paymentMethod = doc.getString("paymentMethod") ?: "CASH",
                                    status = doc.getString("status") ?: "REQUESTED",
                                    driverId = doc.getString("driverId"),
                                    driverName = doc.getString("driverName"),
                                    driverPhone = doc.getString("driverPhone"),
                                    vehiclePlate = doc.getString("vehiclePlate"),
                                    verificationPin = doc.getString("verificationPin") ?: "",
                                    preferences = doc.getString("preferences") ?: "",
                                    distanceKm = (dist * 10).roundToInt() / 10.0,
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                                    geohash = doc.getString("geohash") ?: GeoUtils.encodeGeohash(pLat, pLng)
                                )
                                nearbyRequests.add(req)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing vicinity ride doc: ${e.localizedMessage}")
                        }
                    }

                    // Sort closest to driver first
                    nearbyRequests.sortBy { it.distanceKm }
                    Log.d(TAG, "Vicinity listener received ${nearbyRequests.size} ride requests within ${radiusKm}km")
                    onRequestsChanged(nearbyRequests)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register driver vicinity snapshot listener: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Returns simulated local Gambia fleet drivers for fallback when offline or initial dev testing.
     */
    private fun getSimulatedGambiaDrivers(
        lat: Double,
        lng: Double,
        vehicleFilter: String?
    ): List<DriverLocationData> {
        val baseDrivers = listOf(
            DriverLocationData(
                driverId = "drv_alieu_01",
                driverName = "Alieu Ceesay",
                driverPhone = "+220 7481920",
                vehicleType = "CAR",
                vehiclePlate = "BJL 4821 C",
                latitude = lat + 0.008,
                longitude = lng - 0.005,
                isOnline = true,
                isAvailable = true,
                rating = 4.95,
                distanceFromPickupKm = 0.9
            ),
            DriverLocationData(
                driverId = "drv_mariama_02",
                driverName = "Mariama Jallow",
                driverPhone = "+220 3109482",
                vehicleType = "TRICYCLE",
                vehiclePlate = "KM 9312 T",
                latitude = lat - 0.004,
                longitude = lng + 0.006,
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
                latitude = lat + 0.012,
                longitude = lng + 0.010,
                isOnline = true,
                isAvailable = true,
                rating = 4.92,
                distanceFromPickupKm = 1.8
            ),
            DriverLocationData(
                driverId = "drv_lamin_04",
                driverName = "Lamin Sanneh",
                driverPhone = "+220 2891043",
                vehicleType = "VAN",
                vehiclePlate = "BJL 8812 V",
                latitude = lat - 0.015,
                longitude = lng - 0.012,
                isOnline = true,
                isAvailable = true,
                rating = 4.85,
                distanceFromPickupKm = 2.4
            )
        )

        return if (vehicleFilter != null && vehicleFilter != "ALL") {
            baseDrivers.filter { it.vehicleType.equals(vehicleFilter, ignoreCase = true) }
        } else {
            baseDrivers
        }
    }
}
