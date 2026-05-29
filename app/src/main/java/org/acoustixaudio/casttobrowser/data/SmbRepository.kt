package org.acoustixaudio.casttobrowser.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Properties

private val Context.smbDataStore by preferencesDataStore(name = "smb_browser")

data class SmbConnection(
    val server: String,
    val share: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = ""
)

data class SmbOpenFile(
    val file: SmbFile,
    val randomAccessFile: SmbRandomAccessFile
) : AutoCloseable {
    override fun close() {
        randomAccessFile.close()
    }
}

internal object SmbUrlUtils {
    fun normalizeConnection(connection: SmbConnection): SmbConnection {
        val normalizedServer = connection.server
            .trim()
            .removePrefix("smb://")
            .trim('/')
        require(normalizedServer.isNotEmpty()) { "Enter an SMB server name or address." }

        return connection.copy(
            server = normalizedServer,
            share = connection.share.trim().trim('/'),
            username = connection.username.trim(),
            domain = connection.domain.trim()
        )
    }

    fun buildRootUrl(connection: SmbConnection): String {
        val normalizedConnection = normalizeConnection(connection)
        return if (normalizedConnection.share.isBlank()) {
            "smb://${normalizedConnection.server}/"
        } else {
            "smb://${normalizedConnection.server}/${normalizedConnection.share}/"
        }
    }

    fun ensureDirectoryUrl(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }

    fun nameFromUrl(url: String): String {
        val trimmed = ensureDirectoryUrl(url).removePrefix("smb://").trimEnd('/')
        val segments = trimmed.split('/').filter { it.isNotBlank() }
        return segments.lastOrNull() ?: "SMB"
    }
}

class SmbRepository(private val context: Context) {
    private val serverKey = stringPreferencesKey("server")
    private val shareKey = stringPreferencesKey("share")
    private val usernameKey = stringPreferencesKey("username")
    private val passwordKey = stringPreferencesKey("password")
    private val domainKey = stringPreferencesKey("domain")

    fun normalizeConnection(connection: SmbConnection): SmbConnection {
        return SmbUrlUtils.normalizeConnection(connection)
    }

    suspend fun getPersistedConnection(): SmbConnection? {
        val preferences = context.smbDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        val server = preferences[serverKey] ?: return null
        return SmbConnection(
            server = server,
            share = preferences[shareKey].orEmpty(),
            username = preferences[usernameKey].orEmpty(),
            password = preferences[passwordKey].orEmpty(),
            domain = preferences[domainKey].orEmpty()
        )
    }

    suspend fun persistConnection(connection: SmbConnection) {
        context.smbDataStore.edit { preferences ->
            preferences[serverKey] = connection.server
            preferences[shareKey] = connection.share
            preferences[usernameKey] = connection.username
            preferences[passwordKey] = connection.password
            preferences[domainKey] = connection.domain
        }
    }

    suspend fun clearPersistedConnection() {
        context.smbDataStore.edit { preferences ->
            preferences.remove(serverKey)
            preferences.remove(shareKey)
            preferences.remove(usernameKey)
            preferences.remove(passwordKey)
            preferences.remove(domainKey)
        }
    }

    suspend fun getRootLocation(connection: SmbConnection): FolderLocation = withContext(Dispatchers.IO) {
        val normalizedConnection = normalizeConnection(connection)
        FolderLocation(
            name = normalizedConnection.share.ifBlank { normalizedConnection.server },
            uri = Uri.parse(SmbUrlUtils.buildRootUrl(normalizedConnection))
        )
    }

    suspend fun getChildren(connection: SmbConnection, directoryUri: Uri): List<FolderEntry> =
        withContext(Dispatchers.IO) {
            val normalizedConnection = normalizeConnection(connection)
            val smbDirectory = SmbFile(
                SmbUrlUtils.ensureDirectoryUrl(directoryUri.toString()),
                createContext(normalizedConnection)
            )

            smbDirectory.listFiles()
                ?.mapNotNull { file ->
                    val isDirectory = file.isDirectory
                    val name = file.name.trimEnd('/').ifBlank { SmbUrlUtils.nameFromUrl(file.url.toString()) }
                    val mediaType = if (isDirectory) null else RemoteMediaMetadata.detectMediaType(name)
                    if (!isDirectory && mediaType == null) {
                        null
                    } else {
                        FolderEntry(
                            name = name,
                            uri = Uri.parse(if (isDirectory) SmbUrlUtils.ensureDirectoryUrl(file.url.toString()) else file.url.toString()),
                            isDirectory = isDirectory,
                            mediaType = mediaType,
                            mimeType = if (isDirectory) "" else RemoteMediaMetadata.guessMimeType(name, mediaType),
                            size = if (isDirectory) 0 else file.length(),
                            modifiedTime = file.lastModified()
                        )
                    }
                }
                ?.sortedWith(
                    compareBy<FolderEntry> { !it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
                .orEmpty()
        }

    fun openFile(connection: SmbConnection, fileUri: Uri): SmbOpenFile {
        val normalizedConnection = normalizeConnection(connection)
        val file = SmbFile(fileUri.toString(), createContext(normalizedConnection))
        return SmbOpenFile(
            file = file,
            randomAccessFile = SmbRandomAccessFile(file, "r")
        )
    }

    private fun createContext(connection: SmbConnection): CIFSContext {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.disableSMB1", "true")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
        }
        var context: CIFSContext = BaseContext(PropertyConfiguration(properties))
        if (connection.username.isNotBlank() || connection.password.isNotBlank() || connection.domain.isNotBlank()) {
            context = context.withCredentials(
                NtlmPasswordAuthenticator(connection.domain, connection.username, connection.password)
            )
        }
        return context
    }
}
