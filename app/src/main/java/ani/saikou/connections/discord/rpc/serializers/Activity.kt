package ani.saikou.connections.discord.rpc.serializers

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Activity(
    @SerialName("application_id")
    val applicationId: String? = null,
    val name: String?=null,
    val type: Int,
    val details: String? = null,
    val state: String? = null,
    val timestamps: Timestamps? = null,
    val assets: Assets? = null,
//    val buttons: List<Button>?=emptyList()

)