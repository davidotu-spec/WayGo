package com.example.ui

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Clean, production-ready Login Screen utilizing AuthenticationViewModel and Firebase Auth
 * to handle both Passenger and Driver sign-in and registration flows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthenticationViewModel,
    modifier: Modifier = Modifier,
    initialRole: String = "PASSENGER",
    onAuthSuccess: (role: String) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    onRoleChange: ((role: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val selectedRole by authViewModel.selectedRole.collectAsState()

    // BackHandler to smoothly return to Passenger screen if in Driver mode or back requested
    androidx.activity.compose.BackHandler {
        if (onNavigateBack != null) {
            onNavigateBack()
        } else if (onRoleChange != null && selectedRole == "DRIVER") {
            onRoleChange("PASSENGER")
        }
    }
    val isPassengerLoggedIn by authViewModel.isPassengerLoggedIn.collectAsState()
    val isDriverLoggedIn by authViewModel.isDriverLoggedIn.collectAsState()

    val passengerAuthError by authViewModel.passengerAuthError.collectAsState()
    val driverAuthError by authViewModel.driverAuthError.collectAsState()
    val isPassengerAuthenticating by authViewModel.isPassengerAuthenticating.collectAsState()
    val isDriverAuthenticating by authViewModel.isDriverAuthenticating.collectAsState()

    val isVerificationPending by authViewModel.isVerificationPending.collectAsState()
    val pendingVerificationEmail by authViewModel.pendingVerificationEmail.collectAsState()
    val pendingVerificationRole by authViewModel.pendingVerificationRole.collectAsState()
    val verificationCode by authViewModel.verificationCode.collectAsState()
    val verificationMessage by authViewModel.verificationMessage.collectAsState()

    val pendingProfileCompletion by authViewModel.pendingProfileCompletion.collectAsState()

    // Sync initial role if set
    LaunchedEffect(initialRole) {
        if (selectedRole != initialRole) {
            authViewModel.setSelectedRole(initialRole)
        }
    }

    var hasNotifiedSuccess by remember { mutableStateOf(false) }

    // Reset notification tracker if user is logged out
    LaunchedEffect(isPassengerLoggedIn, isDriverLoggedIn) {
        if (!isPassengerLoggedIn && !isDriverLoggedIn) {
            hasNotifiedSuccess = false
        }
    }

    // Trigger callback when login succeeds
    LaunchedEffect(isPassengerLoggedIn, isDriverLoggedIn, selectedRole) {
        if (!hasNotifiedSuccess) {
            if (isPassengerLoggedIn && selectedRole == "PASSENGER") {
                hasNotifiedSuccess = true
                onAuthSuccess("PASSENGER")
            } else if (isDriverLoggedIn && selectedRole == "DRIVER") {
                hasNotifiedSuccess = true
                onAuthSuccess("DRIVER")
            }
        }
    }

    // Account verification dialog
    if (isVerificationPending) {
        AccountVerificationDialog(
            userEmail = pendingVerificationEmail,
            userRole = pendingVerificationRole,
            generatedCode = verificationCode,
            verificationMessage = verificationMessage,
            onVerifyCode = { code ->
                val success = authViewModel.confirmAccountVerification(code)
                if (success) {
                    hasNotifiedSuccess = true
                    onAuthSuccess(pendingVerificationRole)
                }
                success
            },
            onResendEmail = {
                authViewModel.resendVerificationEmail()
            },
            onDismiss = {
                authViewModel.cancelAccountVerification()
            }
        )
    }

    // Profile completion dialog if triggered post-registration
    pendingProfileCompletion?.let { pending ->
        ProfileCompletionDialog(
            userId = pending.uid,
            userEmail = pending.email,
            initialDisplayName = pending.initialName,
            initialPhoneNumber = pending.initialPhone,
            userRole = pending.role,
            isDark = false,
            onProfileCompleted = { name, phone ->
                authViewModel.completeUserProfile(name, phone) {
                    onAuthSuccess(pending.role)
                }
            },
            onDismiss = { authViewModel.dismissProfileCompletionPrompt() }
        )
    }

    var showGoogleAuthDialog by remember { mutableStateOf(false) }

    if (showGoogleAuthDialog) {
        GoogleAccountAuthDialog(
            userRole = selectedRole,
            isDark = false,
            onDismiss = { showGoogleAuthDialog = false },
            onAuthenticate = { gEmail, gName, gPass, vType, vPlate, lNum, isReg, onError ->
                if (selectedRole == "DRIVER") {
                    authViewModel.loginOrRegisterDriverWithGoogle(
                        googleEmail = gEmail,
                        googleName = gName,
                        pass = gPass,
                        vehicleType = vType,
                        vehiclePlate = vPlate,
                        licenseNum = lNum,
                        isRegisterMode = isReg,
                        onSuccess = {
                            showGoogleAuthDialog = false
                            onAuthSuccess("DRIVER")
                        },
                        onError = onError
                    )
                } else {
                    authViewModel.loginOrRegisterPassengerWithGoogle(
                        googleEmail = gEmail,
                        googleName = gName,
                        pass = gPass,
                        isRegisterMode = isReg,
                        onSuccess = {
                            showGoogleAuthDialog = false
                            onAuthSuccess("PASSENGER")
                        },
                        onError = onError
                    )
                }
            }
        )
    }

    val currentAuthError = if (selectedRole == "DRIVER") driverAuthError else passengerAuthError
    val currentIsAuthenticating = if (selectedRole == "DRIVER") isDriverAuthenticating else isPassengerAuthenticating

    Box(
        modifier = modifier
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
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Optional Top Navigation Bar if back or role change is available
            if (onNavigateBack != null || (selectedRole == "DRIVER" && onRoleChange != null)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (onNavigateBack != null) onNavigateBack()
                            else onRoleChange?.invoke("PASSENGER")
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("login_back_to_passenger_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Back to Passenger",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AssistChip(
                        onClick = { onRoleChange?.invoke("PASSENGER") },
                        label = { Text("Passenger Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = BrandBluePrimary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("login_switch_passenger_chip")
                    )
                }
            }

            // Header Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(bottom = 18.dp)
                    .testTag("waygo_auth_header")
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "WayGo",
                        color = BrandBlueDark,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
                Column {
                    Text(
                        text = "WayGo Gambia",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (selectedRole == "DRIVER") "Driver Fleet Portal" else "Passenger Ride Booking",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Modern Multi-Provider Card for Firebase Auth
            ModernSignInProvidersCard(
                userRole = selectedRole,
                activeRole = selectedRole,
                onSelectRole = { newRole ->
                    authViewModel.setSelectedRole(newRole)
                    authViewModel.clearErrors()
                    onRoleChange?.invoke(newRole)
                },
                isDark = false,
                isAuthenticating = currentIsAuthenticating,
                onGoogleAuthClick = { showGoogleAuthDialog = true },
                onAppleAuthClick = {
                    if (selectedRole == "DRIVER") {
                        authViewModel.loginDriverWithEmail("apple.driver@waygo.com", "driver123")
                    } else {
                        authViewModel.loginPassengerWithEmail("apple.passenger@waygo.com", "passenger123")
                    }
                },
                onEmailLoginSubmit = { email, pass ->
                    if (selectedRole == "DRIVER") {
                        authViewModel.loginDriverWithEmail(email, pass)
                    } else {
                        authViewModel.loginPassengerWithEmail(email, pass)
                    }
                },
                onEmailRegisterSubmit = { email, pass, name, vType, vPlate, lNum, errCb ->
                    if (selectedRole == "DRIVER") {
                        authViewModel.registerDriverWithEmail(
                            email = email,
                            pass = pass,
                            name = name,
                            vehicleType = vType,
                            vehiclePlate = vPlate,
                            licenseNum = lNum,
                            onSuccess = { onAuthSuccess("DRIVER") },
                            onError = errCb
                        )
                    } else {
                        authViewModel.registerPassengerWithEmail(
                            email = email,
                            pass = pass,
                            name = name,
                            onSuccess = { onAuthSuccess("PASSENGER") },
                            onError = errCb
                        )
                    }
                },
                authError = currentAuthError,
                onQuickSelectAccount = { email, pass ->
                    if (selectedRole == "DRIVER") {
                        authViewModel.loginDriverWithEmail(email, pass)
                    } else {
                        authViewModel.loginPassengerWithEmail(email, pass)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Trust badge footer
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Secure",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "End-to-End Secure • Firebase Authentication",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
