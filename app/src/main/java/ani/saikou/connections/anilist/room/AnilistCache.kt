package ani.saikou.connections.anilist.room

import android.content.Context
import android.util.Log
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistent cache for AniList GraphQL query responses.
 *
 * Not every query should go through here — anything that needs to be as fresh as
 * possible (search, progress/list-status mutations & their immediate re-fetches)
 * should call [Anilist.executeQuery] with `cache = null` to skip this entirely.
 */
object AnilistCache {
    const val SIX_HOURS_MINUTES = 6 * 60

    private const val TAG = "AnilistCache"

    private lateinit var dao: AnilistCacheDao
    private var initialized = false

    fun init(context: Context) {
        if (initialized) {
            Log.d(TAG, "init() called again, already initialized — ignoring")
            return
        }
        dao = AnilistCacheDatabase.getInstance(context).cacheDao()
        initialized = true
        Log.d(TAG, "initialized")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun key(query: String, variables: String, tokenPart: String?): String =
        sha256("$query|$variables|$tokenPart")

    /** Returns the cached raw JSON body for [key], or null on a miss/expiry. */
    suspend fun get(key: String): String? {
        if (!initialized) {
            Log.d(TAG, "get($key) skipped — not initialized")
            return null
        }
        return withContext(Dispatchers.IO) {
            val entry = dao.get(key)
            if (entry == null) {
                Log.d(TAG, "MISS  key=$key")
                return@withContext null
            }
            val now = System.currentTimeMillis()
            if (now > entry.expiresAt) {
                val staleForMs = now - entry.expiresAt
                Log.d(TAG, "EXPIRED key=$key staleFor=${staleForMs}ms")
                null
            } else {
                val freshForMs = entry.expiresAt - now
                Log.d(TAG, "HIT   key=$key freshFor=${freshForMs}ms")
                entry.rawJson
            }
        }
    }

    suspend fun put(key: String, rawJson: String, ttlMinutes: Int) {
        if (!initialized) {
            Log.d(TAG, "put($key) skipped — not initialized")
            return
        }
        if (ttlMinutes <= 0) {
            Log.d(TAG, "put($key) skipped — ttlMinutes=$ttlMinutes")
            return
        }
        withContext(Dispatchers.IO) {
            dao.put(
                AnilistCacheEntity(
                    cacheKey = key,
                    rawJson = rawJson,
                    expiresAt = System.currentTimeMillis() + ttlMinutes * 60_000L
                )
            )
            Log.d(TAG, "PUT   key=$key ttl=${ttlMinutes}m bytes=${rawJson.length}")
        }
    }

    suspend fun clear() {
        if (!initialized) {
            Log.d(TAG, "clear() skipped — not initialized")
            return
        }
        withContext(Dispatchers.IO) {
            dao.clearAll()
            Log.d(TAG, "cleared all entries")
        }
    }

    suspend fun purgeExpired() {
        if (!initialized) {
            Log.d(TAG, "purgeExpired() skipped — not initialized")
            return
        }
        withContext(Dispatchers.IO) {
            dao.deleteExpired()
            Log.d(TAG, "purged expired entries")
        }
    }
}