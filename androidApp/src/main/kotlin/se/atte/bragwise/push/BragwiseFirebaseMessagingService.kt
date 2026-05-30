package se.atte.bragwise.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.inject
import se.atte.bragwise.MainActivity

class BragwiseFirebaseMessagingService : FirebaseMessagingService() {

    private val push: PushNotifications by inject()

    override fun onNewToken(token: String) {
        push.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val deepLink = message.data["deepLink"]?.takeIf { isTrustedDeepLink(it) }

        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val channelId = message.data["channel"] ?: CHANNEL_DEFAULT

        ensureChannel(channelId)

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deepLink != null) data = Uri.parse(deepLink)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            message.messageId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(message.messageId.hashCode(), notification)
    }

    private fun isTrustedDeepLink(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host in TRUSTED_HOSTS
    }

    private fun ensureChannel(channelId: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) != null) return
        val name = when (channelId) {
            CHANNEL_SOCIAL -> "Friends"
            CHANNEL_CHALLENGES -> "Challenges"
            CHANNEL_RESULTS -> "Results"
            else -> "Bragwise"
        }
        nm.createNotificationChannel(
            NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val CHANNEL_DEFAULT = "bragwise_default"
        const val CHANNEL_SOCIAL = "bragwise_social"
        const val CHANNEL_CHALLENGES = "bragwise_challenges"
        const val CHANNEL_RESULTS = "bragwise_results"

        private val TRUSTED_HOSTS = setOf("bragwise.firebaseapp.com", "bragwise.app")
    }
}
