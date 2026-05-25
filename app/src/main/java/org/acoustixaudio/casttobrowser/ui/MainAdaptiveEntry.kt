package org.acoustixaudio.casttobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.acoustixaudio.casttobrowser.ui.media.MediaListScreen
import org.acoustixaudio.casttobrowser.ui.remote.RemoteControlDashboard
import org.acoustixaudio.casttobrowser.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainAdaptiveEntry(viewModel: MediaViewModel) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Nothing>()
    val coroutineScope = rememberCoroutineScope()
    val currentMedia by viewModel.currentMedia.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    BackHandler(navigator.canNavigateBack()) {
        coroutineScope.launch {
            navigator.navigateBack()
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            MediaListScreen(
                viewModel = viewModel,
                isRemoteVisible = navigator.canNavigateBack(),
                onShowRemote = {
                    coroutineScope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                    }
                }
            )
        },
        detailPane = {
            currentMedia?.let { media ->
                RemoteControlDashboard(
                    media = media,
                    telemetry = telemetry,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onSeek = { viewModel.seekTo(it) },
                    onClose = {
                        coroutineScope.launch {
                            navigator.navigateBack()
                        }
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
