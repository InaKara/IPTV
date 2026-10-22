package com.example.iptv.di

import android.content.Context
import android.util.Log
import coil.disk.DiskCache
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.example.iptv.BuildConfig
import com.example.iptv.data.repository.ChannelRepository
import com.example.iptv.data.repository.ChannelRepositoryImpl
import com.example.iptv.diagnostics.StartupTimings
import dagger.Binds
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.InstallIn
import dagger.Module
import dagger.Provides
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideStartupTimings(): StartupTimings = StartupTimings(BuildConfig.DEBUG, output = {
        // Fire OS commonly sets persist.log.tag=I, which discards DEBUG records.
        // The entire trace is still disabled in release builds.
        Log.i("AypiStartup", it)
    })

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context, client: OkHttpClient): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .components { add(SvgDecoder.Factory()) }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // 5% of available disk space
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun repository(implementation: ChannelRepositoryImpl): ChannelRepository
}
