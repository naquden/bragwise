package se.atte.bragwise.platform

import android.content.Context
import android.content.Intent

/**
 * Android share sheet. The Context is the application context, supplied by the
 * host (composeApp / androidApp) via [AndroidPlatformShareHolder.appContext]
 * before the first share fires. Avoiding a static Context import keeps
 * commonMain/iosMain unaware of Android types.
 */
class AndroidPlatformShare(private val context: () -> Context) : PlatformShare {
    override fun send(url: String, title: String, subject: String) {
        val ctx = context()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        ctx.startActivity(
            Intent.createChooser(intent, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

object AndroidPlatformShareHolder {
    @Volatile var appContext: Context? = null
}

actual fun createPlatformShare(): PlatformShare =
    AndroidPlatformShare(context = {
        AndroidPlatformShareHolder.appContext
            ?: error("AndroidPlatformShareHolder.appContext not initialised")
    })
