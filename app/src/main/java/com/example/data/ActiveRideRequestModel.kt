package com.example.data

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Enumeration representing the standardized lifecycle states of a ride request in Firestore.
 * Supports canonical values ("pending", "active", "completed", "cancelled") as well as legacy aliases.
 */
enum class RideStatus(val value: String, val label: String) {
    PENDING("pending", "Pending Match"),
    ACTIVE("active", "Active / En Route"),
    COMPLETED("completed", "Completed"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun fromString(status: String?): RideStatus {
            return when (status?.lowercase()?.trim()) {
                "pending", "requested", "searching", "new" -> PENDING
                "active", "accepted", "arrived", "en_route", "en-route", "in_progress", "started" -> ACTIVE
                "completed", "finished", "ended", "done" -> COMPLETED
                "cancelled", "canceled", "rejected", "declined" -> CANCELLED
                else -> PENDING
            }
        }
    }
}

/**
 * Structured geospatial and address representation for ride locations in Firestore.
 * Stores human-readable names, full addresses, and precise GPS coordinates.
 */
data class RideLocation(
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    /**
     * Converts this RideLocation into a Firestore nested Map object.
     */
    fun toMap(): Map<String, Any> = hashMapOf(
        "name" to name,
        "address" to address.ifBlank { name },
        "latitude" to latitude,
        "longitude" to longitude
    )

    fun toCoordinatesString(): String = String.format("%.5f, %.5f", latitude, longitude)

    companion object {
        fun fromMap(map: Map<String, Any?>?): RideLocation {
            if (map == null) return RideLocation()
            val name = map["name"] as? String ?: ""
            val address = map["address"] as? String ?: name
            val lat = (map["latitude"] as? Number)?.toDouble() ?: 0.0
            val lng = (map["longitude"] as? Number)?.toDouble() ?: 0.0
            return RideLocation(
                name = name,
                address = address,
                latitude = lat,
                longitude = lng
            )
        }
    }
}

/**
 * Data Model for Active Ride Requests in Cloud Firestore.
 *
 * Firestore Collection: `active_ride_requests`
 * Document ID: `requestId` (e.g. "req_1728204928_482")
 *
 * Encapsulates the entire contract including pickup location, destination,
 * passenger ID, status ("pending", "active", "completed"), vehicle details, and telematics.
 */
