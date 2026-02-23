package dev.eknath.GymJournal.model.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class LogWaterRequest(
    @field:Min(1) val amountMl: Int,
    val logDateTime: String? = null,   // optional; defaults to now if not provided
    val notes: String = ""
)

data class UpdateWaterEntryRequest(
    @field:Min(1) val amountMl: Int? = null,
    val notes: String? = null
)

data class WaterEntryResponse(
    val id: Long,
    val logDateTime: String,
    val amountMl: Int,
    val notes: String
)

data class DailyWaterResponse(
    val date: String,
    val totalMl: Int,
    val goalMl: Int,
    val progressPercent: Int,
    val entries: List<WaterEntryResponse>
)

data class WaterHistoryResponse(
    val date: String,
    val totalMl: Int,
    val goalMl: Int,
    val progressPercent: Int
)
