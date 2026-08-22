package ani.saikou.media.anime.mpv

import android.annotation.SuppressLint
import ani.saikou.client
import ani.saikou.others.TheMovieDatabase
import ani.saikou.tryWithSuspend
import kotlinx.serialization.Serializable

class PlayerRepository {

    suspend fun fetchAniSkipTimes(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Long
    ): List<SkipInterval>? {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"

        return tryWithSuspend {
            val response = client.get(url)
            val res = response.parsed<AniSkipResponse>()

            if (res.found) res.results?.let { mapAniSkipToUnified(it) } else null
        }
    }

    private fun mapAniSkipToUnified(stamps: List<AniSkipStamp>): List<SkipInterval> {
        return stamps.map { stamp ->
            val startMs = (stamp.interval.startTime * 1000).toLong()
            val endMs = (stamp.interval.endTime * 1000).toLong()
            SkipInterval(
                startTimeMs = startMs,
                endTimeMs = endMs,
                type = stamp.skipType.toDisplayType(),
                durationMs = endMs - startMs,
                startsAtBeginning = startMs == 0L,
                endsAtMediaEnd = false
            )
        }
    }

    data class SkipInterval(
        val startTimeMs: Long,
        val endTimeMs: Long?,
        val type: String,
        val durationMs: Long? = null,
        val startsAtBeginning: Boolean? = null,
        val endsAtMediaEnd: Boolean? = null
    )

    private fun String.toDisplayType(): String = when (this) {
        "op" -> "Opening"
        "ed" -> "Ending"
        "recap" -> "Recap"
        "mixed-ed" -> "Mixed Ending"
        "mixed-op" -> "Mixed Opening"
        else -> this
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class AniSkipResponse(
        val found: Boolean,
        val results: List<AniSkipStamp>?,
        val message: String?,
        val statusCode: Int
    )

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class AniSkipStamp(
        val interval: AniSkipInterval,
        val skipType: String,
        val skipId: String,
        val episodeLength: Double
    )

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class AniSkipInterval(
        val startTime: Double,
        val endTime: Double
    )
}