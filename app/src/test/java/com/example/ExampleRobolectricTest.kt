package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.WayGoViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: WayGoDatabase
    private lateinit var repository: WayGoRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WayGoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WayGoRepository(db.dao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testSubmitRatingAndReview() = runBlocking {
        // 1. Seed driver
        val driver = DriverEntity(
            id = "drv_test",
            name = "Test Driver",
            phone = "+220 123 4567",
            vehicleType = "CAR",
            vehiclePlate = "BJL 1234 A",
            rating = 4.0f,
            approvalStatus = "APPROVED",
            isOnline = true,
            currentLat = 13.44,
            currentLng = -16.68,
            driverLicense = "DL-2026-TEST"
        )
        repository.saveDriver(driver)

        // 2. Seed active trip that is completed but unrated (rating = 0)
        val trip = TripEntity(
            id = "trip_test",
            passengerName = "Alex Johnson",
            driverId = "drv_test",
            driverName = "Test Driver",
            vehicleType = "CAR",
            vehiclePlate = "BJL 1234 A",
            pickupName = "Airport",
            dropoffName = "Hotel",
            pickupLat = 13.44,
            pickupLng = -16.68,
            dropoffLat = 13.45,
            dropoffLng = -16.69,
            fareGmd = 250,
            paymentMethod = "WAVE",
            status = "COMPLETED",
            rating = 0,
            reviewComment = "",
            reviewTags = ""
        )
        repository.saveTrip(trip)

        // Verify it is returned as active initially
        val activeInitial = repository.getActiveTrip()
        assertNotNull(activeInitial)
        assertEquals("trip_test", activeInitial?.id)

        // 3. Perform rating
        repository.rateTrip(
            tripId = "trip_test",
            rating = 5,
            comment = "Great ride!",
            tags = "Safe Driving, Polite"
        )

        // 4. Verify trip is rated and no longer "active"
        val updatedTrip = repository.getTripById("trip_test")
        assertNotNull(updatedTrip)
        assertEquals(5, updatedTrip?.rating)
        assertEquals("Great ride!", updatedTrip?.reviewComment)
        assertEquals("Safe Driving, Polite", updatedTrip?.reviewTags)

        val activeAfter = repository.getActiveTrip()
        assertNull(activeAfter)
    }

    @Test
    fun testSavedPlacesCRUD() = runBlocking {
        var places = repository.allSavedPlacesFlow.first()
        assertEquals(0, places.size)

        val homePlace = SavedPlaceEntity(
            id = 1L,
            name = "Kairaba Avenue, Serrekunda",
            label = "Home",
            lat = 13.4471,
            lng = -16.6791,
            iconType = "HOME"
        )
        repository.saveSavedPlace(homePlace)

        places = repository.allSavedPlacesFlow.first()
        assertEquals(1, places.size)
        assertEquals("Home", places[0].label)
        assertEquals("Kairaba Avenue, Serrekunda", places[0].name)

        repository.deleteSavedPlaceById(1L)
        places = repository.allSavedPlacesFlow.first()
        assertEquals(0, places.size)
    }

    @Test
    fun testCancelActiveRide() = runBlocking {
        // Seed active ride
        val activeTrip = TripEntity(
            id = "trip_to_cancel",
            passengerName = "Alex Johnson",
            driverId = "drv_test",
            driverName = "Test Driver",
            vehicleType = "CAR",
            vehiclePlate = "BJL 1234 A",
            pickupName = "Airport",
            dropoffName = "Hotel",
            pickupLat = 13.44,
            pickupLng = -16.68,
            dropoffLat = 13.45,
            dropoffLng = -16.69,
            fareGmd = 250,
            paymentMethod = "WAVE",
            status = "REQUESTED",
            rating = 0,
            reviewComment = "",
            reviewTags = ""
        )
        repository.saveTrip(activeTrip)

        // Instantiate view model
        val viewModel = WayGoViewModel(repository)

        // Verify it's active
        val activeBefore = repository.getActiveTrip()
        assertNotNull(activeBefore)
        assertEquals("trip_to_cancel", activeBefore?.id)
        assertEquals("REQUESTED", activeBefore?.status)

        // Cancel the trip
        viewModel.cancelTripActive("trip_to_cancel")

        // Wait brief moment or assert directly as database operations in test are immediate
        val activeAfter = repository.getTripById("trip_to_cancel")
        assertNotNull(activeAfter)
        assertEquals("CANCELLED", activeAfter?.status)
    }

    @Test
    fun testDriverRegionalEarningsAndCalculations() = runBlocking {
        val driverId = "drv_test"
        
        // Seed Banjul trip
        val banjulTrip = TripEntity(
            id = "trip_banjul_1",
            passengerName = "Alex Johnson",
            driverId = driverId,
            driverName = "Test Driver",
            vehicleType = "CAR",
            vehiclePlate = "BJL 1234 A",
            pickupName = "Albert Market, Banjul",
            dropoffName = "Arch 22, Banjul",
            pickupLat = 13.4533,
            pickupLng = -16.5746,
            dropoffLat = 13.4580,
            dropoffLng = -16.5820,
            fareGmd = 200,
            paymentMethod = "WAVE",
            status = "COMPLETED",
            rating = 5,
            reviewComment = "Fast!",
            timestamp = System.currentTimeMillis()
        )
        repository.saveTrip(banjulTrip)

        // Seed Kanifing/Serrekunda trip
        val kanifingTrip = TripEntity(
            id = "trip_kanifing_1",
            passengerName = "Aisha Bah",
            driverId = driverId,
            driverName = "Test Driver",
            vehicleType = "CAR",
            vehiclePlate = "BJL 1234 A",
            pickupName = "Kairaba Avenue, Serrekunda",
            dropoffName = "University of Gambia, Kanifing",
            pickupLat = 13.4471,
            pickupLng = -16.6791,
            dropoffLat = 13.4452,
            dropoffLng = -16.6713,
            fareGmd = 350,
            paymentMethod = "CASH",
            status = "COMPLETED",
            rating = 4,
            reviewComment = "Great driving",
            timestamp = System.currentTimeMillis()
        )
        repository.saveTrip(kanifingTrip)

        // Calculate metrics
        val driverTrips = repository.allTripsFlow.first().filter { it.driverId == driverId && it.status == "COMPLETED" }
        assertEquals(2, driverTrips.size)

        // Helper region classifier matching the UI
        fun getTripRegionName(trip: TripEntity): String {
            val pickup = trip.pickupName.lowercase()
            val dropoff = trip.dropoffName.lowercase()
            return when {
                pickup.contains("banjul") || dropoff.contains("banjul") -> "BANJUL"
                pickup.contains("kanifing") || dropoff.contains("kanifing") ||
                pickup.contains("serrekunda") || dropoff.contains("serrekunda") ||
                pickup.contains("senegambia") || dropoff.contains("senegambia") ||
                pickup.contains("bakau") || dropoff.contains("bakau") ||
                pickup.contains("kololi") || dropoff.contains("kololi") ||
                pickup.contains("kairaba") || dropoff.contains("kairaba") ||
                pickup.contains("stadium") || dropoff.contains("stadium") ||
                pickup.contains("university") || dropoff.contains("university") -> "KANIFING"
                else -> "KANIFING"
            }
        }

        val banjulSub = driverTrips.filter { getTripRegionName(it) == "BANJUL" }
        val kanifingSub = driverTrips.filter { getTripRegionName(it) == "KANIFING" }

        assertEquals(1, banjulSub.size)
        assertEquals(200, banjulSub.sumOf { it.fareGmd })

        assertEquals(1, kanifingSub.size)
        assertEquals(350, kanifingSub.sumOf { it.fareGmd })

        val totalGross = driverTrips.sumOf { it.fareGmd }
        assertEquals(550, totalGross)

        val commission = (totalGross * 0.15).toInt()
        assertEquals(82, commission) // (550 * 0.15).toInt() = 82

        val netEarnings = totalGross - commission
        assertEquals(468, netEarnings)
    }

    @Test
    fun testDigitalTippingAndDriverWallet() = runBlocking {
        val driverId = "drv_test_tipping"
        
        // Seed driver
        val driver = DriverEntity(
            id = driverId,
            name = "Professional Driver",
            phone = "+220 555 1234",
            vehicleType = "CAR",
            vehiclePlate = "BJL 9999 B",
            rating = 4.8f,
            approvalStatus = "APPROVED",
            isOnline = true,
            currentLat = 13.4471,
            currentLng = -16.6791,
            driverLicense = "DL-2026-TIPPING"
        )
        repository.saveDriver(driver)

        // Seed uncompleted/unrated trip
        val trip = TripEntity(
            id = "trip_tipping_1",
            passengerName = "Aisha",
            driverId = driverId,
            driverName = "Professional Driver",
            vehicleType = "CAR",
            vehiclePlate = "BJL 9999 B",
            pickupName = "Albert Market, Banjul",
            dropoffName = "Kairaba Avenue, Serrekunda",
            pickupLat = 13.4533,
            pickupLng = -16.5746,
            dropoffLat = 13.4471,
            dropoffLng = -16.6791,
            fareGmd = 400,
            paymentMethod = "WAVE",
            status = "COMPLETED",
            rating = 0,
            reviewComment = "",
            reviewTags = "",
            timestamp = System.currentTimeMillis()
        )
        repository.saveTrip(trip)

        // Perform rating WITH a digital tip of 50 GMD
        repository.rateTrip(
            tripId = "trip_tipping_1",
            rating = 5,
            comment = "Exceptional ride, left a generous tip!",
            tags = "Clean Vehicle, Safe Driving",
            tipGmd = 50
        )

        // Verify trip was rated and tipped in DB
        val updatedTrip = repository.getTripById("trip_tipping_1")
        assertNotNull(updatedTrip)
        assertEquals(5, updatedTrip?.rating)
        assertEquals(50, updatedTrip?.tipGmd)
        assertEquals("Exceptional ride, left a generous tip!", updatedTrip?.reviewComment)

        // Calculate driver's wallet with tip factored in (100% of tip goes to driver)
        val driverCompletedTrips = repository.allTripsFlow.first().filter { it.driverId == driverId && it.status == "COMPLETED" }
        assertEquals(1, driverCompletedTrips.size)

        val totalFaresAllTime = driverCompletedTrips.sumOf { it.fareGmd }
        val totalTipsAllTime = driverCompletedTrips.sumOf { it.tipGmd }
        assertEquals(400, totalFaresAllTime)
        assertEquals(50, totalTipsAllTime)

        // Platform takes 15% commission ONLY on the base fare (not on the tip)
        val totalCommissionAllTime = (totalFaresAllTime * 0.15).toInt()
        assertEquals(60, totalCommissionAllTime)

        val totalNetAllTime = (totalFaresAllTime - totalCommissionAllTime) + totalTipsAllTime
        assertEquals(390, totalNetAllTime) // (400 - 60) + 50 = 390
    }

    @Test
    fun testNearestDriverMatchingLogic() = runBlocking {
        val viewModel = WayGoViewModel(repository)

        // Case 1: No drivers seeded. Verify we spawn a simulated fallback matching driver of the correct vehicleType
        val pickupLat = 13.4471
        val pickupLng = -16.6791
        val matched1 = viewModel.findNearestMatchingDriver(pickupLat, pickupLng, "TRICYCLE")

        assertNotNull(matched1)
        assertEquals("TRICYCLE", matched1.vehicleType)
        assertEquals("APPROVED", matched1.approvalStatus)
        assertEquals(true, matched1.isOnline)

        // Case 2: Seed active matching drivers, verify we find the nearest one
        val farDriver = DriverEntity(
            id = "drv_far",
            name = "Far Driver",
            phone = "+220 771 0001",
            vehicleType = "CAR",
            vehiclePlate = "BJL 7000 A",
            rating = 4.8f,
            approvalStatus = "APPROVED",
            isOnline = true,
            currentLat = 13.5000, // far away
            currentLng = -16.6700,
            driverLicense = "DL-2026-FAR"
        )
        val closeDriver = DriverEntity(
            id = "drv_close",
            name = "Close Driver",
            phone = "+220 771 0002",
            vehicleType = "CAR",
            vehiclePlate = "BJL 8000 B",
            rating = 4.9f,
            approvalStatus = "APPROVED",
            isOnline = true,
            currentLat = 13.4480, // very close to 13.4471
            currentLng = -16.6795, // very close to -16.6791
            driverLicense = "DL-2026-CLOSE"
        )
        val offlineDriver = DriverEntity(
            id = "drv_offline",
            name = "Offline Driver",
            phone = "+220 771 0003",
            vehicleType = "CAR",
            vehiclePlate = "BJL 9000 C",
            rating = 4.5f,
            approvalStatus = "APPROVED",
            isOnline = false, // offline, should be ignored
            currentLat = 13.4472,
            currentLng = -16.6792,
            driverLicense = "DL-2026-OFFLINE"
        )

        repository.saveDriver(farDriver)
        repository.saveDriver(closeDriver)
        repository.saveDriver(offlineDriver)

        // Match for CAR
        val matchedCar = viewModel.findNearestMatchingDriver(pickupLat, pickupLng, "CAR")
        assertNotNull(matchedCar)
        assertEquals("drv_close", matchedCar.id)
        assertEquals("Close Driver", matchedCar.name)
    }

    @Test
    fun testSignInWithEmailValidationAndSuccess() {
        var successCalled = false
        var errorMessage: String? = null

        // Test 1: Blank email validation
        FirebaseAuthManager.signInWithEmail("", "pass123", {
            successCalled = true
        }, { error ->
            errorMessage = error
        })
        assertEquals(false, successCalled)
        assertEquals("Please enter a valid email address.", errorMessage)

        // Test 2: Short password validation
        successCalled = false
        errorMessage = null
        FirebaseAuthManager.signInWithEmail("user@waygo.com", "123", {
            successCalled = true
        }, { error ->
            errorMessage = error
        })
        assertEquals(false, successCalled)
        assertEquals("Password must be at least 4 characters long.", errorMessage)

        // Test 3: Successful sign in with registered user
        successCalled = false
        errorMessage = null
        FirebaseAuthManager.signInWithEmail("test@waygo.com", "pass123", {
            successCalled = true
        }, { error ->
            errorMessage = error
        })
        assertEquals(true, successCalled)
        assertNull(errorMessage)
    }

    @Test
    fun testGoogleAccountAuthValidationAndRegistration() {
        var errorResult: String? = null
        var successResult = false

        val viewModel = WayGoViewModel(repository)

        // Test invalid email validation
        viewModel.loginOrRegisterPassengerWithGoogle(
            googleEmail = "invalidemail",
            googleName = "Test User",
            pass = "pass123",
            isRegisterMode = true,
            onSuccess = { successResult = true },
            onError = { err -> errorResult = err }
        )
        assertEquals(false, successResult)
        assertEquals("Please enter a valid Google email address.", errorResult)

        // Test valid registration
        errorResult = null
        successResult = false
        viewModel.loginOrRegisterPassengerWithGoogle(
            googleEmail = "alex.johnson@gmail.com",
            googleName = "Alex Johnson",
            pass = "googlepass123",
            isRegisterMode = true,
            onSuccess = { successResult = true },
            onError = { err -> errorResult = err }
        )
        assertNull(errorResult)
    }

    @Test
    fun testSignOutFlow() {
        val viewModel = WayGoViewModel(repository)
        viewModel.verifyOtp("1234") // Log in passenger
        assertEquals(true, viewModel.isUserLoggedIn.value)

        // Perform sign out
        viewModel.logout()
        assertEquals(false, viewModel.isUserLoggedIn.value)
    }

    @Test
    fun testRejectWrongPasswordWhenMfaDisabled() {
        var successCalled = false
        var errorResult: String? = null

        // Register user with specific password
        FirebaseAuthManager.createUserWithEmail("testuser@waygo.com", "correctPass99", {
            successCalled = true
        }, { err ->
            errorResult = err
        })
        assertEquals(true, successCalled)

        // Attempt sign-in with wrong password
        successCalled = false
        errorResult = null
        FirebaseAuthManager.signInWithEmail("testuser@waygo.com", "wrongPassword", {
            successCalled = true
        }, { err ->
            errorResult = err
        })
        assertEquals(false, successCalled)
        assertNotNull(errorResult)
        assertEquals("Incorrect password for testuser@waygo.com. Please check your password and try again.", errorResult)

        // Attempt sign-in with correct password
        successCalled = false
        errorResult = null
        FirebaseAuthManager.signInWithEmail("testuser@waygo.com", "correctPass99", {
            successCalled = true
        }, { err ->
            errorResult = err
        })
        assertEquals(true, successCalled)
        assertNull(errorResult)
    }

    @Test
    fun testGoogleDirectAuthWithoutMfa() {
        var registered = false
        var signedIn = false
        var errorResult: String? = null

        FirebaseAuthManager.createUserWithEmail(
            email = "davidotu@mixxd.org",
            pass = "davidSecret123",
            onSuccess = { registered = true },
            onError = { err -> errorResult = err }
        )
        assertEquals(true, registered)
        assertNull(errorResult)

        FirebaseAuthManager.signInWithEmail(
            email = "davidotu@mixxd.org",
            pass = "davidSecret123",
            onSuccess = { signedIn = true },
            onError = { err -> errorResult = err }
        )
        assertEquals(true, signedIn)
        assertNull(errorResult)
    }
}
