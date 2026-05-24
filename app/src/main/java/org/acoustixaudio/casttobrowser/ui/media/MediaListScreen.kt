package org.acoustixaudio.casttobrowser.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.acoustixaudio.casttobrowser.data.MediaItem
import org.acoustixaudio.casttobrowser.data.MediaType
import org.acoustixaudio.casttobrowser.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
    viewModel: MediaViewModel,
    onShowRemote: () -> Unit = {}
) {
    val mediaItems by viewModel.mediaItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val serverError by viewModel.serverError.collectAsState()
    val currentMedia by viewModel.currentMedia.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cast to Browser") },
                actions = {
                    serverIp?.let {
                        Text(
                            text = "http://$it:$serverPort",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            currentMedia?.let { media ->
                PlaybackControlBar(
                    media = media,
                    isPlaying = telemetry?.isPlaying ?: false,
                    currentTime = telemetry?.currentTime?.toFloat() ?: 0f,
                    duration = telemetry?.duration?.toFloat() ?: 0f,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onSeek = { viewModel.seekTo(it) },
                    onClick = onShowRemote
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (serverError != null) {
                ErrorMessage(
                    message = serverError!!,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (mediaItems.isEmpty()) {
                Text(
                    text = "No media found",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mediaItems) { item ->
                        MediaGridItem(item = item, onClick = { 
                            viewModel.selectMedia(item)
                            onShowRemote()
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackControlBar(
    media: MediaItem,
    isPlaying: Boolean,
    currentTime: Float,
    duration: Float,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    Text(
                        text = if (media.type == MediaType.VIDEO) "Video" else "Image",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (media.type == MediaType.VIDEO) {
                    IconButton(onClick = { 
                        onTogglePlay()
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                }
            }

            if (media.type == MediaType.VIDEO && duration > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = currentTime,
                    onValueChange = onSeek,
                    valueRange = 0f..duration,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun MediaGridItem(item: MediaItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val model = if (item.type == MediaType.VIDEO) {
                item.uri // Coil handles video URIs by extracting a frame if fetcher is configured
            } else {
                item.uri
            }

            AsyncImage(
                model = model,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            if (item.type == MediaType.VIDEO) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = formatDuration(item.duration),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun ErrorMessage(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Server Error",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
