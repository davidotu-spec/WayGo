package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DriverFusedLocationTracker
import com.example.data.RideLocation
import com.example.utils.LocationUtils
import com.example.utils.UserLocationState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Selection target mode when interacting with the Google Map.
 */
enum class MapTargetMode {
    PICKUP,
    DESTINATION
}

/**
 * Designated Gambian Landmark for fast location selection.
 */
data class GambiaMapLandmark(
    val name: String,
    val latLng: LatLng,
    val description: String
)

val POPULAR_GAMBIA_LANDMARKS = listOf(
    GambiaMapLandmark("Kairaba Avenue", LatLng(13.4471, -16.6791), "Serrekunda Banking & Shopping Strip"),
    GambiaMapLandmark("Albert Market", LatLng(13.4533, -16.5746), "Central Banjul Commercial Market"),
    GambiaMapLandmark("Westfield Junction", LatLng(13.4385, -16.6760), "Central Transit Nexus & Monument"),
    GambiaMapLandmark("Senegambia Strip", LatLng(13.4420, -16.7110), "Kololi Beach & Hospitality Hub"),
    GambiaMapLandmark("Banjul Ferry", LatLng(13.4505, -16.5710), "Port Terminal to Barra"),
    GambiaMapLandmark("Airport (Yundum)", LatLng(13.3380, -16.6520), "Banjul International Airport"),
    GambiaMapLandmark("Independence Stadium", LatLng(13.4722, -16.6690), "Bakau Sports Stadium"),
    GambiaMapLandmark("Brusubi Turntable", LatLng(13.4020, -16.7180), "Southern Commercial Roundabout"),
    GambiaMapLandmark("Tippa Garage", LatLng(13.4340, -16.6850), "Buffer Zone Taxi Rank")
)

/**
 * Google Maps SDK visualization Composable for passenger pickup and destination selection.
 * Integrates:
 * 1. GoogleMap from maps-compose with custom camera controls, markers, and polyline route.
 * 2. Runtime Location Permission handling with prompt UI.
 * 3. FusedLocationProviderClient for "My Current Location" GPS centering.
 * 4. Interactive tap-to-set for both pickup and destination coordinates.
 * 5. Dynamic route distance, estimated travel time, and fare calculation HUD.
 */
