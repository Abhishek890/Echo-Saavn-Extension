package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.extension.Saavn
import kotlinx.serialization.json.JsonObject

/**
 * Base class for JioSaavn APIs that require a logged-in session.
 *
 * Extends [BaseApi] and adds automatic cookie injection: any call made via
 * [executeAuthed] sends the session cookie from [Saavn.sessionCookie].
 * If no cookie is present, throws [ClientException.LoginRequired] — which Echo
 * catches and turns into a login prompt.
 *
 * ### Why a separate class (vs. a `useAuth` flag on `executeRequest`)
 *
 * Whether an endpoint needs auth is a **class-level** property, not a per-call
 * decision. Splitting into `BaseApi` (public) vs. `AuthenticatedBaseApi` (session)
 * makes the compiler enforce the distinction: public APIs literally cannot send
 * a cookie, and authenticated APIs literally cannot forget to. This prevents the
 * most common class of auth bugs — silently making a request without the cookie
 * and getting an empty/restricted response instead of an error.
 *
 * ### Which classes extend this
 *
 * - [LibraryApi] — read the user's liked/followed items
 * - [FollowApi] — follow/unfollow artists and playlists
 * - [PlaylistEditApi] — create/edit/delete Saavn playlists
 *
 * ### Which classes do NOT extend this
 *
 * - [AuthApi] — verifies a *candidate* cookie; the cookie is a parameter, not the session
 * - [TrackApi], [AlbumApi], [ArtistApi], [PlaylistApi], [HomeApi], [SearchApi], [RadioApi]
 *   — public endpoints that don't need a session
 */
open class AuthenticatedBaseApi : BaseApi() {

    /**
     * Returns the current session cookie, or throws [ClientException.LoginRequired]
     * if none is set.
     *
     * Used internally by [executeAuthed]. Protected so subclasses can read the raw
     * cookie if they need to craft a custom request (e.g. adding a second cookie
     * alongside the session — uncommon, but possible).
     */
    protected fun requireCookie(): String =
        Saavn.sessionCookie ?: throw ClientException.LoginRequired()

    /**
     * Executes an authenticated `__call=...` request with the session cookie attached.
     *
     * Behaves identically to [BaseApi.executeRequest] except for the added cookie.
     * Params, headers, response shape, and exception behavior are all inherited.
     *
     * ### Merging extra headers
     *
     * [extraHeaders] are merged BEFORE the session cookie is attached — if a caller
     * passes `"Cookie"` in [extraHeaders], the session cookie replaces it. This is
     * intentional: the session is authoritative on authenticated endpoints.
     *
     * ### Throws
     * - [ClientException.LoginRequired] if no session cookie is set.
     * - Any exception [BaseApi.executeRequest] would throw (HTTP errors, empty body,
     *   unexpected JSON type).
     *
     * @param call the JioSaavn API method name.
     * @param params additional query parameters for this call.
     * @param extraHeaders additional headers. `Cookie` here is overwritten by the
     *   session cookie — pass non-cookie headers (e.g. custom `Accept`) only.
     * @return the parsed response object (arrays wrapped as `{"results": [...]}`).
     */
    protected suspend fun executeAuthed(
        call: String,
        params: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonObject = executeRequest(
        call = call,
        params = params,
        headers = extraHeaders + mapOf("Cookie" to requireCookie()),
    )
}
