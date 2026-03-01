package dev.eknath.GymJournal.modules.media

import com.zc.common.ZCProject
import com.zc.common.ZCProjectConfig
import com.zc.component.files.ZCFile
import org.springframework.stereotype.Repository
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Wraps the Catalyst FileStore SDK (public API only).
 *
 * Public entry-point chain:
 *   ZCFile.getInstance()             → ZCFile  (root handle; reads SDK state set by CatalystAuthFilter)
 *   ZCFile.getFolder(folderName)     → ZCFolder
 *   ZCFolder.uploadFile(java.io.File)→ ZCFileDetail
 *   ZCFolder.deleteFile(Long)
 *
 * Note: ZCFileService is package-private in the SDK — do NOT import it.
 * URL is constructed manually:
 *   https://{projectDomain}/baas/v1/project/{projectId}/filestore/{folderId}/folder/{fileId}/download
 *
 * FileStore folders must be pre-created in the Catalyst Console: exercises, routines, misc.
 */
@Repository
class CatalystFileRepository {

    companion object {
        private val LOGGER = Logger.getLogger(CatalystFileRepository::class.java.name)
    }

    data class UploadedFileInfo(
        val fileId: Long,
        val fileName: String,
        val sizeBytes: Long,
        val url: String
    )

    /**
     * Uploads [file] to the named FileStore folder.
     * Writes the multipart data to a temp file first (ZCFolder.uploadFile only accepts java.io.File),
     * then deletes the temp file in the finally block.
     */
    fun upload(file: MultipartFile, folderName: String): UploadedFileInfo {
        val config = ZCProject.getDefaultProjectConfig()
            ?: throw RuntimeException("Catalyst project config unavailable — SDK may not be initialised")

        LOGGER.log(Level.INFO,
            "[FileStore] Uploading '${file.originalFilename}' (${file.size} bytes) → folder='$folderName'")

        val tempFile = File.createTempFile("gymjournal-upload-", "-${file.originalFilename ?: "file"}")
        return try {
            file.transferTo(tempFile)

            val folder = ZCFile.getInstance().getFolder(folderName)
            val detail = folder.uploadFile(tempFile)
            val url    = buildDownloadUrl(config, folder.folderId, detail.fileId)

            LOGGER.log(Level.INFO, "[FileStore] Upload OK — fileId=${detail.fileId}")
            UploadedFileInfo(
                fileId    = detail.fileId,
                fileName  = detail.fileName,
                sizeBytes = detail.fileSize,
                url       = url
            )
        } catch (e: Throwable) {
            LOGGER.log(Level.SEVERE, "[FileStore] Upload to '$folderName' failed: ${e.message}", e)
            throw e
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Deletes [fileId] from the named folder.
     */
    fun delete(folderName: String, fileId: Long) {
        LOGGER.log(Level.INFO, "[FileStore] Deleting fileId=$fileId from folder='$folderName'")
        try {
            ZCFile.getInstance().getFolder(folderName).deleteFile(fileId)
            LOGGER.log(Level.INFO, "[FileStore] Deleted fileId=$fileId")
        } catch (e: Throwable) {
            LOGGER.log(Level.SEVERE, "[FileStore] Delete fileId=$fileId failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Builds the Catalyst FileStore download URL.
     * Pattern: https://{projectDomain}/baas/v1/project/{projectId}/filestore/{folderId}/folder/{fileId}/download
     */
    private fun buildDownloadUrl(config: ZCProjectConfig, folderId: Long, fileId: Long): String =
        "https://${config.projectDomain}/baas/v1/project/${config.projectId}" +
        "/filestore/$folderId/folder/$fileId/download"
}
