package dev.eknath.GymJournal.model.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

private val VALID_TYPES = setOf("INJURY", "MEDICATION", "NOTE")
private val VALID_SEVERITIES = setOf("LOW", "MEDIUM", "HIGH", "")

data class CreateGymLogRequest(
    @field:NotBlank val logDate: String,     // YYYY-MM-DD
    @field:NotBlank val type: String,        // INJURY | MEDICATION | NOTE
    @field:NotBlank val title: String,
    val description: String = "",
    val severity: String = ""               // LOW | MEDIUM | HIGH | "" (not set)
)

data class UpdateGymLogRequest(
    val title: String? = null,
    val description: String? = null,
    val severity: String? = null
)

data class GymLogResponse(
    val id: String,
    val logDate: String,
    val type: String,
    val title: String,
    val description: String,
    val severity: String,
    val createdAt: String
)
