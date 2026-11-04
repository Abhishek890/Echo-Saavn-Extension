package dev.brahmkshatriya.echo.extension.client

import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.User

import kotlinx.serialization.json.*

import dev.brahmkshatriya.echo.extension.Saavn
import dev.brahmkshatriya.echo.extension.utils.Logger

class LoginClientImpl : LoginClient.WebView {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val webViewRequest: WebViewRequest<List<User>> =
        object : WebViewRequest.Cookie<List<User>> {
            override val initialUrl = "https://www.jiosaavn.com/login".toGetRequest()

            // JioSaavn home page calls webapi.getLaunchData after login completes.
            // This is our signal that the user is logged in and the cookie is valid.
            override val stopUrlRegex = Regex(".*webapi\\.getLaunchData.*")

            override val dontCache = true

            override suspend fun onStop(url: NetworkRequest, cookie: String): List<User>? {
                Logger.d("LoginClient", "onStop: url=${url.url}, cookie length=${cookie.length}")

                val user = verifyLogin(cookie)
                if (user == null) {
                    Logger.d("LoginClient", "Login not verified — keeping WebView open")
                    return emptyList()
                }

                // Store cookie
                Saavn.sessionCookie = cookie
                Saavn.currentUserId = user.id
                Logger.d("LoginClient", "Login successful for user: ${user.name}")

                return listOf(user)
            }
        }

    // ===== VERIFY LOGIN =====

    private suspend fun verifyLogin(cookie: String): User? {
        return try {
            val response = Saavn.api.auth.getConfigDetailsCSR(cookie)
            val userState = response["user_state"]?.jsonObject
            if (userState == null) {
                Logger.d("LoginClient", "verifyLogin: no user_state")
                return null
            }

            val isLoggedIn = userState["user_logged_in"]?.jsonPrimitive?.intOrNull == 1
            if (!isLoggedIn) {
                Logger.d("LoginClient", "verifyLogin: user_logged_in != 1")
                return null
            }

            val uid = userState["uid"]?.jsonPrimitive?.content
            if (uid.isNullOrBlank()) {
                Logger.d("LoginClient", "verifyLogin: missing uid")
                return null
            }

            val firstname = userState["firstname"]?.jsonPrimitive?.content ?: ""
            val lastname = userState["lastname"]?.jsonPrimitive?.content ?: ""
            val email = userState["email"]?.jsonPrimitive?.content ?: ""

            val name = "$firstname $lastname".trim().ifBlank { email.ifBlank { "User" } }

            User(
                id = uid,
                name = name,
                subtitle = email.ifBlank { null },
                extras = mapOf("cookie" to cookie)
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("LoginClient", "Failed to verify login", e)
            null
        }
    }

    // ===== SET USER / LOGOUT =====

    override fun setLoginUser(user: User?) {
        if (user == null) {
            // Logout
            Logger.d("LoginClient", "setLoginUser(null) — logging out")
            Saavn.sessionCookie = null
            Saavn.currentUserId = null
        } else {
            // Restore session from user extras (e.g., on app restart)
            val cookie = user.extras["cookie"]
            Logger.d("LoginClient", "setLoginUser: ${user.name}, cookie=${cookie != null}")
            Saavn.sessionCookie = cookie
            Saavn.currentUserId = user.id
        }
    }

    override suspend fun getCurrentUser(): User? {
        val cookie = Saavn.sessionCookie ?: return null
        return verifyLogin(cookie)?.copy(extras = emptyMap())
    }
}
