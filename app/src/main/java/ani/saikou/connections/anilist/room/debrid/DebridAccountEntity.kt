package ani.saikou.connections.anilist.room.debrid


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debrid_accounts")
data class DebridAccountEntity(
    @PrimaryKey val provider: String,
    val apiKey: String? = null,
    val clientId: String? = null,
    val token: String? = null,
    val isActive: Boolean = false,
)