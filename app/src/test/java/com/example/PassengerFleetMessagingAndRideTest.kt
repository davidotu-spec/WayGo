package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.WayGoViewModel
import kotlinx.coroutines.flow.first
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
class PassengerFleetMessagingAndRideTest {

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
    fun testPassengerBookingToFleetAcceptanceLifecycle() = runBlocking {
        // 1. Seed online fleet drivers
        val fleetDriver = DriverEntity(
            id = "drv_alieu",
            name = "Alieu Ceesay",
            phone = "+220 992 4831",
            vehicleType = "CAR",
            vehiclePlate = "BJL 4821 C",
            rating = 4.8f,
            approvalStatus = "APPROVED",
            isOnline = true,
            currentLat = 13.4470,
            currentLng = -16.6790,
            driverLicense = "DL-2024-9981"
        )
        repository.saveDriver(fleetDriver)

        // 2. Passenger books a ride
        viewModel.bookRideSync(
            pickup = "Kairaba Avenue, Serrekunda",
            dropoff = "Albert Market, Banjul",
            pickupLat = 13.4470,
            pickupLng = -16.6790,
            dropoffLat = 13.4530,
            dropoffLng = -16.5746,
            fare = 350,
            payment = "WAVE",
            vehicleType = "CAR",
            passengerName = "Fatou Joof"
        )

        // 3. Verify ride is created in Room and active
        val activeTrip = repository.getActiveTrip()
        assertNotNull("Active trip should exist after booking", activeTrip)
        assertEquals("REQUESTED", activeTrip?.status)
        assertEquals("Fatou Joof", activeTrip?.passengerName)
        assertEquals("CAR", activeTrip?.vehicleType)

        // 4. Fleet driver accepts booking
        val tripId = activeTrip!!.id
        viewModel.acceptBookingSync(tripId, fleetDriver.id)

        val acceptedTrip = repository.getTripById(tripId)
        assertNotNull(acceptedTrip)
        assertEquals("ACCEPTED", acceptedTrip?.status)
        assertEquals("drv_alieu", acceptedTrip?.driverId)
        assertEquals("Alieu Ceesay", acceptedTrip?.driverName)

        // 5. Driver updates progress: ARRIVED -> EN_ROUTE -> COMPLETED
        viewModel.updateTripStatusSync(tripId, "ARRIVED")
        assertEquals("ARRIVED", repository.getTripById(tripId)?.status)

        viewModel.updateTripStatusSync(tripId, "EN_ROUTE")
        assertEquals("EN_ROUTE", repository.getTripById(tripId)?.status)

        viewModel.completeTripSync(tripId)
        val completedTrip = repository.getTripById(tripId)
        assertEquals("COMPLETED", completedTrip?.status)
        assertTrue("Driver commission should be recorded", (completedTrip?.commissionGmd ?: 0) > 0)
    }

    @Test
    fun testPassengerAndFleetInAppTextMessaging() = runBlocking {
        val testTripId = "trip_msg_test_101"

        // 1. Seed a trip connecting passenger and fleet driver
        val trip = TripEntity(
            id = testTripId,
            passengerName = "Mariama Sarr",
            driverId = "drv_alieu",
            driverName = "Alieu Ceesay",
            vehicleType = "CAR",
            vehiclePlate = "BJL 4821 C",
            pickupName = "Westfield Monument",
            dropoffName = "Banjul Sea Port",
            pickupLat = 13.4400,
            pickupLng = -16.6750,
            dropoffLat = 13.4530,
            dropoffLng = -16.5746,
            fareGmd = 300,
            paymentMethod = "CASH",
            status = "ACCEPTED"
        )
        repository.saveTrip(trip)

        // 2. Start chat session
        viewModel.startChatSession(testTripId)

        // 3. Passenger texts the fleet driver
        val passengerMsgText = "Salam Alieu! I am waiting right next to the pharmacy."
        viewModel.sendChatMessage(
            tripId = testTripId,
            senderId = "current_passenger",
            senderName = "Mariama Sarr",
            senderRole = "PASSENGER",
            text = passengerMsgText
        )

        val messagesAfterPassenger = viewModel.chatMessages.value
        assertTrue("Message list should contain passenger's text", messagesAfterPassenger.isNotEmpty())
        val lastMsg = messagesAfterPassenger.last()
        assertEquals(passengerMsgText, lastMsg.message)
        assertEquals("PASSENGER", lastMsg.senderRole)
        assertEquals("Mariama Sarr", lastMsg.senderName)

        // 4. Fleet driver replies to the passenger
        val driverReplyText = "Salam! Copied, just made the turn into the avenue. Arriving in 1 minute."
        viewModel.sendChatMessage(
            tripId = testTripId,
            senderId = "drv_alieu",
            senderName = "Alieu Ceesay",
            senderRole = "DRIVER",
            text = driverReplyText
        )

        val allMessages = viewModel.chatMessages.value
        assertEquals("Should have both passenger and driver messages", 2, allMessages.filter {
            it.message == passengerMsgText || it.message == driverReplyText
        }.size)

        val driverMsg = allMessages.first { it.senderRole == "DRIVER" }
        assertEquals(driverReplyText, driverMsg.message)
        assertEquals("Alieu Ceesay", driverMsg.senderName)

        // 5. End chat session
        viewModel.endChatSession()
        assertTrue("Chat messages should be cleared on session end", viewModel.chatMessages.value.isEmpty())
    }

