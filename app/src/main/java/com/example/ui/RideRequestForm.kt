package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActiveRideRequest
import com.example.data.GeoUtils
import com.example.data.RideLocation
import com.example.data.RideStatus
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

/**
 * Production-ready UI Form Composable designed to collect complete ride details:
 * 1. Pickup location (address text, GPS locator, landmark chips, coordinate validation)
 * 2. Destination location (address text, landmark chips, coordinate validation, swap action)
 * 3. Vehicle type choice (Sedan / Car, Tricycle / Keke, Taxi, Van with transparent fares)
 * 4. Passenger user ID and profile information
 * 5. Saves directly to Cloud Firestore 'ride_requests' collection with visual confirmation!
 */
@Composable
fun RideRequestForm(
    pickupLocation: RideLocation,
    destinationLocation: RideLocation,
    selectedVehicleType: String,
    onPickupChanged: (RideLocation) -> Unit,
    onDestinationChanged: (RideLocation) -> Unit,
    onVehicleTypeChanged: (String) -> Unit,
    passengerId: String,
    passengerName: String,
    passengerPhone: String,
    onSubmitRideRequest: (RideRequestFormData) -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    firestoreStatusMessage: String? = null,
    onUseCurrentLocation: (() -> Unit)? = null,
    onOpenMapPicker: ((MapTargetMode) -> Unit)? = null,
    initialPaymentMethod: String = "CASH"
) {
    var pickupText by remember(pickupLocation) { mutableStateOf(pickupLocation.name.ifBlank { pickupLocation.address }) }
    var destinationText by remember(destinationLocation) { mutableStateOf(destinationLocation.name.ifBlank { destinationLocation.address }) }
    var paymentMethod by remember { mutableStateOf(initialPaymentMethod) }
    var showFareDetails by remember { mutableStateOf(false) }

    // Validation error states
    var pickupError by remember { mutableStateOf<String?>(null) }
    var destinationError by remember { mutableStateOf<String?>(null) }

    // Ride customization toggles
    var prefQuiet by remember { mutableStateOf(false) }
    var prefAc by remember { mutableStateOf(false) }
    var prefLuggage by remember { mutableStateOf(false) }

    // Dynamic distance calculation between pickup and destination
    val distanceKm = remember(pickupLocation, destinationLocation) {
        if (pickupLocation.latitude != 0.0 && destinationLocation.latitude != 0.0) {
            val d = GeoUtils.haversineDistanceKm(
                pickupLocation.latitude, pickupLocation.longitude,
                destinationLocation.latitude, destinationLocation.longitude
            )
            // Round to 1 decimal place, minimum 1.5 km in urban Gambia
            kotlin.math.max(1.5, (d * 10).toInt() / 10.0)
        } else {
            5.2 // Default standard Banjul-Kanifing trip estimate
        }
    }

    val estimatedDurationMin = remember(distanceKm) {
        kotlin.math.max(5, (distanceKm * 2.5).toInt() + 3)
    }

    // Dynamic fare calculation based on vehicle type choice and distance
    val selectedOption = remember(selectedVehicleType) {
        AVAILABLE_VEHICLE_OPTIONS.firstOrNull { it.typeId.equals(selectedVehicleType, ignoreCase = true) }
            ?: AVAILABLE_VEHICLE_OPTIONS.first()
    }

    val calculatedFareGmd = remember(selectedOption, distanceKm) {
        val base = selectedOption.baseFareGmd
        val distCharge = distanceKm * selectedOption.perKmRateGmd
        val total = base + distCharge
        // Round to nearest 10 GMD
        ((total / 10).toInt() * 10)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ride_details_form_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Form title & Firestore Collection Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandBluePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Ride Form",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Collect Ride Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = BrandBlueDark
                        )
                        Text(
                            text = "Pickup, destination & vehicle selection",
                            fontSize = 11.5.sp,
                            color = NeutralGray
                        )
                    }
                }

                // Cloud Firestore Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(SuccessGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Firestore: ride_requests",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ========================================================
            // 1. PICKUP LOCATION SECTION
            // ========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pickup Location *",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlueDark
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onUseCurrentLocation != null) {
                        TextButton(
                            onClick = onUseCurrentLocation,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("ride_form_gps_pickup_btn")
                        ) {
                            Icon(Icons.Default.GpsFixed, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Current GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }

                    if (onOpenMapPicker != null) {
                        TextButton(
                            onClick = { onOpenMapPicker(MapTargetMode.PICKUP) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("ride_form_map_pickup_btn")
                        ) {
                            Icon(Icons.Default.PinDrop, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick on Map", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = pickupText,
                onValueChange = {
                    pickupText = it
                    pickupError = null
                    onPickupChanged(
                        pickupLocation.copy(
                            name = it,
                            address = it
                        )
                    )
                },
                placeholder = { Text("e.g. Kairaba Avenue, Serrekunda", fontSize = 13.5.sp) },
                leadingIcon = {
                    Icon(Icons.Default.MyLocation, contentDescription = "Pickup", tint = SuccessGreen)
                },
                trailingIcon = {
                    if (pickupText.isNotEmpty()) {
                        IconButton(onClick = {
                            pickupText = ""
                            onPickupChanged(RideLocation())
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Pickup", tint = NeutralGray)
                        }
                    }
                },
                isError = pickupError != null,
                supportingText = {
                    if (pickupError != null) {
                        Text(text = pickupError!!, color = ErrorRed, fontSize = 11.sp)
                    } else if (pickupLocation.latitude != 0.0) {
                        Text(
                            text = "GPS Coordinates: ${pickupLocation.toCoordinatesString()}",
                            fontSize = 10.sp,
                            color = SuccessGreen
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ride_form_pickup_input")
                    .testTag("pickup_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBluePrimary,
                    unfocusedBorderColor = NeutralGray.copy(alpha = 0.35f),
                    focusedTextColor = BrandBlueDark,
                    unfocusedTextColor = BrandBlueDark
                )
            )

            // Popular Pickup Landmarks Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(POPULAR_PICKUP_SHORTCUTS) { shortcut ->
                    SuggestionChip(
                        onClick = {
                            pickupText = shortcut.name
                            pickupError = null
                            onPickupChanged(
                                RideLocation(
                                    name = shortcut.name,
                                    address = shortcut.address,
                                    latitude = shortcut.latitude,
                                    longitude = shortcut.longitude
                                )
                            )
                        },
                        label = { Text(shortcut.name, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = BrandBluePrimary.copy(alpha = 0.05f),
                            labelColor = BrandBlueDark
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = BrandBluePrimary.copy(alpha = 0.2f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ========================================================
            // 2. DESTINATION (DROP-OFF) LOCATION SECTION
            // ========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Destination (Drop-off) *",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlueDark
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            val tempText = pickupText
                            val tempLoc = pickupLocation
                            pickupText = destinationText
                            destinationText = tempText
                            onPickupChanged(destinationLocation)
                            onDestinationChanged(tempLoc)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("ride_form_swap_locations_btn")
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Swap Locations", tint = BrandBlueDark, modifier = Modifier.size(18.dp))
                    }

                    if (onOpenMapPicker != null) {
                        TextButton(
                            onClick = { onOpenMapPicker(MapTargetMode.DESTINATION) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("ride_form_map_dest_btn")
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick on Map", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = destinationText,
                onValueChange = {
                    destinationText = it
                    destinationError = null
                    onDestinationChanged(
                        destinationLocation.copy(
                            name = it,
                            address = it
                        )
                    )
                },
                placeholder = { Text("e.g. Albert Market, Banjul", fontSize = 13.5.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Place, contentDescription = "Destination", tint = ErrorRed)
                },
                trailingIcon = {
                    if (destinationText.isNotEmpty()) {
                        IconButton(onClick = {
                            destinationText = ""
                            onDestinationChanged(RideLocation())
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Destination", tint = NeutralGray)
                        }
                    }
                },
                isError = destinationError != null,
                supportingText = {
                    if (destinationError != null) {
                        Text(text = destinationError!!, color = ErrorRed, fontSize = 11.sp)
                    } else if (destinationLocation.latitude != 0.0) {
                        Text(
                            text = "GPS Coordinates: ${destinationLocation.toCoordinatesString()}",
                            fontSize = 10.sp,
                            color = BrandBluePrimary
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ride_form_destination_input")
                    .testTag("dropoff_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBluePrimary,
                    unfocusedBorderColor = NeutralGray.copy(alpha = 0.35f),
                    focusedTextColor = BrandBlueDark,
                    unfocusedTextColor = BrandBlueDark
                )
            )

            // Popular Destination Landmarks Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(POPULAR_DESTINATION_SHORTCUTS) { shortcut ->
                    SuggestionChip(
                        onClick = {
                            destinationText = shortcut.name
                            destinationError = null
                            onDestinationChanged(
                                RideLocation(
                                    name = shortcut.name,
                                    address = shortcut.address,
                                    latitude = shortcut.latitude,
                                    longitude = shortcut.longitude
                                )
                            )
                        },
                        label = { Text(shortcut.name, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = BrandBluePrimary.copy(alpha = 0.05f),
                            labelColor = BrandBlueDark
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = BrandBluePrimary.copy(alpha = 0.2f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ========================================================
            // 3. VEHICLE TYPE CHOICE SECTION
            // ========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DriveEta, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vehicle Type Choice *",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BrandBlueDark
                    )
                }

                Text(
                    text = "${AVAILABLE_VEHICLE_OPTIONS.size} Options Available",
                    fontSize = 11.sp,
                    color = NeutralGray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Vehicle Options Cards (2x2 Grid Layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vehicle_type_selection_group"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val carOption = AVAILABLE_VEHICLE_OPTIONS[0]
                    val tricycleOption = AVAILABLE_VEHICLE_OPTIONS[1]

                    VehicleOptionCard(
                        option = carOption,
                        isSelected = selectedVehicleType.equals(carOption.typeId, ignoreCase = true),
                        estimatedFareGmd = (carOption.baseFareGmd + (distanceKm * carOption.perKmRateGmd)).toInt(),
                        onSelect = { onVehicleTypeChanged(carOption.typeId) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_choice_car")
                            .testTag("vehicle_car_select")
                    )

                    VehicleOptionCard(
                        option = tricycleOption,
                        isSelected = selectedVehicleType.equals(tricycleOption.typeId, ignoreCase = true),
                        estimatedFareGmd = (tricycleOption.baseFareGmd + (distanceKm * tricycleOption.perKmRateGmd)).toInt(),
                        onSelect = { onVehicleTypeChanged(tricycleOption.typeId) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_choice_tricycle")
                            .testTag("vehicle_tuk_select")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val taxiOption = AVAILABLE_VEHICLE_OPTIONS[2]
                    val vanOption = AVAILABLE_VEHICLE_OPTIONS[3]

                    VehicleOptionCard(
                        option = taxiOption,
                        isSelected = selectedVehicleType.equals(taxiOption.typeId, ignoreCase = true),
                        estimatedFareGmd = (taxiOption.baseFareGmd + (distanceKm * taxiOption.perKmRateGmd)).toInt(),
                        onSelect = { onVehicleTypeChanged(taxiOption.typeId) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_choice_taxi")
                    )

                    VehicleOptionCard(
                        option = vanOption,
                        isSelected = selectedVehicleType.equals(vanOption.typeId, ignoreCase = true),
                        estimatedFareGmd = (vanOption.baseFareGmd + (distanceKm * vanOption.perKmRateGmd)).toInt(),
                        onSelect = { onVehicleTypeChanged(vanOption.typeId) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vehicle_choice_van")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ========================================================
            // 4. FARE ESTIMATION & ROUTE BREAKDOWN CARD
            // ========================================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BrandBluePrimary.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("estimated_fare_summary_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Estimated Fare (${selectedOption.displayName})",
                                fontSize = 11.5.sp,
                                color = NeutralGray
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$calculatedFareGmd GMD",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BrandBluePrimary,
                                    modifier = Modifier.testTag("calculated_fare_text")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• ~$estimatedDurationMin min",
                                    fontSize = 12.sp,
                                    color = NeutralGray,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Est. $distanceKm km",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Transparent Breakdown Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFareDetails = !showFareDetails }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showFareDetails) "Hide Calculation Details ▲" else "View Calculation Breakdown ▼",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandBluePrimary
                        )
                        Text(
                            text = "Rate: ${selectedOption.perKmRateGmd.toInt()} GMD/km",
                            fontSize = 10.sp,
                            color = NeutralGray
                        )
                    }

                    if (showFareDetails) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PureWhite, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Base Fare (${selectedOption.displayName})", fontSize = 11.sp, color = NeutralGray)
                                Text("${selectedOption.baseFareGmd} GMD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BrandBlueDark)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Distance Charge ($distanceKm km × ${selectedOption.perKmRateGmd.toInt()} GMD)", fontSize = 11.sp, color = NeutralGray)
                                Text("${(distanceKm * selectedOption.perKmRateGmd).toInt()} GMD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BrandBlueDark)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 2.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Estimated Total", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                                Text("$calculatedFareGmd GMD", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = BrandBluePrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ========================================================
            // 5. PAYMENT METHOD & PASSENGER METADATA
            // ========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Payment Method",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlueDark
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "WAVE", "STRIPE").forEach { method ->
                        val isSelected = paymentMethod == method
                        Surface(
                            onClick = { paymentMethod = method },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) BrandBluePrimary else BrandBluePrimary.copy(alpha = 0.08f),
                            modifier = Modifier.testTag("payment_chip_${method.lowercase()}")
                        ) {
                            Text(
                                text = method,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else BrandBlueDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Passenger Identity Preview
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BrandBlueDark.copy(alpha = 0.04f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Passenger: $passengerName ($passengerId)",
                            fontSize = 10.sp,
                            color = BrandBlueDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Status: pending",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                }
            }

            // Status message display if available
            if (!firestoreStatusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firestore_save_status_card")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = firestoreStatusMessage,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ========================================================
            // 6. CONFIRM & SAVE TO FIRESTORE ACTION BUTTON
            // ========================================================
            Button(
                onClick = {
                    // Validation
                    var hasError = false
                    if (pickupText.isBlank()) {
                        pickupError = "Please enter or pick a pickup location"
                        hasError = true
                    }
                    if (destinationText.isBlank()) {
                        destinationError = "Please enter or pick a destination"
                        hasError = true
                    }

                    if (hasError) return@Button

                    val finalPickup = if (pickupLocation.name.isNotBlank()) {
                        pickupLocation
                    } else {
                        RideLocation(name = pickupText, address = pickupText, latitude = 13.4471, longitude = -16.6791)
                    }

                    val finalDest = if (destinationLocation.name.isNotBlank()) {
                        destinationLocation
                    } else {
                        RideLocation(name = destinationText, address = destinationText, latitude = 13.4533, longitude = -16.5746)
                    }

                    val prefs = listOfNotNull(
                        if (prefQuiet) "Quiet" else null,
                        if (prefAc) "AC" else null,
                        if (prefLuggage) "Luggage" else null
                    ).joinToString(" • ")

                    onSubmitRideRequest(
                        RideRequestFormData(
                            pickupLocation = finalPickup,
                            destinationLocation = finalDest,
                            vehicleType = selectedVehicleType,
                            paymentMethod = paymentMethod,
                            fareGmd = calculatedFareGmd,
                            distanceKm = distanceKm,
                            estimatedDurationMin = estimatedDurationMin,
                            passengerId = passengerId,
                            passengerName = passengerName,
                            passengerPhone = passengerPhone,
                            preferences = prefs
                        )
                    )
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_ride_request_button")
                    .testTag("request_ride_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saving to Firestore 'ride_requests'...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Ride Request to Firestore • $calculatedFareGmd GMD",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Reusable Card component for displaying and choosing a specific vehicle type.
 */
@Composable
fun VehicleOptionCard(
    option: VehicleTypeOption,
    isSelected: Boolean,
    estimatedFareGmd: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelect,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) BrandBluePrimary else Color(0xFFE2E8F0)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BrandBluePrimary.copy(alpha = 0.08f) else PureWhite
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) BrandBluePrimary else option.badgeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = option.iconVector,
                        contentDescription = option.displayName,
                        tint = if (isSelected) Color.White else option.badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = option.badgeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = option.badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = option.badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = option.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = BrandBlueDark
            )

            Text(
                text = "${option.capacity} • Est. $estimatedFareGmd GMD",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) BrandBluePrimary else NeutralGray
            )
        }
    }
}
