package org.acoustixaudio.casttobrowser.data

sealed interface RemoteMediaAccess

data class WebDavRemoteAccess(
    val username: String = "",
    val password: String = ""
) : RemoteMediaAccess

data class SmbRemoteAccess(
    val domain: String = "",
    val username: String = "",
    val password: String = ""
) : RemoteMediaAccess

internal object RemoteMediaMetadata {
    fun detectMediaType(name: String, mimeType: String = ""): MediaType? {
        val normalizedMimeType = mimeType.lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            normalizedMimeType.startsWith("image/") -> MediaType.IMAGE
            normalizedMimeType.startsWith("video/") -> MediaType.VIDEO
            extension in IMAGE_EXTENSIONS -> MediaType.IMAGE
            extension in VIDEO_EXTENSIONS -> MediaType.VIDEO
            else -> null
        }
    }

    fun guessMimeType(name: String, mediaType: MediaType?): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            extension in VIDEO_MIME_TYPES -> VIDEO_MIME_TYPES.getValue(extension)
            extension in IMAGE_MIME_TYPES -> IMAGE_MIME_TYPES.getValue(extension)
            mediaType == MediaType.VIDEO -> "video/*"
            mediaType == MediaType.IMAGE -> "image/*"
            else -> ""
        }
    }

    private val IMAGE_EXTENSIONS = setOf("bmp", "gif", "heic", "heif", "jpeg", "jpg", "png", "webp")
    private val VIDEO_EXTENSIONS = setOf("avi", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "webm")
    private val IMAGE_MIME_TYPES = mapOf(
        "bmp" to "image/bmp",
        "gif" to "image/gif",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "jpeg" to "image/jpeg",
        "jpg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp"
    )
    private val VIDEO_MIME_TYPES = mapOf(
        "avi" to "video/x-msvideo",
        "m4v" to "video/x-m4v",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "mp4" to "video/mp4",
        "mpeg" to "video/mpeg",
        "mpg" to "video/mpeg",
        "webm" to "video/webm"
    )
}
