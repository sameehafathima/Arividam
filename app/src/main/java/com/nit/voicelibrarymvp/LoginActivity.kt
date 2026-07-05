package com.nit.voicelibrarymvp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.nit.voicelibrarymvp.ui.theme.ArividamTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private var tts: TextToSpeech? = null

    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    private var isDarkMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Enable Firestore Persistence for better offline support
        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        db = FirebaseFirestore.getInstance()
        db.firestoreSettings = settings

        isDarkMode = getSharedPreferences("user_prefs", MODE_PRIVATE).getBoolean("isDarkMode", false)
        initTTS()
        initGoogleSignIn()

        // Auto-login check
        if (auth.currentUser != null) {
            val user = auth.currentUser!!
            fetchRoleAndNavigate(user.email ?: "")
            return
        }

        setContent {
            ArividamTheme(darkTheme = isDarkMode) {
                LoginScreen(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    isDarkMode = isDarkMode,
                    onLoginClick = { email, pass, libId -> signInWithEmail(email, pass, libId) },
                    onForgotPassword = { email -> forgotPassword(email) },
                    onGoogleSignIn = { signInWithGoogle() },
                    onRegisterClick = {
                        startActivity(Intent(this, RegisterActivity::class.java))
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

    private fun initGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                isLoading = false
                errorMessage = "Google sign in failed: ${e.message}"
            }
        } else {
            isLoading = false
        }
    }

    private fun signInWithGoogle() {
        isLoading = true
        errorMessage = ""
        // Always sign out first to force account selection
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                val user = auth.currentUser
                fetchRoleAndNavigate(user?.email ?: "")
            }
            .addOnFailureListener {
                isLoading = false
                errorMessage = "Authentication failed."
            }
    }

    private fun signInWithEmail(email: String, pass: String, libId: String) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || pass.isBlank() || libId.isBlank()) {
            errorMessage = "Please enter email, password and Library ID"
            return
        }
        isLoading = true
        errorMessage = ""

        auth.signInWithEmailAndPassword(cleanEmail, pass)
            .addOnSuccessListener {
                fetchRoleAndNavigate(cleanEmail, libId)
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = when {
                    e.message?.contains("malformed") == true || e.message?.contains("credential") == true ->
                        "Invalid credentials. If you usually sign in with Google, please use the button below."
                    e.message?.contains("network") == true -> "Network timeout. Please check your connection."
                    else -> e.message ?: "Login failed. Check credentials."
                }
                Log.e("LoginActivity", "SignIn Error: ${e.message}")
            }
    }

    private fun forgotPassword(email: String) {
        if (email.isBlank()) {
            errorMessage = "Please enter your email above first."
            return
        }
        auth.sendPasswordResetEmail(email.trim().lowercase())
            .addOnSuccessListener {
                errorMessage = "Password reset link sent to $email"
            }
            .addOnFailureListener {
                errorMessage = "Failed to send reset link: ${it.message}"
            }
    }

    private fun fetchRoleAndNavigate(email: String, providedLibId: String = "") {
        if (email.isEmpty()) {
            isLoading = false
            return
        }

        val uid = auth.currentUser?.uid ?: ""
        if (uid.isEmpty()) {
            isLoading = false
            return
        }

        lifecycleScope.launch {
            try {
                // Set a 10-second timeout for the profile load
                val document = kotlinx.coroutines.withTimeoutOrNull(10000) {
                    db.collection("users").document(uid).get().await()
                }

                if (document != null && document.exists()) {
                    processUserData(document, providedLibId, email)
                } else {
                    // Try email fallback if UID direct fetch failed/document not found
                    val snapshot: QuerySnapshot? = kotlinx.coroutines.withTimeoutOrNull(10000) {
                        db.collection("users").whereEqualTo("email", email).get().await()
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        processUserData(snapshot.documents.first(), providedLibId, email)
                    } else {
                        redirectToRegistration(email)
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                isLoading = false
                errorMessage = "Login timed out. Please check your network connection."
            } catch (e: Exception) {
                Log.e("LoginActivity", "Profile load error", e)
                isLoading = false
                errorMessage = "Error loading profile: ${e.message}"
            }
        }
    }

    private fun processUserData(doc: DocumentSnapshot, providedLibId: String, email: String) {
        val role = doc.getString("role") ?: "USER"
        val name = doc.getString("name") ?: doc.getString("fullName") ?: ""
        val libId = doc.getString("library_id") ?: doc.getString("libraryId") ?: ""

        if (providedLibId.isNotEmpty() && providedLibId != libId) {
            isLoading = false
            errorMessage = "Incorrect Library ID for this account."
            auth.signOut()
            return
        }

        if (libId.isEmpty()) {
            val intent = Intent(this, RegisterActivity::class.java).apply {
                putExtra("PREFILL_NAME", name)
                putExtra("PREFILL_EMAIL", email)
                putExtra("IS_GOOGLE", false)
            }
            startActivity(intent)
            finish()
            return
        }

        getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
            .putString("libraryId", libId)
            .putString("userRole", role)
            .commit()

        speak("സ്വാഗതം $name")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("USER_ROLE", role)
            putExtra("USER_NAME", name)
            putExtra("LIBRARY_ID", libId)
        }
        startActivity(intent)
        finish()
    }

    private fun redirectToRegistration(email: String) {
        getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply()
        val user = auth.currentUser
        val intent = Intent(this, RegisterActivity::class.java).apply {
            putExtra("PREFILL_NAME", user?.displayName)
            putExtra("PREFILL_EMAIL", user?.email ?: email)
            putExtra("IS_GOOGLE", user?.providerData?.any { it.providerId == "google.com" } == true)
        }
        startActivity(intent)
        finish()
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
    fun LoginScreen(
        isLoading: Boolean,
        errorMessage: String,
        isDarkMode: Boolean,
        onLoginClick: (String, String, String) -> Unit,
        onForgotPassword: (String) -> Unit,
        onGoogleSignIn: () -> Unit,
        onRegisterClick: () -> Unit,
        onThemeToggle: (Boolean) -> Unit
    ) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var libraryId by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }

        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val bgGradient = Brush.verticalGradient(
            colors = if (isDarkMode) 
                listOf(Color(0xFF12100E), Color(0xFF1E1916)) 
                else listOf(Color(0xFFFFFFFF), Color(0xFFF7F0E9))
        )
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        // Theme Toggle Button
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
                modifier = Modifier.size(100.dp).clip(RoundedCornerShape(20.dp))
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Arividam",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = mainColor,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "Your intelligent library companion",
                fontSize = 14.sp,
                color = if (isDarkMode) Color.LightGray else Color.Gray,
                textAlign = TextAlign.Center
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
                    Text(
                        text = "Login",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = mainColor
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    // Email Field
                    CustomTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !isLoading,
                        isDarkMode = isDarkMode
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Library ID Field
                    CustomTextField(
                        value = libraryId,
                        onValueChange = { libraryId = it },
                        label = "Library ID",
                        enabled = !isLoading,
                        isDarkMode = isDarkMode
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    CustomTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        enabled = !isLoading,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onPasswordToggle = { passwordVisible = !passwordVisible },
                        isDarkMode = isDarkMode
                    )

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onForgotPassword(email) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Forgot Password?", fontSize = 12.sp, color = mainColor.copy(alpha = 0.7f))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator(color = mainColor)
                    } else {
                        Button(
                            onClick = { onLoginClick(email, password, libraryId) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = mainColor)
                        ) {
                            Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color(0xFF452719) else Color.White)
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkMode) Color.Gray.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.5f))
                            Text(" OR ", fontSize = 12.sp, color = if (isDarkMode) Color.LightGray else Color.Gray, modifier = Modifier.padding(horizontal = 12.dp))
                            HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkMode) Color.Gray.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.5f))
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        // Google Button
                        OutlinedButton(
                            onClick = onGoogleSignIn,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, if (isDarkMode) Color.Gray.copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Simple Google 'G' icon simulation
                                Card(modifier = Modifier.size(24.dp), shape = CircleShape, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text("G", fontWeight = FontWeight.Black, color = Color(0xFF4285F4), fontSize = 14.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Sign in with Google", color = mainColor, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        TextButton(onClick = onRegisterClick) {
                            Text("New here? Create an account", color = if (isDarkMode) Color(0xFF8D6E63) else Color(0xFFB08968), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTextField(
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
