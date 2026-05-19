package se.atte.bragwise.platform

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

class IosPlatformShare : PlatformShare {
    override fun send(url: String, title: String, subject: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        val items = listOf<Any>(title, nsUrl)
        val vc = UIActivityViewController(activityItems = items, applicationActivities = null)
        // Mail-subject hint via KVC would collide with Kotlin's KMutableProperty.setValue
        // extensions on iOS targets — skipped, most share targets ignore it anyway.
        @Suppress("DEPRECATION")
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        root?.presentViewController(vc, animated = true, completion = null)
    }
}
