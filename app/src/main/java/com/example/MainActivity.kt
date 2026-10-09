package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AccountVerificationDialog
import com.example.ui.ProfileCompletionDialog
import com.example.ui.WayGoViewModel
import com.example.ui.WayGoViewModelFactory
import com.example.ui.AuthenticationViewModel
import com.example.ui.AuthenticationViewModelFactory
import com.example.ui.DriverScreen
import com.example.ui.PassengerScreen
import com.example.ui.theme.*

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class MainActivity : ComponentActivity() {
    private var currentViewModel: WayGoViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Fetch lazy initialized repo from application class
            val app = application as WayGoApplication
            val sharedPrefs = app.getSharedPreferences("waygo_prefs", android.content.Context.MODE_PRIVATE)
            val factory = WayGoViewModelFactory(app.repository, sharedPrefs)
            val viewModel: WayGoViewModel = viewModel(factory = factory)
            currentViewModel = viewModel

            // Process App Links / Deep Links when activity is launched
            LaunchedEffect(intent) {
                intent?.data?.let { uri ->
                    val urlString = uri.toString()
                    if (com.example.data.FirebaseAuthManager.isSignInWithEmailLink(urlString)) {
                        viewModel.handleIncomingEmailLink(urlString)
                    }
                }
            }

            val themeMode by viewModel.themeMode.collectAsState()
            val isDarkTheme = when (themeMode) {
                com.example.ui.ThemeMode.LIGHT -> false
                com.example.ui.ThemeMode.DARK -> true
                com.example.ui.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val authFactory = AuthenticationViewModelFactory(app.repository, sharedPrefs)
            val authViewModel: AuthenticationViewModel = viewModel(factory = authFactory)

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WayGoMasterApp(viewModel = viewModel, authViewModel = authViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            val urlString = uri.toString()
            if (com.example.data.FirebaseAuthManager.isSignInWithEmailLink(urlString)) {
                currentViewModel?.handleIncomingEmailLink(urlString)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WayGoMasterApp(
    viewModel: WayGoViewModel,
    authViewModel: AuthenticationViewModel? = null
) {
    val activeRole by viewModel.currentRole.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeNotif by viewModel.activePushNotification.collectAsState()

    var showSectionSelectorSheet by remember { mutableStateOf(false) }

    // Status bar push notification triggers
    LaunchedEffect(activeNotif) {
        activeNotif?.let { notif ->
            showAndroidSystemNotification(
                context = context,
                title = notif.title,
                message = notif.message
            )
            // Auto-dismiss HUD overlay after 5 seconds
            kotlinx.coroutines.delay(5000)
            if (viewModel.activePushNotification.value?.id == notif.id) {
                viewModel.dismissActivePushNotification()
            }
        }
    }

    val isVerificationPending by viewModel.isVerificationPending.collectAsState()
    val pendingVerificationEmail by viewModel.pendingVerificationEmail.collectAsState()
    val pendingVerificationRole by viewModel.pendingVerificationRole.collectAsState()
    val verificationCode by viewModel.verificationCode.collectAsState()
    val verificationMessage by viewModel.verificationMessage.collectAsState()
    val pendingProfileCompletion by viewModel.pendingProfileCompletion.collectAsState()

    if (isVerificationPending) {
        AccountVerificationDialog(
            userEmail = pendingVerificationEmail,
            userRole = pendingVerificationRole,
            generatedCode = verificationCode,
            verificationMessage = verificationMessage,
            onVerifyCode = { code ->
                viewModel.confirmAccountVerification(code)
            },
            onResendEmail = {
                viewModel.resendVerificationEmail()
            },
            onDismiss = {
                viewModel.cancelAccountVerification()
            }
        )
    } else {
        pendingProfileCompletion?.let { pending ->
            ProfileCompletionDialog(
                userId = pending.uid,
                userEmail = pending.email,
                initialDisplayName = pending.initialName,
                initialPhoneNumber = pending.initialPhone,
                userRole = pending.role,
                isDark = false,
                onProfileCompleted = { name, phone ->
                    viewModel.completeUserProfile(name, phone)
                },
                onDismiss = { viewModel.dismissProfileCompletionPrompt() }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Active View Swap - Full screen without intrusive top segment header bar
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeRole) {
                "PASSENGER" -> PassengerScreen(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    onOpenSectionSheet = { showSectionSelectorSheet = true }
                )
                "DRIVER" -> DriverScreen(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    onOpenSectionSheet = { showSectionSelectorSheet = true }
                )
                else -> PassengerScreen(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    onOpenSectionSheet = { showSectionSelectorSheet = true }
                )
            }
        }

        // Modal Bottom Sheet to switch between distinct app sections
        if (showSectionSelectorSheet) {
            AppSectionSelectionSheet(
                activeRole = activeRole,
                onRoleSelected = { newRole ->
                    viewModel.setRole(newRole)
                },
                onDismiss = { showSectionSelectorSheet = false }
            )
        }

        // Heads-up Push Notification HUD sliding down only when in DRIVER fleet section
        androidx.compose.animation.AnimatedVisibility(
            visible = activeNotif != null && activeRole == "DRIVER",
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            activeNotif?.let { notif ->
                Card(
                    onClick = {
                        // If they click on it, take them directly to the Driver segment to manage!
                        viewModel.setRole("DRIVER")
                        viewModel.setActiveDriver(notif.driverId)
                        viewModel.dismissActivePushNotification()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("heads_up_notification_hud"),
                    colors = CardDefaults.cardColors(containerColor = BrandBlueDark),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentAmber.copy(alpha = 0.5f))
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
                                // Animated glowing notification bell
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Notification Bell",
                                    tint = AccentAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "WayGo • Driver Push Alert",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = notif.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Dismiss Icon Button
                            IconButton(
                                onClick = { viewModel.dismissActivePushNotification() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Alert",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Target driver info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Driver",
                                tint = AccentAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Dispatched to: ${notif.driverName}",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = notif.message,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Actions on HUD
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    // Accept booking directly via push notification!
                                    notif.payload?.let { trip ->
                                        viewModel.setRole("DRIVER")
                                        viewModel.setActiveDriver(notif.driverId)
                                        viewModel.acceptBooking(trip.id, notif.driverId)
                                    }
                                    viewModel.dismissActivePushNotification()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(36.dp)
                                    .testTag("hud_accept_btn"),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Accept Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.dismissActivePushNotification()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Ignore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun showAndroidSystemNotification(context: Context, title: String, message: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    )

    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(context, "waygo_driver_alerts")
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(context)
    }

    val notification = builder
        .setContentTitle(title)
        .setContentText(message)
        .setSmallIcon(android.R.drawable.stat_notify_chat) // standard built-in icon
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSectionSelectionSheet(
    activeRole: String,
    onRoleSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp)
                .testTag("role_switcher_card")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(BrandBluePrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Switch Section",
                        tint = BrandBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "WayGo Platform Sections",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Switch between distinct operational modules",
                        fontSize = 12.sp,
                        color = NeutralGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Passenger
            SectionOptionCard(
                title = "Passenger Section",
                subtitle = "Book trips, track driver location & secure digital payments",
                icon = Icons.Default.DirectionsCar,
                isSelected = activeRole == "PASSENGER",
                onClick = {
                    onRoleSelected("PASSENGER")
                    onDismiss()
                },
                modifier = Modifier.testTag("segment_passenger")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Section 2: Driver Hub
            SectionOptionCard(
                title = "Driver Hub Section",
                subtitle = "Accept ride requests, track mileage, shift performance & payouts",
                icon = Icons.Default.TwoWheeler,
                isSelected = activeRole == "DRIVER",
                onClick = {
                    onRoleSelected("DRIVER")
                    onDismiss()
                },
                modifier = Modifier.testTag("segment_driver")
            )
        }
    }
}

@Composable
fun SectionOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBg by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) BrandBluePrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        label = "sectionCardBg"
    )

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = animatedBg),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) BrandBluePrimary else Color.LightGray.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isSelected) BrandBluePrimary else Color.LightGray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) Color.White else NeutralGray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isSelected) BrandBluePrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = NeutralGray,
                    lineHeight = 15.sp
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = BrandBluePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun RoleSegmentButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isActive) BrandBluePrimary else Color.Transparent,
        label = "roleButtonBg"
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        label = "roleButtonFg"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backColor)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}
