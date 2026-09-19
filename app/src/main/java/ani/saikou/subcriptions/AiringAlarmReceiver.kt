package ani.saikou.subcriptions

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import ani.saikou.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiringAlarmReceiver : BroadcastReceiver() {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Saikou:AiringAlarmReceiverWakeLock"
        ).apply {
            acquire(15_000L)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                App.context = app

                Subscription.notifyScheduledPastDue(app)
                Subscription.armUpcoming(app)
                SubscriptionWorker.enqueueNow(app)
            } catch (_: Throwable) {
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                pending.finish()
            }
        }
    }
}