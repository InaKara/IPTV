package com.example.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.iptv.ui.theme.IPTVTheme
import com.example.iptv.ui.login.StartupBranding
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import com.example.iptv.util.AppTaskNavigation

@OptIn(ExperimentalTvMaterial3Api::class)
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppTaskNavigation.isClosing(this)) { finish(); return }
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
                    AppTaskNavigation.openBrowser(this)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SplashScreenContent(onTimeout: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val timeout by androidx.compose.runtime.rememberUpdatedState(onTimeout)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(2000) // Fresh foreground launch keeps its two-second splash.
            timeout()
            awaitCancellation()
        }
    }

    StartupBranding()
}
