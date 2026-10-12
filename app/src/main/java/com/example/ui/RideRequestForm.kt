package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import com.example.data.RideLocation
import com.example.ui.theme.*

/**
 * Data class representing a Vehicle Option for passenger selection.
 */
data class VehicleTypeOption(
    val typeId: String,
    val displayName: String,
    val subtitle: String,
    val capacity: String,
    val baseFareGmd: Int,
    val perKmRateGmd: Double,
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    val badgeText: String,
    val badgeColor: Color
)

val AVAILABLE_VEHICLE_OPTIONS = listOf(
    VehicleTypeOption(
        typeId = "CAR",
        displayName = "WayGo Sedan",
        subtitle = "Comfortable air-conditioned ride",
        capacity = "4 seats",
        baseFareGmd = 100,
        perKmRateGmd = 35.0,
        iconVector = Icons.Default.DirectionsCar,
        badgeText = "Popular",
        badgeColor = BrandBluePrimary
    ),
    VehicleTypeOption(
        typeId = "TRICYCLE",
        displayName = "Keke Express",
        subtitle = "Affordable 3-wheeler for quick hops",
        capacity = "3 seats",
        baseFareGmd = 50,
        perKmRateGmd = 20.0,
        iconVector = Icons.Default.TwoWheeler,
        badgeText = "Budget",
        badgeColor = SuccessGreen
    ),
    VehicleTypeOption(
        typeId = "TAXI",
        displayName = "City Taxi",
        subtitle = "Classic yellow & green licensed taxi",
        capacity = "4 seats",
        baseFareGmd = 80,
        perKmRateGmd = 30.0,
        iconVector = Icons.Default.LocalTaxi,
        badgeText = "Fast Match",
        badgeColor = AccentAmber
    ),
    VehicleTypeOption(
        typeId = "VAN",
        displayName = "WayGo Van",
        subtitle = "Spacious ride for groups & luggage",
        capacity = "7 seats",
        baseFareGmd = 150,
        perKmRateGmd = 50.0,
        iconVector = Icons.Default.AirportShuttle,
        badgeText = "Group / Luggage",
        badgeColor = BrandBlueDark
    )
)

/**
 * Common Gambia Location shortcut presets for quick form autofill.
 */
data class LocationShortcut(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String
)

val POPULAR_PICKUP_SHORTCUTS = listOf(
    LocationShortcut("Kairaba Avenue", "Kairaba Avenue, Serrekunda", 13.4471, -16.6791, "Kanifing"),
    LocationShortcut("Westfield Junction", "Westfield Transit Hub, Serrekunda", 13.4385, -16.6760, "Kanifing"),
    LocationShortcut("Albert Market", "Albert Market, Banjul", 13.4533, -16.5746, "Banjul"),
    LocationShortcut("Senegambia Strip", "Senegambia Beach Highway, Kololi", 13.4420, -16.7110, "Kololi"),
    LocationShortcut("Brusubi Turntable", "Brusubi Roundabout, Kombo North", 13.4020, -16.7180, "Brusubi")
)

val POPULAR_DESTINATION_SHORTCUTS = listOf(
    LocationShortcut("Banjul Ferry Terminal", "Ferry Port, Central Banjul", 13.4505, -16.5710, "Banjul"),
    LocationShortcut("Banjul Airport (Yundum)", "Banjul International Airport", 13.3380, -16.6520, "Yundum"),
    LocationShortcut("Independence Stadium", "Independence Stadium, Bakau", 13.4722, -16.6690, "Bakau"),
    LocationShortcut("Traffic Lights Fajara", "Fajara Commercial Junction", 13.4680, -16.6850, "Fajara"),
    LocationShortcut("Tippa Garage", "Tippa Garage Buffer Zone, Latrikunda", 13.4340, -16.6850, "Kanifing")
)

/**
 * Ride details submission payload emitted when the user confirms their ride request.
 */
data class RideRequestFormData(
    val pickupLocation: RideLocation,
    val destinationLocation: RideLocation,
    val vehicleType: String,
    val paymentMethod: String,
    val fareGmd: Int,
    val distanceKm: Double,
    val estimatedDurationMin: Int,
    val passengerId: String,
    val passengerName: String,
    val passengerPhone: String,
    val preferences: String
)
