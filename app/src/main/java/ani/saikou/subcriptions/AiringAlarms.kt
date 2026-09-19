package ani.saikou.subcriptions

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri

object AiringAlarms {
    const val ACTION_AIRING = "ani.saikou.action.AIRING_ALARM"

    private fun pendingIntent(context: Context, mediaId: Int): PendingIntent {
        val intent = Intent(context, AiringAlarmReceiver::class.java).apply {
            action = ACTION_AIRING
            data = "saikou://airing/$mediaId".toUri()
            putExtra("mediaId", mediaId)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, mediaId, intent, flags)
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, mediaId: Int, airingAtSeconds: Long) {
        val triggerAt = airingAtSeconds * 1000L + 1_000L
        if (triggerAt <= System.currentTimeMillis()) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, mediaId)
        val exact = canScheduleExact(context)

        try {
            when {
                exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }

                exact ->
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }

                else -> am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    fun cancel(context: Context, mediaId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, mediaId))
    }
}