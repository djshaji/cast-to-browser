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
import org.acoustixaudio.casttobrowser.data.SmbConnection
import org.acoustixaudio.casttobrowser.data.SmbRemoteAccess
import org.acoustixaudio.casttobrowser.data.SmbRepository
import org.acoustixaudio.casttobrowser.data.WebDavConnection
import org.acoustixaudio.casttobrowser.data.WebDavRemoteAccess
import org.acoustixaudio.casttobrowser.data.WebDavRepository
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
    private val smbRepository = SmbRepository(application)
    private val webDavRepository = WebDavRepository(application)
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

    private val _webDavConnection = MutableStateFlow<WebDavConnection?>(null)
    val webDavConnection = _webDavConnection.asStateFlow()

    private val _webDavPath = MutableStateFlow<List<FolderLocation>>(emptyList())
    val webDavPath = _webDavPath.asStateFlow()

    private val _webDavEntries = MutableStateFlow<List<FolderEntry>>(emptyList())
    val webDavEntries = _webDavEntries.asStateFlow()

    private val _webDavLoading = MutableStateFlow(false)
    val webDavLoading = _webDavLoading.asStateFlow()

    private val _webDavError = MutableStateFlow<String?>(null)
    val webDavError = _webDavError.asStateFlow()

    private val _smbConnection = MutableStateFlow<SmbConnection?>(null)
    val smbConnection = _smbConnection.asStateFlow()

    private val _smbPath = MutableStateFlow<List<FolderLocation>>(emptyList())
    val smbPath = _smbPath.asStateFlow()

    private val _smbEntries = MutableStateFlow<List<FolderEntry>>(emptyList())
    val smbEntries = _smbEntries.asStateFlow()

    private val _smbLoading = MutableStateFlow(false)
    val smbLoading = _smbLoading.asStateFlow()

    private val _smbError = MutableStateFlow<String?>(null)
    val smbError = _smbError.asStateFlow()

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
        restoreSmbConnection()
        restoreWebDavConnection()
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

    fun connectWebDav(baseUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _webDavLoading.value = true
            try {
                val connection = webDavRepository.normalizeConnection(
                    WebDavConnection(
                        baseUrl = baseUrl,
                        username = username.trim(),
                        password = password
                    )
                )
                _webDavConnection.value = connection
                val rootLocation = webDavRepository.getRootLocation(connection)
                _webDavPath.value = listOf(rootLocation)
                _webDavEntries.value = webDavRepository.getChildren(connection, rootLocation.uri)
                _webDavError.value = null
                webDavRepository.persistConnection(connection)
            } catch (error: IllegalArgumentException) {
                _webDavPath.value = emptyList()
                _webDavEntries.value = emptyList()
                _webDavError.value = error.message ?: "Enter a valid WebDAV URL."
            } catch (error: Exception) {
                _webDavPath.value = emptyList()
                _webDavEntries.value = emptyList()
                _webDavError.value = error.message ?: "Could not connect to the WebDAV server."
            } finally {
                _webDavLoading.value = false
            }
        }
    }

    fun disconnectWebDav() {
        viewModelScope.launch {
            webDavRepository.clearPersistedConnection()
            _webDavConnection.value = null
            _webDavPath.value = emptyList()
            _webDavEntries.value = emptyList()
            _webDavError.value = null
            _webDavLoading.value = false
        }
    }

    fun navigateIntoWebDav(entry: FolderEntry) {
        if (!entry.isDirectory) return
        val connection = _webDavConnection.value ?: return

        viewModelScope.launch {
            _webDavLoading.value = true
            try {
                _webDavEntries.value = webDavRepository.getChildren(connection, entry.uri)
                _webDavPath.value = _webDavPath.value + FolderLocation(entry.name, entry.uri)
                _webDavError.value = null
            } catch (error: Exception) {
                _webDavError.value = error.message ?: "Could not open this WebDAV folder."
            } finally {
                _webDavLoading.value = false
            }
        }
    }

    fun navigateUpWebDav() {
        val connection = _webDavConnection.value ?: return
        val currentPath = _webDavPath.value
        if (currentPath.size <= 1) return
        val targetPath = currentPath.dropLast(1)
        val targetFolder = targetPath.last()

        viewModelScope.launch {
            _webDavLoading.value = true
            try {
                _webDavEntries.value = webDavRepository.getChildren(connection, targetFolder.uri)
                _webDavPath.value = targetPath
                _webDavError.value = null
            } catch (error: Exception) {
                _webDavError.value = error.message ?: "Could not open this WebDAV folder."
            } finally {
                _webDavLoading.value = false
            }
        }
    }

    fun selectWebDavEntry(entry: FolderEntry) {
        val mediaType = entry.mediaType ?: return
        val connection = _webDavConnection.value ?: return
        selectMedia(
            MediaItem(
                id = stableUriId(entry.uri),
                name = entry.name,
                uri = entry.uri,
                type = mediaType,
                size = entry.size,
                modifiedTime = entry.modifiedTime,
                mimeType = entry.mimeType,
                remoteAccess = WebDavRemoteAccess(
                    username = connection.username,
                    password = connection.password
                )
            )
        )
    }

    fun connectSmb(server: String, share: String, username: String, password: String, domain: String) {
        viewModelScope.launch {
            _smbLoading.value = true
            try {
                val connection = smbRepository.normalizeConnection(
                    SmbConnection(
                        server = server,
                        share = share,
                        username = username,
                        password = password,
                        domain = domain
                    )
                )
                _smbConnection.value = connection
                val rootLocation = smbRepository.getRootLocation(connection)
                _smbPath.value = listOf(rootLocation)
                _smbEntries.value = smbRepository.getChildren(connection, rootLocation.uri)
                _smbError.value = null
                smbRepository.persistConnection(connection)
            } catch (error: IllegalArgumentException) {
                _smbPath.value = emptyList()
                _smbEntries.value = emptyList()
                _smbError.value = error.message ?: "Enter a valid SMB server."
            } catch (error: Exception) {
                _smbPath.value = emptyList()
                _smbEntries.value = emptyList()
                _smbError.value = error.message ?: "Could not connect to the SMB server."
            } finally {
                _smbLoading.value = false
            }
        }
    }

    fun disconnectSmb() {
        viewModelScope.launch {
            smbRepository.clearPersistedConnection()
            _smbConnection.value = null
            _smbPath.value = emptyList()
            _smbEntries.value = emptyList()
            _smbError.value = null
            _smbLoading.value = false
        }
    }

    fun navigateIntoSmb(entry: FolderEntry) {
        if (!entry.isDirectory) return
        val connection = _smbConnection.value ?: return

        viewModelScope.launch {
            _smbLoading.value = true
            try {
                _smbEntries.value = smbRepository.getChildren(connection, entry.uri)
                _smbPath.value = _smbPath.value + FolderLocation(entry.name, entry.uri)
                _smbError.value = null
            } catch (error: Exception) {
                _smbError.value = error.message ?: "Could not open this SMB folder."
            } finally {
                _smbLoading.value = false
            }
        }
    }

    fun navigateUpSmb() {
        val connection = _smbConnection.value ?: return
        val currentPath = _smbPath.value
        if (currentPath.size <= 1) return
        val targetPath = currentPath.dropLast(1)
        val targetFolder = targetPath.last()

        viewModelScope.launch {
            _smbLoading.value = true
            try {
                _smbEntries.value = smbRepository.getChildren(connection, targetFolder.uri)
                _smbPath.value = targetPath
                _smbError.value = null
            } catch (error: Exception) {
                _smbError.value = error.message ?: "Could not open this SMB folder."
            } finally {
                _smbLoading.value = false
            }
        }
    }

    fun selectSmbEntry(entry: FolderEntry) {
        val mediaType = entry.mediaType ?: return
        val connection = _smbConnection.value ?: return
        selectMedia(
            MediaItem(
                id = stableUriId(entry.uri),
                name = entry.name,
                uri = entry.uri,
                type = mediaType,
                size = entry.size,
                modifiedTime = entry.modifiedTime,
                mimeType = entry.mimeType,
                remoteAccess = SmbRemoteAccess(
                    domain = connection.domain,
                    username = connection.username,
                    password = connection.password
                )
            )
        )
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

    private fun restoreSmbConnection() {
        viewModelScope.launch {
            val persistedConnection = smbRepository.getPersistedConnection() ?: return@launch
            _smbConnection.value = persistedConnection
            _smbLoading.value = true
            try {
                val rootLocation = smbRepository.getRootLocation(persistedConnection)
                _smbPath.value = listOf(rootLocation)
                _smbEntries.value = smbRepository.getChildren(persistedConnection, rootLocation.uri)
                _smbError.value = null
            } catch (error: Exception) {
                _smbPath.value = emptyList()
                _smbEntries.value = emptyList()
                _smbError.value = error.message ?: "Could not reconnect to the SMB server."
            } finally {
                _smbLoading.value = false
            }
        }
    }

    private fun restoreWebDavConnection() {
        viewModelScope.launch {
            val persistedConnection = webDavRepository.getPersistedConnection() ?: return@launch
            _webDavConnection.value = persistedConnection
            _webDavLoading.value = true
            try {
                val rootLocation = webDavRepository.getRootLocation(persistedConnection)
                _webDavPath.value = listOf(rootLocation)
                _webDavEntries.value = webDavRepository.getChildren(persistedConnection, rootLocation.uri)
                _webDavError.value = null
            } catch (error: Exception) {
                _webDavPath.value = emptyList()
                _webDavEntries.value = emptyList()
                _webDavError.value = error.message ?: "Could not reconnect to the WebDAV server."
            } finally {
                _webDavLoading.value = false
            }
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
