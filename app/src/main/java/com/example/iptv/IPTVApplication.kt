package com.example.iptv

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.example.iptv.util.GlobalExceptionHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class IPTVApplication : Application() {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        // Media3 can otherwise print provider URLs/throwable details from inside the library.
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
        
        // Initialize Coil with custom ImageLoader (disk caching)
        Coil.setImageLoader(imageLoader)
        
        // Global exception handler
        Thread.setDefaultUncaughtExceptionHandler(
            GlobalExceptionHandler(
                applicationContext,
                Thread.getDefaultUncaughtExceptionHandler()
            )
        )
    }
}
