package org.acoustixaudio.casttobrowser.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

private val Context.webDavDataStore by preferencesDataStore(name = "webdav_browser")

data class WebDavConnection(
    val baseUrl: String,
    val username: String = "",
    val password: String = ""
)

internal data class WebDavResource(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val mediaType: MediaType? = null,
    val mimeType: String = "",
    val size: Long = 0,
    val modifiedTime: Long = 0
)

internal object WebDavUrlUtils {
    fun normalize(url: String): String {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) { "Enter a WebDAV URL." }

        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        val parsed = URI(withScheme)
        val normalizedPath = parsed.normalize().path.orEmpty().ifBlank { "/" }
        val pathWithSlash = if (normalizedPath.endsWith("/")) normalizedPath else "$normalizedPath/"

        return URI(
            parsed.scheme,
            parsed.userInfo,
            parsed.host,
            parsed.port,
            pathWithSlash,
            parsed.query,
            parsed.fragment
        ).toString()
    }

    fun resolve(baseUrl: String, href: String): String {
        val base = URI(normalize(baseUrl))
        val resolved = if (href.isBlank()) {
            base
        } else {
            base.resolve(URI(href.trim()))
        }.normalize()
        return resolved.toString()
    }

    fun ensureDirectoryUrl(url: String): String {
        val parsed = URI(url)
        val path = parsed.normalize().path.orEmpty().ifBlank { "/" }
        val pathWithSlash = if (path.endsWith("/")) path else "$path/"
        return URI(
            parsed.scheme,
            parsed.userInfo,
            parsed.host,
            parsed.port,
            pathWithSlash,
            parsed.query,
            parsed.fragment
        ).toString()
    }

    fun directoryKey(url: String): String = ensureDirectoryUrl(url).removeSuffix("/")

    fun nameFromUrl(url: String, isDirectory: Boolean): String {
        val normalized = if (isDirectory) ensureDirectoryUrl(url) else url
        val parsed = URI(normalized)
        val segments = parsed.path.orEmpty()
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
        val rawName = segments.lastOrNull()
        return when {
            rawName != null -> URLDecoder.decode(rawName, "UTF-8")
            !parsed.host.isNullOrBlank() -> parsed.host
            else -> "WebDAV"
        }
    }
}

internal object WebDavXmlParser {
    fun parseDirectoryListing(
        baseUrl: String,
        currentDirectoryUrl: String,
        xml: String
    ): List<WebDavResource> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        val responses = document.getElementsByTagNameNS("*", "response")
        val currentDirectoryKey = WebDavUrlUtils.directoryKey(currentDirectoryUrl)
        val resources = mutableListOf<WebDavResource>()

        for (index in 0 until responses.length) {
            val responseElement = responses.item(index) as? Element ?: continue
            val href = responseElement.childText("href") ?: continue
            val resolvedUrl = WebDavUrlUtils.resolve(baseUrl, href)
            val propertyElement = responseElement.firstChildElement("prop")
            val isDirectory = propertyElement?.hasChildElement("collection") == true
            if (isDirectory && WebDavUrlUtils.directoryKey(resolvedUrl) == currentDirectoryKey) {
                continue
            }

            val displayName = propertyElement?.childText("displayname")
            val contentType = propertyElement?.childText("getcontenttype").orEmpty()
            val name = displayName?.takeIf { it.isNotBlank() }
                ?: WebDavUrlUtils.nameFromUrl(resolvedUrl, isDirectory)
            val mediaType = when {
                isDirectory -> null
                else -> RemoteMediaMetadata.detectMediaType(name = name, mimeType = contentType)
            }

            if (!isDirectory && mediaType == null) {
                continue
            }

            resources += WebDavResource(
                name = name,
                url = if (isDirectory) WebDavUrlUtils.ensureDirectoryUrl(resolvedUrl) else resolvedUrl,
                isDirectory = isDirectory,
                mediaType = mediaType,
                mimeType = if (isDirectory) "" else contentType.ifBlank {
                    RemoteMediaMetadata.guessMimeType(name, mediaType)
                },
                size = propertyElement?.childText("getcontentlength")?.toLongOrNull() ?: 0,
                modifiedTime = parseModifiedTime(propertyElement?.childText("getlastmodified"))
            )
        }

