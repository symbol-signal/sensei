package symsig.sensei.util.json

import io.github.nomisrev.*
import kotlinx.serialization.json.*
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
                Int::class -> it.int.getOrNull(json) as? T
                Long::class -> it.long.getOrNull(json) as? T
                Float::class -> it.float.getOrNull(json) as? T
                Double::class -> it.double.getOrNull(json) as? T
                LocalDateTime::class -> it.string.getOrNull(json)?.let { dateStr ->
                    try {
                        LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME) as? T
                    } catch (e: DateTimeParseException) {
                        throw IllegalArgumentException("Invalid LocalDateTime format at path: $jsonPath, value: $dateStr", e)
                    }
                }
                Instant::class -> {
                    when (val element = it.getOrNull(json)) {
                        is JsonPrimitive -> {
                            when {
                                element.isString -> try {
                                    Instant.parse(element.content) as? T
                                } catch (e: DateTimeParseException) {
                                    throw IllegalArgumentException("Invalid Instant format at path: $jsonPath, value: ${element.content}", e)
                                }
                                element.longOrNull != null -> Instant.ofEpochMilli(element.long) as? T
                                else -> throw IllegalArgumentException("Invalid Instant format at path: $jsonPath, value: $element")
                            }
                        }
                        else -> throw IllegalArgumentException("Invalid Instant format at path: $jsonPath, value: $element")
                    }
                }
                else -> throw IllegalArgumentException("Unsupported type: ${T::class} at path: $jsonPath")
            }
            value
        }
    }
}

fun jsonPathExtractorAny(jsonPath: String): (JsonObject) -> Any? {
    return { json: JsonObject ->
        JsonPath.path(jsonPath).getOrNull(json)?.let { element ->
            when (val primitive = element as? JsonPrimitive) {
                is JsonPrimitive -> when {
                    primitive.isString -> primitive.content
                    primitive.booleanOrNull != null -> primitive.boolean
                    primitive.intOrNull != null -> primitive.int
                    primitive.longOrNull != null -> primitive.long
                    primitive.floatOrNull != null -> primitive.float
                    primitive.doubleOrNull != null -> primitive.double
                    else -> primitive.content
                }
                else -> element // Return the element itself if not a primitive
            }
        }
    }
}

