package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

class AlbumApi : BaseApi() {

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

    suspend fun getDetails(albumId: String): JsonObject = executeRequest(
        call = "webapi.get",
        params = mapOf(
            "type" to "album",
            "token" to albumId
        )
    )

    suspend fun getTopAlbumsOfYear(year: String, language: String): JsonObject = executeRequest(
        call = "search.topAlbumsoftheYear",
        params = mapOf(
            "album_year" to year,
            "album_lang" to language
        )
    )

    suspend fun getAlbumReco(albumId: String): JsonObject = executeRequest(
        call = "reco.getAlbumReco",
        params = mapOf(
            "albumid" to albumId
        )
    )

}
