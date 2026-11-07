package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn playlist write endpoints (authenticated).
 *
 * Covers the user's own playlists:
 * - [listMyPlaylists] — read the list of playlists the user owns
 * - [create] / [delete] — lifecycle
 * - [rename] / [makePrivate] / [makePublic] — metadata and privacy
 * - [add] / [remove] / [save] — track membership and ordering
 *
 * Public playlist reads (search, detail fetch) live in [PlaylistApi]; this
 * class is exclusively for mutations and user-scoped reads.
 *
 * ### Ownership requirement
 *
 * Every mutation here requires the playlist to be **owned by the current user**.
 * The server enforces this — non-owned playlists return an error status.
 * Ownership is determined by comparing `Playlist.extras["uid"]` to
 * [dev.brahmkshatriya.echo.extension.SaavnSession.userId] in
 * `PlaylistEditClientImpl` before calling these endpoints.
 *
 * ### The `contents` encoding
 *
 * [add] and [save] (and optionally [create]) take a `contents` string in
 * JioSaavn's custom encoding:
 *
 *     ~~<songId>~<language>^~~<songId>~<language>^...
 *
 * - Each entry: `~~<id>~<language>` (double tilde, then id, then language)
 * - Entries joined by `^` (caret), NOT commas
 * - The full string is URL-encoded by `BaseApi.executeRequest`, so `^` -> `%5E`
 *
 * Callers should use `PlaylistEditClientImpl.encodeTracks(...)` rather than
 * constructing this manually.
 *
 * ### Status values (inconsistent)
 *
 * - `"ok"` — [listMyPlaylists], [create], [rename], [delete], [add], [remove], [save]
 * - `"success"` — [makePrivate], [makePublic]
 *
 * The `requireSuccess` helper in `utils` accepts both, so callers can use it
 * uniformly without branching on endpoint.
 *
 * @see PlaylistApi
 * @see dev.brahmkshatriya.echo.extension.client.PlaylistEditClientImpl
 */
class PlaylistEditApi : AuthenticatedBaseApi() {

    /**
     * Lists all playlists created by the current user (not saved/followed ones).
     *
     * Each playlist in the response is a full object (parseable by
     * `PlaylistParser.parsePlaylistToPlaylist`), including `.more_info.uid` and
     * `.more_info.contents` (the comma-separated list of track ids).
     *
     * `contents=1` is what makes `.more_info.contents` appear in the response —
     * without it, the playlist metadata comes back without the track id list, and
     * the "already in playlist" check in `listEditablePlaylists` breaks.
     *
     * `onlypids=true` limits what's returned to the playlist's own metadata (no
     * full song objects for each track) — cheaper and sufficient for the picker.
     *
     * @return a response whose `results` array (via `executeRequest`'s array-wrapping)
     *   contains full playlist objects.
     */
    suspend fun listMyPlaylists(): JsonObject =
        executeAuthed(
            call = "playlist.list",
            params = mapOf(
                "all_playlists" to "true",
                "contents" to "1",
                "onlypids" to "true",
            ),
        )

    /**
     * Appends one or more tracks to a playlist.
     *
     * The endpoint **appends** — there is no index parameter. Tracks are added at
     * the end in the order they appear in [contents]. Reordering existing tracks
     * requires [save].
     *
     * @param listId the numeric playlist id (e.g. `"1304962086"`). NOT the token —
     *   this endpoint only accepts numeric ids.
     * @param contents the encoded track list (see class header for the format).
     *   Multiple tracks can be added in one call by joining with `^`.
     * @return a response with `{"status": "ok"}` on success, and
     *   `.details.more_info.contents` containing the updated track id list.
     */
    suspend fun add(listId: String, contents: String): JsonObject =
        executeAuthed(
            call = "playlist.add",
            params = mapOf(
                "listid" to listId,
                "contents" to contents,
            ),
        )

    /**
     * Removes a single track from a playlist.
     *
     * **One track per call** — the API does not accept multiple `pid` values. Callers
     * removing multiple tracks must loop (see `PlaylistEditClientImpl.removeTracksFromPlaylist`).
     *
     * @param listId the numeric playlist id.
     * @param pid the numeric song id of the track to remove.
     * @return a response with `.details.more_info.contents` (the updated track id
     *   list). Note: this endpoint has **no `status` field** — success is inferred
     *   from the presence of `.details.more_info.contents`.
     */
    suspend fun remove(listId: String, pid: String): JsonObject =
        executeAuthed(
            call = "playlist.remove",
            params = mapOf(
                "listid" to listId,
                "pid" to pid,
            ),
        )

