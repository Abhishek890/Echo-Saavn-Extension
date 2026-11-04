package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn authentication verification.
 *
 * This class does NOT perform the login itself — login happens in a WebView
 * (`LoginClientImpl`) where the user authenticates on JioSaavn's own page, and
 * the resulting session cookie is captured on redirect. This API only **verifies**
 * a candidate cookie by asking the server who it belongs to.
 *
 * ### Why `BaseApi`, not `AuthenticatedBaseApi`
 *
 * [AuthenticatedBaseApi] reads the cookie from [dev.brahmkshatriya.echo.extension.SaavnSession.cookie]
 * — but here, the cookie is a *parameter*, not the active session. During login,
 * the candidate cookie exists only in the WebView; during `getCurrentUser` refresh,
 * the caller already has the cookie and wants to confirm it's still valid. Both cases
 * need to test a cookie that isn't (yet or anymore) the active session.
 *
 * @see dev.brahmkshatriya.echo.extension.client.LoginClientImpl
 */
class AuthApi : BaseApi() {

    /**
     * Verifies a candidate session cookie against the server.
     *
     * The caller (`LoginClientImpl.verifyLogin`) inspects the response's `user_state`
     * object to confirm the login is active and extract the user's identity
     * (`uid`, `firstname`, `lastname`, `email`).
     *
     * Returns 200 even for invalid/expired cookies — the "not logged in" signal is
     * `user_state.user_logged_in != 1` in the body, not an HTTP error. Callers must
     * check the body, not the status code.
     *
     * @param cookie the raw cookie header string to test (e.g. `"L=hindi; csrf=...;"`).
     * @return the config response containing `user_state` (and other fields).
     */
    suspend fun getConfigDetailsCSR(cookie: String): JsonObject = executeRequest(
        call = "webapi.getConfigDetailsCSR",
        headers = mapOf("Cookie" to cookie),
    )

}
