package ani.saikou.connections.anilist.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ani.saikou.connections.anilist.room.subscriptions.AiringScheduleDao
import ani.saikou.connections.anilist.room.subscriptions.AiringScheduleEntity

@Database(
    entities = [AnilistCacheEntity::class, AiringScheduleEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AnilistCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): AnilistCacheDao
    abstract fun airingScheduleDao(): AiringScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AnilistCacheDatabase? = null

        fun getInstance(context: Context): AnilistCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnilistCacheDatabase::class.java,
                    "anilist_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}