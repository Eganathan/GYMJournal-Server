package dev.eknath.GymJournal.modules.insights.engines

import dev.eknath.GymJournal.modules.insights.*
import dev.eknath.GymJournal.model.dto.MetricSnapshotItem
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Produces health insights by comparing a user's snapshot values against
 * established clinical reference ranges (WHO / ACC / ADA guidelines).
 *
 * This engine is purely stateless — it never calls the database.
 * Reference ranges are hardcoded constants; no user data is needed beyond
 * the snapshot values themselves.
 *
 * @Order(1) — runs first in the composite pipeline.
 *
 * To add a new metric rule: add a private `fun <metric>Insight(...)` and
 * call it from [analyze]. No other change is needed.
 */
@Component
@Order(1)
class ReferenceRangeInsightsEngine : MetricInsightsEngine {

    override val name = "reference-range"

    override fun analyze(context: InsightContext): List<MetricInsight> {
        val s = context.snapshot
        val gender = context.gender
        val insights = mutableListOf<MetricInsight>()

        // ── Body Composition ──────────────────────────────────────────────
        s["bmi"]?.let            { insights += bmiInsight(it.value) }
        s["bodyFat"]?.let        { insights += bodyFatInsight(it.value, gender) }
        s["visceralFat"]?.let    { insights += visceralFatInsight(it.value) }
        s["smiComputed"]?.let    { insights += smiInsight(it.value, gender) }

        // ── Lipid Panel ───────────────────────────────────────────────────
        s["cholesterolTotal"]?.let { insights += cholesterolTotalInsight(it.value) }
        s["cholesterolLDL"]?.let   { insights += cholesterolLdlInsight(it.value) }
        s["cholesterolHDL"]?.let   { insights += cholesterolHdlInsight(it.value) }
        s["triglycerides"]?.let    { insights += triglyceridesInsight(it.value) }

        // ── Blood Sugar & Metabolic ───────────────────────────────────────
        s["fastingGlucose"]?.let { insights += fastingGlucoseInsight(it.value) }
        s["hba1c"]?.let          { insights += hba1cInsight(it.value) }

        return insights
    }

    // ── Body Composition ─────────────────────────────────────────────────────

    private fun bmiInsight(value: Double): MetricInsight {
        val (status, message) = when {
            value < 18.5 -> InsightStatus.WARNING   to "Underweight (BMI < 18.5). Consider consulting a healthcare provider about healthy weight gain."
            value < 25.0 -> InsightStatus.OK        to "BMI is in the healthy range (18.5–24.9)."
            value < 30.0 -> InsightStatus.BORDERLINE to "Overweight range (BMI 25–29.9). A healthy target is 18.5–24.9."
            value < 35.0 -> InsightStatus.WARNING   to "Obese Class I (BMI 30–34.9). Weight management is recommended."
            else         -> InsightStatus.DANGER    to "Obese Class II+ (BMI ≥ 35). Medical guidance is strongly advised."
        }
        return MetricInsight(
            metricType     = "bmi",
            value          = value,
            unit           = "kg/m²",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(18.5, 24.9, "Normal: 18.5–24.9 kg/m²")
        )
    }

