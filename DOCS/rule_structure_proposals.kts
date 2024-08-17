rules {
    maintain("Lights are on in the bathroom when someone is there") {
        whilst presenceIn "bathroom" extended 5.sec
        ensure lights "bathroom" kept on
    }
    keep("Lights on in the bathroom when someone is there") {
        whilst presenceIn "bathroom" extended 5.sec
        ensure lights "bathroom" kept on
    }
}

rules {
    trigger("Open curtains in the morning") {
        on light above 50 or time 5.30am
        perform curtains "bedroom" open
    }
    trigger("Turn lights on") {
        on entranceTo "bathroom"
        perform lights "bathroom" turn on
    }
}