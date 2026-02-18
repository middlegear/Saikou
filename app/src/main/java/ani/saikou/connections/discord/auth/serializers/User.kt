package ani.saikou.connections.discord.auth.serializers

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class User(
    @SerialName("id")
    val id: String?=null,
    @SerialName("username")
    val username: String?=null,
    @SerialName("global_name")
    val globalName: String? = null,
    @SerialName("avatar")
    val avatar: String? = null,
    @SerialName("discriminator")
    val discriminator: String? = "0"
) {

    fun getAvatarUrl(size: Int = 128): String? {
        val uid = id ?: return null
        val hash = avatar ?: return null

        val extension = if (hash.startsWith("a_")) "gif" else "png"
        return "https://cdn.discordapp.com/avatars/$uid/$hash.$extension?size=$size"
    }
}