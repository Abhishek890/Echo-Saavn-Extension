package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn album endpoints.
 *
 * Covers search, detail fetch, yearly charts, and recommendations. Track listing
 * for an album comes from [getDetails]'s response (parsed by `AlbumParser.parseAlbumTracks`),
 * not a separate endpoint.
 *
 * @see dev.brahmkshatriya.echo.extension.service.AlbumService
 */
class AlbumApi : BaseApi() {

    /**
     * Searches albums by free-text query.
     *
     * @param query the search terms.
     * @param page 1-based page number.
     * @param limit page size.
     * @return the search response. The server returns an object with a `results` array
     *   (server-provided, not fabricated by [executeRequest]) containing album objects.
     */
    suspend fun search(
        query: String,
        page: Int = 1,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): JsonObject = executeRequest(
        call = "search.getAlbumResults",
        params = mapOf(
            "q" to query,
            "p" to page.toString(),
            "n" to limit.toString()
        )
    )

    /**
     * Fetches full details for a single album, including its track list.
     *
     * @param albumId the album token (permalink segment), NOT the numeric id.
     *   `Album.extras[PERMA_URL]` → `getToken()` yields the correct value.
     * @return the album response. `AlbumParser.parseAlbumToAlbum` extracts metadata
     *   and `AlbumParser.parseAlbumTracks` extracts the track list from the same
     *   response.
     */
    suspend fun getDetails(albumId: String): JsonObject = executeRequest(
        call = "webapi.get",
        params = mapOf(
            "type" to "album",
            "token" to albumId
        )
    )

    /**
     * Fetches the top albums chart for a given year and language.
     *
     * Used by the album detail feed's "Top Albums of {year}" shelf when the current
     * album's year and language are known from its response.
     *
     * @param year 4-digit year string, e.g. `"2023"`.
     * @param language language identifier, e.g. `"hindi"`.
     * @return the response which has album objects. The server returns a bare JSON array, which
     *   [executeRequest] wraps as `{"results": [...]}`. Callers read `.results`.
     */
    suspend fun getTopAlbumsOfYear(year: String, language: String): JsonObject = executeRequest(
        call = "search.topAlbumsoftheYear",
        params = mapOf(
            "album_year" to year,
            "album_lang" to language
        )
    )

    /**
     * Fetches album recommendations for a given album.
     *
     * Used by the album detail feed's "You Might Like" shelf. Note that the input
     * is the numeric id, not the token — see [albumId] param.
     *
     * @param albumId the numeric album id (e.g. `"38682222"`), NOT the permalink token.
     *   This endpoint accepts numeric ids; passing a token returns an empty result.
     * @return the response which has recommended album objects. The server returns a bare JSON array, which
     *   [executeRequest] wraps as `{"results": [...]}`. Callers read `.results`.
     */
    suspend fun getAlbumReco(albumId: String): JsonObject = executeRequest(
        call = "reco.getAlbumReco",
        params = mapOf(
            "albumid" to albumId
        )
    )

}