    /**
     * Body fat % ranges differ by sex. Falls back to a blended threshold
     * when gender is unknown so the user still gets a useful insight.
     */
    private fun bodyFatInsight(value: Double, gender: Gender?): MetricInsight {
        val (status, message, okMin, okMax) = when (gender) {
            Gender.MALE -> when {
                value < 6.0  -> Quad(InsightStatus.DANGER,     "Extremely low body fat (< 6% for men). This level can impair organ function.", 6.0, 17.0)
                value < 18.0 -> Quad(InsightStatus.OK,         "Body fat is in the healthy range for men (6–17%).", 6.0, 17.0)
                value < 25.0 -> Quad(InsightStatus.BORDERLINE, "Body fat is above the ideal range for men (6–17%).", 6.0, 17.0)
                else         -> Quad(InsightStatus.WARNING,    "Body fat is high for men (≥ 25%). Focus on resistance training and diet.", 6.0, 17.0)
            }
            Gender.FEMALE -> when {
                value < 16.0 -> Quad(InsightStatus.DANGER,     "Extremely low body fat (< 16% for women). This can affect hormonal health.", 16.0, 24.0)
                value < 25.0 -> Quad(InsightStatus.OK,         "Body fat is in the healthy range for women (16–24%).", 16.0, 24.0)
                value < 32.0 -> Quad(InsightStatus.BORDERLINE, "Body fat is above the ideal range for women (16–24%).", 16.0, 24.0)
                else         -> Quad(InsightStatus.WARNING,    "Body fat is high for women (≥ 32%). Focus on resistance training and diet.", 16.0, 24.0)
            }
            null -> when {
                value < 10.0 -> Quad(InsightStatus.DANGER,     "Body fat is very low. This level may impair normal bodily functions.", 10.0, 22.0)
                value < 22.0 -> Quad(InsightStatus.OK,         "Body fat appears to be in a reasonable healthy range.", 10.0, 22.0)
                value < 28.0 -> Quad(InsightStatus.BORDERLINE, "Body fat is mildly elevated. Note: healthy range varies by sex.", 10.0, 22.0)
                else         -> Quad(InsightStatus.WARNING,    "Body fat appears high. For an accurate assessment, provide your gender.", 10.0, 22.0)
            }
        }
        return MetricInsight(
            metricType     = "bodyFat",
            value          = value,
            unit           = "%",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(okMin, okMax, "Normal ${if (gender != null) "for ${gender.name.lowercase()}" else "(approx)"}: $okMin–$okMax%")
        )
    }

    private fun visceralFatInsight(value: Double): MetricInsight {
        val (status, message) = when {
            value <= 9.0  -> InsightStatus.OK        to "Visceral fat level is normal (1–9)."
            value <= 14.0 -> InsightStatus.BORDERLINE to "Visceral fat level is mildly elevated (${"%.0f".format(value)}). Target is 1–9."
            else          -> InsightStatus.WARNING   to "Visceral fat level is high (${"%.0f".format(value)}). Excess visceral fat is linked to metabolic disease."
        }
        return MetricInsight(
            metricType     = "visceralFat",
            value          = value,
            unit           = "level",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(1.0, 9.0, "Normal: level 1–9")
        )
    }

    /**
     * SMI (Skeletal Muscle Index) cutoffs for sarcopenia screening.
     * Based on AWGS 2019 criteria.
     */
    private fun smiInsight(value: Double, gender: Gender?): MetricInsight {
        val (okMin, genderLabel) = when (gender) {
            Gender.MALE   -> 7.0 to "men"
            Gender.FEMALE -> 5.7 to "women"
            null          -> 6.0 to "adults (varies by sex)"
        }
        val (status, message) = when {
            value >= okMin -> InsightStatus.OK       to "Skeletal muscle index is in a healthy range for $genderLabel (≥ $okMin kg/m²)."
            value >= okMin - 0.5 -> InsightStatus.BORDERLINE to "Skeletal muscle index is mildly low for $genderLabel (target ≥ $okMin kg/m²). Consider adding resistance training."
            else           -> InsightStatus.WARNING  to "Skeletal muscle index is low for $genderLabel (< $okMin kg/m²). This may indicate sarcopenia risk."
        }
        return MetricInsight(
            metricType     = "smiComputed",
            value          = value,
            unit           = "kg/m²",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(okMin, null, "Normal for $genderLabel: ≥ $okMin kg/m²")
        )
    }

    // ── Lipid Panel ──────────────────────────────────────────────────────────

    private fun cholesterolTotalInsight(value: Double): MetricInsight {
        val (status, message) = when {
            value < 200.0 -> InsightStatus.OK        to "Total cholesterol is in the desirable range (< 200 mg/dL)."
            value < 240.0 -> InsightStatus.BORDERLINE to "Total cholesterol is borderline high (200–239 mg/dL). Dietary changes may help."
            else          -> InsightStatus.WARNING   to "Total cholesterol is high (≥ 240 mg/dL). Consult a doctor about cardiovascular risk."
        }
        return MetricInsight(
            metricType     = "cholesterolTotal",
            value          = value,
            unit           = "mg/dL",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(null, 200.0, "Desirable: < 200 mg/dL")
        )
    }

