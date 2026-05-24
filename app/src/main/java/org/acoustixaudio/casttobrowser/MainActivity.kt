package org.acoustixaudio.casttobrowser

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.decode.VideoFrameDecoder
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.acoustixaudio.casttobrowser.server.CastServerService
import org.acoustixaudio.casttobrowser.ui.MainAdaptiveEntry
import org.acoustixaudio.casttobrowser.ui.theme.CastToBrowserTheme
import org.acoustixaudio.casttobrowser.ui.viewmodel.MediaViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Edge-to-Edge
        enableEdgeToEdge()

        // Start the server service
        startService(Intent(this, CastServerService::class.java))

        setContent {
            CastToBrowserTheme {
                val context = LocalContext.current
                val viewModel: MediaViewModel = viewModel()

                // Handle Shared Intent
                LaunchedEffect(intent) {
                    handleIntent(intent, viewModel)
                }

                val imageLoader = remember {
                    ImageLoader.Builder(context)
                        .components {
                            add(VideoFrameDecoder.Factory())
                        }
                        .build()
                }

                CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                    val permissionsState = rememberMultiplePermissionsState(
                        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            listOf(
                                Manifest.permission.READ_MEDIA_VIDEO,
                                Manifest.permission.READ_MEDIA_IMAGES
                            )
                        } else {
                            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    )

                    LaunchedEffect(Unit) {
                        permissionsState.launchMultiplePermissionRequest()
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (permissionsState.allPermissionsGranted) {
                            MainAdaptiveEntry(viewModel = viewModel)
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .safeDrawingPadding(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = "Cast to Browser needs access to your media files to stream them to your browser.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                                        Text("Grant Permissions")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent, viewModel: MediaViewModel) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                val type = if (intent.type?.startsWith("video") == true) org.acoustixaudio.casttobrowser.data.MediaType.VIDEO else org.acoustixaudio.casttobrowser.data.MediaType.IMAGE
                val name = getFileName(uri)
                val mediaItem = org.acoustixaudio.casttobrowser.data.MediaItem(
                    id = System.currentTimeMillis(), // Temporary ID
                    name = name,
                    uri = uri,
                    type = type
                )
                viewModel.selectMedia(mediaItem)
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "Shared Media"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }
}
