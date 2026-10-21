package com.example.iptv.ui.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.iptv.data.model.M3UChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    channels: List<M3UChannel>,
    onChannelClick: (M3UChannel, List<M3UChannel>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    // Load channels or refresh "Recently viewed" whenever returning to this screen
    LaunchedEffect(channels) {
        viewModel.loadChannels(channels)
    }
    
    // Refresh content whenever the screen is visible to catch "Recently viewed" updates
    LaunchedEffect(Unit) {
        viewModel.refreshContent()
    }

    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading Channels...")
            }
        }
        is HomeUiState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No Channels Found in Playlist.")
            }
        }
        is HomeUiState.Success -> {
            val firstChannelRequester = remember { FocusRequester() }
            val activeCategoryRequester = remember { FocusRequester() }

            Row(modifier = Modifier.fillMaxSize()) {
                // Persistent Sidebar
                Column(
                    Modifier
                        .fillMaxHeight()
                        .width(260.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    TvLazyColumn {
                        items(state.groups) { group ->
                            val isSelected = state.selectedGroup == group
                            Surface(
                                onClick = { viewModel.selectGroup(group) },
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                    focusedContainerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    pressedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                ),
                                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .then(if (isSelected) Modifier.focusRequester(activeCategoryRequester) else Modifier)
                                    .onPreviewKeyEvent {
                                        if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                            it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                                        ) {
                                            firstChannelRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            ) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Channel Grid
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text(
                        text = state.selectedGroup.name,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val channelList = state.selectedGroup.channels
                    TvLazyVerticalGrid(
                        columns = TvGridCells.Fixed(4),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(channelList.size) { index ->
                            val channel = channelList[index]
                            ChannelCard(
                                channel = channel,
                                onClick = { onChannelClick(channel, channelList) },
                                modifier = Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstChannelRequester) else Modifier)
                                    .onPreviewKeyEvent {
                                        if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                            it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                                        ) {
                                            if (index % 4 == 0) {
                                                activeCategoryRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelCard(
    channel: M3UChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        onClick = onClick,
        modifier = modifier
            .width(200.dp)
            .height(120.dp),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                if (!channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(channel.logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.width(60.dp).height(40.dp).padding(bottom = 8.dp),
                        contentScale = ContentScale.Fit,
                        onError = {
                            android.util.Log.e("ChannelCard", "Failed to load logo: ${channel.logoUrl}")
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.width(40.dp).height(40.dp).padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
