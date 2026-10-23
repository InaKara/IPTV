package com.example.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.iptv.ui.theme.IPTVTheme
import com.example.iptv.ui.login.StartupBranding
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val notice = getSharedPreferences("crash_notice", MODE_PRIVATE)
        if (notice.getBoolean("pending", false)) {
            notice.edit().remove("pending").apply()
            startActivity(Intent(this, com.example.iptv.ui.error.ErrorActivity::class.java))
            finish()
            return
        }
        setContent {
            IPTVTheme {
                SplashScreenContent {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SplashScreenContent(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000) // Show for 2 seconds
        onTimeout()
    }

    StartupBranding()
}
