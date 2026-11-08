package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn playlist endpoints (public reads).
 *
 * Covers search, detail fetch (paginated track list included), and recommendations.
 * Writes (create, add tracks, remove, reorder, delete) live in [PlaylistEditApi]
 * and require authentication.
 *
 * @see PlaylistEditApi
 * @see dev.brahmkshatriya.echo.extension.client.PlaylistEditClientImpl
 */
class PlaylistApi : BaseApi() {

    /**
     * Searches playlists by free-text query.
     *
     * @param query the search terms.
     * @param page 1-based page number.
     * @param limit page size.
     * @return the search response. The server returns an object with a `results` array
     *   (server-provided, not fabricated by [executeRequest]) containing playlist objects.
     */
    suspend fun search(
        query: String,
        page: Int = 1,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): JsonObject = executeRequest(
        call = "search.getPlaylistResults",
        params = mapOf(
            "q" to query,
            "p" to page.toString(),
            "n" to limit.toString()
        )
    )

    /**
     * Fetches playlist details, including a page of its tracks.
     *
     * The playlist's metadata (title, cover, track count, uid) is returned on every
     * page — only the `songs` array differs between pages. This is why callers can
     * fetch page 1 for metadata and lazily fetch further pages for the track list
     * without re-reading metadata.
     *
     * @param token the playlist token (permalink segment), NOT the numeric id.
     *   `Playlist.extras[PERMA_URL]` → `getToken()` yields the correct value.
     * @param page 1-based page number for the track list.
     * @param count page size. JioSaavn returns at most this many tracks per page;
     *   callers paginating should keep this consistent across calls.
     * @return the playlist response. `PlaylistParser.parsePlaylistToPlaylist` extracts
     *   metadata, `PlaylistParser.parsePlaylistTracks` extracts the tracks from the
     *   current page.
     */
    suspend fun getDetails(
        token: String,
        page: Int = 1,
        count: Int = 50
    ): JsonObject = executeRequest(
        call = "webapi.get",
        params = mapOf(
            "type" to "playlist",
            "token" to token,
            "p" to page.toString(),
            "n" to count.toString()
        )
    )

    /**
     * Fetches playlist recommendations for a given playlist.
     *
     * Used by the playlist detail feed's "Related Playlists" shelf.
     *
     * @param listId the numeric playlist id (e.g. `"1304962086"`), NOT the permalink
     *   token. This endpoint accepts numeric ids; passing a token returns an empty
     *   result.
     * @return the response which has recommended playlist objects. The server returns a bare JSON array, which
     *   [executeRequest] wraps as `{"results": [...]}`. Callers read `.results`.
     */
    suspend fun getPlaylistReco(listId: String): JsonObject = executeRequest(
        call = "reco.getPlaylistReco",
        params = mapOf(
            "listid" to listId
        )
    )

}
