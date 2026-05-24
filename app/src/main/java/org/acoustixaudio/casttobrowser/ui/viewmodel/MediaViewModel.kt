package org.acoustixaudio.casttobrowser.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.acoustixaudio.casttobrowser.data.MediaItem
import org.acoustixaudio.casttobrowser.data.MediaRepository
import org.acoustixaudio.casttobrowser.server.ControlMessage
import org.acoustixaudio.casttobrowser.server.ServerState

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    val serverIp = ServerState.serverIp
    val serverPort = ServerState.serverPort
    val serverError = ServerState.serverError
    val currentMedia = ServerState.currentMedia
    val telemetry = ServerState.telemetry

    init {
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            _mediaItems.value = repository.fetchMedia()
            _isLoading.value = false
        }
    }

    fun selectMedia(media: MediaItem) {
        viewModelScope.launch {
            ServerState.setCurrentMedia(media)
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            val isPlaying = telemetry.value?.isPlaying ?: false
            if (isPlaying) {
                ServerState.sendCommand(ControlMessage("PAUSE"))
            } else {
                ServerState.sendCommand(ControlMessage("PLAY"))
            }
        }
    }

    fun seekTo(position: Float) {
        viewModelScope.launch {
            ServerState.sendCommand(ControlMessage("SEEK", position.toString()))
        }
    }
}
