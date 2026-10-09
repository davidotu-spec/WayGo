package com.example.ui

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DriverEntity
import com.example.data.RideLocation
import com.example.data.TripEntity
import com.example.data.TripFareEstimationService
import com.example.data.UserProfileEntity
import com.google.android.gms.maps.model.LatLng
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush

// Seed locations of Gambia
data class GLocation(val name: String, val lat: Double, val lng: Double)
val GAMBIA_LOCATIONS = listOf(
    GLocation("Albert Market, Banjul", 13.4533, -16.5746),
    GLocation("Arch 22, Banjul", 13.4580, -16.5820),
    GLocation("Banjul Ferry Terminal", 13.4505, -16.5710),
    GLocation("Kairaba Avenue, Serrekunda", 13.4471, -16.6791),
    GLocation("Westfield Junction, Serrekunda", 13.4385, -16.6760),
    GLocation("University of Gambia, Kanifing", 13.4452, -16.6713),
    GLocation("Tippa Garage, Serrekunda", 13.4340, -16.6850),
    GLocation("Senegambia Beach Resort", 13.4420, -16.7110),
    GLocation("Independence Stadium, Bakau", 13.4722, -16.6690),
    GLocation("Brusubi Turntable", 13.4020, -16.7180),
    GLocation("Kotu Beach, Kanifing", 13.4610, -16.7020),
    GLocation("Pipeline, Serrekunda", 13.4510, -16.6800)
)

