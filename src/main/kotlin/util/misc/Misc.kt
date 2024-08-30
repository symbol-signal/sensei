package symsig.sensei.util.misc

infix fun <A, B, C> ((B) -> C).after(f: (A) -> B): (A) -> C {
    return { a: A -> this(f(a)) }
}

fun <A, B, C> ((A) -> B).andThen(g: (B) -> C): (A) -> C {
    return { a: A -> g(this(a)) }
}

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
