package org.acoustixaudio.casttobrowser.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.acoustixaudio.casttobrowser.data.MediaItem
import org.acoustixaudio.casttobrowser.data.MediaType
import org.acoustixaudio.casttobrowser.server.TelemetryData
import org.acoustixaudio.casttobrowser.ui.media.formatDuration

@Composable
fun RemoteControlDashboard(
    media: MediaItem,
    telemetry: TelemetryData?,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying = telemetry?.isPlaying ?: false
    val currentTime = telemetry?.currentTime?.toFloat() ?: 0f
    val duration = telemetry?.duration?.toFloat() ?: 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Dashboard")
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Artwork/Thumbnail
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = media.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title & Type
        Text(
            text = media.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Text(
            text = if (media.type == MediaType.VIDEO) "Casting Video" else "Viewing Image",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.weight(1f))

        if (media.type == MediaType.VIDEO && duration > 0) {
            // Seek Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentTime,
                    onValueChange = onSeek,
                    valueRange = 0f..duration,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration((currentTime * 1000).toLong()),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = formatDuration((duration * 1000).toLong()),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Playback Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                ControlIcon(Icons.Rounded.Replay10, "Rewind 10s") { onSeek((currentTime - 10).coerceAtLeast(0f)) }
                
                LargeFloatingActionButton(
                    onClick = onTogglePlay,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                ControlIcon(Icons.Rounded.Forward10, "Forward 10s") { onSeek((currentTime + 10).coerceAtMost(duration)) }
            }
        } else {
            // Image Controls (e.g. Navigation if implemented)
            Text(
                text = "Viewing on Browser",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun ControlIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
