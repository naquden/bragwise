package se.atte.bragwise.platform

class IosAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit
    override fun setIsGuest(isGuest: Boolean) = Unit
}