    /**
     * Replaces the first [n] tracks of a playlist with the given ordered [contents].
     *
     * Used for reordering. Given a playlist of `[A,B,C,D,E]` and a move of `E` to
     * index 1, the caller computes the new order `[A,E,B,C,D]`, sets `n = 5`
     * (or `n = max(from, to) + 1` rounded up to the nearest 50 if the playlist is
     * larger — see `PlaylistEditClientImpl.moveTrackInPlaylist`), and passes
     * `contents` for the first `n` entries.
     *
     * Tracks beyond `n` are left untouched. This is why `n` must be >= the highest
     * touched index + 1
     *
     * @param listId the numeric playlist id.
     * @param n the number of leading tracks being replaced. **Must cover the highest
     *   touched index.** The exact rule (rounding up to nearest 50 for large playlists)
     *   is in `PlaylistEditClientImpl.moveTrackInPlaylist`.
     * @param contents the encoded track list for the first `n` tracks (see class
     *   header for the format). Order in this string becomes the playlist's new order.
     * @return a response with `{"status": "ok"}` on success.
     */
    suspend fun save(listId: String, n: Int, contents: String): JsonObject =
        executeAuthed(
            call = "playlist.save",
            params = mapOf(
                "listid" to listId,
                "n" to n.toString(),
                "contents" to contents,
            ),
        )

    /**
     * Creates a new playlist owned by the current user.
     *
     * `contents=""` (empty) creates an empty playlist; populate it later with
     * [add]. `share=true` makes the playlist publicly shareable (visible to
     * anyone with the link) — JioSaavn has no per-invite sharing, so public is
     * the only "shareable" state.
     *
     * @param name the playlist name.
     * @param contents encoded initial tracks (see class header for the format).
     *   Pass `""` for an empty playlist. Multiple tracks can be provided on
     *   creation by joining entries with `^` — same format as [add].
     * @return a response with `{"status": "ok"}` and a `details` object
     *   containing the newly-created playlist's metadata (id, title, cover,
     *   uid, contents, …). The new playlist's id is `.details.listid` or
     *   `.details.id` — verify against a real response before wiring up
     *   `createPlaylist` in the client.
     */
    suspend fun create(
        name: String,
        contents: String = "",
        share: Boolean = true,
    ): JsonObject =
        executeAuthed(
            call = "playlist.create",
            params = mapOf(
                "listname" to name,
                "contents" to contents,
                "share" to share.toString(),
            ),
        )

    /**
     * Renames an existing playlist owned by the current user.
     *
     * @param listId the numeric playlist id (NOT the token).
     * @param name the new playlist name.
     * @return a response with `{"status": "ok"}` on success.
     */
    suspend fun rename(listId: String, name: String): JsonObject =
        executeAuthed(
            call = "playlist.rename",
            params = mapOf(
                "listid" to listId,
                "listname" to name,
            ),
        )

    /**
     * Makes a playlist private (visible only to the owner).
     *
     * Note: the success status for this endpoint is `"success"`, not `"ok"` —
     * inconsistent with the other playlist write endpoints. The `requireSuccess`
     * helper accepts both values, so callers don't need to special-case this.
     *
     * @param listId the numeric playlist id.
     * @return a response with `{"status": "success"}` on success.
     */
    suspend fun makePrivate(listId: String): JsonObject =
        executeAuthed(
            call = "playlist.makePrivate",
            params = mapOf("listid" to listId),
        )

    /**
     * Makes a playlist public (shareable via link).
     *
     * Note: same status value as [makePrivate] — `"success"`, not `"ok"`.
     *
     * @param listId the numeric playlist id.
     * @return a response with `{"status": "success"}` on success.
     */
    suspend fun makePublic(listId: String): JsonObject =
        executeAuthed(
            call = "playlist.makePublic",
            params = mapOf("listid" to listId),
        )


    companion object {
        /**
         * Static `X-Request-Key` header required by JioSaavn's authenticated
         * delete endpoint.
         *
         * This value was extracted from the JioSaavn web client's request
         * headers in DevTools. It is NOT account-specific — the same value is
         * sent for every user and survives logout/login, so it appears to be a
         * global constant baked into JioSaavn's JS bundle.
         *
         * ### Fragility warning
         *
         * JioSaavn may rotate this value (e.g. on a web client release). If it
         * changes, CSRF-protected writes fail with
         * `{"error":{"code":"INVALID_CSRF","msg":"Invalid CSRF Token"}}`.
         *
         * To re-extract: open JioSaavn in a browser, log in, open DevTools →
         * Network, perform the failing operation (e.g. delete a playlist), and
         * copy the `X-Request-Key` header from the failing request.
         *
         * TODO: If this becomes unstable, extract the key dynamically by
         *  fetching JioSaavn's web client JS and parsing it — see
         *  https://github.com/HemanthJabalpuri/Echo-Saavn-Extension/issues
         */
        private const val X_REQUEST_KEY =
            "8ea50be8ddcaacb1ac7d19610702e54be1c1b560c4762b0bbb82e284260bc9a6"
    }

    /**
     * Deletes a playlist owned by the current user.
     *
     * **Irreversible** — the playlist and its contents are removed from the
     * user's account. Callers should confirm with the user before invoking.
     *
     * ### CSRF requirement
     *
     * This endpoint requires the `X-Request-Key` header (see [X_REQUEST_KEY]).
     * Without it, the server rejects the request with `INVALID_CSRF`.
     *
     * @param listId the numeric playlist id.
     * @return the response (may contain `.details.more_info.contents` or be
     *   empty on success).
     */
    suspend fun delete(listId: String): JsonObject =
        executeAuthed(
            call = "playlist.delete",
            params = mapOf("listid" to listId),
            extraHeaders = mapOf("X-Request-Key" to X_REQUEST_KEY),
        )

}
