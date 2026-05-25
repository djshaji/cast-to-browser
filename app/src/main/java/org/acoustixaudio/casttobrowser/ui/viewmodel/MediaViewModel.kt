package org.acoustixaudio.casttobrowser.ui.viewmodel

import android.app.Application
import android.app.Activity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.acoustixaudio.casttobrowser.billing.BillingManager
import org.acoustixaudio.casttobrowser.data.CastUsageRepository
import org.acoustixaudio.casttobrowser.data.FolderBrowserRepository
import org.acoustixaudio.casttobrowser.data.FolderEntry
import org.acoustixaudio.casttobrowser.data.FolderLocation
import org.acoustixaudio.casttobrowser.data.MediaItem
import org.acoustixaudio.casttobrowser.data.MediaRepository
import org.acoustixaudio.casttobrowser.data.MediaType
import org.acoustixaudio.casttobrowser.server.ControlMessage
import org.acoustixaudio.casttobrowser.server.ServerState

enum class MediaFilter(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos")
}

enum class MediaSort(val label: String) {
    TIME_MODIFIED("Time modified"),
    SIZE("Size"),
    ALPHABETICAL("Alphabetical")
}

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val folderRepository = FolderBrowserRepository(application)
    private val castUsageRepository = CastUsageRepository(application)
    private val billingManager = BillingManager(application)
    private var allMediaItems: List<MediaItem> = emptyList()

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedFilter = MutableStateFlow(MediaFilter.ALL)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(MediaSort.TIME_MODIFIED)
    val selectedSort = _selectedSort.asStateFlow()

    private val _folderTreeUri = MutableStateFlow<Uri?>(null)
    val folderTreeUri = _folderTreeUri.asStateFlow()

    private val _folderPath = MutableStateFlow<List<FolderLocation>>(emptyList())
    val folderPath = _folderPath.asStateFlow()

    private val _folderEntries = MutableStateFlow<List<FolderEntry>>(emptyList())
    val folderEntries = _folderEntries.asStateFlow()

    private val _folderLoading = MutableStateFlow(false)
    val folderLoading = _folderLoading.asStateFlow()

    private val _folderError = MutableStateFlow<String?>(null)
    val folderError = _folderError.asStateFlow()

    private val _showPurchaseScreen = MutableStateFlow(false)
    val showPurchaseScreen = _showPurchaseScreen.asStateFlow()

    val serverIp = ServerState.serverIp
    val serverPort = ServerState.serverPort
    val serverError = ServerState.serverError
    val currentMedia = ServerState.currentMedia
    val telemetry = ServerState.telemetry
    val isBillingReady = billingManager.isBillingReady
    val isPro = billingManager.isPro
    val billingError = billingManager.billingError
    val purchaseMessage = billingManager.purchaseMessage

    init {
        loadMedia()
        restoreFolderTree()
        billingManager.connect()
        observePurchaseState()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            allMediaItems = repository.fetchMedia()
            applyMediaPresentation()
            _isLoading.value = false
        }
    }

    fun updateFilter(filter: MediaFilter) {
        _selectedFilter.value = filter
        applyMediaPresentation()
    }

    fun updateSort(sort: MediaSort) {
        _selectedSort.value = sort
        applyMediaPresentation()
    }

    fun setFolderTree(uri: Uri) {
        viewModelScope.launch {
            folderRepository.persistTreeUri(uri)
            openFolderTree(uri)
        }
    }

    fun navigateIntoFolder(entry: FolderEntry) {
        if (!entry.isDirectory) return
        val treeUri = _folderTreeUri.value ?: return

        viewModelScope.launch {
            _folderLoading.value = true
            try {
                _folderEntries.value = folderRepository.getChildren(treeUri, entry.uri)
                _folderPath.value = _folderPath.value + FolderLocation(entry.name, entry.uri)
                _folderError.value = null
            } catch (error: SecurityException) {
                _folderError.value = "Folder access is no longer available. Pick the folder again."
            } catch (error: IllegalArgumentException) {
                _folderError.value = error.message ?: "Could not open this folder."
            } finally {
                _folderLoading.value = false
            }
        }
    }

    fun navigateUpFolder() {
        val treeUri = _folderTreeUri.value ?: return
        val currentPath = _folderPath.value
        if (currentPath.size <= 1) return
        val targetPath = currentPath.dropLast(1)
        val targetFolder = targetPath.last()

        viewModelScope.launch {
            _folderLoading.value = true
            try {
                _folderEntries.value = folderRepository.getChildren(treeUri, targetFolder.uri)
                _folderPath.value = targetPath
                _folderError.value = null
            } catch (error: SecurityException) {
                _folderError.value = "Folder access is no longer available. Pick the folder again."
            } catch (error: IllegalArgumentException) {
                _folderError.value = error.message ?: "Could not open this folder."
            } finally {
                _folderLoading.value = false
            }
        }
    }

    fun selectFolderEntry(entry: FolderEntry) {
        val mediaType = entry.mediaType ?: return
        selectMedia(
            MediaItem(
                id = stableUriId(entry.uri),
                name = entry.name,
                uri = entry.uri,
                type = mediaType,
                size = entry.size,
                modifiedTime = entry.modifiedTime
            )
        )
    }

    fun setFolderError(message: String?) {
        _folderError.value = message
    }

    fun selectMedia(media: MediaItem) {
        viewModelScope.launch {
            maybeShowPurchaseScreen()
            ServerState.setCurrentMedia(media)
        }
    }

    fun dismissPurchaseScreen() {
        _showPurchaseScreen.value = false
    }

    fun openPurchaseScreen() {
        if (!isPro.value) {
            _showPurchaseScreen.value = true
        }
    }

    fun restorePurchases() {
        billingManager.refreshPurchases()
    }

    fun launchProPurchase(activity: Activity) {
        billingManager.launchProPurchase(activity)
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

    private fun applyMediaPresentation() {
        val filteredItems = when (_selectedFilter.value) {
            MediaFilter.ALL -> allMediaItems
            MediaFilter.IMAGES -> allMediaItems.filter { it.type == MediaType.IMAGE }
            MediaFilter.VIDEOS -> allMediaItems.filter { it.type == MediaType.VIDEO }
        }

        _mediaItems.value = when (_selectedSort.value) {
            MediaSort.TIME_MODIFIED -> filteredItems.sortedByDescending { it.modifiedTime }
            MediaSort.SIZE -> filteredItems.sortedByDescending { it.size }
            MediaSort.ALPHABETICAL -> filteredItems.sortedBy { it.name.lowercase() }
        }
    }

    private fun restoreFolderTree() {
        viewModelScope.launch {
            val persistedTreeUri = folderRepository.getPersistedTreeUri() ?: return@launch
            openFolderTree(persistedTreeUri)
        }
    }

    private suspend fun openFolderTree(treeUri: Uri) {
        _folderLoading.value = true
        try {
            val rootLocation = folderRepository.getRootLocation(treeUri)
            _folderTreeUri.value = treeUri
            _folderPath.value = listOf(rootLocation)
            _folderEntries.value = folderRepository.getChildren(treeUri, rootLocation.uri)
            _folderError.value = null
        } catch (error: SecurityException) {
            folderRepository.clearPersistedTreeUri()
            _folderTreeUri.value = null
            _folderPath.value = emptyList()
            _folderEntries.value = emptyList()
            _folderError.value = "Folder access is no longer available. Pick the folder again."
        } catch (error: IllegalArgumentException) {
            _folderTreeUri.value = null
            _folderPath.value = emptyList()
            _folderEntries.value = emptyList()
            _folderError.value = error.message ?: "Could not open the selected folder."
        } finally {
            _folderLoading.value = false
        }
    }

    private fun stableUriId(uri: Uri): Long {
        return uri.toString().fold(1125899906842597L) { hash, character ->
            31L * hash + character.code
        }
    }

    private suspend fun maybeShowPurchaseScreen() {
        val castCount = castUsageRepository.incrementCastCount()
        val paywallAlreadyShown = castUsageRepository.hasShownPaywall()
        if (!isPro.value && castCount >= FREE_CAST_LIMIT && !paywallAlreadyShown) {
            castUsageRepository.markPaywallShown()
            _showPurchaseScreen.value = true
        }
    }

    private fun observePurchaseState() {
        viewModelScope.launch {
            isPro.collectLatest { purchased ->
                if (purchased) {
                    _showPurchaseScreen.value = false
                }
            }
        }
    }

    override fun onCleared() {
        billingManager.disconnect()
        super.onCleared()
    }

    companion object {
        private const val FREE_CAST_LIMIT = 2
    }
}
