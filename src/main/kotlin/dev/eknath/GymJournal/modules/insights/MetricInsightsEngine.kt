package dev.eknath.GymJournal.modules.insights

import dev.eknath.GymJournal.model.dto.MetricSnapshotItem

/**
 * Contract for an intelligence engine that produces health insights
 * from a user's metric snapshot.
 *
 * To add a new engine:
 *   1. Create a class implementing this interface.
 *   2. Annotate it with @Component (and optionally @Order for priority).
 *   3. Spring will inject it into CompositeInsightsEngine automatically —
 *      no other wiring required.
 *
 * Engines must never throw — return emptyList() if nothing is applicable.
 * Each engine is isolated: a failure in one does not affect the others.
 */
interface MetricInsightsEngine {

    /** Short identifier used for logging and source attribution. */
    val name: String

    /**
     * Analyses the provided context and returns zero or more insights.
     * Return only insights the engine is confident about.
     * Return emptyList() if the context lacks the data this engine needs.
     */
    fun analyze(context: InsightContext): List<MetricInsight>
}

// ---------------------------------------------------------------------------
// Context passed into every engine
// ---------------------------------------------------------------------------

/**
 * Everything an engine needs to produce insights.
 *
 * [snapshot] is keyed by metricType and already includes server-side
 * computed values (bmi, smiComputed) from the snapshot endpoint.
 *
 * [gender] is optional — engines that produce gender-neutral insights
 * simply ignore it. Engines that are gender-aware (body fat %, SMI cutoffs)
 * should fall back to a conservative range when it is null.
 */
data class InsightContext(
    val snapshot: Map<String, MetricSnapshotItem>,
    val gender: Gender? = null
)

enum class Gender { MALE, FEMALE }

// ---------------------------------------------------------------------------
// Output types
// ---------------------------------------------------------------------------

data class MetricInsight(
    /** The metric this insight applies to. */
    val metricType: String,
    /** The value that was evaluated. */
    val value: Double,
    val unit: String,
    /** Severity classification. */
    val status: InsightStatus,
    /** Human-readable message suitable for display in the app. */
    val message: String,
    /** The clinical reference range used for the evaluation, if applicable. */
    val referenceRange: ReferenceRange? = null
)

data class ReferenceRange(
    val min: Double?,
    val max: Double?,
    /** Short label for display, e.g. "Normal: 18.5–24.9 kg/m²" */
    val description: String
)

enum class InsightStatus {
    /** Within the healthy reference range. */
    OK,
    /** Mildly outside normal — worth monitoring but not urgent. */
    BORDERLINE,
    /** Outside normal range — action is recommended. */
    WARNING,
    /** Significantly outside range — medical attention advised. */
    DANGER
}