data class ActiveRideRequest(
    val requestId: String = "",
    val passengerId: String = "",
    val passengerName: String = "",
    val passengerPhone: String = "",
    val pickupName: String = "",
    val pickupLat: Double = 0.0,
    val pickupLng: Double = 0.0,
    val dropoffName: String = "",
    val dropoffLat: Double = 0.0,
    val dropoffLng: Double = 0.0,
    val vehicleType: String = "CAR", // "CAR", "TAXI", "TRICYCLE", "VAN"
    val fareGmd: Int = 0,
    val paymentMethod: String = "CASH", // "CASH", "WAVE", "AFRICELL", "FLUTTERWAVE", "STRIPE"
    val status: String = RideStatus.PENDING.value, // "pending", "active", "completed", "cancelled" (or legacy "REQUESTED", "ACCEPTED")
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val vehiclePlate: String? = null,
    val verificationPin: String = "",
    val preferences: String = "",
    val distanceKm: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val geohash: String = GeoUtils.encodeGeohash(pickupLat, pickupLng),
    val pickupLocation: RideLocation = RideLocation(
        name = pickupName,
        address = pickupName,
        latitude = pickupLat,
        longitude = pickupLng
    ),
    val destination: RideLocation = RideLocation(
        name = dropoffName,
        address = dropoffName,
        latitude = dropoffLat,
        longitude = dropoffLng
    )
) {
    /**
     * Resolves the canonical status enum.
     */
    val normalizedStatus: RideStatus
        get() = RideStatus.fromString(status)

    val isPending: Boolean
        get() = normalizedStatus == RideStatus.PENDING

    val isActive: Boolean
        get() = normalizedStatus == RideStatus.ACTIVE

    val isCompleted: Boolean
        get() = normalizedStatus == RideStatus.COMPLETED

    val isCancelled: Boolean
        get() = normalizedStatus == RideStatus.CANCELLED

    /**
     * Returns an effective Pickup RideLocation object regardless of whether
     * flat coordinates or structured nested objects were populated.
     */
    fun resolvePickupLocation(): RideLocation {
        return if (pickupLocation.latitude != 0.0 || pickupLocation.longitude != 0.0 || pickupLocation.name.isNotBlank()) {
            pickupLocation
        } else {
            RideLocation(name = pickupName, address = pickupName, latitude = pickupLat, longitude = pickupLng)
        }
    }

    /**
     * Returns an effective Destination RideLocation object regardless of whether
     * flat coordinates or structured nested objects were populated.
     */
    fun resolveDestination(): RideLocation {
        return if (destination.latitude != 0.0 || destination.longitude != 0.0 || destination.name.isNotBlank()) {
            destination
        } else {
            RideLocation(name = dropoffName, address = dropoffName, latitude = dropoffLat, longitude = dropoffLng)
        }
    }

    /**
     * Serializes this active ride request into a Firestore document structure.
     * Includes structured nested objects ("pickupLocation", "destination") as well
     * as top-level fields for index queries.
     */
    fun toFirestoreMap(): Map<String, Any?> {
        val resolvedPickup = resolvePickupLocation()
        val resolvedDest = resolveDestination()

        return hashMapOf(
            "requestId" to requestId,
            "passengerId" to passengerId,
            "passengerName" to passengerName,
            "passengerPhone" to passengerPhone,
            "pickupLocation" to resolvedPickup.toMap(),
            "destination" to resolvedDest.toMap(),
            "pickupName" to (pickupName.ifBlank { resolvedPickup.name }),
            "pickupLat" to (if (pickupLat != 0.0) pickupLat else resolvedPickup.latitude),
            "pickupLng" to (if (pickupLng != 0.0) pickupLng else resolvedPickup.longitude),
            "dropoffName" to (dropoffName.ifBlank { resolvedDest.name }),
            "destinationName" to (resolvedDest.name.ifBlank { dropoffName }),
            "dropoffLat" to (if (dropoffLat != 0.0) dropoffLat else resolvedDest.latitude),
            "dropoffLng" to (if (dropoffLng != 0.0) dropoffLng else resolvedDest.longitude),
            "vehicleType" to vehicleType,
            "fareGmd" to fareGmd,
            "paymentMethod" to paymentMethod,
            "status" to status,
            "driverId" to driverId,
            "driverName" to driverName,
            "driverPhone" to driverPhone,
            "vehiclePlate" to vehiclePlate,
            "verificationPin" to verificationPin,
            "preferences" to preferences,
            "distanceKm" to distanceKm,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "geohash" to geohash
        )
    }

    companion object {
        /**
         * Factory for creating a brand new pending ride request with structured locations.
         */
        fun createPending(
            requestId: String,
            passengerId: String,
            passengerName: String,
            passengerPhone: String,
            pickupLocation: RideLocation,
            destination: RideLocation,
            fareGmd: Int,
            vehicleType: String = "CAR",
            paymentMethod: String = "CASH",
            preferences: String = "",
            verificationPin: String = (1000..9999).random().toString()
        ): ActiveRideRequest {
            return ActiveRideRequest(
                requestId = requestId,
                passengerId = passengerId,
                passengerName = passengerName,
                passengerPhone = passengerPhone,
                pickupName = pickupLocation.name.ifBlank { pickupLocation.address },
                pickupLat = pickupLocation.latitude,
                pickupLng = pickupLocation.longitude,
                dropoffName = destination.name.ifBlank { destination.address },
                dropoffLat = destination.latitude,
                dropoffLng = destination.longitude,
                vehicleType = vehicleType,
                fareGmd = fareGmd,
                paymentMethod = paymentMethod,
                status = RideStatus.PENDING.value,
                verificationPin = verificationPin,
                preferences = preferences,
                pickupLocation = pickupLocation,
                destination = destination,
                geohash = GeoUtils.encodeGeohash(pickupLocation.latitude, pickupLocation.longitude),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        /**
         * Safely parses a Firestore DocumentSnapshot into an ActiveRideRequest instance,
         * gracefully extracting both nested map objects and flat legacy fields.
         */
        fun fromFirestore(doc: DocumentSnapshot): ActiveRideRequest? {
            if (!doc.exists()) return null
            return try {
                @Suppress("UNCHECKED_CAST")
                val pickupMap = doc.get("pickupLocation") as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val destMap = (doc.get("destination") as? Map<String, Any?>)
                    ?: (doc.get("dropoffLocation") as? Map<String, Any?>)

                val pLat = (pickupMap?.get("latitude") as? Number)?.toDouble()
                    ?: doc.getDouble("pickupLat") ?: 0.0
                val pLng = (pickupMap?.get("longitude") as? Number)?.toDouble()
                    ?: doc.getDouble("pickupLng") ?: 0.0
                val pName = (pickupMap?.get("name") as? String)
                    ?: (pickupMap?.get("address") as? String)
                    ?: doc.getString("pickupName") ?: "Pickup Location"

                val dLat = (destMap?.get("latitude") as? Number)?.toDouble()
                    ?: doc.getDouble("dropoffLat") ?: 0.0
                val dLng = (destMap?.get("longitude") as? Number)?.toDouble()
                    ?: doc.getDouble("dropoffLng") ?: 0.0
                val dName = (destMap?.get("name") as? String)
                    ?: (destMap?.get("address") as? String)
                    ?: doc.getString("destinationName") ?: doc.getString("dropoffName") ?: "Destination"

                val pickupLoc = if (pickupMap != null) {
                    RideLocation.fromMap(pickupMap)
                } else {
                    RideLocation(name = pName, address = pName, latitude = pLat, longitude = pLng)
                }

                val destLoc = if (destMap != null) {
                    RideLocation.fromMap(destMap)
                } else {
                    RideLocation(name = dName, address = dName, latitude = dLat, longitude = dLng)
                }

                ActiveRideRequest(
                    requestId = doc.getString("requestId") ?: doc.id,
                    passengerId = doc.getString("passengerId") ?: "",
                    passengerName = doc.getString("passengerName") ?: "Passenger",
                    passengerPhone = doc.getString("passengerPhone") ?: "",
                    pickupName = pName,
                    pickupLat = pLat,
                    pickupLng = pLng,
                    dropoffName = dName,
                    dropoffLat = dLat,
                    dropoffLng = dLng,
                    vehicleType = doc.getString("vehicleType") ?: "CAR",
                    fareGmd = doc.getLong("fareGmd")?.toInt() ?: 150,
                    paymentMethod = doc.getString("paymentMethod") ?: "CASH",
                    status = doc.getString("status") ?: RideStatus.PENDING.value,
                    driverId = doc.getString("driverId"),
                    driverName = doc.getString("driverName"),
                    driverPhone = doc.getString("driverPhone"),
                    vehiclePlate = doc.getString("vehiclePlate"),
                    verificationPin = doc.getString("verificationPin") ?: "",
                    preferences = doc.getString("preferences") ?: "",
                    distanceKm = doc.getDouble("distanceKm") ?: 0.0,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                    geohash = doc.getString("geohash") ?: GeoUtils.encodeGeohash(pLat, pLng),
                    pickupLocation = pickupLoc,
                    destination = destLoc
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Architectural specification and documentation of the Firestore Collection Structure
 * for WayGo Active Ride Requests.
 */
object ActiveRideRequestFirestoreSchema {
    /** Primary collection path in Firestore */
    const val COLLECTION_RIDE_REQUESTS = "ride_requests"

    /** Legacy / real-time collection path in Firestore */
    const val COLLECTION_NAME = "active_ride_requests"

    // Primary Field Keys
    const val FIELD_REQUEST_ID = "requestId"
    const val FIELD_PASSENGER_ID = "passengerId"
    const val FIELD_PASSENGER_NAME = "passengerName"
    const val FIELD_PASSENGER_PHONE = "passengerPhone"
    const val FIELD_PICKUP_LOCATION = "pickupLocation"
    const val FIELD_DESTINATION = "destination"
    const val FIELD_STATUS = "status"
    const val FIELD_DRIVER_ID = "driverId"
    const val FIELD_DRIVER_NAME = "driverName"
    const val FIELD_VEHICLE_TYPE = "vehicleType"
    const val FIELD_FARE_GMD = "fareGmd"
    const val FIELD_PAYMENT_METHOD = "paymentMethod"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_UPDATED_AT = "updatedAt"
    const val FIELD_GEOHASH = "geohash"

    // Canonical Status Constants
    const val STATUS_PENDING = "pending"
    const val STATUS_ACTIVE = "active"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_CANCELLED = "cancelled"

    /**
     * Recommended Firestore Composite Indexes:
     * 1. (passengerId ASC, status ASC, createdAt DESC) -> For passenger active ride queries.
     * 2. (status ASC, vehicleType ASC, createdAt ASC) -> For driver dispatch match queues.
     * 3. (driverId ASC, status ASC) -> For driver active trip assignment.
     * 4. (status ASC, geohash ASC) -> For proximity GeoQueries.
     */
    val RECOMMENDED_INDEXES = listOf(
        "active_ride_requests: passengerId (ASC), status (ASC), createdAt (DESC)",
        "active_ride_requests: status (ASC), vehicleType (ASC), createdAt (ASC)",
        "active_ride_requests: driverId (ASC), status (ASC)",
        "active_ride_requests: status (ASC), geohash (ASC)"
    )

    /**
     * Sample JSON representation for a PENDING ride request.
     */
    fun samplePendingDocumentJson(): String = """
    {
      "requestId": "req_1728204928_001",
      "passengerId": "usr_fatou_01",
      "passengerName": "Fatou Joof",
      "passengerPhone": "+220 782 1290",
      "pickupLocation": {
        "name": "Kairaba Avenue",
        "address": "Kairaba Avenue, Serrekunda, The Gambia",
        "latitude": 13.4471,
        "longitude": -16.6791
      },
      "destination": {
        "name": "Albert Market",
        "address": "Albert Market, Banjul, The Gambia",
        "latitude": 13.4533,
        "longitude": -16.5746
      },
      "status": "pending",
      "vehicleType": "CAR",
      "fareGmd": 350,
      "paymentMethod": "WAVE",
      "driverId": null,
      "verificationPin": "4821",
      "createdAt": 1728204928000,
      "updatedAt": 1728204928000
    }
    """.trimIndent()

    /**
     * Sample JSON representation for an ACTIVE ride request.
     */
    fun sampleActiveDocumentJson(): String = """
    {
      "requestId": "req_1728204928_001",
      "passengerId": "usr_fatou_01",
      "passengerName": "Fatou Joof",
      "passengerPhone": "+220 782 1290",
      "pickupLocation": {
        "name": "Kairaba Avenue",
        "address": "Kairaba Avenue, Serrekunda, The Gambia",
        "latitude": 13.4471,
        "longitude": -16.6791
      },
      "destination": {
        "name": "Albert Market",
        "address": "Albert Market, Banjul, The Gambia",
        "latitude": 13.4533,
        "longitude": -16.5746
      },
      "status": "active",
      "vehicleType": "CAR",
      "fareGmd": 350,
      "paymentMethod": "WAVE",
      "driverId": "drv_alieu",
      "driverName": "Alieu Ceesay",
      "driverPhone": "+220 992 4831",
      "vehiclePlate": "BJL 4821 C",
      "verificationPin": "4821",
      "createdAt": 1728204928000,
      "updatedAt": 1728204990000
    }
    """.trimIndent()

    /**
     * Sample JSON representation for a COMPLETED ride request.
     */
    fun sampleCompletedDocumentJson(): String = """
    {
      "requestId": "req_1728204928_001",
      "passengerId": "usr_fatou_01",
      "passengerName": "Fatou Joof",
      "passengerPhone": "+220 782 1290",
      "pickupLocation": {
        "name": "Kairaba Avenue",
        "address": "Kairaba Avenue, Serrekunda, The Gambia",
        "latitude": 13.4471,
        "longitude": -16.6791
      },
      "destination": {
        "name": "Albert Market",
        "address": "Albert Market, Banjul, The Gambia",
        "latitude": 13.4533,
        "longitude": -16.5746
      },
      "status": "completed",
      "vehicleType": "CAR",
      "fareGmd": 350,
      "paymentMethod": "WAVE",
      "driverId": "drv_alieu",
      "driverName": "Alieu Ceesay",
      "driverPhone": "+220 992 4831",
      "vehiclePlate": "BJL 4821 C",
      "verificationPin": "4821",
      "createdAt": 1728204928000,
      "updatedAt": 1728206200000
    }
    """.trimIndent()
}
