devices {
    shellyPro2PMDimmer {
        name = "bathroom dimmer"
        url = "xxx"
    }
    shelly1Relay {
        name = "bathroom fan"
        url = "xxx"
    }
}

scene("movie_night") {
    lights("living room") brightness 20
    lights("kitchen") switched off
    tv("living room") switched on
}

rules {
    whenever not presenceIn flat perform allLights except "something" switched off
    whenever time "20:00" perform scene "movie_night"
}

defaults {
    lights {
        brightness = 50
        color = "warm_white"
    }
}

room("living room") {
    rules {
        whenever time "5.30" or ... perform curtains open
    }
}

room("bathroom") {
    devices {
        mqttPresence {
            topic = "bathroom/mmWave1"
        }
    }

    deviceGroup {
        lights("bathroom lights") {
            light "bathroom dimmer#ch0"
            light "bathroom dimmer#ch1"
        }
    }

    actions {
        action("house party") {
            lights "party lights" effect strobo
        }
    }

    rules {
        lights("bathroom lights") {
            keepOn {
                all {
                    any {
                        presenceIn "bathroom"
                        presenceIn "corridor" brightness 50 duration 20.seconds
                    }
                    time between "18:00" and "06:00"
                }
            }
            keepOff {
                not presenceIn flat
            }
        }

        rule("turn on fan on high humidity or temperature") {
            whenever any {
                moisture "bathroom" above 60
                temperature "bathroom" above 25
                presence "bathroom" duration 5.minutes
            }
            perform {
                switch "bathroom fan" on
            }
        }
    }
}
