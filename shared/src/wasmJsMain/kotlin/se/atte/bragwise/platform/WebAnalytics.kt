package se.atte.bragwise.platform

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import se.atte.bragwise.firebase.logAnalyticsEvent

/**
 * Real GA4 web analytics. Each [AnalyticsEvent] maps 1:1 to a Firebase
 * `logEvent(name, params)` call, with a `platform: "web"` param added to
 * every event so web traffic is distinguishable from mobile in GA4.
 */
class WebAnalytics : Analytics {

    init {
        // Fire a load-time session event so raw web visits register in GA4 even
        // before any in-app action (page_view is GA4's canonical visit event).
        logAnalyticsEvent("page_view", paramsJson(emptyMap()))
    }

    override fun log(event: AnalyticsEvent) {
        logAnalyticsEvent(event.name, paramsJson(event.params))
    }

    override fun setIsGuest(isGuest: Boolean) {
        // Mirror mobile's user-property intent as a lightweight event param.
        logAnalyticsEvent("set_is_guest", paramsJson(mapOf("is_guest" to isGuest.toString())))
    }

    /** Serialize event params (+ platform tag) to a JSON object string for logEvent. */
    private fun paramsJson(params: Map<String, Any>): String {
        val obj = buildMap<String, kotlinx.serialization.json.JsonElement> {
            put("platform", JsonPrimitive("web"))
            params.forEach { (k, v) ->
                put(
                    k,
                    when (v) {
                        is Int -> JsonPrimitive(v)
                        is Long -> JsonPrimitive(v)
                        is Boolean -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        else -> JsonPrimitive(v.toString())
                    },
                )
            }
        }
        return Json.encodeToString(JsonObject.serializer(), JsonObject(obj))
    }
}
