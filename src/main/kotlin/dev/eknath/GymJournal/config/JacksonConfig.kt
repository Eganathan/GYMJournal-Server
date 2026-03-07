package dev.eknath.GymJournal.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdScalarSerializer
import tools.jackson.databind.type.LogicalType

/**
 * Serialises all [Long] (and [Long?]) values as JSON strings in HTTP responses.
 *
 * Catalyst DataStore ROWIDs are ~17-digit numbers that exceed JavaScript's
 * Number.MAX_SAFE_INTEGER (9_007_199_254_740_991). JSON.parse() silently rounds
 * values beyond that threshold, causing ID mismatches on the client.
 *
 * Serialising as strings:
 *  - Preserves full precision on all clients (JS, mobile, etc.)
 *  - Aligns with React Router's useParams() which always yields strings
 *  - Keeps all FK references (routineId, sessionId, exerciseId, etc.) consistent
 *
 * NOTE: clients must use String equality for IDs — do not parseInt() them.
 *
 * Architecture: Spring Framework 7 uses [JacksonJsonHttpMessageConverter] (Jackson 3.x,
 * tools.jackson.*) as the primary JSON converter. [MappingJackson2HttpMessageConverter]
 * (Jackson 2.x, com.fasterxml.jackson.*) is present but deprecated; we configure it
 * too for completeness.
 *
 * For [JacksonJsonHttpMessageConverter]: since Jackson 3.x ObjectMapper is immutable,
 * we rebuild it with our module via [JsonMapper.builder], then replace the converter.
 * Coercion (String → Integer types) is also enabled so that request bodies sending
 * Long IDs as strings are accepted.
 */
@Configuration
@Suppress("DEPRECATION")
class JacksonConfig : WebMvcConfigurer {

    /**
     * Jackson 3.x Long→String serialiser (tools.jackson.*).
     * Uses java.lang.Long (boxed) — required so that both Long and Long? fields at
     * JVM level resolve to the same serializer registration.
     */
    private class LongToStringSerializer :
        StdScalarSerializer<java.lang.Long>(java.lang.Long::class.java) {

        override fun serialize(value: java.lang.Long, gen: JsonGenerator, ctxt: SerializationContext) {
            gen.writeString(value.toString())
        }
    }

    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        val longModule = SimpleModule("long-as-string").apply {
            addSerializer(java.lang.Long::class.java, LongToStringSerializer())
        }

        // ── Jackson 3.x converter (primary in Spring Framework 7) ──────────────
        // ObjectMapper is immutable in Jackson 3.x; rebuild via the saved builder state
        // and replace the converter in the list.
        val jackson3Index = converters.indexOfFirst { it is JacksonJsonHttpMessageConverter }
        if (jackson3Index >= 0) {
            val newMapper = JsonMapper.builder()
                .findAndAddModules()             // picks up KotlinModule and other SPI modules
                .addModule(longModule)
                .withCoercionConfig(LogicalType.Integer) { cfg ->
                    // Allow String values ("11585000000685312") to be deserialised into Long? fields.
                    // Needed when clients send Long IDs as strings in request bodies.
                    cfg.setCoercion(CoercionInputShape.String, CoercionAction.TryConvert)
                }
                .build()
            converters[jackson3Index] = JacksonJsonHttpMessageConverter(newMapper)
        }

        // ── Jackson 2.x converter (deprecated in Spring 7, but may be present) ─
        // ObjectMapper in Jackson 2.x is mutable; registerModule works directly.
        converters
            .filterIsInstance<MappingJackson2HttpMessageConverter>()
            .forEach { converter ->
                converter.objectMapper.registerModule(
                    com.fasterxml.jackson.databind.module.SimpleModule("long-as-string-2x").apply {
                        addSerializer(java.lang.Long::class.java, Jackson2LongToStringSerializer())
                    }
                )
            }
    }

    /**
     * Jackson 2.x Long→String serialiser (com.fasterxml.jackson.*) for the legacy converter.
     */
    private class Jackson2LongToStringSerializer :
        com.fasterxml.jackson.databind.ser.std.StdScalarSerializer<java.lang.Long>(java.lang.Long::class.java) {

        override fun serialize(
            value: java.lang.Long,
            gen: com.fasterxml.jackson.core.JsonGenerator,
            provider: com.fasterxml.jackson.databind.SerializerProvider
        ) {
            gen.writeString(value.toString())
        }
    }
}
