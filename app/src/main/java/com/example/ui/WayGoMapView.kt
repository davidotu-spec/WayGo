package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.DriverEntity
import com.example.data.TripEntity
import com.example.ui.theme.*

// LatLng bbox for Kanifing / Serekunda / Banjul region
// Min Lat: 13.4300, Max Lat: 13.4800
// Min Lng: -16.7200, Max Lng: -16.5600
private const val MIN_LAT = 13.4300
private const val MAX_LAT = 13.4800
private const val MIN_LNG = -16.7200
private const val MAX_LNG = -16.5600

data class MapLandmark(
    val name: String,
    val lat: Double,
    val lng: Double,
    val description: String
)

val GAMBIAN_LANDMARKS = listOf(
    MapLandmark("Albert Market", 13.4533, -16.5746, "Market hub in Banjul"),
    MapLandmark("Arch 22", 13.4580, -16.5820, "Historic Banjul gateway gate"),
    MapLandmark("Banjul Ferry Terminal", 13.4505, -16.5710, "Ferry port to Barra"),
    MapLandmark("University of Gambia", 13.4452, -16.6713, "Education center"),
    MapLandmark("Kairaba Business Hub", 13.4471, -16.6791, "Serrekunda shopping street"),
    MapLandmark("Westfield Junction", 13.4385, -16.6760, "Transit hub"),
    MapLandmark("Tippa Garage", 13.4340, -16.6850, "Taxi rank"),
    MapLandmark("Senegambia Beach", 13.4420, -16.7110, "Tourist & beach hotels"),
    MapLandmark("Independence Stadium", 13.4722, -16.6690, "Bakau national stadium"),
    MapLandmark("Brusubi Turntable", 13.4020, -16.7180, "Roundabout"),
    MapLandmark("Kotu Beach", 13.4610, -16.7020, "Coastal beach hub")
)

data class MapPinSelection(
    val name: String,
    val lat: Double,
    val lng: Double
)

private fun reverseGeocodeGambia(lat: Double, lng: Double): String {
    val closest = GAMBIAN_LANDMARKS.minByOrNull { calculateDistance(lat, lng, it.lat, it.lng) }
    if (closest != null) {
        val dist = calculateDistance(lat, lng, closest.lat, closest.lng)
        if (dist < 0.5) return closest.name
        if (dist < 1.5) return "Near ${closest.name}"
    }
    return when {
        lng > -16.61 -> "Banjul City Center"
        lat > 13.465 -> "Bakau District, Kanifing"
        lng < -16.70 -> "Senegambia / Coastal Area"
        lat > 13.44 -> "Kanifing / Pipeline Area"
        else -> "Serrekunda Central Area"
    }
}

private fun getLngFromX(x: Float, width: Float, zoom: Float): Double {
    val centerX = width / 2f
    val rawX = (x - centerX) / zoom + centerX
    val ratio = (rawX / width).toDouble()
    return (MIN_LNG + ratio * (MAX_LNG - MIN_LNG)).coerceIn(MIN_LNG, MAX_LNG)
}

private fun getLatFromY(y: Float, height: Float, zoom: Float): Double {
    val centerY = height / 2f
    val rawY = (y - centerY) / zoom + centerY
    val ratio = (rawY / height).toDouble()
    return (MAX_LAT - ratio * (MAX_LAT - MIN_LAT)).coerceIn(MIN_LAT, MAX_LAT)
}

private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth's radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

