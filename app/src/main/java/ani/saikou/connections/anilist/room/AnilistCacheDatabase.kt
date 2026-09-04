package ani.saikou.connections.anilist.room


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AnilistCacheEntity::class], version = 1, exportSchema = false)
abstract class AnilistCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): AnilistCacheDao

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
                    // Cache is disposable data, safe to just wipe & recreate on schema bumps
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}