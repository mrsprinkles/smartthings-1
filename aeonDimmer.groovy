/* AEON specific micro driver 1.8
 *
 * Variation of the stock SmartThings "Dimmer-Switch" and twack's improved dimmer
 *	--auto re-configure after setting preferences
 *	--alarm indicator capability (using AEON hardware blink function)
 *	--up/down dimmer tiles, with configurable interval rates
 *
 * Includes:
 *	preferences tile for setting:
 * 		reporting functions (parameter 80)	[ 0:off, 1:hail, 2:report ] set to "Report" for fastest physical updates from the device
 *		control switch type (parameter 120)	[ 0:momentary, 1:toggle, 2:three way ] (2 isn't tested, not sure how its suppposed to work)
 *		
 * Mike Maxwell
 * madmax98087@yahoo.com
 * 2014-12-06
 * Eugene Kardash
 * 2016-10-27
 *
 	change log
    1.1 2014-12-08
    	-added light state restore to prevent alarm smartapps from turning off the light if it was on when the stobe request was made.
    1.2 2014-12-10
    	-added flash command with parameters for smartapp integration        
    1.3 2014-12-14
    	-almost a complete parser re-write
    1.4 2014-12-25
    	-fixed null display in activity feed
    1.5 2014-12-26
    	-yanked flakey turning states
    1.6 2015-02-19
    	-corrected fingerprint (was using aeon switch finger print)
    1.7 2015-05-15
        -removed background color for levelValue tile
    1.8 2016-10-27
    	-removed blinker, improved definitions of params 80 and 120

	AEON G2 
	0x20 Basic
	0x25 Switch Binary
	0x26 Switch Multilevel
	0x2C Scene Actuator Conf
	0x2B Scene Activation
	0x70 Configuration 
	0x72 Manufacturer Specific
	0x73 Powerlevel
	0x77 Node Naming
	0x85 Association
	0x86 Version
	0xEF MarkMark
	0x82 Hail

*/

metadata {
	definition (name: "AeonDimmer", namespace: "EugeneKardash", 
    	author: "eugene@kardash.net") {
		capability "Actuator"
		capability "Switch"
		capability "Refresh"
		capability "Sensor"
        capability "Alarm" 
        capability "Switch Level"
        command "levelUp"
        command "levelDown"
		inClusters: "0x26 0x27 0x2C 0x2B 0x70 0x85 0x72 0x86 0xEF 0x82"

	}
    preferences {
       	input name: "Notifications_param_80", type: "enum", title: "State Change Notifications (param 80)", description: "(0:nothing, 1:hail CC, 2:basic CC report, other: ignore)", required: true, options: ["Nothing","Hail","Report"]
        input name: "Button_Turn_Mode_param_120", type: "enum", title: "External Button Turn Mode (param 120)", description: "(0:Momentary button mode, 1: 2 state switch mode, 2: 3 way switch mode, 255: Unidentified mode, Other: ignore)", required: true, options: ["Momentary","Two State Switch","Three Way Switch","Unidentified Mode"]
        input name: "dInterval", type: "enum", title: "Set dimmer button offset", description: "Value per click: (1, 5, 10, or 25). Default: 10", required: false, options:["1","5","10","25"]
    }
  

	// simulator metadata
	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"

		// reply messages
		reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
		reply "200100,delay 100,2502": "command: 2503, payload: 00"
	}

	// tile definitions
tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true, canChangeBackground: true) {
			state "on", label:'${name}', action:"switch.off", backgroundColor:"#79b821"
			state "off", label:'${name}', action:"switch.on", backgroundColor:"#ffffff"
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		valueTile("lValue", "device.level", inactiveLabel: true, height:1, width:1, decoration: "flat") {
            state "levelValue", label:'${currentValue}%', unit:""
        }
        standardTile("lUp", "device.switchLevel", inactiveLabel: false,decoration: "flat", canChangeIcon: false) {
            state "default", action:"levelUp", icon:"st.illuminance.illuminance.bright"
        }
        standardTile("lDown", "device.switchLevel", inactiveLabel: false,decoration: "flat", canChangeIcon: false) {
            state "default", action:"levelDown", icon:"st.illuminance.illuminance.light"
        }

		main(["switch"])
        details(["switch", "lUp", "lDown","lValue","refresh"])
	}
 }

def parse(String description) {
	//log.debug "res:${description.inspect()}"
	def result = null
	def cmd = zwave.parse(description, [0x20: 1, 0x70: 1])
    //log.debug "res:${item1.inspect()}"
    if (cmd.hasProperty("value")) {
		result = createEvent(cmd)
	}
    //log.debug "res:${item1.inspect()}"
	return result
}


def createEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	//aeons return this when in mode 2
    def dName = device.displayName
    def dValue = cmd.value ? "on" : "off"
    def dSC = isStateChange(device, "switch", dValue)
    def item1 = [
    		name			: "switch"
            ,value			: dValue
            ,linkText		: dName
            ,descriptionText: "${dName} was turned ${dValue}"
            ,isStateChange	: dSC
            ,displayed		: dSC
    ]
    	
	def result = [item1]
    
    //log.debug "cr:${item1.inspect()}"
	
    if (cmd.value > 15) {
		def item2 = new LinkedHashMap(item1)
		item2.name = "level"
		item2.value = cmd.value as String
		item2.unit = "%"
		item2.descriptionText = "${item1.linkText} dimmed ${item2.value} %"
		item2.canBeCurrentState = true
		item2.isStateChange = isStateChange(device, item2.name, item2.value)
		item2.displayed = false
		result << item2
	}
    //i still don't know what this is...
    for (int i = 0; i < result.size(); i++) {
		result[i].type = "physical"
	}
    //log.debug "resultInspect:${result.inspect()}"
    return  result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in or don't know about
    //log.debug "udf:${cmd.inspect()}"
	return [:]
}

