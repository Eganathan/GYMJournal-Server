package dev.eknath.GymJournal.modules.logs

import dev.eknath.GymJournal.model.domain.GymLog
import dev.eknath.GymJournal.model.dto.CreateGymLogRequest
import dev.eknath.GymJournal.model.dto.GymLogResponse
import dev.eknath.GymJournal.model.dto.UpdateGymLogRequest
import org.springframework.stereotype.Service

private val VALID_TYPES = setOf("INJURY", "MEDICATION", "NOTE")
private val VALID_SEVERITIES = setOf("LOW", "MEDIUM", "HIGH", "")

@Service
class GymLogService(private val repository: GymLogRepository) {

    fun create(userId: Long, request: CreateGymLogRequest): GymLogResponse {
        val type = request.type.uppercase()
        if (type !in VALID_TYPES)
            throw IllegalArgumentException("Invalid type '$type'. Must be one of: INJURY, MEDICATION, NOTE")

        val severity = request.severity.uppercase()
        if (severity !in VALID_SEVERITIES)
            throw IllegalArgumentException("Invalid severity '$severity'. Must be one of: LOW, MEDIUM, HIGH or empty")

        val log = GymLog(
            userId      = userId,
            logDate     = request.logDate,
            type        = type,
            title       = request.title.trim(),
            description = request.description.trim(),
            severity    = severity
        )
        return repository.save(log).toResponse()
    }

    fun getByDate(userId: Long, date: String): List<GymLogResponse> =
        repository.findByDate(userId, date).map { it.toResponse() }

    fun getRecent(userId: Long): List<GymLogResponse> =
        repository.findRecent(userId).map { it.toResponse() }

    fun update(userId: Long, id: Long, request: UpdateGymLogRequest): GymLogResponse {
        val existing = repository.findById(id)
            ?: throw NoSuchElementException("Log entry $id not found")
        if (existing.userId != userId) throw IllegalAccessException("Log does not belong to this user")

        val severity = request.severity?.uppercase() ?: existing.severity
        if (severity !in VALID_SEVERITIES)
            throw IllegalArgumentException("Invalid severity '$severity'. Must be one of: LOW, MEDIUM, HIGH or empty")

        val updated = existing.copy(
            title       = request.title?.trim()       ?: existing.title,
            description = request.description?.trim() ?: existing.description,
            severity    = severity
        )
        repository.update(id, updated)
        return (repository.findById(id) ?: updated).toResponse()
    }

    fun delete(userId: Long, id: Long) {
        val existing = repository.findById(id)
            ?: throw NoSuchElementException("Log entry $id not found")
        if (existing.userId != userId) throw IllegalAccessException("Log does not belong to this user")
        repository.delete(id)
    }

    private fun GymLog.toResponse() = GymLogResponse(
        id          = (id ?: 0).toString(),
        logDate     = logDate,
        type        = type,
        title       = title,
        description = description,
        severity    = severity,
        createdAt   = createdAt.replace(" ", "T")
    )
}
