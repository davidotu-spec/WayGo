package com.example

import com.example.data.FirestoreRealtimeTrackingService
import com.example.data.GeoUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirestoreRealtimeTrackingServiceTest {

    @Test
    fun testServiceInitializationAndSingleton() {
        val service = FirestoreRealtimeTrackingService.getInstance()
        assertNotNull(service)
        assertSame(service, FirestoreRealtimeTrackingService.getInstance())
    }

    @Test
    fun testListenerCountAndCleanup() {
        val service = FirestoreRealtimeTrackingService.getInstance()
        assertEquals(0, service.getActiveRideListenersCount())
        assertEquals(0, service.getActiveDriverListenersCount())

        // Testing clean removal
        service.removeAllListeners()
        assertEquals(0, service.getActiveRideListenersCount())
        assertEquals(0, service.getActiveDriverListenersCount())
    }

    @Test
    fun testGeoUtilsCalculations() {
        // Banjul to Serrekunda distance check (~12-14km)
        val banjulLat = 13.4549
        val banjulLng = -16.5790
        val serrekundaLat = 13.4385
        val serrekundaLng = -16.6760

        val distance = GeoUtils.haversineDistanceKm(banjulLat, banjulLng, serrekundaLat, serrekundaLng)
        assertTrue(distance in 10.0..15.0)

        val geohash = GeoUtils.encodeGeohash(banjulLat, banjulLng, precision = 7)
        assertNotNull(geohash)
        assertEquals(7, geohash.length)
    }

    @Test
    fun testFallbackRideRequestOnOffline() {
        val service = FirestoreRealtimeTrackingService(forceOfflineMode = true)
        var receivedRequest: com.example.data.ActiveRideRequest? = null

        val registration = service.listenToRideRequestStatus("req_test_123", onUpdate = {
            receivedRequest = it
        })

        assertNull(registration)
        assertNotNull(receivedRequest)
        assertEquals("req_test_123", receivedRequest?.requestId)
        assertEquals("SEARCHING", receivedRequest?.status)
    }

    @Test
    fun testFallbackDriverLocationOnOffline() {
        val service = FirestoreRealtimeTrackingService(forceOfflineMode = true)
        var receivedLocation: com.example.data.DriverLocationData? = null

        val registration = service.listenToDriverLocation("drv_test_456", onLocationUpdate = {
            receivedLocation = it
        })

        assertNull(registration)
        assertNotNull(receivedLocation)
        assertEquals("drv_test_456", receivedLocation?.driverId)
        assertTrue(receivedLocation?.isOnline == true)
    }
}
