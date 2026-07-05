package com.nit.voicelibrarymvp

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.nit.voicelibrarymvp.ui.theme.ArividamTheme
import java.util.Locale

class RegisterActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var tts: TextToSpeech? = null

    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    private var isDarkMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        isDarkMode = getSharedPreferences("user_prefs", MODE_PRIVATE).getBoolean("isDarkMode", false)
        initTTS()

        val prefillName = intent.getStringExtra("PREFILL_NAME") ?: ""
        val prefillEmail = intent.getStringExtra("PREFILL_EMAIL") ?: ""
        val isGoogleUser = intent.getBooleanExtra("IS_GOOGLE", false)
        val isExistingUser = auth.currentUser != null

        setContent {
            ArividamTheme(darkTheme = isDarkMode) {
                RegisterScreen(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    prefillName = prefillName,
                    prefillEmail = prefillEmail,
                    isExistingUser = isExistingUser && !isGoogleUser,
                    isDarkMode = isDarkMode,
                    onRegister = { name, email, phone, libraryId, role, pass, confirmPass ->
                        if (pass != confirmPass) {
                            errorMessage = "Passwords do not match"
                            return@RegisterScreen
                        }
                        
                        val cleanEmail = email.trim().lowercase()
                        if (!validateInputs(name, cleanEmail, phone, libraryId, pass, isGoogle = false)) {
                            return@RegisterScreen
                        }

                        isLoading = true
                        errorMessage = ""
                        
                        // First, try creating the Firebase Auth account.
                        // This allows the app to be 'authenticated' so it can check Firestore rules.
                        auth.createUserWithEmailAndPassword(cleanEmail, pass)
                            .addOnSuccessListener {
                                val formattedPhone = "+91$phone"
                                // Now check if this phone number exists for another user
                                db.collection("users")
                                    .whereEqualTo("mobile_phone", formattedPhone)
                                    .get()
                                    .addOnSuccessListener { snapshot ->
                                        // It's unique if nobody has it, or if ONLY the current user has it
                                        val isUnique = snapshot.isEmpty || (snapshot.size() == 1 && snapshot.documents[0].id == auth.currentUser?.uid)
                                        
                                        if (isUnique) {
                                            saveProfile(name, cleanEmail, phone, libraryId, role, pass)
                                        } else {
                                            // Cleanup the auth account since phone is duplicate
                                            auth.currentUser?.delete()
                                            isLoading = false
                                            errorMessage = "This phone number is already registered with another account."
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        // If Firestore check fails (network error), still try to save profile
                                        Log.e("Register", "Firestore uniqueness check failed", e)
                                        saveProfile(name, cleanEmail, phone, libraryId, role, pass)
                                    }
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                // Give a specific reason instead of generic "Validation failed"
                                errorMessage = when {
                                    e.message?.contains("email address is already in use") == true -> "This email is already registered."
                                    e.message?.contains("badly formatted") == true -> "Invalid email format."
                                    e.message?.contains("at least 6 characters") == true -> "Password must be at least 6 characters."
                                    else -> e.localizedMessage ?: "Registration failed. Please try again."
                                }
                            }
                    },
                    onBackToLogin = { 
                        auth.signOut()
                        finish() 
                    },
                    onThemeToggle = { enabled ->
                        isDarkMode = enabled
                        getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
                            .putBoolean("isDarkMode", enabled)
                            .apply()
                    }
                )
            }
        }
    }

    private fun registerWithEmail(
        name: String, email: String, phone: String,
        libraryId: String, role: String, pass: String
    ) {
        val cleanEmail = email.trim().lowercase()
        if (!validateInputs(name, cleanEmail, phone, libraryId, pass, isGoogle = false)) {
            isLoading = false
            return
        }

        auth.createUserWithEmailAndPassword(cleanEmail, pass)
            .addOnSuccessListener {
                saveProfile(name, cleanEmail, phone, libraryId, role, pass)
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = e.message ?: "Registration failed."
            }
    }

    private fun saveProfile(
        name: String, email: String, phone: String,
        libraryId: String, role: String, password: String = ""
    ) {
        val cleanEmail = email.trim().lowercase()
        if (!validateInputs(name, cleanEmail, phone, libraryId, password, isGoogle = false)) {
            isLoading = false
            return
        }

        val user = auth.currentUser
        if (user != null && password.isNotEmpty()) {
            val credential = EmailAuthProvider.getCredential(cleanEmail, password)
            
            // Check if user already has password provider
            val hasPassword = user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
            
            if (hasPassword) {
                user.updatePassword(password).addOnCompleteListener { 
                    saveFirestoreData(user.uid, name, cleanEmail, phone, libraryId, role, password)
                }
            } else {
                user.linkWithCredential(credential)
                    .addOnCompleteListener { 
                        // Even if linking fails, try to save firestore data
                        saveFirestoreData(user.uid, name, cleanEmail, phone, libraryId, role, password)
                    }
            }
        } else {
            saveFirestoreData(user?.uid ?: "", name, cleanEmail, phone, libraryId, role, password)
        }
    }

    private fun saveFirestoreData(
        uid: String, name: String, email: String, phone: String,
        libraryId: String, role: String, password: String
    ) {
        val userMap = hashMapOf(
            "user_id" to uid,
            "name" to name,
            "email" to email,
            "mobile_phone" to "+91$phone",
            "password" to password,
            "library_id" to libraryId,
            "role" to role,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users").document(uid).set(userMap)
            .addOnSuccessListener {
                getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
                    .putString("libraryId", libraryId)
                    .putString("userRole", role)
                    .apply()

                isLoading = false
                speak("രജിസ്ട്രേഷൻ വിജയകരമായി")
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("USER_ROLE", role)
                    putExtra("USER_NAME", name)
                    putExtra("LIBRARY_ID", libraryId)
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                isLoading = false
                errorMessage = "Failed to save profile."
            }
    }

    private fun validateInputs(
        name: String, email: String, phone: String,
        libraryId: String, pass: String, isGoogle: Boolean
    ): Boolean {
        if (name.isBlank()) {
            errorMessage = "Please enter your full name"
            return false
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage = "Please enter a valid email address"
            return false
        }
        if (phone.length != 10) {
            errorMessage = "Phone number must be exactly 10 digits"
            return false
        }
        if (libraryId.isBlank()) {
            errorMessage = "Please enter your Library ID"
            return false
        }
        if (!isGoogle && !ValidationUtils.isValidPassword(pass)) {
            errorMessage = "Password does not meet all requirements"
            return false
        }
        return true
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ml", "IN")
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun RegisterScreen(
    isLoading: Boolean,
    errorMessage: String,
    prefillName: String,
    prefillEmail: String,
    isExistingUser: Boolean,
    isDarkMode: Boolean,
    onRegister: (String, String, String, String, String, String, String) -> Unit,
    onBackToLogin: () -> Unit,
    onThemeToggle: (Boolean) -> Unit
) {
    var fullName by remember { mutableStateOf(prefillName) }
    var email by remember { mutableStateOf(prefillEmail) }
    var phone by remember { mutableStateOf("") }
    var libraryId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("USER") }
    var expanded by remember { mutableStateOf(false) }

    val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
    val bgGradient = Brush.verticalGradient(
        colors = if (isDarkMode) 
            listOf(Color(0xFF12100E), Color(0xFF1E1916)) 
            else listOf(Color(0xFFFFFFFF), Color(0xFFF7F0E9))
    )
    val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
    val textFieldContainer = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFF3EAE2)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        IconButton(
            onClick = { onThemeToggle(!isDarkMode) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Toggle Dark Mode",
                tint = mainColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isExistingUser) "Complete Profile" else "Create Account",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = mainColor,
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = mainColor)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    RegisterTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = "Full Name",
                        enabled = !isLoading,
                        isDarkMode = isDarkMode
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    RegisterTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address",
                        enabled = !isExistingUser && !isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isDarkMode = isDarkMode
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    RegisterTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 10) phone = it },
                        label = "Phone Number",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        enabled = !isLoading,
                        isDarkMode = isDarkMode
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Select Role", 
                            fontSize = 12.sp, 
                            color = if (isDarkMode) Color.LightGray else Color.Gray, 
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = selectedRole,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = textFieldContainer,
                                    unfocusedContainerColor = textFieldContainer,
                                    disabledContainerColor = textFieldContainer,
                                    focusedTextColor = mainColor,
                                    unfocusedTextColor = mainColor,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { expanded = true }) {
                                        Icon(
                                            Icons.Default.ArrowDropDown, 
                                            contentDescription = null,
                                            tint = mainColor
                                        )
                                    }
                                },
                                enabled = !isLoading
                            )
                            DropdownMenu(
                                expanded = expanded, 
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(cardBg)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("USER", color = mainColor) },
                                    onClick = { selectedRole = "USER"; expanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("ADMIN", color = mainColor) },
                                    onClick = { selectedRole = "ADMIN"; expanded = false }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    RegisterTextField(
                        value = libraryId,
                        onValueChange = { libraryId = it },
                        label = if (selectedRole == "ADMIN") "Admin ID (Library ID)" else "User ID (Library ID)",
                        enabled = !isLoading,
                        isDarkMode = isDarkMode
                    )
                    
                    if (!isExistingUser) {
                        Spacer(modifier = Modifier.height(16.dp))
                        RegisterTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onPasswordToggle = { passwordVisible = !passwordVisible },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = !isLoading,
                            isDarkMode = isDarkMode
                        )
                        
                        // Password Requirements
                        if (password.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val requirements = ValidationUtils.getPasswordRequirements(password)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                requirements.forEach { req ->
                                    PasswordRequirementItem(req = req, isDarkMode = isDarkMode)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        RegisterTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = "Confirm Password",
                            isPassword = true,
                            passwordVisible = confirmPasswordVisible,
                            onPasswordToggle = { confirmPasswordVisible = !confirmPasswordVisible },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = !isLoading,
                            isDarkMode = isDarkMode
                        )
                    }

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { onRegister(fullName, email, phone, libraryId, selectedRole, password, confirmPassword) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = mainColor),
                        enabled = !isLoading
                    ) {
                        Text(
                            if (isExistingUser) "Complete Profile" else "Register", 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFF452719) else Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = onBackToLogin) {
                        Text("Back to Login", color = if (isDarkMode) Color(0xFF8D6E63) else Color(0xFFB08968), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: () -> Unit = {},
    isDarkMode: Boolean = false
) {
    val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
    val containerColor = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFF3EAE2)

    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label, color = if (isDarkMode) Color.LightGray.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.7f)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            focusedTextColor = mainColor,
            unfocusedTextColor = mainColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = onPasswordToggle) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (isDarkMode) Color.LightGray else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
}

@Composable
fun PasswordRequirementItem(req: ValidationUtils.PasswordRequirement, isDarkMode: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (req.isMet) Icons.Default.Check else Icons.Default.Circle,
            contentDescription = null,
            tint = if (req.isMet) Color(0xFF4CAF50) else (if (isDarkMode) Color.Gray else Color.LightGray),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = req.label,
            fontSize = 12.sp,
            color = if (req.isMet) (if (isDarkMode) Color.LightGray else Color.Gray) else (if (isDarkMode) Color.Gray.copy(alpha = 0.5f) else Color.LightGray),
            fontWeight = if (req.isMet) FontWeight.Medium else FontWeight.Normal
        )
    }
}