def levelUp(){
	def int step = (settings.dInterval ?:10).toInteger() //set 10 as default
    def int crntLevel = device.currentValue("level")
    def int nextLevel = crntLevel - (crntLevel % step) + step  
    state.alarmTriggered = 0
    if( nextLevel > 99)	nextLevel = 99
    //log.debug "crnt:${crntLevel} next:${nextLevel}"
    //Don't request a config report when advanced reporting is enabled
    if (settings.Notifications_param_80 in ["Hail","Report"]) zwave.basicV1.basicSet(value: nextLevel).format()
    else delayBetween ([zwave.basicV1.basicSet(value: nextLevel).format(), zwave.basicV1.basicGet().format()], 5000)
}

def levelDown(){
	def int step = (settings.dInterval ?:10).toInteger() //set 10 as default
	def int crntLevel = device.currentValue("level")
    def int nextLevel //= crntLevel - (crntLevel % step) - step  
    state.alarmTriggered = 0
    if (crntLevel == 99) {
    	nextLevel = 100 - step
    } else {
    	nextLevel = crntLevel - (crntLevel % step) - step
    }
	//log.debug "crnt:${crntLevel} next:${nextLevel}"
	if (nextLevel == 0){
    	off()
    }
    else
    {
    	//Don't request a config report when advanced reporting is enabled
    	if (settings.Notifications_param_80 in ["Hail","Report"]) zwave.basicV1.basicSet(value: nextLevel).format()
    	else delayBetween ([zwave.basicV1.basicSet(value: nextLevel).format(), zwave.basicV1.basicGet().format()], 5000)
    }
}

def setLevel(value) {
	//Don't request a config report when advanced reporting is enabled
	if (settings.Notifications_param_80 in ["Hail","Report"]) zwave.basicV1.basicSet(value: value).format()
    else delayBetween ([zwave.basicV1.basicSet(value: value).format(), zwave.basicV1.basicGet().format()], 5000)
}
def setLevel(value, duration) {
	def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
	//Don't request a config report when advanced reporting is enabled
	if (settings.Notifications_param_80 in ["Hail","Report"]) zwave.switchMultilevelV2.switchMultilevelSet(value: value, dimmingDuration: 0).format()
    else delayBetween ([zwave.switchMultilevelV2.switchMultilevelSet(value: value, dimmingDuration: duration).format(), zwave.basicV1.basicGet().format()], 0)
}

def on() {
    //reset alarm trigger
    state.alarmTriggered = 0
	//Don't request a config report when advanced reporting is enabled
	if (settings.Notifications_param_80 in ["Hail","Report"]) zwave.basicV1.basicSet(value: 0xFF).format()
    else delayBetween([zwave.basicV1.basicSet(value: 0xFF).format(), zwave.basicV1.basicGet().format()], 50)
}

def off() {
    //log.debug "at:${state.alarmTriggered} swf:${state.stateWhenFlashed}"
    
    //override alarm off command from smartApps
    if (state.alarmTriggered == 1 && state.stateWhenFlashed == 1) {
    	state.alarmTriggered = 0
    } else {
    	//Don't request a config report when advanced reporting is enabled
    	if (settings.Notifications_param_80 in ["Hail","Report"]) zwave.basicV1.basicSet(value: 0x00).format()
		else delayBetween ([zwave.basicV1.basicSet(value: 0x00).format(),  zwave.basicV1.basicGet().format()], 50)
    }
}

def refresh() {
     return zwave.basicV1.basicGet().format()
}

//capture preference changes
def updated() {
    //log.debug "before settings: ${settings.inspect()}, state: ${state.inspect()}" 
    
    //get requested reporting preferences
    Short p80
    switch (settings.Notifications_param_80) {
		case "Nothing":
			p80 = 0
            break
		case "Hail":
			p80 = 1
            break
		case "Report":
			p80 = 2
            break
		default:
			p80 = 0 // Nothing
            break
	}    
    
	//get requested switch function preferences
    Short p120
    switch (settings.Button_Turn_Mode_param_120) {
		case "Momentary":
			p120 = 0
            break
		case "Two State Switch":
			p120 = 1
            break
		case "Three Way Switch":
			p120 = 2
            break
		default:
			p120 = 255 // Unidentified mode
            break
	}    
  
	//update if the settings were changed
    if (p80 != state.Notifications_param_80)	{
    	//log.debug "update 80:${p80}"
        state.Notifications_param_80 = p80 
        return response(zwave.configurationV1.configurationSet(parameterNumber: 80, size: 1, configurationValue: [p80]).format())
    }
	if (p120 != state.Button_Turn_Mode_param_120)	{
    	//log.debug "update 120:${p120}"
        state.Button_Turn_Mode_param_120 = p120
        return response(zwave.configurationV1.configurationSet(parameterNumber: 120, size: 1, configurationValue: [p120]).format())
    }

	//log.debug "after settings: ${settings.inspect()}, state: ${state.inspect()}"
}

def configure() {
	settings.Notifications_param_80 = "Report"
    settings.Button_Turn_Mode_param_120 = "Toggle"
	delayBetween([
		zwave.configurationV1.configurationSet(parameterNumber: 80, size: 1, configurationValue: 2).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 120, size: 1, configurationValue: 1).format()
	])
}
