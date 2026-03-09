package dev.eknath.GymJournal.model.dto

import jakarta.validation.constraints.Min

data class LogWaterRequest(
    @field:Min(1) val amountMl: Int,
    val notes: String = ""
)

data class UpdateWaterEntryRequest(
    @field:Min(1) val amountMl: Int? = null,
    val notes: String? = null
)

data class WaterEntryResponse(
    val id: String,
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

data class SetGoalRequest(
    @field:Min(1) val goalMl: Int
)

data class GoalResponse(
    val goalMl: Int
)
