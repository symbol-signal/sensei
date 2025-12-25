println("Day: $dayStart to $dayEnd")

val dimmer = KinconyD16Dimmer(
    mqtt,
    "dimmer/d96c4bd0672e64279c34a168/state",
    "dimmer/d96c4bd0672e64279c34a168/set",
    scope,
    mapOf(KinconyD16Dimmer.Channel.Ch2 to 20..40)
)

launch {
    val ch2 = dimmer.channel(KinconyD16Dimmer.Channel.Ch2)
    println("Turning on Ch2")
    ch2.turnOn(50)
    delay(3.seconds)
    println("Turning off Ch2")
    ch2.turnOff()
}
