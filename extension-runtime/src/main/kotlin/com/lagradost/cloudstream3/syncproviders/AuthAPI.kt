package com.lagradost.cloudstream3.syncproviders

import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URL

data class AuthLoginPage(
    val url: String,
    val payload: String? = null,
)

data class AuthToken(
    @JsonProperty("accessToken") val accessToken: String? = null,
    @JsonProperty("refreshToken") val refreshToken: String? = null,
    @JsonProperty("accessTokenLifetime") val accessTokenLifetime: Long? = null,
    @JsonProperty("refreshTokenLifetime") val refreshTokenLifetime: Long? = null,
    @JsonProperty("payload") val payload: String? = null,
)

data class AuthUser(
    @JsonProperty("name") val name: String?,
    @JsonProperty("id") val id: Int,
    @JsonProperty("profilePicture") val profilePicture: String? = null,
    @JsonProperty("profilePictureHeader") val profilePictureHeaders: Map<String, String>? = null
)

data class AuthData(
    @JsonProperty("user") val user: AuthUser,
    @JsonProperty("token") val token: AuthToken,
)

data class AuthPinData(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int,
    val interval: Int,
)

enum class AuthLoginRequirement {
    Email,
    Password,
}

data class AuthLoginResponse(
    val email: String,
    val password: String,
)

abstract class AuthAPI {
    open val name: String = "NONE"
    open val idPrefix: String = "NONE"
    open val requiresLogin: Boolean = true
    open val createAccountUrl: String? = null
    open val redirectUrlIdentifier: String? = null
    open val hasOAuth2: Boolean = false
    open val hasInApp: Boolean = false
    open val hasPin: Boolean = false
    open var mainUrl: String = "NONE"
    open val icon: Int? = null

    open val inAppLoginRequirement: List<AuthLoginRequirement> = emptyList()

    companion object {
        val unixTime: Long get() = System.currentTimeMillis() / 1000L

        fun splitRedirectUrl(redirectUrl: String): Map<String, String> {
            return redirectUrl.substringAfter("?").split("&").associate {
                val (key, value) = it.split("=")
                key to value
            }
        }

        fun generateCodeVerifier(): String {
            val secureRandom = java.security.SecureRandom()
            val code = ByteArray(32)
            secureRandom.nextBytes(code)
            return android.util.Base64.encodeToString(
                code,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
        }
    }

    open fun loginRequest(): AuthLoginPage? = null
    open suspend fun pinRequest(): AuthPinData? = null
    open suspend fun login(redirectUrl: String, payload: String?): AuthToken? = null
    open suspend fun login(payload: AuthPinData): AuthToken? = null
    open suspend fun login(payload: AuthLoginResponse): AuthToken? = null
    open suspend fun refreshToken(token: AuthToken): AuthToken? = null
    open suspend fun user(token: AuthToken?): AuthUser? = null
}

abstract class AuthRepo(open val api: AuthAPI)
