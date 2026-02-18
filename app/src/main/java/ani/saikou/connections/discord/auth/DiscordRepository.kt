package ani.saikou.connections.discord.auth

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.core.content.edit
import ani.saikou.R
import ani.saikou.client
import ani.saikou.connections.discord.auth.serializers.User
import ani.saikou.others.CustomBottomDialog
import ani.saikou.toast
import ani.saikou.tryWith
import ani.saikou.tryWithSuspend
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin

import java.io.File

class DiscordRepository(private val context: Context) {


    private val discordAuthToken: String = "discord_auth_token"
    suspend fun fetchUserData(token: String?): User? {

        if (token.isNullOrBlank()) return null

        return tryWithSuspend(post = true) {

            client.get(
                url = "https://discord.com/api/v10/users/@me",
                headers = mapOf("Authorization" to token)
            ).parsed<User>()
        }
    }


    fun saveToken(authToken: String) {
        val sharedPreferences =
            context.getSharedPreferences(
                context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE
            )

        sharedPreferences.edit {
            putString(discordAuthToken, authToken)
            apply()
        }
    }

    fun getSavedToken(): String? {
        val sharedPreferences = context.getSharedPreferences(
            context.getString(R.string.preference_file_key),
            Context.MODE_PRIVATE
        )

        return sharedPreferences.getString(discordAuthToken, null)
    }

    fun removeSavedToken() {
        val sharedPreferences = context.getSharedPreferences(
            context.getString(R.string.preference_file_key),
            Context.MODE_PRIVATE
        )

        sharedPreferences.edit {
            remove(discordAuthToken)
            apply()
        }
        //clear browser data
        tryWith(true) {
            val dir = File(context.filesDir?.parentFile, "app_webview")
            if (dir.deleteRecursively())
                toast(context.getString(R.string.discord_logout_success))
        }
    }


    fun warning(context: Context) = CustomBottomDialog().apply {
        title = context.getString(R.string.warning)
        val md = context.getString(R.string.discord_warning)
        addView(TextView(context).apply {
            val markWon =
                Markwon.builder(context).usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
            markWon.setMarkdown(this, md)
        })

        setNegativeButton(context.getString(R.string.cancel)) {
            dismiss()
        }

        setPositiveButton(context.getString(R.string.login)) {
            dismiss()
            loginIntent(context)
        }
    }

    private fun loginIntent(context: Context) {
        val intent = Intent(context, Login::class.java)
        context.startActivity(intent)
    }

}



data class DiscordUiState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val avatarUrl: String? = null,
    val isRpcEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)