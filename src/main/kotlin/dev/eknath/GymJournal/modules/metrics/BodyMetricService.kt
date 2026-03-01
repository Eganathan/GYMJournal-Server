package dev.eknath.GymJournal.modules.metrics

import dev.eknath.GymJournal.model.domain.BodyMetricEntry
import dev.eknath.GymJournal.model.domain.CustomMetricDef
import dev.eknath.GymJournal.model.dto.*
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.round

/** Metric types that are always derived from stored values — never stored themselves. */
private val COMPUTED_TYPES = setOf("bmi", "smiComputed")

@Service
class BodyMetricService(
    private val entryRepo: BodyMetricRepository,
    private val defRepo: CustomMetricDefRepository
) {

    // ---------------------------------------------------------------------------
    // Metric entries
    // ---------------------------------------------------------------------------

    /**
     * Saves all entries in the batch.
     * Rejects computed metric types (bmi, smiComputed) with a 400-triggering exception.
     * Validates that each logDate is in YYYY-MM-DD format.
     */
    fun batchLog(userId: String, request: BatchLogMetricRequest): List<MetricEntryResponse> {
        return request.entries.map { req ->
            if (req.metricType in COMPUTED_TYPES)
                throw IllegalArgumentException(
                    "'${req.metricType}' is a computed metric and cannot be stored directly."
                )
            if (!isValidDate(req.logDate))
                throw IllegalArgumentException(
                    "Invalid logDate '${req.logDate}'. Expected format: YYYY-MM-DD."
                )
            val entry = BodyMetricEntry(
                metricType = req.metricType.trim(),
                value      = req.value,
                unit       = req.unit.trim(),
                logDate    = req.logDate,
                notes      = req.notes.trim()
            )
            entryRepo.save(entry)
        }.map { it.toResponse() }
    }

    /**
     * Returns all entries for [userId] on [date] (YYYY-MM-DD), sorted by metricType.
     * Used to pre-fill the log form when revisiting a past date.
     */
    fun getEntriesForDate(userId: String, date: String): List<MetricEntryResponse> =
        entryRepo.findByDate(userId, date).map { it.toResponse() }

    /**
     * Returns history for [metricType] within the given date range.
     * Defaults: startDate = 90 days ago, endDate = today.
     */
    fun getHistory(
        userId: String,
        metricType: String,
        startDate: String?,
        endDate: String?
    ): List<MetricEntryResponse> {
        val today = LocalDate.now()
        val end   = endDate   ?: today.toString()
        val start = startDate ?: today.minusDays(90).toString()
        return entryRepo.findByType(userId, metricType, start, end).map { it.toResponse() }
    }

    /**
     * Returns the most-recent value per metric type.
     *
     * Implementation:
     * 1. Fetch the 300 most-recent entries ordered by logDate DESC.
     * 2. Group by metricType in-memory; take the first (most-recent date) per type.
     * 3. Derive and append computed metrics:
     *    - bmi         = weight(kg) / (height(cm)/100)²
     *    - smiComputed = smm(kg)    / (height(cm)/100)²
     *    Both are omitted if source data is unavailable.
     *    logDate for a computed item = max(source1.logDate, source2.logDate).
     */
    fun getSnapshot(userId: String): List<MetricSnapshotItem> {
        val recent = entryRepo.findRecent(userId)

        // Group by metricType — entries are DESC by logDate, so first in each group is most-recent
        val snapshotMap: Map<String, BodyMetricEntry> = recent
            .groupBy { it.metricType }
            .mapValues { (_, entries) -> entries.first() }

        val items = snapshotMap.values.map { it.toSnapshotItem() }.toMutableList()

        // Derived: BMI (requires weight in kg and height in cm)
        val weight = snapshotMap["weight"]
        val height = snapshotMap["height"]
        if (weight != null && height != null && height.value > 0) {
            val heightM = height.value / 100.0
            val bmi     = round(weight.value / heightM.pow(2) * 10) / 10.0
            val logDate = maxOf(weight.logDate, height.logDate)
            items.add(MetricSnapshotItem("bmi", bmi, "kg/m²", logDate))
        }

        // Derived: SMI (requires smm in kg and height in cm)
        val smm = snapshotMap["smm"]
        if (smm != null && height != null && height.value > 0) {
            val heightM     = height.value / 100.0
            val smiComputed = round(smm.value / heightM.pow(2) * 10) / 10.0
            val logDate     = maxOf(smm.logDate, height.logDate)
            items.add(MetricSnapshotItem("smiComputed", smiComputed, "kg/m²", logDate))
        }

        return items
    }

    /**
     * Updates the given entry. Only the creator may update.
     * Validates logDate format if supplied.
     */
    fun updateEntry(userId: String, id: Long, request: UpdateMetricEntryRequest): MetricEntryResponse {
        val existing = entryRepo.findById(id)
            ?: throw NoSuchElementException("Metric entry $id not found")
        if (existing.createdBy != userId)
            throw IllegalAccessException("Metric entry $id does not belong to this user")

        request.logDate?.let {
            if (!isValidDate(it))
                throw IllegalArgumentException("Invalid logDate '$it'. Expected format: YYYY-MM-DD.")
        }

        val updated = existing.copy(
            value   = request.value   ?: existing.value,
            unit    = request.unit?.trim()   ?: existing.unit,
            logDate = request.logDate ?: existing.logDate,
            notes   = request.notes?.trim()  ?: existing.notes
        )
        entryRepo.update(id, updated)
        return (entryRepo.findById(id) ?: updated).toResponse()
    }

    /** Deletes the given entry. Only the creator may delete. */
    fun deleteEntry(userId: String, id: Long) {
        val existing = entryRepo.findById(id)
            ?: throw NoSuchElementException("Metric entry $id not found")
        if (existing.createdBy != userId)
            throw IllegalAccessException("Metric entry $id does not belong to this user")
        entryRepo.delete(id)
    }

    // ---------------------------------------------------------------------------
    // Custom metric definitions
    // ---------------------------------------------------------------------------

    fun listCustomDefs(userId: String): List<CustomMetricDefResponse> =
        defRepo.findAll(userId).map { it.toResponse() }

    /**
     * Creates a new custom metric definition.
     * The metricKey is derived from the label using the same algorithm as the frontend:
     *   "custom_" + label.lowercase().replace(Regex("[^a-z0-9]"), "_")
     * Returns 400 if a definition with the same key already exists for this user.
     */
    fun addCustomDef(userId: String, request: CreateCustomMetricRequest): CustomMetricDefResponse {
        val label = request.label.trim()
        val key   = deriveMetricKey(label)
        if (defRepo.findByKey(userId, key) != null)
            throw IllegalArgumentException(
                "A custom metric with key '$key' already exists for this user."
            )
        val def = CustomMetricDef(metricKey = key, label = label, unit = request.unit.trim())
        return defRepo.save(def).toResponse()
    }

    /**
     * Deletes the custom metric definition and cascade-deletes all entries with that metricType.
     */
    fun deleteCustomDef(userId: String, key: String) {
        val def = defRepo.findByKey(userId, key)
            ?: throw NoSuchElementException("Custom metric definition '$key' not found")
        // Cascade: remove all stored entries for this custom type
        entryRepo.findAllByType(userId, key).forEach { entryRepo.delete(it.id!!) }
        defRepo.delete(def.id!!)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Derives the canonical storage key from a user-supplied label (matches frontend logic). */
    private fun deriveMetricKey(label: String): String =
        "custom_" + label.lowercase().replace(Regex("[^a-z0-9]"), "_")

    /** Returns true iff [date] matches YYYY-MM-DD. */
    private fun isValidDate(date: String): Boolean =
        Regex("""^\d{4}-\d{2}-\d{2}$""").matches(date)

    // ---------------------------------------------------------------------------
    // Mappers
    // ---------------------------------------------------------------------------

    private fun BodyMetricEntry.toResponse() = MetricEntryResponse(
        id         = id ?: 0,
        metricType = metricType,
        value      = value,
        unit       = unit,
        logDate    = logDate,
        notes      = notes,
        createdAt  = createdAt.replace(" ", "T"),
        updatedAt  = updatedAt.replace(" ", "T")
    )

    private fun BodyMetricEntry.toSnapshotItem() = MetricSnapshotItem(
        metricType = metricType,
        value      = value,
        unit       = unit,
        logDate    = logDate
    )

    private fun CustomMetricDef.toResponse() = CustomMetricDefResponse(
        id        = id ?: 0,
        metricKey = metricKey,
        label     = label,
        unit      = unit
    )
}
