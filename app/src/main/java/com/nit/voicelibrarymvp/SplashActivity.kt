// SplashActivity.kt
// Location: com/nit/voicelibrarymvp/SplashActivity.kt

package com.nit.voicelibrarymvp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.nit.voicelibrarymvp.ui.theme.ArividamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDarkMode = getSharedPreferences("user_prefs", MODE_PRIVATE).getBoolean("isDarkMode", false)

        setContent {
            ArividamTheme(darkTheme = isDarkMode) {
                SplashScreen(isDarkMode)
            }
        }

        // Proceed to LoginActivity after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 1500)
    }
}

@Composable
fun SplashScreen(isDarkMode: Boolean) {
    val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
    val bgColor = if (isDarkMode) Color(0xFF12100E) else Color(0xFFFAF9F6)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Arividam",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = mainColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "നിങ്ങളുടെ ലൈബ്രറി",
                fontSize = 18.sp,
                color = mainColor.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(color = mainColor, strokeWidth = 3.dp)
        }
    }
}
