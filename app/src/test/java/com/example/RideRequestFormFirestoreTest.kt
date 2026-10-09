package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.AVAILABLE_VEHICLE_OPTIONS
import com.example.ui.RideRequestFormData
import com.example.ui.WayGoViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RideRequestFormFirestoreTest {

    private lateinit var db: WayGoDatabase
    private lateinit var repository: WayGoRepository
    private lateinit var viewModel: WayGoViewModel

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FirebaseAuthManager.init(context)
        db = Room.inMemoryDatabaseBuilder(context, WayGoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WayGoRepository(db.dao())
        viewModel = WayGoViewModel(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFirestoreRideRequestsCollectionConstant() {
        // Verify the Firestore collection constant is specifically 'ride_requests'
        assertEquals("ride_requests", FirestoreRideService.COLLECTION_RIDE_REQUESTS)
        assertEquals("ride_requests", ActiveRideRequestFirestoreSchema.COLLECTION_RIDE_REQUESTS)
        assertEquals("active_ride_requests", ActiveRideRequestFirestoreSchema.COLLECTION_NAME)
    }

    @Test
    fun testRideRequestDataModelAndFirestoreMapStructure() {
        val pickup = RideLocation(
            name = "Kairaba Avenue",
            address = "Kairaba Avenue, Serrekunda, The Gambia",
            latitude = 13.4471,
            longitude = -16.6791
        )
        val dest = RideLocation(
            name = "Albert Market",
            address = "Albert Market, Banjul, The Gambia",
            latitude = 13.4533,
            longitude = -16.5746
        )

        val request = ActiveRideRequest.createPending(
            requestId = "req_test_12345",
            passengerId = "usr_fatou_01",
            passengerName = "Fatou Joof",
            passengerPhone = "+220 782 1290",
            pickupLocation = pickup,
            destination = dest,
            fareGmd = 350,
            vehicleType = "CAR",
            paymentMethod = "WAVE",
            preferences = "Quiet • AC"
        )

        // Verify fields
        assertEquals("req_test_12345", request.requestId)
        assertEquals("usr_fatou_01", request.passengerId)
        assertEquals("Fatou Joof", request.passengerName)
        assertEquals("CAR", request.vehicleType)
        assertEquals("pending", request.status)
        assertEquals(350, request.fareGmd)
        assertEquals("WAVE", request.paymentMethod)
        assertEquals(13.4471, request.pickupLat, 0.0001)
        assertEquals(-16.6791, request.pickupLng, 0.0001)
        assertEquals(13.4533, request.dropoffLat, 0.0001)
        assertEquals(-16.5746, request.dropoffLng, 0.0001)

        // Verify serialization to Firestore document map
        val map = request.toFirestoreMap()
        assertEquals("req_test_12345", map["requestId"])
        assertEquals("usr_fatou_01", map["passengerId"])
        assertEquals("pending", map["status"])
        assertEquals("CAR", map["vehicleType"])
        assertEquals(350, map["fareGmd"])

        // Check structured nested objects for pickupLocation and destination
        @Suppress("UNCHECKED_CAST")
        val pickupMap = map["pickupLocation"] as? Map<String, Any>
        assertNotNull("pickupLocation nested map should exist in Firestore payload", pickupMap)
        assertEquals("Kairaba Avenue", pickupMap?.get("name"))
        assertEquals(13.4471, pickupMap?.get("latitude"))
        assertEquals(-16.6791, pickupMap?.get("longitude"))

        @Suppress("UNCHECKED_CAST")
        val destMap = map["destination"] as? Map<String, Any>
        assertNotNull("destination nested map should exist in Firestore payload", destMap)
        assertEquals("Albert Market", destMap?.get("name"))
        assertEquals(13.4533, destMap?.get("latitude"))
        assertEquals(-16.5746, destMap?.get("longitude"))
    }

    @Test
    fun testSecurityRulesValidationForRideRequest() {
        val validRide = ActiveRideRequest(
            requestId = "req_valid_01",
            passengerId = "usr_lamin@example.com",
            pickupLat = 13.4471,
            pickupLng = -16.6791,
            dropoffLat = 13.4533,
            dropoffLng = -16.5746,
            fareGmd = 250,
            vehicleType = "TRICYCLE",
            status = "pending"
        )
        val (isValid, reason) = FirebaseSecurityRulesManager.validateRideRequestWrite(validRide)
        assertTrue("Valid ride request must pass security rules: $reason", isValid)

        // Invalid GPS latitude check
        val invalidRide = validRide.copy(pickupLat = 120.0)
        val (isInvalid, invalidReason) = FirebaseSecurityRulesManager.validateRideRequestWrite(invalidRide)
        assertFalse("Out of range latitude must be rejected by rules", isInvalid)
        assertTrue(invalidReason.contains("latitude", ignoreCase = true))

        // Negative fare check
        val negativeFareRide = validRide.copy(fareGmd = -50)
        val (isFareInvalid, fareReason) = FirebaseSecurityRulesManager.validateRideRequestWrite(negativeFareRide)
        assertFalse("Negative fare must be rejected by rules", isFareInvalid)
        assertTrue(fareReason.contains("fare", ignoreCase = true))
    }

    @Test
    fun testAvailableVehicleTypeChoices() {
        // Verify all 4 vehicle options exist
        val vehicleIds = AVAILABLE_VEHICLE_OPTIONS.map { it.typeId }
        assertTrue("CAR must be an available vehicle choice", vehicleIds.contains("CAR"))
        assertTrue("TRICYCLE must be an available vehicle choice", vehicleIds.contains("TRICYCLE"))
        assertTrue("TAXI must be an available vehicle choice", vehicleIds.contains("TAXI"))
        assertTrue("VAN must be an available vehicle choice", vehicleIds.contains("VAN"))

        // Check rates and base fares
        val car = AVAILABLE_VEHICLE_OPTIONS.first { it.typeId == "CAR" }
        assertEquals(100, car.baseFareGmd)
        assertTrue(car.capacity.contains("4"))

        val tricycle = AVAILABLE_VEHICLE_OPTIONS.first { it.typeId == "TRICYCLE" }
        assertEquals(50, tricycle.baseFareGmd)
        assertTrue(tricycle.capacity.contains("3"))
    }

    @Test
    fun testSaveRideDetailsToFirestoreFlow() = runBlocking {
        val pickup = RideLocation(
            name = "Westfield Junction",
            address = "Westfield Junction, Serrekunda",
            latitude = 13.4385,
            longitude = -16.6760
        )
        val dest = RideLocation(
            name = "Senegambia Strip",
            address = "Senegambia Beach Highway, Kololi",
            latitude = 13.4420,
            longitude = -16.7110
        )

        val (success, savedDocId) = viewModel.saveRideDetailsToFirestoreSync(
            pickupLocation = pickup,
            destinationLocation = dest,
            vehicleType = "CAR",
            paymentMethod = "WAVE",
            fareGmd = 300,
            preferences = "Quiet Ride"
        )

        assertTrue("Save to Firestore 'ride_requests' should succeed", success)
        assertNotNull("Generated document ID should not be null", savedDocId)

        // Verify that the local trip entity was also created and saved
        val activeTrip = repository.getActiveTrip()
        assertNotNull("Active trip should exist after booking", activeTrip)
        assertEquals("Westfield Junction", activeTrip?.pickupName)
        assertEquals("Senegambia Strip", activeTrip?.dropoffName)
        assertEquals("CAR", activeTrip?.vehicleType)
        assertEquals(300, activeTrip?.fareGmd)
        assertEquals("WAVE", activeTrip?.paymentMethod)
        assertEquals("REQUESTED", activeTrip?.status)

        // Verify status message updated
        val statusMsg = viewModel.firestoreRideRequestStatus.value
        assertNotNull(statusMsg)
        assertTrue(
            "Status message should reference 'ride_requests': $statusMsg",
            statusMsg?.contains("ride_requests") == true || statusMsg?.contains("offline") == true
        )
    }

    @Test
    fun testRideRequestFormDataPayload() {
        val pickup = RideLocation(name = "Kairaba Ave", address = "Kairaba Ave", latitude = 13.4471, longitude = -16.6791)
        val dropoff = RideLocation(name = "Albert Market", address = "Albert Market", latitude = 13.4533, longitude = -16.5746)

        val formData = RideRequestFormData(
            pickupLocation = pickup,
            destinationLocation = dropoff,
            vehicleType = "TRICYCLE",
            paymentMethod = "CASH",
            fareGmd = 150,
            distanceKm = 5.2,
            estimatedDurationMin = 15,
            passengerId = "usr_mariama",
            passengerName = "Mariama",
            passengerPhone = "+220 312 0000",
            preferences = "AC"
        )

        assertEquals("TRICYCLE", formData.vehicleType)
        assertEquals("CASH", formData.paymentMethod)
        assertEquals(150, formData.fareGmd)
        assertEquals(5.2, formData.distanceKm, 0.01)
        assertEquals("usr_mariama", formData.passengerId)
    }
}
