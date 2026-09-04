package ani.saikou.connections.anilist.room



import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnilistCacheDao {

    @Query("SELECT * FROM anilist_query_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): AnilistCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: AnilistCacheEntity)

    @Query("DELETE FROM anilist_query_cache WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM anilist_query_cache")
    suspend fun clearAll()
}