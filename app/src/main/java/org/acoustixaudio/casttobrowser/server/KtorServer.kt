package org.acoustixaudio.casttobrowser.server

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.MediaStore
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.acoustixaudio.casttobrowser.data.MediaType
import org.acoustixaudio.casttobrowser.data.SmbConnection
import org.acoustixaudio.casttobrowser.data.SmbRemoteAccess
import org.acoustixaudio.casttobrowser.data.SmbRepository
import org.acoustixaudio.casttobrowser.data.WebDavRemoteAccess
import java.io.EOFException
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ControlMessage(val type: String, val data: String? = null)

class KtorServer(private val context: Context) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val httpClient = OkHttpClient()
    private val smbRepository = SmbRepository(context)

    /**
     * Starts the server on the specified port.
     * Throws an exception if the port is already in use.
     */
    fun start(port: Int = 8080) {
        server = embeddedServer(Netty, port = port) {
            module()
        }.start(wait = false)
    }

    fun Application.module() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        install(PartialContent)

        routing {
            get("/") {
                val media = ServerState.currentMedia.value
                val mediaUrl = media?.let { "/media/${it.type.name.lowercase()}/${it.id}" } ?: ""

                call.respondText(
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Cast to Browser Player</title>
                        <style>
                            body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000; overflow: hidden; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; }
                            #container { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 100%; height: 100%; position: relative; }
                            video, img { max-width: 100%; max-height: 100%; object-fit: contain; box-shadow: 0 0 50px rgba(0,0,0,0.5); }
                            #status { position: absolute; bottom: 20px; left: 20px; color: rgba(255,255,255,0.7); font-size: 14px; background: rgba(0,0,0,0.5); padding: 5px 12px; border-radius: 20px; pointer-events: none; opacity: 0; transition: opacity 0.5s; }
                            .visible #status { opacity: 1; }
                            #no-media { color: #555; font-size: 24px; text-transform: uppercase; letter-spacing: 2px; }
                        </style>
                    </head>
                    <body>
                        <div id="container">
                            ${if (media == null) "<div id='no-media'>Waiting for media...</div>" else ""}
                            ${if (media?.type == MediaType.VIDEO) """<video id="player" controls autoplay><source src="$mediaUrl" type="video/mp4"></video>""" else if (media?.type == MediaType.IMAGE) """<img id="player" src="$mediaUrl">""" else ""}
                            <div id="status">Disconnected</div>
                        </div>

                        <script>
                            const player = document.getElementById('player');
                            const status = document.getElementById('status');
                            const container = document.getElementById('container');
                            let socket;
                            let telemetryInterval;

                            function connect() {
                                socket = new WebSocket('ws://' + window.location.host + '/control');
                                
                                socket.onopen = () => {
                                    status.innerText = 'Connected';
                                    container.classList.add('visible');
                                    startTelemetry();
                                };

                                socket.onclose = () => {
                                    status.innerText = 'Disconnected';
                                    container.classList.remove('visible');
                                    stopTelemetry();
                                    setTimeout(connect, 3000);
                                };

                                socket.onmessage = (event) => {
                                    const msg = JSON.parse(event.data);
                                    console.log('Received:', msg);
                                    
                                    if (!player) {
                                        if (msg.type === 'LOAD') window.location.reload();
                                        return;
                                    }

                                    switch(msg.type) {
                                        case 'LOAD':
                                            window.location.reload();
                                            break;
                                        case 'PLAY':
                                            if (player.play) player.play();
                                            break;
                                        case 'PAUSE':
                                            if (player.pause) player.pause();
                                            break;
                                        case 'SEEK':
                                            if (player.currentTime !== undefined) {
                                                player.currentTime = parseFloat(msg.data);
                                            }
                                            break;
                                    }
                                };
                            }

                            function startTelemetry() {
                                if (telemetryInterval) clearInterval(telemetryInterval);
                                telemetryInterval = setInterval(() => {
                                    if (!socket || socket.readyState !== WebSocket.OPEN || !player) return;
                                    
                                    const telemetry = {
                                        type: 'TELEMETRY',
                                        data: JSON.stringify({
                                            currentTime: player.currentTime || 0,
                                            duration: player.duration || 0,
                                            isPlaying: player.paused === false
                                        })
                                    };
                                    socket.send(JSON.stringify(telemetry));
                                }, 1000);
                            }

                            function stopTelemetry() {
                                clearInterval(telemetryInterval);
                            }

                            connect();

                            // Auto hide status
                            document.addEventListener('mousemove', () => {
                                container.classList.add('visible');
                                clearTimeout(window.statusTimeout);
                                window.statusTimeout = setTimeout(() => container.classList.remove('visible'), 3000);
                            });
                        </script>
                    </body>
                    </html>
                    """.trimIndent(),
                    ContentType.Text.Html
                )
            }

            get("/media/{type}/{id}") {
                val mediaType = when (call.parameters["type"]?.lowercase()) {
                    "video" -> MediaType.VIDEO
                    "image" -> MediaType.IMAGE
                    else -> null
                }
                val idString = call.parameters["id"]
                val id = idString?.toLongOrNull()
                if (mediaType == null || id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid Media ID")
                    return@get
                }

                val resolvedMediaType = mediaType
                val currentMedia = ServerState.currentMedia.value
                val uriToServe = if (
                    currentMedia != null &&
                    currentMedia.id == id &&
                    currentMedia.type == resolvedMediaType
                ) {
                    currentMedia.uri
                } else if (resolvedMediaType == MediaType.VIDEO) {
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                } else {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
                val defaultContentType = if (resolvedMediaType == MediaType.VIDEO) {
                    ContentType.Video.Any
                } else {
                    ContentType.Image.Any
                }

                if (
                    currentMedia != null &&
                    currentMedia.id == id &&
                    currentMedia.type == resolvedMediaType &&
                    isRemoteUri(currentMedia.uri)
                ) {
                    proxyRemoteMedia(call, currentMedia, defaultContentType)
                    return@get
                }

                if (exists(uriToServe)) {
                    try {
                        val descriptor = context.contentResolver.openAssetFileDescriptor(uriToServe, "r")
                        if (descriptor != null) {
                            val length = resolveContentLength(uriToServe, descriptor.length)
                            val inputStream = descriptor.createInputStream()
                            val resolvedContentType = context.contentResolver.getType(uriToServe)
                                ?.let(ContentType::parse)
                                ?: defaultContentType
                            if (length != null) {
                                call.respond(object : OutgoingContent.ReadChannelContent() {
                                    override val contentLength: Long = length
                                    override val contentType: ContentType = resolvedContentType
                                    override fun readFrom() = inputStream.toByteReadChannel()
                                })
                            } else {
                                call.respond(object : OutgoingContent.ReadChannelContent() {
                                    override val contentType: ContentType = resolvedContentType
                                    override fun readFrom() = inputStream.toByteReadChannel()
                                })
                            }
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown error")
                    }
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            webSocket("/control") {
                launch {
                    ServerState.mediaLoadEvent.collectLatest { media ->
                        send(Frame.Text(Json.encodeToString(ControlMessage("LOAD", media.id.toString()))))
                    }
                }
                
                launch {
                    ServerState.commandFlow.collect { command ->
                        send(Frame.Text(Json.encodeToString(command)))
                    }
                }

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        try {
                            val text = frame.readText()
                            val msg = Json.decodeFromString<ControlMessage>(text)
                            if (msg.type == "TELEMETRY" && msg.data != null) {
                                val telemetryData = Json.decodeFromString<TelemetryData>(msg.data)
                                ServerState.updateTelemetry(telemetryData)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun exists(uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveContentLength(uri: Uri, descriptorLength: Long): Long? {
        if (descriptorLength >= 0) {
            return descriptorLength
        }

        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    val size = cursor.getLong(sizeIndex)
                    if (size >= 0) {
                        return size
                    }
                }
            }
        }

        return null
    }

    private suspend fun proxyRemoteMedia(
        call: ApplicationCall,
        media: org.acoustixaudio.casttobrowser.data.MediaItem,
        defaultContentType: ContentType
    ) {
        when (val access = media.remoteAccess) {
            is SmbRemoteAccess -> proxySmbMedia(call, media.uri, access, media.mimeType, defaultContentType)
            is WebDavRemoteAccess, null -> proxyHttpMedia(call, media.uri, access as? WebDavRemoteAccess, media.mimeType, defaultContentType)
        }
    }

    private suspend fun proxyHttpMedia(
        call: ApplicationCall,
        uri: Uri,
        access: WebDavRemoteAccess?,
        declaredMimeType: String,
        defaultContentType: ContentType
    ) {
        val request = Request.Builder()
            .url(uri.toString())
            .get()
            .apply {
                call.request.headers[HttpHeaders.Range]?.let { header(HttpHeaders.Range, it) }
                if (access != null && (access.username.isNotBlank() || access.password.isNotBlank())) {
                    header(HttpHeaders.Authorization, Credentials.basic(access.username, access.password))
                }
            }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != HttpStatusCode.PartialContent.value) {
                call.respond(
                    HttpStatusCode.fromValue(response.code),
                    response.message.ifBlank { "Could not fetch remote media." }
                )
                return
            }

            val body = response.body ?: run {
                call.respond(HttpStatusCode.BadGateway, "Remote server returned an empty body.")
                return
            }

            response.header(HttpHeaders.AcceptRanges)?.let {
                call.response.headers.append(HttpHeaders.AcceptRanges, it)
            }
            response.header(HttpHeaders.ContentRange)?.let {
                call.response.headers.append(HttpHeaders.ContentRange, it)
            }
            response.header(HttpHeaders.ContentLength)?.let {
                call.response.headers.append(HttpHeaders.ContentLength, it)
            }

            val contentType = response.header(HttpHeaders.ContentType)
                ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                ?: declaredMimeType.takeIf { it.isNotBlank() }?.let(ContentType::parse)
                ?: defaultContentType
            val status = HttpStatusCode.fromValue(response.code)

            call.respondOutputStream(contentType = contentType, status = status) {
                body.byteStream().use { input ->
                    input.copyTo(this)
                }
            }
        }
    }

    private suspend fun proxySmbMedia(
        call: ApplicationCall,
        uri: Uri,
        access: SmbRemoteAccess,
        declaredMimeType: String,
        defaultContentType: ContentType
    ) {
        val connection = SmbConnection(
            server = uri.host.orEmpty(),
            share = uri.pathSegments.firstOrNull().orEmpty(),
            username = access.username,
            password = access.password,
            domain = access.domain
        )

        smbRepository.openFile(connection, uri).use { smbFile ->
            val totalLength = smbFile.file.length()
            val range = parseRangeHeader(call.request.headers[HttpHeaders.Range], totalLength)

            if (range == InvalidRange) {
                call.response.headers.append(HttpHeaders.ContentRange, "bytes */$totalLength")
                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                return
            }

            val selectedRange = range as? LongRange
            val contentType = declaredMimeType.takeIf { it.isNotBlank() }?.let(ContentType::parse) ?: defaultContentType
            val contentLength = selectedRange?.let { it.last - it.first + 1 } ?: totalLength
            val status = if (selectedRange != null) HttpStatusCode.PartialContent else HttpStatusCode.OK

            call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
            call.response.headers.append(HttpHeaders.ContentLength, contentLength.toString())
            if (selectedRange != null) {
                call.response.headers.append(
                    HttpHeaders.ContentRange,
                    "bytes ${selectedRange.first}-${selectedRange.last}/$totalLength"
                )
            }

            call.respondOutputStream(contentType = contentType, status = status) {
                val randomAccessFile = smbFile.randomAccessFile
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                randomAccessFile.seek(selectedRange?.first ?: 0L)
                var remaining = contentLength

                while (remaining > 0) {
                    val bytesToRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val bytesRead = try {
                        randomAccessFile.read(buffer, 0, bytesToRead)
                    } catch (_: EOFException) {
                        -1
                    }
                    if (bytesRead <= 0) {
                        break
                    }
                    write(buffer, 0, bytesRead)
                    remaining -= bytesRead
                }
            }
        }
    }

    private fun isRemoteUri(uri: Uri): Boolean {
        return uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("smb", ignoreCase = true)
    }

    private fun parseRangeHeader(rangeHeader: String?, totalLength: Long): Any? {
        if (rangeHeader.isNullOrBlank()) {
            return null
        }
        val match = Regex("""bytes=(\d*)-(\d*)""").matchEntire(rangeHeader.trim()) ?: return InvalidRange
        val startText = match.groupValues[1]
        val endText = match.groupValues[2]

        if (startText.isEmpty() && endText.isEmpty()) {
            return InvalidRange
        }

        return if (startText.isEmpty()) {
            val suffixLength = endText.toLongOrNull() ?: return InvalidRange
            if (suffixLength <= 0) {
                InvalidRange
            } else {
                val start = (totalLength - suffixLength).coerceAtLeast(0)
                start..(totalLength - 1)
            }
        } else {
            val start = startText.toLongOrNull() ?: return InvalidRange
            val end = if (endText.isEmpty()) totalLength - 1 else endText.toLongOrNull() ?: return InvalidRange
            if (start < 0 || start >= totalLength || end < start) {
                InvalidRange
            } else {
                start..minOf(end, totalLength - 1)
            }
        }
    }

    fun stop() {
        server?.stop(1000, 5000)
    }

    private object InvalidRange
}
