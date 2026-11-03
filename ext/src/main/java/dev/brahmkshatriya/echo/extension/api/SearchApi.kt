package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn quick-search endpoint.
 *
 * Powers Echo's "quick search" feature — the instant results shown as the user types.
 * Unlike the full search (which requires separate calls per entity type via
 * [TrackApi.search] / [AlbumApi.search] / [ArtistApi.search] / [PlaylistApi.search]),
 * this returns all entity types in a single response.
 *
 * ### `api_version = null`
 *
 * This is the only endpoint in the extension that passes `apiVersion = null`.
 * Sending `api_version=4` (the default) changes the response shape and content in
 * ways that break quick search — the endpoint returns a different, incompatible
 * payload with that param present. Omitting it restores the expected response.
 *
 * @see dev.brahmkshatriya.echo.extension.client.QuickSearchClientImpl
 */
class SearchApi : BaseApi() {

    /**
     * Runs a quick-search across all entity types (songs, albums, artists, playlists).
     *
     * @param query the search terms.
     * @return the autocomplete response, containing per-type result arrays
     *   (`songs`, `albums`, `artists`, `playlists`, and possibly `topquery`).
     */
    suspend fun all(query: String): JsonObject = executeRequest(
        call = "autocomplete.get",
        params = mapOf("query" to query),
        apiVersion = null,
    )
}
