package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.example.utils.AuthValidator
import com.example.utils.PasswordStrength
import com.example.ui.theme.*

@Composable
fun GoogleAccountAuthDialog(
    userRole: String = "PASSENGER", // "PASSENGER" or "DRIVER"
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onAuthenticate: (
        email: String,
        name: String,
        pass: String,
        vehicleType: String,
        vehiclePlate: String,
        licenseNum: String,
        isRegister: Boolean,
        onError: (String) -> Unit
    ) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    // Driver specific fields
    var vehicleType by remember { mutableStateOf("CAR") }
    var vehiclePlate by remember { mutableStateOf("") }
    var licenseNum by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    val dialogBg = if (isDark) Color(0xFF1E293B) else PureWhite
    val textPrimary = if (isDark) PureWhite else BrandBlueDark
    val textSecondary = if (isDark) Color(0xFF94A3B8) else NeutralGray
    val inputBg = if (isDark) Color(0xFF0F172A) else BrandBlueLight
    val borderCol = if (isDark) Color(0xFF334155) else NeutralGray.copy(alpha = 0.3f)

    Dialog(
        onDismissRequest = { if (!isLoading) safeDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .imePadding()
                .padding(16.dp)
                .testTag("google_auth_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = dialogBg,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Google Brand Header Badge
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4285F4).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.3f)),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "G",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF4285F4)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isRegisterMode) "Create WayGo Account" else "Google Account Sign In",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Firebase Auth Security Subtitle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF34A853).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Security Shield",
                            tint = Color(0xFF34A853),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Firebase Auth • Google OAuth 2.0 Verified",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF137333)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Switcher for Sign In vs Register
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(inputBg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = {
                            isRegisterMode = false
                            errorMessage = ""
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("google_auth_mode_signin"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isRegisterMode) Color(0xFF4285F4) else Color.Transparent,
                            contentColor = if (!isRegisterMode) PureWhite else textSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = null
                    ) {
                        Text("Sign In", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            isRegisterMode = true
                            errorMessage = ""
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("google_auth_mode_signup"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRegisterMode) Color(0xFF4285F4) else Color.Transparent,
                            contentColor = if (isRegisterMode) PureWhite else textSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = null
                    ) {
                        Text("Create Account", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input: Google Email
                val emailValidation = remember(emailInput) {
                    if (emailInput.isNotBlank()) AuthValidator.validateEmail(emailInput) else null
                }
                val passwordValidation = remember(passwordInput) {
                    if (passwordInput.isNotBlank()) AuthValidator.validatePassword(passwordInput) else null
                }
                val context = LocalContext.current

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it
                        if (errorMessage.isNotBlank()) errorMessage = ""
                    },
                    label = { Text("Google Email Address", color = textSecondary) },
                    placeholder = { Text("e.g. user@gmail.com", color = textSecondary.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF4285F4)) },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_email_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4285F4),
                        unfocusedBorderColor = borderCol,
                        errorBorderColor = Color(0xFFEF4444),
                        focusedContainerColor = inputBg,
                        unfocusedContainerColor = inputBg,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = Color(0xFF4285F4),
                        focusedLabelColor = Color(0xFF4285F4),
                        unfocusedLabelColor = textSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Input: Full Name (shown during account creation or optional)
                if (isRegisterMode) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            if (errorMessage.isNotBlank()) errorMessage = ""
                        },
                        label = { Text("Full Name", color = textSecondary) },
                        placeholder = { Text("e.g. Alex Johnson", color = textSecondary.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF4285F4)) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = borderCol,
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = Color(0xFF4285F4),
                            focusedLabelColor = Color(0xFF4285F4),
                            unfocusedLabelColor = textSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Input: Google Password / PIN
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        if (errorMessage.isNotBlank()) errorMessage = ""
                    },
                    label = { Text("Password / Security Key", color = textSecondary) },
                    placeholder = { Text("At least 6 characters", color = textSecondary.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF4285F4)) },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.testTag("toggle_google_password_visibility")
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = textSecondary
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = isRegisterMode && passwordInput.isNotBlank() && passwordValidation?.isValid == false,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_password_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4285F4),
                        unfocusedBorderColor = borderCol,
                        errorBorderColor = Color(0xFFEF4444),
                        focusedContainerColor = inputBg,
                        unfocusedContainerColor = inputBg,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = Color(0xFF4285F4),
                        focusedLabelColor = Color(0xFF4285F4),
                        unfocusedLabelColor = textSecondary
                    ),
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
                            modifier = Modifier.testTag("google_forgot_password_btn")
                        ) {
                            Text(
                                text = "Forgot Password?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4285F4)
                            )
                        }
                    }
                }

                // Password strength indicator in register mode
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
                    }
                }

                // Driver registration extra fields
                if (userRole == "DRIVER" && isRegisterMode) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Driver Fleet Information",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("CAR", "TAXI", "TRICYCLE", "VAN").forEach { type ->
                            val selected = vehicleType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color(0xFF4285F4) else inputBg)
                                    .border(1.dp, if (selected) Color(0xFF4285F4) else borderCol, RoundedCornerShape(8.dp))
                                    .clickable { vehicleType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) PureWhite else textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = vehiclePlate,
                        onValueChange = { vehiclePlate = it },
                        label = { Text("Vehicle License Plate", color = textSecondary) },
                        placeholder = { Text("e.g. BJL 8844 X", color = textSecondary.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color(0xFF4285F4)) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_plate_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = borderCol,
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = Color(0xFF4285F4),
                            focusedLabelColor = Color(0xFF4285F4),
                            unfocusedLabelColor = textSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = licenseNum,
                        onValueChange = { licenseNum = it },
                        label = { Text("Driver License #", color = textSecondary) },
                        placeholder = { Text("e.g. GAM-DL-9082", color = textSecondary.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF4285F4)) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_license_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = borderCol,
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = Color(0xFF4285F4),
                            focusedLabelColor = Color(0xFF4285F4),
                            unfocusedLabelColor = textSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Error Message Display
                AnimatedVisibility(visible = errorMessage.isNotBlank()) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage,
                            color = ErrorRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Submit Action Button
                Button(
                    onClick = {
                        val cleanEmail = emailInput.trim()
                        val cleanPass = passwordInput.trim()
                        val cleanName = nameInput.trim()

                        if (isRegisterMode) {
                            val validation = AuthValidator.validateSignUp(
                                email = cleanEmail,
                                password = cleanPass,
                                name = cleanName,
                                isDriver = (userRole == "DRIVER"),
                                vehiclePlate = vehiclePlate.trim()
                            )

                            if (!validation.isValid) {
                                val err = validation.errorMessage ?: "Please verify your input fields."
                                errorMessage = err
                                Toast.makeText(context, "⚠️ $err", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        } else {
                            val emailCheck = AuthValidator.validateEmail(cleanEmail)
                            if (!emailCheck.isValid) {
                                val err = emailCheck.errorMessage ?: "Please enter a valid Google email address."
                                errorMessage = err
                                Toast.makeText(context, "⚠️ $err", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (cleanPass.isBlank()) {
                                val err = "Please enter your password."
                                errorMessage = err
                                Toast.makeText(context, "⚠️ $err", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }

                        errorMessage = ""
                        isLoading = true
                        Toast.makeText(context, if (isRegisterMode) "Creating account with Firebase..." else "Signing in...", Toast.LENGTH_SHORT).show()
                        onAuthenticate(
                            cleanEmail,
                            cleanName,
                            cleanPass,
                            vehicleType,
                            vehiclePlate,
                            licenseNum,
                            isRegisterMode
                        ) { err ->
                            isLoading = false
                            errorMessage = err
                            Toast.makeText(context, "⚠️ $err", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_auth_submit_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PureWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Authenticating with Firebase...", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                    } else {
                        Text(
                            text = if (isRegisterMode) "Create Account with Google" else "Authenticate with Google",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { if (!isLoading) safeDismiss() },
                    modifier = Modifier.testTag("google_auth_cancel_btn")
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary
                    )
                }
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


