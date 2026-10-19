package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

class TrackApi : BaseApi() {

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

    suspend fun getDetails(songId: String): JsonObject = executeRequest(
        call = "webapi.get",
        params = mapOf(
            "type" to "song",
            "token" to songId
        )
    )
}
