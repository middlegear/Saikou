package ani.saikou.connections.anilist.room.debrid

import android.content.Context
import androidx.room.withTransaction
import ani.saikou.connections.anilist.room.AnilistCacheDatabase
import kotlinx.coroutines.flow.Flow


class DebridRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db get() = AnilistCacheDatabase.getInstance(appContext)
    private val dao get() = db.debridAccountDao()

    // ------------------------------------------------------------------ reads

    suspend fun getAccount(provider: String): DebridAccountEntity? = dao.get(provider)

    suspend fun getActive(): DebridAccountEntity? = dao.getActive()

    fun observeActive(): Flow<DebridAccountEntity?> = dao.observeActive()

    /**
     * Returns e.g. "realdebrid=KEY" or "putio=CLIENTID@TOKEN" for the Torrentio URL,
     * or null when no provider is active, it isn't supported by Torrentio,
     * or its credentials are incomplete.
     */
    suspend fun activeConfigSegment(): String? = getActive()?.let(::toConfigSegment)

    // ----------------------------------------------------------------- writes

    suspend fun saveAndActivate(
        provider: String,
        apiKey: String? = null,
        clientId: String? = null,
        token: String? = null,
    ) = db.withTransaction {
        dao.clearActive()
        dao.upsert(
            DebridAccountEntity(
                provider = provider,
                apiKey = apiKey,
                clientId = clientId,
                token = token,
                isActive = true,
            ),
        )
    }

    suspend fun setActive(provider: String) = db.withTransaction {
        dao.clearActive()
        dao.markActive(provider)
    }

    /** Deletes the credentials of whichever provider is currently active (used by "None"). */
    suspend fun removeActive() = dao.deleteActive()

    suspend fun remove(provider: String) = dao.delete(provider)

    // -------------------------------------------------------------- conversion

    private fun toConfigSegment(account: DebridAccountEntity): String? {
        val configKey = TORRENTIO_KEYS[account.provider] ?: return null

        val value = if (account.provider == "Put.io") {
            val clientId = account.clientId?.trim().orEmpty()
            val token = account.token?.trim().orEmpty()
            if (clientId.isEmpty() || token.isEmpty()) return null
            "$clientId@$token"
        } else {
            account.apiKey?.trim().orEmpty().ifEmpty { return null }
        }

        return "$configKey=$value"
    }

    companion object {

        private val TORRENTIO_KEYS = mapOf(
            "Real-Debrid" to "realdebrid",
            "Premiumize" to "premiumize",
            "AllDebrid" to "alldebrid",
            "DebridLink" to "debridlink",
            "EasyDebrid" to "easydebrid",
            "Offcloud" to "offcloud",
            "TorBox" to "torbox",
            "Put.io" to "putio",

        )

        val CONFIG_KEYS: Set<String> = TORRENTIO_KEYS.values.toSet()
    }
}