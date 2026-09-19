package ani.saikou.connections.anilist.room.subscriptions

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "airing_schedule")
data class AiringScheduleEntity(
    @PrimaryKey val mediaId: Int,
    val title: String,
    val episodeNumber: Int,
    val airingAt: Long,
    val lastNotifiedAt: Long = 0L,
    val scheduledAt: Long = 0L,
)