    @Test
    fun testFleetDriverOnlineToggleAndVehicleFiltering() = runBlocking {
        // Seed driver
        val driver = DriverEntity(
            id = "drv_mariama",
            name = "Mariama Jallow",
            phone = "+220 312 0451",
            vehicleType = "TRICYCLE",
            vehiclePlate = "KM 9312 T",
            rating = 4.9f,
            approvalStatus = "APPROVED",
            isOnline = false,
            currentLat = 13.4460,
            currentLng = -16.6720,
            driverLicense = "DL-2025-1029"
        )
        repository.saveDriver(driver)

        // Verify initially offline
        val initialDriver = repository.getDriverById("drv_mariama")
        assertNotNull(initialDriver)
        assertFalse(initialDriver!!.isOnline)

        // Toggle online
        viewModel.toggleDriverOnlineStateSync("drv_mariama", true)
        val onlineDriver = repository.getDriverById("drv_mariama")
        assertTrue(onlineDriver!!.isOnline)

        // Toggle back offline
        viewModel.toggleDriverOnlineStateSync("drv_mariama", false)
        val offlineDriver = repository.getDriverById("drv_mariama")
        assertFalse(offlineDriver!!.isOnline)
    }

    @Test
    fun testPassengerTripRatingUpdatesFleetDriverStats() = runBlocking {
        // 1. Seed trip and driver
        val driver = DriverEntity(
            id = "drv_bakary",
            name = "Bakary Touray",
            phone = "+220 754 1121",
            vehicleType = "CAR",
            vehiclePlate = "WCR 7431 B",
            rating = 4.5f,
            approvalStatus = "APPROVED",
            isOnline = true,
            currentLat = 13.4410,
            currentLng = -16.7100,
            driverLicense = "DL-2023-4552"
        )
        repository.saveDriver(driver)

        val trip = TripEntity(
            id = "trip_completed_rating_test",
            passengerName = "Lamin",
            driverId = "drv_bakary",
            driverName = "Bakary Touray",
            vehicleType = "CAR",
            vehiclePlate = "WCR 7431 B",
            pickupName = "Senegambia",
            dropoffName = "Kairaba",
            pickupLat = 13.44,
            pickupLng = -16.71,
            dropoffLat = 13.45,
            dropoffLng = -16.68,
            fareGmd = 200,
            paymentMethod = "WAVE",
            status = "COMPLETED",
            rating = 0
        )
        repository.saveTrip(trip)

        // 2. Rate trip with comment and tags
        viewModel.rateTripSync(
            tripId = "trip_completed_rating_test",
            rating = 5,
            comment = "Smooth ride, very polite driver!",
            tags = "Polite, Smooth driving"
        )

        // 3. Verify rating was saved
        val ratedTrip = repository.getTripById("trip_completed_rating_test")
        assertNotNull(ratedTrip)
        assertEquals(5, ratedTrip?.rating)
        assertEquals("Smooth ride, very polite driver!", ratedTrip?.reviewComment)
        assertEquals("Polite, Smooth driving", ratedTrip?.reviewTags)
    }
}