@SuppressLint("MissingPermission")
@Composable
fun GoogleMapsRideSelector(
    pickupLocation: RideLocation,
    destinationLocation: RideLocation,
    onPickupChanged: (RideLocation) -> Unit,
    onDestinationChanged: (RideLocation) -> Unit,
    driverLatLng: LatLng? = null,
    modifier: Modifier = Modifier,
    isSelectionEnabled: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var targetMode by remember { mutableStateOf(MapTargetMode.PICKUP) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var showPermissionRationale by remember { mutableStateOf(!LocationUtils.hasLocationPermission(context)) }
    var isLocatingUser by remember { mutableStateOf(false) }

    // Map marker states
    val pickupLatLng = remember(pickupLocation.latitude, pickupLocation.longitude) {
        val lat = if (pickupLocation.latitude != 0.0) pickupLocation.latitude else 13.4471
        val lng = if (pickupLocation.longitude != 0.0) pickupLocation.longitude else -16.6791
        LatLng(lat, lng)
    }

    val destinationLatLng = remember(destinationLocation.latitude, destinationLocation.longitude) {
        val lat = if (destinationLocation.latitude != 0.0) destinationLocation.latitude else 13.4533
        val lng = if (destinationLocation.longitude != 0.0) destinationLocation.longitude else -16.5746
        LatLng(lat, lng)
    }

    val pickupMarkerState = rememberMarkerState(position = pickupLatLng)
    val destMarkerState = rememberMarkerState(position = destinationLatLng)

    LaunchedEffect(pickupLatLng) {
        pickupMarkerState.position = pickupLatLng
    }
    LaunchedEffect(destinationLatLng) {
        destMarkerState.position = destinationLatLng
    }

    // Default camera center roughly midway between Serrekunda and Banjul
    val initialCameraPosition = remember {
        CameraPosition.fromLatLngZoom(pickupLatLng, 13.5f)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = initialCameraPosition
    }

    // Permission launcher for Fine & Coarse location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        showPermissionRationale = !granted
        if (granted) {
            isLocatingUser = true
            DriverFusedLocationTracker.requestOneShotLocation(context) { loc ->
                isLocatingUser = false
                if (loc != null) {
                    val userLatLng = LatLng(loc.latitude, loc.longitude)
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                    }
                    if (targetMode == MapTargetMode.PICKUP) {
                        onPickupChanged(
                            RideLocation(
                                name = "My Current Location",
                                address = String.format("%.4f°N, %.4f°W", loc.latitude, -loc.longitude),
                                latitude = loc.latitude,
                                longitude = loc.longitude
                            )
                        )
                    } else {
                        onDestinationChanged(
                            RideLocation(
                                name = "Selected GPS Destination",
                                address = String.format("%.4f°N, %.4f°W", loc.latitude, -loc.longitude),
                                latitude = loc.latitude,
                                longitude = loc.longitude
                            )
                        )
                    }
                }
            }
        }
    }

    // Calculate distance and route estimates
    val routeDistanceKm = remember(pickupLatLng, destinationLatLng) {
        calculateHaversineDistanceKm(
            pickupLatLng.latitude, pickupLatLng.longitude,
            destinationLatLng.latitude, destinationLatLng.longitude
        )
    }
    val estimatedMinutes = remember(routeDistanceKm) {
        max(3, (routeDistanceKm * 2.5).roundToInt())
    }
    val estimatedFareGmd = remember(routeDistanceKm) {
        (100 + (routeDistanceKm * 35)).roundToInt().coerceAtLeast(150)
    }

    // UI Configuration for Google Maps SDK
    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = true,
            rotationGesturesEnabled = true,
            tiltGesturesEnabled = true
        )
    }
    val mapProperties = remember(mapType) {
        MapProperties(
            mapType = mapType,
            isMyLocationEnabled = LocationUtils.hasLocationPermission(context)
        )
    }

    Box(modifier = modifier.fillMaxSize().testTag("google_map_container")) {
        // --- 1. GOOGLE MAP COMPOSABLE ---
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings,
            onMapClick = { clickedLatLng ->
                if (!isSelectionEnabled) return@GoogleMap
                if (targetMode == MapTargetMode.PICKUP) {
                    val resolvedName = findClosestLandmarkName(clickedLatLng) ?: "Custom Pickup Spot"
                    onPickupChanged(
                        RideLocation(
                            name = resolvedName,
                            address = String.format("%.4f°N, %.4f°W", clickedLatLng.latitude, -clickedLatLng.longitude),
                            latitude = clickedLatLng.latitude,
                            longitude = clickedLatLng.longitude
                        )
                    )
                } else {
                    val resolvedName = findClosestLandmarkName(clickedLatLng) ?: "Custom Destination Spot"
                    onDestinationChanged(
                        RideLocation(
                            name = resolvedName,
                            address = String.format("%.4f°N, %.4f°W", clickedLatLng.latitude, -clickedLatLng.longitude),
                            latitude = clickedLatLng.latitude,
                            longitude = clickedLatLng.longitude
                        )
                    )
                }
            }
        ) {
            // PICKUP MARKER (Green Hue)
            Marker(
                state = pickupMarkerState,
                title = "Pickup Location",
                snippet = pickupLocation.name.ifBlank { "Pickup Point" },
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                draggable = isSelectionEnabled,
                onClick = {
                    targetMode = MapTargetMode.PICKUP
                    false
                }
            )

            // DESTINATION MARKER (Red/Orange Hue)
            Marker(
                state = destMarkerState,
                title = "Destination",
                snippet = destinationLocation.name.ifBlank { "Dropoff Destination" },
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                draggable = isSelectionEnabled,
                onClick = {
                    targetMode = MapTargetMode.DESTINATION
                    false
                }
            )

            // DRIVER LOCATION MARKER (Azure Hue if available)
            if (driverLatLng != null) {
                Marker(
                    state = rememberMarkerState(position = driverLatLng),
                    title = "Assigned Driver",
                    snippet = "En route in live GPS coordinates",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // ROUTE POLYLINE (Primary Theme Color)
            Polyline(
                points = listOf(pickupLatLng, destinationLatLng),
                color = MaterialTheme.colorScheme.primary,
                width = 12f,
                geodesic = true
            )
        }

        // --- 2. TOP TARGET SELECTOR & MAP TYPE HUD ---
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Permission Banner (if not granted)
            AnimatedVisibility(
                visible = showPermissionRationale,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enable GPS for accurate pickup detection & driver telematics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Enable GPS", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Selection Mode Tabs (Pickup vs Destination)
            if (isSelectionEnabled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pickup Selection Button
                        Button(
                            onClick = {
                                targetMode = MapTargetMode.PICKUP
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 14f))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetMode == MapTargetMode.PICKUP)
                                    Color(0xFF2E7D32)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (targetMode == MapTargetMode.PICKUP)
                                    Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("select_pickup_mode_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TripOrigin,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "📍 Pickup",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (targetMode == MapTargetMode.PICKUP) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        // Destination Selection Button
                        Button(
                            onClick = {
                                targetMode = MapTargetMode.DESTINATION
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 14f))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetMode == MapTargetMode.DESTINATION)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (targetMode == MapTargetMode.DESTINATION)
                                    Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("select_dest_mode_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🏁 Destination",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (targetMode == MapTargetMode.DESTINATION) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Quick Landmark Carousel
            if (isSelectionEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    POPULAR_GAMBIA_LANDMARKS.forEach { landmark ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shadowElevation = 3.dp,
                            modifier = Modifier.clickable {
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(landmark.latLng, 15f)
                                    )
                                }
                                val loc = RideLocation(
                                    name = landmark.name,
                                    address = landmark.description,
                                    latitude = landmark.latLng.latitude,
                                    longitude = landmark.latLng.longitude
                                )
                                if (targetMode == MapTargetMode.PICKUP) {
                                    onPickupChanged(loc)
                                } else {
                                    onDestinationChanged(loc)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (targetMode == MapTargetMode.PICKUP) "📍" else "🏁",
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = landmark.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. FLOATING ACTION CONTROLS (My Location & Map Type) ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // My Location Button
            FloatingActionButton(
                onClick = {
                    if (LocationUtils.hasLocationPermission(context)) {
                        isLocatingUser = true
                        DriverFusedLocationTracker.requestOneShotLocation(context) { loc ->
                            isLocatingUser = false
                            if (loc != null) {
                                val userLatLng = LatLng(loc.latitude, loc.longitude)
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(userLatLng, 15.5f))
                                }
                                onPickupChanged(
                                    RideLocation(
                                        name = "My Current Location",
                                        address = String.format("%.4f°N, %.4f°W", loc.latitude, -loc.longitude),
                                        latitude = loc.latitude,
                                        longitude = loc.longitude
                                    )
                                )
                            }
                        }
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(46.dp).testTag("my_location_gps_btn")
            ) {
                if (isLocatingUser) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Center on Current GPS Location",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Map Layer Type Toggle (Normal / Satellite)
            FloatingActionButton(
                onClick = {
                    mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(46.dp).testTag("map_layer_toggle_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Toggle Map Layers",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Fit Route in View Button
            FloatingActionButton(
                onClick = {
                    val midLat = (pickupLatLng.latitude + destinationLatLng.latitude) / 2
                    val midLng = (pickupLatLng.longitude + destinationLatLng.longitude) / 2
                    val zoom = when {
                        routeDistanceKm > 20 -> 10.5f
                        routeDistanceKm > 10 -> 12f
                        routeDistanceKm > 5 -> 13f
                        else -> 14f
                    }
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(midLat, midLng), zoom))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(46.dp).testTag("fit_route_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOutMap,
                    contentDescription = "Fit Route in View",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // --- 4. BOTTOM ROUTE & FARE ESTIMATION HUD ---
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🟢", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pickupLocation.name.ifBlank { "Select Pickup" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔴", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = destinationLocation.name.ifBlank { "Select Destination" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Route Stats Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${String.format("%.1f", routeDistanceKm)} km",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "~$estimatedMinutes mins • $estimatedFareGmd GMD",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calculates distance in kilometers between two GPS coordinates using the Haversine formula.
 */
private fun calculateHaversineDistanceKm(
    lat1: Double, lng1: Double,
    lat2: Double, lng2: Double
): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
}

/**
 * Finds the nearest designated landmark name within 500 meters of a coordinate.
 */
private fun findClosestLandmarkName(latLng: LatLng): String? {
    val closest = POPULAR_GAMBIA_LANDMARKS.minByOrNull {
        calculateHaversineDistanceKm(latLng.latitude, latLng.longitude, it.latLng.latitude, it.latLng.longitude)
    } ?: return null

    val dist = calculateHaversineDistanceKm(latLng.latitude, latLng.longitude, closest.latLng.latitude, closest.latLng.longitude)
    return if (dist <= 0.6) closest.name else null
}
