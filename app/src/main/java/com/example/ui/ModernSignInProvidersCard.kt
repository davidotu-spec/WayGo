package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.example.utils.AuthValidator
import com.example.utils.PasswordStrength
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ModernSignInProvidersCard(
    userRole: String, // "PASSENGER" or "DRIVER"
    activeRole: String,
    onSelectRole: (String) -> Unit,
    isDark: Boolean = false,
    isAuthenticating: Boolean = false,
    onGoogleAuthClick: () -> Unit,
    onAppleAuthClick: () -> Unit = {},
    onEmailLoginSubmit: (email: String, pass: String) -> Unit,
    onEmailRegisterSubmit: (email: String, pass: String, name: String, vehicleType: String, vehiclePlate: String, licenseNum: String, onError: (String) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    authError: String = "",
    isAdminLoggedIn: Boolean = false,
    isSecretAdminUnlocked: Boolean = false,
    onLongPressHeader: () -> Unit = {},
    onQuickSelectAccount: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var isEmailModeExpanded by remember { mutableStateOf(true) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var vehicleTypeInput by remember { mutableStateOf("CAR") }
    var vehiclePlateInput by remember { mutableStateOf("") }
    var licenseNumInput by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(authError) {
        if (authError.isNotBlank()) {
            isSubmitting = false
            localError = authError
        }
    }

    LaunchedEffect(isAuthenticating) {
        if (!isAuthenticating) {
            isSubmitting = false
        }
    }

    val cardBg = if (isDark) Color(0xFF1E293B) else PureWhite
    val textPrimary = if (isDark) PureWhite else BrandBlueDark
    val textSecondary = if (isDark) Color(0xFF94A3B8) else NeutralGray
    val inputBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val borderCol = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .testTag("modern_login_card"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Role Section Switcher Tabs (Passenger vs Driver)
            AuthRoleSectionTabs(
                activeRole = activeRole,
                onSelectRole = onSelectRole,
                isDarkBg = isDark
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quick One-Tap Driver Selection for rapid fleet portal entry
            if (activeRole == "DRIVER") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("driver_fleet_quick_access_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E293B) else BrandBlueLight
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Quick Fleet Entry",
                                tint = AccentAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "One-Tap Driver Fleet Sign In",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BrandBlueDark
                                )
                                Text(
                                    text = "Select active fleet vehicle or tap Instant Access:",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val fleetAccounts = listOf(
                            Triple("Alieu Ceesay", "driver.alieu@waygo.com", "Yellow Cab • BJL 4821 C"),
                            Triple("Mariama Jallow", "mariama.driver@waygo.com", "Tricycle • KM 9312 T"),
                            Triple("Bakary Touray", "bakary.driver@waygo.com", "Comfort Sedan • WCR 7431 B")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            fleetAccounts.forEach { (name, email, vehicle) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) Color(0xFF0F172A) else PureWhite,
                                    border = BorderStroke(1.dp, borderCol),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            onQuickSelectAccount(email, "driver123")
                                        }
                                        .testTag("quick_driver_btn_${name.take(4).lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = textPrimary
                                            )
                                            Text(
                                                text = vehicle,
                                                fontSize = 10.5.sp,
                                                color = textSecondary
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Sign In",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandBluePrimary
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = null,
                                                tint = BrandBluePrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // SIGN-IN PROVIDERS BUTTONS (EXACTLY MATCHING USER MOCKUP IMAGE)
            // 1. Continue with Apple
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFF0F172A) else PureWhite,
                border = BorderStroke(1.dp, borderCol),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onAppleAuthClick()
                    }
                    .testTag("provider_apple_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Apple",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Continue with Google
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFF0F172A) else PureWhite,
                border = BorderStroke(1.dp, borderCol),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onGoogleAuthClick()
                    }
                    .testTag("provider_google_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4285F4).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF4285F4))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Continue with Email
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFF0F172A) else PureWhite,
                border = BorderStroke(1.dp, borderCol),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable {
                        isEmailModeExpanded = !isEmailModeExpanded
                        localError = ""
                    }
                    .testTag("provider_email_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Sign In",
                        tint = BrandBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isEmailModeExpanded) "Hide Email Login" else "Continue with Email",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                }
            }

            // EXPANDABLE EMAIL SIGN IN / REGISTER FORM
            AnimatedVisibility(
                visible = isEmailModeExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                // Real-time validation results
                val emailValidation = remember(emailInput) {
                    if (emailInput.isNotBlank()) AuthValidator.validateEmail(emailInput) else null
                }
                val passwordValidation = remember(passwordInput) {
                    if (passwordInput.isNotBlank()) AuthValidator.validatePassword(passwordInput) else null
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(inputBg)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRegisterMode) "Create $userRole Account" else "Email Sign In",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Clear Mode Toggle Segmented Control (Sign In vs Create Account)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                            .padding(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isRegisterMode) BrandBluePrimary else Color.Transparent)
                                .clickable {
                                    isRegisterMode = false
                                    localError = ""
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign In",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isRegisterMode) PureWhite else textSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isRegisterMode) BrandBluePrimary else Color.Transparent)
                                .clickable {
                                    isRegisterMode = true
                                    localError = ""
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Account",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRegisterMode) PureWhite else textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isRegisterMode) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                if (localError.isNotBlank()) localError = ""
                            },
                            label = { Text("Full Name (Optional)", color = textSecondary) },
                            placeholder = { Text("e.g. Lamin Touray", color = textSecondary.copy(alpha = 0.6f)) },
                            supportingText = {
                                Text("Auto-derived from email if left blank", fontSize = 11.sp, color = textSecondary.copy(alpha = 0.7f))
                            },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BrandBluePrimary) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandBluePrimary,
                                unfocusedBorderColor = borderCol,
                                focusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                                unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                cursorColor = BrandBluePrimary,
                                focusedLabelColor = BrandBluePrimary,
                                unfocusedLabelColor = textSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input_field"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Email Address Field with real-time format indicator
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            if (localError.isNotBlank()) localError = ""
                        },
                        label = { Text("Email Address", color = textSecondary) },
                        placeholder = { Text("e.g. user@gmail.com", color = textSecondary.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = BrandBluePrimary) },
                        trailingIcon = {
                            if (emailInput.isNotBlank()) {
                                val isValid = emailValidation?.isValid == true
                                Icon(
                                    imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = if (isValid) "Valid Email" else "Invalid Email",
                                    tint = if (isValid) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        isError = emailInput.isNotBlank() && emailValidation?.isValid == false,
                        supportingText = if (emailInput.isNotBlank() && emailValidation?.isValid == false) {
                            { Text(emailValidation.errorMessage ?: "Invalid email format", color = Color(0xFFEF4444), fontSize = 11.sp) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBluePrimary,
                            unfocusedBorderColor = borderCol,
                            errorBorderColor = Color(0xFFEF4444),
                            focusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                            unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = BrandBluePrimary,
                            focusedLabelColor = BrandBluePrimary,
                            unfocusedLabelColor = textSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input_field"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Password Field
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            if (localError.isNotBlank()) localError = ""
                        },
                        label = { Text("Password", color = textSecondary) },
                        placeholder = { Text("At least 6 characters", color = textSecondary.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BrandBluePrimary) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = textSecondary
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = isRegisterMode && passwordInput.isNotBlank() && passwordValidation?.isValid == false,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBluePrimary,
                            unfocusedBorderColor = borderCol,
                            errorBorderColor = Color(0xFFEF4444),
                            focusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                            unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = BrandBluePrimary,
                            focusedLabelColor = BrandBluePrimary,
                            unfocusedLabelColor = textSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input_field"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Forgot Password Button in Login Mode
                    if (!isRegisterMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showForgotPasswordDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("email_forgot_password_btn")
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandBluePrimary
                                )
                            }
                        }
                    }

                    // Registration Password Requirements and Strength Indicator
                    if (isRegisterMode && passwordInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val strength = passwordValidation?.strength ?: PasswordStrength.TOO_SHORT
                        val strengthColor = when (strength) {
                            PasswordStrength.TOO_SHORT -> Color(0xFFEF4444)
                            PasswordStrength.WEAK -> Color(0xFFF97316)
                            PasswordStrength.MEDIUM -> Color(0xFFEAB308)
                            PasswordStrength.STRONG -> Color(0xFF10B981)
                        }
                        val strengthProgress = when (strength) {
                            PasswordStrength.TOO_SHORT -> 0.25f
                            PasswordStrength.WEAK -> 0.5f
                            PasswordStrength.MEDIUM -> 0.75f
                            PasswordStrength.STRONG -> 1.0f
                        }

                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Password Strength: ${strength.label}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = strengthColor
                                )
                                Text(
                                    text = "${passwordInput.length}/6+ chars",
                                    fontSize = 10.5.sp,
                                    color = if (passwordInput.length >= 6) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { strengthProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = strengthColor,
                                trackColor = borderCol
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // Checklist items
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val minLenMet = passwordValidation?.hasMinLength == true
                                val letterMet = passwordValidation?.hasLetter == true
                                val digitMet = passwordValidation?.hasDigitOrSymbol == true

                                RequirementPill(label = "6+ Chars", isMet = minLenMet, isDark = isDark)
                                RequirementPill(label = "Letters", isMet = letterMet, isDark = isDark)
                                RequirementPill(label = "Numbers/Symbols", isMet = digitMet, isDark = isDark)
                            }
                        }
                    }

                    if (userRole == "DRIVER" && isRegisterMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = vehicleTypeInput,
                            onValueChange = { vehicleTypeInput = it },
                            label = { Text("Vehicle Type (CAR, TAXI, TRICYCLE)", color = textSecondary) },
                            placeholder = { Text("CAR", color = textSecondary.copy(alpha = 0.6f)) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandBluePrimary,
                                unfocusedBorderColor = borderCol,
                                focusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                                unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                cursorColor = BrandBluePrimary,
                                focusedLabelColor = BrandBluePrimary,
                                unfocusedLabelColor = textSecondary
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = vehiclePlateInput,
                            onValueChange = { vehiclePlateInput = it },
                            label = { Text("License Plate", color = textSecondary) },
                            placeholder = { Text("e.g. BJL 1234 A", color = textSecondary.copy(alpha = 0.6f)) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandBluePrimary,
                                unfocusedBorderColor = borderCol,
                                focusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                                unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else PureWhite,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                cursorColor = BrandBluePrimary,
                                focusedLabelColor = BrandBluePrimary,
                                unfocusedLabelColor = textSecondary
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Visible UI Feedback for errors
                    AnimatedVisibility(visible = localError.isNotBlank()) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = localError,
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            localError = ""
                            focusManager.clearFocus()
                            keyboardController?.hide()

                            if (isRegisterMode) {
                                val cleanEmail = emailInput.trim()
                                val derivedName = if (nameInput.isNotBlank()) {
                                    nameInput.trim()
                                } else {
                                    cleanEmail.substringBefore("@")
                                        .replace(".", " ")
                                        .replace("_", " ")
                                        .replace("-", " ")
                                        .split(" ")
                                        .filter { it.isNotBlank() }
                                        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                                        .ifBlank { if (userRole == "DRIVER") "Fleet Driver" else "WayGo Passenger" }
                                }

                                // Explicit Pre-Firebase Validation Layer
                                val validation = AuthValidator.validateSignUp(
                                    email = cleanEmail,
                                    password = passwordInput,
                                    name = derivedName,
                                    isDriver = (userRole == "DRIVER"),
                                    vehiclePlate = vehiclePlateInput.ifBlank { "BJL 9988 X" }
                                )

                                if (!validation.isValid) {
                                    val err = validation.errorMessage ?: "Please verify all registration fields."
                                    localError = err
                                    Toast.makeText(context, "⚠️ $err", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isSubmitting = true
                                Toast.makeText(context, "Authorizing driver account...", Toast.LENGTH_SHORT).show()
                                onEmailRegisterSubmit(
                                    cleanEmail,
                                    passwordInput.trim(),
                                    derivedName,
                                    vehicleTypeInput.trim(),
                                    vehiclePlateInput.ifBlank { "BJL 9988 X" }.trim(),
                                    licenseNumInput.ifBlank { "GAM-DL-9082" }.trim()
                                ) { err ->
                                    if (err.contains("already in use", ignoreCase = true) || err.contains("already exists", ignoreCase = true)) {
                                        // Account already registered in fleet: automatically complete sign-in!
                                        Toast.makeText(context, "Account found, signing into fleet...", Toast.LENGTH_SHORT).show()
                                        onEmailLoginSubmit(cleanEmail, passwordInput.trim())
                                    } else {
                                        isSubmitting = false
                                        localError = err
                                        Toast.makeText(context, "⚠️ $err", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                // Pre-Firebase Login Validation
                                val emailCheck = AuthValidator.validateEmail(emailInput)
                                if (!emailCheck.isValid) {
                                    val err = emailCheck.errorMessage ?: "Please enter a valid email address."
                                    localError = err
                                    Toast.makeText(context, "⚠️ $err", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                if (passwordInput.isBlank()) {
                                    val err = "Please enter your password."
                                    localError = err
                                    Toast.makeText(context, "⚠️ $err", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isSubmitting = true
                                onEmailLoginSubmit(emailInput.trim(), passwordInput.trim())
                            }
                        },
                        enabled = !isSubmitting && !isAuthenticating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("email_submit_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandBluePrimary,
                            disabledContainerColor = BrandBluePrimary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmitting || isAuthenticating) {
                            CircularProgressIndicator(
                                color = PureWhite,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRegisterMode) "Creating Account..." else "Signing In...",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        } else {
                            Text(
                                text = if (isRegisterMode) "Register & Sign In" else "Sign In with Email",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                    }
                }
            }

            // Error Display
            val errToDisplay = if (localError.isNotBlank()) localError else authError
            if (errToDisplay.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errToDisplay,
                    color = ErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            initialEmail = emailInput,
            isDark = isDark,
            onDismiss = { showForgotPasswordDialog = false }
        )
    }
}

@Composable
private fun RequirementPill(
    label: String,
    isMet: Boolean,
    isDark: Boolean
) {
    val pillBg = if (isMet) {
        Color(0xFF10B981).copy(alpha = 0.15f)
    } else {
        if (isDark) Color(0xFF334155).copy(alpha = 0.4f) else Color(0xFFE2E8F0)
    }
    val pillTextColor = if (isMet) {
        Color(0xFF10B981)
    } else {
        if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = pillBg,
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = if (isMet) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = pillTextColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isMet) FontWeight.Bold else FontWeight.Medium,
                color = pillTextColor
            )
        }
    }
}


