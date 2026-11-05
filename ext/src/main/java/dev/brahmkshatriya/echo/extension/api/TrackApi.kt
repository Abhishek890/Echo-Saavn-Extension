package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject
import dev.brahmkshatriya.echo.extension.utils.Ctx

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

    /**
     * Fetches song recommendations for a given song.
     *
     * Used by the song detail feed's "You Might Like" shelf. Note that the input
     * is the numeric id, not the token — see [songId] param.
     *
     * @param songId the saavn id (e.g. `"38682222"`), NOT the permalink token.
     *   This endpoint accepts ids; passing a token returns an empty result.
     * @return the response which has recommended song objects.
     */
    suspend fun getSongReco(songId: String): JsonObject = executeRequest(
        call = "reco.getreco",
        params = mapOf("pid" to songId),
        ctx = Ctx.ANDROID
    )

}
