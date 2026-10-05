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

    suspend fun getDetails(playlistId: String): JsonObject = executeRequest(
        call = "webapi.get",
        params = mapOf(
            "type" to "playlist",
            "token" to playlistId,
            "p" to "1",
            "n" to "100"
        )
    )

    suspend fun getPlaylistReco(listId: String): JsonObject = executeRequest(
        call = "reco.getPlaylistReco",
        params = mapOf(
            "listid" to listId
        )
    )

}
