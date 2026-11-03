package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn artist endpoints.
 *
 * Covers search, detail fetch (with top songs/albums/singles/etc.), and paginated
 * "more songs" / "more albums" feeds used by the artist detail screen's "See more" tabs.
 *
 * ### Category and sort order
 *
 * Most endpoints take a [ArtistCategory], which pairs an `apiValue` (`"popularity"` /
 * `"latest"`) with a `sortOrder` (`"asc"` / `"desc"`). The pairing is intentionally
 * asymmetric — popularity is ascending (most played first), latest is descending
 * (newest first). This matches JioSaavn's own web player behavior.
 *
 * @see dev.brahmkshatriya.echo.extension.client.ArtistClientImpl
 */
class ArtistApi : BaseApi() {

    /**
     * Searches artists by free-text query.
     *
     * @param query the search terms.
     * @param page 1-based page number.
     * @param limit page size.
     * @return the search response. The server returns an object with a `results` array
     *   (server-provided, not fabricated by [executeRequest]) containing artist objects.
     */
    suspend fun search(
        query: String,
        page: Int = 1,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): JsonObject = executeRequest(
        call = "search.getArtistResults",
        params = mapOf(
            "q" to query,
            "p" to page.toString(),
            "n" to limit.toString()
        )
    )

    /**
     * Artist feed category, with its paired sort order.
     *
     * The `apiValue` and `sortOrder` are both sent to the API and together determine
     * which ordering is requested. They're kept together as one enum (rather than
     * two separate params) because they're always used as a pair — mixing a category
     * with the wrong sort order gives garbage results.
     */
    enum class ArtistCategory(val apiValue: String, val sortOrder: String) {
        POPULAR("popularity", "asc"),
        LATEST("latest", "desc")
    }

    /**
     * Fetches full artist details — top songs, top albums, singles, dedicated/featured
     * playlists, and similar artists in a single response.
     *
     * @param token the artist token (permalink segment), NOT the numeric id.
     *   `Artist.extras[PERMA_URL]` → `getToken()` yields the correct value.
     * @param songCount max number of songs to include in the initial feed.
     * @param albumCount max number of albums to include in the initial feed.
     * @param page 1-based page number. NOTE: converted to 0-based internally
     *   (`p = page - 1`) because this endpoint is 0-indexed, unlike most others.
     * @param subType additional filtering hint; usually empty.
     * @param category which section (Popular/Latest) to use for the top songs and
     *   top albums in the initial response.
     * @return the artist response. `ArtistParser` methods extract each section
     *   (top songs, top albums, singles, playlists, similar artists) from it.
     */
    suspend fun getDetails(
        token: String,
        songCount: Int = 50,
        albumCount: Int = 50,
        page: Int = 1,
        subType: String = "",
        category: ArtistCategory = ArtistCategory.POPULAR
    ): JsonObject = executeRequest(
        call = "webapi.get",
        params = buildMap {
            put("type", "artist")
            put("token", token)
            put("n_song", songCount.toString())
            put("n_album", albumCount.toString())
            // This endpoint is 0-indexed — subtract 1 from the caller's 1-based page.
            put("p", (page - 1).toString())
            put("sub_type", subType)
            put("category", category.apiValue)
            put("sort_order", category.sortOrder)
        }
    )

    /**
     * Fetches an additional page of an artist's songs for the "See more" tab.
     *
     * @param artistId the numeric artist id (e.g. `"568565"`), NOT the permalink token.
     *   This endpoint accepts numeric ids.
     * @param page 1-based page number. Converted to 0-based internally
     *   (`page = page - 1`) because this endpoint is 0-indexed.
     * @param category which ordering to request (Popular/Latest).
     * @return the response object with a nested `topSongs.songs` array
     *   — parsed by `parseArtistMoreSongs`.
     */
    suspend fun getMoreSongs(
        artistId: String,
        page: Int,
        category: ArtistCategory = ArtistCategory.POPULAR
    ): JsonObject = executeRequest(
        call = "artist.getArtistMoreSong",
        params = mapOf(
            "artistId" to artistId,
            // 0-indexed endpoint — subtract 1 from the caller's 1-based page.
            "page" to (page - 1).toString(),
            "category" to category.apiValue,
            "sort_order" to category.sortOrder
        )
    )

    /**
     * Fetches an additional page of an artist's albums for the "See more" tab.
     *
     * @param artistId the numeric artist id (e.g. `"568565"`), NOT the permalink token.
     * @param page 1-based page number. Converted to 0-based internally
     *   (`page = page - 1`) because this endpoint is 0-indexed.
     * @param category which ordering to request (Popular/Latest).
     * @return the response object with a nested `topAlbums.albums` array
     *   — parsed by `parseArtistMoreSongs`.
     */
    suspend fun getMoreAlbums(
        artistId: String,
        page: Int,
        category: ArtistCategory = ArtistCategory.POPULAR
    ): JsonObject = executeRequest(
        call = "artist.getArtistMoreAlbum",
        params = mapOf(
            "artistId" to artistId,
            // 0-indexed endpoint — subtract 1 from the caller's 1-based page.
            "page" to (page - 1).toString(),
            "category" to category.apiValue,
            "sort_order" to category.sortOrder
        )
    )

}