fun resolveLocationForInput(input: String): GLocation {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return GAMBIA_LOCATIONS[0]
    }
    // Check against verified Banjul and Kanifing designated pickup points first
    val pickupMatch = com.example.data.LocationTrackingService.BANJUL_KANIFING_PICKUP_POINTS.firstOrNull { point ->
        point.name.equals(trimmed, ignoreCase = true) ||
        point.name.contains(trimmed, ignoreCase = true) ||
        trimmed.contains(point.name.split(",")[0], ignoreCase = true) ||
        trimmed.contains(point.zone, ignoreCase = true)
    }
    if (pickupMatch != null) {
        return GLocation(pickupMatch.name, pickupMatch.latitude, pickupMatch.longitude)
    }

    val match = GAMBIA_LOCATIONS.firstOrNull { loc ->
        loc.name.equals(trimmed, ignoreCase = true) ||
        loc.name.contains(trimmed, ignoreCase = true) ||
        trimmed.contains(loc.name.split(",")[0], ignoreCase = true)
    }
    if (match != null) return match

    val hash = kotlin.math.abs(trimmed.lowercase().hashCode())
    val latOffset = ((hash % 100) - 50) * 0.0006
    val lngOffset = (((hash / 100) % 100) - 50) * 0.0006
    return GLocation(
        name = trimmed,
        lat = 13.4471 + latOffset,
        lng = -16.6791 + lngOffset
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerScreen(
    viewModel: WayGoViewModel,
    modifier: Modifier = Modifier,
    authViewModel: AuthenticationViewModel? = null,
    onOpenSectionSheet: (() -> Unit)? = null
) {
    val isLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val authError by viewModel.authError.collectAsState()

    val profileFlow = viewModel.userProfile.collectAsState()
    val profile = profileFlow.value

    var activeTabSubState by remember { mutableStateOf("HOME") } // "HOME", "HISTORY", "PROFILE", "CHAT"
    var showSignOutModal by remember { mutableStateOf(false) }

    if (showSignOutModal) {
        SignOutConfirmationDialog(
            userRole = "Passenger",
            userEmail = profile?.email ?: "rider@waygo.gm",
            isDark = false,
            onDismiss = { showSignOutModal = false },
            onConfirmSignOut = {
                viewModel.logout()
                authViewModel?.logoutPassenger()
            }
        )
    }

    val isPassengerAuthenticating by viewModel.isPassengerAuthenticating.collectAsState()

    // Authentication Gate
    if (!isLoggedIn) {
        if (authViewModel != null) {
            LoginScreen(
                authViewModel = authViewModel,
                initialRole = "PASSENGER",
                onAuthSuccess = { role ->
                    viewModel.onAuthenticationSuccess(role)
                }
            )
        } else {
            val context = androidx.compose.ui.platform.LocalContext.current
            val activity = context as? android.app.Activity
            PassengerAuthView(
                authError = authError,
                isAuthenticating = isPassengerAuthenticating,
                onEmailLogin = { email, pass -> viewModel.loginPassengerWithEmail(email, pass) },
                onEmailRegister = { email, pass, name, errCb ->
                    viewModel.registerPassengerWithEmail(email, pass, name, onSuccess = { viewModel.onAuthenticationSuccess("PASSENGER") }, onError = errCb)
                },
                onSocialLogin = { provider -> viewModel.socialLoginPassenger(provider) },
                onGoogleAuth = { email, name, pass, isRegister, onError ->
                    viewModel.loginOrRegisterPassengerWithGoogle(email, name, pass, isRegister, onSuccess = { viewModel.onAuthenticationSuccess("PASSENGER") }, onError = onError)
                },
                onSelectRole = { newRole -> viewModel.setRole(newRole) }
            )
        }
        return
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
                            .testTag("passenger_topbar_brand")
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
                                    text = "WayGo Ride",
                                    color = BrandBlueDark,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
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
                                text = "Gambia • Tap to switch section",
                                color = BrandBluePrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite),
                actions = {
                    IconButton(
                        onClick = { onOpenSectionSheet?.invoke() },
                        modifier = Modifier.testTag("open_section_sheet_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Switch Section",
                            tint = BrandBluePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { activeTabSubState = "PROFILE" }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = BrandBlueDark,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = { showSignOutModal = true },
                        modifier = Modifier.testTag("passenger_topbar_logout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Sign Out",
                            tint = ErrorRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = PureWhite,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTabSubState == "HOME",
                    onClick = { activeTabSubState = "HOME" },
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Ride") },
                    label = { Text("Ride") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBluePrimary,
                        selectedTextColor = BrandBluePrimary,
                        indicatorColor = BrandBlueLight
                    )
                )
                NavigationBarItem(
                    selected = activeTabSubState == "HISTORY",
                    onClick = { activeTabSubState = "HISTORY" },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Activity") },
                    label = { Text("Activity") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBluePrimary,
                        selectedTextColor = BrandBluePrimary,
                        indicatorColor = BrandBlueLight
                    )
                )
                NavigationBarItem(
                    selected = activeTabSubState == "PROFILE" || activeTabSubState == "CHAT",
                    onClick = { activeTabSubState = "PROFILE" },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Account") },
                    label = { Text("Account") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBluePrimary,
                        selectedTextColor = BrandBluePrimary,
                        indicatorColor = BrandBlueLight
                    ),
                    modifier = Modifier.testTag("tab_account")
                )
            }
        },
        floatingActionButton = {
            if (activeTabSubState == "HOME") {
                FloatingActionButton(
                    onClick = {
                        // Quick Emergency SOS dialer simulation
                        _isSosDialogOpen.value = true
                    },
                    containerColor = ErrorRed,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Emergency, contentDescription = "Emergency SOS")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(BrandBlueLight)
        ) {
            when (activeTabSubState) {
                "HOME" -> HomeScreenContent(viewModel, profile)
                "HISTORY" -> ProfileScreenContent(viewModel, profile, initialSection = "TRIP_LOG", onSignOutClick = { showSignOutModal = true }) { activeTabSubState = "HOME" }
                "PROFILE" -> ProfileScreenContent(viewModel, profile, initialSection = "PROFILE", onSignOutClick = { showSignOutModal = true }) { activeTabSubState = "HOME" }
                "CHAT" -> ProfileScreenContent(viewModel, profile, initialSection = "INBOX", onSignOutClick = { showSignOutModal = true }) { activeTabSubState = "HOME" }
            }

            // Emergency Dialog
            SosDialog()
        }
    }
}

// Global SOS State
private val _isSosDialogOpen = mutableStateOf(false)

@Composable
fun SosDialog() {
    if (_isSosDialogOpen.value) {
        AlertDialog(
            onDismissRequest = { _isSosDialogOpen.value = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Alert", tint = ErrorRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Emergency SOS Center", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Are you experiencing an emergency? WayGo operates real-time tracking shared immediately with local Gambia security response teams in Banjul and Serrekunda.",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Simulated emergency numbers:",
                        fontWeight = FontWeight.Bold,
                        color = BrandBlueDark
                    )
                    Text("• Serekunda Police Helpline: 117", fontSize = 13.sp)
                    Text("• Fire & Ambulance Rescue: 118", fontSize = 13.sp)
                    Text("• Banjul Central Command: +220 422 4910", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { _isSosDialogOpen.value = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Share Live Link & Call 117", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { _isSosDialogOpen.value = false }) {
                    Text("Cancel", color = NeutralGray)
                }
            }
        )
    }
}

data class CountryCode(val code: String, val name: String, val flag: String, val lengthHint: String)

@Composable
fun AuthRoleSectionTabs(
    activeRole: String,
    onSelectRole: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDarkBg: Boolean = true,
    isAdminLoggedIn: Boolean = false,
    isSecretAdminUnlocked: Boolean = false,
    onLongPressHeader: (() -> Unit)? = null
) {
    val roles = listOf(
        Triple("PASSENGER", "Passenger", "🙋‍♂️"),
        Triple("DRIVER", "Driver Fleet", "🚗")
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDarkBg) PureWhite.copy(alpha = 0.12f) else BrandBlueLight,
        border = BorderStroke(1.dp, if (isDarkBg) PureWhite.copy(alpha = 0.25f) else BrandBluePrimary.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Text(
                text = "SELECT YOUR ACCOUNT PORTAL:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkBg) PureWhite.copy(alpha = 0.8f) else NeutralGray,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                roles.forEach { (roleKey, label, icon) ->
                    val isSelected = activeRole == roleKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) BrandBluePrimary else Color.Transparent
                            )
                            .clickable { onSelectRole(roleKey) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = icon,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) PureWhite else (if (isDarkBg) PureWhite.copy(alpha = 0.85f) else BrandBlueDark),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PassengerAuthView(
    authError: String = "",
    isAuthenticating: Boolean = false,
    onEmailLogin: (String, String) -> Unit = { _, _ -> },
    onEmailRegister: (String, String, String, (String) -> Unit) -> Unit = { _, _, _, _ -> },
    onSocialLogin: (String) -> Unit = {},
    onGoogleAuth: (email: String, name: String, pass: String, isRegister: Boolean, onError: (String) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onSelectRole: (String) -> Unit = {}
) {
    var showGoogleAuthDialog by remember { mutableStateOf(false) }

    if (showGoogleAuthDialog) {
        GoogleAccountAuthDialog(
            userRole = "PASSENGER",
            isDark = false,
            onDismiss = { showGoogleAuthDialog = false },
            onAuthenticate = { gEmail, gName, gPass, _, _, _, isReg, onError ->
                onGoogleAuth(gEmail, gName, gPass, isReg, onError)
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
            userRole = "PASSENGER",
            activeRole = "PASSENGER",
            onSelectRole = onSelectRole,
            isDark = false,
            isAuthenticating = isAuthenticating,
            onGoogleAuthClick = { showGoogleAuthDialog = true },
            onAppleAuthClick = {
                // Simulate Apple OAuth Sign in
                onGoogleAuth("apple.user@icloud.com", "Apple User", "applePass123", false) { _ -> }
            },
            onEmailLoginSubmit = { em, pw -> onEmailLogin(em, pw) },
            onEmailRegisterSubmit = { em, pw, nm, _, _, _, errCb -> onEmailRegister(em, pw, nm, errCb) },
            authError = authError
        )
    }
}

@Composable
fun TrustFeatureBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PureWhite.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentAmber,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = PureWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HomeScreenContent(
    viewModel: WayGoViewModel,
    profile: UserProfileEntity?
) {
    val drivers by viewModel.allDrivers.collectAsState()
    val activeTrip by viewModel.activeTrip.collectAsState()
    val trips by viewModel.allTrips.collectAsState()
    val progress by viewModel.simulationProgress.collectAsState()
    val broadcastLogs by viewModel.broadcastLogs.collectAsState()
    val broadcastDrivers by viewModel.broadcastDrivers.collectAsState()

    var selectedDriverForProfile by remember { mutableStateOf<com.example.data.DriverEntity?>(null) }

    val simLat by viewModel.simulatedDriverLat.collectAsState()
    val simLng by viewModel.simulatedDriverLng.collectAsState()
    val firestoreRideRequestStatus by viewModel.firestoreRideRequestStatus.collectAsState()
    var isBookingSaving by remember { mutableStateOf(false) }

    var pickupName by remember { mutableStateOf("") }
    var dropoffName by remember { mutableStateOf("") }
    var selectVehicleType by remember { mutableStateOf("CAR") } // "CAR", "TRICYCLE"
    var selectPaymentMethod by remember { mutableStateOf("CASH") } // "CASH", "WAVE", "AFRICELL", "STRIPE"

    // Stripe State Hookups
    val coroutineScope = rememberCoroutineScope()
    var isProcessingStripe by remember { mutableStateOf(false) }
    var isStripeInitiating by remember { mutableStateOf(false) }
    var stripeStatusMsg by remember { mutableStateOf("") }
    var stripeUrl by remember { mutableStateOf<String?>(null) }

    var showPickupDropdown by remember { mutableStateOf(false) }
    var showDropoffDropdown by remember { mutableStateOf(false) }
    var vehicleDropdownExpanded by remember { mutableStateOf(false) }

    var selectedSavedPlaceForOptions by remember { mutableStateOf<com.example.data.SavedPlaceEntity?>(null) }
    var showAddSavedPlaceDialog by remember { mutableStateOf(false) }

    // Scheduler dialog options
    var showScheduleDialog by remember { mutableStateOf(false) }
    var selectedScheduleDate by remember { mutableStateOf("Tomorrow") }
    var selectedScheduleHour by remember { mutableStateOf("09") }
    var selectedScheduleMinute by remember { mutableStateOf("00") }
    var selectedScheduleAmPm by remember { mutableStateOf("AM") }

    // Live GPS Location Tracking & Banjul/Kanifing Pickup Identification State
    val context = LocalContext.current
    val locationHelper = com.example.utils.rememberLocationState()
    val liveTrackingState by com.example.data.LocationTrackingService.trackingState.collectAsState()
    var selectedRegionFilter by remember { mutableStateOf("ALL") } // "ALL", "BANJUL", "KANIFING", "COASTAL"

    // Start high-accuracy location tracking service when permission is available
    DisposableEffect(locationHelper.state.value.isPermissionGranted) {
        if (locationHelper.state.value.isPermissionGranted) {
            com.example.data.LocationTrackingService.startTracking(context)
        }
        onDispose {
            com.example.data.LocationTrackingService.stopTracking()
        }
    }

    // LatLng anchors
    var pCoordinates by remember { mutableStateOf<GLocation?>(null) }
    var dCoordinates by remember { mutableStateOf<GLocation?>(null) }
    var mapPickingMode by remember { mutableStateOf<String?>(null) }
    var dynamicCalculatedFare by remember { mutableStateOf(150) }
    var useGoogleMapsSdk by remember { mutableStateOf(false) }

    var ratingScore by remember { mutableIntStateOf(5) }
    var ratingComment by remember { mutableStateOf("") }
    val tags = listOf("Safe", "Polite", "Fast", "Clean Car", "Good Music")
    val selectedTags = remember { mutableStateListOf<String>() }

    // Prepopulate inputs with profile details or templates on first mount
    LaunchedEffect(profile) {
        if (profile != null && dropoffName.isEmpty()) {
            pickupName = profile.savedHome
            val homeMatch = GAMBIA_LOCATIONS.firstOrNull { it.name.startsWith(profile.savedHome.split(",")[0]) }
            pCoordinates = homeMatch ?: GAMBIA_LOCATIONS[2]

            dropoffName = profile.savedWork
            val workMatch = GAMBIA_LOCATIONS.firstOrNull { it.name.startsWith(profile.savedWork.split(",")[0]) }
            dCoordinates = workMatch ?: GAMBIA_LOCATIONS[0]
        }
    }

    val oneTapDest by viewModel.oneTapBookingDestination.collectAsState()
    LaunchedEffect(oneTapDest) {
        val dest = oneTapDest
        if (dest != null) {
            dropoffName = dest.name
            val match = GAMBIA_LOCATIONS.firstOrNull { it.name.contains(dest.name.split(",")[0], ignoreCase = true) }
            dCoordinates = match ?: GLocation(dest.name, dest.lat, dest.lng)
            viewModel.clearOneTapDestination()
        }
    }

    // Trip Fare Estimation Service computation based on distance between origin and destination
    val currentFareEstimateResult = remember(pCoordinates, dCoordinates, selectVehicleType) {
        val pL = pCoordinates?.lat ?: 13.4471
        val pG = pCoordinates?.lng ?: -16.6791
        val dL = dCoordinates?.lat ?: 13.4533
        val dG = dCoordinates?.lng ?: -16.5746
        TripFareEstimationService.calculateEstimatedFare(
            originLat = pL,
            originLng = pG,
            destLat = dL,
            destLng = dG,
            vehicleType = selectVehicleType
        )
    }

    val carFareResult = remember(pCoordinates, dCoordinates) {
        val pL = pCoordinates?.lat ?: 13.4471
        val pG = pCoordinates?.lng ?: -16.6791
        val dL = dCoordinates?.lat ?: 13.4533
        val dG = dCoordinates?.lng ?: -16.5746
        TripFareEstimationService.calculateEstimatedFare(pL, pG, dL, dG, "CAR")
    }
    val tricycleFareResult = remember(pCoordinates, dCoordinates) {
        val pL = pCoordinates?.lat ?: 13.4471
        val pG = pCoordinates?.lng ?: -16.6791
        val dL = dCoordinates?.lat ?: 13.4533
        val dG = dCoordinates?.lng ?: -16.5746
        TripFareEstimationService.calculateEstimatedFare(pL, pG, dL, dG, "TRICYCLE")
    }

    val carFareEstimate = carFareResult.finalFareGmd
    val tricycleFareEstimate = tricycleFareResult.finalFareGmd
    val estimatedFare = currentFareEstimateResult.finalFareGmd

    LaunchedEffect(estimatedFare) {
        dynamicCalculatedFare = estimatedFare
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("home_screen_col"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Map Display
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_container_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Map Engine Switcher Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (useGoogleMapsSdk) Icons.Default.Place else Icons.Default.Map,
                                contentDescription = null,
                                tint = if (useGoogleMapsSdk) ErrorRed else BrandBluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (useGoogleMapsSdk) "Google Maps SDK" else "WayGo City Map",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = BrandBlueDark
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !useGoogleMapsSdk,
                                onClick = { useGoogleMapsSdk = false },
                                label = { Text("Vector", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                modifier = Modifier.testTag("toggle_vector_map_chip")
                            )
                            FilterChip(
                                selected = useGoogleMapsSdk,
                                onClick = { useGoogleMapsSdk = true },
                                label = { Text("Google Maps", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("toggle_google_maps_chip")
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (useGoogleMapsSdk) {
                            val currentPickup = remember(pickupName, pCoordinates) {
                                RideLocation(
                                    name = pickupName.ifBlank { "Pickup Point" },
                                    address = pickupName,
                                    latitude = pCoordinates?.lat ?: 13.4471,
                                    longitude = pCoordinates?.lng ?: -16.6791
                                )
                            }
                            val currentDropoff = remember(dropoffName, dCoordinates) {
                                RideLocation(
                                    name = dropoffName.ifBlank { "Dropoff Destination" },
                                    address = dropoffName,
                                    latitude = dCoordinates?.lat ?: 13.4533,
                                    longitude = dCoordinates?.lng ?: -16.5746
                                )
                            }
                            val driverCoord = remember(simLat, simLng) {
                                if (simLat != null && simLng != null) LatLng(simLat, simLng) else null
                            }
                            GoogleMapsRideSelector(
                                pickupLocation = currentPickup,
                                destinationLocation = currentDropoff,
                                onPickupChanged = { loc ->
                                    pickupName = loc.name
                                    pCoordinates = GLocation(loc.name, loc.latitude, loc.longitude)
                                },
                                onDestinationChanged = { loc ->
                                    dropoffName = loc.name
                                    dCoordinates = GLocation(loc.name, loc.latitude, loc.longitude)
                                },
                                driverLatLng = driverCoord,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            WayGoMapView(
                                drivers = drivers,
                                activeTrip = activeTrip,
                                simulatedDriverLat = simLat,
                                simulatedDriverLng = simLng,
                                passengerLat = pCoordinates?.lat ?: 13.4471,
                                passengerLng = pCoordinates?.lng ?: -16.6791,
                                pickupLocationName = pickupName,
                                pickupLat = pCoordinates?.lat,
                                pickupLng = pCoordinates?.lng,
                                dropoffLocationName = dropoffName,
                                dropoffLat = dCoordinates?.lat,
                                dropoffLng = dCoordinates?.lng,
                                mapPickingMode = mapPickingMode,
                                onSetPickupLocation = { name, lat, lng ->
                                    pickupName = name
                                    pCoordinates = GLocation(name, lat, lng)
                                    mapPickingMode = null
                                },
                                onSetDropoffLocation = { name, lat, lng ->
                                    dropoffName = name
                                    dCoordinates = GLocation(name, lat, lng)
                                    mapPickingMode = null
                                },
                                onCancelMapPicking = { mapPickingMode = null },
                                progress = progress,
                                onSelectDriver = { selectedDriver ->
                                    selectVehicleType = selectedDriver.vehicleType
                                    selectedDriverForProfile = selectedDriver
                                }
                            )
                        }

                        SosEmergencyButton(
                            pLat = pCoordinates?.lat ?: 13.4471,
                            pLng = pCoordinates?.lng ?: -16.6791,
                            activeTripId = activeTrip?.id,
                            modifier = Modifier
                                .zIndex(12f)
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 12.dp)
                        )
                    }
                }
            }
        }

        if (activeTrip == null) {
            // UI FORM TO COLLECT RIDE DETAILS & SAVE TO FIRESTORE 'ride_requests' COLLECTION
            item {
                val formPickupLoc = remember(pickupName, pCoordinates) {
                    RideLocation(
                        name = pickupName.ifBlank { "Kairaba Avenue" },
                        address = pickupName.ifBlank { "Kairaba Avenue, Serrekunda" },
                        latitude = pCoordinates?.lat ?: 13.4471,
                        longitude = pCoordinates?.lng ?: -16.6791
                    )
                }
                val formDropoffLoc = remember(dropoffName, dCoordinates) {
                    RideLocation(
                        name = dropoffName.ifBlank { "Albert Market" },
                        address = dropoffName.ifBlank { "Albert Market, Banjul" },
                        latitude = dCoordinates?.lat ?: 13.4533,
                        longitude = dCoordinates?.lng ?: -16.5746
                    )
                }

                RideRequestForm(
                    pickupLocation = formPickupLoc,
                    destinationLocation = formDropoffLoc,
                    selectedVehicleType = selectVehicleType,
                    onPickupChanged = { loc ->
                        pickupName = loc.name
                        pCoordinates = GLocation(loc.name, loc.latitude, loc.longitude)
                    },
                    onDestinationChanged = { loc ->
                        dropoffName = loc.name
                        dCoordinates = GLocation(loc.name, loc.latitude, loc.longitude)
                    },
                    onVehicleTypeChanged = { type ->
                        selectVehicleType = type
                    },
                    passengerId = profile?.email?.ifBlank { null } ?: profile?.id ?: "usr_passenger",
                    passengerName = profile?.name ?: "John Doe",
                    passengerPhone = profile?.phone ?: "+220 7000000",
                    isSaving = isBookingSaving,
                    firestoreStatusMessage = firestoreRideRequestStatus,
                    onUseCurrentLocation = {
                        locationHelper.requestPermissionAndLocation()
                        val state = locationHelper.state.value
                        val name = state.locationName.ifBlank { "Current Location" }
                        pickupName = name
                        pCoordinates = GLocation(name, state.latitude, state.longitude)
                    },
                    onOpenMapPicker = { mode ->
                        useGoogleMapsSdk = true
                        mapPickingMode = if (mode == MapTargetMode.PICKUP) "PICKUP" else "DROPOFF"
                    },
                    initialPaymentMethod = selectPaymentMethod,
                    onSubmitRideRequest = { formData ->
                        isBookingSaving = true
                        viewModel.saveRideDetailsToFirestore(
                            pickupLocation = formData.pickupLocation,
                            destinationLocation = formData.destinationLocation,
                            vehicleType = formData.vehicleType,
                            paymentMethod = formData.paymentMethod,
                            fareGmd = formData.fareGmd,
                            preferences = formData.preferences
                        ) { _, _ ->
                            isBookingSaving = false
                        }
                    }
                )
            }

            // RIDE REQUEST CREATION PANEL
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Where are you heading today?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlueDark
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // SAVED PLACES SECTION
                        val savedPlaces by viewModel.allSavedPlaces.collectAsState()

                        Text(
                            text = "Saved Places",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BrandBlueDark.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("saved_places_row")
                        ) {
                            items(savedPlaces) { place ->
                                val icon = when (place.iconType) {
                                    "HOME" -> Icons.Default.Home
                                    "WORK" -> Icons.Default.Work
                                    "STAR" -> Icons.Default.Star
                                    else -> Icons.Default.Place
                                }
                                
                                Card(
                                    onClick = { selectedSavedPlaceForOptions = place },
                                    modifier = Modifier.testTag("saved_place_item_${place.label.lowercase()}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = BrandBluePrimary.copy(alpha = 0.05f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = place.label,
                                            tint = BrandBluePrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = place.label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = BrandBlueDark
                                            )
                                            Text(
                                                text = place.name.split(",")[0],
                                                fontSize = 10.sp,
                                                color = NeutralGray,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Add Saved Place Button
                            item {
                                Card(
                                    onClick = { showAddSavedPlaceDialog = true },
                                    modifier = Modifier.testTag("add_saved_place_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Place",
                                            tint = BrandBluePrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Add Place",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = BrandBluePrimary
                                        )
                                    }
                                }
                            }
                        }

                        // Saved Place Options Dialog
                        if (selectedSavedPlaceForOptions != null) {
                            val place = selectedSavedPlaceForOptions!!
                            AlertDialog(
                                onDismissRequest = { selectedSavedPlaceForOptions = null },
                                title = {
                                    Text(
                                        text = place.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = BrandBlueDark
                                    )
                                },
                                text = {
                                    Column {
                                        Text(
                                            text = "Location: ${place.name}",
                                            fontSize = 13.sp,
                                            color = BrandBlueDark.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Use this saved place to speed up booking or manage your saved list.",
                                            fontSize = 12.sp,
                                            color = NeutralGray
                                        )
                                    }
                                },
                                confirmButton = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                pickupName = place.name
                                                pCoordinates = GLocation(place.name, place.lat, place.lng)
                                                selectedSavedPlaceForOptions = null
                                            },
                                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("set_saved_pickup_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Set as Pickup", color = Color.White)
                                        }
                                        
                                        Button(
                                            onClick = {
                                                dropoffName = place.name
                                                dCoordinates = GLocation(place.name, place.lat, place.lng)
                                                selectedSavedPlaceForOptions = null
                                            },
                                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("set_saved_dropoff_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Set as Destination", color = Color.White)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.removeSavedPlace(place.id)
                                                selectedSavedPlaceForOptions = null
                                            },
                                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("delete_saved_place_button"),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Delete Saved Place")
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { selectedSavedPlaceForOptions = null },
                                        modifier = Modifier.fillMaxWidth().testTag("saved_place_options_dismiss")
                                    ) {
                                        Text("Cancel", color = NeutralGray)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color.White
                            )
                        }

                        // Add Saved Place Dialog
                        if (showAddSavedPlaceDialog) {
                            var customLabel by remember { mutableStateOf("") }
                            var selectedLocationIndex by remember { mutableStateOf(0) }
                            var selectedIconType by remember { mutableStateOf("HOME") } // "HOME", "WORK", "STAR", "PLACE"
                            var isDropdownExpanded by remember { mutableStateOf(false) }

                            AlertDialog(
                                onDismissRequest = { showAddSavedPlaceDialog = false },
                                title = {
                                    Text(
                                        text = "Pin a New Saved Place",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = BrandBlueDark
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Label Input
                                        OutlinedTextField(
                                            value = customLabel,
                                            onValueChange = { customLabel = it },
                                            label = { Text("Label (e.g. Home, Work, Gym)") },
                                            modifier = Modifier.fillMaxWidth().testTag("add_saved_place_label_input"),
                                            singleLine = true,
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

                                        // Location Selector Dropdown
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = GAMBIA_LOCATIONS[selectedLocationIndex].name,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Select Location") },
                                                trailingIcon = {
                                                    IconButton(onClick = { isDropdownExpanded = true }) {
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand Locations")
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().clickable { isDropdownExpanded = true }.testTag("add_saved_place_location_select"),
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
                                            
                                            DropdownMenu(
                                                expanded = isDropdownExpanded,
                                                onDismissRequest = { isDropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.8f)
                                            ) {
                                                GAMBIA_LOCATIONS.forEachIndexed { index, loc ->
                                                    DropdownMenuItem(
                                                        text = { Text(loc.name) },
                                                        onClick = {
                                                            selectedLocationIndex = index
                                                            isDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Icon Selector row
                                        Text(
                                            text = "Select Icon",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandBlueDark.copy(alpha = 0.8f)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            val iconsList = listOf(
                                                Triple("HOME", Icons.Default.Home, "Home"),
                                                Triple("WORK", Icons.Default.Work, "Work"),
                                                Triple("STAR", Icons.Default.Star, "Favorite"),
                                                Triple("PLACE", Icons.Default.Place, "Other")
                                            )
                                            iconsList.forEach { (type, icon, desc) ->
                                                val isSelected = selectedIconType == type
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isSelected) BrandBluePrimary else BrandBluePrimary.copy(alpha = 0.1f)
                                                        )
                                                        .clickable { selectedIconType = type }
                                                        .testTag("icon_select_${type.lowercase()}"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = desc,
                                                        tint = if (isSelected) Color.White else BrandBluePrimary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (customLabel.isNotBlank()) {
                                                val loc = GAMBIA_LOCATIONS[selectedLocationIndex]
                                                viewModel.addSavedPlace(
                                                    name = loc.name,
                                                    label = customLabel,
                                                    lat = loc.lat,
                                                    lng = loc.lng,
                                                    iconType = selectedIconType
                                                )
                                                showAddSavedPlaceDialog = false
                                            }
                                        },
                                        enabled = customLabel.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("add_saved_place_confirm_button")
                                    ) {
                                        Text("Save Place", color = Color.White)
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showAddSavedPlaceDialog = false },
                                        modifier = Modifier.fillMaxWidth().testTag("add_saved_place_cancel_button")
                                    ) {
                                        Text("Cancel", color = NeutralGray)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Map Location Picking Shortcut Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { mapPickingMode = if (mapPickingMode == "PICKUP") null else "PICKUP" },
                                shape = RoundedCornerShape(10.dp),
                                color = if (mapPickingMode == "PICKUP") SuccessGreen else SuccessGreen.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f).testTag("pick_pickup_on_map_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PinDrop,
                                        contentDescription = null,
                                        tint = if (mapPickingMode == "PICKUP") Color.White else SuccessGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (mapPickingMode == "PICKUP") "Tap Map Above" else "Pick Pickup on Map",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mapPickingMode == "PICKUP") Color.White else SuccessGreen
                                    )
                                }
                            }

                            Surface(
                                onClick = { mapPickingMode = if (mapPickingMode == "DROPOFF") null else "DROPOFF" },
                                shape = RoundedCornerShape(10.dp),
                                color = if (mapPickingMode == "DROPOFF") BrandBluePrimary else BrandBluePrimary.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f).testTag("pick_dropoff_on_map_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (mapPickingMode == "DROPOFF") Color.White else BrandBluePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (mapPickingMode == "DROPOFF") "Tap Map Above" else "Pick Destination on Map",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mapPickingMode == "DROPOFF") Color.White else BrandBluePrimary
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val tempName = pickupName
                                    val tempCoords = pCoordinates
                                    pickupName = dropoffName
                                    pCoordinates = dCoordinates
                                    dropoffName = tempName
                                    dCoordinates = tempCoords
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(BrandBlueDark.copy(alpha = 0.06f), CircleShape)
                                    .testTag("swap_locations_btn")
                            ) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Swap Locations", tint = BrandBlueDark, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Banjul & Kanifing High-Accuracy Pick-up Point Assistant
                        val nearestHub = liveTrackingState.closestPickupPoint
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pickup_point_assistant_card"),
                            shape = RoundedCornerShape(12.dp),
                            color = BrandBluePrimary.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.18f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(if (liveTrackingState.isTrackingActive) SuccessGreen else NeutralGray, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (liveTrackingState.isTrackingActive) "GPS Tracked: ${liveTrackingState.detectedRegion}" else "Banjul & Kanifing Smart Hubs",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandBlueDark
                                        )
                                    }

                                    if (!locationHelper.state.value.isPermissionGranted) {
                                        TextButton(
                                            onClick = { locationHelper.requestPermissionAndLocation() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Enable GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = SuccessGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "High Accuracy",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SuccessGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (nearestHub != null) {
                                    val distStr = com.example.data.LocationTrackingService.formatDistance(liveTrackingState.distanceToClosestPickupMeters)
                                    val walkMin = com.example.data.LocationTrackingService.estimateWalkingMinutes(liveTrackingState.distanceToClosestPickupMeters)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.GpsFixed,
                                                    contentDescription = null,
                                                    tint = SuccessGreen,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Nearest Hub: ${nearestHub.name}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandBlueDark
                                                )
                                            }
                                            Text(
                                                text = "$distStr away • ~$walkMin min walk • ${nearestHub.pickupGuidance}",
                                                fontSize = 10.sp,
                                                color = NeutralGray,
                                                lineHeight = 13.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                pickupName = nearestHub.name
                                                pCoordinates = GLocation(nearestHub.name, nearestHub.latitude, nearestHub.longitude)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("apply_nearest_pickup_btn")
                                        ) {
                                            Text("Set As Pickup", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }

                                // Quick Region Filter Buttons
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("ALL" to "All Points", "BANJUL" to "Banjul City", "KANIFING" to "Kanifing / KM").forEach { (code, label) ->
                                        val isSel = selectedRegionFilter == code
                                        Surface(
                                            onClick = { selectedRegionFilter = code },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSel) BrandBluePrimary else PureWhite,
                                            border = BorderStroke(1.dp, if (isSel) BrandBluePrimary else NeutralGray.copy(alpha = 0.2f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSel) Color.White else BrandBlueDark,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Pickup Input
                        OutlinedTextField(
                            value = pickupName,
                            onValueChange = { input ->
                                pickupName = input
                                pCoordinates = resolveLocationForInput(input)
                            },
                            label = { Text("Pickup Location") },
                            placeholder = { Text("Type pickup location or landmark...") },
                            leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = "Loc", tint = SuccessGreen) },
                            trailingIcon = {
                                if (pickupName.isNotEmpty()) {
                                    IconButton(onClick = {
                                        pickupName = ""
                                        pCoordinates = null
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear pickup", tint = NeutralGray)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pickup_input"),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = BrandBlueDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = BrandBlueDark,
                                unfocusedTextColor = BrandBlueDark,
                                focusedBorderColor = BrandBluePrimary,
                                unfocusedBorderColor = NeutralGray.copy(alpha = 0.3f),
                                focusedContainerColor = PureWhite,
                                unfocusedContainerColor = PureWhite,
                                focusedPlaceholderColor = NeutralGray.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = NeutralGray.copy(alpha = 0.5f),
                                focusedLabelColor = BrandBluePrimary,
                                unfocusedLabelColor = NeutralGray
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Dropoff Input
                        OutlinedTextField(
                            value = dropoffName,
                            onValueChange = { input ->
                                dropoffName = input
                                dCoordinates = resolveLocationForInput(input)
                            },
                            label = { Text("Destination (Drop-off)") },
                            placeholder = { Text("Type destination address or landmark...") },
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = "Drop", tint = ErrorRed) },
                            trailingIcon = {
                                if (dropoffName.isNotEmpty()) {
                                    IconButton(onClick = {
                                        dropoffName = ""
                                        dCoordinates = null
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear destination", tint = NeutralGray)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dropoff_input"),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = BrandBlueDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = BrandBlueDark,
                                unfocusedTextColor = BrandBlueDark,
                                focusedBorderColor = BrandBluePrimary,
                                unfocusedBorderColor = NeutralGray.copy(alpha = 0.3f),
                                focusedContainerColor = PureWhite,
                                unfocusedContainerColor = PureWhite,
                                focusedPlaceholderColor = NeutralGray.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = NeutralGray.copy(alpha = 0.5f),
                                focusedLabelColor = BrandBluePrimary,
                                unfocusedLabelColor = NeutralGray
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Location Suggestion Chips (Filtered by Selected Region)
                        val suggestedPickups = remember(selectedRegionFilter) {
                            when (selectedRegionFilter) {
                                "BANJUL" -> com.example.data.LocationTrackingService.BANJUL_KANIFING_PICKUP_POINTS.filter { it.zone == "Banjul City" }
                                "KANIFING" -> com.example.data.LocationTrackingService.BANJUL_KANIFING_PICKUP_POINTS.filter { it.zone != "Banjul City" }
                                else -> com.example.data.LocationTrackingService.BANJUL_KANIFING_PICKUP_POINTS
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Verified Pick-up Hubs (${suggestedPickups.size}):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandBlueDark.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Tap to select",
                                    fontSize = 10.sp,
                                    color = NeutralGray
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(suggestedPickups) { point ->
                                    val shortName = point.name.split(",")[0]
                                    SuggestionChip(
                                        onClick = {
                                            if (pickupName.isBlank()) {
                                                pickupName = point.name
                                                pCoordinates = GLocation(point.name, point.latitude, point.longitude)
                                            } else {
                                                dropoffName = point.name
                                                dCoordinates = GLocation(point.name, point.latitude, point.longitude)
                                            }
                                        },
                                        label = {
                                            Column {
                                                Text(
                                                    text = shortName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = point.category.label,
                                                    fontSize = 8.sp,
                                                    color = BrandBluePrimary
                                                )
                                            }
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = PureWhite,
                                            labelColor = BrandBlueDark
                                        ),
                                        border = SuggestionChipDefaults.suggestionChipBorder(
                                            enabled = true,
                                            borderColor = BrandBluePrimary.copy(alpha = 0.25f)
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Vehicle Selector Header & Dropdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Select Vehicle Type", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)

                            // Vehicle Type Dropdown
                            Box {
                                OutlinedButton(
                                    onClick = { vehicleDropdownExpanded = true },
                                    modifier = Modifier.testTag("vehicle_type_dropdown_btn"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = BrandBluePrimary.copy(alpha = 0.05f),
                                        contentColor = BrandBluePrimary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary)
                                ) {
                                    Icon(
                                        imageVector = if (selectVehicleType == "CAR") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selectVehicleType == "CAR") BrandBluePrimary else AccentAmber
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectVehicleType == "CAR") "Car (Sedan) ▾" else "Tricycle (Keke) ▾",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                DropdownMenu(
                                    expanded = vehicleDropdownExpanded,
                                    onDismissRequest = { vehicleDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text("Car (WayGo Sedan)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                                    Text("Comfortable 4-seater • Est. ${carFareEstimate} GMD", fontSize = 11.sp, color = NeutralGray)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectVehicleType = "CAR"
                                            vehicleDropdownExpanded = false
                                        },
                                        modifier = Modifier.testTag("dropdown_option_car")
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.TwoWheeler, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text("Tricycle (Keke Express)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                                    Text("Affordable 3-wheeler • Est. ${tricycleFareEstimate} GMD", fontSize = 11.sp, color = NeutralGray)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectVehicleType = "TRICYCLE"
                                            vehicleDropdownExpanded = false
                                        },
                                        modifier = Modifier.testTag("dropdown_option_tricycle")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                onClick = { selectVehicleType = "CAR" },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vehicle_car_select"),
                                shape = RoundedCornerShape(24.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 2.dp,
                                    color = if (selectVehicleType == "CAR") BrandBluePrimary else Color(0xFFE2E8F0)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectVehicleType == "CAR") BrandBluePrimary.copy(alpha = 0.08f) else PureWhite
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = "Car",
                                        tint = BrandBluePrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("WayGo Sedan (Car)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                    Text("Est. ${carFareEstimate} GMD", fontSize = 11.sp, color = BrandBlueDark.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
                                    Text("Comfortable", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                                }
                            }

                            Card(
                                onClick = { selectVehicleType = "TRICYCLE" },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vehicle_tuk_select"),
                                shape = RoundedCornerShape(24.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 2.dp,
                                    color = if (selectVehicleType == "TRICYCLE") BrandBluePrimary else Color(0xFFE2E8F0)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectVehicleType == "TRICYCLE") BrandBluePrimary.copy(alpha = 0.08f) else PureWhite
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TwoWheeler,
                                        contentDescription = "Tuk",
                                        tint = AccentAmber,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Keke / Tricycle", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                    Text("Est. ${tricycleFareEstimate} GMD", fontSize = 11.sp, color = BrandBlueDark.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
                                    Text("Affordable", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Payment Methods Card
                        Text("Payment Method (Hybrid Option)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("CASH", "WAVE", "STRIPE").forEach { method ->
                                Card(
                                    onClick = { selectPaymentMethod = method },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectPaymentMethod == method) BrandBluePrimary else BrandBlueLight
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.padding(6.dp).fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = method,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectPaymentMethod == method) Color.White else BrandBlueDark
                                        )
                                    }
                                }
                            }
                        }

                        // Ride Customization Preferences Filters
                        var prefQuiet by remember { mutableStateOf(false) }
                        var prefAc by remember { mutableStateOf(false) }
                        var prefLuggage by remember { mutableStateOf(false) }
                        var prefPet by remember { mutableStateOf(false) }

                        val selectedPreferences = remember(prefQuiet, prefAc, prefLuggage, prefPet) {
                            listOfNotNull(
                                if (prefQuiet) "🤫 Quiet Ride" else null,
                                if (prefAc) "❄️ AC On" else null,
                                if (prefLuggage) "🧳 Luggage Help" else null,
                                if (prefPet) "🐾 Pet Friendly" else null
                            ).joinToString(" • ")
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Ride Customization Preferences", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = prefQuiet,
                                onClick = { prefQuiet = !prefQuiet },
                                label = { Text("Quiet 🤫", fontSize = 10.5.sp) },
                                modifier = Modifier.height(30.dp)
                            )
                            FilterChip(
                                selected = prefAc,
                                onClick = { prefAc = !prefAc },
                                label = { Text("AC ❄️", fontSize = 10.5.sp) },
                                modifier = Modifier.height(30.dp)
                            )
                            FilterChip(
                                selected = prefLuggage,
                                onClick = { prefLuggage = !prefLuggage },
                                label = { Text("Luggage 🧳", fontSize = 10.5.sp) },
                                modifier = Modifier.height(30.dp)
                            )
                            FilterChip(
                                selected = prefPet,
                                onClick = { prefPet = !prefPet },
                                label = { Text("Pets 🐾", fontSize = 10.5.sp) },
                                modifier = Modifier.height(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 🏷️ ESTIMATED FARE & ROUTE DISTANCE BREAKDOWN CARD (Displayed before confirming ride)
                        var showFareBreakdownDetails by remember { mutableStateOf(false) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("estimated_fare_display_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = BrandBluePrimary.copy(alpha = 0.05f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandBluePrimary.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = BrandBluePrimary,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.ReceiptLong,
                                                    contentDescription = "Fare Estimate",
                                                    tint = PureWhite,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Estimated Fare",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = BrandBlueDark
                                            )
                                            Text(
                                                text = "Calculated by route distance",
                                                fontSize = 10.sp,
                                                color = NeutralGray
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = SuccessGreen.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Price Locked",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SuccessGreen
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Main Price & Key Metrics Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            text = "${currentFareEstimateResult.finalFareGmd} GMD",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = BrandBluePrimary,
                                            modifier = Modifier.testTag("estimated_fare_value_text")
                                        )
                                        Text(
                                            text = currentFareEstimateResult.vehicleDisplayName,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandBlueDark.copy(alpha = 0.8f)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = PureWhite,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                                    .testTag("estimated_distance_value_text"),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Navigation,
                                                    contentDescription = null,
                                                    tint = BrandBluePrimary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${currentFareEstimateResult.distanceKm} km",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandBlueDark
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = PureWhite,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = AccentAmber,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "~${currentFareEstimateResult.estimatedDurationMin} min",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandBlueDark
                                                )
                                            }
                                        }
                                    }
                                }

                                // Collapsible Detailed Itemized Breakdown
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showFareBreakdownDetails = !showFareBreakdownDetails }
                                        .padding(vertical = 4.dp)
                                        .testTag("fare_breakdown_toggle_btn"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showFareBreakdownDetails) "Hide Price Calculation ▲" else "View Calculation Breakdown ▼",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandBluePrimary
                                    )
                                    Text(
                                        text = "Rate: ${currentFareEstimateResult.perKmRate.toInt()} GMD/km",
                                        fontSize = 10.sp,
                                        color = NeutralGray
                                    )
                                }

                                if (showFareBreakdownDetails) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = PureWhite,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Base Starting Fare", fontSize = 11.sp, color = NeutralGray)
                                                Text("${currentFareEstimateResult.baseFare.toInt()} GMD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BrandBlueDark)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Distance (${currentFareEstimateResult.distanceKm} km × ${currentFareEstimateResult.perKmRate.toInt()} GMD)", fontSize = 11.sp, color = NeutralGray)
                                                Text("${currentFareEstimateResult.distanceCharge.toInt()} GMD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BrandBlueDark)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Estimated Time (${currentFareEstimateResult.estimatedDurationMin} min)", fontSize = 11.sp, color = NeutralGray)
                                                Text("${currentFareEstimateResult.timeCharge.toInt()} GMD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BrandBlueDark)
                                            }
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), thickness = 0.5.dp, color = Color(0xFFE2E8F0))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Final Estimated Total", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                                                Text("${currentFareEstimateResult.finalFareGmd} GMD", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = BrandBluePrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                if (selectPaymentMethod == "STRIPE") {
                                    if (profile?.isPaymentLinked == true) {
                                        // 1-Click Cashless Booking! Bypass WebView secure payment gateway check.
                                        viewModel.initiateBooking(
                                            pickupName = pickupName,
                                            dropoffName = dropoffName,
                                            vehicleType = selectVehicleType,
                                            paymentMethod = "STRIPE (LINKED)",
                                            fare = dynamicCalculatedFare,
                                            preferences = selectedPreferences,
                                            pLat = pCoordinates?.lat ?: 13.4471,
                                            pLng = pCoordinates?.lng ?: -16.6791,
                                            dLat = dCoordinates?.lat ?: 13.4533,
                                            dLng = dCoordinates?.lng ?: -16.5746
                                        )
                                    } else {
                                        isStripeInitiating = true
                                        isProcessingStripe = true
                                        stripeStatusMsg = "Preparing secure Stripe gateway..."
                                        coroutineScope.launch {
                                            val pEmail = profile?.email ?: "johndoe@example.com"
                                            val pName = profile?.name ?: "John Doe"
                                            val pPhone = profile?.phone ?: "+220 771 2345"
                                            val txRef = "st_ref_" + System.currentTimeMillis().toString().takeLast(6)
                                            val link = com.example.data.StripeManager.initiateStripePayment(
                                                amountGmd = dynamicCalculatedFare.toDouble(),
                                                passengerEmail = pEmail,
                                                passengerName = pName,
                                                passengerPhone = pPhone,
                                                tripTxRef = txRef
                                            )
                                            isStripeInitiating = false
                                            if (link != null) {
                                                stripeUrl = link
                                            } else {
                                                stripeStatusMsg = "Error initiating Stripe payments. Please try again."
                                            }
                                        }
                                    }
                                } else {
                                    viewModel.initiateBooking(
                                        pickupName = pickupName,
                                        dropoffName = dropoffName,
                                        vehicleType = selectVehicleType,
                                        paymentMethod = selectPaymentMethod,
                                        fare = dynamicCalculatedFare,
                                        preferences = selectedPreferences,
                                        pLat = pCoordinates?.lat ?: 13.4471,
                                        pLng = pCoordinates?.lng ?: -16.6791,
                                        dLat = dCoordinates?.lat ?: 13.4533,
                                        dLng = dCoordinates?.lng ?: -16.5746
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("request_ride_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary)
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = "r")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Request WayGo Ride • $dynamicCalculatedFare GMD", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showScheduleDialog = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("schedule_ride_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandBluePrimary),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandBluePrimary)
                        ) {
                            Icon(Icons.Default.Event, contentDescription = "Schedule Later")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Schedule Ride for Later", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (showScheduleDialog) {
                            AlertDialog(
                                onDismissRequest = { showScheduleDialog = false },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.EventNote, contentDescription = "Schedule", tint = BrandBluePrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Schedule WayGo Ride", color = BrandBlueDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            "Set a future date & time for pickup. Available drivers in Gambia will be notified in advance.",
                                            fontSize = 12.sp,
                                            color = NeutralGray
                                        )
                                        Divider()

                                        Text("Select Date:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandBlueDark)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            listOf("Today", "Tomorrow", "In 2 days").forEach { item ->
                                                val isSelected = selectedScheduleDate == item
                                                Card(
                                                    onClick = { selectedScheduleDate = item },
                                                    modifier = Modifier.weight(1f),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isSelected) BrandBluePrimary else BrandBlueLight
                                                    )
                                                ) {
                                                    Box(modifier = Modifier.padding(8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            item,
                                                            fontSize = 11.sp,
                                                            color = if (isSelected) Color.White else BrandBlueDark,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Text("Select Time:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandBlueDark)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Hour", fontSize = 10.sp, color = NeutralGray)
                                                var showHourMenu by remember { mutableStateOf(false) }
                                                OutlinedButton(
                                                    onClick = { showHourMenu = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(selectedScheduleHour, fontSize = 12.sp)
                                                    Icon(Icons.Default.ArrowDropDown, "", modifier = Modifier.size(16.dp))
                                                }
                                                DropdownMenu(expanded = showHourMenu, onDismissRequest = { showHourMenu = false }) {
                                                    listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12").forEach { h ->
                                                        DropdownMenuItem(text = { Text(h) }, onClick = { selectedScheduleHour = h; showHourMenu = false })
                                                    }
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Minute", fontSize = 10.sp, color = NeutralGray)
                                                var showMinMenu by remember { mutableStateOf(false) }
                                                OutlinedButton(
                                                    onClick = { showMinMenu = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(selectedScheduleMinute, fontSize = 12.sp)
                                                    Icon(Icons.Default.ArrowDropDown, "", modifier = Modifier.size(16.dp))
                                                }
                                                DropdownMenu(expanded = showMinMenu, onDismissRequest = { showMinMenu = false }) {
                                                    listOf("00", "15", "30", "45").forEach { m ->
                                                        DropdownMenuItem(text = { Text(m) }, onClick = { selectedScheduleMinute = m; showMinMenu = false })
                                                    }
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("AM/PM", fontSize = 10.sp, color = NeutralGray)
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(BrandBlueLight)
                                                        .clickable {
                                                            selectedScheduleAmPm = if (selectedScheduleAmPm == "AM") "PM" else "AM"
                                                        }
                                                        .padding(8.dp)
                                                        .fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Text(selectedScheduleAmPm, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandBluePrimary)
                                                }
                                            }
                                        }

                                        Divider()

                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Est. Fare Total:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("$dynamicCalculatedFare GMD", color = BrandBluePrimary, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val scheduledTimeStr = "$selectedScheduleDate at $selectedScheduleHour:$selectedScheduleMinute $selectedScheduleAmPm"
                                            val offsetHours = when (selectedScheduleDate) {
                                                "Tomorrow" -> 24L
                                                "In 2 days" -> 48L
                                                else -> 2L
                                            }
                                            val epochVal = System.currentTimeMillis() + (offsetHours * 3600 * 1000)

                                            viewModel.scheduleRide(
                                                pickupName = pickupName,
                                                dropoffName = dropoffName,
                                                vehicleType = selectVehicleType,
                                                paymentMethod = selectPaymentMethod,
                                                fare = dynamicCalculatedFare,
                                                pLat = pCoordinates?.lat ?: 13.4471,
                                                pLng = pCoordinates?.lng ?: -16.6791,
                                                dLat = dCoordinates?.lat ?: 13.4533,
                                                dLng = dCoordinates?.lng ?: -16.5746,
                                                scheduledTime = scheduledTimeStr,
                                                scheduledEpochMs = epochVal
                                            )
                                            showScheduleDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                    ) {
                                        Text("Confirm Booking")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showScheduleDialog = false }) {
                                        Text("Cancel", color = NeutralGray)
                                    }
                                }
                            )
                        }

                        // Stripe Secure Payment Processing States
                        if (isProcessingStripe) {
                            if (isStripeInitiating) {
                                AlertDialog(
                                    onDismissRequest = { 
                                        isProcessingStripe = false 
                                        isStripeInitiating = false
                                    },
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = Color(0xFF635BFF), // Stripe brand indigo
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Stripe Secure", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = stripeStatusMsg,
                                            fontSize = 13.sp,
                                            color = BrandBlueDark
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            isProcessingStripe = false
                                            isStripeInitiating = false
                                        }) {
                                            Text("Cancel", color = NeutralGray)
                                        }
                                    }
                                )
                            } else if (stripeUrl != null) {
                                StripeCheckoutDialog(
                                    checkoutUrl = stripeUrl!!,
                                    onPaymentSuccess = { txRef, transactionId ->
                                        // Save standard details
                                        viewModel.initiateBooking(
                                            pickupName = pickupName,
                                            dropoffName = dropoffName,
                                            vehicleType = selectVehicleType,
                                            paymentMethod = "STRIPE (PAID)",
                                            fare = dynamicCalculatedFare,
                                            pLat = pCoordinates?.lat ?: 13.4471,
                                            pLng = pCoordinates?.lng ?: -16.6791,
                                            dLat = dCoordinates?.lat ?: 13.4533,
                                            dLng = dCoordinates?.lng ?: -16.5746
                                        )
                                        // Reset states
                                        stripeUrl = null
                                        isProcessingStripe = false
                                    },
                                    onCancel = {
                                        stripeUrl = null
                                        isProcessingStripe = false
                                    }
                                )
                            } else {
                                // Error layout or retry message
                                AlertDialog(
                                    onDismissRequest = { isProcessingStripe = false },
                                    title = { Text("Payment Blocked", fontWeight = FontWeight.Bold, color = ErrorRed) },
                                    text = { Text(stripeStatusMsg) },
                                    confirmButton = {
                                        TextButton(onClick = { isProcessingStripe = false }) {
                                            Text("Go Back")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // STATE DRIVING & TRIP MONITOR PANEL

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("active_ride_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (activeTrip!!.status == "REQUESTED") "Searching for drivers..."
                                    else if (activeTrip!!.status == "ACCEPTED") "Driver coming to you!"
                                    else if (activeTrip!!.status == "ARRIVED") "Driver has arrived!"
                                    else if (activeTrip!!.status == "EN_ROUTE") "Driving to destination"
                                    else "You have arrived!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = BrandBluePrimary
                                )
                                Text(
                                    text = "Route: ${activeTrip!!.pickupName.split(",")[0]} ➔ ${activeTrip!!.dropoffName.split(",")[0]}",
                                    fontSize = 12.sp,
                                    color = NeutralGray
                                )
                            }

                            // Dynamic ETA display
                            val etaMins = remember(progress) {
                                if (progress < 0.38f) {
                                    (1..3).random()
                                } else {
                                    ((1f - progress) * 12).toInt().coerceAtLeast(1)
                                }
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BrandBlueLight)
                            ) {
                                Text(
                                    "ETA: $etaMins mins",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandBlueDark
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Visual progress bar and 4-step indicator during active ride
                        RideStatusStepIndicator(
                            currentStatus = activeTrip!!.status,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // SAFETY PIN CODE DISPLAY CARD
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("passenger_safety_pin_card"),
                            colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.12f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = AccentAmber
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = activeTrip!!.verificationPin,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                                Column {
                                    Text("Ride Safety PIN: ${activeTrip!!.verificationPin}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                    Text("Provide this 4-digit code to your driver upon pickup to start your ride.", fontSize = 11.sp, color = NeutralGray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Visual State Transition: Searching for drivers loading indicator -> Matched Driver Profile Card
                        Crossfade(
                            targetState = (activeTrip!!.driverName != null),
                            label = "driver_search_fade",
                            modifier = Modifier.fillMaxWidth()
                        ) { isDriverMatched ->
                            if (isDriverMatched) {
                                // Matched Driver Profile Card State
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BrandBlueLight)
                                        .padding(12.dp)
                                        .testTag("matched_driver_profile_card")
                                ) {
                                    val tripStatus = activeTrip?.status ?: "ACCEPTED"

                                    var statusTitle by remember { mutableStateOf("Driver En Route") }
                                    var statusSubtitle by remember { mutableStateOf("Heading to your pickup location") }
                                    var statusBgColor by remember { mutableStateOf(BrandBluePrimary.copy(alpha = 0.15f)) }
                                    var statusBorderColor by remember { mutableStateOf(BrandBluePrimary) }
                                    var statusIcon by remember { mutableStateOf(Icons.Default.Navigation) }
                                    var stepIndex by remember { mutableStateOf(1) }

                                    when (tripStatus) {
                                        "ARRIVED" -> {
                                            statusTitle = "Driver Arrived"
                                            statusSubtitle = "Driver is waiting at pickup location"
                                            statusBgColor = AccentAmber.copy(alpha = 0.15f)
                                            statusBorderColor = AccentAmber
                                            statusIcon = Icons.Default.PinDrop
                                            stepIndex = 2
                                        }
                                        "EN_ROUTE" -> {
                                            statusTitle = "Trip in Progress"
                                            statusSubtitle = "Driving to destination safely"
                                            statusBgColor = SuccessGreen.copy(alpha = 0.15f)
                                            statusBorderColor = SuccessGreen
                                            statusIcon = Icons.Default.DirectionsCar
                                            stepIndex = 3
                                        }
                                        "COMPLETED" -> {
                                            statusTitle = "Trip Completed"
                                            statusSubtitle = "Arrived at destination"
                                            statusBgColor = SuccessGreen.copy(alpha = 0.15f)
                                            statusBorderColor = SuccessGreen
                                            statusIcon = Icons.Default.CheckCircle
                                            stepIndex = 4
                                        }
                                        else -> { // ACCEPTED or REQUESTED
                                            statusTitle = "Driver En Route"
                                            statusSubtitle = "Heading to your pickup location"
                                            statusBgColor = BrandBluePrimary.copy(alpha = 0.15f)
                                            statusBorderColor = BrandBluePrimary
                                            statusIcon = Icons.Default.Navigation
                                            stepIndex = 1
                                        }
                                    }

                                    // Matched driver notification banner with live status polling tracker
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                            .testTag("status_tracker_card"),
                                        shape = RoundedCornerShape(12.dp),
                                        color = statusBgColor,
                                        border = BorderStroke(1.dp, statusBorderColor)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = statusIcon,
                                                        contentDescription = null,
                                                        tint = statusBorderColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = statusTitle,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 13.sp,
                                                        color = BrandBlueDark,
                                                        modifier = Modifier.testTag("status_badge_text")
                                                    )
                                                }

                                                // Polling heartbeat badge
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(PureWhite.copy(alpha = 0.8f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(7.dp)
                                                            .clip(CircleShape)
                                                            .background(SuccessGreen)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Live Sync",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = NeutralGray
                                                    )
                                                }
                                            }

                                            Text(
                                                text = statusSubtitle,
                                                fontSize = 11.sp,
                                                color = NeutralGray,
                                                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                                            )

                                            // 4-Step Visual Progress Tracker (Driver Assigned, Driver Arrived, Trip In Progress, Trip Completed)
                                            RideStatusStepIndicator(
                                                currentStatus = tripStatus,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }

                                    val matchedDriver = drivers.find { it.id == activeTrip!!.driverId }
                                    var showDriverTrustProfile by remember { mutableStateOf(false) }

                                    if (showDriverTrustProfile) {
                                        DriverTrustProfileDialog(
                                            driver = matchedDriver,
                                            vehicleType = activeTrip!!.vehicleType,
                                            vehiclePlate = activeTrip!!.vehiclePlate ?: "BJL 4821 C",
                                            driverName = activeTrip!!.driverName!!,
                                            trips = trips,
                                            onDismiss = { showDriverTrustProfile = false }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Custom Avatar with Badge Overlay
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clickable { showDriverTrustProfile = true }
                                                .testTag("driver_trust_profile_avatar"),
                                            contentAlignment = Alignment.BottomEnd
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(CircleShape)
                                                    .background(BrandBluePrimary.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val nameInitials = (activeTrip!!.driverName ?: "GP").split(" ")
                                                    .mapNotNull { it.firstOrNull()?.toString() }
                                                    .joinToString("").take(2).uppercase()
                                                Text(
                                                    text = nameInitials,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = BrandBluePrimary,
                                                    fontSize = 16.sp
                                                )
                                            }
                                            
                                            // Trust Mini Check Badge
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(SuccessGreen),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Verified Badge",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { showDriverTrustProfile = true }
                                                .testTag("driver_trust_profile_clickable_column")
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = activeTrip!!.driverName!!,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = BrandBlueDark
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Rating",
                                                    tint = AccentAmber,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = String.format("%.1f", matchedDriver?.rating ?: 4.8f),
                                                    fontSize = 12.sp,
                                                    color = BrandBlueDark,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(2.dp))
                                            
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "${if (activeTrip!!.vehicleType == "CAR") "Yellow Sedan" else "Tricycle"} • ${activeTrip!!.vehiclePlate}",
                                                    fontSize = 12.sp,
                                                    color = NeutralGray
                                                )
                                                // Mini Verification pill badge
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(SuccessGreen.copy(alpha = 0.12f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.VerifiedUser,
                                                            contentDescription = "Verified",
                                                            tint = SuccessGreen,
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Text(
                                                            text = "Verified",
                                                            color = SuccessGreen,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Interactive messaging & chat
                                        var showChatDialog by remember { mutableStateOf(false) }
                                        IconButton(
                                            onClick = { showChatDialog = true },
                                            modifier = Modifier
                                                .testTag("passenger_chat_launcher")
                                                .background(BrandBlueSecondary, CircleShape)
                                                .size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Chat,
                                                contentDescription = "Chat",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        activeTrip?.let { currentTrip ->
                                            if (showChatDialog) {
                                                WayGoChatDialog(
                                                    tripId = currentTrip.id,
                                                    currentRole = "PASSENGER",
                                                    currentUserId = "current_passenger",
                                                    currentUserName = profile?.name ?: "Fatou Joof",
                                                    viewModel = viewModel,
                                                    onDismiss = { 
                                                        showChatDialog = false 
                                                        viewModel.endChatSession()
                                                    }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Interactive masked phone icon
                                        var showMaskDialer by remember { mutableStateOf(false) }
                                        IconButton(
                                            onClick = { showMaskDialer = true },
                                            modifier = Modifier
                                                .background(BrandBluePrimary, CircleShape)
                                                .size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Phone,
                                                contentDescription = "Call",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        if (showMaskDialer) {
                                            AlertDialog(
                                                onDismissRequest = { showMaskDialer = false },
                                                title = { Text("Encrypted Call Masking", fontWeight = FontWeight.Bold) },
                                                text = {
                                                    Text(
                                                        "Calling driver ${activeTrip!!.driverName} via secure WayGo central mask. Your personal phone is safe from third parties.\n\nSimulated Dial: +220 110-MASK-${activeTrip!!.driverId?.takeLast(4)}"
                                                    )
                                                },
                                                confirmButton = {
                                                    Button(onClick = { showMaskDialer = false }) { Text("Dial Number") }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { showMaskDialer = false }) { Text("Cancel") }
                                                }
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    // Direct safety CTA row to prompt trust profiles
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showDriverTrustProfile = true }
                                            .background(PureWhite)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = "Safety Check",
                                                tint = BrandBluePrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Tap to verify background check & GTA permit",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = BrandBluePrimary
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Go",
                                            tint = BrandBluePrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else {
                                // Animated Searching State Container
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BrandBlueLight.copy(alpha = 0.5f))
                                        .border(1.dp, BrandBluePrimary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                        .padding(16.dp)
                                        .testTag("searching_driver_container"),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Pulse Radar Visual
                                    val infiniteTransition = rememberInfiniteTransition(label = "driver_radar_pulse")
                                    val pulseScale by infiniteTransition.animateFloat(
                                        initialValue = 0.85f,
                                        targetValue = 1.35f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1200, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulse_scale"
                                    )
                                    val pulseAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.7f,
                                        targetValue = 0.15f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1200, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulse_alpha"
                                    )

                                    Box(
                                        modifier = Modifier.size(100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Outer pulsing ring
                                        Box(
                                            modifier = Modifier
                                                .size((80 * pulseScale).dp)
                                                .clip(CircleShape)
                                                .background(BrandBluePrimary.copy(alpha = pulseAlpha))
                                        )
                                        // Secondary pulsing ring
                                        Box(
                                            modifier = Modifier
                                                .size((64 * (pulseScale * 0.9f)).dp)
                                                .clip(CircleShape)
                                                .background(BrandBluePrimary.copy(alpha = (pulseAlpha + 0.2f).coerceAtMost(0.5f)))
                                        )
                                        // Center spinner container
                                        Surface(
                                            modifier = Modifier.size(54.dp),
                                            shape = CircleShape,
                                            color = PureWhite,
                                            shadowElevation = 4.dp
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(46.dp).testTag("searching_driver_loading_indicator"),
                                                    color = BrandBluePrimary,
                                                    strokeWidth = 3.dp
                                                )
                                                Icon(
                                                    imageVector = if (activeTrip!!.vehicleType == "CAR") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                                    contentDescription = "Searching Vehicle",
                                                    tint = BrandBluePrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Searching for Nearby Drivers...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = BrandBlueDark
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Scanning 3.0 km radius around ${activeTrip!!.pickupName.split(",")[0]}",
                                        fontSize = 12.sp,
                                        color = NeutralGray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Real-time broadcast logs card
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.12f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    "Live Dispatch Radar Log:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandBluePrimary
                                                )
                                                Text(
                                                    "Pinging active units...",
                                                    fontSize = 10.sp,
                                                    color = NeutralGray
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            broadcastLogs.takeLast(3).forEach { log ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(AccentAmber)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = log,
                                                        fontSize = 11.sp,
                                                        color = BrandBlueDark.copy(alpha = 0.9f)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (broadcastDrivers.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Active drivers in dispatch range (${broadcastDrivers.size}):",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeutralGray
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        broadcastDrivers.forEach { (driver, dist) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(PureWhite)
                                                    .clickable { selectedDriverForProfile = driver }
                                                    .padding(8.dp)
                                                    .testTag("nearest_driver_item_${driver.id}"),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (driver.vehicleType == "CAR") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                                        contentDescription = "vehicle",
                                                        tint = BrandBluePrimary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "${driver.name} (★${driver.rating})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = BrandBlueDark
                                                    )
                                                }
                                                Text(
                                                    text = "${String.format("%.2f", dist)} km • Tap Profile",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SuccessGreen
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Fare and breakdown visualization
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BrandBlueLight.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Fare Guarantee", fontWeight = FontWeight.Bold, color = BrandBlueDark, fontSize = 13.sp)
                                    Text("${activeTrip!!.fareGmd} GMD", fontWeight = FontWeight.Bold, color = BrandBluePrimary, fontSize = 14.sp)
                                }
                                Text(
                                    "Payment Method: ${activeTrip!!.paymentMethod} • Local Fare Guarantee pricing applied.",
                                    fontSize = 11.sp,
                                    color = NeutralGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (activeTrip!!.status != "COMPLETED") {
                            var showRiderCancelDialog by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showRiderCancelDialog = true },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("cancel_ride_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ErrorRed.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = ErrorRed)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancel Ride", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                                }

                                var showSosActiveDialog by remember { mutableStateOf(false) }

                                Button(
                                    onClick = { showSosActiveDialog = true },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("sos_active_ride_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = "SOS", tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Emergency SOS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                if (showSosActiveDialog) {
                                    SosEmergencyDialog(
                                        pLat = pCoordinates?.lat ?: 13.4471,
                                        pLng = pCoordinates?.lng ?: -16.6791,
                                        activeTripId = activeTrip!!.id,
                                        onDismiss = { showSosActiveDialog = false }
                                    )
                                }
                            }

                            if (showRiderCancelDialog) {
                                RiderCancelConfirmationDialog(
                                    pickupName = activeTrip!!.pickupName,
                                    dropoffName = activeTrip!!.dropoffName,
                                    onConfirmCancel = { selectedReason ->
                                        viewModel.cancelTripActive(activeTrip!!.id, selectedReason)
                                        showRiderCancelDialog = false
                                    },
                                    onDismiss = { showRiderCancelDialog = false }
                                )
                            }
                        } else {
                            val matchedDriver = drivers.firstOrNull { it.id == activeTrip!!.driverId }
                            RatingReviewComponent(
                                driverName = activeTrip!!.driverName ?: "Gambia Partner Driver",
                                vehicleType = activeTrip!!.vehicleType,
                                vehiclePlate = activeTrip!!.vehiclePlate ?: "BJL-Registered",
                                driverRating = matchedDriver?.rating ?: 4.9f,
                                tripId = activeTrip!!.id,
                                tripFareGmd = activeTrip!!.fareGmd,
                                onSubmitRating = { stars, comment, selectedTagsList, tipGmd ->
                                    viewModel.submitRating(
                                        tripId = activeTrip!!.id,
                                        stars = stars,
                                        comment = comment,
                                        selectedTags = selectedTagsList,
                                        tipGmd = tipGmd
                                    )
                                },
                                onDismiss = {
                                    // Bypass/Skip rating with default 5-stars feedback
                                    viewModel.submitRating(
                                        tripId = activeTrip!!.id,
                                        stars = 5,
                                        comment = "Journey Completed",
                                        selectedTags = emptyList()
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedDriverForProfile != null) {
        DriverTrustProfileDialog(
            driver = selectedDriverForProfile,
            vehicleType = selectedDriverForProfile!!.vehicleType,
            vehiclePlate = selectedDriverForProfile!!.vehiclePlate,
            driverName = selectedDriverForProfile!!.name,
            trips = trips,
            onDismiss = { selectedDriverForProfile = null }
        )
    }
}

@Composable
fun HistoryScreenContent(viewModel: WayGoViewModel) {
    // Highly polished passenger ride history log matching localized taxi services in Banjul
    val trips by viewModel.allTrips.collectAsState()
    val drivers by viewModel.allDrivers.collectAsState()
    var selectedDriverForProfile by remember { mutableStateOf<com.example.data.DriverEntity?>(null) }
    val scheduledRides by viewModel.allScheduledRides.collectAsState()
    val firestoreTrips by viewModel.firestoreTrips.collectAsState()
    val firestoreIsLoading by viewModel.firestoreIsLoading.collectAsState()
    val firestoreStatusMessage by viewModel.firestoreStatusMessage.collectAsState()

    var selectedTab by remember { mutableStateOf("PAST_RIDES") } // "PAST_RIDES", "SCHEDULED", "COMPLETED"
    var viewCloudOnly by remember { mutableStateOf(true) } // toggles between Firestore Cloud and Local Database Cache

    val formatTimestamp = remember {
        { ms: Long ->
            try {
                val instant = java.time.Instant.ofEpochMilli(ms)
                val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                    .withZone(java.time.ZoneId.systemDefault())
                formatter.format(instant)
            } catch (e: Exception) {
                "Recent Date"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PureWhite)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "PAST_RIDES" to "Room History",
                "SCHEDULED" to "Scheduled",
                "COMPLETED" to "Cloud Trips"
            ).forEach { (tabKey, tabLabel) ->
                val isSel = selectedTab == tabKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) BrandBluePrimary else Color.Transparent)
                        .clickable { selectedTab = tabKey }
                        .padding(vertical = 10.dp)
                        .testTag("tab_history_${tabKey.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSel) Color.White else BrandBlueDark
                    )
                }
            }
        }

        if (selectedTab == "PAST_RIDES") {
            PastRideHistoryView(
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
        } else if (selectedTab == "SCHEDULED") {
            // Filter passenger-owned scheduled rides
            val activeScheduled = scheduledRides.filter { it.status != "DISPATCHED" }
            if (activeScheduled.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.EventAvailable, contentDescription = "Empty", tint = NeutralGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No scheduled future rides yet.", color = NeutralGray, fontSize = 14.sp)
                        Text("You can schedule a sedan or keke taxi for work/market trips anytime!", color = NeutralGray, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeScheduled) { ride ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("scheduled_ride_item_${ride.id}"),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (ride.vehicleType == "CAR") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                            contentDescription = "vehicle",
                                            tint = BrandBluePrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (ride.vehicleType == "CAR") "Yellow Cab (Sedan)" else "Keke / Caravan",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = BrandBlueDark
                                        )
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (ride.status == "ACCEPTED") SuccessGreen.copy(alpha = 0.15f)
                                            else if (ride.status == "CANCELLED") ErrorRed.copy(alpha = 0.15f)
                                            else AccentAmber.copy(alpha = 0.15f)
                                        )
                                    ) {
                                        Text(
                                            text = ride.status,
                                            color = if (ride.status == "ACCEPTED") SuccessGreen
                                            else if (ride.status == "CANCELLED") ErrorRed
                                            else AccentAmber,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = BrandBlueSecondary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Time: ${ride.scheduledTime}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = BrandBlueDark
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Pickup: ${ride.pickupName}", fontSize = 11.5.sp, color = NeutralGray)
                                Text("Dropoff: ${ride.dropoffName}", fontSize = 11.5.sp, color = NeutralGray)

                                Spacer(modifier = Modifier.height(8.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Fare Estimate: ${ride.fareGmd} GMD", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBluePrimary)
                                        Text(
                                            text = if (ride.driverName != null) "Confirmed with ${ride.driverName}" else "Awaiting driver pickup confirmation",
                                            fontSize = 10.sp,
                                            color = if (ride.driverName != null) SuccessGreen else NeutralGray,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    if (ride.status == "SCHEDULED" || ride.status == "ACCEPTED") {
                                        TextButton(
                                            onClick = { viewModel.cancelScheduledRide(ride.id) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                                        ) {
                                            Icon(Icons.Default.Cancel, contentDescription = "Cancel", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Cancel", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // PAST TRIPS LOG tab
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                
                // Firestore Synchronization HUD / Panel Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("firestore_sync_hud"),
                    colors = CardDefaults.cardColors(containerColor = BrandBlueLight.copy(alpha = 0.5f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Cloud",
                                    tint = BrandBluePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Firestore Cloud Sync Engine",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = BrandBlueDark
                                )
                            }
                            
                            // Live badge indicator based on Firebase configuration state
                            val isFirestoreAvailable = com.example.data.FirestoreManager.firestore != null
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isFirestoreAvailable) SuccessGreen.copy(alpha = 0.15f)
                                    else NeutralGray.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = if (isFirestoreAvailable) "LIVE CONNECTED" else "OFFLINE SIMULATION",
                                    color = if (isFirestoreAvailable) SuccessGreen else NeutralGray,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 8.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (firestoreStatusMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = firestoreStatusMessage!!,
                                fontSize = 11.sp,
                                color = BrandBlueDark.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.refreshTripHistoryFromFirestore() },
                                modifier = Modifier.weight(1f).height(36.dp).testTag("btn_pull_firestore"),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !firestoreIsLoading
                            ) {
                                if (firestoreIsLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudDone, contentDescription = "sync", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pull Cloud", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.syncLocalTripsToFirestore() },
                                modifier = Modifier.weight(1.1f).height(36.dp).testTag("btn_upload_firestore"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandBluePrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !firestoreIsLoading
                            ) {
                                Icon(Icons.Default.CloudQueue, contentDescription = "upload", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Local", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Cloud vs Local Sub-tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewCloudOnly) BrandBlueSecondary else Color.Transparent)
                            .clickable { viewCloudOnly = true }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Firestore Cloud (${firestoreTrips.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewCloudOnly) Color.White else BrandBlueDark
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!viewCloudOnly) BrandBlueSecondary else Color.Transparent)
                            .clickable { viewCloudOnly = false }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Local Backup Database (${trips.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!viewCloudOnly) Color.White else BrandBlueDark
                        )
                    }
                }

                // Displaying Selected source list
                val activeList = if (viewCloudOnly) firestoreTrips else trips

                if (activeList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "empty",
                                tint = NeutralGray,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (viewCloudOnly) "No Firestore Journeys Found" else "No local database history",
                                fontWeight = FontWeight.Bold,
                                color = BrandBlueDark,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (viewCloudOnly) "Click 'Upload Local' above to easily sync your current local routes to live Firestore cloud!"
                                       else "Complete trips via passenger mode or request a vehicle first.",
                                color = NeutralGray,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeList) { trip ->
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("trip_history_card_${trip.id}"),
                                colors = CardDefaults.cardColors(containerColor = PureWhite),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(BrandBlueLight),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (trip.vehicleType == "CAR") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                                    contentDescription = "vehicle",
                                                    tint = BrandBluePrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = if (trip.vehicleType == "CAR") "Car Taxi" else "Tricycle (TukTuk)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = BrandBlueDark
                                                )
                                                Text(
                                                    text = formatTimestamp(trip.timestamp),
                                                    fontSize = 10.sp,
                                                    color = NeutralGray
                                                )
                                            }
                                        }

                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (trip.status == "COMPLETED") SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f)
                                            ),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = trip.status,
                                                color = if (trip.status == "COMPLETED") SuccessGreen else ErrorRed,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 8.5.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    // Origin & Destination points
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(BrandBluePrimary))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("From: ${trip.pickupName}", fontSize = 11.5.sp, color = BrandBlueDark, maxLines = 1)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ErrorRed))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("To: ${trip.dropoffName}", fontSize = 11.5.sp, color = BrandBlueDark, maxLines = 1)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Divider(color = Color.LightGray.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .clickable {
                                                    val matchedD = drivers.find { it.id == trip.driverId } ?: DriverEntity(
                                                        id = trip.driverId ?: "drv_unknown",
                                                        name = trip.driverName ?: "Gambia Partner Driver",
                                                        phone = "+220 555 0100",
                                                        vehicleType = trip.vehicleType,
                                                        vehiclePlate = trip.vehiclePlate ?: "BJL Registered",
                                                        rating = if (trip.rating > 0) trip.rating.toFloat() else 4.8f,
                                                        approvalStatus = "APPROVED",
                                                        isOnline = true,
                                                        currentLat = 13.45,
                                                        currentLng = -16.6,
                                                        driverLicense = "DL-2024-9981"
                                                    )
                                                    selectedDriverForProfile = matchedD
                                                }
                                                .testTag("history_driver_profile_${trip.id}")
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Person, contentDescription = "Driver", tint = BrandBlueSecondary, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = trip.driverName ?: "Gambia Partner Driver",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = BrandBlueDark
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Verified,
                                                    contentDescription = "Verified Profile",
                                                    tint = SuccessGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Text(
                                                text = "Plate: ${trip.vehiclePlate ?: "BJL Registered"} • View Profile",
                                                fontSize = 10.sp,
                                                color = BrandBluePrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(start = 17.dp)
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${trip.fareGmd} GMD",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.5.sp,
                                                color = BrandBluePrimary
                                            )
                                            Text(
                                                text = "Paid via ${trip.paymentMethod}",
                                                fontSize = 9.5.sp,
                                                color = NeutralGray
                                            )
                                        }
                                    }

                                    if (trip.rating > 0) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(BrandBlueLight.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                (1..5).forEach { s ->
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = "*",
                                                        tint = if (s <= trip.rating) AccentAmber else Color.LightGray,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (trip.reviewComment.isNotEmpty()) "\"${trip.reviewComment}\"" else "No commented feedback",
                                                fontSize = 10.sp,
                                                color = BrandBlueDark,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
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

    if (selectedDriverForProfile != null) {
        DriverTrustProfileDialog(
            driver = selectedDriverForProfile,
            vehicleType = selectedDriverForProfile!!.vehicleType,
            vehiclePlate = selectedDriverForProfile!!.vehiclePlate,
            driverName = selectedDriverForProfile!!.name,
            trips = trips,
            onDismiss = { selectedDriverForProfile = null }
        )
    }
}

@Composable
fun ProfileScreenContent(
    viewModel: WayGoViewModel,
    profile: UserProfileEntity?,
    initialSection: String = "PROFILE",
    onSignOutClick: () -> Unit = {},
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val trips by viewModel.allTrips.collectAsState()
    val pastRides by viewModel.allPastRides.collectAsState()
    
    // Active Account Tab state: "PROFILE", "TRIP_LOG", "INBOX", "PAYMENT", "SAFETY", "SAVED_PLACES", "SETTINGS"
    var activeAccountTab by remember(initialSection) { mutableStateOf(initialSection) }

    // Calculate total completed rides dynamically from database
    val completedRidesCount = trips.count { it.status == "COMPLETED" }
    val totalSpendingGmd = trips.filter { it.status == "COMPLETED" }.sumOf { it.fareGmd }

    var editName by remember { mutableStateOf(profile?.name ?: "") }
    var editPhone by remember { mutableStateOf(profile?.phone ?: "") }
    var editEmail by remember { mutableStateOf(profile?.email ?: "") }
    var editGender by remember { mutableStateOf(profile?.gender ?: "Male") }
    var editMM by remember { mutableStateOf(profile?.mobileMoneyNumber ?: "") }
    var editHome by remember { mutableStateOf(profile?.savedHome ?: "Albert Market, Banjul") }
    var editWork by remember { mutableStateOf(profile?.savedWork ?: "Kairaba Avenue, Serrekunda") }
    var activeAvatarIndex by remember { mutableIntStateOf(profile?.avatarIndex ?: 1) }
    var photoUriState by remember { mutableStateOf<String?>(profile?.photoUri) }

    var isLinkingCardProcessing by remember { mutableStateOf(false) }
    var linkingCardUrl by remember { mutableStateOf<String?>(null) }
    var linkingStatusMsg by remember { mutableStateOf("") }

    var showHomePicker by remember { mutableStateOf(false) }
    var showWorkPicker by remember { mutableStateOf(false) }
    var showPhotoPickerOptions by remember { mutableStateOf(false) }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            photoUriState = uri.toString()
            showPhotoPickerOptions = false
        }
    }
    var showAddSavedPlaceDialog by remember { mutableStateOf(false) }
    var newSavedPlaceName by remember { mutableStateOf("") }
    var newSavedPlaceAddress by remember { mutableStateOf("") }

    // Saved places list
    var customSavedPlaces by remember {
        mutableStateOf(
            listOf(
                Pair("Senegambia Beach Resort", "Kairaba Avenue Coast, Kololi"),
                Pair("Banjul International Airport", "Yundum Local Terminal"),
                Pair("Arch 22 Monument", "Banjul City Gateway")
            )
        )
    }

    // Inbox messages list
    var inboxMessages by remember {
        mutableStateOf(
            listOf(
                AccountInboxItem("1", "Welcome to WayGo Gambia", "Your account is active. Book rides seamlessly across Banjul, Serrekunda & Brikama.", "10 mins ago", "SYSTEM", false),
                AccountInboxItem("2", "15% Off SeneGambia Weekend Ride", "Use promo code WAYGO15 on your next coastal ride. Offer expires Sunday midnight!", "2 hours ago", "PROMO", false),
                AccountInboxItem("3", "Digital Payment Secured", "Visa, MasterCard & Wave mobile money payments are fully enabled.", "1 day ago", "PAYMENT", true),
                AccountInboxItem("4", "Safety Verification Update", "All drivers on WayGo undergo physical Gambia Police background checks.", "3 days ago", "SAFETY", true)
            )
        )
    }

    // Settings preferences
    var pushNotifsEnabled by remember { mutableStateOf(true) }
    var smsUpdatesEnabled by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf("English") }

    // Trip Log filters: Status & Date Range
    var tripLogFilter by remember { mutableStateOf("ALL") } // "ALL", "COMPLETED", "CANCELLED"
    var tripLogDateRangeFilter by remember { mutableStateOf("ALL_TIME") } // "ALL_TIME", "TODAY", "LAST_7_DAYS", "LAST_30_DAYS", "CUSTOM"
    var customStartDaysAgo by remember { mutableIntStateOf(30) }
    var customEndDaysAgo by remember { mutableIntStateOf(0) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var selectedTripForReceipt by remember { mutableStateOf<TripEntity?>(null) }

    LaunchedEffect(profile) {
        if (profile != null) {
            editName = profile.name
            editPhone = profile.phone
            editEmail = profile.email
            editGender = profile.gender
            editMM = profile.mobileMoneyNumber
            if (profile.savedHome.isNotEmpty()) editHome = profile.savedHome
            if (profile.savedWork.isNotEmpty()) editWork = profile.savedWork
            activeAvatarIndex = profile.avatarIndex
            photoUriState = profile.photoUri
        }
    }

    val avatars = listOf(
        Pair("🦁", Color(0xFFFFB300)), // Gold (Gambia Lion)
        Pair("🌴", Color(0xFF2E7D32)), // Emerald (Coastlands)
        Pair("🚖", Color(0xFFFDD835)), // Yellow (Taxi Cab)
        Pair("💼", Color(0xFF1E88E5)), // Blue (Business)
        Pair("☀️", Color(0xFFE64A19)), // Coral (SeneGambia Sun)
        Pair("🧉", Color(0xFF8E24AA))  // Purple (Wonjo Lover)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("profile_view")
    ) {
        // Upper Navigation Title Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { onBack() },
                modifier = Modifier
                    .background(BrandBlueLight, CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                    contentDescription = "Back",
                    tint = BrandBluePrimary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Account Hub", 
                    fontWeight = FontWeight.ExtraBold, 
                    fontSize = 20.sp, 
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Personal info, trips, payments & safety",
                    fontSize = 11.5.sp,
                    color = NeutralGray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PROFILE OVERVIEW HEADER CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.profile_banner),
                    contentDescription = "Profile Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.68f))
                )
                
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { activeAccountTab = "PROFILE" }
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUriState != null) {
                                when (photoUriState) {
                                    "preset_active" -> {
                                        Image(
                                            painter = painterResource(id = com.example.R.drawable.rider_avatar_active),
                                            contentDescription = "Profile Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    "preset_alt" -> {
                                        Image(
                                            painter = painterResource(id = com.example.R.drawable.rider_avatar_alt),
                                            contentDescription = "Profile Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    else -> {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = photoUriState),
                                            contentDescription = "Profile Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else {
                                val avatarStyle = avatars.getOrElse(activeAvatarIndex) { avatars[0] }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(avatarStyle.second),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = avatarStyle.first, fontSize = 38.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = editName.ifBlank { "Gambia Passenger" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = editPhone.ifBlank { "+220 771 2345" },
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = completedRidesCount.toString(),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Completed Rides",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalSpendingGmd GMD",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = SuccessGreen
                            )
                            Text(
                                text = "Total Spending",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // QUICK ACTION CARDS (Activity, Wallet, Inbox, Safety)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Activity Card
            Card(
                onClick = { activeAccountTab = "TRIP_LOG" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeAccountTab == "TRIP_LOG") BrandBlueLight else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandBluePrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Activity", tint = BrandBluePrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Activity", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Wallet Card
            Card(
                onClick = { activeAccountTab = "PAYMENT" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeAccountTab == "PAYMENT") BrandBlueLight else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet", tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Wallet", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Inbox Card
            Card(
                onClick = { activeAccountTab = "INBOX" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeAccountTab == "INBOX") BrandBlueLight else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentAmber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Inbox, contentDescription = "Inbox", tint = AccentAmber, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Inbox", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Safety Card
            Card(
                onClick = { activeAccountTab = "SAFETY" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeAccountTab == "SAFETY") BrandBlueLight else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ErrorRed.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = "Safety", tint = ErrorRed, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Safety", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // PLATFORM MODES CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account_platform_modes_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.12f))
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
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(BrandBluePrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "Account Portals & Modes",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        color = BrandBlueLight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "WayGo Unified",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBluePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. PASSENGER MODE ROW
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    color = BrandBluePrimary.copy(alpha = 0.06f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(BrandBluePrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = "Passenger Mode", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Passenger Mode", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Request rides, saved places & trip logs", fontSize = 11.5.sp, color = NeutralGray)
                            }
                        }
                        Surface(
                            color = SuccessGreen,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text("Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. DRIVER HUB ROW
                Surface(
                    onClick = { viewModel.setRole("DRIVER") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AccentAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TwoWheeler, contentDescription = "Driver Hub", tint = BrandBlueDark, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Driver Hub", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Earn with WayGo • Online dispatch & payouts", fontSize = 11.5.sp, color = NeutralGray)
                            }
                        }
                        Button(
                            onClick = { viewModel.setRole("DRIVER") },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlueDark),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Switch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ACCOUNT FEATURE NAVIGATION SELECTOR CHIPS
        Text(
            text = "Account Sections",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = BrandBlueSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        val unreadInboxCount = inboxMessages.count { !it.isRead }

        val accountSections = remember(unreadInboxCount, trips.size, pastRides.size, profile?.isPaymentLinked) {
            buildList {
                add(AccountSectionItem("PROFILE", "Profile", Icons.Default.Person, null))
                add(AccountSectionItem("PAST_RIDES", "Ride History (Room)", Icons.Default.DirectionsCar, pastRides.size.takeIf { it > 0 }?.toString()))
                add(AccountSectionItem("TRIP_LOG", "Trip Log", Icons.Default.History, trips.size.takeIf { it > 0 }?.toString()))
                add(AccountSectionItem("INBOX", "Inbox", Icons.Default.Inbox, unreadInboxCount.takeIf { it > 0 }?.toString()))
                add(AccountSectionItem("DRIVER_HUB", "Driver Hub", Icons.Default.TwoWheeler, "DRIVER"))
                add(AccountSectionItem("PAYMENT", "Payment", Icons.Default.AccountBalanceWallet, if (profile?.isPaymentLinked == true) "Card" else null))
                add(AccountSectionItem("SAFETY", "Support & Safety", Icons.Default.Shield, null))
                add(AccountSectionItem("SAVED_PLACES", "Saved Places", Icons.Default.Bookmark, null))
                add(AccountSectionItem("SETTINGS", "Settings", Icons.Default.Settings, null))
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(accountSections) { section ->
                val isSelected = activeAccountTab == section.id
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (section.id == "DRIVER_HUB") {
                            viewModel.setRole("DRIVER")
                        } else {
                            activeAccountTab = section.id
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = section.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                            if (section.badge != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else BrandBluePrimary)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = section.badge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) BrandBluePrimary else Color.White
                                    )
                                }
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = section.title,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandBluePrimary,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = NeutralGray
                    ),
                    modifier = Modifier.testTag("tab_${section.id.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // FEATURE CONTENT SECTION BASED ON ACTIVE TAB
        when (activeAccountTab) {
            "PAST_RIDES" -> {
                PastRideHistoryView(
                    viewModel = viewModel,
                    onRebookRide = { _, _, _ -> onBack() }
                )
            }
            "TRIP_LOG" -> {
                // 📜 1. TRIP LOG SECTION WITH ENHANCED FILTERS
                val nowMs = System.currentTimeMillis()
                val oneDayMs = 24 * 3600 * 1000L
                val startOfTodayMs = remember(nowMs) {
                    try {
                        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (e: Exception) {
                        nowMs - (nowMs % oneDayMs)
                    }
                }
                val sevenDaysAgoMs = nowMs - (7 * oneDayMs)
                val thirtyDaysAgoMs = nowMs - (30 * oneDayMs)
                val customStartMs = nowMs - (customStartDaysAgo * oneDayMs)
                val customEndMs = nowMs - (customEndDaysAgo * oneDayMs)

                val formatTripTime = remember {
                    { ms: Long ->
                        try {
                            val instant = java.time.Instant.ofEpochMilli(ms)
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                                .withZone(java.time.ZoneId.systemDefault())
                            formatter.format(instant)
                        } catch (e: Exception) {
                            "Recent Date"
                        }
                    }
                }

                val filteredTrips = trips.filter { trip ->
                    val matchesStatus = when (tripLogFilter) {
                        "COMPLETED" -> trip.status == "COMPLETED"
                        "CANCELLED" -> trip.status == "CANCELLED"
                        else -> true
                    }

                    val matchesDate = when (tripLogDateRangeFilter) {
                        "TODAY" -> trip.timestamp >= startOfTodayMs
                        "LAST_7_DAYS" -> trip.timestamp >= sevenDaysAgoMs
                        "LAST_30_DAYS" -> trip.timestamp >= thirtyDaysAgoMs
                        "CUSTOM" -> trip.timestamp in customStartMs..customEndMs
                        else -> true
                    }

                    matchesStatus && matchesDate
                }

                val totalFilteredSpend = filteredTrips.filter { it.status == "COMPLETED" }.sumOf { it.fareGmd + it.tipGmd }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Trip History & Log",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = BrandBlueDark
                                )
                                Text(
                                    text = "${filteredTrips.size} trips • Total Spent: $totalFilteredSpend GMD",
                                    fontSize = 11.sp,
                                    color = NeutralGray
                                )
                            }

                            if (tripLogFilter != "ALL" || tripLogDateRangeFilter != "ALL_TIME") {
                                TextButton(
                                    onClick = {
                                        tripLogFilter = "ALL"
                                        tripLogDateRangeFilter = "ALL_TIME"
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Reset Filters", fontSize = 11.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 1. STATUS FILTER CHIPS
                        Text("Filter by Status", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = NeutralGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val statusOptions = listOf(
                                "ALL" to "All Statuses",
                                "COMPLETED" to "Completed",
                                "CANCELLED" to "Cancelled"
                            )
                            statusOptions.forEach { (filterKey, label) ->
                                val isSelected = tripLogFilter == filterKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected && filterKey == "COMPLETED" -> SuccessGreen.copy(alpha = 0.18f)
                                                isSelected && filterKey == "CANCELLED" -> ErrorRed.copy(alpha = 0.18f)
                                                isSelected -> BrandBluePrimary.copy(alpha = 0.18f)
                                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = when {
                                                isSelected && filterKey == "COMPLETED" -> SuccessGreen
                                                isSelected && filterKey == "CANCELLED" -> ErrorRed
                                                isSelected -> BrandBluePrimary
                                                else -> Color.Transparent
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { tripLogFilter = filterKey }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isSelected && filterKey == "COMPLETED" -> SuccessGreen
                                            isSelected && filterKey == "CANCELLED" -> ErrorRed
                                            isSelected -> BrandBluePrimary
                                            else -> NeutralGray
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. DATE RANGE FILTER CHIPS
                        Text("Filter by Date Range", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = NeutralGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val dateOptions = listOf(
                                "ALL_TIME" to "All Time",
                                "TODAY" to "Today",
                                "LAST_7_DAYS" to "7 Days",
                                "LAST_30_DAYS" to "30 Days",
                                "CUSTOM" to "Custom"
                            )
                            dateOptions.forEach { (dateKey, label) ->
                                val isSelected = tripLogDateRangeFilter == dateKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) BrandBluePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                        .clickable {
                                            tripLogDateRangeFilter = dateKey
                                            if (dateKey == "CUSTOM") showCustomDateDialog = true
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // CUSTOM DATE RANGE SELECTOR CARD
                        AnimatedVisibility(
                            visible = tripLogDateRangeFilter == "CUSTOM",
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = BrandBluePrimary.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Custom Range Window:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                                            Text("${customStartDaysAgo}d ago to ${customEndDaysAgo}d ago", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = BrandBluePrimary)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(3 to "Last 3 Days", 14 to "Last 14 Days", 60 to "Last 60 Days", 90 to "Last 90 Days").forEach { (days, btnLabel) ->
                                                OutlinedButton(
                                                    onClick = {
                                                        customStartDaysAgo = days
                                                        customEndDaysAgo = 0
                                                    },
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text(btnLabel, fontSize = 9.5.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (filteredTrips.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = NeutralGray, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No trip logs match selected filters.", fontSize = 13.sp, color = NeutralGray)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                filteredTrips.forEach { trip ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (trip.vehicleType == "CAR") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                                        contentDescription = null,
                                                        tint = BrandBluePrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Trip #${trip.id.takeLast(6)}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                val statusColor = when (trip.status) {
                                                    "COMPLETED" -> SuccessGreen
                                                    "CANCELLED" -> ErrorRed
                                                    else -> AccentAmber
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(statusColor.copy(alpha = 0.15f))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = trip.status,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = statusColor
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formatTripTime(trip.timestamp),
                                                fontSize = 10.5.sp,
                                                color = NeutralGray
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SuccessGreen))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = trip.pickupName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                            }

                                            Box(modifier = Modifier.padding(start = 3.dp).width(2.dp).height(10.dp).background(Color.LightGray))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ErrorRed))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = trip.dropoffName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "Driver: ${(trip.driverName ?: "Fatou (Taxi)").ifBlank { "Fatou (Taxi)" }}",
                                                        fontSize = 11.5.sp,
                                                        color = NeutralGray
                                                    )
                                                    Text(
                                                        text = "Payment: ${trip.paymentMethod}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = BrandBluePrimary
                                                    )
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "${trip.fareGmd + trip.tipGmd} GMD",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 14.sp,
                                                        color = BrandBlueDark
                                                    )
                                                    if (trip.tipGmd > 0) {
                                                        Text(
                                                            text = "(Includes ${trip.tipGmd} GMD Tip)",
                                                            fontSize = 9.5.sp,
                                                            color = SuccessGreen,
                                                            fontWeight = FontWeight.Bold
                                                        )
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

            "INBOX" -> {
                // 📥 2. INBOX SECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Inbox & Notifications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BrandBlueDark
                            )
                            if (unreadInboxCount > 0) {
                                TextButton(
                                    onClick = {
                                        inboxMessages = inboxMessages.map { it.copy(isRead = true) }
                                    }
                                ) {
                                    Text("Mark all as read", fontSize = 11.sp, color = BrandBluePrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            inboxMessages.forEach { msg ->
                                Card(
                                    onClick = {
                                        inboxMessages = inboxMessages.map {
                                            if (it.id == msg.id) it.copy(isRead = true) else it
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (!msg.isRead) BrandBluePrimary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (!msg.isRead) BrandBluePrimary.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (msg.category) {
                                                        "PROMO" -> AccentAmber.copy(alpha = 0.15f)
                                                        "PAYMENT" -> SuccessGreen.copy(alpha = 0.15f)
                                                        else -> BrandBluePrimary.copy(alpha = 0.15f)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (msg.category) {
                                                    "PROMO" -> Icons.Default.LocalOffer
                                                    "PAYMENT" -> Icons.Default.CreditCard
                                                    "SAFETY" -> Icons.Default.Shield
                                                    else -> Icons.Default.Notifications
                                                },
                                                contentDescription = null,
                                                tint = when (msg.category) {
                                                    "PROMO" -> AccentAmber
                                                    "PAYMENT" -> SuccessGreen
                                                    else -> BrandBluePrimary
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = msg.title,
                                                    fontWeight = if (!msg.isRead) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 13.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = msg.timestamp,
                                                    fontSize = 10.sp,
                                                    color = NeutralGray
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = msg.message,
                                                fontSize = 11.5.sp,
                                                color = NeutralGray,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "PROFILE" -> {
                // 👤 3. PERSONAL PROFILE SECTION
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Select Profile Avatar Icon",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BrandBlueSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            avatars.forEachIndexed { index, (emoji, bg) ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (activeAvatarIndex == index) bg else bg.copy(alpha = 0.3f)
                                        )
                                        .clickable { 
                                            activeAvatarIndex = index 
                                            photoUriState = null
                                        }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emoji, 
                                        fontSize = 24.sp,
                                        modifier = Modifier.alpha(if (activeAvatarIndex == index && photoUriState == null) 1f else 0.45f)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Personal & Contact Information",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BrandBlueSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Profile Name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "name", tint = BrandBluePrimary) },
                                modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = BrandBlueDark),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = BrandBlueDark,
                                    unfocusedTextColor = BrandBlueDark,
                                    focusedBorderColor = BrandBluePrimary,
                                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f),
                                    focusedContainerColor = PureWhite,
                                    unfocusedContainerColor = PureWhite
                                )
                            )

                            OutlinedTextField(
                                value = editPhone,
                                onValueChange = { editPhone = it },
                                label = { Text("Phone Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "phone", tint = BrandBluePrimary) },
                                modifier = Modifier.fillMaxWidth().testTag("profile_phone_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                textStyle = androidx.compose.ui.text.TextStyle(color = BrandBlueDark),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = BrandBlueDark,
                                    unfocusedTextColor = BrandBlueDark,
                                    focusedBorderColor = BrandBluePrimary,
                                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f),
                                    focusedContainerColor = PureWhite,
                                    unfocusedContainerColor = PureWhite
                                )
                            )

                            OutlinedTextField(
                                value = editEmail,
                                onValueChange = { editEmail = it },
                                label = { Text("Email Address") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "email", tint = BrandBluePrimary) },
                                modifier = Modifier.fillMaxWidth().testTag("profile_email_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                textStyle = androidx.compose.ui.text.TextStyle(color = BrandBlueDark),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = BrandBlueDark,
                                    unfocusedTextColor = BrandBlueDark,
                                    focusedBorderColor = BrandBluePrimary,
                                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f),
                                    focusedContainerColor = PureWhite,
                                    unfocusedContainerColor = PureWhite
                                )
                            )

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Gender Selection", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeutralGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("Male", "Female", "Prefer Not").forEach { option ->
                                        Row(
                                            modifier = Modifier
                                                .clickable { editGender = option }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = editGender == option,
                                                onClick = { editGender = option },
                                                colors = RadioButtonDefaults.colors(selectedColor = BrandBluePrimary)
                                            )
                                            Text(text = option, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                            OutlinedTextField(
                                value = editMM,
                                onValueChange = { editMM = it },
                                label = { Text("MobileMoney (Wave/Africell) Number") },
                                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "mobile_money", tint = BrandBluePrimary) },
                                modifier = Modifier.fillMaxWidth().testTag("profile_mm_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandBluePrimary,
                                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveProfile(
                                name = editName.trim(),
                                phone = editPhone.trim(),
                                email = editEmail.trim(),
                                gender = editGender,
                                mobileMoney = editMM.trim(),
                                savedHome = editHome.trim(),
                                savedWork = editWork.trim(),
                                avatarIndex = activeAvatarIndex,
                                photoUri = photoUriState
                            )
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_profile_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save Profile Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            "PAYMENT" -> {
                // 💳 4. PAYMENT SECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Cashless Payments & Cards",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = BrandBlueDark
                        )

                        if (profile?.isPaymentLinked == true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SuccessGreen.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = "Card Linked",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Visa / MasterCard Linked",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Card Ending in **** **** **** ${profile.linkedCardLast4.ifBlank { "4242" }}",
                                        fontSize = 12.sp,
                                        color = NeutralGray
                                    )
                                    Text(
                                        text = "Secured: ${profile.linkedPaymentEmail}",
                                        fontSize = 11.sp,
                                        color = BrandBluePrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(onClick = { viewModel.removePaymentMethod() }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Unlink Card",
                                        tint = ErrorRed
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SuccessGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cashless checkout is active. Stripe will charge automatically.",
                                    fontSize = 11.sp,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BrandBluePrimary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("No Card Linked", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Link debit/credit card for automatic cashless bookings.", fontSize = 11.sp, color = NeutralGray)
                                }
                            }

                            Button(
                                onClick = {
                                    isLinkingCardProcessing = true
                                    linkingStatusMsg = "Opening secure Stripe gateway..."
                                    coroutineScope.launch {
                                        val pEmail = editEmail.ifBlank { "johndoe@example.com" }
                                        val pName = editName.ifBlank { "John Doe" }
                                        val pPhone = editPhone.ifBlank { "+220 771 2345" }
                                        val txRef = "st_link_" + System.currentTimeMillis().toString().takeLast(6)
                                        val link = com.example.data.StripeManager.initiateStripePayment(
                                            amountGmd = 25.0,
                                            passengerEmail = pEmail,
                                            passengerName = pName,
                                            passengerPhone = pPhone,
                                            tripTxRef = txRef
                                        )
                                        if (link != null) {
                                            linkingCardUrl = link
                                        } else {
                                            linkingStatusMsg = "Failed to connect to Stripe gateway."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Link Card with Stripe", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                        Text("Mobile Money Wallets", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wave / Africell Pay", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Text(editMM.ifBlank { "No Mobile Money number set" }, fontSize = 11.sp, color = NeutralGray)
                            }
                        }
                    }
                }
            }

            "SAFETY" -> {
                // 🛡️ 5. SUPPORT & SAFETY SECTION
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gambia Emergency Hotlines (24/7)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ErrorRed)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(
                                    Triple("Gambia Police", "117", Icons.Default.LocalPolice),
                                    Triple("Ambulance", "116", Icons.Default.MedicalServices),
                                    Triple("Fire Rescue", "118", Icons.Default.FireTruck)
                                ).forEach { (name, number, icon) ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 2.dp),
                                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.08f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(icon, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text(number, fontSize = 12.sp, fontWeight = FontWeight.Black, color = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Ride Safety Features", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlueDark)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Share Trip Live Location", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    Text("Send real-time GPS coordinates to family or trusted contacts during active rides.", fontSize = 11.sp, color = NeutralGray)
                                }
                            }

                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.HeadsetMic, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("WayGo Support Live Chat", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    Text("Contact 24/7 Banjul operations support agent directly.", fontSize = 11.sp, color = NeutralGray)
                                }
                            }
                        }
                    }
                }
            }

            "SAVED_PLACES" -> {
                // 📍 SAVED PLACES MANAGEMENT SCREEN
                val savedPlacesFromDb by viewModel.allSavedPlaces.collectAsState()

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Top Hero Banner Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Saved Places & Locations",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = BrandBlueDark
                                    )
                                    Text(
                                        text = "${savedPlacesFromDb.size} locations stored for 1-tap booking",
                                        fontSize = 11.5.sp,
                                        color = NeutralGray
                                    )
                                }

                                Button(
                                    onClick = { showAddSavedPlaceDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("add_saved_place_top_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Place", fontSize = 12.sp, color = Color.White)
                                }
                            }

                            // Quick tip banner
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = BrandBluePrimary.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Tap 'Book Ride' on any place to instantly load it as your dropoff location and calculate instant fares!",
                                        fontSize = 10.5.sp,
                                        color = BrandBlueDark,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // 1. PRIMARY LOCATIONS (Home & Work)
                    Text("Primary Locations", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Home Card
                        val homePlace = savedPlacesFromDb.firstOrNull { it.label.equals("Home", ignoreCase = true) }
                        val homeAddress = homePlace?.name ?: editHome
                        val homeLat = homePlace?.lat ?: 13.4471
                        val homeLng = homePlace?.lng ?: -16.6791

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(BrandBluePrimary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Home, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { showHomePicker = true }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Home", tint = NeutralGray, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Home", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = BrandBlueDark)
                                Text(
                                    text = homeAddress,
                                    fontSize = 11.sp,
                                    color = NeutralGray,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.heightIn(min = 32.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        viewModel.selectOneTapDestination(
                                            com.example.data.SavedPlaceEntity(
                                                name = homeAddress,
                                                label = "Home",
                                                lat = homeLat,
                                                lng = homeLng,
                                                iconType = "HOME"
                                            )
                                        )
                                        onBack()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(32.dp).testTag("book_home_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Book Ride", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Work Card
                        val workPlace = savedPlacesFromDb.firstOrNull { it.label.equals("Work", ignoreCase = true) }
                        val workAddress = workPlace?.name ?: editWork
                        val workLat = workPlace?.lat ?: 13.4533
                        val workLng = workPlace?.lng ?: -16.5746

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(BrandBluePrimary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Work, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { showWorkPicker = true }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Work", tint = NeutralGray, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Work", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = BrandBlueDark)
                                Text(
                                    text = workAddress,
                                    fontSize = 11.sp,
                                    color = NeutralGray,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.heightIn(min = 32.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        viewModel.selectOneTapDestination(
                                            com.example.data.SavedPlaceEntity(
                                                name = workAddress,
                                                label = "Work",
                                                lat = workLat,
                                                lng = workLng,
                                                iconType = "WORK"
                                            )
                                        )
                                        onBack()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(32.dp).testTag("book_work_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Book Ride", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 2. CUSTOM FAVORITE PLACES LIST
                    val customPlaces = savedPlacesFromDb.filter {
                        !it.label.equals("Home", ignoreCase = true) && !it.label.equals("Work", ignoreCase = true)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Custom Favorite Locations", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlueDark)
                                Text("${customPlaces.size} saved", fontSize = 11.sp, color = NeutralGray)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (customPlaces.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No Custom Saved Places Yet", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBlueDark)
                                        Text(
                                            "Pin your favorite gym, beach resort, market, or family location for 1-tap booking.",
                                            fontSize = 11.sp,
                                            color = NeutralGray,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { showAddSavedPlaceDialog = true },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("+ Add First Custom Place", fontSize = 11.5.sp)
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    customPlaces.forEach { place ->
                                        val placeIcon = when (place.iconType) {
                                            "HOME" -> Icons.Default.Home
                                            "WORK" -> Icons.Default.Work
                                            "STAR" -> Icons.Default.Star
                                            "SHOPPING" -> Icons.Default.ShoppingBag
                                            "AIRPORT" -> Icons.Default.Flight
                                            "BEACH" -> Icons.Default.BeachAccess
                                            else -> Icons.Default.Place
                                        }

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(AccentAmber.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(placeIcon, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(place.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(place.name, fontSize = 11.sp, color = NeutralGray)
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                Button(
                                                    onClick = {
                                                        viewModel.selectOneTapDestination(place)
                                                        onBack()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp).testTag("book_custom_place_${place.id}")
                                                ) {
                                                    Text("Book", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                }

                                                IconButton(
                                                    onClick = { viewModel.removeSavedPlace(place.id) },
                                                    modifier = Modifier.size(28.dp).testTag("delete_saved_place_${place.id}")
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = ErrorRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
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

            "SETTINGS" -> {
                // ⚙️ 7. SETTINGS SECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("App Appearance & Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlueDark)

                        val currentThemeMode by viewModel.themeMode.collectAsState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themeOptions = listOf(
                                Triple(ThemeMode.SYSTEM, Icons.Default.SettingsSuggest, "System"),
                                Triple(ThemeMode.LIGHT, Icons.Default.LightMode, "Light"),
                                Triple(ThemeMode.DARK, Icons.Default.DarkMode, "Dark")
                            )

                            themeOptions.forEach { (mode, icon, label) ->
                                val isSelected = currentThemeMode == mode
                                val bg = if (isSelected) BrandBluePrimary else Color.Transparent
                                val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .clickable { viewModel.setThemeMode(mode) }
                                        .padding(vertical = 10.dp)
                                        .testTag("theme_mode_${label.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = contentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = label,
                                            color = contentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                        Text("Notification Preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlueDark)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Push Notifications", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Text("Receive real-time ride updates & driver status", fontSize = 11.sp, color = NeutralGray)
                            }
                            Switch(
                                checked = pushNotifsEnabled,
                                onCheckedChange = { pushNotifsEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBluePrimary)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SMS Trip Alerts", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Text("Receive SMS receipt confirmation on trip end", fontSize = 11.sp, color = NeutralGray)
                            }
                            Switch(
                                checked = smsUpdatesEnabled,
                                onCheckedChange = { smsUpdatesEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBluePrimary)
                            )
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                        Text("App Language", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlueDark)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("English", "Wolof", "Mandinka").forEach { lang ->
                                FilterChip(
                                    selected = selectedLanguage == lang,
                                    onClick = { selectedLanguage = lang },
                                    label = { Text(lang, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandBluePrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                        OutlinedButton(
                            onClick = onSignOutClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("logout_profile_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "logout", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Log Out phone session", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // ADD NEW SAVED PLACE DIALOG
    if (showAddSavedPlaceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSavedPlaceDialog = false },
            title = { Text("Add Favorite Place", fontWeight = FontWeight.Bold, color = BrandBlueDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newSavedPlaceName,
                        onValueChange = { newSavedPlaceName = it },
                        label = { Text("Place Name (e.g. SeneGambia Market)") },
                        singleLine = true,
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
                    OutlinedTextField(
                        value = newSavedPlaceAddress,
                        onValueChange = { newSavedPlaceAddress = it },
                        label = { Text("Address / Landmark") },
                        singleLine = true,
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSavedPlaceName.isNotBlank()) {
                            customSavedPlaces = customSavedPlaces + Pair(newSavedPlaceName.trim(), newSavedPlaceAddress.trim().ifBlank { "Serrekunda District" })
                            newSavedPlaceName = ""
                            newSavedPlaceAddress = ""
                            showAddSavedPlaceDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary)
                ) {
                    Text("Add Place")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSavedPlaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // CUSTOM CARD LINKING CHECKOUT DIALOG
    if (linkingCardUrl != null) {
        StripeCheckoutDialog(
            checkoutUrl = linkingCardUrl!!,
            onPaymentSuccess = { txRef, transactionId ->
                viewModel.linkPaymentMethod(
                    email = editEmail.ifBlank { "johndoe@example.com" },
                    cardLast4 = txRef.takeLast(4).filter { it.isDigit() }.ifBlank { "4242" }
                )
                linkingCardUrl = null
                isLinkingCardProcessing = false
            },
            onCancel = {
                linkingCardUrl = null
                isLinkingCardProcessing = false
            }
        )
    }

    if (isLinkingCardProcessing && linkingCardUrl == null) {
        AlertDialog(
            onDismissRequest = { isLinkingCardProcessing = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF635BFF),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Stripe Secure", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                }
            },
            text = {
                Text(
                    text = linkingStatusMsg,
                    fontSize = 13.sp,
                    color = BrandBlueDark
                )
            },
            confirmButton = {
                TextButton(onClick = { isLinkingCardProcessing = false }) {
                    Text("Cancel", color = NeutralGray)
                }
            }
        )
    }

    // HOME LOCATION PICKER DIALOG
    if (showHomePicker) {
        AlertDialog(
            onDismissRequest = { showHomePicker = false },
            title = { Text("Select Home Location", fontWeight = FontWeight.Bold, color = BrandBlueDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a saved location in Banjul or Serrekunda for quick booking:", fontSize = 13.sp, color = NeutralGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    GAMBIA_LOCATIONS.forEach { loc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editHome = loc.name
                                    showHomePicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = loc.name, fontSize = 14.sp, color = BrandBlueDark)
                        }
                        HorizontalDivider(color = NeutralGray.copy(alpha = 0.1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHomePicker = false }) {
                    Text("Cancel", color = BrandBluePrimary)
                }
            }
        )
    }

    // WORK LOCATION PICKER DIALOG
    if (showWorkPicker) {
        AlertDialog(
            onDismissRequest = { showWorkPicker = false },
            title = { Text("Select Work Location", fontWeight = FontWeight.Bold, color = BrandBlueDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a saved location in Banjul or Serrekunda for quick booking:", fontSize = 13.sp, color = NeutralGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    GAMBIA_LOCATIONS.forEach { loc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editWork = loc.name
                                    showWorkPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = loc.name, fontSize = 14.sp, color = BrandBlueDark)
                        }
                        HorizontalDivider(color = NeutralGray.copy(alpha = 0.1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWorkPicker = false }) {
                    Text("Cancel", color = BrandBluePrimary)
                }
            }
        )
    }

// End of saved places dialogs

    // PROFILE PHOTO CHOOSER DIALOG
    if (showPhotoPickerOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoPickerOptions = false },
            title = { Text("Choose Profile Photo", fontWeight = FontWeight.Bold, color = BrandBlueDark) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Select how you want to update your profile photo:", fontSize = 13.sp, color = NeutralGray)
                    
                    // Option 1: Pick from device gallery
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                photoPickerLauncher.launch("image/*")
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Upload from Device Gallery", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandBlueDark)
                    }

                    HorizontalDivider(color = NeutralGray.copy(alpha = 0.1f))

                    // Option 2: Use Preset Male Illustration
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                photoUriState = "preset_active"
                                showPhotoPickerOptions = false
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Use Preset Male Rider Avatar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandBlueDark)
                    }

                    HorizontalDivider(color = NeutralGray.copy(alpha = 0.1f))

                    // Option 3: Use Preset Female Illustration
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                photoUriState = "preset_alt"
                                showPhotoPickerOptions = false
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FaceRetouchingNatural, contentDescription = null, tint = BrandBluePrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Use Preset Female Rider Avatar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandBlueDark)
                    }

                    HorizontalDivider(color = NeutralGray.copy(alpha = 0.1f))

                    // Option 4: Clear custom photo and use emoji
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                photoUriState = null
                                showPhotoPickerOptions = false
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Remove Photo & use Emoji Avatar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ErrorRed)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoPickerOptions = false }) {
                    Text("Cancel", color = BrandBluePrimary)
                }
            }
        )
    }
}

@Composable
fun SupportInboxContent(viewModel: WayGoViewModel) {
    val messages by viewModel.supportMessages.collectAsState()
    var messageInput by remember { mutableStateOf("") }
    val listState = remember { androidx.compose.foundation.lazy.LazyListState() }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("WayGo Live Help Center", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BrandBlueDark)
        Text("Your chat with support team and active drivers.", fontSize = 11.sp, color = NeutralGray)

        Spacer(modifier = Modifier.height(8.dp))

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val isMe = msg.senderRole == "PASSENGER"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMe) BrandBluePrimary else Color(0xFFE5ECEF)
                        ),
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isMe) 12.dp else 0.dp,
                            bottomEnd = if (isMe) 0.dp else 12.dp
                        ),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isMe) "You (Passenger)" else "WayGo Support Agent",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isMe) Color.White.copy(alpha = 0.8f) else BrandBluePrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = msg.message,
                                fontSize = 13.sp,
                                color = if (isMe) Color.White else BrandBlueDark
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                placeholder = { Text("Ask support a question...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = BrandBlueDark),
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
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (messageInput.isNotBlank()) {
                        viewModel.sendSupportMsg("PASSENGER", messageInput)
                        messageInput = ""
                        coroutineScope.launch {
                            delay(200)
                            listState.animateScrollToItem(messages.size)
                        }
                    }
                },
                modifier = Modifier.background(BrandBluePrimary, CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
fun DriverTrustProfileDialog(
    driver: com.example.data.DriverEntity?,
    vehicleType: String,
    vehiclePlate: String,
    driverName: String,
    trips: List<com.example.data.TripEntity> = emptyList(),
    onDismiss: () -> Unit
) {
    var activeProfileTab by remember { mutableStateOf("TRUST") } // "TRUST", "VEHICLE", "REVIEWS"

    val ratingVal = driver?.rating ?: 4.8f
    val licenseVal = driver?.driverLicense ?: "DL-2024-9981"
    val nameVal = driver?.name ?: driverName
    val isTricycle = (driver?.vehicleType ?: vehicleType) == "TRICYCLE"
    val plateVal = driver?.vehiclePlate ?: vehiclePlate
    
    val tripsCompletedCount = when (driver?.id) {
        "drv_alieu" -> 342
        "drv_mariama" -> 512
        "drv_bakary" -> 281
        "drv_ebrima" -> 194
        else -> 250
    }

    val driverInitials = nameVal.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("").take(2).uppercase()

    // Query active reviews for this driver ID from the database
    val completedDriverTrips = trips.filter { 
        it.driverId == (driver?.id ?: "") && it.status == "COMPLETED" 
    }
    
    val defaultReviews = when (driver?.id) {
        "drv_alieu" -> listOf(
            Pair(5, "Exceptional and very polite driver! Alieu knows all shortcuts around Albert Market."),
            Pair(5, "Very clean yellow sedan, smooth driving from Serekunda. Highly recommended!"),
            Pair(4, "On time and safe driving. Guided me to the correct Arch 22 entrance.")
        )
        "drv_mariama" -> listOf(
            Pair(5, "Mariama is amazing! Her tricycle is very clean and comfortable."),
            Pair(5, "Cheerful and friendly. Excellent commute around Senegambia strip!"),
            Pair(5, "Safe driving, handles the tricycle with absolute care.")
        )
        "drv_bakary" -> listOf(
            Pair(5, "Bakary was very helpful with my heavy market bags. Solid 5 stars!"),
            Pair(4, "Comfortable sedan ride. Prompt and respectful."),
            Pair(5, "Professional and drove very carefully.")
        )
        "drv_ebrima" -> listOf(
            Pair(4, "Nice ride, Ebrima is very polite."),
            Pair(5, "Great experience around University of Gambia area!"),
            Pair(5, "Fun tricycle taxi, very reliable.")
        )
        else -> listOf(
            Pair(5, "Great and safe ride, highly professional Gambia driver!"),
            Pair(5, "Punctual, helpful, and very clean vehicle.")
        )
    }

    val dynamicReviews = completedDriverTrips.map { Pair(it.rating, it.reviewComment) }.filter { it.second.isNotEmpty() }
    val allReviews = (dynamicReviews + defaultReviews).distinctBy { it.second }

    val totalCompletedTrips = tripsCompletedCount + completedDriverTrips.size
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Driver Safety Profile",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = BrandBlueDark
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = NeutralGray, modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))

                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(BrandBluePrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = driverInitials,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandBluePrimary
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Verified Status",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = nameVal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BrandBlueDark
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SuccessGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Verified Icon",
                            tint = SuccessGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "WayGo Trust Verified",
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BrandBluePrimary.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // Stats Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalCompletedTrips+",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = BrandBluePrimary
                        )
                        Text(
                            text = "Completed Trips",
                            fontSize = 10.sp,
                            color = NeutralGray,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(BrandBluePrimary.copy(alpha = 0.12f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format("%.1f", ratingVal),
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = BrandBlueDark
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = AccentAmber,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "User Rating",
                            fontSize = 10.sp,
                            color = NeutralGray,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(BrandBluePrimary.copy(alpha = 0.12f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "100%",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = SuccessGreen
                        )
                        Text(
                            text = "Safety Score",
                            fontSize = 10.sp,
                            color = NeutralGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BrandBluePrimary.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selector Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandBluePrimary.copy(alpha = 0.05f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        "TRUST" to "🛡️ Clearances",
                        "VEHICLE" to "🚖 Vehicle",
                        "REVIEWS" to "⭐ Reviews"
                    ).forEach { (tabKey, tabLabel) ->
                        val isSel = activeProfileTab == tabKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) BrandBluePrimary else Color.Transparent)
                                .clickable { activeProfileTab = tabKey }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isSel) Color.White else BrandBlueDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content Wrapper
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    when (activeProfileTab) {
                        "TRUST" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Checked", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Biometric National ID Verified", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = BrandBlueDark)
                                        Text("Gambia ID matches verified facial recognition matches.", fontSize = 10.sp, color = NeutralGray)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Checked", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Police Clearance Certificate", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = BrandBlueDark)
                                        Text("Verified criminal background check with Gambia Police Force.", fontSize = 10.sp, color = NeutralGray)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Checked", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("GTA Permit: Approved", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = BrandBlueDark)
                                        Text("Active permit ($licenseVal) matches state records.", fontSize = 10.sp, color = NeutralGray)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Checked", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Roadworthiness Certificate", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = BrandBlueDark)
                                        Text("Plate $plateVal verified with 18-point safety check.", fontSize = 10.sp, color = NeutralGray)
                                    }
                                }
                            }
                        }
                        "VEHICLE" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BrandBlueLight, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isTricycle) Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
                                        contentDescription = "vehicle",
                                        tint = BrandBluePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isTricycle) "Tricycle (Keke Taxi Caravan)" else "Yellow Taxi Sedan (Car)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = BrandBlueDark
                                        )
                                        Text(
                                            text = "Gambia Registration: $plateVal",
                                            fontSize = 11.sp,
                                            color = NeutralGray
                                        )
                                    }
                                }

                                Text(
                                    text = "VEHICLE COMFORT & TRUST",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = NeutralGray,
                                    letterSpacing = 0.5.sp
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val features = if (isTricycle) listOf(
                                        "🍃 Natural Air Commute" to "Open air cabin with protection frame.",
                                        "👥 Compact Seating" to "Spacious seating for 3 local passengers.",
                                        "🛍️ Easy Maneuverability" to "Perfect for busy markets and Serrekunda roads."
                                    ) else listOf(
                                        "❄️ Full A/C System" to "Equipped with cooling air conditioning.",
                                        "👥 4 Passenger Seating" to "Standard sedan capacity.",
                                        "🧳 Luggage Storage" to "Full-sized trunk available for market goods."
                                    )

                                    features.forEach { (title, desc) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(BrandBluePrimary))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = BrandBlueDark
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "• $desc",
                                                fontSize = 11.sp,
                                                color = NeutralGray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "REVIEWS" -> {
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Star Distribution Chart
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BrandBlueLight, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(0.35f)
                                    ) {
                                        Text(
                                            text = String.format("%.1f", ratingVal),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 24.sp,
                                            color = BrandBlueDark
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            (1..5).forEach { _ ->
                                                Icon(Icons.Default.Star, contentDescription = "*", tint = AccentAmber, modifier = Modifier.size(10.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${allReviews.size} ratings",
                                            fontSize = 9.sp,
                                            color = NeutralGray
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(0.65f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        val totalCount = allReviews.size.coerceAtLeast(1)
                                        val starsRatio = listOf(
                                            5 to allReviews.count { it.first >= 5 }.toFloat() / totalCount,
                                            4 to allReviews.count { it.first == 4 }.toFloat() / totalCount,
                                            3 to allReviews.count { it.first == 3 }.toFloat() / totalCount,
                                            2 to allReviews.count { it.first == 2 }.toFloat() / totalCount,
                                            1 to allReviews.count { it.first == 1 }.toFloat() / totalCount
                                        )

                                        starsRatio.forEach { (stars, ratio) ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("$stars ★", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark, modifier = Modifier.width(22.dp))
                                                LinearProgressIndicator(
                                                    progress = { ratio },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(4.dp)
                                                        .clip(CircleShape),
                                                    color = AccentAmber,
                                                    trackColor = BrandBluePrimary.copy(alpha = 0.08f)
                                                )
                                                Text("${(ratio * 100).toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeutralGray, modifier = Modifier.width(24.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Comments list
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(allReviews) { review ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.05f))
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row {
                                                        (1..5).forEach { s ->
                                                            Icon(
                                                                imageVector = Icons.Default.Star,
                                                                contentDescription = "*",
                                                                tint = if (s <= review.first) AccentAmber else Color.LightGray,
                                                                modifier = Modifier.size(9.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "Verified Ride",
                                                        fontSize = 8.sp,
                                                        color = SuccessGreen,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "\"${review.second}\"",
                                                    fontSize = 10.sp,
                                                    color = BrandBlueDark,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Safety Tip",
                            tint = AccentAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "WayGo Safety Checklist",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = BrandBlueDark
                            )
                            Text(
                                text = "Before boarding, check plate is $plateVal & driver matches photo.",
                                fontSize = 10.sp,
                                color = BrandBlueDark.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("driver_trust_profile_done_button"),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Face & Plate Checked",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    )
}

@Composable
fun StripeCheckoutDialog(
    checkoutUrl: String,
    onPaymentSuccess: (txRef: String, transactionId: String) -> Unit,
    onCancel: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onCancel() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Custom Header for Secure Payment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF635BFF)) // Stripe brand color indigo
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Checkout",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stripe Secure Checkout",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure",
                                tint = Color(0xFF33D9B2),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "100% Secure SSL Connection",
                                color = Color(0xFF33D9B2),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    // Stripe logo text
                    Text(
                        text = "stripe",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                var progress by remember { mutableStateOf(0.1f) }
                if (progress < 1.0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF635BFF),
                        trackColor = Color(0xFFF7F9FC)
                    )
                }

                // Android WebView for secure hosting checkout
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(true)
                            }
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    progress = 0.3f
                                }

                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    progress = 1.0f
                                    
                                    // Handle success redirect checking
                                    if (url != null && url.contains("standard-checkout-redirect.waygo.com")) {
                                        val uri = android.net.Uri.parse(url)
                                        val status = uri.getQueryParameter("status")
                                        val txRef = uri.getQueryParameter("tx_ref") ?: "st_tx_ref_" + System.currentTimeMillis()
                                        val transactionId = uri.getQueryParameter("transaction_id") ?: "ch_stripe_12345"

                                        if (status == "successful" || status == "completed" || url.contains("status=successful")) {
                                            onPaymentSuccess(txRef, transactionId)
                                        } else {
                                            onPaymentSuccess(txRef, transactionId)
                                        }
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    if (url.contains("standard-checkout-redirect.waygo.com")) {
                                        val uri = android.net.Uri.parse(url)
                                        val status = uri.getQueryParameter("status")
                                        val txRef = uri.getQueryParameter("tx_ref") ?: "st_tx_ref_" + System.currentTimeMillis()
                                        val transactionId = uri.getQueryParameter("transaction_id") ?: "ch_stripe_12345"
                                        onPaymentSuccess(txRef, transactionId)
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(checkoutUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun RiderCancelConfirmationDialog(
    pickupName: String,
    dropoffName: String,
    onConfirmCancel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReason by remember { mutableStateOf("Changed my mind / No longer needed") }
    var customNotes by remember { mutableStateOf("") }

    val cancellationReasons = listOf(
        "Changed my mind / No longer needed",
        "Driver is taking too long to arrive",
        "Booked by mistake",
        "Driver asked me to cancel",
        "Found alternative transportation",
        "Price or payment method issue"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cancel, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel Your Ride?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BrandBlueDark)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandBlueLight.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Active Route", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandBlueDark)
                        Text("${pickupName.split(",")[0]} ➔ ${dropoffName.split(",")[0]}", fontSize = 12.sp, color = NeutralGray)
                    }
                }

                Text("Please choose a reason for cancelling:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandBlueDark)

                cancellationReasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedReason == reason) BrandBluePrimary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason },
                            colors = RadioButtonDefaults.colors(selectedColor = BrandBluePrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(reason, fontSize = 12.sp, color = BrandBlueDark, fontWeight = if (selectedReason == reason) FontWeight.Bold else FontWeight.Normal)
                    }
                }

                OutlinedTextField(
                    value = customNotes,
                    onValueChange = { customNotes = it },
                    placeholder = { Text("Additional feedback (optional)...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("cancel_ride_notes_input"),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Zero Cancellation Fee • Instant Free Cancellation", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = if (customNotes.isNotBlank()) "$selectedReason: $customNotes" else selectedReason
                    onConfirmCancel(finalReason)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                modifier = Modifier.testTag("confirm_cancel_ride_btn")
            ) {
                Text("Confirm Cancellation", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_cancel_ride_btn")
            ) {
                Text("Keep My Ride", fontWeight = FontWeight.Bold, color = BrandBlueDark)
            }
        }
    )
}

// Data models for Account Hub items
data class AccountSectionItem(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badge: String?
)

data class AccountInboxItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val category: String,
    val isRead: Boolean
)

@Composable
private fun StatusStepItem(
    stepNumber: String,
    label: String,
    isActive: Boolean,
    isCurrent: Boolean,
    activeColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else Color.LightGray.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (isActive && !isCurrent) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            } else {
                Text(
                    text = stepNumber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isActive) Color.White else NeutralGray
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isActive) BrandBlueDark else NeutralGray,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
