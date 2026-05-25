package org.acoustixaudio.casttobrowser.ui.media

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import org.acoustixaudio.casttobrowser.data.FolderEntry
import org.acoustixaudio.casttobrowser.data.FolderLocation
import org.acoustixaudio.casttobrowser.data.MediaItem
import org.acoustixaudio.casttobrowser.data.MediaType
import org.acoustixaudio.casttobrowser.ui.viewmodel.MediaFilter
import org.acoustixaudio.casttobrowser.ui.viewmodel.MediaSort
import org.acoustixaudio.casttobrowser.ui.viewmodel.MediaViewModel

private enum class MainTab(val label: String) {
    GALLERY("Gallery"),
    FOLDERS("Folders")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
    viewModel: MediaViewModel,
    isRemoteVisible: Boolean,
    onShowRemote: () -> Unit = {}
) {
    val context = LocalContext.current
    val mediaItems by viewModel.mediaItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val serverError by viewModel.serverError.collectAsState()
    val currentMedia by viewModel.currentMedia.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val folderTreeUri by viewModel.folderTreeUri.collectAsState()
    val folderPath by viewModel.folderPath.collectAsState()
    val folderEntries by viewModel.folderEntries.collectAsState()
    val folderLoading by viewModel.folderLoading.collectAsState()
    val folderError by viewModel.folderError.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.GALLERY) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.setFolderTree(uri)
                selectedTab = MainTab.FOLDERS
            } catch (error: SecurityException) {
                viewModel.setFolderError("Could not keep access to the selected folder.")
            } catch (error: IllegalArgumentException) {
                viewModel.setFolderError(error.message ?: "Could not open the selected folder.")
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
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
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    MainTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) }
                        )
                    }
                }
                if (selectedTab == MainTab.GALLERY) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MediaDropdown(
                            label = "Show",
                            selectedOption = selectedFilter.label,
                            options = MediaFilter.entries.map { it.label },
                            onOptionSelected = { label ->
                                viewModel.updateFilter(MediaFilter.entries.first { it.label == label })
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MediaDropdown(
                            label = "Sort",
                            selectedOption = selectedSort.label,
                            options = MediaSort.entries.map { it.label },
                            onOptionSelected = { label ->
                                viewModel.updateSort(MediaSort.entries.first { it.label == label })
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    FolderToolbar(
                        path = folderPath,
                        canNavigateUp = folderPath.size > 1,
                        onPickFolder = { folderPicker.launch(folderTreeUri) },
                        onNavigateUp = { viewModel.navigateUpFolder() }
                    )
                }
            }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (serverError != null) {
                ErrorMessage(
                    message = serverError!!,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (selectedTab == MainTab.GALLERY) {
                GalleryContent(
                    mediaItems = mediaItems,
                    isLoading = isLoading,
                    onSelectMedia = { item ->
                        viewModel.selectMedia(item)
                        onShowRemote()
                    }
                )
            } else {
                FolderBrowserContent(
                    folderTreeSelected = folderTreeUri != null,
                    folderPath = folderPath,
                    folderEntries = folderEntries,
                    isLoading = folderLoading,
                    errorMessage = folderError,
                    isRemoteVisible = isRemoteVisible,
                    onPickFolder = { folderPicker.launch(folderTreeUri) },
                    onNavigateUp = { viewModel.navigateUpFolder() },
                    onSelectEntry = { entry ->
                        if (entry.isDirectory) {
                            viewModel.navigateIntoFolder(entry)
                        } else {
                            viewModel.selectFolderEntry(entry)
                            onShowRemote()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun GalleryContent(
    mediaItems: List<MediaItem>,
    isLoading: Boolean,
    onSelectMedia: (MediaItem) -> Unit
) {
    when {
        isLoading -> {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        }

        mediaItems.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "No media found for the selected filter",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mediaItems) { item ->
                    MediaGridItem(
                        item = item,
                        onClick = { onSelectMedia(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderBrowserContent(
    folderTreeSelected: Boolean,
    folderPath: List<FolderLocation>,
    folderEntries: List<FolderEntry>,
    isLoading: Boolean,
    errorMessage: String?,
    isRemoteVisible: Boolean,
    onPickFolder: () -> Unit,
    onNavigateUp: () -> Unit,
    onSelectEntry: (FolderEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = folderPath.size > 1 && !isRemoteVisible) {
        onNavigateUp()
    }

    when {
        !folderTreeSelected -> {
            EmptyFolderState(
                message = "Pick a folder to browse files with scoped storage.",
                buttonLabel = "Choose Folder",
                onButtonClick = onPickFolder,
                modifier = modifier
            )
        }

        isLoading -> {
            Box(modifier = modifier) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        errorMessage != null -> {
            EmptyFolderState(
                message = errorMessage,
                buttonLabel = "Choose Folder",
                onButtonClick = onPickFolder,
                modifier = modifier
            )
        }

        folderEntries.isEmpty() -> {
            EmptyFolderState(
                message = "No folders or supported media found here.",
                buttonLabel = "Choose Folder",
                onButtonClick = onPickFolder,
                modifier = modifier
            )
        }

        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folderEntries, key = { it.uri.toString() }) { entry ->
                    FolderEntryRow(
                        entry = entry,
                        onClick = { onSelectEntry(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFolderState(
    message: String,
    buttonLabel: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onButtonClick) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun FolderToolbar(
    path: List<FolderLocation>,
    canNavigateUp: Boolean,
    onPickFolder: () -> Unit,
    onNavigateUp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onPickFolder) {
                Text("Choose Folder")
            }
            if (canNavigateUp) {
                FilledTonalButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Up"
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Up")
                }
            }
        }
        if (path.isNotEmpty()) {
            Text(
                text = path.joinToString(" / ") { it.name },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDropdown(
    label: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            maxLines = 1
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
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
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                }
            }

            if (media.type == MediaType.VIDEO && duration > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Slider(
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
            AsyncImage(
                model = item.uri,
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

@Composable
private fun FolderEntryRow(
    entry: FolderEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FolderEntryThumbnail(entry = entry)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        entry.isDirectory -> "Folder"
                        entry.mediaType == MediaType.IMAGE -> formatFileDetails("Image", entry)
                        else -> formatFileDetails("Video", entry)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FolderEntryThumbnail(entry: FolderEntry) {
    Surface(
        modifier = Modifier.size(56.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (entry.isDirectory) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                SubcomposeAsyncImage(
                    model = entry.uri,
                    contentDescription = entry.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (entry.mediaType == MediaType.IMAGE) {
                                    Icons.Default.Image
                                } else {
                                    Icons.Default.VideoLibrary
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    success = {
                        SubcomposeAsyncImageContent()
                    }
                )

                if (entry.mediaType == MediaType.VIDEO) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(1.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileDetails(label: String, entry: FolderEntry): String {
    return if (entry.size > 0) {
        "$label • ${formatFileSize(entry.size)}"
    } else {
        label
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes < 1024) return "$sizeBytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = sizeBytes.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format("%.1f %s", value, units[unitIndex])
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
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
