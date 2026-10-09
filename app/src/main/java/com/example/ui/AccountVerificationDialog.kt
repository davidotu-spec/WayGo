package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@Composable
fun AccountVerificationDialog(
    userEmail: String,
    userRole: String,
    generatedCode: String = "",
    verificationMessage: String,
    isDark: Boolean = false,
    onVerifyCode: (String) -> Boolean,
    onResendEmail: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var inputCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    val dialogBg = if (isDark) Color(0xFF1E293B) else PureWhite
    val textPrimary = if (isDark) PureWhite else BrandBlueDark
    val textSecondary = if (isDark) Color(0xFF94A3B8) else NeutralGray
    val containerBg = if (isDark) Color(0xFF0F172A) else BrandBlueLight

    Dialog(
        onDismissRequest = safeDismiss,
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
                .testTag("account_verification_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = dialogBg,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Email / Security Badge Icon
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(BrandBluePrimary, Color(0xFF4285F4)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = "Email Verification Icon",
                        tint = PureWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Verify Your Email",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "We have sent a 6-digit security code to:",
                    fontSize = 13.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = containerBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = userEmail.ifBlank { "your email address" },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandBluePrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Information Card explaining real email delivery
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, BrandBluePrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = BrandBluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Please check your inbox (and spam/junk folder) and enter the 6-digit security code below.",
                            fontSize = 11.5.sp,
                            color = textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 6-Digit Code Input
                OutlinedTextField(
                    value = inputCode,
                    onValueChange = {
                        if (it.length <= 6) {
                            inputCode = it.filter { char -> char.isDigit() }
                            errorMessage = ""
                            if (inputCode.length == 6) {
                                isVerifying = true
                                val success = onVerifyCode(inputCode)
                                if (!success) {
                                    errorMessage = "Incorrect code. Please check the code sent to your email."
                                    isVerifying = false
                                } else {
                                    Toast.makeText(context, "Account verified successfully!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    label = { Text("6-Digit Security Code") },
                    placeholder = { Text("• • • • • •") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verification_code_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBluePrimary,
                        unfocusedBorderColor = NeutralGray,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary
                    )
                )

                AnimatedVisibility(visible = errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                AnimatedVisibility(visible = successMessage.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = SuccessGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = successMessage,
                            color = SuccessGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Verify Button
                Button(
                    onClick = {
                        keyboardController?.hide()
                        if (inputCode.length < 4) {
                            errorMessage = "Please enter the 6-digit verification code received in your email."
                            return@Button
                        }
                        isVerifying = true
                        val success = onVerifyCode(inputCode)
                        if (!success) {
                            errorMessage = "Incorrect code. Please verify the code sent to your email and try again."
                            isVerifying = false
                        } else {
                            Toast.makeText(context, "Account verified successfully!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_verification_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandBluePrimary,
                        contentColor = PureWhite
                    ),
                    enabled = !isVerifying && inputCode.isNotEmpty()
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PureWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Verify & Continue", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Direct Open Email App Action
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                addCategory(android.content.Intent.CATEGORY_APP_EMAIL)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Please open your email application to view your inbox.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("open_email_app_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandBluePrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Open Mail",
                        modifier = Modifier.size(16.dp),
                        tint = BrandBluePrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Email App", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            isResending = true
                            onResendEmail()
                            inputCode = ""
                            errorMessage = ""
                            successMessage = "New verification code dispatched to your email."
                            Toast.makeText(context, "Verification code resent to $userEmail", Toast.LENGTH_SHORT).show()
                            isResending = false
                        },
                        modifier = Modifier.testTag("resend_verification_btn"),
                        enabled = !isResending
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Resend",
                            modifier = Modifier.size(16.dp),
                            tint = BrandBluePrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resend Email", fontSize = 12.5.sp, color = BrandBluePrimary, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = safeDismiss,
                        modifier = Modifier.testTag("cancel_verification_btn")
                    ) {
                        Text("Cancel", fontSize = 12.5.sp, color = textSecondary)
                    }
                }
            }
        }
    }
}
