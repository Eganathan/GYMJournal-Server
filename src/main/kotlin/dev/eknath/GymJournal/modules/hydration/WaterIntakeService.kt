package dev.eknath.GymJournal.modules.hydration

import dev.eknath.GymJournal.model.domain.WaterIntakeEntry
import dev.eknath.GymJournal.model.dto.*
import org.springframework.stereotype.Service

private const val DEFAULT_DAILY_GOAL_ML = 2500

@Service
class WaterIntakeService(private val repository: WaterIntakeRepository) {

    fun logWater(userId: Long, request: LogWaterRequest): WaterEntryResponse {
        val entry = WaterIntakeEntry(
            userId   = userId,
            amountMl = request.amountMl,
            notes    = request.notes
            // CREATEDTIME is set automatically by Catalyst on insert — no logDateTime needed
        )
        return repository.save(entry).toResponse()
    }

    fun getDailySummary(userId: Long, date: String): DailyWaterResponse {
        val entries = repository.findEntriesForDate(userId, date)
        val totalMl = entries.sumOf { it.amountMl }
        return DailyWaterResponse(
            date            = date,
            totalMl         = totalMl,
            goalMl          = DEFAULT_DAILY_GOAL_ML,
            progressPercent = ((totalMl.toDouble() / DEFAULT_DAILY_GOAL_ML) * 100).toInt().coerceAtMost(100),
            entries         = entries.map { it.toResponse() }
        )
    }

    fun getHistory(userId: Long, startDate: String, endDate: String): List<WaterHistoryResponse> {
        val entries = repository.findEntriesForDateRange(userId, startDate, endDate)
        return entries
            .groupBy { it.createdAt.substring(0, 10) }   // group by YYYY-MM-DD from CREATEDTIME
            .map { (date, dayEntries) ->
                val totalMl = dayEntries.sumOf { it.amountMl }
                WaterHistoryResponse(
                    date            = date,
                    totalMl         = totalMl,
                    goalMl          = DEFAULT_DAILY_GOAL_ML,
                    progressPercent = ((totalMl.toDouble() / DEFAULT_DAILY_GOAL_ML) * 100).toInt().coerceAtMost(100)
                )
            }
            .sortedByDescending { it.date }
    }

    fun updateEntry(userId: Long, id: Long, request: UpdateWaterEntryRequest): WaterEntryResponse {
        val existing = repository.findById(id)
            ?: throw NoSuchElementException("Water entry $id not found")
        if (existing.userId != userId) throw IllegalAccessException("Entry does not belong to this user")

        val updated = existing.copy(
            amountMl = request.amountMl ?: existing.amountMl,
            notes    = request.notes    ?: existing.notes
        )
        repository.update(id, updated)
        return (repository.findById(id) ?: updated).toResponse()
    }

    fun deleteEntry(userId: Long, id: Long) {
        val existing = repository.findById(id)
            ?: throw NoSuchElementException("Water entry $id not found")
        if (existing.userId != userId) throw IllegalAccessException("Entry does not belong to this user")
        repository.delete(id)
    }

    private fun WaterIntakeEntry.toResponse() = WaterEntryResponse(
        id          = (id ?: 0).toString(),
        logDateTime = createdAt.replace(" ", "T"),   // CREATEDTIME "yyyy-MM-dd HH:mm:ss" → ISO-8601
        amountMl    = amountMl,
        notes       = notes
    )
}
