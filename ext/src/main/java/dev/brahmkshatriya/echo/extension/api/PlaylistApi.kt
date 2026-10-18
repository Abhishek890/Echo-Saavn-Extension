package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

class PlaylistApi : BaseApi() {

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

    suspend fun getDetails(
        token: String,
        page: Int = 1,
        count: Int = 100
    ): JsonObject = executeRequest(
        call = "webapi.get",
        params = mapOf(
            "type" to "playlist",
            "token" to token,
            "p" to page.toString(),
            "n" to count.toString()
        )
    )

    suspend fun getPlaylistReco(listId: String): JsonObject = executeRequest(
        call = "reco.getPlaylistReco",
        params = mapOf(
            "listid" to listId
        )
    )

}
