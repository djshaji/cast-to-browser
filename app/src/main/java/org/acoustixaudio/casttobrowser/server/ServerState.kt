package org.acoustixaudio.casttobrowser.server

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.acoustixaudio.casttobrowser.data.MediaItem
import kotlinx.serialization.Serializable

@Serializable
data class TelemetryData(
    val currentTime: Double,
    val duration: Double,
    val isPlaying: Boolean
)

object ServerState {
    private val _currentMedia = MutableStateFlow<MediaItem?>(null)
    val currentMedia = _currentMedia.asStateFlow()

    private val _mediaLoadEvent = MutableSharedFlow<MediaItem>(replay = 0)
    val mediaLoadEvent = _mediaLoadEvent.asSharedFlow()

    private val _serverIp = MutableStateFlow<String?>(null)
    val serverIp = _serverIp.asStateFlow()

    private val _serverPort = MutableStateFlow(8080)
    val serverPort = _serverPort.asStateFlow()

    private val _serverError = MutableStateFlow<String?>(null)
    val serverError = _serverError.asStateFlow()

    private val _telemetry = MutableStateFlow<TelemetryData?>(null)
    val telemetry = _telemetry.asStateFlow()

    private val _commandFlow = MutableSharedFlow<ControlMessage>(replay = 0)
    val commandFlow = _commandFlow.asSharedFlow()

    suspend fun setCurrentMedia(media: MediaItem) {
        _currentMedia.value = media
        _mediaLoadEvent.emit(media)
    }

    fun setServerIp(ip: String?) {
        _serverIp.value = ip
    }

    fun setServerPort(port: Int) {
        _serverPort.value = port
    }

    fun setServerError(error: String?) {
        _serverError.value = error
    }

    fun updateTelemetry(data: TelemetryData) {
        _telemetry.value = data
    }

    suspend fun sendCommand(command: ControlMessage) {
        _commandFlow.emit(command)
    }
}
