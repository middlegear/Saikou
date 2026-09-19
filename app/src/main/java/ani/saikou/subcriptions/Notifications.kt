package ani.saikou.subcriptions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import ani.saikou.R
import ani.saikou.connections.anilist.UrlMedia

@Suppress("MemberVisibilityCanBePrivate", "unused")
class Notifications {

    companion object {
        private const val CHANNEL_ID = "anime_notifications"
        private const val CHANNEL_NAME = "New Episodes"

        fun openSettings(context: Context, channelId: String? = CHANNEL_ID): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    if (channelId != null) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                    else Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                }
                context.startActivity(intent)
                true
            } else false
        }

        fun getIntent(context: Context, mediaId: Int): PendingIntent {
            val notifyIntent = Intent(context, UrlMedia::class.java).apply {
                putExtra("media", mediaId)
                action = mediaId.toString()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            return PendingIntent.getActivity(
                context,
                mediaId,
                notifyIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
        }

        fun createChannel(
            context: Context,
            id: String = CHANNEL_ID,
            name: String = CHANNEL_NAME,
            silent: Boolean = false
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager =
                    context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                val importance = if (!silent) {
                    NotificationManager.IMPORTANCE_HIGH
                } else {
                    NotificationManager.IMPORTANCE_LOW
                }

                var mChannel = notificationManager.getNotificationChannel(id)
                if (mChannel == null) {
                    mChannel = NotificationChannel(id, name, importance).apply {
                        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(mChannel)
                }
            }
        }

        fun deleteChannel(context: Context, id: String = CHANNEL_ID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager =
                    context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.deleteNotificationChannel(id)
            }
        }

        fun getNotification(
            context: Context,
            showTitle: String,
            episodeNumber: String,
            mediaId: Int,
            silent: Boolean = false
        ): NotificationCompat.Builder {
            createChannel(context, CHANNEL_ID, CHANNEL_NAME, silent)

            val contentText = "Episode $episodeNumber has released!"

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setPriority(if (!silent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.monochrome)
                .setContentTitle(showTitle)
                .setContentText(contentText)
                .setContentIntent(getIntent(context, mediaId))
                .setAutoCancel(true)
        }
    }
}