package ani.saikou.connections.anilist.room


import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single cached AniList GraphQL response, keyed on a hash of
 * (query + variables + auth context)
 */
@Entity(tableName = "anilist_query_cache")
data class AnilistCacheEntity(
    @PrimaryKey val cacheKey: String,
    val rawJson: String,
    val expiresAt: Long,
    val createdAt: Long = System.currentTimeMillis()
)