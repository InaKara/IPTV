package com.example.iptv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.iptv.R

@Composable
internal fun ChannelLogo(
    url: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalContext.current.imageLoader
) {
    val fallback = painterResource(R.drawable.channel_logo_placeholder)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url?.trim()?.takeIf { it.isNotEmpty() })
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        placeholder = fallback,
        error = fallback,
        fallback = fallback
    )
}
