package dev.eknath.GymJournal.modules.hydration

import dev.eknath.GymJournal.model.domain.WaterIntakeEntry
import dev.eknath.GymJournal.model.dto.*
import org.springframework.stereotype.Service

private const val DEFAULT_DAILY_GOAL_ML = 2500

@Service
class WaterIntakeService(
    private val repository: WaterIntakeRepository,
    private val goalRepository: HydrationGoalRepository
) {

    fun logWater(userId: Long, request: LogWaterRequest): WaterEntryResponse {
        val entry = WaterIntakeEntry(
            userId   = userId,
            amountMl = request.amountMl,
            notes    = request.notes
        )
        return repository.save(entry).toResponse()
    }

    fun getGoal(userId: Long): GoalResponse =
        GoalResponse(goalMl = goalRepository.findByUser(userId) ?: DEFAULT_DAILY_GOAL_ML)

    fun setGoal(userId: Long, goalMl: Int): GoalResponse {
        goalRepository.upsert(userId, goalMl)
        return GoalResponse(goalMl = goalMl)
    }

    fun getDailySummary(userId: Long, date: String): DailyWaterResponse {
        val goalMl = goalRepository.findByUser(userId) ?: DEFAULT_DAILY_GOAL_ML
        val entries = repository.findEntriesForDate(userId, date)
        val totalMl = entries.sumOf { it.amountMl }
        return DailyWaterResponse(
            date            = date,
            totalMl         = totalMl,
            goalMl          = goalMl,
            progressPercent = ((totalMl.toDouble() / goalMl) * 100).toInt().coerceAtMost(100),
            entries         = entries.map { it.toResponse() }
        )
    }

    fun getHistory(userId: Long, startDate: String, endDate: String): List<WaterHistoryResponse> {
        val goalMl = goalRepository.findByUser(userId) ?: DEFAULT_DAILY_GOAL_ML
        val entries = repository.findEntriesForDateRange(userId, startDate, endDate)
        return entries
            .groupBy { it.createdAt.substring(0, 10) }
            .map { (date, dayEntries) ->
                val totalMl = dayEntries.sumOf { it.amountMl }
                WaterHistoryResponse(
                    date            = date,
                    totalMl         = totalMl,
                    goalMl          = goalMl,
                    progressPercent = ((totalMl.toDouble() / goalMl) * 100).toInt().coerceAtMost(100)
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
        logDateTime = createdAt.replace(" ", "T"),
        amountMl    = amountMl,
        notes       = notes
    )
}
