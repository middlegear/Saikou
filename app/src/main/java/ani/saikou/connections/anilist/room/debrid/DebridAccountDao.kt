package ani.saikou.connections.anilist.room.debrid

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DebridAccountDao {

    @Query("SELECT * FROM debrid_accounts WHERE provider = :provider LIMIT 1")
    suspend fun get(provider: String): DebridAccountEntity?

    @Query("SELECT * FROM debrid_accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): DebridAccountEntity?

    @Query("SELECT * FROM debrid_accounts WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<DebridAccountEntity?>

    @Upsert
    suspend fun upsert(account: DebridAccountEntity)

    @Query("UPDATE debrid_accounts SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE debrid_accounts SET isActive = 1 WHERE provider = :provider")
    suspend fun markActive(provider: String)

    @Query("DELETE FROM debrid_accounts WHERE provider = :provider")
    suspend fun delete(provider: String)

    @Query("DELETE FROM debrid_accounts WHERE isActive = 1")
    suspend fun deleteActive()
}