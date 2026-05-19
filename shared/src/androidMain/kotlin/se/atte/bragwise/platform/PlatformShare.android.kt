package se.atte.bragwise.platform

import android.content.Context
import android.content.Intent

class AndroidPlatformShare(private val context: Context) : PlatformShare {
    override fun send(url: String, title: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        context.startActivity(
            Intent.createChooser(intent, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
