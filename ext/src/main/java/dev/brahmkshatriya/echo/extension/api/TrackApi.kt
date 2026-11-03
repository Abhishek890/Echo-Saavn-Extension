package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn track endpoints.
 *
 * Covers search and detail fetch for songs. Streamable URL resolution lives in
 * `TrackClientImpl` (decryption), not here — this class is pure API wrappers.
 *
 * @see dev.brahmkshatriya.echo.extension.client.TrackClientImpl
 */
class TrackApi : BaseApi() {

    /**
     * Searches songs by free-text query.
     *
     * @param query the search terms.
     * @param page 1-based page number.
     * @param limit page size.
     * @return the search response. The server returns an object with a `results` array
     *   (server-provided, not fabricated by [executeRequest]) containing song objects.
     */
    suspend fun search(
        query: String,
        page: Int = 1,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): JsonObject = executeRequest(
        call = "search.getResults",
        params = mapOf(
            "q" to query,
            "p" to page.toString(),
            "n" to limit.toString()
        )
    )

    /**
     * Fetches full details for a single song.
     *
     * Uses the generic `webapi.get` endpoint with `type=song` — the same endpoint
     * dispatches to album/artist/playlist depending on [songId]'s `type` param.
     *
     * @param songId the song token (permalink segment), NOT the numeric id.
     *   `Track.extras[PERMA_URL]` → `getToken()` yields the correct value.
     * @return the song response. The shape matches what `library.getDetails` returns
     *   for a single song, so `parseSongDetails` can parse it.
     */
    suspend fun getDetails(songId: String): JsonObject = executeRequest(
        call = "webapi.get",
        params = mapOf(
            "type" to "song",
            "token" to songId
        )
    )
}
