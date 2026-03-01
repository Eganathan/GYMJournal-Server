package dev.eknath.GymJournal.util

import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    // Malformed or missing JSON request body
    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ApiResponse<Nothing> =
        ApiResponse.error("INVALID_REQUEST", "Request body is missing or contains invalid JSON")

    // @Valid constraint violations on @RequestBody fields
    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ApiResponse<Nothing> {
        val message = ex.bindingResult.allErrors
            .joinToString("; ") { error ->
                val field = (error as? FieldError)?.field
                if (field != null) "$field: ${error.defaultMessage}" else error.defaultMessage ?: "invalid"
            }
        return ApiResponse.error("VALIDATION_ERROR", message)
    }

    // Entry not found (thrown by services on missing records)
    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: NoSuchElementException): ApiResponse<Nothing> =
        ApiResponse.error("NOT_FOUND", ex.message ?: "Resource not found")

    // Ownership check failed (thrown by services when user doesn't own the resource)
    @ExceptionHandler(IllegalAccessException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleForbidden(ex: IllegalAccessException): ApiResponse<Nothing> =
        ApiResponse.error("FORBIDDEN", ex.message ?: "Access denied")

    // Business rule violation (e.g. computed metric type submitted, invalid date format, duplicate custom key)
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(ex: IllegalArgumentException): ApiResponse<Nothing> =
        ApiResponse.error("INVALID_REQUEST", ex.message ?: "Invalid request")
}
