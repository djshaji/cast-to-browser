package org.acoustixaudio.casttobrowser.server

import android.content.Context

object CastServerManager {
    private var ktorServer: KtorServer? = null
    private var isStarted = false

    @Synchronized
    fun start(context: Context) {
        if (isStarted) {
            return
        }

        val appContext = context.applicationContext
        val ip = NetworkUtils.getLocalIpAddress()
        ServerState.setServerIp(ip)
        ServerState.setServerError(null)

        val server = KtorServer(appContext)
        val startedPort = findAvailablePort(server)
        if (startedPort != null) {
            ktorServer = server
            ServerState.setServerPort(startedPort)
            isStarted = true
        } else {
            server.stop()
            ServerState.setServerError("Could not find an available port to start the server.")
        }
    }

    @Synchronized
    fun stop() {
        ktorServer?.stop()
        ktorServer = null
        isStarted = false
        ServerState.setServerIp(null)
        ServerState.setServerError(null)
    }

    private fun findAvailablePort(server: KtorServer): Int? {
        var currentPort = DEFAULT_PORT
        repeat(MAX_PORT_TRIES) {
            try {
                server.start(currentPort)
                return currentPort
            } catch (error: Exception) {
                if (
                    error is java.net.BindException ||
                    error.message?.contains("Address already in use", ignoreCase = true) == true
                ) {
                    currentPort++
                } else {
                    ServerState.setServerError(error.message ?: "Failed to start server")
                    return null
                }
            }
        }
        return null
    }

    private const val DEFAULT_PORT = 8080
    private const val MAX_PORT_TRIES = 10
}
