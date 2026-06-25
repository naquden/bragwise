package se.atte.bragwise.platform

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

class AndroidAnalytics : Analytics {
    private val firebase = Firebase.analytics

    override fun log(event: AnalyticsEvent) {
        firebase.logEvent(event.name) {
            event.params.forEach { (key, value) ->
                when (value) {
                    is String -> param(key, value)
                    is Int -> param(key, value.toLong())
                    is Long -> param(key, value)
                    is Double -> param(key, value)
                }
            }
        }
    }

    override fun setIsGuest(isGuest: Boolean) {
        firebase.setUserProperty("is_guest", isGuest.toString())
    }
}
