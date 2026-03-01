package dev.eknath.GymJournal.modules.insights

import dev.eknath.GymJournal.modules.metrics.BodyMetricService
import org.springframework.stereotype.Service

@Service
class InsightsService(
    private val bodyMetricService: BodyMetricService,
    private val compositeEngine: CompositeInsightsEngine
) {

    /**
     * Fetches the user's snapshot (most-recent value per metric type,
     * including server-side computed bmi and smiComputed), builds an
     * [InsightContext], runs all registered engines, and returns the results.
     *
     * Returns an empty list if the user has no metric data yet.
     */
    fun getInsights(userId: String, gender: Gender?): List<MetricInsight> {
        // Reuse the existing snapshot logic — includes computed bmi/smiComputed
        val snapshot = bodyMetricService.getSnapshot(userId)
        if (snapshot.isEmpty()) return emptyList()

        val snapshotMap = snapshot.associateBy { it.metricType }
        val context = InsightContext(snapshot = snapshotMap, gender = gender)

        return compositeEngine.analyze(context)
    }
}
