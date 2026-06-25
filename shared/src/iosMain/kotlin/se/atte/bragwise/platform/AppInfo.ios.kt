package se.atte.bragwise.platform

import platform.Foundation.NSBundle

actual object AppInfo {
    actual val version: String =
        (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String) ?: "1.0.0"

}
