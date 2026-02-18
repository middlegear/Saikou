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

    fun getAvatarUrl(): String {
        
        val userId = id ?: return "https://cdn.discordapp.com/embed/avatars/0.png"
        return if (avatar != null) {
            val extension = if (avatar.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/avatars/$userId/$avatar.$extension"
        } else {

            val index = if (discriminator == "0" || discriminator == null) {

                (userId.toLongOrNull()?.let { it shr 22 } ?: 0L) % 6
            } else {
                (discriminator.toIntOrNull() ?: 0) % 5
            }
            "https://cdn.discordapp.com/embed/avatars/$index.png"
        }
    }
}