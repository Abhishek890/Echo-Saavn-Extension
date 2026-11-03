package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn home/launch endpoints.
 *
 * ### The language cookie trick
 *
 * JioSaavn decides which language's content to return based on a `Cookie: L=<languages>;`
 * header, not a query parameter. The `L` value can be a single language (`"hindi"`) or
 * a comma-separated list (`"hindi,telugu,english"`) to get a mixed feed across those
 * languages. [getHomeData] and [getMore] send this header to scope the response.
 *
 * This `L` cookie is unrelated to the session/auth cookie — it's a preference hint that
 * works even when logged out. It's sent via `headers = mapOf("Cookie" to ...)` for that
 * reason.
 */
class HomeApi : BaseApi() {

    /**
     * Fetches the initial home feed for the given [language].
     *
     * @param language JioSaavn language identifier or comma-separated list, e.g. `"hindi"`,
     *   `"hindi,telugu"`. Sent as the `L` cookie so the server returns content across all
     *   listed languages (mixed feed). This is how Echo's "Default" tab mixes languages.
     * @return the home/launch response JSON, containing a `modules` array of shelves.
     */
    suspend fun getHomeData(language: String = "hindi"): JsonObject = executeRequest(
        call = "webapi.getLaunchData",
        headers = mapOf("Cookie" to "L=$language;")
    )

    /**
     * Fetches an additional page of a home-feed shelf.
     *
     * Shelf endpoints come from the server (`HomeParser` extracts them from `modules`),
     * so the call name, page/size param names, and default size all come from the
     * feed data rather than being hardcoded here.
     *
     * @param api the endpoint call name for this shelf (e.g. `"content.getTrending"`).
     * @param page page number (1-based).
     * @param size page size — typically `MoreInfo.defaultSize` from the shelf metadata.
     * @param pageParam the query param name the endpoint expects for the page number
     *   (e.g. `"p"` for some endpoints, `"page"` for others).
     * @param sizeParam the query param name for the page size.
     * @param language optional language or comma-separated list (e.g. `"hindi,telugu"`).
     *   When null or blank, the request is made without the `L` cookie (returns the
     *   server's default mix).
     */
    suspend fun getMore(
        api: String,
        page: Int,
        size: Int,
        pageParam: String,
        sizeParam: String,
        language: String? = null
    ): JsonObject = executeRequest(
        call = api,
        params = mapOf(
            pageParam to page.toString(),
            sizeParam to size.toString()
        ),
        headers = if (!language.isNullOrBlank()) {
            mapOf("Cookie" to "L=$language;")
        } else {
            emptyMap()
        }
    )

    /**
     * Fetches trending items of a given type scoped to a language.
     *
     * Unlike [getHomeData]/[getMore], this uses query params (`entity_type`,
     * `entity_language`) rather than the `L` cookie — the endpoint's design, not a
     * choice made here.
     *
     * @param entityType `"song"`, `"album"`, `"playlist"`, or `"artist"`.
     * @param language language identifier, e.g. `"hindi"`. Single language only —
     *   unlike the `L` cookie, this param doesn't support comma-separated lists.
     * @return a bare array, wrapped by [executeRequest] as `{"results": [...]}`.
     */
    suspend fun getTrending(entityType: String, language: String): JsonObject = executeRequest(
        call = "content.getTrending",
        params = mapOf(
            "entity_type" to entityType,
            "entity_language" to language
        )
    )

}
