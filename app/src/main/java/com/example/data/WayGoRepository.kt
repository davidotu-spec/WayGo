package com.example.data

import kotlinx.coroutines.flow.Flow

class WayGoRepository(private val dao: WayGoDao) {

    // User Profile
    val userProfileFlow: Flow<UserProfileEntity?> = dao.getUserProfileFlow()
    
    suspend fun getUserProfile(): UserProfileEntity? = dao.getUserProfile()
    
    suspend fun saveUserProfile(profile: UserProfileEntity) {
        dao.insertOrUpdateProfile(profile)
    }

    suspend fun deleteUserProfile() {
        dao.deleteUserProfile()
    }

    // Drivers
    val allDriversFlow: Flow<List<DriverEntity>> = dao.getAllDriversFlow()
    
    suspend fun getAllDrivers(): List<DriverEntity> = dao.getAllDrivers()
    
    suspend fun getDriverById(id: String): DriverEntity? = dao.getDriverById(id)
    
    suspend fun saveDriver(driver: DriverEntity) {
        dao.insertOrUpdateDriver(driver)
    }

    suspend fun updateDriverOnlineStatus(driverId: String, isOnline: Boolean) {
        dao.updateDriverOnlineStatus(driverId, isOnline)
    }

    suspend fun updateDriverLocation(driverId: String, lat: Double, lng: Double) {
        dao.updateDriverLocation(driverId, lat, lng)
    }

    suspend fun updateDriverApprovalStatus(driverId: String, status: String) {
        dao.updateDriverApprovalStatus(driverId, status)
    }

    // Trips
    val allTripsFlow: Flow<List<TripEntity>> = dao.getAllTripsFlow()
    val activeTripFlow: Flow<TripEntity?> = dao.getActiveTripFlow()
    
    suspend fun getActiveTrip(): TripEntity? = dao.getActiveTrip()
    suspend fun getTripById(id: String): TripEntity? = dao.getTripById(id)
    suspend fun getTripsForDriver(driverId: String): List<TripEntity> = dao.getTripsForDriver(driverId)
    
    suspend fun saveTrip(trip: TripEntity) {
        dao.insertOrUpdateTrip(trip)
    }

    suspend fun updateTripStatus(tripId: String, status: String) {
        if (status == "COMPLETED") {
            val trip = dao.getTripById(tripId)
            if (trip != null) {
                val commission = (trip.fareGmd * 0.15).toInt()
                val updatedTrip = trip.copy(status = "COMPLETED", commissionGmd = commission)
                dao.insertOrUpdateTrip(updatedTrip)
                FirestoreManager.saveTripToFirestore(updatedTrip) { success ->
                    android.util.Log.d("WayGoRepository", "Sync complete status on updateTripStatus: $success")
                }
                return
            }
        }
        dao.updateTripStatus(tripId, status)
    }

    suspend fun rateTrip(tripId: String, rating: Int, comment: String, tags: String, tipGmd: Int = 0) {
        dao.rateTrip(tripId, rating, comment, tags, tipGmd)
    }

    // Support Chat
    val allSupportMessagesFlow: Flow<List<SupportMessageEntity>> = dao.getAllSupportMessagesFlow()
    
    suspend fun sendSupportMessage(msg: SupportMessageEntity) {
        dao.insertSupportMessage(msg)
    }

    // Scheduled Rides
    val allScheduledRidesFlow: Flow<List<ScheduledRideEntity>> = dao.getAllScheduledRidesFlow()

    suspend fun getScheduledRideById(id: String): ScheduledRideEntity? = dao.getScheduledRideById(id)

    suspend fun saveScheduledRide(ride: ScheduledRideEntity) {
        dao.insertOrUpdateScheduledRide(ride)
    }

    suspend fun updateScheduledRideStatus(rideId: String, status: String) {
        dao.updateScheduledRideStatus(rideId, status)
    }

    suspend fun updateScheduledRideNotified(rideId: String, isNotified: Boolean) {
        dao.updateScheduledRideNotified(rideId, isNotified)
    }

    // Saved Places
    val allSavedPlacesFlow: Flow<List<SavedPlaceEntity>> = dao.getAllSavedPlacesFlow()

    suspend fun saveSavedPlace(place: SavedPlaceEntity) {
        dao.insertSavedPlace(place)
    }

    suspend fun deleteSavedPlaceById(id: Long) {
        dao.deleteSavedPlaceById(id)
    }

    suspend fun deleteSavedPlacesByLabel(label: String) {
        dao.deleteSavedPlacesByLabel(label)
    }

    // Vehicle Mileage & Maintenance
    fun getVehicleMileageFlow(driverId: String): Flow<VehicleMileageEntity?> = dao.getVehicleMileageFlow(driverId)

    suspend fun getVehicleMileage(driverId: String): VehicleMileageEntity? = dao.getVehicleMileage(driverId)

    suspend fun saveVehicleMileage(mileage: VehicleMileageEntity) {
        dao.insertOrUpdateVehicleMileage(mileage)
    }

    // Past Ride History (Room Database Table)
    val allPastRidesFlow: Flow<List<PastRideHistoryEntity>> = dao.getAllPastRideHistoryFlow()

    fun searchPastRidesFlow(query: String): Flow<List<PastRideHistoryEntity>> = dao.searchPastRidesFlow(query)

    suspend fun getPastRideById(id: String): PastRideHistoryEntity? = dao.getPastRideById(id)

    suspend fun savePastRide(ride: PastRideHistoryEntity) {
        dao.insertPastRide(ride)
    }

    suspend fun savePastRides(rides: List<PastRideHistoryEntity>) {
        dao.insertPastRides(rides)
    }

    suspend fun deletePastRide(id: String) {
        dao.deletePastRideById(id)
    }

    suspend fun clearAllPastRides() {
        dao.clearAllPastRides()
    }
}

