package symsig.sensei.util.misc

fun interpretAsBoolean(value: Any?): Boolean? {
    return when (value) {
        is Boolean -> value
        is Number -> when (value.toInt()) {
            1 -> true
            0 -> false
            else -> null
        }
        is String -> when (value.lowercase()) {
            "true", "yes", "on" -> true
            "false", "no", "off" -> false
            else -> null
        }
        else -> null
    }
}