        return resources.sortedWith(
            compareBy<WebDavResource> { !it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun parseModifiedTime(value: String?): Long {
        if (value.isNullOrBlank()) {
            return 0
        }

        return runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0)
    }

    private fun Element.firstChildElement(localName: String): Element? {
        return getElementsByTagNameNS("*", localName).item(0) as? Element
    }

    private fun Element.childText(localName: String): String? {
        return firstChildElement(localName)?.textContent?.trim()
    }

    private fun Element.hasChildElement(localName: String): Boolean {
        return getElementsByTagNameNS("*", localName).length > 0
    }

}

class WebDavRepository(private val context: Context) {
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val usernameKey = stringPreferencesKey("username")
    private val passwordKey = stringPreferencesKey("password")
    private val client = OkHttpClient()

    fun normalizeConnection(connection: WebDavConnection): WebDavConnection {
        return connection.copy(baseUrl = WebDavUrlUtils.normalize(connection.baseUrl))
    }

    suspend fun getPersistedConnection(): WebDavConnection? {
        val preferences = context.webDavDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        val baseUrl = preferences[baseUrlKey] ?: return null
        return WebDavConnection(
            baseUrl = baseUrl,
            username = preferences[usernameKey].orEmpty(),
            password = preferences[passwordKey].orEmpty()
        )
    }

    suspend fun persistConnection(connection: WebDavConnection) {
        context.webDavDataStore.edit { preferences ->
            preferences[baseUrlKey] = connection.baseUrl
            preferences[usernameKey] = connection.username
            preferences[passwordKey] = connection.password
        }
    }

    suspend fun clearPersistedConnection() {
        context.webDavDataStore.edit { preferences ->
            preferences.remove(baseUrlKey)
            preferences.remove(usernameKey)
            preferences.remove(passwordKey)
        }
    }

    suspend fun getRootLocation(connection: WebDavConnection): FolderLocation = withContext(Dispatchers.IO) {
        val normalizedConnection = normalizeConnection(connection)
        FolderLocation(
            name = WebDavUrlUtils.nameFromUrl(normalizedConnection.baseUrl, isDirectory = true),
            uri = Uri.parse(normalizedConnection.baseUrl)
        )
    }

    suspend fun getChildren(connection: WebDavConnection, directoryUri: Uri): List<FolderEntry> =
        withContext(Dispatchers.IO) {
            val normalizedConnection = normalizeConnection(connection)
            val directoryUrl = WebDavUrlUtils.ensureDirectoryUrl(directoryUri.toString())
            val request = Request.Builder()
                .url(directoryUrl)
                .header("Depth", "1")
                .method("PROPFIND", DIRECTORY_LIST_REQUEST.toRequestBody(XML_MEDIA_TYPE))
                .applyAuthorization(normalizedConnection)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code != 207 && !response.isSuccessful) {
                    val suffix = response.message.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    throw IOException("WebDAV request failed (${response.code})$suffix")
                }

                val body = response.body?.string()
                    ?: throw IOException("WebDAV server returned an empty response.")
                WebDavXmlParser.parseDirectoryListing(
                    baseUrl = normalizedConnection.baseUrl,
                    currentDirectoryUrl = directoryUrl,
                    xml = body
                ).map { resource ->
                    FolderEntry(
                        name = resource.name,
                        uri = Uri.parse(resource.url),
                        isDirectory = resource.isDirectory,
                        mediaType = resource.mediaType,
                        mimeType = resource.mimeType,
                        size = resource.size,
                        modifiedTime = resource.modifiedTime
                    )
                }
            }
        }

    private fun Request.Builder.applyAuthorization(connection: WebDavConnection): Request.Builder {
        return apply {
            if (connection.username.isNotBlank() || connection.password.isNotBlank()) {
                header("Authorization", Credentials.basic(connection.username, connection.password))
            }
        }
    }

    private companion object {
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        const val DIRECTORY_LIST_REQUEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:displayname />
                <D:getcontentlength />
                <D:getcontenttype />
                <D:getlastmodified />
                <D:resourcetype />
              </D:prop>
            </D:propfind>
        """
    }
}
