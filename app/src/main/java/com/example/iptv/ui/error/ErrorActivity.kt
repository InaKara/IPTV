package com.example.iptv.ui.error

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.iptv.R
import com.example.iptv.SplashActivity
import com.example.iptv.ui.theme.IPTVTheme

class ErrorActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVTheme {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.crash_title))
                    Text(stringResource(R.string.crash_message), Modifier.padding(16.dp))
                    Button(onClick = { startActivity(Intent(this@ErrorActivity, SplashActivity::class.java)); finish() }) {
                        Text(stringResource(R.string.restart_app))
                    }
                }
            }
        }
    }
}
