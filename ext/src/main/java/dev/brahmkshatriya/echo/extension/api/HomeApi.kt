package dev.brahmkshatriya.echo.extension.api

import kotlinx.serialization.json.JsonObject

class HomeApi : BaseApi() {

    suspend fun getHomeData(language: String = "hindi"): JsonObject = executeRequest(
        call = "webapi.getLaunchData",
        headers = mapOf("Cookie" to "L=$language;")
    )

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

    suspend fun getTrending(entityType: String, language: String): JsonObject = executeRequest(
        call = "content.getTrending",
        params = mapOf(
            "entity_type" to entityType,
            "entity_language" to language
        )
    )

}
