package dev.eknath.GymJournal.model.dto

/**
 * Response returned by POST /api/v1/media/upload.
 *
 * [url]       — permanent Catalyst FileStore public download URL; store this on the entity.
 * [fileId]    — FileStore file ID (use to delete the file later if needed).
 * [fileName]  — original filename as stored in FileStore.
 * [mimeType]  — MIME type of the uploaded file (echoed from the request part header).
 * [sizeBytes] — file size in bytes as reported by FileStore after upload.
 */
data class UploadResponse(
    val url: String,
    val fileId: Long,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)
