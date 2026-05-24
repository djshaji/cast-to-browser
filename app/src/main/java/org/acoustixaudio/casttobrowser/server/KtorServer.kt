package org.acoustixaudio.casttobrowser.server

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ControlMessage(val type: String, val data: String? = null)

class KtorServer(private val context: Context) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

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
                val mediaUrl = media?.let { "/media/${it.id}" } ?: ""
                val mediaType = media?.type?.name ?: ""

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
                            ${if (media?.type == org.acoustixaudio.casttobrowser.data.MediaType.VIDEO) """<video id="player" controls autoplay><source src="$mediaUrl" type="video/mp4"></video>""" else if (media?.type == org.acoustixaudio.casttobrowser.data.MediaType.IMAGE) """<img id="player" src="$mediaUrl">""" else ""}
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

            get("/media/{id}") {
                val idString = call.parameters["id"]
                val id = idString?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid Media ID")
                    return@get
                }

                // Try to find if it's a video or image
                val videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                var uriToServe: Uri? = null
                var contentType = ContentType.Video.Any

                if (exists(videoUri)) {
                    uriToServe = videoUri
                    contentType = ContentType.Video.Any
                } else if (exists(imageUri)) {
                    uriToServe = imageUri
                    contentType = ContentType.Image.Any
                }

                if (uriToServe != null) {
                    try {
                        val descriptor = context.contentResolver.openAssetFileDescriptor(uriToServe, "r")
                        if (descriptor != null) {
                            val length = descriptor.length
                            val inputStream = descriptor.createInputStream()
                            call.respond(object : OutgoingContent.ReadChannelContent() {
                                override val contentLength: Long = length
                                override val contentType: ContentType = contentType
                                override fun readFrom() = inputStream.toByteReadChannel()
                            })
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

    fun stop() {
        server?.stop(1000, 5000)
    }
}
