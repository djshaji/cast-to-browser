package org.acoustixaudio.casttobrowser.data

import android.net.Uri

enum class MediaType {
    VIDEO, IMAGE
}

data class MediaItem(
    val id: Long,
    val name: String,
    val uri: Uri,
    val type: MediaType,
    val duration: Long = 0,
    val size: Long = 0,
    val modifiedTime: Long = 0,
    val mimeType: String = "",
    val remoteAccess: RemoteMediaAccess? = null
)
