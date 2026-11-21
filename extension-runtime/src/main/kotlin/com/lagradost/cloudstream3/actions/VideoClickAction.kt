package com.lagradost.cloudstream3.actions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.LinkLoadingResult
import com.lagradost.cloudstream3.ResultEpisode
import com.lagradost.cloudstream3.UiText
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import kotlin.reflect.jvm.jvmName

object VideoClickActionHolder {
    val allVideoClickActions = threadSafeListOf<VideoClickAction>()

    private const val ACTION_ID_OFFSET = 1000

    fun makeOptionMap(activity: Activity?, video: ResultEpisode) = allVideoClickActions
        .mapIndexed { id, it -> it to id + ACTION_ID_OFFSET }
        .filter { it.first.shouldShowSafe(activity, video) }
        .map { it.first.name to it.second }

    fun getActionById(id: Int): VideoClickAction? = allVideoClickActions.getOrNull(id - ACTION_ID_OFFSET)
    fun getByUniqueId(uniqueId: String): VideoClickAction? = allVideoClickActions.firstOrNull { it.uniqueId() == uniqueId }
    fun uniqueIdToId(uniqueId: String?): Int? {
        if (uniqueId == null) return null
        return allVideoClickActions
            .mapIndexed { id, it -> it to id + ACTION_ID_OFFSET }
            .firstOrNull { it.first.uniqueId() == uniqueId }
            ?.second
    }
    fun getPlayers(activity: Activity? = null) = allVideoClickActions.filter { it.isPlayer && it.shouldShowSafe(activity, null) }
}

abstract class VideoClickAction {
    abstract val name: UiText
    open val oneSource : Boolean = false
    open val isPlayer: Boolean = false
    open val sourceTypes: Set<ExtractorLinkType> = ExtractorLinkType.entries.toSet()
    var sourcePlugin: String? = null

    @Throws
    suspend fun <T> uiThread(callable : Callable<T>) : T? {
        val future = FutureTask{
            try {
                Result.success(callable.call())
            } catch (t : Throwable) {
                Result.failure(t)
            }
        }
        // In the bridge, we just execute directly if we don't have an activity context available
        // Pluggins shouldn't really rely on uiThread context inside bridging
        future.run()
        val result = withContext(Dispatchers.IO) { future.get() }
        return result.getOrThrow()
    }

    @Throws
    suspend fun launchResult(intent : Intent?, options : ActivityOptionsCompat? = null) {
        // No-op in bridge for now
    }

    @Throws
    suspend fun launch(intent : Intent?, bundle : Bundle? = null) {
        // No-op in bridge for now
    }

    fun uniqueId() = "$sourcePlugin:${this::class.jvmName}"

    @Throws
    abstract fun shouldShow(context: Context?, video: ResultEpisode?): Boolean

    fun shouldShowSafe(context: Context?, video: ResultEpisode?): Boolean {
        return try {
            shouldShow(context,video)
        } catch (t : Throwable) {
            logError(t)
            false
        }
    }

    @Throws
    abstract suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?)

    fun runActionSafe(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) = ioSafe {
        try {
            runAction(context, video, result, index)
        } catch (t : Throwable) {
            logError(t)
        }
    }
}
