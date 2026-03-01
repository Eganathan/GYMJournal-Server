package dev.eknath.GymJournal.model.dto

import dev.eknath.GymJournal.model.domain.RoutineItem
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

// ── Request DTOs ─────────────────────────────────────────────────────────────

data class CreateRoutineRequest(
    @field:NotBlank @field:Size(max = 100)
    val name: String,

    val description: String = "",

    @field:NotEmpty
    val items: List<RoutineItem>,

    val estimatedMinutes: Int = 0,
    val tags: List<String> = emptyList(),
    val isPublic: Boolean = false
)

data class UpdateRoutineRequest(
    @field:Size(max = 100)
    val name: String? = null,

    val description: String? = null,
    val items: List<RoutineItem>? = null,
    val estimatedMinutes: Int? = null,
    val tags: List<String>? = null,
    val isPublic: Boolean? = null
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

/**
 * Full routine detail — returned by GET /{id} and POST (create/clone).
 */
data class RoutineResponse(
    val id: Long,
    val name: String,
    val description: String,
    val items: List<RoutineItem>,
    val estimatedMinutes: Int,
    val tags: List<String>,
    val isPublic: Boolean,
    val createdBy: String,
    val createdAt: String,              // ISO-8601
    val updatedAt: String               // ISO-8601
)

/**
 * Compact routine item for paginated list responses.
 */
data class RoutineSummaryResponse(
    val id: Long,
    val name: String,
    val description: String,
    val estimatedMinutes: Int,
    val tags: List<String>,
    val itemCount: Int,
    val isPublic: Boolean,
    val createdBy: String,
    val updatedAt: String               // ISO-8601
)
