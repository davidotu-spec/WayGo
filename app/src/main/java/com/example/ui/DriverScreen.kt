package com.example.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.DriverEntity
import com.example.data.TripEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverScreen(
    viewModel: WayGoViewModel,
    modifier: Modifier = Modifier,
    authViewModel: AuthenticationViewModel? = null,
    onOpenSectionSheet: (() -> Unit)? = null
) {
    val drivers by viewModel.allDrivers.collectAsState()
    val activeDriverId by viewModel.activeDriverId.collectAsState()
    val trips by viewModel.allTrips.collectAsState()
    val scheduledRides by viewModel.allScheduledRides.collectAsState()
    val notifications by viewModel.driverNotifications.collectAsState()
    val payoutState by viewModel.payoutState.collectAsState()
    val activeDriverMileage by viewModel.activeDriverMileage.collectAsState()
    val endShiftSummary by viewModel.endShiftSummary.collectAsState()

    val isDriverLoggedIn by viewModel.isDriverLoggedIn.collectAsState()
    val driverEmail by viewModel.driverEmail.collectAsState()
    val driverPassword by viewModel.driverPassword.collectAsState()
    val isDriverAuthenticating by viewModel.isDriverAuthenticating.collectAsState()
    val driverAuthError by viewModel.driverAuthError.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity

    val isDark = MaterialTheme.colorScheme.background == BrandBlueDark

    // BackHandler ensures pressing the back button in Driver Hub returns to the Passenger section
    androidx.activity.compose.BackHandler {
        viewModel.setRole("PASSENGER")
    }

    if (!isDriverLoggedIn) {
        if (authViewModel != null) {
            LoginScreen(
                authViewModel = authViewModel,
                initialRole = "DRIVER",
                onAuthSuccess = { role ->
                    viewModel.onAuthenticationSuccess(role)
                },
                onNavigateBack = { viewModel.setRole("PASSENGER") },
                onRoleChange = { newRole -> viewModel.setRole(newRole) }
            )
        } else {
            DriverAuthView(
                email = driverEmail,
                pass = driverPassword,
                isAuthenticating = isDriverAuthenticating,
                authError = driverAuthError,
                onEmailChange = { viewModel.setDriverEmail(it) },
                onPassChange = { viewModel.setDriverPassword(it) },
                onLoginSubmit = { em, pw -> viewModel.loginDriverWithEmail(em, pw) },
                onQuickDriverSelect = { email, pass ->
                    viewModel.setDriverEmail(email)
                    viewModel.setDriverPassword(pass)
                    viewModel.loginDriverWithEmail(email, pass)
                },
                onRegisterSubmit = { email, pass, name, vehicleType, vehiclePlate, licenseNum, onError ->
                    viewModel.registerDriverWithEmail(
                        email = email,
                        pass = pass,
                        name = name,
                        vehicleType = vehicleType,
                        vehiclePlate = vehiclePlate,
                        licenseNum = licenseNum,
                        onSuccess = { viewModel.onAuthenticationSuccess("DRIVER") },
                        onError = onError
                    )
                },
                onGoogleDriverAuth = { email, name, pass, vehicleType, vehiclePlate, licenseNum, isRegisterMode, onError ->
                    viewModel.loginOrRegisterDriverWithGoogle(
                        googleEmail = email,
                        googleName = name,
                        pass = pass,
                        vehicleType = vehicleType,
                        vehiclePlate = vehiclePlate,
                        licenseNum = licenseNum,
                        isRegisterMode = isRegisterMode,
                        onSuccess = { viewModel.onAuthenticationSuccess("DRIVER") },
                        onError = onError
                    )
                },
                onSelectRole = { role -> viewModel.setRole(role) },
                isDark = isDark
            )
        }
        return
    }

    if (endShiftSummary != null) {
        DailyPerformanceSummaryModal(
            summary = endShiftSummary!!,
            onDismiss = { viewModel.clearShiftSummary() },
            isDark = isDark
        )
    }

    var showOnboardingForm by remember { mutableStateOf(false) }
    var showRequestPayoutDialog by remember { mutableStateOf(false) }
    var showSignOutModal by remember { mutableStateOf(false) }

    if (showSignOutModal) {
        SignOutConfirmationDialog(
            userRole = "Driver",
            userEmail = drivers.firstOrNull { it.id == activeDriverId }?.name ?: "driver@waygo.gm",
            isDark = isDark,
            onDismiss = { showSignOutModal = false },
            onConfirmSignOut = {
                viewModel.logoutDriver()
                authViewModel?.logoutDriver()
            }
        )
    }

    // Dynamic system battery observation with tester simulator override state
    val (systemBatteryLevel, systemIsCharging) = rememberBatteryState()
    var simulatedBatteryLevel by remember { mutableStateOf<Int?>(null) }
    val batteryLevel = simulatedBatteryLevel ?: systemBatteryLevel
    val isCharging = systemIsCharging

    // Fallback default driver prevents infinite spinner during async Room initialization
    val fallbackDriver = remember {
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
        )
    }
    val currentDriver = drivers.firstOrNull { it.id == activeDriverId } ?: drivers.firstOrNull() ?: fallbackDriver

    val cardBg = if (isDark) Color(0xFF1E293B) else PureWhite
    val textPrimary = if (isDark) PureWhite else BrandBlueDark
    val textSecondary = if (isDark) Color(0xFF94A3B8) else NeutralGray
    val appBg = if (isDark) Color(0xFF0F172A) else BrandBlueLight
    val borderCol = if (isDark) Color(0xFF334155) else Color.LightGray.copy(alpha = 0.3f)

    // Local subscription state
    var hasActiveSubscription by remember { mutableStateOf(true) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }

    // Wallet Cash-out dialog state
    var showCashOutDialog by remember { mutableStateOf(false) }
    var isCashingOutDone by remember { mutableStateOf(false) }

    // Visual demand surge alert notification state
    var activeSurgeEvent by remember { mutableStateOf<DemandSurgeEvent?>(null) }

    val driverLiveLocation by com.example.data.DriverFusedLocationTracker.locationState.collectAsState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            com.example.data.DriverFusedLocationTracker.startTracking(
                context = context,
                driverId = currentDriver.id,
                driverName = currentDriver.name,
                driverPhone = currentDriver.phone,
                vehicleType = currentDriver.vehicleType,
                vehiclePlate = currentDriver.vehiclePlate,
                rating = currentDriver.rating.toDouble()
            )
        }
    }

    // Real-time Firestore Vicinity Listener & FusedLocation Tracking for Active Online Driver
    // (Keyed stably on driver ID, online state, and vehicle type to avoid recreating listener every 4s)
    LaunchedEffect(currentDriver.id, currentDriver.isOnline, currentDriver.vehicleType) {
        if (currentDriver.isOnline) {
            viewModel.startListeningToNearbyRidesForDriver(
                driverId = currentDriver.id,
                driverLat = currentDriver.currentLat,
                driverLng = currentDriver.currentLng,
                vehicleType = currentDriver.vehicleType
            )
            if (com.example.data.DriverFusedLocationTracker.hasLocationPermission(context)) {
                com.example.data.DriverFusedLocationTracker.startTracking(
                    context = context,
                    driverId = currentDriver.id,
                    driverName = currentDriver.name,
                    driverPhone = currentDriver.phone,
                    vehicleType = currentDriver.vehicleType,
                    vehiclePlate = currentDriver.vehiclePlate,
                    rating = currentDriver.rating.toDouble()
                )
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else {
            viewModel.stopListeningToNearbyRides()
            com.example.data.DriverFusedLocationTracker.stopTracking()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopListeningToNearbyRides()
            com.example.data.DriverFusedLocationTracker.stopTracking()
        }
    }

    // Automated demand surge simulation engine
    LaunchedEffect(currentDriver?.isOnline, currentDriver?.id) {
        if (currentDriver?.isOnline == true && currentDriver != null) {
            // Wait 15 seconds before triggering first simulated surge to avoid overwhelming on launch
            delay(15000L)
            while (true) {
                if (activeSurgeEvent == null) {
                    val isBanjul = kotlin.random.Random.nextBoolean()
                    val newEvent = if (isBanjul) {
                        DemandSurgeEvent(
                            id = "surge_" + System.currentTimeMillis().toString().takeLast(5),
                            zoneName = "Banjul City",
                            region = "BANJUL",
                            surgeMultiplier = 1.4 + (kotlin.random.Random.nextDouble() * 0.3).let { (it * 10).roundToInt() / 10.0 },
                            description = "Ferry port congestion has caused passenger queuing. Commuters seeking immediate transport back to Serekunda.",
                            hotspotName = "Banjul Ferry Port",
                            lat = 13.4510,
                            lng = -16.5680,
                            potentialEarningsGmd = 80 + kotlin.random.Random.nextInt(5) * 20
                        )
                    } else {
                        DemandSurgeEvent(
                            id = "surge_" + System.currentTimeMillis().toString().takeLast(5),
                            zoneName = "Kanifing Area",
                            region = "KANIFING",
                            surgeMultiplier = 1.5 + (kotlin.random.Random.nextDouble() * 0.4).let { (it * 10).roundToInt() / 10.0 },
                            description = "Serekunda Market midday peak hour and Senegambia Strip nightlife crowds looking for urgent rides.",
                            hotspotName = "Serekunda Market",
                            lat = 13.4382,
                            lng = -16.6780,
                            potentialEarningsGmd = 120 + kotlin.random.Random.nextInt(5) * 20
                        )
                    }
                    activeSurgeEvent = newEvent
                    // Also trigger a system-wide push notification overlay for complete integration!
                    viewModel.triggerDriverPushNotification(
                        driverId = currentDriver.id,
                        driverName = currentDriver.name,
                        title = "🔥 HIGH DEMAND SURGE DETECTED",
                        message = "${newEvent.zoneName} has an active surge: ${newEvent.surgeMultiplier}x fare potential!"
                    )
                }
                // Wait 45 seconds before trying to trigger next simulated surge
                delay(45000L)
            }
        } else {
            activeSurgeEvent = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenSectionSheet?.invoke() }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .testTag("driver_topbar_brand")
                    ) {
                        // Circular brand badge with WayGo inside
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(BrandBluePrimary, BrandBlueDark)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "WayGo",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.5.sp,
                                style = LocalTextStyle.current.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Driver Hub",
                                    color = BrandBlueDark,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    letterSpacing = (-0.3).sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Section",
                                    tint = BrandBluePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Online Dispatch • Tap to switch section",
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardBg),
                actions = {
                    IconButton(
                        onClick = { onOpenSectionSheet?.invoke() },
                        modifier = Modifier.testTag("driver_open_section_sheet_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Switch Section",
                            tint = BrandBluePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    AssistChip(
                        onClick = { viewModel.setRole("PASSENGER") },
                        label = { Text("Passenger", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(Icons.Default.DirectionsCar, contentDescription = "Passenger", modifier = Modifier.size(14.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = BrandBlueLight,
                            labelColor = BrandBluePrimary,
                            leadingIconContentColor = BrandBluePrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("driver_switch_passenger_chip")
                    )

                    val themeMode by viewModel.themeMode.collectAsState()
                    IconButton(
                        onClick = {
                            val nextMode = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                            viewModel.setThemeMode(nextMode)
                        },
                        modifier = Modifier.testTag("driver_topbar_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (themeMode == ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (themeMode == ThemeMode.DARK) AccentAmber else BrandBluePrimary
                        )
                    }

                    IconButton(
                        onClick = { showSignOutModal = true },
                        modifier = Modifier.testTag("driver_topbar_logout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Sign Out Driver",
                            tint = ErrorRed
                        )
                    }

                    // Battery-level indicator to help drivers plan their shifts
                    BatteryLevelIndicatorCompact(
                        level = batteryLevel,
                        isCharging = isCharging,
                        onClick = {
                            android.widget.Toast.makeText(
                                context,
                                "Battery: $batteryLevel% (${if (isCharging) "Charging" else "${batteryLevel / 12} hrs shift left"})",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.padding(end = 4.dp).testTag("battery_indicator_compact")
                    )

                    // Let user switch driver perspective dynamically to inspect different vehicles!
                    var showDriverSwitchMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showDriverSwitchMenu = true }) {
                        Icon(Icons.Default.Cached, contentDescription = "Switch Driver", tint = textPrimary)
                    }
                    DropdownMenu(
                        expanded = showDriverSwitchMenu,
                        onDismissRequest = { showDriverSwitchMenu = false }
                    ) {
                        drivers.forEach { driver ->
                            DropdownMenuItem(
                                text = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(driver.name)
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (driver.approvalStatus == "APPROVED") SuccessGreen.copy(alpha = 0.15f) else Color.LightGray
                                            )
                                        ) {
                                            Text(
                                                driver.approvalStatus,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setActiveDriver(driver.id)
                                    showDriverSwitchMenu = false
                                }
                            )
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = "register",
                                        tint = BrandBluePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "Onboard New Driver",
                                        color = BrandBluePrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            },
                            onClick = {
                                showOnboardingForm = true
                                showDriverSwitchMenu = false
                            },
                            modifier = Modifier.testTag("menu_onboard_driver")
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showOnboardingForm) {
            Box(
                modifier = modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                DriverOnboardingForm(
                    viewModel = viewModel,
                    onDismiss = { showOnboardingForm = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
            return@Scaffold
        }

        if (currentDriver == null) {
            Box(
                modifier = modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandBluePrimary)
            }
            return@Scaffold
        }

        Box(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(appBg)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .testTag("driver_screen_col"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // DRIVER HEAD CARD & STATUS TOGGLE
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = currentDriver.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = textPrimary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (currentDriver.vehicleType == "CAR") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                            contentDescription = "vehicle",
                                            tint = BrandBlueSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${if (currentDriver.vehicleType == "CAR") "Yellow Cab" else "Tricycle (Tuk)"} • ${currentDriver.vehiclePlate}",
                                            fontSize = 12.sp,
                                            color = textSecondary
                                        )
                                    }
                                }

                                // Beautiful Interactive Switch for Active / Offline status
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (currentDriver.isOnline) "Active" else "Offline",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (currentDriver.isOnline) SuccessGreen else NeutralGray
                                    )
                                    Switch(
                                        checked = currentDriver.isOnline,
                                        onCheckedChange = { isChecked ->
                                            viewModel.toggleDriverOnlineState(currentDriver.id, isChecked)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = SuccessGreen,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = Color.LightGray
                                        ),
                                        modifier = Modifier.testTag("driver_online_switch")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Availability Status Indicator Card
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("driver_availability_banner"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (currentDriver.isOnline) SuccessGreen.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (currentDriver.isOnline) Icons.Default.CheckCircle else Icons.Default.DoNotDisturbOn,
                                        contentDescription = "Status Icon",
                                        tint = if (currentDriver.isOnline) SuccessGreen else NeutralGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (currentDriver.isOnline) "You are Active & Available" else "You are currently Offline",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = BrandBlueDark
                                        )
                                        Text(
                                            text = if (currentDriver.isOnline) {
                                                "You will receive live ride requests matching your ${if (currentDriver.vehicleType == "CAR") "Yellow Cab" else "Tricycle"}."
                                            } else {
                                                "Set yourself to Active to start receiving passenger bookings."
                                            },
                                            fontSize = 11.sp,
                                            color = BrandBlueDark.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            // Real-time FusedLocationProviderClient GPS Telematics & Firestore Card
                            if (currentDriver.isOnline) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("driver_fused_location_telematics_card"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0xFF1E293B) else BrandBlueLight.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (driverLiveLocation.isFirestoreSyncActive) SuccessGreen.copy(alpha = 0.5f) else BrandBluePrimary.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.GpsFixed,
                                                    contentDescription = "GPS Telematics",
                                                    tint = if (driverLiveLocation.isTracking) SuccessGreen else BrandBluePrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "FusedLocation GPS Telematics",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = textPrimary
                                                )
                                            }
                                            Surface(
                                                color = if (driverLiveLocation.isFirestoreSyncActive) SuccessGreen.copy(alpha = 0.15f) else AccentAmber.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(if (driverLiveLocation.isFirestoreSyncActive) SuccessGreen else AccentAmber)
                                                    )
                                                    Text(
                                                        text = if (driverLiveLocation.isFirestoreSyncActive) "Firestore Synced" else "Connecting",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (driverLiveLocation.isFirestoreSyncActive) SuccessGreen else AccentAmber
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Coordinates (Lat / Lng)", fontSize = 10.sp, color = textSecondary)
                                                Text(
                                                    text = driverLiveLocation.coordinatesDisplay,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimary
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Speed / Accuracy", fontSize = 10.sp, color = textSecondary)
                                                Text(
                                                    text = "${driverLiveLocation.speedKmh.roundToInt()} km/h · ±${driverLiveLocation.accuracyMeters.roundToInt()}m",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimary
                                                )
                                            }
                                        }

                                        if (driverLiveLocation.errorMessage != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = driverLiveLocation.errorMessage ?: "",
                                                color = ErrorRed,
                                                fontSize = 10.sp
                                            )
                                        }

                                        if (!com.example.data.DriverFusedLocationTracker.hasLocationPermission(context)) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    locationPermissionLauncher.launch(
                                                        arrayOf(
                                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                                        )
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth().height(32.dp).testTag("btn_grant_driver_location_perm"),
                                                colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text("Grant GPS Location Permission", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            // Quick Shift Summary Action Row
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.triggerShiftSummaryForDriver(currentDriver.id)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("btn_trigger_shift_summary"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandBlueSecondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBlueSecondary.copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "Shift Summary",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Daily Shift Performance Summary", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            // Verification / Document Summary Fulfills MVP verification status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Verification Badge", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = NeutralGray)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (currentDriver.approvalStatus == "APPROVED") Icons.Default.Verified else Icons.Default.NewReleases,
                                            contentDescription = "verified",
                                            tint = if (currentDriver.approvalStatus == "APPROVED") SuccessGreen else ErrorRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (currentDriver.approvalStatus == "APPROVED") "Verified WayGo Partner" else "Under Review by Admin",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentDriver.approvalStatus == "APPROVED") SuccessGreen else ErrorRed
                                        )
                                    }
                                }

                                Text(
                                    text = "Lic: ${currentDriver.driverLicense.takeLast(6)}",
                                    fontSize = 11.sp,
                                    color = NeutralGray
                                )
                            }
                        }
                    }
                }

                // Live Demand Surge visual notification banner/alert
                activeSurgeEvent?.let { surge ->
                    item {
                        DemandSurgeNotificationCard(
                            event = surge,
                            isDark = isDark,
                            onNavigate = { event ->
                                viewModel.updateDriverLocation(currentDriver.id, event.lat, event.lng)
                                android.widget.Toast.makeText(
                                    context,
                                    "GPS routed to ${event.hotspotName}! Commencing navigation to ${event.zoneName} surge zone.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                activeSurgeEvent = null
                            },
                            onDismiss = {
                                activeSurgeEvent = null
                            }
                        )
                    }
                }

                // Vehicle Mileage & Maintenance Widget
                item {
                    VehicleMileageMaintenanceWidget(
                        mileage = activeDriverMileage,
                        driverId = activeDriverId,
                        onUpdateMileage = { drvId, newMileage ->
                            viewModel.updateVehicleMileage(drvId, newMileage)
                        },
                        onResetOil = { drvId ->
                            viewModel.resetOilChange(drvId)
                        },
                        onResetTire = { drvId ->
                            viewModel.resetTireCheck(drvId)
                        },
                        onToggleSimulating = { drvId, isSimulating ->
                            viewModel.toggleSimulatedMileage(drvId, isSimulating)
                        },
                        isDark = isDark
                    )
                }

                // Dynamic Battery & Shift Optimizer Widget
                item {
                    BatteryLevelDashboardWidget(
                        level = batteryLevel,
                        isCharging = isCharging,
                        onSimulateBattery = { newLevel ->
                            simulatedBatteryLevel = newLevel
                        },
                        onNavigateToHub = { hub ->
                            android.widget.Toast.makeText(
                                context,
                                "Routing to ${hub.name} (${hub.distanceKm}km) in ${hub.location}!",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }

                // Dynamic Night Shift & Ergonomics Widget
                item {
                    val themeMode by viewModel.themeMode.collectAsState()
                    NightShiftErgonomicsWidget(
                        themeMode = themeMode,
                        onThemeChange = { mode ->
                            viewModel.setThemeMode(mode)
                        }
                    )
                }

                // SUB STATUS / PENDING ADMIN REVIEW NOTICE
                if (currentDriver.approvalStatus != "APPROVED") {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = "Pend", tint = ErrorRed)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Application Standard Review", fontWeight = FontWeight.Bold, color = ErrorRed, fontSize = 13.sp)
                                    Text("Your license & vehicle registration files are awaiting verification by WayGo Admin staff.", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // DRIVER ALERTS & PUSH NOTIFICATIONS CENTER
                val driverNotificationsFiltered = notifications.filter { it.driverId == currentDriver.id }
                if (currentDriver.approvalStatus == "APPROVED" && currentDriver.isOnline) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("driver_alerts_center_card"),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = "Alerts",
                                            tint = BrandBluePrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Push Notifications Inbox",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = BrandBlueDark
                                        )
                                    }

                                    if (driverNotificationsFiltered.isNotEmpty()) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = BrandBluePrimary.copy(alpha = 0.12f))
                                        ) {
                                            Text(
                                                text = "${driverNotificationsFiltered.size} Alerts",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = BrandBluePrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (driverNotificationsFiltered.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MarkChatRead,
                                            contentDescription = "No alerts",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "Your Push Inbox is empty",
                                            color = NeutralGray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "New passenger requests will appear here in real-time as push notifications.",
                                            color = NeutralGray.copy(alpha = 0.7f),
                                            fontSize = 10.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        driverNotificationsFiltered.take(4).forEach { notif ->
                                            val associatedTrip = notif.payload?.id?.let { tid ->
                                                trips.firstOrNull { it.id == tid }
                                            }
                                            val isAvailable = associatedTrip?.status == "REQUESTED"

                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isAvailable) BrandBlueLight.copy(alpha = 0.6f) else Color(0xFFF1F5F9)
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = notif.title,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp,
                                                                color = BrandBlueDark
                                                            )
                                                            Text(
                                                                text = notif.message,
                                                                fontSize = 11.sp,
                                                                color = Color.DarkGray,
                                                                lineHeight = 15.sp,
                                                                modifier = Modifier.padding(vertical = 4.dp)
                                                            )
                                                        }

                                                        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                                        val timeStr = sdf.format(java.util.Date(notif.timestamp))
                                                        Text(
                                                            text = timeStr,
                                                            fontSize = 9.sp,
                                                            color = NeutralGray,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(start = 6.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    if (isAvailable && associatedTrip != null) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End
                                                        ) {
                                                            Button(
                                                                onClick = {
                                                                    viewModel.acceptBooking(associatedTrip.id, currentDriver.id)
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.height(28.dp),
                                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                                            ) {
                                                                Text("Accept Request", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                            }
                                                        }
                                                    } else {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            val statusLabel = if (associatedTrip?.driverId == currentDriver.id) "Assigned to You" else "No longer available"
                                                            val statusColor = if (associatedTrip?.driverId == currentDriver.id) SuccessGreen else NeutralGray
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                                                                Text(text = statusLabel, fontSize = 9.sp, color = statusColor, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // WALLET & EARNINGS CARD (Fulfills Earnings & Wallet Tracker MVP & rich Driver Earnings Dashboard)
                item {
                    var selectedTimeframe by remember { mutableStateOf("DAILY") }

                    val driverCompletedTrips = trips.filter { it.driverId == currentDriver.id && it.status == "COMPLETED" }

                    // Time Calculations using Java Calendar
                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    val beginningOfToday = calendar.timeInMillis

                    // 7 days ago (weekly)
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, -6)
                    val beginningOfWeek = calendar.timeInMillis

                    // Filter subsets
                    val todayTrips = driverCompletedTrips.filter { it.timestamp >= beginningOfToday }
                    val weeklyTrips = driverCompletedTrips.filter { it.timestamp >= beginningOfWeek }

                    val activeTripsSubset = if (selectedTimeframe == "DAILY") todayTrips else weeklyTrips

                    // Earnings for active timeframe
                    val totalTips = activeTripsSubset.sumOf { it.tipGmd }
                    val totalFares = activeTripsSubset.sumOf { it.fareGmd }
                    val totalGross = totalFares + totalTips
                    val platformCommission = (totalFares * 0.15).toInt()
                    val netEarningsForTimeframe = (totalFares - platformCommission) + totalTips
                    val successfulTripsCount = activeTripsSubset.size

                    // Explicit current week calculations for the detailed breakdown
                    val weeklyFares = weeklyTrips.sumOf { it.fareGmd }
                    val weeklyTipsVal = weeklyTrips.sumOf { it.tipGmd }
                    val weeklyGross = weeklyFares + weeklyTipsVal
                    val weeklyCommission = (weeklyFares * 0.15).toInt()
                    val weeklyNetPayout = (weeklyFares - weeklyCommission) + weeklyTipsVal
                    val weeklyTripsCount = weeklyTrips.size

                    // Compute last 7 days of earnings dynamically for the line chart (Recharts style)
                    val last7DaysData = remember(driverCompletedTrips) {
                        val list = mutableListOf<DayEarnings>()
                        val sdf = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                        
                        // We want chronological order from 6 days ago up to today
                        for (i in 6 downTo 0) {
                            val dCal = java.util.Calendar.getInstance()
                            dCal.add(java.util.Calendar.DAY_OF_YEAR, -i)
                            
                            // Start of day
                            dCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            dCal.set(java.util.Calendar.MINUTE, 0)
                            dCal.set(java.util.Calendar.SECOND, 0)
                            dCal.set(java.util.Calendar.MILLISECOND, 0)
                            val dayStart = dCal.timeInMillis
                            
                            // End of day
                            val dayEnd = dayStart + (24 * 60 * 60 * 1000 - 1)
                            
                            val label = sdf.format(dCal.time)
                            
                            // Compute net earnings
                            val dayTrips = driverCompletedTrips.filter { it.timestamp in dayStart..dayEnd }
                            val dayFares = dayTrips.sumOf { it.fareGmd }
                            val dayTips = dayTrips.sumOf { it.tipGmd }
                            val dayCommission = (dayFares * 0.15).toInt()
                            val dayNet = (dayFares - dayCommission) + dayTips
                            
                            list.add(DayEarnings(label = label, amount = dayNet, timestamp = dayStart))
                        }
                        list
                    }

                    // Total available balance to Cash Out (All-time net completed trips)
                    val totalFaresAllTime = driverCompletedTrips.sumOf { it.fareGmd }
                    val totalTipsAllTime = driverCompletedTrips.sumOf { it.tipGmd }
                    val totalGrossAllTime = totalFaresAllTime + totalTipsAllTime
                    val totalCommissionAllTime = (totalFaresAllTime * 0.15).toInt()
                    val totalNetAllTime = (totalFaresAllTime - totalCommissionAllTime) + totalTipsAllTime

                    // Helper to identify regions
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
                            else -> "KANIFING" // default fallback to Kanifing
                        }
                    }

                    // Regional counts for active timeframe
                    val banjulTrips = activeTripsSubset.filter { getTripRegionName(it) == "BANJUL" }
                    val banjulGross = banjulTrips.sumOf { it.fareGmd } + banjulTrips.sumOf { it.tipGmd }
                    val banjulCount = banjulTrips.size

                    val kanifingTrips = activeTripsSubset.filter { getTripRegionName(it) == "KANIFING" }
                    val kanifingGross = kanifingTrips.sumOf { it.fareGmd } + kanifingTrips.sumOf { it.tipGmd }
                    val kanifingCount = kanifingTrips.size

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("driver_earnings_dashboard_card"),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header with Icon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = "Earnings Hub",
                                        tint = BrandBluePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Driver Earnings Dashboard",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = BrandBlueDark
                                    )
                                }
                                
                                Badge(
                                    containerColor = BrandBlueLight,
                                    contentColor = BrandBluePrimary
                                ) {
                                    Text(
                                        text = if (selectedTimeframe == "DAILY") "Today" else "7 Days",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Connection Quality & Local Caching Controller
                            val isConnectionPoor by viewModel.isConnectionPoor.collectAsState()
                            val localCachingStatus by viewModel.localCachingStatus.collectAsState()

                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isConnectionPoor) ErrorRed.copy(alpha = 0.08f) else SuccessGreen.copy(alpha = 0.05f))
                                    .border(
                                        1.dp,
                                        if (isConnectionPoor) ErrorRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isConnectionPoor) Icons.Default.WifiOff else Icons.Default.Wifi,
                                                contentDescription = "Connection State",
                                                tint = if (isConnectionPoor) ErrorRed else SuccessGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (isConnectionPoor) "Poor Connection Fallback Active" else "Connected to WayGo Cloud",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isConnectionPoor) ErrorRed else BrandBlueDark
                                            )
                                        }

                                        // Simulate poor connection toggle button
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isConnectionPoor) ErrorRed.copy(alpha = 0.15f) else BrandBlueLight)
                                                .clickable { viewModel.toggleConnectionQuality() }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                                .testTag("toggle_network_poor_btn")
                                        ) {
                                            Text(
                                                text = if (isConnectionPoor) "Poor Connection ON" else "Simulate Poor Connection",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isConnectionPoor) ErrorRed else BrandBluePrimary
                                            )
                                        }
                                    }
                                    
                                    Text(
                                        text = localCachingStatus,
                                        fontSize = 10.sp,
                                        color = NeutralGray,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Timeframe Segmented Control
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrandBlueLight)
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedTimeframe == "DAILY") PureWhite else Color.Transparent)
                                        .clickable { selectedTimeframe = "DAILY" }
                                        .padding(vertical = 8.dp)
                                        .testTag("earnings_timeframe_daily"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Daily Summary",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (selectedTimeframe == "DAILY") BrandBluePrimary else NeutralGray
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedTimeframe == "WEEKLY") PureWhite else Color.Transparent)
                                        .clickable { selectedTimeframe = "WEEKLY" }
                                        .padding(vertical = 8.dp)
                                        .testTag("earnings_timeframe_weekly"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Weekly Summary",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (selectedTimeframe == "WEEKLY") BrandBluePrimary else NeutralGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Statistics Cards Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Net Payout Card (Primary Focus)
                                Card(
                                    modifier = Modifier.weight(1.2f),
                                    colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.05f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.15f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Net Payout", fontSize = 10.sp, color = NeutralGray)
                                        Text(
                                            text = "${netEarningsForTimeframe} GMD",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = SuccessGreen,
                                            modifier = Modifier.testTag("total_earnings_val")
                                        )
                                        Text("Take-home", fontSize = 9.sp, color = NeutralGray.copy(alpha = 0.8f))
                                    }
                                }

                                // Gross Earnings
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = BrandBluePrimary.copy(alpha = 0.03f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.1f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Gross Fares", fontSize = 10.sp, color = NeutralGray)
                                        Text("${totalGross} GMD", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlueDark)
                                        Text("Before platform fee", fontSize = 9.sp, color = NeutralGray.copy(alpha = 0.8f))
                                    }
                                }

                                // Successful Trips Count
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = BrandBluePrimary.copy(alpha = 0.03f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.1f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Success Trips", fontSize = 10.sp, color = NeutralGray)
                                        Text("${successfulTripsCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlueDark)
                                        Text("Completed", fontSize = 9.sp, color = NeutralGray.copy(alpha = 0.8f))
                                    }
                                }
                            }

                            // EARNINGS GOAL TRACKER CARD
                            val driverGoal by viewModel.driverDailyGoalGmd.collectAsState()
                            val currentNetToday = netEarningsForTimeframe
                            val goalProgressFraction = (currentNetToday.toFloat() / driverGoal.toFloat()).coerceIn(0f, 1f)
                            val goalPercent = (goalProgressFraction * 100).toInt()
                            var showEditGoalDialog by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("driver_earnings_goal_card"),
                                colors = CardDefaults.cardColors(containerColor = BrandBluePrimary.copy(alpha = 0.05f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                                            Text("Daily Earnings Goal Tracker", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                        }
                                        TextButton(
                                            onClick = { showEditGoalDialog = true },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Set Goal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Text("$currentNetToday GMD earned", fontWeight = FontWeight.Black, fontSize = 15.sp, color = SuccessGreen)
                                        Text("Goal: $driverGoal GMD ($goalPercent%)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NeutralGray)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    LinearProgressIndicator(
                                        progress = { goalProgressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (goalProgressFraction >= 1f) SuccessGreen else BrandBluePrimary,
                                        trackColor = Color.LightGray.copy(alpha = 0.4f)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Milestone Badges Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val badge500 = currentNetToday >= 500
                                        val badge1000 = currentNetToday >= 1000
                                        val badge1500 = currentNetToday >= driverGoal

                                        AssistChip(
                                            onClick = {},
                                            label = { Text("500 GMD 🥉", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (badge500) Color(0xFFCD7F32).copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.2f),
                                                labelColor = if (badge500) BrandBlueDark else NeutralGray
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (badge500) Color(0xFFCD7F32) else Color.Transparent)
                                        )
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("1,000 GMD 🥈", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (badge1000) Color(0xFFC0C0C0).copy(alpha = 0.25f) else Color.LightGray.copy(alpha = 0.2f),
                                                labelColor = if (badge1000) BrandBlueDark else NeutralGray
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (badge1000) Color(0xFFC0C0C0) else Color.Transparent)
                                        )
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("Goal Met 🏆", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (badge1500) AccentAmber.copy(alpha = 0.25f) else Color.LightGray.copy(alpha = 0.2f),
                                                labelColor = if (badge1500) BrandBlueDark else NeutralGray
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (badge1500) AccentAmber else Color.Transparent)
                                        )
                                    }
                                }
                            }

                            if (showEditGoalDialog) {
                                var customGoalInput by remember { mutableStateOf(driverGoal.toString()) }
                                AlertDialog(
                                    onDismissRequest = { showEditGoalDialog = false },
                                    title = { Text("Customize Daily Target", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                                    text = {
                                        OutlinedTextField(
                                            value = customGoalInput,
                                            onValueChange = { customGoalInput = it.filter { char -> char.isDigit() } },
                                            label = { Text("Target Amount (GMD)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            val valInt = customGoalInput.toIntOrNull() ?: 1500
                                            viewModel.setDriverDailyGoal(valInt)
                                            showEditGoalDialog = false
                                        }) {
                                            Text("Save Goal")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showEditGoalDialog = false }) { Text("Cancel") }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Detailed Weekly Earnings Breakdown (Gross, 15% Commission, and Net Payout)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("weekly_detailed_breakdown_card")
                                    .padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = BrandBlueLight.copy(alpha = 0.4f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalanceWallet,
                                                contentDescription = "Wallet Icon",
                                                tint = BrandBluePrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Current Week's Payout Breakdown",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = BrandBlueDark
                                            )
                                        }
                                        
                                        Badge(
                                            containerColor = BrandBluePrimary,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = "15% PLATFORM FEE",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Row 1: Gross Weekly Ride Fares
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Gross Weekly Ride Fares", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandBlueDark)
                                            Text("Sum of passenger base fares (excluding tips)", fontSize = 9.sp, color = NeutralGray)
                                        }
                                        Text(
                                            text = "${weeklyFares} GMD",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = BrandBlueDark,
                                            modifier = Modifier.testTag("weekly_gross_fares_val")
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Row 2: 15% Platform Commission
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("WayGo Commission Deduction (15%)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ErrorRed)
                                            Text("Flat service fee per completed booking", fontSize = 9.sp, color = NeutralGray)
                                        }
                                        Text(
                                            text = "-${weeklyCommission} GMD",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = ErrorRed,
                                            modifier = Modifier.testTag("weekly_commission_val")
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Row 3: Tips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Passenger Tips (Keep 100%)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SuccessGreen)
                                            Text("Tips from completed rides this week", fontSize = 9.sp, color = NeutralGray)
                                        }
                                        Text(
                                            text = "+${weeklyTipsVal} GMD",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = SuccessGreen,
                                            modifier = Modifier.testTag("weekly_tips_val")
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = BrandBluePrimary.copy(alpha = 0.2f), thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    // Row 4: Net Weekly Payout
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Net Payout Ready for Cash Out", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = BrandBlueDark)
                                            Text("Payout ready for Africell/Wave Mobile Money", fontSize = 9.sp, color = NeutralGray)
                                        }
                                        Text(
                                            text = "${weeklyNetPayout} GMD",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = SuccessGreen,
                                            modifier = Modifier.testTag("weekly_net_payout_val")
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Weekly Earnings Trend Line Chart (Visual progression over last 7 days - Recharts Style)
                            WeeklyEarningsTrendChart(
                                data = last7DaysData,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Interactive Hotspots Map Preview with live demand indicators
                            HotspotMapPreview(
                                modifier = Modifier.padding(bottom = 16.dp),
                                onNavigateToHotspot = { hotspot ->
                                    if (currentDriver != null) {
                                        viewModel.updateDriverLocation(currentDriver.id, hotspot.lat, hotspot.lng)
                                        android.widget.Toast.makeText(
                                            context,
                                            "GPS simulated to ${hotspot.name}! Awaiting bookings in ${hotspot.region}.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )

                            // Regional breakdown
                            Text(
                                text = "Regional Tracking (Banjul & Kanifing)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = BrandBlueDark,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Banjul Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "Banjul",
                                            tint = BrandBlueSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Banjul City", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = BrandBlueDark)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "$banjulCount trips",
                                            fontSize = 11.sp,
                                            color = NeutralGray,
                                            modifier = Modifier.testTag("banjul_trips_count")
                                        )
                                        Text(
                                            text = "$banjulGross GMD",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = BrandBluePrimary,
                                            modifier = Modifier.testTag("banjul_earnings_val")
                                        )
                                    }
                                }

                                // Kanifing Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "Kanifing",
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Kanifing Area", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = BrandBlueDark)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "$kanifingCount trips",
                                            fontSize = 11.sp,
                                            color = NeutralGray,
                                            modifier = Modifier.testTag("kanifing_trips_count")
                                        )
                                        Text(
                                            text = "$kanifingGross GMD",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = BrandBluePrimary,
                                            modifier = Modifier.testTag("kanifing_earnings_val")
                                        )
                                    }
                                }

                                // Dynamic ratio calculation
                                val totalGmd = (banjulGross + kanifingGross).toFloat()
                                val banjulRatio = if (totalGmd > 0f) banjulGross / totalGmd else 0.5f
                                val kanifingRatio = if (totalGmd > 0f) kanifingGross / totalGmd else 0.5f

                                // Duo-colored visual bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.LightGray.copy(alpha = 0.3f))
                                ) {
                                    if (totalGmd > 0f) {
                                        if (banjulRatio > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(banjulRatio)
                                                    .background(BrandBlueSecondary)
                                            )
                                        }
                                        if (kanifingRatio > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(kanifingRatio)
                                                    .background(SuccessGreen)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(1f)
                                                .background(Color.LightGray.copy(alpha = 0.4f))
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Banjul City (${(banjulRatio * 100).toInt()}%)",
                                        fontSize = 9.sp,
                                        color = BrandBlueSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Kanifing Area (${(kanifingRatio * 100).toInt()}%)",
                                        fontSize = 9.sp,
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.3f))

                                Text(
                                    text = "DEMO SURGE CONTROLS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = NeutralGray,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val newEvent = DemandSurgeEvent(
                                                id = "surge_manual_banjul_" + System.currentTimeMillis().toString().takeLast(4),
                                                zoneName = "Banjul City",
                                                region = "BANJUL",
                                                surgeMultiplier = 1.5,
                                                description = "Simulated high passenger volume at Banjul Ferry Terminal. Commuters waiting under high heat.",
                                                hotspotName = "Banjul Ferry Port",
                                                lat = 13.4510,
                                                lng = -16.5680,
                                                potentialEarningsGmd = 90
                                            )
                                            activeSurgeEvent = newEvent
                                            viewModel.triggerDriverPushNotification(
                                                driverId = currentDriver?.id ?: "",
                                                driverName = currentDriver?.name ?: "",
                                                title = "🔥 BANJUL SURGE ALERT",
                                                message = "High demand simulated in Banjul City! 1.5x Multipliers active."
                                            )
                                            android.widget.Toast.makeText(context, "Simulated Banjul Surge Triggered!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp).testTag("simulate_banjul_surge_btn"),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlueSecondary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ElectricBolt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Banjul Surge", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            val newEvent = DemandSurgeEvent(
                                                id = "surge_manual_kanifing_" + System.currentTimeMillis().toString().takeLast(4),
                                                zoneName = "Kanifing Area",
                                                region = "KANIFING",
                                                surgeMultiplier = 1.7,
                                                description = "Midday peak demand simulated at Serekunda Market. High trip request volume.",
                                                hotspotName = "Serekunda Market",
                                                lat = 13.4382,
                                                lng = -16.6780,
                                                potentialEarningsGmd = 150
                                            )
                                            activeSurgeEvent = newEvent
                                            viewModel.triggerDriverPushNotification(
                                                driverId = currentDriver?.id ?: "",
                                                driverName = currentDriver?.name ?: "",
                                                title = "🔥 KANIFING SURGE ALERT",
                                                message = "High demand simulated in Kanifing Area! 1.7x Multipliers active."
                                            )
                                            android.widget.Toast.makeText(context, "Simulated Kanifing Surge Triggered!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp).testTag("simulate_kanifing_surge_btn"),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ElectricBolt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Kanifing Surge", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            if (selectedTimeframe == "WEEKLY") {
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth().testTag("weekly_payout_summary_card"),
                                    colors = CardDefaults.cardColors(containerColor = BrandBlueLight.copy(alpha = 0.5f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.15f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        // Header
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Payments,
                                                contentDescription = "Payout Info",
                                                tint = BrandBluePrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Weekly Payout Summary",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = BrandBlueDark
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        // Direct visual callout emphasizing zero signup fees & flat commission
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(PureWhite)
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "Zero signup fee info",
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Zero sign-up fees. Weekly payouts via mobile money (Wave/Africell). Flat 15% platform commission tracked per completed ride request.",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = BrandBlueDark.copy(alpha = 0.8f)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        // Numbers
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Gross Ride Fares", fontSize = 11.sp, color = NeutralGray)
                                            Text("${totalFares} GMD", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = BrandBlueDark)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Flat 15% Commission Deduction", fontSize = 11.sp, color = ErrorRed)
                                            Text("-${platformCommission} GMD", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ErrorRed)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Completed Tips (Keep 100%)", fontSize = 11.sp, color = NeutralGray)
                                            Text("+${totalTips} GMD", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = SuccessGreen)
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Net Ready for Payout", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                            Text("${netEarningsForTimeframe} GMD", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = SuccessGreen)
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Completed Rides This Week:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = BrandBlueDark
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        if (weeklyTrips.isEmpty()) {
                                            Text("No completed rides recorded for this week.", fontSize = 10.sp, color = NeutralGray)
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                weeklyTrips.forEach { trip ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(PureWhite)
                                                            .padding(8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "${trip.pickupName.split(",")[0]} ➔ ${trip.dropoffName.split(",")[0]}",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = BrandBlueDark,
                                                                maxLines = 1
                                                            )
                                                            Text(
                                                                text = "Fare: ${trip.fareGmd} GMD | Tip: ${trip.tipGmd} GMD",
                                                                fontSize = 9.sp,
                                                                color = NeutralGray
                                                            )
                                                            Text(
                                                                text = "Calculated 15% Commission: ${trip.commissionGmd} GMD (tracked)",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = ErrorRed.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                        Badge(
                                                            containerColor = SuccessGreen.copy(alpha = 0.1f),
                                                            contentColor = SuccessGreen
                                                        ) {
                                                            Text(
                                                                text = "READY",
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        Button(
                                            onClick = {
                                                showRequestPayoutDialog = true
                                            },
                                            enabled = netEarningsForTimeframe > 0,
                                            modifier = Modifier.fillMaxWidth().testTag("weekly_payout_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalanceWallet,
                                                contentDescription = "Payout Wallet",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Request Weekly Payout (${netEarningsForTimeframe} GMD)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons: Cash Out & Weekly Status
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        showCashOutDialog = true
                                        isCashingOutDone = false
                                    },
                                    enabled = totalNetAllTime > 0,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                ) {
                                    Icon(Icons.Default.Payment, contentDescription = "Pay", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cash Out (${totalNetAllTime} GMD)", fontSize = 11.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { showSubscriptionDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandBluePrimary)
                                ) {
                                    Icon(
                                        imageVector = if (hasActiveSubscription) Icons.Default.CardMembership else Icons.Default.AddCard,
                                        contentDescription = "Sub",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (hasActiveSubscription) "Weekly Active" else "Renew License",
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // INCOMING RIDE REQUEST OVERLAY (Real-time Closed Loop!)
                val incomingRequest = trips.firstOrNull { it.status == "REQUESTED" && it.vehicleType == currentDriver.vehicleType }
                if (incomingRequest != null && currentDriver.isOnline && currentDriver.approvalStatus == "APPROVED") {
                    item {
                        var timeLeft by remember(incomingRequest.id) { mutableStateOf(30) }
                        LaunchedEffect(incomingRequest.id) {
                            timeLeft = 30
                            while (timeLeft > 0) {
                                delay(1000L)
                                timeLeft--
                            }
                            viewModel.declineBooking(incomingRequest.id)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("incoming_request_card"),
                            colors = CardDefaults.cardColors(containerColor = BrandBlueDark),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = "bell", tint = AccentAmber)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("NEW TRIP REQUEST AVAILABLE!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }

                                    Text(
                                        text = "${incomingRequest.fareGmd} GMD",
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Visual 30-Second Countdown Timer Progress Bar
                                val progress = timeLeft / 30f
                                val barColor = when {
                                    timeLeft > 15 -> SuccessGreen
                                    timeLeft > 7 -> AccentAmber
                                    else -> ErrorRed
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = "Timer Icon",
                                            tint = barColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Auto-declining in ${timeLeft}s",
                                            color = barColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Text(
                                        text = "DECISION TIMER",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(barColor)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text("Passenger: ${incomingRequest.passengerName}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Text("Pickup: ${incomingRequest.pickupName}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                Text("Dropoff: ${incomingRequest.dropoffName}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                Text("Prefers: ${incomingRequest.paymentMethod} • Cashless Preferred", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.acceptBooking(incomingRequest.id, currentDriver.id) },
                                        modifier = Modifier.weight(1.5f).testTag("accept_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                    ) {
                                        Text("Accept Booking Flat")
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.declineBooking(incomingRequest.id) },
                                        modifier = Modifier.weight(1.0f).testTag("decline_button"),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                                    ) {
                                        Text("Ignore")
                                    }
                                }
                            }
                        }
                    }
                }

                // TRIP EXECUTION NAVIGATION SHEET (Visual control from driver App side)
                val activeExecution = trips.firstOrNull {
                    it.driverId == currentDriver.id && it.status in listOf("ACCEPTED", "ARRIVED", "EN_ROUTE")
                }
                if (activeExecution != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("active_execution_card"),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandBluePrimary)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Active Journey Guidance",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = BrandBlueDark
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Text("Current Passenger: ${activeExecution.passengerName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Heading To: ${activeExecution.dropoffName.split(",")[0]}", fontSize = 12.sp, color = NeutralGray)
                                        if (activeExecution.preferences.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = BrandBluePrimary.copy(alpha = 0.1f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.25f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Tune, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Prefs: ${activeExecution.preferences}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                                                }
                                            }
                                        }
                                    }

                                    Card(colors = CardDefaults.cardColors(containerColor = BrandBlueLight)) {
                                        Text(
                                            text = activeExecution.status,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = BrandBluePrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Ride status visual progress step indicator
                                RideStatusStepIndicator(
                                    currentStatus = activeExecution.status,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var showDriverChatDialog by remember { mutableStateOf(false) }

                                    AssistChip(
                                        onClick = { showDriverChatDialog = true },
                                        label = { Text("Chat with Passenger", fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Chat,
                                                contentDescription = "Chat",
                                                modifier = Modifier.size(14.dp),
                                                tint = BrandBluePrimary
                                            )
                                        },
                                        modifier = Modifier.testTag("driver_chat_launcher")
                                    )

                                    if (showDriverChatDialog) {
                                        WayGoChatDialog(
                                            tripId = activeExecution.id,
                                            currentRole = "DRIVER",
                                            currentUserId = currentDriver.id,
                                            currentUserName = "${currentDriver.name} (Driver)",
                                            viewModel = viewModel,
                                            onDismiss = {
                                                showDriverChatDialog = false
                                                viewModel.endChatSession()
                                            }
                                        )
                                    }

                                    var showCallMaskDialog by remember { mutableStateOf(false) }

                                    AssistChip(
                                        onClick = { showCallMaskDialog = true },
                                        label = { Text("Call Support Masked", fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Phone,
                                                contentDescription = "Call",
                                                modifier = Modifier.size(14.dp),
                                                tint = BrandBluePrimary
                                            )
                                        }
                                    )

                                    if (showCallMaskDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showCallMaskDialog = false },
                                            title = { Text("Encrypted Call Masking", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                                            text = {
                                                Text(
                                                    "Calling passenger ${activeExecution.passengerName} via secure WayGo masked gateway. Phone: +220 330-WAY-MASK",
                                                    fontSize = 12.sp
                                                )
                                            },
                                            confirmButton = {
                                                Button(onClick = { showCallMaskDialog = false }) { Text("Dial") }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showCallMaskDialog = false }) { Text("Cancel") }
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                 // Driver manual steps to match real ride handling
                                when (activeExecution.status) {
                                    "ACCEPTED" -> {
                                        Button(
                                            onClick = { viewModel.setArrivedAtPickup(activeExecution.id, currentDriver.id) }, // triggers update to arrived state next
                                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("set_arrived_pickup_btn"),
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlueSecondary)
                                        ) {
                                            Text("Set: Arrived at Pickup")
                                        }
                                    }
                                    "ARRIVED" -> {
                                        var enteredPin by remember { mutableStateOf("") }
                                        val pinError by viewModel.pinVerificationError.collectAsState()

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(BrandBlueLight.copy(alpha = 0.5f))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Pin, contentDescription = null, tint = BrandBluePrimary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Enter Passenger Safety PIN", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                            }
                                            
                                            OutlinedTextField(
                                                value = enteredPin,
                                                onValueChange = { if (it.length <= 4) enteredPin = it },
                                                placeholder = { Text("Ask passenger for 4-digit code (e.g. ${activeExecution.verificationPin})") },
                                                modifier = Modifier.fillMaxWidth().testTag("driver_pin_input"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true
                                            )

                                            if (pinError.isNotEmpty()) {
                                                Text(pinError, fontSize = 11.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.beginTransitWithPin(activeExecution.id, currentDriver.id, enteredPin)
                                                },
                                                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("begin_transit_btn"),
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                enabled = enteredPin.length == 4
                                            ) {
                                                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Verify PIN & Begin Transit", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    "EN_ROUTE" -> {
                                        Button(
                                            onClick = { viewModel.completeTrip(activeExecution.id, currentDriver.id) }, // finalize as completed
                                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("complete_drive_btn"),
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary)
                                        ) {
                                            Text("Complete Drive (Confirm & End)")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                var showEndRideConfirmDialog by remember { mutableStateOf(false) }

                                Button(
                                    onClick = { showEndRideConfirmDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("end_ride_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "End Ride Icon",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("End Ride", fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                if (showEndRideConfirmDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showEndRideConfirmDialog = false },
                                        title = { Text("End Ride & Complete?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                                        text = {
                                            Text(
                                                "Are you sure you want to end this ride and complete the trip? This will finalize the passenger's fare of ${activeExecution.fareGmd} GMD and allow them to tip or review.",
                                                fontSize = 13.sp
                                             )
                                         },
                                         confirmButton = {
                                             Button(
                                                 onClick = {
                                                     showEndRideConfirmDialog = false
                                                     viewModel.completeTrip(activeExecution.id, currentDriver.id)
                                                 },
                                                 colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                             ) {
                                                 Text("Confirm & End")
                                             }
                                         },
                                         dismissButton = {
                                             TextButton(onClick = { showEndRideConfirmDialog = false }) {
                                                 Text("Cancel")
                                             }
                                         }
                                     )
                                 }
                            }
                        }
                    }
                }

                // ADVANCE BOOKINGS QUEUE CARD SECTION
                item {
                    Text(
                        "Available Advance Bookings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BrandBlueDark,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                val availableSchedules = scheduledRides.filter { 
                    it.status == "SCHEDULED" && it.vehicleType == currentDriver.vehicleType 
                }

                if (availableSchedules.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PureWhite)
                        ) {
                            Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No upcoming advance bookings listed for your vehicle class right now.",
                                    color = NeutralGray,
                                    fontSize = 11.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(availableSchedules) { ride ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("driver_advance_ride_${ride.id}"),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.EventNote, contentDescription = "Schedule", tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Time: ${ride.scheduledTime}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = BrandBlueDark
                                        )
                                    }

                                    Text(
                                        "${ride.fareGmd} GMD",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = SuccessGreen
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Customer: ${ride.passengerName}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = BrandBlueDark)
                                Text("Pickup: ${ride.pickupName}", fontSize = 11.sp, color = NeutralGray)
                                Text("Dropoff: ${ride.dropoffName}", fontSize = 11.sp, color = NeutralGray)

                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { 
                                        viewModel.driverAcceptScheduledRide(ride.id, currentDriver.id, currentDriver.name) 
                                    },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Accept", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Claim Scheduled Booking", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                val myAcceptedSchedules = scheduledRides.filter {
                    it.driverId == currentDriver.id && it.status == "ACCEPTED"
                }
                
                if (myAcceptedSchedules.isNotEmpty()) {
                    item {
                        Text(
                            "My Confirmed Scheduled Rides",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = BrandBlueDark,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(myAcceptedSchedules) { ride ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandBluePrimary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Date/Time: ${ride.scheduledTime}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = BrandBluePrimary
                                    )

                                    Card(colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f))) {
                                        Text(
                                            "CONFIRMED",
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Passenger: ${ride.passengerName}", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                Text("Pickup: ${ride.pickupName}", fontSize = 11.sp, color = NeutralGray)
                                Text("Dropoff: ${ride.dropoffName}", fontSize = 11.sp, color = NeutralGray)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Fare: ${ride.fareGmd} GMD", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandBlueDark)
                                    
                                    TextButton(
                                        onClick = { viewModel.cancelScheduledRide(ride.id) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                                    ) {
                                        Text("Release", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // COMPLETED LIST
                item {
                    Text(
                        "Completed Orders",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BrandBlueDark,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                val driverTripsList = trips.filter { it.driverId == currentDriver.id && it.status == "COMPLETED" }
                if (driverTripsList.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PureWhite)) {
                            Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No rides completed today yet.", color = NeutralGray, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    items(driverTripsList) { trip ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Pickup: ${trip.pickupName.split(",")[0]}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                        Text("Dropoff: ${trip.dropoffName.split(",")[0]}", fontSize = 11.sp, color = NeutralGray)
                                        Text("Paid: ${trip.fareGmd} GMD via ${trip.paymentMethod}", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                                    }

                                    if (trip.rating > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${trip.rating}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandBlueDark)
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Icon(Icons.Default.Star, contentDescription = "r", tint = AccentAmber, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                if (trip.rating > 0 && (trip.reviewComment.isNotEmpty() || trip.reviewTags.isNotEmpty())) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = Color.LightGray.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (trip.reviewTags.isNotEmpty()) {
                                        Text(
                                            text = "Tags: ${trip.reviewTags}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandBluePrimary,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                    if (trip.reviewComment.isNotEmpty()) {
                                        Text(
                                            text = "\"${trip.reviewComment}\"",
                                            fontSize = 11.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = BrandBlueDark.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Request Payout Dialog (Disbursement summary via Wave/Africell)
            if (showRequestPayoutDialog) {
                val driverCompletedTrips = trips.filter { it.driverId == (currentDriver?.id ?: "") && it.status == "COMPLETED" }
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -6)
                val beginningOfWeek = calendar.timeInMillis
                
                val weeklyTrips = driverCompletedTrips.filter { it.timestamp >= beginningOfWeek }
                val weeklyFares = weeklyTrips.sumOf { it.fareGmd }
                val weeklyTipsVal = weeklyTrips.sumOf { it.tipGmd }
                val weeklyGross = weeklyFares + weeklyTipsVal
                val weeklyCommission = (weeklyFares * 0.15).toInt()
                val weeklyNetPayout = (weeklyFares - weeklyCommission) + weeklyTipsVal

                var selectedProvider by remember { mutableStateOf("Wave") } // "Wave" or "Africell"
                var payoutPhone by remember { mutableStateOf("+220 384 5678") }
                
                AlertDialog(
                    onDismissRequest = { 
                        if (payoutState !is PayoutState.Loading) {
                            showRequestPayoutDialog = false 
                            viewModel.resetPayoutState()
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Payout Title Icon",
                                tint = BrandBluePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Prepare Weekly Disbursement", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (val state = payoutState) {
                                is PayoutState.Idle -> {
                                    Text(
                                        "Select your preferred Mobile Money provider and confirm the weekly earnings disbursement payload.",
                                        fontSize = 12.sp,
                                        color = NeutralGray
                                    )
                                    
                                    // Custom Radio / Selector for Wave / Africell
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Wave Card Option
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedProvider = "Wave" }
                                                .testTag("provider_wave_card"),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (selectedProvider == "Wave") BrandBlueLight else PureWhite
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = 2.dp,
                                                color = if (selectedProvider == "Wave") BrandBluePrimary else Color.LightGray.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Payments,
                                                    contentDescription = "Wave Logo",
                                                    tint = if (selectedProvider == "Wave") BrandBluePrimary else Color.Gray,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "Wave Money", 
                                                    fontWeight = FontWeight.Bold, 
                                                    fontSize = 11.sp,
                                                    color = if (selectedProvider == "Wave") BrandBlueDark else Color.Gray
                                                )
                                                Text("Instant (GMD)", fontSize = 8.sp, color = NeutralGray)
                                            }
                                        }
                                        
                                        // Africell Card Option
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedProvider = "Africell" }
                                                .testTag("provider_africell_card"),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (selectedProvider == "Africell") BrandBlueLight else PureWhite
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = 2.dp,
                                                color = if (selectedProvider == "Africell") BrandBluePrimary else Color.LightGray.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalanceWallet,
                                                    contentDescription = "Africell Logo",
                                                    tint = if (selectedProvider == "Africell") BrandBluePrimary else Color.Gray,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "Africell Money", 
                                                    fontWeight = FontWeight.Bold, 
                                                    fontSize = 11.sp,
                                                    color = if (selectedProvider == "Africell") BrandBlueDark else Color.Gray
                                                )
                                                Text("Instant (GMD)", fontSize = 8.sp, color = NeutralGray)
                                            }
                                        }
                                    }
                                    
                                    // Phone Number Input
                                    OutlinedTextField(
                                        value = payoutPhone,
                                        onValueChange = { payoutPhone = it },
                                        label = { Text("Mobile Money Phone Number", fontSize = 11.sp) },
                                        placeholder = { Text("+220 XXXXXXX") },
                                        modifier = Modifier.fillMaxWidth().testTag("payout_phone_input"),
                                        textStyle = androidx.compose.ui.text.TextStyle(color = BrandBlueDark, fontSize = 13.sp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = BrandBlueDark,
                                            unfocusedTextColor = BrandBlueDark,
                                            focusedBorderColor = BrandBluePrimary,
                                            unfocusedBorderColor = NeutralGray.copy(alpha = 0.3f),
                                            focusedContainerColor = PureWhite,
                                            unfocusedContainerColor = PureWhite,
                                            focusedPlaceholderColor = NeutralGray.copy(alpha = 0.6f),
                                            unfocusedPlaceholderColor = NeutralGray.copy(alpha = 0.6f)
                                        )
                                    )
                                    
                                    // Quick Summary Callout inside dialog
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = BrandBlueLight.copy(alpha = 0.3f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.1f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Weekly Earnings Summary", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandBlueDark)
                                            
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Gross Ride Fares (+Tips)", fontSize = 10.sp, color = NeutralGray)
                                                Text("${weeklyGross} GMD", fontWeight = FontWeight.Medium, fontSize = 10.sp, color = BrandBlueDark)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("WayGo Commission (15%)", fontSize = 10.sp, color = ErrorRed)
                                                Text("-${weeklyCommission} GMD", fontWeight = FontWeight.Medium, fontSize = 10.sp, color = ErrorRed)
                                            }
                                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Net Payout", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SuccessGreen)
                                                Text("${weeklyNetPayout} GMD", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SuccessGreen)
                                            }
                                        }
                                    }
                                }
                                
                                is PayoutState.Loading -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(color = BrandBluePrimary, modifier = Modifier.size(40.dp).testTag("payout_loading_indicator"))
                                        Text(
                                            "Preparing Weekly Disbursement...", 
                                            fontWeight = FontWeight.Bold, 
                                            fontSize = 13.sp, 
                                            color = BrandBlueDark
                                        )
                                        Text(
                                            "Compiling 15% deductions, preparing $selectedProvider Money payload, and requesting secure transfer reference...",
                                            fontSize = 10.sp,
                                            color = NeutralGray,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                                
                                is PayoutState.Success -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Large Success Checkmark
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(SuccessGreen.copy(alpha = 0.15f))
                                                .align(Alignment.CenterHorizontally),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Success Icon",
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(28.dp).testTag("payout_success_icon")
                                            )
                                        }
                                        
                                        Text(
                                            text = "Disbursement Prepared Successfully!",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = SuccessGreen,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        Text(
                                            text = "The weekly earnings summary has been compiled and dispatched for mobile money transfer.",
                                            fontSize = 10.sp,
                                            color = NeutralGray,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Disbursement Method", fontSize = 10.sp, color = NeutralGray)
                                                    Text("$selectedProvider Mobile Money", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = BrandBlueDark)
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Weekly Gross", fontSize = 10.sp, color = NeutralGray)
                                                    Text("${state.gross} GMD", fontWeight = FontWeight.Medium, fontSize = 10.sp, color = BrandBlueDark)
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Platform Fee (15%)", fontSize = 10.sp, color = NeutralGray)
                                                    Text("-${state.commission} GMD", fontWeight = FontWeight.Medium, fontSize = 10.sp, color = ErrorRed)
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Net Disbursed", fontSize = 10.sp, color = NeutralGray)
                                                    Text("${state.amount} GMD", fontWeight = FontWeight.Black, fontSize = 11.sp, color = SuccessGreen)
                                                }
                                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                                Column {
                                                    Text("Transaction Reference", fontSize = 9.sp, color = NeutralGray)
                                                    Text(
                                                        text = state.refId, 
                                                        fontWeight = FontWeight.Bold, 
                                                        fontSize = 10.sp, 
                                                        color = BrandBluePrimary,
                                                        modifier = Modifier.testTag("payout_ref_id")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                is PayoutState.Error -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Error Icon",
                                            tint = ErrorRed,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            "Request Failed",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = ErrorRed
                                        )
                                        Text(
                                            state.message,
                                            fontSize = 10.sp,
                                            color = NeutralGray,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        when (payoutState) {
                            is PayoutState.Idle -> {
                                Button(
                                    onClick = {
                                        viewModel.requestWeeklyPayout(
                                            driverId = currentDriver?.id ?: "",
                                            provider = selectedProvider,
                                            phone = payoutPhone,
                                            gross = weeklyGross,
                                            commission = weeklyCommission,
                                            net = weeklyNetPayout
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    modifier = Modifier.testTag("confirm_payout_button")
                                ) {
                                    Text("Confirm & Disburse")
                                }
                            }
                            is PayoutState.Success -> {
                                Button(
                                    onClick = {
                                        showRequestPayoutDialog = false
                                        viewModel.resetPayoutState()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                    modifier = Modifier.testTag("payout_success_close_btn")
                                ) {
                                    Text("Done")
                                }
                            }
                            is PayoutState.Error -> {
                                Button(
                                    onClick = {
                                        viewModel.resetPayoutState()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary)
                                ) {
                                    Text("Retry")
                                }
                            }
                            else -> {} // no action button during loading
                        }
                    },
                    dismissButton = {
                        if (payoutState is PayoutState.Idle) {
                            TextButton(
                                onClick = { showRequestPayoutDialog = false },
                                modifier = Modifier.testTag("cancel_payout_button")
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                )
            }

            // Wallet Dialog (Simulated Mobile Money Transfer)
            if (showCashOutDialog) {
                var mobileMoneyNum by remember { mutableStateOf("+220 384 5678") }
                AlertDialog(
                    onDismissRequest = { showCashOutDialog = false },
                    title = { Text("Wave / QMoney Digital Cashout") },
                    text = {
                        Column {
                            if (!isCashingOutDone) {
                                Text("Transfer net partner earnings to your mobile-money phone line in Gambia instantly.")
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = mobileMoneyNum,
                                    onValueChange = { mobileMoneyNum = it },
                                    label = { Text("Transfer Phone Line") },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = androidx.compose.ui.text.TextStyle(color = BrandBlueDark),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = BrandBlueDark,
                                        unfocusedTextColor = BrandBlueDark,
                                        focusedBorderColor = BrandBluePrimary,
                                        unfocusedBorderColor = NeutralGray.copy(alpha = 0.3f),
                                        focusedContainerColor = PureWhite,
                                        unfocusedContainerColor = PureWhite
                                    )
                                )
                            } else {
                                Text("Success! Wallet balance moved successfully to Wave. Standard network commissions computed.", color = SuccessGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {
                        if (!isCashingOutDone) {
                            Button(
                                onClick = {
                                    isCashingOutDone = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Text("Authorize Wave Transfer")
                            }
                        } else {
                            Button(onClick = { showCashOutDialog = false }) {
                                Text("Close")
                            }
                        }
                    }
                )
            }

            // Subscription Options Dialog (Fulfills Subscription Plans MVP)
            if (showSubscriptionDialog) {
                AlertDialog(
                    onDismissRequest = { showSubscriptionDialog = false },
                    title = { Text("Driver Hub Subscriptions") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("WayGo works with a zero-interest subscription structure for drivers instead of taxing heavily.")
                            Spacer(modifier = Modifier.height(4.dp))

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hasActiveSubscription = true; showSubscriptionDialog = false },
                                colors = CardDefaults.cardColors(containerColor = BrandBlueLight)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Weekly Access Plan", fontWeight = FontWeight.Bold, color = BrandBlueDark)
                                    Text("250 GMD / Week", fontSize = 13.sp, color = BrandBluePrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Keep 100% of organic customer cash.", fontSize = 11.sp, color = NeutralGray)
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hasActiveSubscription = true; showSubscriptionDialog = false },
                                colors = CardDefaults.cardColors(containerColor = BrandBlueLight)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Monthly Access Premium Plan", fontWeight = FontWeight.Bold, color = BrandBlueDark)
                                    Text("900 GMD / Month", fontSize = 13.sp, color = BrandBluePrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Preferred queue scheduling algorithm & support priority, save 100 GMD.", fontSize = 11.sp, color = NeutralGray)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSubscriptionDialog = false }) {
                            Text("Dismiss", color = NeutralGray)
                        }
                    }
                )
            }
        }
    }
}

data class DayEarnings(
    val label: String,
    val amount: Int,
    val timestamp: Long
)

@Composable
fun WeeklyEarningsTrendChart(
    data: List<DayEarnings>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_earnings_trend_card"),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Weekly Earnings Trend",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BrandBlueDark
                    )
                    Text(
                        text = "Last 7 days income progression",
                        fontSize = 10.sp,
                        color = NeutralGray
                    )
                }
                
                // Show currently selected/hovered day info
                if (selectedIndex != null && selectedIndex!! < data.size) {
                    val selected = data[selectedIndex!!]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Text(
                            text = "${selected.label}: ${selected.amount} GMD",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = SuccessGreen
                        )
                    }
                } else {
                    // Default overall stat or legend
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BrandBluePrimary)
                        )
                        Text(
                            text = "Net Take-home (GMD)",
                            fontSize = 10.sp,
                            color = NeutralGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Recharts-Style Chart Drawing Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val density = LocalDensity.current
                
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(data) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val leftPaddingPx = with(density) { 45.dp.toPx() }
                                    val rightPaddingPx = with(density) { 12.dp.toPx() }
                                    val chartWidth = size.width - leftPaddingPx - rightPaddingPx
                                    if (chartWidth > 0) {
                                        val xPos = offset.x - leftPaddingPx
                                        val idx = (xPos / chartWidth * 6f).roundToInt().coerceIn(0, 6)
                                        selectedIndex = idx
                                    }
                                },
                                onDragEnd = { selectedIndex = null },
                                onDragCancel = { selectedIndex = null },
                                onDrag = { change, _ ->
                                    val leftPaddingPx = with(density) { 45.dp.toPx() }
                                    val rightPaddingPx = with(density) { 12.dp.toPx() }
                                    val chartWidth = size.width - leftPaddingPx - rightPaddingPx
                                    if (chartWidth > 0) {
                                        val xPos = change.position.x - leftPaddingPx
                                        val idx = (xPos / chartWidth * 6f).roundToInt().coerceIn(0, 6)
                                        selectedIndex = idx
                                    }
                                }
                            )
                        }
                ) {
                    val leftPadding = 45.dp.toPx()
                    val rightPadding = 12.dp.toPx()
                    val topPadding = 10.dp.toPx()
                    val bottomPadding = 20.dp.toPx()
                    
                    val chartWidth = size.width - leftPadding - rightPadding
                    val chartHeight = size.height - topPadding - bottomPadding
                    
                    if (chartWidth <= 0 || chartHeight <= 0) return@Canvas
                    
                    val maxAmount = data.maxOfOrNull { it.amount } ?: 0
                    val maxVal = maxOf(maxAmount.toFloat(), 400f) // min height scale to prevent division by 0
                    
                    // 1. Draw Grid Lines and Y-Axis Labels
                    val gridLinesCount = 4
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 24f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    
                    for (i in 0 until gridLinesCount) {
                        val fraction = i.toFloat() / (gridLinesCount - 1)
                        val y = topPadding + chartHeight - fraction * chartHeight
                        
                        // Dashed horizontal line
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(leftPadding, y),
                            end = Offset(leftPadding + chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(10f, 10f), 0f
                            )
                        )
                        
                        // Y-Label
                        val labelVal = (fraction * maxVal).roundToInt()
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                "$labelVal",
                                leftPadding - 10f,
                                y + 8f,
                                textPaint
                            )
                        }
                    }
                    
                    // 2. Generate point positions
                    val points = data.mapIndexed { idx, item ->
                        val x = leftPadding + (idx.toFloat() / 6f) * chartWidth
                        val y = topPadding + chartHeight - (item.amount.toFloat() / maxVal) * chartHeight
                        Offset(x, y)
                    }
                    
                    // 3. Draw gradient area fill below the trend line (Recharts area chart look)
                    val areaPath = Path().apply {
                        moveTo(points[0].x, topPadding + chartHeight)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, topPadding + chartHeight)
                        close()
                    }
                    
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                BrandBluePrimary.copy(alpha = 0.22f),
                                BrandBluePrimary.copy(alpha = 0.00f)
                            ),
                            startY = topPadding,
                            endY = topPadding + chartHeight
                        )
                    )
                    
                    // 4. Draw the trend line stroke
                    val linePath = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    
                    drawPath(
                        path = linePath,
                        color = BrandBluePrimary,
                        style = Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    
                    // 5. Draw data point dots & bottom X-Axis labels
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 24f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    
                    points.forEachIndexed { idx, point ->
                        val isSelected = selectedIndex == idx
                        
                        // Draw dot
                        drawCircle(
                            color = if (isSelected) SuccessGreen else BrandBluePrimary,
                            radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                            center = point
                        )
                        drawCircle(
                            color = Color.White,
                            radius = if (isSelected) 3.dp.toPx() else 1.5.dp.toPx(),
                            center = point
                        )
                        
                        // Draw X-Label (Day name)
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                data[idx].label,
                                point.x,
                                topPadding + chartHeight + 24f,
                                labelPaint
                            )
                        }
                    }
                    
                    // 6. Draw vertical interactive selector line if hovered
                    if (selectedIndex != null && selectedIndex!! < points.size) {
                        val activePoint = points[selectedIndex!!]
                        drawLine(
                            color = SuccessGreen.copy(alpha = 0.6f),
                            start = Offset(activePoint.x, topPadding),
                            end = Offset(activePoint.x, topPadding + chartHeight),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(8f, 8f), 0f
                            )
                        )
                    }
                }
            }
            
            // Explanatory footer
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Tips info",
                    tint = NeutralGray,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Chart displays total take-home pay (including 100% of driver tips)",
                    fontSize = 9.sp,
                    color = NeutralGray
                )
            }
        }
    }
}

@Composable
fun DriverAuthView(
    email: String,
    pass: String,
    isAuthenticating: Boolean,
    authError: String,
    onEmailChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onLoginSubmit: (String, String) -> Unit,
    onQuickDriverSelect: (String, String) -> Unit,
    onRegisterSubmit: (email: String, pass: String, name: String, vehicleType: String, vehiclePlate: String, licenseNum: String, onError: (String) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onGoogleDriverAuth: (email: String, name: String, pass: String, vehicleType: String, vehiclePlate: String, licenseNum: String, isRegister: Boolean, onError: (String) -> Unit) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onSelectRole: (String) -> Unit = {},
    isDark: Boolean
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var isDriverRegisterMode by remember { mutableStateOf(false) }
    var driverNameInput by remember { mutableStateOf("") }
    var vehicleTypeInput by remember { mutableStateOf("CAR") }
    var vehiclePlateInput by remember { mutableStateOf("") }
    var licenseNumInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    var showGoogleAuthDialog by remember { mutableStateOf(false) }

    if (showGoogleAuthDialog) {
        GoogleAccountAuthDialog(
            userRole = "DRIVER",
            isDark = isDark,
            onDismiss = { showGoogleAuthDialog = false },
            onAuthenticate = { gEmail, gName, gPass, vType, vPlate, lNum, isReg, onError ->
                onGoogleDriverAuth(gEmail, gName, gPass, vType, vPlate, lNum, isReg, onError)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A192F),
                        BrandBluePrimary,
                        BrandBlueSecondary,
                        Color(0xFF0F172A)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        ModernSignInProvidersCard(
            userRole = "DRIVER",
            activeRole = "DRIVER",
            onSelectRole = onSelectRole,
            isDark = isDark,
            isAuthenticating = isAuthenticating,
            onGoogleAuthClick = { showGoogleAuthDialog = true },
            onAppleAuthClick = {
                onGoogleDriverAuth("apple.driver@icloud.com", "Apple Driver", "applePass123", "Mercedes Sedan", "BJL 8844 X", "GAM-DL-9082", false) { _ -> }
            },
            onEmailLoginSubmit = { em, pw ->
                onEmailChange(em)
                onPassChange(pw)
                onLoginSubmit(em, pw)
            },
            onEmailRegisterSubmit = { em, pw, nm, vt, vp, ln, errCb ->
                onEmailChange(em)
                onPassChange(pw)
                onRegisterSubmit(em, pw, nm, vt, vp, ln, errCb)
            },
            authError = authError,
            onQuickSelectAccount = { em, pw ->
                onQuickDriverSelect(em, pw)
            }
        )
    }
}
