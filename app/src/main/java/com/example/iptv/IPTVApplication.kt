package com.example.iptv

import android.app.Application
import coil.ImageLoader
import coil.Coil
import com.example.iptv.util.GlobalExceptionHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IPTVApplication : Application() {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        
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
