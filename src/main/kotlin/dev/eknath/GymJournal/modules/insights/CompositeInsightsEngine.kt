package dev.eknath.GymJournal.modules.insights

import org.springframework.stereotype.Component
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Aggregates all registered [MetricInsightsEngine] implementations and
 * runs them in order against a single [InsightContext].
 *
 * Spring automatically injects every @Component that implements
 * [MetricInsightsEngine] into [engines], respecting @Order annotations.
 * Adding a new engine is as simple as creating the class — nothing here changes.
 *
 * This class intentionally does NOT implement [MetricInsightsEngine] itself
 * to avoid Spring injecting it into its own [engines] list.
 *
 * Error isolation: if one engine throws, its results are dropped and the
 * remaining engines still run. This guarantees partial results are always
 * returned rather than a total failure.
 */
@Component
class CompositeInsightsEngine(
    private val engines: List<MetricInsightsEngine>
) {

    companion object {
        private val LOGGER = Logger.getLogger(CompositeInsightsEngine::class.java.name)
    }

    /**
     * Runs all registered engines against [context] and returns
     * the combined list of insights.
     *
     * Results from all engines are merged in order. If two engines
     * produce an insight for the same [metricType], both are included —
     * engines should avoid overlapping coverage.
     */
    fun analyze(context: InsightContext): List<MetricInsight> {
        LOGGER.log(Level.INFO, "[Insights] Running ${engines.size} engine(s): ${engines.map { it.name }}")

        return engines.flatMap { engine ->
            try {
                val results = engine.analyze(context)
                LOGGER.log(Level.INFO, "[Insights] ${engine.name} → ${results.size} insight(s)")
                results
            } catch (e: Throwable) {
                LOGGER.log(Level.SEVERE, "[Insights] Engine '${engine.name}' failed: ${e.message}", e)
                emptyList()
            }
        }
    }
}
