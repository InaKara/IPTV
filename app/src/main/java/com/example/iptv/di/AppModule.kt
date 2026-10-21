package com.example.iptv.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import com.example.iptv.data.parser.M3UParser
import com.example.iptv.data.repository.ChannelRepository
import com.example.iptv.data.repository.ChannelRepositoryImpl
import com.example.iptv.data.storage.CredentialsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideM3UParser(): M3UParser = M3UParser()

    @Provides
    @Singleton
    fun provideChannelRepository(parser: M3UParser): ChannelRepository = ChannelRepositoryImpl(parser)

    @Provides
    @Singleton
    fun provideCredentialsManager(@ApplicationContext context: Context): CredentialsManager = CredentialsManager(context)

    @Provides
    @Singleton
    fun provideRecentlyWatchedManager(@ApplicationContext context: Context): com.example.iptv.data.storage.RecentlyWatchedManager {
        return com.example.iptv.data.storage.RecentlyWatchedManager(context)
    }

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
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
