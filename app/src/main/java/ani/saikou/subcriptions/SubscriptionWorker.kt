package ani.saikou.subcriptions

import android.content.Context
import androidx.work.*
import ani.saikou.loadData
import ani.saikou.subcriptions.Subscription.Companion.DEFAULT_TIME
import ani.saikou.subcriptions.Subscription.Companion.TIME_MINUTES
import java.util.concurrent.TimeUnit

class SubscriptionWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val allowNotify = inputData.getBoolean(KEY_ALLOW_NOTIFY, true)
        val force = inputData.getBoolean(KEY_FORCE, false)
        Subscription.perform(context, allowNotify = allowNotify, forceRefresh = force)
        return Result.success()
    }

    companion object {
        private const val SUBSCRIPTION_WORK_NAME = "work_subscription"
        private const val REFRESH_NOW_WORK_NAME = "work_subscription_refresh_now"

        const val KEY_ALLOW_NOTIFY = "allow_notify"
        const val KEY_FORCE = "force_refresh"

        private fun buildPeriodicRequest(context: Context): PeriodicWorkRequest? {
            val curTime = loadData<Int>("subscriptions_time", context) ?: DEFAULT_TIME
            val minutesValue = TIME_MINUTES.getOrNull(curTime) ?: TIME_MINUTES[DEFAULT_TIME]
            if (minutesValue <= 0L) return null

            val intervalMinutes = minutesValue.coerceAtLeast(15L)

            return PeriodicWorkRequestBuilder<SubscriptionWorker>(
                repeatInterval = intervalMinutes,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            ).apply {
                addTag(SUBSCRIPTION_WORK_NAME)
                setInputData(workDataOf(KEY_ALLOW_NOTIFY to true, KEY_FORCE to false))
            }.build()
        }

        fun enqueue(context: Context) {
            val request = buildPeriodicRequest(context)
            if (request == null) {
                WorkManager.getInstance(context).cancelUniqueWork(SUBSCRIPTION_WORK_NAME)
                return
            }
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SUBSCRIPTION_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun updateInterval(context: Context) {
            val request = buildPeriodicRequest(context)
            if (request == null) {
                WorkManager.getInstance(context).cancelUniqueWork(SUBSCRIPTION_WORK_NAME)
                return
            }
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SUBSCRIPTION_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueNow(context: Context, force: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_ALLOW_NOTIFY to true,
                        KEY_FORCE to force
                    )
                )
                .addTag(REFRESH_NOW_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                REFRESH_NOW_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueWithDelay(context: Context, delayMinutes: Long = 1L) {
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        KEY_ALLOW_NOTIFY to true,
                        KEY_FORCE to true
                    )
                )
                .addTag(REFRESH_NOW_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                REFRESH_NOW_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueOneTimeWorkAt(context: Context, mediaId: Int, targetAiringAtSeconds: Long) {
            val nowSeconds = System.currentTimeMillis() / 1000
            val diffSeconds = targetAiringAtSeconds - nowSeconds

            if (diffSeconds < 0L) return

            val uniqueWorkName = "work_subscription_show_$mediaId"

            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInitialDelay(diffSeconds, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_ALLOW_NOTIFY to true, KEY_FORCE to false))
                .addTag(uniqueWorkName)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}