package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WayGoDao {
    // User Profile
    @Query("SELECT * FROM user_profiles WHERE id = 'current_passenger' LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = 'current_passenger' LIMIT 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE id = 'current_passenger'")
    suspend fun deleteUserProfile()

    // Drivers
    @Query("SELECT * FROM drivers")
    fun getAllDriversFlow(): Flow<List<DriverEntity>>

    @Query("SELECT * FROM drivers")
    suspend fun getAllDrivers(): List<DriverEntity>

    @Query("SELECT * FROM drivers WHERE id = :id LIMIT 1")
    suspend fun getDriverById(id: String): DriverEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDriver(driver: DriverEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrivers(drivers: List<DriverEntity>)

    @Query("UPDATE drivers SET isOnline = :isOnline WHERE id = :driverId")
    suspend fun updateDriverOnlineStatus(driverId: String, isOnline: Boolean)

    @Query("UPDATE drivers SET currentLat = :lat, currentLng = :lng WHERE id = :driverId")
    suspend fun updateDriverLocation(driverId: String, lat: Double, lng: Double)

    @Query("UPDATE drivers SET approvalStatus = :status WHERE id = :driverId")
    suspend fun updateDriverApprovalStatus(driverId: String, status: String)

    // Trips
    @Query("SELECT * FROM trips ORDER BY timestamp DESC")
    fun getAllTripsFlow(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE driverId = :driverId ORDER BY timestamp DESC")
    suspend fun getTripsForDriver(driverId: String): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: String): TripEntity?

    @Query("SELECT * FROM trips WHERE status IN ('REQUESTED', 'ACCEPTED', 'EN_ROUTE', 'ARRIVED') OR (status = 'COMPLETED' AND rating = 0) ORDER BY timestamp DESC LIMIT 1")
    fun getActiveTripFlow(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE status IN ('REQUESTED', 'ACCEPTED', 'EN_ROUTE', 'ARRIVED') OR (status = 'COMPLETED' AND rating = 0) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTrip(trip: TripEntity)

    @Query("UPDATE trips SET status = :status WHERE id = :tripId")
    suspend fun updateTripStatus(tripId: String, status: String)

    @Query("UPDATE trips SET rating = :rating, reviewComment = :comment, reviewTags = :tags, tipGmd = :tipGmd WHERE id = :tripId")
    suspend fun rateTrip(tripId: String, rating: Int, comment: String, tags: String, tipGmd: Int)

    // Support Messages
    @Query("SELECT * FROM support_messages ORDER BY timestamp ASC")
    fun getAllSupportMessagesFlow(): Flow<List<SupportMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupportMessage(message: SupportMessageEntity)

    // Scheduled Rides
    @Query("SELECT * FROM scheduled_rides ORDER BY scheduledEpochMs ASC")
    fun getAllScheduledRidesFlow(): Flow<List<ScheduledRideEntity>>

    @Query("SELECT * FROM scheduled_rides WHERE id = :id LIMIT 1")
    suspend fun getScheduledRideById(id: String): ScheduledRideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateScheduledRide(ride: ScheduledRideEntity)

    @Query("UPDATE scheduled_rides SET status = :status WHERE id = :rideId")
    suspend fun updateScheduledRideStatus(rideId: String, status: String)

    @Query("UPDATE scheduled_rides SET isNotified = :isNotified WHERE id = :rideId")
    suspend fun updateScheduledRideNotified(rideId: String, isNotified: Boolean)

    // Saved Places
    @Query("SELECT * FROM saved_places ORDER BY label ASC")
    fun getAllSavedPlacesFlow(): Flow<List<SavedPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPlace(place: SavedPlaceEntity)

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun deleteSavedPlaceById(id: Long)

    @Query("DELETE FROM saved_places WHERE label = :label")
    suspend fun deleteSavedPlacesByLabel(label: String)

    // Vehicle Mileage & Maintenance
    @Query("SELECT * FROM vehicle_mileage WHERE driverId = :driverId LIMIT 1")
    fun getVehicleMileageFlow(driverId: String): Flow<VehicleMileageEntity?>

    @Query("SELECT * FROM vehicle_mileage WHERE driverId = :driverId LIMIT 1")
    suspend fun getVehicleMileage(driverId: String): VehicleMileageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateVehicleMileage(mileage: VehicleMileageEntity)

    // Past Ride History (Local Room Database Table)
    @Query("SELECT * FROM past_ride_history ORDER BY timestamp DESC")
    fun getAllPastRideHistoryFlow(): Flow<List<PastRideHistoryEntity>>

    @Query("SELECT * FROM past_ride_history WHERE id = :id LIMIT 1")
    suspend fun getPastRideById(id: String): PastRideHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPastRide(ride: PastRideHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPastRides(rides: List<PastRideHistoryEntity>)

    @Query("DELETE FROM past_ride_history WHERE id = :id")
    suspend fun deletePastRideById(id: String)

    @Query("DELETE FROM past_ride_history")
    suspend fun clearAllPastRides()

    @Query("SELECT * FROM past_ride_history WHERE destination LIKE '%' || :query || '%' OR pickupLocation LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchPastRidesFlow(query: String): Flow<List<PastRideHistoryEntity>>
}

