package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class ActiveRideRequestDataModelTest {

    @Test
    fun testActiveRideRequestModelCreationWithStructuredLocations() {
        val pickup = RideLocation(
            name = "Kairaba Avenue",
            address = "Kairaba Avenue, Serrekunda, The Gambia",
            latitude = 13.4471,
            longitude = -16.6791
        )
        val destination = RideLocation(
            name = "Albert Market",
            address = "Albert Market, Banjul, The Gambia",
            latitude = 13.4533,
            longitude = -16.5746
        )

        val ride = ActiveRideRequest(
            requestId = "req_cuj_101",
            passengerId = "usr_fatou_01",
            passengerName = "Fatou Joof",
            passengerPhone = "+220 782 1290",
            pickupLocation = pickup,
            destination = destination,
            pickupName = pickup.name,
            pickupLat = pickup.latitude,
            pickupLng = pickup.longitude,
            dropoffName = destination.name,
            dropoffLat = destination.latitude,
            dropoffLng = destination.longitude,
            fareGmd = 350,
            vehicleType = "CAR",
            paymentMethod = "WAVE",
            status = RideStatus.PENDING.value
        )

        assertEquals("req_cuj_101", ride.requestId)
        assertEquals("usr_fatou_01", ride.passengerId)
        assertEquals("Kairaba Avenue", ride.pickupLocation.name)
        assertEquals(13.4471, ride.pickupLocation.latitude, 0.0001)
        assertEquals(-16.6791, ride.pickupLocation.longitude, 0.0001)
        assertEquals("Albert Market", ride.destination.name)
        assertEquals(13.4533, ride.destination.latitude, 0.0001)
        assertEquals(-16.5746, ride.destination.longitude, 0.0001)
        assertEquals(RideStatus.PENDING, ride.normalizedStatus)
        assertTrue(ride.isPending)
        assertFalse(ride.isActive)
        assertFalse(ride.isCompleted)
    }

    @Test
    fun testRideStatusTransitionsAndNormalizations() {
        // Pending
        val pendingRide = ActiveRideRequest(
            requestId = "req_1",
            passengerId = "usr_01",
            status = "pending"
        )
        assertEquals(RideStatus.PENDING, pendingRide.normalizedStatus)
        assertTrue(pendingRide.isPending)

        // Active (e.g. driver assigned and moving)
        val activeRide = pendingRide.copy(
            status = "active",
            driverId = "drv_alieu",
            driverName = "Alieu Ceesay"
        )
        assertEquals(RideStatus.ACTIVE, activeRide.normalizedStatus)
        assertTrue(activeRide.isActive)
        assertFalse(activeRide.isPending)
        assertFalse(activeRide.isCompleted)

        // Completed
        val completedRide = activeRide.copy(
            status = "completed"
        )
        assertEquals(RideStatus.COMPLETED, completedRide.normalizedStatus)
        assertTrue(completedRide.isCompleted)
        assertFalse(completedRide.isActive)

        // Legacy string status backward compatibility
        val legacyRequested = ActiveRideRequest(status = "REQUESTED")
        assertEquals(RideStatus.PENDING, legacyRequested.normalizedStatus)
        assertTrue(legacyRequested.isPending)

        val legacyAccepted = ActiveRideRequest(status = "ACCEPTED")
        assertEquals(RideStatus.ACTIVE, legacyAccepted.normalizedStatus)
        assertTrue(legacyAccepted.isActive)

        val legacyEnRoute = ActiveRideRequest(status = "EN_ROUTE")
        assertEquals(RideStatus.ACTIVE, legacyEnRoute.normalizedStatus)
        assertTrue(legacyEnRoute.isActive)

        val legacyCompleted = ActiveRideRequest(status = "COMPLETED")
        assertEquals(RideStatus.COMPLETED, legacyCompleted.normalizedStatus)
        assertTrue(legacyCompleted.isCompleted)
    }

    @Test
    fun testFirestoreMapSerializationStructure() {
        val pickup = RideLocation(
            name = "Senegambia Strip",
            address = "Senegambia Tourism Corridor, Kololi",
            latitude = 13.4380,
            longitude = -16.7120
        )
        val destination = RideLocation(
            name = "Banjul International Airport",
            address = "Yundum, Banjul International Airport",
            latitude = 13.3380,
            longitude = -16.6520
        )

        val ride = ActiveRideRequest(
            requestId = "req_airport_transfer_01",
            passengerId = "usr_alex_02",
            passengerName = "Alex Touray",
            passengerPhone = "+220 994 2231",
            pickupLocation = pickup,
            destination = destination,
            pickupName = pickup.name,
            pickupLat = pickup.latitude,
            pickupLng = pickup.longitude,
            dropoffName = destination.name,
            dropoffLat = destination.latitude,
            dropoffLng = destination.longitude,
            vehicleType = "CAR",
            fareGmd = 600,
            paymentMethod = "AFRICELL",
            status = "pending"
        )

        val firestoreMap = ride.toFirestoreMap()

        // 1. Verify primary keys
        assertEquals("req_airport_transfer_01", firestoreMap["requestId"])
        assertEquals("usr_alex_02", firestoreMap["passengerId"])
        assertEquals("pending", firestoreMap["status"])
        assertEquals(600, firestoreMap["fareGmd"])

        // 2. Verify nested pickupLocation structure
        @Suppress("UNCHECKED_CAST")
        val pickupMap = firestoreMap["pickupLocation"] as? Map<String, Any>
        assertNotNull("pickupLocation should be a nested Map in Firestore", pickupMap)
        assertEquals("Senegambia Strip", pickupMap?.get("name"))
        assertEquals("Senegambia Tourism Corridor, Kololi", pickupMap?.get("address"))
        assertEquals(13.4380, (pickupMap?.get("latitude") as? Number)?.toDouble() ?: 0.0, 0.0001)
        assertEquals(-16.7120, (pickupMap?.get("longitude") as? Number)?.toDouble() ?: 0.0, 0.0001)

        // 3. Verify nested destination structure
        @Suppress("UNCHECKED_CAST")
        val destMap = firestoreMap["destination"] as? Map<String, Any>
        assertNotNull("destination should be a nested Map in Firestore", destMap)
        assertEquals("Banjul International Airport", destMap?.get("name"))
        assertEquals("Yundum, Banjul International Airport", destMap?.get("address"))
        assertEquals(13.3380, (destMap?.get("latitude") as? Number)?.toDouble() ?: 0.0, 0.0001)
        assertEquals(-16.6520, (destMap?.get("longitude") as? Number)?.toDouble() ?: 0.0, 0.0001)

        // 4. Verify flat indexable fields for composite indexing
        assertEquals(13.4380, (firestoreMap["pickupLat"] as? Number)?.toDouble() ?: 0.0, 0.0001)
        assertEquals(-16.7120, (firestoreMap["pickupLng"] as? Number)?.toDouble() ?: 0.0, 0.0001)
        assertEquals(13.3380, (firestoreMap["dropoffLat"] as? Number)?.toDouble() ?: 0.0, 0.0001)
        assertEquals(-16.6520, (firestoreMap["dropoffLng"] as? Number)?.toDouble() ?: 0.0, 0.0001)
    }

    @Test
    fun testFactoryCreatePending() {
        val pickup = RideLocation(name = "Westfield Junction", latitude = 13.4385, longitude = -16.6760)
        val dest = RideLocation(name = "Arch 22 Banjul", latitude = 13.4580, longitude = -16.5820)

        val pending = ActiveRideRequest.createPending(
            requestId = "req_auto_001",
            passengerId = "usr_99",
            passengerName = "Musa",
            passengerPhone = "+220 300 0000",
            pickupLocation = pickup,
            destination = dest,
            fareGmd = 250,
            vehicleType = "TRICYCLE"
        )

        assertEquals("req_auto_001", pending.requestId)
        assertEquals("usr_99", pending.passengerId)
        assertEquals("pending", pending.status)
        assertEquals("TRICYCLE", pending.vehicleType)
        assertEquals(250, pending.fareGmd)
        assertEquals("Westfield Junction", pending.pickupLocation.name)
        assertEquals("Arch 22 Banjul", pending.destination.name)
        assertTrue(pending.isPending)
        assertFalse(pending.verificationPin.isBlank())
    }

    @Test
    fun testSecurityRuleComplianceForActiveRideRequest() {
        val pickup = RideLocation(name = "Kairaba", latitude = 13.4471, longitude = -16.6791)
        val dest = RideLocation(name = "Albert Market", latitude = 13.4533, longitude = -16.5746)

        val ride = ActiveRideRequest.createPending(
            requestId = "req_sec_audit",
            passengerId = "usr_passenger_ok",
            passengerName = "Samba",
            passengerPhone = "+220 711 0000",
            pickupLocation = pickup,
            destination = dest,
            fareGmd = 300
        )

        val (isValid, reason) = FirebaseSecurityRulesManager.validateRideRequestWrite(ride)
        assertTrue("Ride request should pass Firestore security rules: $reason", isValid)
    }

    @Test
    fun testFirestoreSchemaConstantsAndSampleDocuments() {
        assertEquals("active_ride_requests", ActiveRideRequestFirestoreSchema.COLLECTION_NAME)
        assertEquals("passengerId", ActiveRideRequestFirestoreSchema.FIELD_PASSENGER_ID)
        assertEquals("pickupLocation", ActiveRideRequestFirestoreSchema.FIELD_PICKUP_LOCATION)
        assertEquals("destination", ActiveRideRequestFirestoreSchema.FIELD_DESTINATION)
        assertEquals("status", ActiveRideRequestFirestoreSchema.FIELD_STATUS)

        val pendingJson = ActiveRideRequestFirestoreSchema.samplePendingDocumentJson()
        assertTrue(pendingJson.contains("\"status\": \"pending\""))
        assertTrue(pendingJson.contains("\"pickupLocation\""))
        assertTrue(pendingJson.contains("\"destination\""))
        assertTrue(pendingJson.contains("\"passengerId\""))

        val activeJson = ActiveRideRequestFirestoreSchema.sampleActiveDocumentJson()
        assertTrue(activeJson.contains("\"status\": \"active\""))
        assertTrue(activeJson.contains("\"driverId\": \"drv_alieu\""))

        val completedJson = ActiveRideRequestFirestoreSchema.sampleCompletedDocumentJson()
        assertTrue(completedJson.contains("\"status\": \"completed\""))
    }
}
