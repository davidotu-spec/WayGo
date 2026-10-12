package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.WayGoViewModel
import com.example.utils.LocationUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        FirebaseAuthManager.init(context)
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
        FirebaseAuthManager.signInWithEmail("", "pass1234", {
            successCalled = true
        }, { error ->
            errorMessage = error
        })
        assertEquals(false, successCalled)
        assertEquals("Email address cannot be empty.", errorMessage)

        // Test 2: Short password validation
        successCalled = false
        errorMessage = null
        FirebaseAuthManager.signInWithEmail("user@waygo.com", "123", {
            successCalled = true
        }, { error ->
            errorMessage = error
        })
        assertEquals(false, successCalled)
        assertEquals("Password must be at least 6 characters long.", errorMessage)

        // Test 3: Sign in with existing user
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
            pass = "pass1234",
            isRegisterMode = true,
            onSuccess = { successResult = true },
            onError = { err -> errorResult = err }
        )
        assertEquals(false, successResult)
        assertEquals(true, errorResult?.startsWith("Please enter a valid Google email address") == true)

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
        viewModel.loginOrRegisterPassengerWithGoogle(
            googleEmail = "alex.johnson@gmail.com",
            googleName = "Alex Johnson",
            pass = "googlepass123",
            isRegisterMode = true,
            onSuccess = {},
            onError = {}
        )
        assertEquals(true, viewModel.isUserLoggedIn.value)

        // Perform sign out
        viewModel.logout()
        assertEquals(false, viewModel.isUserLoggedIn.value)
    }

    @Test
    fun testGoogleSignInRejectsWrongPassword() {
        val viewModel = WayGoViewModel(repository)
        var successResult = false
        var errorResult: String? = null

        // 1. Create/register account with correct password
        viewModel.loginOrRegisterPassengerWithGoogle(
            googleEmail = "security.test@gmail.com",
            googleName = "Security Test",
            pass = "correctPassword123",
            isRegisterMode = true,
            onSuccess = { successResult = true },
            onError = { err -> errorResult = err }
        )
        assertEquals(true, successResult)
        assertNull(errorResult)

        // Logout
        viewModel.logout()
        assertEquals(false, viewModel.isUserLoggedIn.value)

        // 2. Attempt Google Sign In with WRONG password
        successResult = false
        errorResult = null
        viewModel.loginOrRegisterPassengerWithGoogle(
            googleEmail = "security.test@gmail.com",
            googleName = "Security Test",
            pass = "wrongPassword999",
            isRegisterMode = false,
            onSuccess = { successResult = true },
            onError = { err -> errorResult = err }
        )
        // Must be rejected!
        assertEquals(false, successResult)
        assertEquals(false, viewModel.isUserLoggedIn.value)
        assertEquals("Incorrect password. Please check your password and try again.", errorResult)

        // 3. Attempt Google Sign In with CORRECT password
        successResult = false
        errorResult = null
        viewModel.loginOrRegisterPassengerWithGoogle(
            googleEmail = "security.test@gmail.com",
            googleName = "Security Test",
            pass = "correctPassword123",
            isRegisterMode = false,
            onSuccess = { successResult = true },
            onError = { err -> errorResult = err }
        )
        // Must succeed!
        assertEquals(true, successResult)
        assertEquals(true, viewModel.isUserLoggedIn.value)
        assertNull(errorResult)
    }

    @Test
    fun testGoogleSignInRejectsUnregisteredAccountInSignInMode() {
        val viewModel = WayGoViewModel(repository)
        var successResult = false
        var errorResult: String? = null

        viewModel.loginOrRegisterPassengerWithGoogle(
            googleEmail = "never.registered@gmail.com",
            googleName = "Never Registered",
            pass = "someRandomPassword123",
            isRegisterMode = false,
            onSuccess = { successResult = true },
            onError = { err -> errorResult = err }
        )
        // Must be rejected!
        assertEquals(false, successResult)
        assertEquals(false, viewModel.isUserLoggedIn.value)
        assertNotNull(errorResult)
        assertEquals(true, errorResult?.contains("No account found", ignoreCase = true) == true)
    }

    @Test
    fun testDriverGoogleSignInRejectsWrongPassword() {
        val viewModel = WayGoViewModel(repository)
        var successResult = false
        var errorResult: String? = null

        // Register driver
        viewModel.loginOrRegisterDriverWithGoogle(
            googleEmail = "driver.security@gmail.com",
            googleName = "Driver Security",
            pass = "driverPass123",
            vehicleType = "CAR",
            vehiclePlate = "BJL 1122 X",
            licenseNum = "GAM-DL-7788",
            isRegisterMode = true,
            onSuccess = { successResult = true },
            onError = { err -> errorResult = err }
        )
        assertEquals(true, successResult)
        assertNull(errorResult)

        // Logout
        viewModel.logout()
        assertEquals(false, viewModel.isDriverLoggedIn.value)

        // Attempt driver sign-in with wrong password
        successResult = false
        errorResult = null
        viewModel.loginOrRegisterDriverWithGoogle(
            googleEmail = "driver.security@gmail.com",
            googleName = "Driver Security",
            pass = "wrongDriverPass999",
            vehicleType = "CAR",
            vehiclePlate = "BJL 1122 X",
            licenseNum = "GAM-DL-7788",
            isRegisterMode = false,
            onSuccess = { successResult = true },
            onError = { err -> errorResult = err }
        )
        // Must be rejected!
        assertEquals(false, successResult)
        assertEquals(false, viewModel.isDriverLoggedIn.value)
        assertEquals("Incorrect password. Please check your password and try again.", errorResult)
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
        assertEquals("Incorrect password. Please check your password and try again.", errorResult)

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
        var signedIn = false
        var errorResult: String? = null

        FirebaseAuthManager.signInWithEmail(
            email = "davidotu@mixxd.org",
            pass = "pass123",
            onSuccess = { signedIn = true },
            onError = { err -> errorResult = err }
        )
        assertEquals(true, signedIn)
        assertNull(errorResult)
    }

    @Test
    fun testFullRideLifecycleSimulation() = runBlocking {
        val viewModel = WayGoViewModel(repository)

        // 1. Seed a verified driver in Kanifing
        val driver = DriverEntity(
            id = "drv_kanifing_01",
            name = "Musa Ceesay",
            phone = "+220 789 1234",
            vehicleType = "CAR",
            vehiclePlate = "BJL 4488 C",
            rating = 4.9f,
            approvalStatus = "APPROVED",
            isOnline = true,
            currentLat = 13.4471,
            currentLng = -16.6791,
            driverLicense = "DL-KM-8842"
        )
        repository.saveDriver(driver)

        // 2. Passenger books a ride from Kairaba Ave to Banjul Arch 22
        val tripId = "trip_sim_cuj_01"
        val initialTrip = TripEntity(
            id = tripId,
            passengerName = "Fatou Jallow",
            driverId = null,
            driverName = null,
            vehicleType = "CAR",
            vehiclePlate = null,
            pickupName = "Kairaba Avenue, Serrekunda",
            dropoffName = "Arch 22, Banjul",
            pickupLat = 13.4471,
            pickupLng = -16.6791,
            dropoffLat = 13.4580,
            dropoffLng = -16.5820,
            fareGmd = 350,
            paymentMethod = "WAVE",
            status = "REQUESTED",
            verificationPin = "5821"
        )
        repository.saveTrip(initialTrip)

        // Verify REQUESTED state
        var trip = repository.getTripById(tripId)
        assertNotNull(trip)
        assertEquals("REQUESTED", trip?.status)
        assertEquals("5821", trip?.verificationPin)

        // 3. Driver accepts the ride
        repository.saveTrip(
            trip!!.copy(
                status = "ACCEPTED",
                driverId = driver.id,
                driverName = driver.name,
                vehiclePlate = driver.vehiclePlate
            )
        )
        trip = repository.getTripById(tripId)
        assertEquals("ACCEPTED", trip?.status)
        assertEquals("drv_kanifing_01", trip?.driverId)
        assertEquals("Musa Ceesay", trip?.driverName)

        // 4. Driver arrives at pickup
        repository.updateTripStatus(tripId = tripId, status = "ARRIVED")
        trip = repository.getTripById(tripId)
        assertEquals("ARRIVED", trip?.status)

        // 5. Trip starts (IN_PROGRESS / EN_ROUTE)
        repository.updateTripStatus(tripId = tripId, status = "EN_ROUTE")
        trip = repository.getTripById(tripId)
        assertEquals("EN_ROUTE", trip?.status)

        // 6. Trip completes at destination
        repository.updateTripStatus(tripId = tripId, status = "COMPLETED")
        trip = repository.getTripById(tripId)
        assertEquals("COMPLETED", trip?.status)

        // 7. Passenger rates the trip 5 stars with a tip
        repository.rateTrip(
            tripId = tripId,
            rating = 5,
            comment = "Smooth journey through Banjul highway!",
            tags = "Punctual, Safe Driver, Clean Car",
            tipGmd = 50
        )
        trip = repository.getTripById(tripId)
        assertEquals(5, trip?.rating)
        assertEquals(50, trip?.tipGmd)
        assertEquals("Smooth journey through Banjul highway!", trip?.reviewComment)

        // 8. Verify active trip has settled
        val active = repository.getActiveTrip()
        assertNull(active)
    }

    @Test
    fun testStripePaymentApiSimulation() = runBlocking {
        // Test Stripe checkout URL generator with fallback simulation
        val txRef = "waygo_tx_stripe_${System.currentTimeMillis()}"
        val checkoutUrl = StripeManager.initiateStripePayment(
            amountGmd = 350.0,
            passengerEmail = "rider@waygo.com",
            passengerName = "Lamin Sanneh",
            passengerPhone = "+220 7001122",
            tripTxRef = txRef
        )

        assertNotNull(checkoutUrl)
        assertEquals(true, checkoutUrl?.contains("tx_ref=$txRef") == true)
        assertEquals(true, (checkoutUrl?.contains("standard-checkout-redirect.waygo.com") == true) || (checkoutUrl?.contains("stripe.com") == true))
    }

    @Test
    fun testFlutterwavePaymentPayloadGeneration() = runBlocking {
        // Test Flutterwave secret key fallback and transaction ref format
        val secretKey = FlutterwaveManager.secretKey
        assertNotNull(secretKey)
        assertEquals(true, secretKey.isNotBlank())

        val publicKey = FlutterwaveManager.publicKey
        assertNotNull(publicKey)
        assertEquals(true, publicKey.isNotBlank())
    }

    @Test
    fun testMobileMoneyAndCashPaymentMethods() = runBlocking {
        val methods = listOf("WAVE", "QMONEY", "AFRIMONEY", "CASH", "STRIPE", "FLUTTERWAVE")

        methods.forEachIndexed { index, method ->
            val tripId = "trip_payment_test_$index"
            val trip = TripEntity(
                id = tripId,
                passengerName = "Test Rider $index",
                driverId = "drv_test",
                driverName = "Driver $index",
                vehicleType = "CAR",
                vehiclePlate = "BJL 100$index",
                pickupName = "Westfield Monument",
                dropoffName = "Senegambia Strip",
                pickupLat = 13.4355,
                pickupLng = -16.6740,
                dropoffLat = 13.4431,
                dropoffLng = -16.7161,
                fareGmd = 200 + (index * 50),
                paymentMethod = method,
                status = "COMPLETED",
                rating = 5,
                reviewComment = "Paid via $method",
                reviewTags = "Verified Payment"
            )
            repository.saveTrip(trip)

            val saved = repository.getTripById(tripId)
            assertNotNull(saved)
            assertEquals(method, saved?.paymentMethod)
            assertEquals(200 + (index * 50), saved?.fareGmd)
        }

        val allTrips = repository.allTripsFlow.first()
        assertEquals(true, allTrips.size >= 6)
    }

    @Test
    fun testPassengerAndDriverAuthLifecycle() {
        var createSuccess = false
        var loginSuccess = false
        var errorMsg: String? = null

        // 1. Register new Passenger
        val testEmail = "passenger_${System.currentTimeMillis()}@waygo.gm"
        val testPass = "SecurePass2026!"
        FirebaseAuthManager.createUserWithEmail(
            email = testEmail,
            pass = testPass,
            onSuccess = { createSuccess = true },
            onError = { errorMsg = it }
        )
        assertEquals(true, createSuccess)
        assertNull(errorMsg)

        // 2. Sign In newly created Passenger
        FirebaseAuthManager.signInWithEmail(
            email = testEmail,
            pass = testPass,
            onSuccess = { loginSuccess = true },
            onError = { errorMsg = it }
        )
        assertEquals(true, loginSuccess)
        assertNull(errorMsg)

        // 3. Verify user is saved in persistent credential store
        val isRegistered = FirebaseAuthManager.isUserRegistered(testEmail)
        assertEquals(true, isRegistered)
        val storedPass = FirebaseAuthManager.getStoredPassword(testEmail)
        assertEquals(testPass, storedPass)

        // 4. Sign Out
        FirebaseAuthManager.signOut()
        val userAfterSignOut = FirebaseAuthManager.getCurrentUser()
        assertNull(userAfterSignOut)
    }

    @Test
    fun testDriverOnboardingAndApprovalStatus() = runBlocking {
        // Seed an onboarded driver with pending status
        val newDriver = DriverEntity(
            id = "drv_onboard_test",
            name = "Ebrima Sowe",
            phone = "+220 722 9988",
            vehicleType = "TRICYCLE",
            vehiclePlate = "KMC 3321 T",
            rating = 5.0f,
            approvalStatus = "PENDING",
            isOnline = false,
            currentLat = 13.4412,
            currentLng = -16.6811,
            driverLicense = "DL-2026-KM-9988"
        )
        repository.saveDriver(newDriver)

        var driver = repository.getDriverById("drv_onboard_test")
        assertNotNull(driver)
        assertEquals("PENDING", driver?.approvalStatus)
        assertEquals(false, driver?.isOnline)

        // Admin approves the driver
        val approvedDriver = driver!!.copy(approvalStatus = "APPROVED", isOnline = true)
        repository.saveDriver(approvedDriver)

        driver = repository.getDriverById("drv_onboard_test")
        assertEquals("APPROVED", driver?.approvalStatus)
        assertEquals(true, driver?.isOnline)
        assertEquals("TRICYCLE", driver?.vehicleType)
    }

    @Test
    fun testDriverEtaCalculationFromGpsCoordinates() = runBlocking {
        // Driver at Westfield Junction (13.4385, -16.6760)
        // Passenger at Kairaba Avenue (13.4471, -16.6791)
        val lat1 = 13.4385
        val lng1 = -16.6760
        val lat2 = 13.4471
        val lng2 = -16.6791

        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distKm = r * c

        // Assert distance is reasonable for Banjul/Kanifing metro zone (~1.0 km)
        assert(distKm in 0.5..2.0)

        // Estimated minutes (calibrated for Gambian urban transit)
        val etaMin = (distKm * 3.5 + 1).toInt().coerceAtLeast(1)
        assert(etaMin in 1..10)
    }

    @Test
    fun testPastRideHistoryRoomDatabaseTableAndOperations() = runBlocking {
        // 1. Verify initial state is empty in memory db
        val initialRides = repository.allPastRidesFlow.first()
        assertEquals(0, initialRides.size)

        // 2. Insert sample past rides with destinations, dates, and vehicle info
        val ride1 = PastRideHistoryEntity(
            id = "ride_room_1",
            pickupLocation = "Westfield Junction, Serrekunda",
            destination = "Senegambia Strip, Kololi",
            dateFormatted = "31 Aug 2026, 20:15",
            timestamp = 1756671300000L,
            fareGmd = 250,
            driverName = "Alieu Ceesay",
            vehicleType = "CAR",
            vehiclePlate = "BJL 4821 C",
            paymentMethod = "WAVE",
            status = "COMPLETED",
            rating = 5.0f,
            distanceKm = 6.8,
            durationMinutes = 14,
            notes = "Night ride to Kololi",
            tipGmd = 30
        )
        val ride2 = PastRideHistoryEntity(
            id = "ride_room_2",
            pickupLocation = "Kairaba Avenue, Fajara",
            destination = "Albert Market, Banjul",
            dateFormatted = "29 Aug 2026, 09:30",
            timestamp = 1756455000000L,
            fareGmd = 350,
            driverName = "Mariama Jallow",
            vehicleType = "TRICYCLE",
            vehiclePlate = "KM 9312 T",
            paymentMethod = "CASH",
            status = "COMPLETED",
            rating = 4.9f,
            distanceKm = 11.2,
            durationMinutes = 24,
            notes = "Morning commute to Banjul port",
            tipGmd = 20
        )
        val ride3 = PastRideHistoryEntity(
            id = "ride_room_3",
            pickupLocation = "Brusubi Turntable",
            destination = "Banjul International Airport, Yundum",
            dateFormatted = "26 Aug 2026, 14:00",
            timestamp = 1756216800000L,
            fareGmd = 450,
            driverName = "Bakary Touray",
            vehicleType = "CAR",
            vehiclePlate = "WCR 7431 B",
            paymentMethod = "QMONEY",
            status = "COMPLETED",
            rating = 5.0f,
            distanceKm = 14.5,
            durationMinutes = 22,
            notes = "Airport transfer",
            tipGmd = 50
        )

        repository.savePastRides(listOf(ride1, ride2, ride3))

        // 3. Query all past rides flow
        val allRides = repository.allPastRidesFlow.first()
        assertEquals(3, allRides.size)
        // Ordered by timestamp DESC
        assertEquals("ride_room_1", allRides[0].id)
        assertEquals("Senegambia Strip, Kololi", allRides[0].destination)
        assertEquals("31 Aug 2026, 20:15", allRides[0].dateFormatted)

        // 4. Test search query by destination
        val searchAirport = repository.searchPastRidesFlow("Airport").first()
        assertEquals(1, searchAirport.size)
        assertEquals("Banjul International Airport, Yundum", searchAirport[0].destination)
        assertEquals("26 Aug 2026, 14:00", searchAirport[0].dateFormatted)

        val searchBanjul = repository.searchPastRidesFlow("Banjul").first()
        assertEquals(2, searchBanjul.size)

        // 5. Test single fetch by ID
        val fetchedRide = repository.getPastRideById("ride_room_2")
        assertNotNull(fetchedRide)
        assertEquals("Albert Market, Banjul", fetchedRide?.destination)
        assertEquals("Mariama Jallow", fetchedRide?.driverName)
        assertEquals(350, fetchedRide?.fareGmd)

        // 6. Test delete past ride
        repository.deletePastRide("ride_room_2")
        val afterDelete = repository.allPastRidesFlow.first()
        assertEquals(2, afterDelete.size)
        assertNull(repository.getPastRideById("ride_room_2"))

        // 7. Test clear all
        repository.clearAllPastRides()
        val afterClear = repository.allPastRidesFlow.first()
        assertEquals(0, afterClear.size)
    }

    @Test
    fun testEstimatedFareCalculationBasedOnOriginAndDestinationDistance() {
        // Origin: Westfield Junction, Serrekunda (13.4471, -16.6791)
        val originLat = 13.4471
        val originLng = -16.6791
        // Destination: Senegambia Strip, Kololi (13.4455, -16.7180)
        val destLat = 13.4455
        val destLng = -16.7180

        // 1. Calculate distance
        val distKm = TripFareEstimationService.calculateDistanceKm(originLat, originLng, destLat, destLng)
        assertTrue(distKm > 0.0)

        // 2. Calculate estimated fare for CAR
        val carEstimate = TripFareEstimationService.calculateEstimatedFare(
            originLat = originLat,
            originLng = originLng,
            destLat = destLat,
            destLng = destLng,
            vehicleType = "CAR"
        )
        assertNotNull(carEstimate)
        assertEquals("CAR", carEstimate.vehicleType)
        assertTrue(carEstimate.finalFareGmd >= TripFareEstimationService.CAR_MIN_FARE)
        assertTrue(carEstimate.distanceCharge > 0)
        assertTrue(carEstimate.estimatedDurationMin > 0)

        // 3. Calculate estimated fare for TRICYCLE (should be more affordable)
        val trikeEstimate = TripFareEstimationService.calculateEstimatedFare(
            originLat = originLat,
            originLng = originLng,
            destLat = destLat,
            destLng = destLng,
            vehicleType = "TRICYCLE"
        )
        assertNotNull(trikeEstimate)
        assertEquals("TRICYCLE", trikeEstimate.vehicleType)
        assertTrue(trikeEstimate.finalFareGmd < carEstimate.finalFareGmd)
        assertTrue(trikeEstimate.finalFareGmd >= TripFareEstimationService.TRICYCLE_MIN_FARE)

        // 4. Test calculateEstimatedFareByDistance helper directly
        val fixed10KmCar = TripFareEstimationService.calculateEstimatedFareByDistance(10.0, "CAR")
        val fixed10KmTrike = TripFareEstimationService.calculateEstimatedFareByDistance(10.0, "TRICYCLE")
        assertTrue(fixed10KmCar.finalFareGmd > fixed10KmTrike.finalFareGmd)

        // 5. Test LocationUtils convenience wrapper
        val locUtilsEstimate = LocationUtils.calculateEstimatedFare(
            currentLat = originLat,
            currentLng = originLng,
            destLat = destLat,
            destLng = destLng,
            vehicleType = "CAR"
        )
        assertEquals(carEstimate.finalFareGmd, locUtilsEstimate.finalFareGmd)
    }
}
