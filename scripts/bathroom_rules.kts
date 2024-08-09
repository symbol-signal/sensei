//@file:DependsOn("../build/libs/sensei-0.1.0-SNAPSHOT-all.jar")

import symsig.sensei.*

/*devices {
    shellyPro2PMDimmer {
        name = "bathroom dimmer"
        url = "xxx"
    }
}*/

room("Bathroom") {
    devices {
        wsPresence { sensorId = "sen0395/bathroom" }
    }

    rules {
        /*lights("bathroom dimmer#ch02") {
            keepOn {
                presenceIn "bathroom" extends 5.seconds
            }
        }*/

        rule("turn on light in bathroom when someone is there") {
            whenever presenceIn "bathroom" extends 5.seconds
//            perform lights "bathroom dimmer#ch02" keepOn
        }
    }
}
