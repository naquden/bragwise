package se.atte.bragwise.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectGetMidX
import platform.CoreGraphics.CGRectGetMidY
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.setValue
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPopoverArrowDirectionUnknown
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
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

        val window = keyWindow() ?: return
        var top: UIViewController? = window.rootViewController
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }

        // On iPad, UIActivityViewController presents as a popover and crashes
        // (NSGenericException) unless a sourceView/sourceRect is set. Anchor it to the
        // centre of the presenting view; permittedArrowDirections = 0 (Unknown) makes
        // UIKit centre the popover with no arrow instead of pointing at a real control.
        vc.popoverPresentationController?.let { popover ->
            val anchor = top?.view ?: window
            popover.sourceView = anchor
            popover.sourceRect = CGRectMake(
                x = CGRectGetMidX(anchor.bounds),
                y = CGRectGetMidY(anchor.bounds),
                width = 0.0,
                height = 0.0,
            )
            popover.permittedArrowDirections = UIPopoverArrowDirectionUnknown
        }

        top?.presentViewController(vc, animated = true, completion = null)
    }

    private fun keyWindow(): UIWindow? =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows }
            .filterIsInstance<UIWindow>()
            .firstOrNull { it.isKeyWindow() }
}
