package symsig.sensei.util.json

import io.github.nomisrev.JsonPath
import io.github.nomisrev.boolean
import io.github.nomisrev.path
import io.github.nomisrev.string
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

inline fun <reified T> jsonPathExtractor(jsonPath: String): (JsonObject) -> T? {
    return { json: JsonObject ->
        JsonPath.path(jsonPath).let {
            val value = when (T::class) {
                String::class -> it.string.getOrNull(json) as? T
                Boolean::class -> it.boolean.getOrNull(json) as? T
                LocalDateTime::class -> it.string.getOrNull(json)?.let { dateStr ->
                    try {
                        LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME) as? T
                    } catch (e: DateTimeParseException) {
                        throw IllegalArgumentException("Invalid LocalDateTime format at path: $jsonPath, value: $dateStr", e)
                    }
                }
                Instant::class -> it.string.getOrNull(json)?.let { dateStr ->
                    try {
                        Instant.parse(dateStr) as? T
                    } catch (e: DateTimeParseException) {
                        throw IllegalArgumentException("Invalid Instant format at path: $jsonPath, value: $dateStr", e)
                    }
                }
                else -> throw IllegalArgumentException("Unsupported type: ${T::class} at path: $jsonPath")
            }
            value
        }
    }
}
