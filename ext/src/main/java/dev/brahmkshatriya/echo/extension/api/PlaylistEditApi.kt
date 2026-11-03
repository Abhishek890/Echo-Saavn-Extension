package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn playlist write endpoints (authenticated).
 *
 * Covers the user's own playlists — listing, adding/removing tracks, and reordering.
 * Public playlist reads (search, detail fetch) live in [PlaylistApi]; this class is
 * exclusively for mutations.
 *
 * ### Ownership requirement
 *
 * Every mutation here requires the playlist to be **owned by the current user**.
 * The server enforces this — non-owned playlists return an error status. Ownership
 * is determined by comparing `Playlist.extras["uid"]` to
 * [dev.brahmkshatriya.echo.extension.SaavnDependencies.currentUserId] in
 * `PlaylistEditClientImpl` before calling these endpoints.
 *
 * ### The `contents` encoding
 *
 * [add] and [save] take a `contents` string in JioSaavn's custom encoding:
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
 * ### Status values
 *
 * [listMyPlaylists], [add], [remove], [save] return `{"status": "ok"}` on success.
 * Note this differs from [LibraryApi] / [FollowApi], which return `"success"`.
 * The `requireSuccess` helper in `utils` accepts both.
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

}
