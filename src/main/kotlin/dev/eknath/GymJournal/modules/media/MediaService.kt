package dev.eknath.GymJournal.modules.media

import dev.eknath.GymJournal.model.dto.UploadResponse
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Validates and orchestrates media uploads to Catalyst FileStore.
 *
 * Allowed types:
 *   Images — image/jpeg, image/png, image/webp  (max 5 MB)
 *   Videos — video/mp4, video/quicktime         (max 50 MB)
 *
 * Allowed folders (coerces unknown values to "misc"):
 *   exercises | routines | misc
 *
 * Returns an [UploadResponse] containing the permanent FileStore download URL.
 * The caller is responsible for persisting that URL on the target entity via the
 * entity's own update endpoint (e.g. PUT /api/v1/exercises/{id} with imageUrl).
 */
@Service
class MediaService(private val fileRepo: CatalystFileRepository) {

    companion object {
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val ALLOWED_VIDEO_TYPES = setOf("video/mp4", "video/quicktime")
        private val ALLOWED_FOLDERS     = setOf("exercises", "routines")
        private const val MAX_IMAGE_BYTES = 5L  * 1024 * 1024   // 5 MB
        private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024   // 50 MB
    }

    fun upload(file: MultipartFile, folder: String?): UploadResponse {
        if (file.isEmpty)
            throw IllegalArgumentException("File must not be empty")

        val mime = file.contentType?.lowercase()?.trim()
            ?: throw IllegalArgumentException("File content type is missing")

        val maxBytes = when {
            mime in ALLOWED_IMAGE_TYPES -> MAX_IMAGE_BYTES
            mime in ALLOWED_VIDEO_TYPES -> MAX_VIDEO_BYTES
            else -> throw IllegalArgumentException(
                "Unsupported file type '$mime'. " +
                "Allowed: image/jpeg, image/png, image/webp, video/mp4, video/quicktime"
            )
        }

        if (file.size > maxBytes)
            throw IllegalArgumentException(
                "File too large: ${file.size} bytes exceeds the " +
                "${maxBytes / (1024 * 1024)} MB limit for type '$mime'"
            )

        val safeFolder = if (folder?.lowercase() in ALLOWED_FOLDERS) folder!!.lowercase() else "misc"

        val info = try {
            fileRepo.upload(file, safeFolder)
        } catch (e: Exception) {
            throw RuntimeException("Upload failed: ${e.message}", e)
        }

        return UploadResponse(
            url       = info.url,
            fileId    = info.fileId,
            fileName  = info.fileName,
            mimeType  = mime,
            sizeBytes = info.sizeBytes
        )
    }
}
