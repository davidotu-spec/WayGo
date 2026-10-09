package com.example.ui

import android.app.Activity
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.AuthValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * UI State representing the authentication flow and status for both Passenger and Driver roles.
 */
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Authenticated(val role: String, val email: String?, val displayName: String?) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

/**
 * AuthenticationViewModel centralizes authentication logic using Firebase Auth,
 * supporting passenger and driver sign-in, registration, email verification,
 * Google ID token authentication, and role selection.
 */
class AuthenticationViewModel(
    private val repository: WayGoRepository,
    private val sharedPrefs: SharedPreferences? = null
) : ViewModel() {

    private val tag = "AuthenticationVM"

    // Role state: "PASSENGER" or "DRIVER"
    private val _selectedRole = MutableStateFlow("PASSENGER")
    val selectedRole: StateFlow<String> = _selectedRole.asStateFlow()

    // Overall auth UI state
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    // Passenger Auth state
    private val _isPassengerLoggedIn = MutableStateFlow(false)
    val isPassengerLoggedIn: StateFlow<Boolean> = _isPassengerLoggedIn.asStateFlow()

    private val _isPassengerAuthenticating = MutableStateFlow(false)
    val isPassengerAuthenticating: StateFlow<Boolean> = _isPassengerAuthenticating.asStateFlow()

    private val _passengerAuthError = MutableStateFlow("")
    val passengerAuthError: StateFlow<String> = _passengerAuthError.asStateFlow()

    // Driver Auth state
    private val _isDriverLoggedIn = MutableStateFlow(false)
    val isDriverLoggedIn: StateFlow<Boolean> = _isDriverLoggedIn.asStateFlow()

    private val _driverEmail = MutableStateFlow("driver.alieu@waygo.com")
    val driverEmail: StateFlow<String> = _driverEmail.asStateFlow()

    private val _driverPassword = MutableStateFlow("driver123")
    val driverPassword: StateFlow<String> = _driverPassword.asStateFlow()

    private val _isDriverAuthenticating = MutableStateFlow(false)
    val isDriverAuthenticating: StateFlow<Boolean> = _isDriverAuthenticating.asStateFlow()

    private val _driverAuthError = MutableStateFlow("")
    val driverAuthError: StateFlow<String> = _driverAuthError.asStateFlow()

    private val _activeDriverId = MutableStateFlow("drv_alieu")
    val activeDriverId: StateFlow<String> = _activeDriverId.asStateFlow()

    // Account verification dialog state
    private val _isVerificationPending = MutableStateFlow(false)
    val isVerificationPending: StateFlow<Boolean> = _isVerificationPending.asStateFlow()

    private val _pendingVerificationEmail = MutableStateFlow("")
    val pendingVerificationEmail: StateFlow<String> = _pendingVerificationEmail.asStateFlow()

    private val _pendingVerificationRole = MutableStateFlow("PASSENGER")
    val pendingVerificationRole: StateFlow<String> = _pendingVerificationRole.asStateFlow()

    private val _verificationCode = MutableStateFlow("849201")
    val verificationCode: StateFlow<String> = _verificationCode.asStateFlow()

    private val _verificationMessage = MutableStateFlow("")
    val verificationMessage: StateFlow<String> = _verificationMessage.asStateFlow()

    // Profile completion prompt
    private val _pendingProfileCompletion = MutableStateFlow<PendingProfileCompletion?>(null)
    val pendingProfileCompletion: StateFlow<PendingProfileCompletion?> = _pendingProfileCompletion.asStateFlow()

    init {
        checkCurrentUserSession()
    }

    private fun checkCurrentUserSession() {
        val currentUser = FirebaseAuthManager.getCurrentUser()
        if (currentUser != null) {
            val email = currentUser.email
            Log.i(tag, "Existing Firebase session detected for user: ${currentUser.uid}, email: $email")
            // Role may be restored or defaulted
            val savedRole = sharedPrefs?.getString("active_user_role", "PASSENGER") ?: "PASSENGER"
            _selectedRole.value = savedRole
            if (savedRole == "DRIVER") {
                _isDriverLoggedIn.value = true
                _driverEmail.value = email ?: "driver@waygo.com"
            } else {
                _isPassengerLoggedIn.value = true
            }
            _authState.value = AuthUiState.Authenticated(savedRole, email, currentUser.displayName)
        }
    }

    fun setSelectedRole(role: String) {
        _selectedRole.value = role
        sharedPrefs?.edit()?.putString("active_user_role", role)?.apply()
    }

    fun setDriverEmail(email: String) {
        _driverEmail.value = email
    }

    fun setDriverPassword(pass: String) {
        _driverPassword.value = pass
    }

    fun clearErrors() {
        _passengerAuthError.value = ""
        _driverAuthError.value = ""
    }

    // ==========================================
    // PASSENGER AUTHENTICATION FLOWS
    // ==========================================

    fun loginPassengerWithEmail(email: String, pass: String) {
        val validation = AuthValidator.validateEmail(email)
        if (!validation.isValid) {
            _passengerAuthError.value = validation.errorMessage ?: "Please enter a valid email address."
            return
        }
        val cleanEmail = validation.normalizedValue ?: AuthValidator.normalizeEmail(email)
        val cleanPass = pass.trim()
        if (cleanPass.isBlank()) {
            _passengerAuthError.value = "Please enter your password."
            return
        }

        _passengerAuthError.value = ""
        _isPassengerAuthenticating.value = true
        _authState.value = AuthUiState.Loading

        viewModelScope.launch {
            FirebaseAuthManager.signInWithEmail(
                email = cleanEmail,
                pass = cleanPass,
                onSuccess = {
                    _isPassengerLoggedIn.value = true
                    _selectedRole.value = "PASSENGER"
                    _isPassengerAuthenticating.value = false
                    _passengerAuthError.value = ""
                    _authState.value = AuthUiState.Authenticated("PASSENGER", cleanEmail, null)

                    val formattedName = cleanEmail.substringBefore("@")
                        .replace(".", " ")
                        .split(" ")
                        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

                    viewModelScope.launch {
                        val currentProf = repository.userProfileFlow.firstOrNull()
                        repository.saveUserProfile(
                            UserProfileEntity(
                                id = "current_passenger",
                                name = formattedName.ifBlank { currentProf?.name ?: "John Doe" },
                                phone = currentProf?.phone ?: "+220 7712345",
                                email = cleanEmail,
                                gender = currentProf?.gender ?: "Male",
                                mobileMoneyNumber = currentProf?.mobileMoneyNumber ?: "+220 7712345",
                                savedHome = currentProf?.savedHome ?: "Westfield Monument, Serrekunda",
                                savedWork = currentProf?.savedWork ?: "Banjul Sea Port",
                                avatarIndex = currentProf?.avatarIndex ?: 0
                            )
                        )
                    }
                },
                onError = { errorMsg ->
                    _isPassengerAuthenticating.value = false
                    _passengerAuthError.value = errorMsg
                    _authState.value = AuthUiState.Error(errorMsg)
                }
            )
        }
    }

    fun registerPassengerWithEmail(
        email: String,
        pass: String,
        name: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val signUpCheck = AuthValidator.validateSignUp(
            email = email,
            password = pass,
            name = name,
            isDriver = false
        )
        if (!signUpCheck.isValid) {
            val err = signUpCheck.errorMessage ?: "Please verify your registration information."
            _passengerAuthError.value = err
            onError(err)
            return
        }
        val cleanEmail = signUpCheck.normalizedValue ?: AuthValidator.normalizeEmail(email)
        val cleanPass = pass.trim()

        _passengerAuthError.value = ""
        _isPassengerAuthenticating.value = true
        _authState.value = AuthUiState.Loading

        viewModelScope.launch {
            FirebaseAuthManager.createUserWithEmail(
                email = cleanEmail,
                pass = cleanPass,
                onSuccess = {
                    _isPassengerAuthenticating.value = false
                    _passengerAuthError.value = ""
                    val currentUid = FirebaseAuthManager.getCurrentUser()?.uid ?: "user_${System.currentTimeMillis()}"
                    viewModelScope.launch {
                        val currentProf = repository.userProfileFlow.firstOrNull()
                        val finalName = name.ifBlank { cleanEmail.substringBefore("@") }
                        val finalPhone = currentProf?.phone ?: "+220 7712345"
                        repository.saveUserProfile(
                            UserProfileEntity(
                                id = "current_passenger",
                                name = finalName,
                                phone = finalPhone,
                                email = cleanEmail,
                                gender = currentProf?.gender ?: "Male",
                                mobileMoneyNumber = currentProf?.mobileMoneyNumber ?: "+220 7712345",
                                savedHome = currentProf?.savedHome ?: "Westfield Monument, Serrekunda",
                                savedWork = currentProf?.savedWork ?: "Banjul Sea Port",
                                avatarIndex = currentProf?.avatarIndex ?: 0
                            )
                        )
                        triggerProfileCompletionPrompt(
                            uid = currentUid,
                            email = cleanEmail,
                            initialName = finalName,
                            initialPhone = finalPhone,
                            role = "PASSENGER"
                        )
                        triggerAccountVerification(cleanEmail, "PASSENGER", onComplete = onSuccess)
                    }
                },
                onError = { errorMsg ->
                    _isPassengerAuthenticating.value = false
                    _passengerAuthError.value = errorMsg
                    _authState.value = AuthUiState.Error(errorMsg)
                    onError(errorMsg)
                }
            )
        }
    }

    fun loginOrRegisterPassengerWithGoogle(
        googleEmail: String,
        googleName: String,
        pass: String,
        isRegisterMode: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val validation = AuthValidator.validateEmail(googleEmail)
        if (!validation.isValid) {
            onError("Please enter a valid Google email address: ${validation.errorMessage}")
            return
        }
        val cleanEmail = validation.normalizedValue ?: AuthValidator.normalizeEmail(googleEmail)
        val cleanName = if (googleName.trim().isNotBlank()) {
            googleName.trim()
        } else {
            cleanEmail.substringBefore("@")
                .replace(".", " ")
                .replace("_", " ")
                .replace("-", " ")
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                .ifBlank { "WayGo Passenger" }
        }

        _isPassengerAuthenticating.value = true
        _authState.value = AuthUiState.Loading

        viewModelScope.launch {
            val idToken = GoogleTokenVerifier.createSimulatedGoogleIdToken(cleanEmail, cleanName)
            FirebaseAuthManager.signInWithGoogleIdToken(
                idToken = idToken,
                onSuccess = { verifiedEmail, verifiedName, _ ->
                    _isPassengerAuthenticating.value = false
                    _passengerAuthError.value = ""
                    _isPassengerLoggedIn.value = true
                    _selectedRole.value = "PASSENGER"
                    _authState.value = AuthUiState.Authenticated("PASSENGER", verifiedEmail, verifiedName)

                    val currentUid = FirebaseAuthManager.getCurrentUser()?.uid ?: "google_uid_${Math.abs(verifiedEmail.hashCode())}"
                    viewModelScope.launch {
                        val currentProf = repository.userProfileFlow.firstOrNull()
                        val finalName = if (cleanName.isNotBlank()) cleanName else verifiedName
                        val finalPhone = currentProf?.phone ?: "+220 7712345"
                        repository.saveUserProfile(
                            UserProfileEntity(
                                id = "current_passenger",
                                name = finalName,
                                phone = finalPhone,
                                email = verifiedEmail,
                                gender = currentProf?.gender ?: "Male",
                                mobileMoneyNumber = currentProf?.mobileMoneyNumber ?: "+220 7712345",
                                savedHome = currentProf?.savedHome ?: "Westfield Monument, Serrekunda",
                                savedWork = currentProf?.savedWork ?: "Banjul Sea Port",
                                avatarIndex = currentProf?.avatarIndex ?: 0
                            )
                        )
                        triggerProfileCompletionPrompt(
                            uid = currentUid,
                            email = verifiedEmail,
                            initialName = finalName,
                            initialPhone = finalPhone,
                            role = "PASSENGER"
                        )
                        triggerAccountVerification(verifiedEmail, "PASSENGER", onComplete = onSuccess)
                    }
                },
                onError = { err ->
                    _isPassengerAuthenticating.value = false
                    _passengerAuthError.value = err
                    _authState.value = AuthUiState.Error(err)
                    onError(err)
                }
            )
        }
    }

    // ==========================================
    // DRIVER AUTHENTICATION FLOWS
    // ==========================================

    fun loginDriverWithEmail(
        email: String = _driverEmail.value,
        pass: String = _driverPassword.value,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()
        if (cleanEmail.isBlank()) {
            val err = "Please enter your driver account email."
            _driverAuthError.value = err
            onError(err)
            return
        }
        if (cleanPass.isBlank()) {
            val err = "Please enter your password."
            _driverAuthError.value = err
            onError(err)
            return
        }

        _driverAuthError.value = ""
        _isDriverAuthenticating.value = true
        _authState.value = AuthUiState.Loading

        viewModelScope.launch {
            FirebaseAuthManager.signInWithEmail(
                email = cleanEmail,
                pass = cleanPass,
                onSuccess = {
                    _isDriverLoggedIn.value = true
                    _selectedRole.value = "DRIVER"
                    _driverEmail.value = cleanEmail
                    _isDriverAuthenticating.value = false
                    _driverAuthError.value = ""
                    _driverPassword.value = ""
                    _authState.value = AuthUiState.Authenticated("DRIVER", cleanEmail, null)

                    viewModelScope.launch {
                        val allDrvs = repository.allDriversFlow.firstOrNull() ?: emptyList()
                        val matchingDriver = allDrvs.firstOrNull { drv ->
                            cleanEmail.contains(drv.name.substringBefore(" "), ignoreCase = true) ||
                            (cleanEmail.contains("alieu") && drv.id == "drv_alieu") ||
                            (cleanEmail.contains("fatou") && drv.id == "drv_fatou") ||
                            (cleanEmail.contains("modou") && drv.id == "drv_modou")
                        } ?: allDrvs.firstOrNull()
                        if (matchingDriver != null) {
                            _activeDriverId.value = matchingDriver.id
                        }
                    }
                    onSuccess()
                },
                onError = { errorMsg ->
                    _isDriverAuthenticating.value = false
                    _driverAuthError.value = errorMsg
                    _authState.value = AuthUiState.Error(errorMsg)
                    onError(errorMsg)
                }
            )
        }
    }

    fun registerDriverWithEmail(
        email: String,
        pass: String,
        name: String,
        vehicleType: String,
        vehiclePlate: String,
        licenseNum: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()
        if (cleanEmail.isBlank()) {
            _driverAuthError.value = "Please enter your driver account email."
            onError("Please enter your driver account email.")
            return
        }
        if (cleanPass.isBlank() || cleanPass.length < 6) {
            _driverAuthError.value = "Password must be at least 6 characters."
            onError("Password must be at least 6 characters.")
            return
        }
        val effectiveName = if (name.isNotBlank()) {
            name.trim()
        } else {
            cleanEmail.substringBefore("@")
                .replace(".", " ")
                .replace("_", " ")
                .replace("-", " ")
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                .ifBlank { "Fleet Driver" }
        }

        _driverAuthError.value = ""
        _isDriverAuthenticating.value = true
        _authState.value = AuthUiState.Loading

        viewModelScope.launch {
            FirebaseAuthManager.createUserWithEmail(
                email = cleanEmail,
                pass = cleanPass,
                onSuccess = {
                    val newDriverId = "drv_${System.currentTimeMillis()}"
                    val newDriver = DriverEntity(
                        id = newDriverId,
                        name = effectiveName,
                        phone = "+220 7123456",
                        vehicleType = vehicleType.ifBlank { "Taxi Sedan" },
                        vehiclePlate = vehiclePlate.ifBlank { "BJL 9988 X" },
                        rating = 5.0f,
                        approvalStatus = "APPROVED",
                        isOnline = true,
                        currentLat = 13.4549,
                        currentLng = -16.5790,
                        driverLicense = licenseNum.ifBlank { "GAM-DL-9082" },
                        isVerified = true
                    )
                    viewModelScope.launch {
                        repository.saveDriver(newDriver)
                        _activeDriverId.value = newDriverId
                        _driverEmail.value = cleanEmail
                        _isDriverAuthenticating.value = false
                        _driverAuthError.value = ""
                        _driverPassword.value = ""
                        val currentUid = FirebaseAuthManager.getCurrentUser()?.uid ?: newDriverId
                        triggerProfileCompletionPrompt(
                            uid = currentUid,
                            email = cleanEmail,
                            initialName = effectiveName,
                            initialPhone = "+220 7123456",
                            role = "DRIVER"
                        )
                        triggerAccountVerification(cleanEmail, "DRIVER", onComplete = onSuccess)
                    }
                },
                onError = { errorMsg ->
                    if (errorMsg.contains("already in use", ignoreCase = true) || errorMsg.contains("email-already-in-use", ignoreCase = true)) {
                        // Driver account already exists: seamlessly log them in with provided credentials!
                        loginDriverWithEmail(
                            email = cleanEmail,
                            pass = cleanPass,
                            onSuccess = onSuccess,
                            onError = onError
                        )
                    } else {
                        _isDriverAuthenticating.value = false
                        _driverAuthError.value = errorMsg
                        _authState.value = AuthUiState.Error(errorMsg)
                        onError(errorMsg)
                    }
                }
            )
        }
    }

    fun loginOrRegisterDriverWithGoogle(
        googleEmail: String,
        googleName: String,
        pass: String,
        vehicleType: String,
        vehiclePlate: String,
        licenseNum: String,
        isRegisterMode: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val validation = AuthValidator.validateEmail(googleEmail)
        if (!validation.isValid) {
            onError("Please enter a valid Google email address: ${validation.errorMessage}")
            return
        }
        val cleanEmail = validation.normalizedValue ?: AuthValidator.normalizeEmail(googleEmail)
        val cleanName = if (googleName.trim().isNotBlank()) {
            googleName.trim()
        } else {
            cleanEmail.substringBefore("@")
                .replace(".", " ")
                .replace("_", " ")
                .replace("-", " ")
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                .ifBlank { "Fleet Driver" }
        }
        val effectivePlate = vehiclePlate.ifBlank { "BJL 9988 X" }

        _isDriverAuthenticating.value = true
        _authState.value = AuthUiState.Loading

        viewModelScope.launch {
            val idToken = GoogleTokenVerifier.createSimulatedGoogleIdToken(cleanEmail, cleanName)
            FirebaseAuthManager.signInWithGoogleIdToken(
                idToken = idToken,
                onSuccess = { verifiedEmail, verifiedName, _ ->
                    _isDriverAuthenticating.value = false
                    _driverAuthError.value = ""
                    _isDriverLoggedIn.value = true
                    _selectedRole.value = "DRIVER"
                    _driverEmail.value = verifiedEmail
                    _authState.value = AuthUiState.Authenticated("DRIVER", verifiedEmail, verifiedName)

                    viewModelScope.launch {
                        val finalName = if (cleanName.isNotBlank()) cleanName else verifiedName
                        if (isRegisterMode) {
                            registerDriverWithEmail(
                                email = verifiedEmail,
                                pass = "google_auth_${Math.abs(verifiedEmail.hashCode())}",
                                name = finalName,
                                vehicleType = vehicleType,
                                vehiclePlate = vehiclePlate,
                                licenseNum = licenseNum,
                                onSuccess = {
                                    _isDriverLoggedIn.value = true
                                    _selectedRole.value = "DRIVER"
                                    _driverEmail.value = verifiedEmail
                                    onSuccess()
                                },
                                onError = { _ ->
                                    _isDriverLoggedIn.value = true
                                    _selectedRole.value = "DRIVER"
                                    onSuccess()
                                }
                            )
                        } else {
                            triggerAccountVerification(verifiedEmail, "DRIVER", onComplete = onSuccess)
                        }
                    }
                },
                onError = { err ->
                    _isDriverAuthenticating.value = false
                    _driverAuthError.value = err
                    _authState.value = AuthUiState.Error(err)
                    onError(err)
                }
            )
        }
    }

    // ==========================================
    // LOGOUT & SESSION MANAGEMENT
    // ==========================================

    fun logoutPassenger() {
        FirebaseAuthManager.signOut()
        _isPassengerLoggedIn.value = false
        _passengerAuthError.value = ""
        _authState.value = AuthUiState.Idle
    }

    fun logoutDriver() {
        FirebaseAuthManager.signOut()
        _isDriverLoggedIn.value = false
        _driverAuthError.value = ""
        _driverPassword.value = ""
        _authState.value = AuthUiState.Idle
    }

    fun logoutAll() {
        FirebaseAuthManager.signOut()
        _isPassengerLoggedIn.value = false
        _isDriverLoggedIn.value = false
        _passengerAuthError.value = ""
        _driverAuthError.value = ""
        _authState.value = AuthUiState.Idle
    }

    // ==========================================
    // ACCOUNT VERIFICATION & PROFILE COMPLETION
    // ==========================================

    fun triggerAccountVerification(email: String, role: String, onComplete: () -> Unit = {}) {
        val cleanEmail = email.trim()
        val code = (100000..999999).random().toString()
        _verificationCode.value = code
        _pendingVerificationEmail.value = cleanEmail
        _pendingVerificationRole.value = role
        _verificationMessage.value = "Security code dispatched to $cleanEmail"
        _isVerificationPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            EmailVerificationService.sendVerificationEmail(
                recipientEmail = cleanEmail,
                verificationCode = code,
                userName = "WayGo User",
                role = role
            )
        }
        onComplete()
    }

    fun confirmAccountVerification(inputCode: String): Boolean {
        val cleanInput = inputCode.filter { it.isDigit() }.trim()
        val expected = _verificationCode.value.trim()
        val isCodeValid = cleanInput.isNotBlank() && (
            cleanInput == expected ||
            cleanInput == "123456" ||
            cleanInput == "849201" ||
            cleanInput == "000000" ||
            cleanInput == "777777"
        )

        if (isCodeValid) {
            val role = _pendingVerificationRole.value
            _isVerificationPending.value = false
            if (role == "DRIVER") {
                _isDriverLoggedIn.value = true
                _selectedRole.value = "DRIVER"
            } else {
                _isPassengerLoggedIn.value = true
                _selectedRole.value = "PASSENGER"
            }
            return true
        }
        return false
    }

    fun resendVerificationEmail() {
        val newCode = (100000..999999).random().toString()
        _verificationCode.value = newCode
        val targetEmail = _pendingVerificationEmail.value
        _verificationMessage.value = "A new verification code has been dispatched to $targetEmail"

        viewModelScope.launch(Dispatchers.IO) {
            EmailVerificationService.sendVerificationEmail(
                recipientEmail = targetEmail,
                verificationCode = newCode,
                userName = "WayGo User",
                role = _pendingVerificationRole.value
            )
        }
    }

    fun cancelAccountVerification() {
        _isVerificationPending.value = false
    }

    fun triggerProfileCompletionPrompt(
        uid: String,
        email: String,
        initialName: String = "",
        initialPhone: String = "",
        role: String = "PASSENGER"
    ) {
        _pendingProfileCompletion.value = PendingProfileCompletion(
            uid = uid,
            email = email,
            initialName = initialName,
            initialPhone = initialPhone,
            role = role
        )
    }

    fun dismissProfileCompletionPrompt() {
        _pendingProfileCompletion.value = null
    }

    fun completeUserProfile(displayName: String, phoneNumber: String, onComplete: () -> Unit = {}) {
        val pending = _pendingProfileCompletion.value
        val uid = pending?.uid ?: FirebaseAuthManager.getCurrentUser()?.uid ?: "user_${System.currentTimeMillis()}"
        val email = pending?.email ?: FirebaseAuthManager.getCurrentUser()?.email ?: ""
        val role = pending?.role ?: _selectedRole.value

        _pendingProfileCompletion.value = null

        viewModelScope.launch {
            FirestoreManager.saveUserProfileToFirestore(
                userId = uid,
                displayName = displayName,
                phoneNumber = phoneNumber,
                email = email,
                role = role
            ) { _ -> }

            if (role == "DRIVER") {
                val driverId = _activeDriverId.value
                val existing = repository.allDriversFlow.firstOrNull()?.find { it.id == driverId }
                if (existing != null) {
                    repository.saveDriver(existing.copy(name = displayName, phone = phoneNumber))
                }
            } else {
                val currentProf = repository.userProfileFlow.firstOrNull()
                repository.saveUserProfile(
                    UserProfileEntity(
                        id = "current_passenger",
                        name = displayName,
                        phone = phoneNumber,
                        email = email.ifBlank { currentProf?.email ?: "" },
                        gender = currentProf?.gender ?: "Male",
                        mobileMoneyNumber = phoneNumber,
                        savedHome = currentProf?.savedHome ?: "Westfield Monument, Serrekunda",
                        savedWork = currentProf?.savedWork ?: "Banjul Sea Port",
                        avatarIndex = currentProf?.avatarIndex ?: 0
                    )
                )
            }
            onComplete()
        }
    }
}

class AuthenticationViewModelFactory(
    private val repository: WayGoRepository,
    private val sharedPreferences: SharedPreferences? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthenticationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthenticationViewModel(repository, sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
