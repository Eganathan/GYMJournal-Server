package dev.eknath.GymJournal.modules.media

import dev.eknath.GymJournal.model.dto.UploadResponse
import dev.eknath.GymJournal.util.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * POST /api/v1/media/upload
 *
 * Generic file upload endpoint. Uploads the file to Catalyst FileStore and returns the
 * permanent download URL. The client is responsible for attaching the returned URL to the
 * target entity via that entity's own update endpoint (e.g. PUT /api/v1/exercises/{id}).
 *
 * Form params:
 *   file   (MultipartFile) — required
 *   folder (String?)       — optional; "exercises" | "routines"; defaults to "misc"
 *
 * FileStore folders must be pre-created in the Catalyst Console: exercises, routines, misc.
 */
@RestController
@RequestMapping("/api/v1/media")
class MediaController(private val service: MediaService) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) folder: String?
    ): ApiResponse<UploadResponse> = ApiResponse.ok(service.upload(file, folder))
}
