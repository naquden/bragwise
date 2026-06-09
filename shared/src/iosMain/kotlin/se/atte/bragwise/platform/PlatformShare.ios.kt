package se.atte.bragwise.platform

import platform.Foundation.NSURL
import platform.Foundation.setValue
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.darwin.NSObject

class IosPlatformShare : PlatformShare {
    override fun send(url: String, title: String, subject: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        val items = listOf<Any>(title, nsUrl)
        val vc = UIActivityViewController(activityItems = items, applicationActivities = null)
        // Mail-subject hint. UIActivityViewController forwards the "subject" KVC key to
        // share targets that honour it (Mail, etc.); others ignore it. Matches Android's
        // Intent.EXTRA_SUBJECT (PlatformShare.android.kt). Routed through the NSObject
        // receiver so Kotlin picks NSKeyValueCoding.setValue, not its delegate operator.
        if (subject.isNotEmpty()) {
            (vc as NSObject).setValue(subject, forKey = "subject")
        }
        @Suppress("DEPRECATION")
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        root?.presentViewController(vc, animated = true, completion = null)
    }
}