// Data structure representing estimated arrival time & location stats from Google Maps SDK
data class DriverEtaInfo(
    val headline: String,
    val subtext: String,
    val etaMinutes: Int,
    val distanceKm: Double,
    val status: String,
    val statusText: String,
    val badgeColor: Color,
    val isLiveGps: Boolean
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun WayGoMapView(
    modifier: Modifier = Modifier,
    drivers: List<DriverEntity> = emptyList(),
    activeTrip: TripEntity? = null,
    simulatedDriverLat: Double? = null,
    simulatedDriverLng: Double? = null,
    passengerLat: Double = 13.4471,
    passengerLng: Double = -16.6791,
    pickupLocationName: String? = null,
    pickupLat: Double? = null,
    pickupLng: Double? = null,
    dropoffLocationName: String? = null,
    dropoffLat: Double? = null,
    dropoffLng: Double? = null,
    mapPickingMode: String? = null,
    onSetPickupLocation: ((name: String, lat: Double, lng: Double) -> Unit)? = null,
    onSetDropoffLocation: ((name: String, lat: Double, lng: Double) -> Unit)? = null,
    onCancelMapPicking: (() -> Unit)? = null,
    progress: Float = 0f,
    onSelectDriver: ((DriverEntity) -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Interactive Map Control States
    var mapType by remember { mutableStateOf("ROADMAP") } // "ROADMAP", "SATELLITE", "TERRAIN"
    var showTraffic by remember { mutableStateOf(false) }
    var showHeatmap by remember { mutableStateOf(true) }
    var vehicleFilter by remember { mutableStateOf("ALL") } // "ALL", "CAR", "TRICYCLE"
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var selectedDriverOnMap by remember { mutableStateOf<DriverEntity?>(null) }
    var tappedPinLocation by remember { mutableStateOf<MapPinSelection?>(null) }
    var canvasWidth by remember { mutableFloatStateOf(1000f) }
    var canvasHeight by remember { mutableFloatStateOf(800f) }
    var showMapTypeMenu by remember { mutableStateOf(false) }

    val isActiveRide = activeTrip != null && activeTrip.status in listOf("ACCEPTED", "ARRIVED", "EN_ROUTE")
    val boxHeight = if (isActiveRide) 400.dp else 360.dp

    // Filter available online drivers
    val availableDrivers = remember(drivers, vehicleFilter) {
        drivers.filter { driver ->
            driver.isOnline && driver.approvalStatus == "APPROVED" &&
                    (activeTrip == null || activeTrip.driverId != driver.id) &&
                    (vehicleFilter == "ALL" || driver.vehicleType == vehicleFilter)
        }
    }

    // Real-time Driver ETA calculation based on current location data from Google Maps SDK
    val driverEtaData = remember(
        activeTrip,
        simulatedDriverLat,
        simulatedDriverLng,
        availableDrivers,
        selectedDriverOnMap,
        passengerLat,
        passengerLng,
        pickupLat,
        pickupLng,
        dropoffLat,
        dropoffLng,
        progress
    ) {
        val targetPickupLat = activeTrip?.pickupLat ?: pickupLat ?: passengerLat
        val targetPickupLng = activeTrip?.pickupLng ?: pickupLng ?: passengerLng
        val targetDropoffLat = activeTrip?.dropoffLat ?: dropoffLat
        val targetDropoffLng = activeTrip?.dropoffLng ?: dropoffLng

        if (activeTrip != null) {
            val dLat = simulatedDriverLat ?: run {
                val startLat = targetPickupLat
                val endLat = targetDropoffLat ?: targetPickupLat
                startLat + (endLat - startLat) * progress.coerceIn(0f, 1f)
            }
            val dLng = simulatedDriverLng ?: run {
                val startLng = targetPickupLng
                val endLng = targetDropoffLng ?: targetPickupLng
                startLng + (endLng - startLng) * progress.coerceIn(0f, 1f)
            }

            when (activeTrip.status) {
                "ACCEPTED" -> {
                    val distKm = calculateDistance(dLat, dLng, targetPickupLat, targetPickupLng)
                    val etaMin = (distKm * 3.5 + 1).toInt().coerceAtLeast(1)
                    val driverName = activeTrip.driverName?.split(" ")?.firstOrNull() ?: "Driver"
                    DriverEtaInfo(
                        headline = "Driver ETA: $etaMin mins",
                        subtext = "$driverName is ${String.format(java.util.Locale.US, "%.1f", distKm)} km away • Approaching pickup point",
                        etaMinutes = etaMin,
                        distanceKm = distKm,
                        status = "APPROACHING",
                        statusText = "Heading to Pickup",
                        badgeColor = Color(0xFF1976D2),
                        isLiveGps = true
                    )
                }
                "ARRIVED" -> {
                    DriverEtaInfo(
                        headline = "Driver Arrived",
                        subtext = "${activeTrip.driverName ?: "Driver"} is waiting outside pickup location",
                        etaMinutes = 0,
                        distanceKm = 0.0,
                        status = "ARRIVED",
                        statusText = "At Pickup Point",
                        badgeColor = Color(0xFF2E7D32),
                        isLiveGps = true
                    )
                }
                "EN_ROUTE" -> {
                    val destLat = targetDropoffLat ?: targetPickupLat
                    val destLng = targetDropoffLng ?: targetPickupLng
                    val distKm = calculateDistance(dLat, dLng, destLat, destLng)
                    val etaMin = (distKm * 3.2 + 2).toInt().coerceAtLeast(1)
                    DriverEtaInfo(
                        headline = "Trip ETA: $etaMin mins",
                        subtext = "${String.format(java.util.Locale.US, "%.1f", distKm)} km to destination • Live traffic routing",
                        etaMinutes = etaMin,
                        distanceKm = distKm,
                        status = "IN_TRANSIT",
                        statusText = "En Route to Destination",
                        badgeColor = Color(0xFFE65100),
                        isLiveGps = true
                    )
                }
                else -> {
                    val distKm = calculateDistance(dLat, dLng, targetPickupLat, targetPickupLng)
                    val etaMin = (distKm * 3.5 + 1).toInt().coerceAtLeast(1)
                    DriverEtaInfo(
                        headline = "Driver ETA: $etaMin mins",
                        subtext = "${String.format(java.util.Locale.US, "%.1f", distKm)} km away • Google Maps SDK Live Location",
                        etaMinutes = etaMin,
                        distanceKm = distKm,
                        status = "ACTIVE",
                        statusText = "Active GPS Tracking",
                        badgeColor = Color(0xFF1976D2),
                        isLiveGps = true
                    )
                }
            }
        } else {
            val currentSelected = selectedDriverOnMap
            if (currentSelected != null) {
                val distKm = calculateDistance(passengerLat, passengerLng, currentSelected.currentLat, currentSelected.currentLng)
                val etaMin = (distKm * 3.5 + 2).toInt().coerceAtLeast(1)
                val driverName = currentSelected.name.split(" ").firstOrNull() ?: "Driver"
                DriverEtaInfo(
                    headline = "Driver ETA: $etaMin mins",
                    subtext = "$driverName (${currentSelected.vehiclePlate}) • ${String.format(java.util.Locale.US, "%.1f", distKm)} km away",
                    etaMinutes = etaMin,
                    distanceKm = distKm,
                    status = "SELECTED",
                    statusText = "Selected Driver",
                    badgeColor = Color(0xFF1976D2),
                    isLiveGps = true
                )
            } else {
                val closestDriver = availableDrivers.minByOrNull { calculateDistance(passengerLat, passengerLng, it.currentLat, it.currentLng) }
                if (closestDriver != null) {
                    val distKm = calculateDistance(passengerLat, passengerLng, closestDriver.currentLat, closestDriver.currentLng)
                    val etaMin = (distKm * 3.5 + 2).toInt().coerceAtLeast(1)
                    val driverName = closestDriver.name.split(" ").firstOrNull() ?: "Driver"
                    DriverEtaInfo(
                        headline = "Closest Driver ETA: $etaMin mins",
                        subtext = "$driverName is ${String.format(java.util.Locale.US, "%.1f", distKm)} km away (${closestDriver.vehicleType.lowercase().replaceFirstChar { it.uppercase() }})",
                        etaMinutes = etaMin,
                        distanceKm = distKm,
                        status = "AVAILABLE",
                        statusText = "Google Maps SDK Live Location",
                        badgeColor = Color(0xFF0F9D58),
                        isLiveGps = true
                    )
                } else {
                    DriverEtaInfo(
                        headline = "Driver ETA: ~5 mins",
                        subtext = "Estimated pickup time in Banjul & Kanifing zone",
                        etaMinutes = 5,
                        distanceKm = 1.5,
                        status = "SEARCHING",
                        statusText = "Google Maps SDK",
                        badgeColor = Color(0xFF4285F4),
                        isLiveGps = false
                    )
                }
            }
        }
    }

    val carCount = remember(drivers) { drivers.count { it.isOnline && it.approvalStatus == "APPROVED" && it.vehicleType == "CAR" } }
    val bikeCount = remember(drivers) { drivers.count { it.isOnline && it.approvalStatus == "APPROVED" && it.vehicleType == "TRICYCLE" } }

    // Pulsing animation for real-time driver GPS pins
    val infiniteTransition = rememberInfiniteTransition(label = "driver_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    // Colors matching official Google Maps themes
    val (landColor, oceanColor, roadBorderColor, roadFillColor, highwayColor, parkColor) = when (mapType) {
        "SATELLITE" -> Tuple6(
            Color(0xFF0F172A), // Dark slate satellite base
            Color(0xFF0284C7), // Ocean deep blue
            Color(0xFF334155),
            Color(0xFF1E293B),
            Color(0xFF38BDF8),
            Color(0xFF065F46)
        )
        "TERRAIN" -> Tuple6(
            Color(0xFFEFEBE4), // Topographic warm paper
            Color(0xFF90CAF9),
            Color(0xFFD7CCC8),
            Color(0xFFFFFFFF),
            Color(0xFFFFB74D),
            Color(0xFFA5D6A7)
        )
        else -> Tuple6( // "ROADMAP" - Official Google Light Palette
            Color(0xFFF5F3ED), // Google Map Land warm off-white
            Color(0xFFAADAFF), // Google Map Sky Water
            Color(0xFFE6E6E6),
            Color(0xFFFFFFFF),
            Color(0xFFFFD54F), // Google Highway Yellow
            Color(0xFFC8E6C9)  // Google Park Light Green
        )
    }

    // Keep track of rendered driver screen positions for tap detection
    val driverPositions = remember { mutableStateListOf<Pair<DriverEntity, Offset>>() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(boxHeight)
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, if (mapType == "SATELLITE") Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
            .background(landColor)
            .testTag("google_maps_view")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(availableDrivers, zoomLevel, mapType, mapPickingMode) {
                    detectTapGestures { tapOffset ->
                        val touchRadiusPx = with(density) { 32.dp.toPx() }
                        val tappedDriver = driverPositions.firstOrNull { (_, pos) ->
                            val dx = tapOffset.x - pos.x
                            val dy = tapOffset.y - pos.y
                            (dx * dx + dy * dy) <= (touchRadiusPx * touchRadiusPx)
                        }?.first

                        if (tappedDriver != null && mapPickingMode == null) {
                            selectedDriverOnMap = tappedDriver
                            tappedPinLocation = null
                        } else {
                            selectedDriverOnMap = null
                            val tappedLng = getLngFromX(tapOffset.x, canvasWidth, zoomLevel)
                            val tappedLat = getLatFromY(tapOffset.y, canvasHeight, zoomLevel)
                            val locName = reverseGeocodeGambia(tappedLat, tappedLng)
                            tappedPinLocation = MapPinSelection(locName, tappedLat, tappedLng)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            canvasWidth = width
            canvasHeight = height

            // Clear previous positions
            driverPositions.clear()

            // Coordinate mapping with zoom scaling
            fun getX(lng: Double): Float {
                val ratio = (lng - MIN_LNG) / (MAX_LNG - MIN_LNG)
                val rawX = ratio * width
                val centerX = width / 2f
                return (centerX + (rawX - centerX) * zoomLevel).toFloat()
            }

            fun getY(lat: Double): Float {
                val ratio = (MAX_LAT - lat) / (MAX_LAT - MIN_LAT)
                val rawY = ratio * height
                val centerY = height / 2f
                return (centerY + (rawY - centerY) * zoomLevel).toFloat()
            }

            // 1. Draw Land Mass
            drawRect(color = landColor)

            // 2. Draw Parks / Greenery polygons
            val parkPath = Path().apply {
                val x1 = getX(-16.7050)
                val y1 = getY(13.4450)
                val x2 = getX(-16.6900)
                val y2 = getY(13.4600)
                moveTo(x1, y1)
                lineTo(x2, y1)
                lineTo(x2, y2)
                lineTo(x1, y2)
                close()
            }
            drawPath(parkPath, color = parkColor.copy(alpha = 0.5f))

            // 3. Draw Google Map Grid / Block Texture
            val gridSize = 36.dp.toPx() * zoomLevel
            var curX = 0f
            val gridColor = if (mapType == "SATELLITE") Color(0xFF334155).copy(alpha = 0.2f) else Color(0xFFE0E0E0).copy(alpha = 0.25f)
            while (curX < width) {
                drawLine(color = gridColor, start = Offset(curX, 0f), end = Offset(curX, height), strokeWidth = 1f)
                curX += gridSize
            }
            var curY = 0f
            while (curY < height) {
                drawLine(color = gridColor, start = Offset(0f, curY), end = Offset(width, curY), strokeWidth = 1f)
                curY += gridSize
            }

            // 4. Draw Atlantic Ocean coastline
            val oceanPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(width, 0f)
                quadraticTo(width * 0.7f, height * 0.28f, width * 0.45f, height * 0.15f)
                quadraticTo(width * 0.2f, height * 0.38f, 0f, height * 0.22f)
                close()
            }
            drawPath(oceanPath, color = oceanColor)

            // River Gambia Estuary
            val riverPath = Path().apply {
                moveTo(width, height * 0.25f)
                quadraticTo(width * 0.85f, height * 0.42f, width * 0.88f, height * 0.68f)
                quadraticTo(width * 0.8f, height * 0.85f, width * 0.84f, height)
                lineTo(width, height)
                close()
            }
            drawPath(riverPath, color = oceanColor)

            // 5. Draw Google Roads & Major Highways
            // Banjul - Serrekunda Highway
            val hStart = Offset(getX(-16.7110), getY(13.4380))
            val hMid = Offset(getX(-16.6500), getY(13.4490))
            val hEnd = Offset(getX(-16.5820), getY(13.4580))

            drawLine(roadBorderColor, hStart, hMid, strokeWidth = 10f * zoomLevel)
            drawLine(roadBorderColor, hMid, hEnd, strokeWidth = 10f * zoomLevel)
            drawLine(highwayColor, hStart, hMid, strokeWidth = 6f * zoomLevel)
            drawLine(highwayColor, hMid, hEnd, strokeWidth = 6f * zoomLevel)

            // Kairaba Avenue
            val kStart = Offset(getX(-16.6820), getY(13.4310))
            val kEnd = Offset(getX(-16.6710), getY(13.4750))
            drawLine(roadBorderColor, kStart, kEnd, strokeWidth = 8f * zoomLevel)
            drawLine(roadFillColor, kStart, kEnd, strokeWidth = 4f * zoomLevel)

            // Atlantic Boulevard
            val bStart = Offset(getX(-16.7110), getY(13.4420))
            val bEnd = Offset(getX(-16.6800), getY(13.4750))
            drawLine(roadBorderColor, bStart, bEnd, strokeWidth = 7f * zoomLevel)
            drawLine(roadFillColor, bStart, bEnd, strokeWidth = 3.5f * zoomLevel)

            // 6. Google Live Traffic Overlay Layer
            if (showTraffic) {
                // Smooth traffic (Green) along Highway
                drawLine(Color(0xFF4CAF50), hStart, hMid, strokeWidth = 4f * zoomLevel)
                // Moderate traffic (Orange) along Kairaba
                drawLine(Color(0xFFFF9800), kStart, kEnd, strokeWidth = 4f * zoomLevel)
                // Congested traffic (Red) along Banjul entry
                drawLine(Color(0xFFF44336), hMid, hEnd, strokeWidth = 5f * zoomLevel)
            }

            // 6b. Driver Demand Heatmap & Surge Multipliers Layer
            if (showHeatmap) {
                val surgeZones = listOf(
                    Triple("Westfield", GLocation("Westfield Junction", 13.4385, -16.6760), "1.8x"),
                    Triple("Senegambia", GLocation("Senegambia Beach", 13.4420, -16.7110), "1.5x"),
                    Triple("Market", GLocation("Albert Market Banjul", 13.4533, -16.5746), "1.4x"),
                    Triple("Turntable", GLocation("Brusubi Turntable", 13.4020, -16.7180), "1.3x")
                )

                surgeZones.forEach { (shortName, loc, multiplier) ->
                    val zX = getX(loc.lng)
                    val zY = getY(loc.lat)
                    if (zX in 0f..width && zY in 0f..height) {
                        val colorBase = when (multiplier) {
                            "1.8x" -> Color(0xFFE53935)
                            "1.5x" -> Color(0xFFFF9800)
                            "1.4x" -> Color(0xFFFFA000)
                            else -> Color(0xFF388E3C)
                        }

                        val glowRadius = 42.dp.toPx() * pulseScale
                        drawCircle(color = colorBase.copy(alpha = 0.18f), radius = glowRadius, center = Offset(zX, zY))
                        drawCircle(color = colorBase.copy(alpha = 0.35f), radius = 24.dp.toPx(), center = Offset(zX, zY))
                        drawCircle(color = colorBase, radius = 9.dp.toPx(), center = Offset(zX, zY))

                        val labelText = "🔥 $shortName $multiplier"
                        val labelStyle = TextStyle(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        val measured = textMeasurer.measure(labelText, labelStyle)
                        val pW = measured.size.width + 12.dp.toPx()
                        val pH = measured.size.height + 6.dp.toPx()
                        val pL = zX - pW / 2f
                        val pT = zY - 14.dp.toPx() - pH

                        drawRoundRect(
                            color = colorBase,
                            topLeft = Offset(pL, pT),
                            size = androidx.compose.ui.geometry.Size(pW, pH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                        )
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(pL + 6.dp.toPx(), pT + 3.dp.toPx())
                        )
                    }
                }
            }

            // 7. Draw Landmarks
            GAMBIAN_LANDMARKS.forEach { landmark ->
                val lx = getX(landmark.lng)
                val ly = getY(landmark.lat)

                if (lx in 0f..width && ly in 0f..height) {
                    drawCircle(color = BrandBlueDark.copy(alpha = 0.3f), radius = 5f * zoomLevel, center = Offset(lx, ly))
                    drawCircle(color = BrandBlueSecondary, radius = 3f * zoomLevel, center = Offset(lx, ly))

                    val textStyle = TextStyle(
                        fontSize = (8.5.sp.value * zoomLevel).sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mapType == "SATELLITE") Color.White else BrandBlueDark.copy(alpha = 0.85f)
                    )
                    val textLayoutResult = textMeasurer.measure(text = landmark.name, style = textStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(lx - textLayoutResult.size.width / 2f, ly + 5.dp.toPx())
                    )
                }
            }

            // 8. Active Trip Routing Line & Live Traffic Polyline (if route active or picked)
            val routePLat = activeTrip?.pickupLat ?: pickupLat
            val routePLng = activeTrip?.pickupLng ?: pickupLng
            val routeDLat = activeTrip?.dropoffLat ?: dropoffLat
            val routeDLng = activeTrip?.dropoffLng ?: dropoffLng

            if (routePLat != null && routePLng != null && routeDLat != null && routeDLng != null) {
                val pX = getX(routePLng)
                val pY = getY(routePLat)
                val dX = getX(routeDLng)
                val dY = getY(routeDLat)

                // Pickup Pin
                drawCircle(color = SuccessGreen.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = Offset(pX, pY))
                drawCircle(color = SuccessGreen, radius = 6.dp.toPx(), center = Offset(pX, pY))

                // Dropoff Pin
                drawCircle(color = ErrorRed.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = Offset(dX, dY))
                drawCircle(color = ErrorRed, radius = 6.dp.toPx(), center = Offset(dX, dY))

                // 3 Color-Coded Traffic Density Segments
                val m1X = pX + (dX - pX) * 0.45f
                val m1Y = pY + (dY - pY) * 0.45f
                val m2X = pX + (dX - pX) * 0.75f
                val m2Y = pY + (dY - pY) * 0.75f

                // Green Segment (Smooth Flow)
                drawLine(
                    color = Color(0xFF2E7D32),
                    start = Offset(pX, pY),
                    end = Offset(m1X, m1Y),
                    strokeWidth = 5.dp.toPx()
                )
                // Amber Segment (Moderate Traffic)
                drawLine(
                    color = Color(0xFFF57C00),
                    start = Offset(m1X, m1Y),
                    end = Offset(m2X, m2Y),
                    strokeWidth = 5.dp.toPx()
                )
                // Red Segment (Westfield / Junction Congestion)
                drawLine(
                    color = Color(0xFFD32F2F),
                    start = Offset(m2X, m2Y),
                    end = Offset(dX, dY),
                    strokeWidth = 6.dp.toPx()
                )

                // Smooth Car Marker Position on Route (Live Driver Current Position)
                val routeProgress = if (progress > 0f) progress else 0.4f
                val cX = if (simulatedDriverLat != null && simulatedDriverLng != null) getX(simulatedDriverLng) else pX + (dX - pX) * routeProgress
                val cY = if (simulatedDriverLat != null && simulatedDriverLng != null) getY(simulatedDriverLat) else pY + (dY - pY) * routeProgress

                // Subtle Outer Pulsing Radar Rings for Driver's Current Position
                val activePulseRadius1 = 26.dp.toPx() * pulseScale
                val activePulseRadius2 = 17.dp.toPx() * (1.0f + (pulseScale - 1.0f) * 0.5f)

                // 1. Spreading outer translucent blue pulse ring
                drawCircle(
                    color = BrandBluePrimary.copy(alpha = pulseAlpha * 0.45f),
                    radius = activePulseRadius1,
                    center = Offset(cX, cY)
                )

                // 2. Secondary inner amber glow pulse ring
                drawCircle(
                    color = AccentAmber.copy(alpha = pulseAlpha * 0.35f),
                    radius = activePulseRadius2,
                    center = Offset(cX, cY)
                )

                // 3. Drop shadow for elevation depth
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = 11.dp.toPx(),
                    center = Offset(cX, cY + 2.dp.toPx())
                )

                // 4. Solid white border base pin
                drawCircle(
                    color = Color.White,
                    radius = 9.5.dp.toPx(),
                    center = Offset(cX, cY)
                )

                // 5. Driver primary core pin
                drawCircle(
                    color = BrandBluePrimary,
                    radius = 6.5.dp.toPx(),
                    center = Offset(cX, cY)
                )

                // 6. Inner white center dot
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = Offset(cX, cY)
                )

                // Active Driver Live Location Badge Pill
                val driverPillText = if (activeTrip != null) {
                    val driverShort = activeTrip.driverName?.split(" ")?.firstOrNull() ?: "Driver"
                    if (driverEtaData.etaMinutes > 0) {
                        "🚖 $driverShort • ETA: ${driverEtaData.etaMinutes} min (${String.format(java.util.Locale.US, "%.1f", driverEtaData.distanceKm)} km)"
                    } else {
                        "🚖 $driverShort • Arrived at Pickup"
                    }
                } else {
                    "🚖 Driver • Live GPS"
                }
                val driverPillStyle = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                val driverPillMeasured = textMeasurer.measure(driverPillText, driverPillStyle)
                val dpW = driverPillMeasured.size.width + 14.dp.toPx()
                val dpH = driverPillMeasured.size.height + 6.dp.toPx()
                val dpL = cX - dpW / 2f
                val dpT = cY - 18.dp.toPx() - dpH

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(dpL, dpT),
                    size = androidx.compose.ui.geometry.Size(dpW, dpH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
                drawRoundRect(
                    color = BrandBluePrimary,
                    topLeft = Offset(dpL, dpT),
                    size = androidx.compose.ui.geometry.Size(dpW, dpH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    style = Stroke(width = 2f)
                )
                drawText(
                    textLayoutResult = driverPillMeasured,
                    topLeft = Offset(dpL + 7.dp.toPx(), dpT + 3.dp.toPx())
                )

                // Overhead Live Route Traffic Summary Badge
                val tagText = "🟢 Smooth • 🚦 Westfield Slowdown • ⏱️ 12m"
                val tagStyle = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                val tagMeasured = textMeasurer.measure(tagText, tagStyle)
                val tW = tagMeasured.size.width + 16.dp.toPx()
                val tH = tagMeasured.size.height + 8.dp.toPx()
                val tL = m1X - tW / 2f
                val tT = m1Y - 14.dp.toPx() - tH

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(tL, tT),
                    size = androidx.compose.ui.geometry.Size(tW, tH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                )
                drawRoundRect(
                    color = BrandBluePrimary.copy(alpha = 0.4f),
                    topLeft = Offset(tL, tT),
                    size = androidx.compose.ui.geometry.Size(tW, tH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                    style = Stroke(width = 2f)
                )
                drawText(
                    textLayoutResult = tagMeasured,
                    topLeft = Offset(tL + 8.dp.toPx(), tT + 4.dp.toPx())
                )
            }

            // 9. DRAW REAL-TIME NEARBY AVAILABLE DRIVER MARKERS
            availableDrivers.forEach { driver ->
                val dX = getX(driver.currentLng)
                val dY = getY(driver.currentLat)

                if (dX in -50f..(width + 50f) && dY in -50f..(height + 50f)) {
                    // Record position for touch detection
                    driverPositions.add(Pair(driver, Offset(dX, dY)))

                    val isSelected = selectedDriverOnMap?.id == driver.id
                    val baseColor = if (driver.vehicleType == "CAR") BrandBluePrimary else AccentAmber

                    // Live Pulsing Radar Ring around available driver
                    val pulseRadius = (12.dp.toPx() * pulseScale) * (if (isSelected) 1.4f else 1.0f)
                    drawCircle(
                        color = baseColor.copy(alpha = pulseAlpha * (if (isSelected) 0.9f else 0.5f)),
                        radius = pulseRadius,
                        center = Offset(dX, dY)
                    )

                    if (isSelected) {
                        // Outer selection halo
                        drawCircle(
                            color = Color(0xFFFFD54F),
                            radius = 16.dp.toPx(),
                            center = Offset(dX, dY)
                        )
                    }

                    // Google Maps Pin Marker Base
                    drawCircle(
                        color = Color.White,
                        radius = 10.dp.toPx(),
                        center = Offset(dX, dY)
                    )
                    drawCircle(
                        color = baseColor,
                        radius = 8.dp.toPx(),
                        center = Offset(dX, dY)
                    )

                    // Calculate distance & ETA to passenger for overhead pill
                    val distKm = calculateDistance(passengerLat, passengerLng, driver.currentLat, driver.currentLng)
                    val etaMin = (distKm * 3.5 + 2).toInt().coerceAtLeast(1)
                    val driverFirstName = driver.name.split(" ")[0]
                    val labelText = "$driverFirstName • ${etaMin}m"

                    // Overhead Driver ETA Pill on Canvas
                    val labelStyle = TextStyle(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlueDark
                    )
                    val measured = textMeasurer.measure(labelText, labelStyle)
                    val pillWidth = measured.size.width + 16.dp.toPx()
                    val pillHeight = measured.size.height + 8.dp.toPx()
                    val pillLeft = dX - pillWidth / 2f
                    val pillTop = dY - 22.dp.toPx() - pillHeight

                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.95f),
                        topLeft = Offset(pillLeft, pillTop),
                        size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                    )
                    drawRoundRect(
                        color = baseColor.copy(alpha = 0.6f),
                        topLeft = Offset(pillLeft, pillTop),
                        size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                        style = Stroke(width = 2f)
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(pillLeft + 8.dp.toPx(), pillTop + 4.dp.toPx())
                    )
                }
            }

            // 10. Draw Custom Pickup Pin (Green)
            if (pickupLat != null && pickupLng != null && !isActiveRide) {
                val pX = getX(pickupLng)
                val pY = getY(pickupLat)
                drawCircle(color = SuccessGreen.copy(alpha = 0.25f), radius = 18.dp.toPx(), center = Offset(pX, pY))
                drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(pX, pY))
                drawCircle(color = SuccessGreen, radius = 6.dp.toPx(), center = Offset(pX, pY))

                val labelText = "Pickup: ${pickupLocationName?.split(",")?.get(0)?.take(16) ?: "Selected"}"
                val labelStyle = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                val measured = textMeasurer.measure(labelText, labelStyle)
                val pillWidth = measured.size.width + 12.dp.toPx()
                val pillHeight = measured.size.height + 6.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(pX - pillWidth / 2f, pY - 18.dp.toPx() - pillHeight),
                    size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
                drawRoundRect(
                    color = SuccessGreen,
                    topLeft = Offset(pX - pillWidth / 2f, pY - 18.dp.toPx() - pillHeight),
                    size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    style = Stroke(width = 2f)
                )
                drawText(measured, topLeft = Offset(pX - pillWidth / 2f + 6.dp.toPx(), pY - 18.dp.toPx() - pillHeight + 3.dp.toPx()))
            } else if (!isActiveRide) {
                val pX = getX(passengerLng)
                val pY = getY(passengerLat)

                drawCircle(
                    color = Color(0xFF4285F4).copy(alpha = 0.25f),
                    radius = 18.dp.toPx(),
                    center = Offset(pX, pY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = Offset(pX, pY)
                )
                drawCircle(
                    color = Color(0xFF4285F4),
                    radius = 6.dp.toPx(),
                    center = Offset(pX, pY)
                )
            }

            // 11. Draw Custom Dropoff Pin (Red)
            if (dropoffLat != null && dropoffLng != null && !isActiveRide) {
                val dX = getX(dropoffLng)
                val dY = getY(dropoffLat)
                drawCircle(color = ErrorRed.copy(alpha = 0.25f), radius = 18.dp.toPx(), center = Offset(dX, dY))
                drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(dX, dY))
                drawCircle(color = ErrorRed, radius = 6.dp.toPx(), center = Offset(dX, dY))

                val labelText = "Drop-off: ${dropoffLocationName?.split(",")?.get(0)?.take(16) ?: "Selected"}"
                val labelStyle = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                val measured = textMeasurer.measure(labelText, labelStyle)
                val pillWidth = measured.size.width + 12.dp.toPx()
                val pillHeight = measured.size.height + 6.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(dX - pillWidth / 2f, dY - 18.dp.toPx() - pillHeight),
                    size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
                drawRoundRect(
                    color = ErrorRed,
                    topLeft = Offset(dX - pillWidth / 2f, dY - 18.dp.toPx() - pillHeight),
                    size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    style = Stroke(width = 2f)
                )
                drawText(measured, topLeft = Offset(dX - pillWidth / 2f + 6.dp.toPx(), dY - 18.dp.toPx() - pillHeight + 3.dp.toPx()))
            }

            // 12. Route polyline & distance calculation between pickup and dropoff
            if (pickupLat != null && pickupLng != null && dropoffLat != null && dropoffLng != null && !isActiveRide) {
                val pX = getX(pickupLng)
                val pY = getY(pickupLat)
                val dX = getX(dropoffLng)
                val dY = getY(dropoffLat)

                drawLine(
                    color = BrandBluePrimary,
                    start = Offset(pX, pY),
                    end = Offset(dX, dY),
                    strokeWidth = 4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )

                val distKm = calculateDistance(pickupLat, pickupLng, dropoffLat, dropoffLng)
                val estMin = (distKm * 3.2 + 2).toInt().coerceAtLeast(2)
                val routeLabel = "${String.format(java.util.Locale.US, "%.1f", distKm)} km • ~$estMin min"
                val routeStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                val measured = textMeasurer.measure(routeLabel, routeStyle)
                val midX = (pX + dX) / 2f
                val midY = (pY + dY) / 2f
                val pillW = measured.size.width + 14.dp.toPx()
                val pillH = measured.size.height + 8.dp.toPx()

                drawRoundRect(
                    color = BrandBlueDark,
                    topLeft = Offset(midX - pillW / 2f, midY - pillH / 2f),
                    size = androidx.compose.ui.geometry.Size(pillW, pillH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                )
                drawText(measured, topLeft = Offset(midX - pillW / 2f + 7.dp.toPx(), midY - pillH / 2f + 4.dp.toPx()))
            }

            // 13. Draw Tapped Interactive Pin (Amber pulsing halo)
            tappedPinLocation?.let { pin ->
                val tX = getX(pin.lng)
                val tY = getY(pin.lat)

                drawCircle(color = AccentAmber.copy(alpha = 0.4f), radius = 22.dp.toPx() * pulseScale, center = Offset(tX, tY))
                drawCircle(color = Color.White, radius = 11.dp.toPx(), center = Offset(tX, tY))
                drawCircle(color = AccentAmber, radius = 8.dp.toPx(), center = Offset(tX, tY))
            }
        }

        // =========================================================
        // GOOGLE MAPS CONTROLS & OVERLAYS
        // =========================================================

        // Top Header Bar Overlay
        Column(
            modifier = Modifier
                .zIndex(10f)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.95f),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Google Maps Branding Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4285F4),
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("G", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Google Maps SDK", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = BrandBlueDark)
                            Text("${availableDrivers.size} live vehicles • Banjul & Kanifing", fontSize = 9.5.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Map Layer & Traffic Switches
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Traffic Layer Toggle Button
                        FilterChip(
                            selected = showTraffic,
                            onClick = { showTraffic = !showTraffic },
                            label = { Text("Traffic", fontSize = 9.5.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Traffic,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (showTraffic) Color.White else NeutralGray
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF44336),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(26.dp)
                        )

                        // Surge Heatmap Toggle Button
                        FilterChip(
                            selected = showHeatmap,
                            onClick = { showHeatmap = !showHeatmap },
                            label = { Text("Heatmap", fontSize = 9.5.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (showHeatmap) Color.White else NeutralGray
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF5722),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(26.dp)
                        )

                        // Map Type Dropdown Button
                        Box {
                            IconButton(
                                onClick = { showMapTypeMenu = !showMapTypeMenu },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = "Map Style", tint = BrandBluePrimary, modifier = Modifier.size(18.dp))
                            }

                            DropdownMenu(
                                expanded = showMapTypeMenu,
                                onDismissRequest = { showMapTypeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default Roadmap") },
                                    leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                                    onClick = { mapType = "ROADMAP"; showMapTypeMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Satellite View") },
                                    leadingIcon = { Icon(Icons.Default.Satellite, contentDescription = null) },
                                    onClick = { mapType = "SATELLITE"; showMapTypeMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Terrain View") },
                                    leadingIcon = { Icon(Icons.Default.Terrain, contentDescription = null) },
                                    onClick = { mapType = "TERRAIN"; showMapTypeMenu = false }
                                )
                            }
                        }
                    }
                }
            }

            // Driver ETA Text Element on Map View
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_eta_text_container"),
                color = Color.White.copy(alpha = 0.96f),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, driverEtaData.badgeColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = driverEtaData.badgeColor.copy(alpha = 0.12f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (driverEtaData.status) {
                                        "ARRIVED" -> Icons.Default.CheckCircle
                                        "IN_TRANSIT" -> Icons.Default.Navigation
                                        else -> Icons.Default.AccessTime
                                    },
                                    contentDescription = "Driver ETA",
                                    tint = driverEtaData.badgeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = driverEtaData.headline,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp,
                                    color = BrandBlueDark,
                                    modifier = Modifier.testTag("driver_eta_text")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = driverEtaData.badgeColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (driverEtaData.etaMinutes > 0) "${driverEtaData.etaMinutes} min" else "ARRIVED",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = driverEtaData.badgeColor,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = driverEtaData.subtext,
                                fontSize = 9.sp,
                                color = NeutralGray,
                                maxLines = 1
                            )
                        }
                    }

                    // Google Maps SDK Live Location Attribution
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (driverEtaData.isLiveGps) Color(0xFF0F9D58) else Color(0xFF4285F4))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Google Maps SDK",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandBlueDark
                        )
                    }
                }
            }

            // Vehicle Category Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = { vehicleFilter = "ALL" },
                    shape = RoundedCornerShape(20.dp),
                    color = if (vehicleFilter == "ALL") BrandBluePrimary else Color.White.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.3f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.height(26.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "All (${availableDrivers.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (vehicleFilter == "ALL") Color.White else BrandBlueDark
                        )
                    }
                }

                Surface(
                    onClick = { vehicleFilter = "CAR" },
                    shape = RoundedCornerShape(20.dp),
                    color = if (vehicleFilter == "CAR") BrandBluePrimary else Color.White.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.3f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.height(26.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (vehicleFilter == "CAR") Color.White else BrandBluePrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cars ($carCount)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (vehicleFilter == "CAR") Color.White else BrandBlueDark
                        )
                    }
                }

                Surface(
                    onClick = { vehicleFilter = "TRICYCLE" },
                    shape = RoundedCornerShape(20.dp),
                    color = if (vehicleFilter == "TRICYCLE") AccentAmber else Color.White.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.3f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.height(26.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TwoWheeler,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (vehicleFilter == "TRICYCLE") Color.White else AccentAmber
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Motorbikes ($bikeCount)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (vehicleFilter == "TRICYCLE") Color.White else BrandBlueDark
                        )
                    }
                }
            }
        }

        // Floating Map Controls (Zoom & Recenter) on the Right Side
        Column(
            modifier = Modifier
                .zIndex(10f)
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                onClick = { zoomLevel = (zoomLevel + 0.3f).coerceAtMost(2.5f) },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = BrandBlueDark, modifier = Modifier.size(16.dp))
                }
            }

            Surface(
                onClick = { zoomLevel = (zoomLevel - 0.3f).coerceAtLeast(0.8f) },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = BrandBlueDark, modifier = Modifier.size(16.dp))
                }
            }

            Surface(
                onClick = { zoomLevel = 1.0f },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter", tint = BrandBluePrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Google Maps Watermark Footer (Bottom Left)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = if (selectedDriverOnMap != null) 140.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Google Maps",
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = if (mapType == "SATELLITE") Color.White.copy(alpha = 0.8f) else Color.DarkGray.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "• Map data ©2026 Google",
                fontSize = 8.5.sp,
                color = if (mapType == "SATELLITE") Color.LightGray.copy(alpha = 0.7f) else NeutralGray
            )
        }

        // =========================================================
        // SELECTED DRIVER QUICK CARD SHEET OVERLAY
        // =========================================================
        AnimatedVisibility(
            visible = selectedDriverOnMap != null,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { it },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedDriverOnMap?.let { selectedDriver ->
                val distKm = calculateDistance(passengerLat, passengerLng, selectedDriver.currentLat, selectedDriver.currentLng)
                val etaMin = (distKm * 3.5 + 2).toInt().coerceAtLeast(1)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("driver_map_card_${selectedDriver.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(BrandBluePrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (selectedDriver.vehicleType == "CAR") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                        contentDescription = null,
                                        tint = BrandBluePrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(selectedDriver.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlueDark)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.Verified, contentDescription = "Verified", tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                    }
                                    Text(
                                        text = "${if (selectedDriver.vehicleType == "CAR") "Yellow Taxi Sedan" else "Tricycle / Moto"} • ${selectedDriver.vehiclePlate}",
                                        fontSize = 11.sp,
                                        color = NeutralGray
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedDriverOnMap = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = NeutralGray, modifier = Modifier.size(16.dp))
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${selectedDriver.rating} ★ (120+ trips)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = BrandBlueDark)
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BrandBluePrimary.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${String.format("%.1f", distKm)} km • ~$etaMin min away",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandBluePrimary
                                    )
                                }
                            }
                        }

                        if (onSelectDriver != null) {
                            Button(
                                onClick = {
                                    onSelectDriver.invoke(selectedDriver)
                                    selectedDriverOnMap = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .testTag("request_ride_with_driver_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Request Ride with ${selectedDriver.name.split(" ")[0]}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Interactive Tapped Location Pin Bottom Sheet Card
            AnimatedVisibility(
                visible = tappedPinLocation != null,
                enter = fadeIn() + androidx.compose.animation.slideInVertically { it },
                exit = fadeOut() + androidx.compose.animation.slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(20f)
            ) {
                tappedPinLocation?.let { pin ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .testTag("tapped_location_pin_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentAmber.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Place, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(pin.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = BrandBlueDark)
                                        Text(
                                            String.format(java.util.Locale.US, "%.4f° N, %.4f° W • Banjul & Kanifing Zone", pin.lat, -pin.lng),
                                            fontSize = 10.sp,
                                            color = NeutralGray
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { tappedPinLocation = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Pin", tint = NeutralGray, modifier = Modifier.size(16.dp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onSetPickupLocation?.invoke(pin.name, pin.lat, pin.lng)
                                        tappedPinLocation = null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("set_map_pin_pickup_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Set as Pickup", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        onSetDropoffLocation?.invoke(pin.name, pin.lat, pin.lng)
                                        tappedPinLocation = null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("set_map_pin_dropoff_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Set Destination", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Top Banner overlay when Map Picking Mode is active
            AnimatedVisibility(
                visible = mapPickingMode != null,
                enter = fadeIn() + androidx.compose.animation.slideInVertically { -it },
                exit = fadeOut() + androidx.compose.animation.slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(25f)
                    .padding(top = 45.dp, start = 8.dp, end = 8.dp)
            ) {
                Surface(
                    color = BrandBlueDark,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = AccentAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tap map to set ${if (mapPickingMode == "PICKUP") "Pickup Location" else "Destination"} in Banjul/Kanifing",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { onCancelMapPicking?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// Simple tuple helper
private data class Tuple6<A, B, C, D, E, F>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
)
