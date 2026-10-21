package com.example.iptv.di

import android.content.Context
import coil.disk.DiskCache
import coil.ImageLoader
import com.example.iptv.data.repository.ChannelRepository
import com.example.iptv.data.repository.ChannelRepositoryImpl
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
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context, client: OkHttpClient): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(client)
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
