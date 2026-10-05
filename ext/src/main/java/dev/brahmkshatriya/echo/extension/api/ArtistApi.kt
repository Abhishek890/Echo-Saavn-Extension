package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

class ArtistApi : BaseApi() {

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

    enum class ArtistCategory(val apiValue: String, val sortOrder: String) {
        POPULAR("popularity", "asc"),
        LATEST("latest", "desc")
    }

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
            put("p", (page - 1).toString())
            put("sub_type", subType)
            put("category", category.apiValue)
            put("sort_order", category.sortOrder)
        }
    )

    suspend fun getMoreSongs(
        artistId: String,
        page: Int,
        category: ArtistCategory = ArtistCategory.POPULAR
    ): JsonObject = executeRequest(
        call = "artist.getArtistMoreSong",
        params = mapOf(
            "artistId" to artistId,
            "page" to (page - 1).toString(),
            "category" to category.apiValue,
            "sort_order" to category.sortOrder
        )
    )

    suspend fun getMoreAlbums(
        artistId: String,
        page: Int,
        category: ArtistCategory = ArtistCategory.POPULAR
    ): JsonObject = executeRequest(
        call = "artist.getArtistMoreAlbum",
        params = mapOf(
            "artistId" to artistId,
            "page" to (page - 1).toString(),
            "category" to category.apiValue,
            "sort_order" to category.sortOrder
        )
    )

}