    private fun cholesterolLdlInsight(value: Double): MetricInsight {
        val (status, message) = when {
            value < 100.0 -> InsightStatus.OK        to "LDL cholesterol is optimal (< 100 mg/dL)."
            value < 130.0 -> InsightStatus.OK        to "LDL cholesterol is near-optimal (100–129 mg/dL)."
            value < 160.0 -> InsightStatus.BORDERLINE to "LDL cholesterol is borderline high (130–159 mg/dL). Diet and activity can lower LDL."
            value < 190.0 -> InsightStatus.WARNING   to "LDL cholesterol is high (160–189 mg/dL). Medical review recommended."
            else          -> InsightStatus.DANGER    to "LDL cholesterol is very high (≥ 190 mg/dL). Please consult a doctor."
        }
        return MetricInsight(
            metricType     = "cholesterolLDL",
            value          = value,
            unit           = "mg/dL",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(null, 100.0, "Optimal: < 100 mg/dL")
        )
    }

    private fun cholesterolHdlInsight(value: Double): MetricInsight {
        // HDL is inverse — higher is better
        val (status, message) = when {
            value < 40.0 -> InsightStatus.DANGER    to "HDL cholesterol is low (< 40 mg/dL). Low HDL is a risk factor for heart disease."
            value < 60.0 -> InsightStatus.BORDERLINE to "HDL cholesterol is acceptable (40–59 mg/dL). Above 60 is protective."
            else         -> InsightStatus.OK        to "HDL cholesterol is at a protective level (≥ 60 mg/dL)."
        }
        return MetricInsight(
            metricType     = "cholesterolHDL",
            value          = value,
            unit           = "mg/dL",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(60.0, null, "Protective: ≥ 60 mg/dL")
        )
    }

    private fun triglyceridesInsight(value: Double): MetricInsight {
        val (status, message) = when {
            value < 150.0 -> InsightStatus.OK        to "Triglycerides are normal (< 150 mg/dL)."
            value < 200.0 -> InsightStatus.BORDERLINE to "Triglycerides are borderline high (150–199 mg/dL). Reducing sugar and refined carbs helps."
            value < 500.0 -> InsightStatus.WARNING   to "Triglycerides are high (200–499 mg/dL). Medical evaluation recommended."
            else          -> InsightStatus.DANGER    to "Triglycerides are very high (≥ 500 mg/dL). Risk of pancreatitis — please see a doctor."
        }
        return MetricInsight(
            metricType     = "triglycerides",
            value          = value,
            unit           = "mg/dL",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(null, 150.0, "Normal: < 150 mg/dL")
        )
    }

    // ── Blood Sugar & Metabolic ───────────────────────────────────────────────

    private fun fastingGlucoseInsight(value: Double): MetricInsight {
        val (status, message) = when {
            value < 70.0  -> InsightStatus.WARNING   to "Fasting glucose is low (< 70 mg/dL — hypoglycemia range). Consult a doctor."
            value < 100.0 -> InsightStatus.OK        to "Fasting glucose is normal (70–99 mg/dL)."
            value < 126.0 -> InsightStatus.BORDERLINE to "Fasting glucose is in the prediabetes range (100–125 mg/dL). Lifestyle changes can reverse this."
            else          -> InsightStatus.DANGER    to "Fasting glucose is in the diabetes range (≥ 126 mg/dL). Please consult a doctor."
        }
        return MetricInsight(
            metricType     = "fastingGlucose",
            value          = value,
            unit           = "mg/dL",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(70.0, 99.0, "Normal: 70–99 mg/dL")
        )
    }

    private fun hba1cInsight(value: Double): MetricInsight {
        val (status, message) = when {
            value < 5.7  -> InsightStatus.OK        to "HbA1c is in the normal range (< 5.7%)."
            value < 6.5  -> InsightStatus.BORDERLINE to "HbA1c is in the prediabetes range (5.7–6.4%). Diet and exercise can bring this down."
            else         -> InsightStatus.DANGER    to "HbA1c is in the diabetes range (≥ 6.5%). Medical management is recommended."
        }
        return MetricInsight(
            metricType     = "hba1c",
            value          = value,
            unit           = "%",
            status         = status,
            message        = message,
            referenceRange = ReferenceRange(null, 5.7, "Normal: < 5.7%")
        )
    }

    // ── Internal helper ───────────────────────────────────────────────────────

    /** Avoids destructuring verbosity when returning 4 values from when blocks. */
    private data class Quad(
        val status: InsightStatus,
        val message: String,
        val okMin: Double,
        val okMax: Double
    )
}
