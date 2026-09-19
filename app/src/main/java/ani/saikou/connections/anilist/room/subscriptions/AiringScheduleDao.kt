package ani.saikou.connections.anilist.room.subscriptions

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AiringScheduleDao {

    @Query("SELECT * FROM airing_schedule")
    suspend fun getAll(): List<AiringScheduleEntity>

    @Query("SELECT * FROM airing_schedule WHERE airingAt <= (:now + 3) AND lastNotifiedAt < airingAt")
    suspend fun getReadyToNotify(now: Long): List<AiringScheduleEntity>

    @Upsert
    suspend fun upsertAll(rows: List<AiringScheduleEntity>)

    @Query("DELETE FROM airing_schedule WHERE mediaId NOT IN (:ids)")
    suspend fun pruneNotIn(ids: List<Int>)

    @Query("UPDATE airing_schedule SET lastNotifiedAt = :airingAt WHERE mediaId = :mediaId")
    suspend fun markNotified(mediaId: Int, airingAt: Long)

    @Query("UPDATE airing_schedule SET scheduledAt = :airingAt WHERE mediaId = :mediaId")
    suspend fun markScheduled(mediaId: Int, airingAt: Long)
}