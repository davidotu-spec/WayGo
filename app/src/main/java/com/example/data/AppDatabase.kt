package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserProfileEntity::class,
        DriverEntity::class,
        TripEntity::class,
        SupportMessageEntity::class,
        ScheduledRideEntity::class,
        SavedPlaceEntity::class,
        VehicleMileageEntity::class,
        PastRideHistoryEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class WayGoDatabase : RoomDatabase() {
    abstract fun dao(): WayGoDao

    companion object {
        @Volatile
        private var INSTANCE: WayGoDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): WayGoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WayGoDatabase::class.java,
                    "waygo_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    preseedData(database.dao())
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val drivers = database.dao().getAllDrivers()
                        if (drivers.isEmpty()) {
                            preseedData(database.dao())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WayGoDatabase", "Driver check on open: ${e.localizedMessage}")
                    }
                }
            }
        }

        private suspend fun preseedData(dao: WayGoDao) {
            // Seed passenger profile
            dao.insertOrUpdateProfile(
                UserProfileEntity(
                    id = "current_passenger",
                    name = "John Doe",
                    phone = "+220 771 2345",
                    email = "johndoe@example.com",
                    gender = "Male",
                    mobileMoneyNumber = "+220 384 5678",
                    savedHome = "Kairaba Avenue, Serrekunda",
                    savedWork = "Albert Market, Banjul",
                    avatarIndex = 1,
                    isPaymentLinked = false,
                    linkedCardLast4 = "",
                    linkedPaymentEmail = ""
                )
            )

            // Seed initial Saved Places
            dao.insertSavedPlace(
                SavedPlaceEntity(
                    name = "Kairaba Avenue, Serrekunda",
                    label = "Home",
                    lat = 13.4471,
                    lng = -16.6791,
                    iconType = "HOME"
                )
            )
            dao.insertSavedPlace(
                SavedPlaceEntity(
                    name = "Albert Market, Banjul",
                    label = "Work",
                    lat = 13.4533,
                    lng = -16.5746,
                    iconType = "WORK"
                )
            )

            // Seed initial drivers
            val initialDrivers = listOf(
                DriverEntity(
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
                ),
                DriverEntity(
                    id = "drv_mariama",
                    name = "Mariama Jallow",
                    phone = "+220 312 0451",
                    vehicleType = "TRICYCLE",
                    vehiclePlate = "KM 9312 T",
                    rating = 4.9f,
                    approvalStatus = "APPROVED",
                    isOnline = true,
                    currentLat = 13.4460,
                    currentLng = -16.6720,
                    driverLicense = "DL-2025-1029"
                ),
                DriverEntity(
                    id = "drv_bakary",
                    name = "Bakary Touray",
                    phone = "+220 754 1121",
                    vehicleType = "CAR",
                    vehiclePlate = "WCR 7431 B",
                    rating = 4.7f,
                    approvalStatus = "APPROVED",
                    isOnline = true,
                    currentLat = 13.4410,
                    currentLng = -16.7100,
                    driverLicense = "DL-2023-4552"
                ),
                DriverEntity(
                    id = "drv_ebrima",
                    name = "Ebrima Sanneh",
                    phone = "+220 221 0098",
                    vehicleType = "TRICYCLE",
                    vehiclePlate = "BJL 1102 T",
                    rating = 4.6f,
                    approvalStatus = "APPROVED",
                    isOnline = true,
                    currentLat = 13.4530,
                    currentLng = -16.5760,
                    driverLicense = "DL-2025-0551"
                ),
                DriverEntity(
                    id = "drv_fatoumata",
                    name = "Fatoumata Barrow",
                    phone = "+220 541 7765",
                    vehicleType = "CAR",
                    vehiclePlate = "KM 5612 A",
                    rating = 5.0f,
                    approvalStatus = "PENDING", // Pending Admin Approval
                    isOnline = false,
                    currentLat = 13.4750,
                    currentLng = -16.6800,
                    driverLicense = "DL-2026-6612"
                ),
                DriverEntity(
                    id = "drv_lamin",
                    name = "Lamin Camara",
                    phone = "+220 663 2291",
                    vehicleType = "TRICYCLE",
                    vehiclePlate = "KM 3099 T",
                    rating = 4.3f,
                    approvalStatus = "APPROVED",
                    isOnline = false, // Offline
                    currentLat = 13.4380,
                    currentLng = -16.6820,
                    driverLicense = "DL-2024-3011"
                )
            )
            dao.insertDrivers(initialDrivers)

            // Seed initial completed trips
            dao.insertOrUpdateTrip(
                TripEntity(
                    id = "trip_local_1",
                    passengerName = "John Doe",
                    driverId = "drv_alieu",
                    driverName = "Alieu Ceesay",
                    vehicleType = "CAR",
                    vehiclePlate = "BJL 4821 C",
                    pickupName = "Kairaba Avenue, Serrekunda",
                    dropoffName = "Albert Market, Banjul",
                    pickupLat = 13.4470,
                    pickupLng = -16.6790,
                    dropoffLat = 13.4530,
                    dropoffLng = -16.5760,
                    fareGmd = 350,
                    paymentMethod = "WAVE",
                    status = "COMPLETED",
                    rating = 5,
                    reviewComment = "Smooth ride from Serrekunda to Albert Market!",
                    reviewTags = "Safe, Polite, Quick",
                    timestamp = System.currentTimeMillis() - 24 * 60 * 60 * 1000
                )
            )
            dao.insertOrUpdateTrip(
                TripEntity(
                    id = "trip_local_2",
                    passengerName = "John Doe",
                    driverId = "drv_mariama",
                    driverName = "Mariama Jallow",
                    vehicleType = "TRICYCLE",
                    vehiclePlate = "KM 9312 T",
                    pickupName = "Senegambia Strip, Kololi",
                    dropoffName = "Kairaba Avenue, Serrekunda",
                    pickupLat = 13.4380,
                    pickupLng = -16.7120,
                    dropoffLat = 13.4470,
                    dropoffLng = -16.6790,
                    fareGmd = 150,
                    paymentMethod = "CASH",
                    status = "COMPLETED",
                    rating = 4,
                    reviewComment = "Fun tricycle ride!",
                    reviewTags = "Cheerful",
                    timestamp = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000
                )
            )

            // Seed initial support messages
            dao.insertSupportMessage(
                SupportMessageEntity(
                    senderRole = "PASSENGER",
                    message = "Hello! Welcome to WayGo. How can we help you with your journey in Gambia today?",
                    status = "RESOLVED",
                    issueCategory = "Welcome"
                )
            )

            // Seed initial vehicle mileage data for drivers
            dao.insertOrUpdateVehicleMileage(
                VehicleMileageEntity(
                    driverId = "drv_alieu",
                    currentMileage = 12450.0,
                    lastOilChangeMileage = 10000.0,
                    lastTireCheckMileage = 8000.0
                )
            )
            dao.insertOrUpdateVehicleMileage(
                VehicleMileageEntity(
                    driverId = "drv_mariama",
                    currentMileage = 4920.0,
                    lastOilChangeMileage = 4000.0,
                    lastTireCheckMileage = 3000.0
                )
            )
            dao.insertOrUpdateVehicleMileage(
                VehicleMileageEntity(
                    driverId = "drv_bakary",
                    currentMileage = 15300.0,
                    lastOilChangeMileage = 12000.0,
                    lastTireCheckMileage = 12000.0
                )
            )

            // Seed initial past ride history table records (destinations, dates, fares, drivers)
            val initialPastRides = listOf(
                PastRideHistoryEntity(
                    id = "past_ride_101",
                    pickupLocation = "Westfield Junction, Serrekunda",
                    destination = "Senegambia Strip, Kololi",
                    dateFormatted = "31 Aug 2026, 20:15",
                    timestamp = System.currentTimeMillis() - 18 * 60 * 60 * 1000L,
                    fareGmd = 250,
                    driverName = "Alieu Ceesay",
                    vehicleType = "CAR",
                    vehiclePlate = "BJL 4821 C",
                    paymentMethod = "WAVE",
                    status = "COMPLETED",
                    rating = 5.0f,
                    distanceKm = 6.8,
                    durationMinutes = 14,
                    notes = "Evening trip to tourist strip, smooth AC ride",
                    tipGmd = 30
                ),
                PastRideHistoryEntity(
                    id = "past_ride_102",
                    pickupLocation = "Kairaba Avenue, Fajara",
                    destination = "Albert Market, Banjul",
                    dateFormatted = "29 Aug 2026, 09:30",
                    timestamp = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L,
                    fareGmd = 350,
                    driverName = "Mariama Jallow",
                    vehicleType = "TRICYCLE",
                    vehiclePlate = "KM 9312 T",
                    paymentMethod = "CASH",
                    status = "COMPLETED",
                    rating = 4.9f,
                    distanceKm = 11.2,
                    durationMinutes = 24,
                    notes = "Morning commute to downtown Banjul commercial district",
                    tipGmd = 20
                ),
                PastRideHistoryEntity(
                    id = "past_ride_103",
                    pickupLocation = "Brusubi Turntable, West Coast",
                    destination = "Banjul International Airport, Yundum",
                    dateFormatted = "26 Aug 2026, 14:00",
                    timestamp = System.currentTimeMillis() - 6 * 24 * 60 * 60 * 1000L,
                    fareGmd = 450,
                    driverName = "Bakary Touray",
                    vehicleType = "CAR",
                    vehiclePlate = "WCR 7431 B",
                    paymentMethod = "QMONEY",
                    status = "COMPLETED",
                    rating = 5.0f,
                    distanceKm = 14.5,
                    durationMinutes = 22,
                    notes = "Airport transfer with luggage assistance",
                    tipGmd = 50
                ),
                PastRideHistoryEntity(
                    id = "past_ride_104",
                    pickupLocation = "Bakau Craft Market",
                    destination = "Kotu Beach Resort",
                    dateFormatted = "23 Aug 2026, 16:45",
                    timestamp = System.currentTimeMillis() - 9 * 24 * 60 * 60 * 1000L,
                    fareGmd = 150,
                    driverName = "Ebrima Sanneh",
                    vehicleType = "TRICYCLE",
                    vehiclePlate = "BJL 1102 T",
                    paymentMethod = "AFRIMONEY",
                    status = "COMPLETED",
                    rating = 4.7f,
                    distanceKm = 4.2,
                    durationMinutes = 10,
                    notes = "Quick afternoon beach cruise",
                    tipGmd = 15
                ),
                PastRideHistoryEntity(
                    id = "past_ride_105",
                    pickupLocation = "Tabokoto Highway",
                    destination = "University of The Gambia, Kanifing",
                    dateFormatted = "19 Aug 2026, 08:10",
                    timestamp = System.currentTimeMillis() - 13 * 24 * 60 * 60 * 1000L,
                    fareGmd = 180,
                    driverName = "Alieu Ceesay",
                    vehicleType = "CAR",
                    vehiclePlate = "BJL 4821 C",
                    paymentMethod = "WAVE",
                    status = "COMPLETED",
                    rating = 4.8f,
                    distanceKm = 5.6,
                    durationMinutes = 12,
                    notes = "Campus commute for morning lectures",
                    tipGmd = 0
                )
            )
            dao.insertPastRides(initialPastRides)
        }
    }
}

