rules {
    maintain("Lights are on in the bathroom when someone is there") {
        whilst presenceIn "bathroom" extended 5.sec
        ensure lights "bathroom" kept on
    }
    keep("Lights on in the bathroom when someone is there") {
        whilst presenceIn "bathroom" + 5.sec
        ensure lights "bathroom" kept on
    }
}

rules {
    trigger("Open curtains in the morning") {
        on light above 50 or time 5.30am
        perform curtains "bedroom" open
    }
    trigger("Turn lights on") {
        on entrance "bathroom"
        perform delayed 3.sec dimmer "bathroom" lights ["1"] turn on
    }
    trigger("Turn lights off") {
        on exit "bathroom"
        perform delayed 3.sec lights "bathroom" turn off
    }
}