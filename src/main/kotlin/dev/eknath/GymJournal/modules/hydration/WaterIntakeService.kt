package dev.eknath.GymJournal.modules.hydration

import dev.eknath.GymJournal.model.domain.WaterIntakeEntry
import dev.eknath.GymJournal.model.dto.*
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val DEFAULT_DAILY_GOAL_ML = 2500

@Service
class WaterIntakeService(private val repository: WaterIntakeRepository) {

    fun logWater(userId: String, request: LogWaterRequest): WaterEntryResponse {
        val dateTime = request.logDateTime
            ?: LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val entry = WaterIntakeEntry(
            userId = userId,
            logDateTime = dateTime,
            amountMl = request.amountMl,
            notes = request.notes
        )
        return repository.save(entry).toResponse()
    }

    fun getDailySummary(userId: String, date: String): DailyWaterResponse {
        val entries = repository.findEntriesForDate(userId, date)
        val totalMl = entries.sumOf { it.amountMl }
        return DailyWaterResponse(
            date = date,
            totalMl = totalMl,
            goalMl = DEFAULT_DAILY_GOAL_ML,
            progressPercent = ((totalMl.toDouble() / DEFAULT_DAILY_GOAL_ML) * 100).toInt().coerceAtMost(100),
            entries = entries.map { it.toResponse() }
        )
    }

    fun getHistory(userId: String, startDate: String, endDate: String): List<WaterHistoryResponse> {
        val entries = repository.findEntriesForDateRange(userId, startDate, endDate)

        // Group by date and compute daily totals
        return entries
            .groupBy { it.logDateTime.substringBefore("T") }
            .map { (date, dayEntries) ->
                val totalMl = dayEntries.sumOf { it.amountMl }
                WaterHistoryResponse(
                    date = date,
                    totalMl = totalMl,
                    goalMl = DEFAULT_DAILY_GOAL_ML,
                    progressPercent = ((totalMl.toDouble() / DEFAULT_DAILY_GOAL_ML) * 100).toInt().coerceAtMost(100)
                )
            }
            .sortedByDescending { it.date }
    }

    fun updateEntry(userId: String, id: Long, request: UpdateWaterEntryRequest): WaterEntryResponse {
        val existing = repository.findById(id)
            ?: throw NoSuchElementException("Water entry $id not found")
        if (existing.userId != userId) throw IllegalAccessException("Entry does not belong to this user")

        val updated = existing.copy(
            amountMl = request.amountMl ?: existing.amountMl,
            notes = request.notes ?: existing.notes
        )
        repository.update(id, updated)
        return updated.toResponse()
    }

    fun deleteEntry(userId: String, id: Long) {
        val existing = repository.findById(id)
            ?: throw NoSuchElementException("Water entry $id not found")
        if (existing.userId != userId) throw IllegalAccessException("Entry does not belong to this user")
        repository.delete(id)
    }

    private fun WaterIntakeEntry.toResponse() = WaterEntryResponse(
        id = id ?: 0,
        logDateTime = logDateTime,
        amountMl = amountMl,
        notes = notes
    )
}
