package ani.saikou.subcriptions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ani.saikou.*
import ani.saikou.connections.anilist.Anilist
import ani.saikou.connections.anilist.room.AnilistCacheDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class Subscription {
    companion object {
        const val DEFAULT_TIME = 6

        @JvmField
        val TIME_MINUTES = arrayOf(
            0L, 5, 10, 15, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440
        )

        private const val PROGRESS_NOTIFICATION_ID = 100
        private const val REFRESH_INTERVAL_FLOOR_MINUTES = 60L
        private const val LAST_REFRESHED_KEY = "airing_schedule_last_refreshed"

        private val performMutex = Mutex()
        private val notifyMutex = Mutex()

        fun Context.startSubscription(force: Boolean = true) {
            SubscriptionWorker.enqueue(this)
            if (force) {
                refreshSubscriptionNow()
            } else {
                SubscriptionWorker.enqueueNow(this)
            }
        }

        fun Context.refreshSubscriptionNow() {
            saveData(LAST_REFRESHED_KEY, 0L, this)
            SubscriptionWorker.enqueueNow(this, force = true)
        }

        fun Context.refreshSubscriptionDelayed(delayMinutes: Long = 1L) {
            saveData(LAST_REFRESHED_KEY, 0L, this)
            SubscriptionWorker.enqueueWithDelay(this, delayMinutes)
        }

        private fun scheduleDao(context: Context) =
            AnilistCacheDatabase.getInstance(context).airingScheduleDao()

        private fun canPostNotifications(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        suspend fun perform(context: Context, allowNotify: Boolean = true, forceRefresh: Boolean = false) {
            performMutex.withLock {
                try {
                    App.context = context.applicationContext

                    try {
                        refreshAiringScheduleIfStale(context, force = forceRefresh)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        logError(t)
                    }

                    if (allowNotify) {
                        notifyScheduledPastDue(context)
                    }

                    armUpcoming(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logError(t)
                }
            }
        }

        suspend fun notifyScheduledPastDue(context: Context) =
            notifyMutex.withLock { notifyScheduledPastDueLocked(context) }

        @SuppressLint("MissingPermission")
        private suspend fun notifyScheduledPastDueLocked(context: Context) {
            if (!canPostNotifications(context)) return

            val dao = scheduleDao(context)
            val nowSeconds = System.currentTimeMillis() / 1000
            val ready = dao.getReadyToNotify(nowSeconds)

            if (ready.isEmpty()) return

            val notificationManager = NotificationManagerCompat.from(context)
            var fired = 0

            for (entry in ready.sortedBy { it.airingAt }) {
                val wasScheduled = entry.scheduledAt > 0L

                if (!wasScheduled) {
                    dao.markNotified(entry.mediaId, entry.airingAt)
                    continue
                }

                val builder = Notifications.getNotification(
                    context = context,
                    showTitle = entry.title,
                    episodeNumber = entry.episodeNumber.toString(),
                    mediaId = entry.mediaId,
                    silent = false
                )

                val notifId = PROGRESS_NOTIFICATION_ID + 1 + entry.mediaId
                notificationManager.notify(notifId, builder.build())
                fired++

                dao.markNotified(entry.mediaId, entry.airingAt)
            }

            if (fired > 0) saveData(LAST_REFRESHED_KEY, 0L, context)
        }

        private suspend fun refreshAiringScheduleIfStale(
            context: Context,
            force: Boolean = false
        ) {
            val userIntervalMinutes =
                loadData<Int>("subscriptions_time", context)?.let { TIME_MINUTES.getOrNull(it) }
                    ?: TIME_MINUTES[DEFAULT_TIME]
            val refreshIntervalMinutes = maxOf(userIntervalMinutes, REFRESH_INTERVAL_FLOOR_MINUTES)

            val lastRefreshedAt = loadData<Long>(LAST_REFRESHED_KEY, context) ?: 0L
            val nowMillis = System.currentTimeMillis()

            if (!force && nowMillis - lastRefreshedAt < refreshIntervalMinutes * 60_000L) {
                return
            }

            Anilist.getSavedToken(context)

            if (Anilist.token == null) return

            if (Anilist.userid == null) {
                val ok = Anilist.query.getUserData()
                if (!ok) return
            }

            val userId = Anilist.userid ?: return
            val fetchedRows = Anilist.query.fetchAiringSchedule(userId)

            if (fetchedRows.isEmpty()) {
                saveData(LAST_REFRESHED_KEY, nowMillis, context)
                return
            }

            val dao = scheduleDao(context)
            val before = dao.getAll().associateBy { it.mediaId }
            val nowSeconds = nowMillis / 1000

            val rowsToSave = fetchedRows.map { newEntry ->
                val prev = before[newEntry.mediaId]
                when {
                    prev != null && prev.airingAt == newEntry.airingAt ->
                        newEntry.copy(
                            lastNotifiedAt = prev.lastNotifiedAt,
                            scheduledAt = prev.scheduledAt
                        )

                    newEntry.airingAt <= nowSeconds ->
                        newEntry.copy(scheduledAt = newEntry.airingAt)

                    else -> newEntry
                }
            }

            dao.upsertAll(rowsToSave)
            dao.pruneNotIn(rowsToSave.map { it.mediaId })
            saveData(LAST_REFRESHED_KEY, nowMillis, context)
        }

        suspend fun armUpcoming(context: Context) = withContext(Dispatchers.IO) {
            val dao = scheduleDao(context)
            val nowSeconds = System.currentTimeMillis() / 1000
            val upcoming = dao.getAll().filter { it.airingAt > nowSeconds }.sortedBy { it.airingAt }

            if (upcoming.isEmpty()) return@withContext

            val earliestShow = upcoming.first()
            AiringAlarms.schedule(context, earliestShow.mediaId, earliestShow.airingAt)
            dao.markScheduled(earliestShow.mediaId, earliestShow.airingAt)

            upcoming.drop(1).forEach { show ->
                SubscriptionWorker.enqueueOneTimeWorkAt(
                    context = context,
                    mediaId = show.mediaId,
                    targetAiringAtSeconds = show.airingAt
                )
                dao.markScheduled(show.mediaId, show.airingAt)
            }
        }
    }
}