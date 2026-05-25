package org.acoustixaudio.casttobrowser.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.folderBrowserDataStore by preferencesDataStore(name = "folder_browser")

data class FolderEntry(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val mediaType: MediaType? = null,
    val mimeType: String = "",
    val size: Long = 0,
    val modifiedTime: Long = 0
)

data class FolderLocation(
    val name: String,
    val uri: Uri
)

class FolderBrowserRepository(private val context: Context) {
    private val selectedTreeUriKey = stringPreferencesKey("selected_tree_uri")

    suspend fun getPersistedTreeUri(): Uri? {
        val preferences = context.folderBrowserDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        return preferences[selectedTreeUriKey]?.let(Uri::parse)
    }

    suspend fun persistTreeUri(uri: Uri) {
        val previousUri = getPersistedTreeUri()
        if (previousUri != null && previousUri != uri) {
            releasePersistedPermission(previousUri)
        }

        context.folderBrowserDataStore.edit { preferences ->
            preferences[selectedTreeUriKey] = uri.toString()
        }
    }

    suspend fun clearPersistedTreeUri() {
        val previousUri = getPersistedTreeUri()
        if (previousUri != null) {
            releasePersistedPermission(previousUri)
        }

        context.folderBrowserDataStore.edit { preferences ->
            preferences.remove(selectedTreeUriKey)
        }
    }

    suspend fun getRootLocation(treeUri: Uri): FolderLocation = withContext(Dispatchers.IO) {
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        FolderLocation(
            name = queryDocumentName(rootDocumentUri) ?: "Selected Folder",
            uri = rootDocumentUri
        )
    }

    suspend fun getChildren(treeUri: Uri, directoryUri: Uri): List<FolderEntry> = withContext(Dispatchers.IO) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(directoryUri)
        )
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
        )

        val entries = mutableListOf<FolderEntry>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val documentIdColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(documentIdColumn)
                val name = cursor.getString(nameColumn) ?: "Untitled"
                val mimeType = cursor.getString(mimeTypeColumn) ?: ""
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                val isDirectory = mimeType == Document.MIME_TYPE_DIR
                val mediaType = when {
                    mimeType.startsWith("image/") -> MediaType.IMAGE
                    mimeType.startsWith("video/") -> MediaType.VIDEO
                    else -> null
                }

                if (!isDirectory && mediaType == null) {
                    continue
                }

                entries.add(
                    FolderEntry(
                        name = name,
                        uri = childUri,
                        isDirectory = isDirectory,
                        mediaType = mediaType,
                        mimeType = mimeType,
                        size = cursor.getLong(sizeColumn),
                        modifiedTime = cursor.getLong(modifiedColumn)
                    )
                )
            }
        }

        entries.sortedWith(
            compareBy<FolderEntry> { !it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun queryDocumentName(documentUri: Uri): String? {
        val projection = arrayOf(Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME))
            }
        }
        return null
    }

    private fun releasePersistedPermission(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
    }
}
